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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;

/**
 * A Service Class User (SCU) implementation for DICOM C-GET operations.
 *
 * <p>Supports retrieval of DICOM objects using Query/Retrieve service classes. Retrieved objects
 * are stored to a local directory with progress tracking and cancellation support.
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Nicolas Roduit
 */
public class GetSCU implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetSCU.class);

  /** Supported DICOM information models for C-GET operations. */
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
  private Path storageDir;
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

          var iuid = rq.getString(Tag.AffectedSOPInstanceUID);
          var cuid = rq.getString(Tag.AffectedSOPClassUID);
          var tsuid = pc.getTransferSyntax();

          var tempPath = storageDir.resolve(TMP_DIR).resolve(iuid);
          var finalPath = storageDir.resolve(iuid);
          try {
            var fmi = as.createFileMetaInformation(iuid, cuid, tsuid);
            storeTo(as, fmi, data, tempPath);
            totalSize += Files.size(tempPath);
            renameTo(as, tempPath, finalPath);

            var progress = state.getProgress();
            if (progress != null) {
              progress.setProcessedFile(finalPath);
            }
          } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
          }
          updateProgress(as, null);
        }
      };

  /** Creates a new GetSCU without progress tracking. */
  public GetSCU() {
    this(null);
  }

  /**
   * Creates a new GetSCU with optional progress tracking.
   *
   * @param progress the progress handler, may be null
   */
  public GetSCU(DicomProgress progress) {
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

  /** Stores DICOM data to the specified path. */
  public static void storeTo(Association as, Attributes fmi, PDVInputStream data, Path path)
      throws IOException {
    LOGGER.debug("{}: M-WRITE {}", as, path);
    Files.createDirectories(path.getParent());

    try (var out = new DicomOutputStream(path.toFile())) {
      out.writeFileMetaInformation(fmi);
      data.copyTo(out);
    }
  }

  private static void renameTo(Association as, Path from, Path dest) throws IOException {
    LOGGER.info("{}: M-RENAME {} to {}", as, from, dest);
    FileUtil.prepareToWriteFile(dest);
    Files.move(from, dest, StandardCopyOption.REPLACE_EXISTING);
  }

  private DicomServiceRegistry createServiceRegistry() {
    var serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(storageSCP);
    return serviceRegistry;
  }

  /** Sets the directory where retrieved DICOM files will be stored. */
  public void setStorageDirectory(Path storageDir) {
    if (storageDir != null) {
      try {
        Files.createDirectories(storageDir);
        System.out.println("M-WRITE " + storageDir);
      } catch (IOException e) {
        LOGGER.warn("Failed to create storage directory: {}", storageDir, e);
      }
    }
    this.storageDir = storageDir;
  }

  /**
   * @deprecated Use {@link #setStorageDirectory(Path)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public void setStorageDirectory(java.io.File storageDir) {
    setStorageDirectory(storageDir != null ? storageDir.toPath() : null);
  }

  public final void setPriority(int priority) {
    this.priority = priority;
  }

  public void setCancelAfter(int cancelAfter) {
    this.cancelAfter = cancelAfter;
  }

  /** Configures the DICOM information model for C-GET operations. */
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
    var vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
    keys.setString(tag, vr, ss);
  }

  public final void setInputFilter(int[] inFilter) {
    this.inFilter = inFilter;
  }

  /** Adds an offered storage SOP class for the association. */
  public void addOfferedStorageSOPClass(String cuid, String... tsuids) {
    if (!rq.containsPresentationContextFor(cuid)) {
      rq.addRoleSelection(new RoleSelection(cuid, false, true));
    }
    rq.addPresentationContext(
        new PresentationContext(2 * rq.getNumberOfPresentationContexts() + 1, cuid, tsuids));
  }

  /** Opens the association to the remote DICOM node. */
  public void open()
      throws IOException,
          InterruptedException,
          IncompatibleConnectionException,
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

  /** Retrieves DICOM objects based on keys extracted from the specified file. */
  public void retrieve(Path filePath) throws IOException, InterruptedException {
    var attrs = new Attributes();
    try (var dis = new DicomInputStream(Files.newInputStream(filePath))) {
      attrs.addSelected(dis.readDataset(), inFilter);
    }
    attrs.addAll(keys);
    retrieve(attrs);
  }

  /**
   * @deprecated Use {@link #retrieve(Path)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public void retrieve(java.io.File f) throws IOException, InterruptedException {
    retrieve(f.toPath());
  }

  /** Retrieves DICOM objects using the configured query keys. */
  public void retrieve() throws IOException, InterruptedException {
    retrieve(keys);
  }

  private void retrieve(Attributes keys) throws IOException, InterruptedException {
    var rspHandler = createResponseHandler();
    retrieve(keys, rspHandler);
    scheduleCancellationIfNeeded(rspHandler);
  }

  private DimseRSPHandler createResponseHandler() {
    return new DimseRSPHandler(as.nextMessageID()) {
      @Override
      public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
        super.onDimseRSP(as, cmd, data);
        updateProgress(as, cmd);
      }
    };
  }

  private void scheduleCancellationIfNeeded(DimseRSPHandler rspHandler) {
    if (cancelAfter > 0) {
      device.schedule(() -> cancelRetrieve(rspHandler), cancelAfter, TimeUnit.MILLISECONDS);
    }
  }

  private void cancelRetrieve(DimseRSPHandler rspHandler) {
    try {
      rspHandler.cancel(as);
    } catch (IOException e) {
      LOGGER.error("Cancel C-GET", e);
    }
  }

  /** Retrieves DICOM objects using a custom response handler. */
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

  /** Stops the GetSCU and releases all resources. */
  public void stop() {
    try {
      close();
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.error("Stop GetSCU", e);
    }
    ServiceUtil.shutdownService((ExecutorService) device.getExecutor());
    ServiceUtil.shutdownService(device.getScheduledExecutor());
  }

  private void updateProgress(Association as, Attributes cmd) {
    var progress = state.getProgress();
    if (progress != null) {
      progress.setAttributes(cmd);
      if (progress.isCancelled() && rspHandler != null) {
        cancelRetrieve(rspHandler);
      }
    }
  }
}
