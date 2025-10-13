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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;

/**
 * DICOM Storage Service Class Provider (SCP) for receiving and storing DICOM objects. Supports file
 * path formatting, access control, and progress tracking.
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Nicolas Roduit
 */
public class StoreSCP {

  private static final Logger LOGGER = LoggerFactory.getLogger(StoreSCP.class);

  private static final String TMP_DIR = "tmp";
  private static final String REGEX_PATTERN = "\\{(.*?)\\}";

  private final Device device = new Device("storescp");
  private final ApplicationEntity ae = new ApplicationEntity("*");
  private final Connection conn = new Connection();
  private final Path storageDir;
  private final List<DicomNode> authorizedCallingNodes;
  private final DicomProgress progress;
  private AttributesFormat filePathFormat;
  private Pattern regex;
  private volatile int status = Status.Success;
  private int[] receiveDelays;
  private int[] responseDelays;

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
          if (!isNodeAuthorized(as, rq, rsp)) {
            return;
          }

          sleep(as, receiveDelays);
          try {
            storeFile(as, pc, rq, data, rsp);
          } finally {
            sleep(as, responseDelays);
          }
        }
      };

  /**
   * Creates a StoreSCP with specified storage directory.
   *
   * @param storageDir the base path of storage folder
   */
  public StoreSCP(Path storageDir) {
    this(storageDir, null, null);
  }

  /**
   * Creates a StoreSCP with specified storage directory and authorized calling nodes.
   *
   * @param storageDir the base path of storage folder
   * @param authorizedCallingNodes the list of authorized nodes to call store files
   */
  public StoreSCP(Path storageDir, List<DicomNode> authorizedCallingNodes) {
    this(storageDir, authorizedCallingNodes, null);
  }

  /**
   * Creates a StoreSCP with full configuration options.
   *
   * @param storageDir the base path of storage folder
   * @param authorizedCallingNodes the list of authorized nodes to call store files
   * @param progress the progress tracker for DICOM operations
   */
  public StoreSCP(Path storageDir, List<DicomNode> authorizedCallingNodes, DicomProgress progress) {
    this.storageDir = Objects.requireNonNull(storageDir, "Storage directory cannot be null");
    this.authorizedCallingNodes = authorizedCallingNodes;
    this.progress = progress;

    initializeDevice();
  }

  private void initializeDevice() {
    device.setDimseRQHandler(createServiceRegistry());
    device.addConnection(conn);
    device.addApplicationEntity(ae);
    ae.setAssociationAcceptor(true);
    ae.addConnection(conn);
  }

  private boolean isNodeAuthorized(Association as, Attributes rq, Attributes rsp) {
    if (authorizedCallingNodes == null || authorizedCallingNodes.isEmpty()) {
      return true;
    }

    var sourceNode = DicomNode.buildRemoteDicomNode(as);
    boolean valid =
        authorizedCallingNodes.stream()
            .anyMatch(
                n ->
                    n.getAet().equals(sourceNode.getAet())
                        && (!n.isValidateHostname() || n.equalsHostname(sourceNode.getHostname())));
    if (!valid) {
      rsp.setInt(Tag.Status, VR.US, Status.NotAuthorized);
      LOGGER.error(
          "Refused: not authorized (124H). Source node: {}. SopUID: {}",
          sourceNode,
          rq.getString(Tag.AffectedSOPInstanceUID));
    }
    return valid;
  }

  private void storeFile(
      Association as, PresentationContext pc, Attributes rq, PDVInputStream data, Attributes rsp)
      throws IOException {
    rsp.setInt(Tag.Status, VR.US, status);

    String cuid = rq.getString(Tag.AffectedSOPClassUID);
    String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
    String tsuid = pc.getTransferSyntax();
    Path tempFile = createTempFile(iuid);
    try {
      Attributes fmi = as.createFileMetaInformation(iuid, cuid, tsuid);
      writeToTempFile(as, fmi, data, tempFile);

      String filename = determineFilename(tempFile, fmi, iuid);
      Path finalFile = storageDir.resolve(filename);

      moveToFinalLocation(as, tempFile, finalFile);
      notifyProgress(finalFile);
    } catch (Exception e) {
      cleanupTempFile(tempFile);
      throw new DicomServiceException(Status.ProcessingFailure, e);
    }
  }

  private Path createTempFile(String instanceUid) {
    return storageDir.resolve(TMP_DIR).resolve(instanceUid);
  }

  private void writeToTempFile(Association as, Attributes fmi, PDVInputStream data, Path tempFile)
      throws IOException {
    LOGGER.debug("{}: M-WRITE {}", as, tempFile);
    Files.createDirectories(tempFile.getParent());

    try (DicomOutputStream out = new DicomOutputStream(tempFile.toFile())) {
      out.writeFileMetaInformation(fmi);
      data.copyTo(out);
    }
  }

  private String determineFilename(Path tempFile, Attributes fmi, String instanceUid)
      throws IOException {
    if (filePathFormat == null) {
      return instanceUid;
    }

    Attributes attributes = fmi;
    if (needsAttributeParsing()) {
      attributes = parseAttributes(tempFile);
      attributes.addAll(fmi);
    }

    return filePathFormat.format(attributes);
  }

  private boolean needsAttributeParsing() {
    if (regex == null) {
      return false;
    }

    Matcher matcher = regex.matcher(filePathFormat.toString());
    while (matcher.find()) {
      if (!matcher.group(1).startsWith("0002")) {
        return true;
      }
    }
    return false;
  }

  private Attributes parseAttributes(Path file) throws IOException {
    try (DicomInputStream in = new DicomInputStream(file.toFile())) {
      in.setIncludeBulkData(IncludeBulkData.NO);
      return in.readDatasetUntilPixelData();
    }
  }

  private void moveToFinalLocation(Association as, Path source, Path target) throws IOException {
    LOGGER.info("{}: M-RENAME {} to {}", as, source, target);
    Files.createDirectories(target.getParent());
    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
  }

  private void notifyProgress(Path file) {
    if (progress != null) {
      progress.setProcessedFile(file);
      progress.setAttributes(null);
    }
  }

  private void cleanupTempFile(Path tempFile) {
    try {
      Files.deleteIfExists(tempFile);
    } catch (IOException e) {
      LOGGER.warn("Failed to cleanup temporary file: {}", tempFile, e);
    }
  }

  private void sleep(Association as, int[] delays) {
    if (delays == null) {
      return;
    }

    int delayMs = delays[(as.getNumberOfReceived(Dimse.C_STORE_RQ) - 1) % delays.length];
    if (delayMs > 0) {
      try {
        Thread.sleep(delayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOGGER.debug("Sleep interrupted", e);
      }
    }
  }

  private DicomServiceRegistry createServiceRegistry() {
    var serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());
    serviceRegistry.addDicomService(cstoreSCP);
    return serviceRegistry;
  }

  /**
   * Sets the storage file path format pattern.
   *
   * @param pattern the format pattern for file paths
   */
  public void setStorageFilePathFormat(String pattern) {
    if (StringUtil.hasText(pattern)) {
      this.filePathFormat = new AttributesFormat(pattern);
      this.regex = Pattern.compile(REGEX_PATTERN);
    } else {
      this.filePathFormat = null;
      this.regex = null;
    }
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public void setReceiveDelays(int[] receiveDelays) {
    this.receiveDelays = receiveDelays != null ? receiveDelays.clone() : null;
  }

  public void setResponseDelays(int[] responseDelays) {
    this.responseDelays = responseDelays != null ? responseDelays.clone() : null;
  }

  /**
   * Loads default transfer capabilities from a properties file.
   *
   * @param transferCapabilityFile the URL to the transfer capability file, or null for default
   */
  public void loadDefaultTransferCapability(URL transferCapabilityFile) {
    var properties = new Properties();

    try {
      loadProperties(properties, transferCapabilityFile);
      configureTransferCapabilities(properties);
    } catch (IOException e) {
      LOGGER.error("Cannot read sop-classes", e);
    }
  }

  private void loadProperties(Properties properties, URL transferCapabilityFile)
      throws IOException {
    if (transferCapabilityFile != null) {
      try (InputStream in = transferCapabilityFile.openStream()) {
        properties.load(in);
      }
    } else {
      try (InputStream in = getClass().getResourceAsStream("sop-classes.properties")) {
        properties.load(in);
      }
    }
  }

  private void configureTransferCapabilities(Properties properties) {
    for (String cuid : properties.stringPropertyNames()) {
      String ts = properties.getProperty(cuid);
      var tc =
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

  public Path getStorageDir() {
    return storageDir;
  }

  public DicomProgress getProgress() {
    return progress;
  }
}
