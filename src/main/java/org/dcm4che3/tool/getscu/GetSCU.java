/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.getscu;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4che3.util.SafeClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class GetSCU implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetSCU.class);

  public enum InformationModel {
    PatientRoot(UID.PatientRootQueryRetrieveInformationModelGet, "STUDY"),
    StudyRoot(UID.StudyRootQueryRetrieveInformationModelGet, "STUDY"),
    PatientStudyOnly(UID.PatientStudyOnlyQueryRetrieveInformationModelGet, "STUDY"),
    CompositeInstanceRoot(UID.CompositeInstanceRootRetrieveGet, "IMAGE"),
    WithoutBulkData(UID.CompositeInstanceRetrieveWithoutBulkDataGet, null),
    HangingProtocol(UID.HangingProtocolInformationModelGet, null),
    ColorPalette(UID.ColorPaletteQueryRetrieveInformationModelGet, null);

    private final String cuid;
    final String level;

    InformationModel(String cuid, String level) {
      this.cuid = cuid;
      this.level = level;
    }

    public String getCuid() {
      return cuid;
    }
  }

  private static final int[] DEF_IN_FILTER = {
    Tag.SOPInstanceUID, Tag.StudyInstanceUID, Tag.SeriesInstanceUID
  };
  private static final String TMP_DIR = "tmp";

  private final Device device = new Device("getscu");
  private final ApplicationEntity ae;
  private final Connection conn = new Connection();
  private final Connection remote = new Connection();
  private final AAssociateRQ rq = new AAssociateRQ();
  private int priority;
  private InformationModel model;
  private File storageDir;
  private final Attributes keys = new Attributes();
  private int[] inFilter = DEF_IN_FILTER;
  private Association as;
  private int cancelAfter;
  private final DicomState state;
  private DimseRSPHandler rspHandler;
  private long totalSize = 0;

  private final BasicCStoreSCP storageSCP =
      new BasicCStoreSCP("*") {

        @Override
        protected void store(
            Association as,
            PresentationContext pc,
            Attributes rq,
            PDVInputStream data,
            Attributes rsp)
            throws IOException {
          if (storageDir == null) {
            return;
          }

          String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
          String cuid = rq.getString(Tag.AffectedSOPClassUID);
          String tsuid = pc.getTransferSyntax();
          File file = new File(storageDir, TMP_DIR + File.separator + iuid);
          try {
            storeTo(as, as.createFileMetaInformation(iuid, cuid, tsuid), data, file);
            totalSize += file.length();
            File rename = new File(storageDir, iuid);
            renameTo(as, file, rename);
            DicomProgress p = state.getProgress();
            if (p != null) {
              p.setProcessedFile(rename);
            }
          } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
          }
          updateProgress(as, null);
        }
      };

  public GetSCU() throws IOException {
    this(null);
  }

  public GetSCU(DicomProgress progress) throws IOException {
    ae = new ApplicationEntity("GETSCU");
    device.addConnection(conn);
    device.addApplicationEntity(ae);
    ae.addConnection(conn);
    device.setDimseRQHandler(createServiceRegistry());
    state = new DicomState(progress);
  }

  public ApplicationEntity getApplicationEntity() {
    return ae;
  }

  public Connection getRemoteConnection() {
    return remote;
  }

  public AAssociateRQ getAAssociateRQ() {
    return rq;
  }

  public Association getAssociation() {
    return as;
  }

  public Device getDevice() {
    return device;
  }

  public Attributes getKeys() {
    return keys;
  }

  public static void storeTo(Association as, Attributes fmi, PDVInputStream data, File file)
      throws IOException {
    LOGGER.debug("{}: M-WRITE {}", as, file);
    file.getParentFile().mkdirs();
    DicomOutputStream out = new DicomOutputStream(file);
    try {
      out.writeFileMetaInformation(fmi);
      data.copyTo(out);
    } finally {
      SafeClose.close(out);
    }
  }

  private static void renameTo(Association as, File from, File dest) throws IOException {
    LOGGER.info("{}: M-RENAME {} to {}", as, from, dest);
    FileUtil.prepareToWriteFile(dest);
    if (!from.renameTo(dest)) throw new IOException("Failed to rename " + from + " to " + dest);
  }

  private DicomServiceRegistry createServiceRegistry() {
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(storageSCP);
    return serviceRegistry;
  }

  public void setStorageDirectory(File storageDir) {
    if (storageDir != null) {
      if (storageDir.mkdirs()) {
        System.out.println("M-WRITE " + storageDir);
      }
    }
    this.storageDir = storageDir;
  }

  public final void setPriority(int priority) {
    this.priority = priority;
  }

  public void setCancelAfter(int cancelAfter) {
    this.cancelAfter = cancelAfter;
  }

  public final void setInformationModel(InformationModel model, String[] tss, boolean relational) {
    this.model = model;
    rq.addPresentationContext(new PresentationContext(1, model.cuid, tss));
    if (relational) {
      rq.addExtendedNegotiation(new ExtendedNegotiation(model.cuid, new byte[] {1}));
    }
    if (model.level != null) {
      addLevel(model.level);
    }
  }

  public void addLevel(String s) {
    keys.setString(Tag.QueryRetrieveLevel, VR.CS, s);
  }

  public void addKey(int tag, String... ss) {
    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
    keys.setString(tag, vr, ss);
  }

  public final void setInputFilter(int[] inFilter) {
    this.inFilter = inFilter;
  }

  public void addOfferedStorageSOPClass(String cuid, String... tsuids) {
    if (!rq.containsPresentationContextFor(cuid)) {
      rq.addRoleSelection(new RoleSelection(cuid, false, true));
    }
    rq.addPresentationContext(
        new PresentationContext(2 * rq.getNumberOfPresentationContexts() + 1, cuid, tsuids));
  }

  public void open()
      throws IOException, InterruptedException, IncompatibleConnectionException,
          GeneralSecurityException {
    as = ae.connect(conn, remote, rq);
  }

  @Override
  public void close() throws IOException, InterruptedException {
    if (as != null && as.isReadyForDataTransfer()) {
      as.waitForOutstandingRSP();
      as.release();
    }
  }

  public void retrieve(File f) throws IOException, InterruptedException {
    Attributes attrs = new Attributes();
    try (DicomInputStream dis = new DicomInputStream(f)) {
      attrs.addSelected(dis.readDataset(-1, -1), inFilter);
    }
    attrs.addAll(keys);
    retrieve(attrs);
  }

  public void retrieve() throws IOException, InterruptedException {
    retrieve(keys);
  }

  private void retrieve(Attributes keys) throws IOException, InterruptedException {
    final DimseRSPHandler rspHandler =
        new DimseRSPHandler(as.nextMessageID()) {

          @Override
          public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
            super.onDimseRSP(as, cmd, data);
            updateProgress(as, cmd);
          }
        };

    retrieve(keys, rspHandler);
    if (cancelAfter > 0) {
      device.schedule(
          () -> {
            try {
              rspHandler.cancel(as);
            } catch (IOException e) {
              LOGGER.error("Cancel C-GET", e);
            }
          },
          cancelAfter,
          TimeUnit.MILLISECONDS);
    }
  }

  public void retrieve(DimseRSPHandler rspHandler) throws IOException, InterruptedException {
    retrieve(keys, rspHandler);
  }

  private void retrieve(Attributes keys, DimseRSPHandler rspHandler)
      throws IOException, InterruptedException {
    this.rspHandler = rspHandler;
    as.cget(model.getCuid(), priority, keys, null, rspHandler);
  }

  public Connection getConnection() {
    return conn;
  }

  public DicomState getState() {
    return state;
  }

  public long getTotalSize() {
    return totalSize;
  }

  public void stop() {
    try {
      close();
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
    ServiceUtil.shutdownService((ExecutorService) device.getExecutor());
    ServiceUtil.shutdownService(device.getScheduledExecutor());
  }

  private void updateProgress(Association as, Attributes cmd) {
    DicomProgress p = state.getProgress();
    if (p != null) {
      p.setAttributes(cmd);
      if (p.isCancel() && rspHandler != null) {
        try {
          rspHandler.cancel(as);
        } catch (IOException e) {
          LOGGER.error("Cancel C-GET", e);
        }
      }
    }
  }
}
