/*
 * Copyright (c) 2017-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.storescp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4che3.tool.common.CLIUtils;
import org.dcm4che3.util.AttributesFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class StoreSCP {

  private static final Logger LOGGER = LoggerFactory.getLogger(StoreSCP.class);

  private static final String TMP_DIR = "tmp";

  private final Device device = new Device("storescp");
  private final ApplicationEntity ae = new ApplicationEntity("*");
  private final Connection conn = new Connection();
  private final File storageDir;
  private final List<DicomNode> authorizedCallingNodes;
  private AttributesFormat filePathFormat;
  private Pattern regex;
  private volatile int status = Status.Success;
  private int[] receiveDelays;
  private int[] responseDelays;

  private final DicomProgress progress;

  private final BasicCStoreSCP cstoreSCP =
      new BasicCStoreSCP("*") {

        @Override
        protected void store(
            Association as,
            PresentationContext pc,
            Attributes rq,
            PDVInputStream data,
            Attributes rsp)
            throws IOException {
          if (authorizedCallingNodes != null && !authorizedCallingNodes.isEmpty()) {
            DicomNode sourceNode = DicomNode.buildRemoteDicomNode(as);
            boolean valid =
                authorizedCallingNodes.stream()
                    .anyMatch(
                        n ->
                            n.getAet().equals(sourceNode.getAet())
                                && (!n.isValidateHostname()
                                    || n.equalsHostname(sourceNode.getHostname())));
            if (!valid) {
              rsp.setInt(Tag.Status, VR.US, Status.NotAuthorized);
              LOGGER.error(
                  "Refused: not authorized (124H). Source node: {}. SopUID: {}",
                  sourceNode,
                  rq.getString(Tag.AffectedSOPInstanceUID));
              return;
            }
          }
          sleep(as, receiveDelays);
          try {
            rsp.setInt(Tag.Status, VR.US, status);

            String cuid = rq.getString(Tag.AffectedSOPClassUID);
            String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
            String tsuid = pc.getTransferSyntax();
            File file = new File(storageDir, TMP_DIR + File.separator + iuid);
            try {
              Attributes fmi = as.createFileMetaInformation(iuid, cuid, tsuid);
              storeTo(as, fmi, data, file);
              String filename;
              if (filePathFormat == null) {
                filename = iuid;
              } else {
                Attributes a = fmi;
                Matcher regexMatcher = regex.matcher(filePathFormat.toString());
                while (regexMatcher.find()) {
                  if (!regexMatcher.group(1).startsWith("0002")) {
                    a = parse(file);
                    a.addAll(fmi);
                    break;
                  }
                }
                filename = filePathFormat.format(a);
              }
              File rename = new File(storageDir, filename);
              renameTo(as, file, rename);
              if (progress != null) {
                progress.setProcessedFile(rename);
                progress.setAttributes(null);
              }
            } catch (Exception e) {
              FileUtil.delete(file);
              throw new DicomServiceException(Status.ProcessingFailure, e);
            }
          } finally {
            sleep(as, responseDelays);
          }
        }
      };

  private void sleep(Association as, int[] delays) {
    int responseDelay =
        delays != null ? delays[(as.getNumberOfReceived(Dimse.C_STORE_RQ) - 1) % delays.length] : 0;
    if (responseDelay > 0) {
      try {
        Thread.sleep(responseDelay);
      } catch (InterruptedException ignore) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * @param storageDir the base path of storage folder
   */
  public StoreSCP(File storageDir) {
    this(storageDir, null);
  }

  /**
   * @param storageDir the base path of storage folder
   * @param authorizedCallingNodes the list of authorized nodes to call store files
   *     (authorizedCallingNodes allow to check hostname unlike acceptedCallingAETitles)
   */
  public StoreSCP(File storageDir, List<DicomNode> authorizedCallingNodes) {
    this(storageDir, authorizedCallingNodes, null);
  }

  public StoreSCP(
      File storageDir, List<DicomNode> authorizedCallingNodes, DicomProgress dicomProgress) {
    this.storageDir = Objects.requireNonNull(storageDir);
    device.setDimseRQHandler(createServiceRegistry());
    device.addConnection(conn);
    device.addApplicationEntity(ae);
    ae.setAssociationAcceptor(true);
    ae.addConnection(conn);
    this.authorizedCallingNodes = authorizedCallingNodes;
    this.progress = dicomProgress;
  }

  private void storeTo(Association as, Attributes fmi, PDVInputStream data, File file)
      throws IOException {
    LOGGER.debug("{}: M-WRITE {}", as, file);
    file.getParentFile().mkdirs();
    try (DicomOutputStream out = new DicomOutputStream(file)) {
      out.writeFileMetaInformation(fmi);
      data.copyTo(out);
    }
  }

  private static void renameTo(Association as, File from, File dest) throws IOException {
    LOGGER.info("{}: M-RENAME {} to {}", as, from, dest);
    FileUtil.prepareToWriteFile(dest);
    if (!from.renameTo(dest)) throw new IOException("Failed to rename " + from + " to " + dest);
  }

  private static Attributes parse(File file) throws IOException {
    try (DicomInputStream in = new DicomInputStream(file)) {
      in.setIncludeBulkData(IncludeBulkData.NO);
      return in.readDataset(-1, Tag.PixelData);
    }
  }

  private DicomServiceRegistry createServiceRegistry() {
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());
    serviceRegistry.addDicomService(cstoreSCP);
    return serviceRegistry;
  }

  public void setStorageFilePathFormat(String pattern) {
    if (StringUtil.hasText(pattern)) {
      this.filePathFormat = new AttributesFormat(pattern);
      this.regex = Pattern.compile("\\{(.*?)\\}");
    } else {
      this.filePathFormat = null;
      this.regex = null;
    }
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public void setReceiveDelays(int[] receiveDelays) {
    this.receiveDelays = receiveDelays;
  }

  public void setResponseDelays(int[] responseDelays) {
    this.responseDelays = responseDelays;
  }

  public void loadDefaultTransferCapability(URL transferCapabilityFile) {
    Properties p = new Properties();

    try {
      if (transferCapabilityFile != null) {
        try (InputStream in = transferCapabilityFile.openStream()) {
          p.load(in);
        }
      } else {
        p.load(this.getClass().getResourceAsStream("sop-classes.properties"));
      }
    } catch (IOException e) {
      LOGGER.error("Cannot read sop-classes", e);
    }

    for (String cuid : p.stringPropertyNames()) {
      String ts = p.getProperty(cuid);
      TransferCapability tc =
          new TransferCapability(
              null, CLIUtils.toUID(cuid), TransferCapability.Role.SCP, CLIUtils.toUIDs(ts));
      ae.addTransferCapability(tc);
    }
  }

  public ApplicationEntity getApplicationEntity() {
    return ae;
  }

  public Connection getConnection() {
    return conn;
  }

  public Device getDevice() {
    return device;
  }

  public File getStorageDir() {
    return storageDir;
  }

  public DicomProgress getProgress() {
    return progress;
  }
}
