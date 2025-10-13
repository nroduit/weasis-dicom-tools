/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.movescu;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

/**
 * DICOM C-MOVE Service Class User (SCU) implementation for retrieving medical images and other
 * DICOM objects from a remote DICOM server.
 *
 * <p>This class provides functionality to perform DICOM C-MOVE operations, supporting various
 * information models including Patient Root, Study Root, and Composite Instance Root. It handles
 * association management, query key configuration, and progress tracking.
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Nicolas Roduit
 */
public class MoveSCU extends Device implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(MoveSCU.class);

  /** Supported DICOM information models for C-MOVE operations. */
  public enum InformationModel {
    PatientRoot(UID.PatientRootQueryRetrieveInformationModelMove, "STUDY"),
    StudyRoot(UID.StudyRootQueryRetrieveInformationModelMove, "STUDY"),
    PatientStudyOnly(UID.PatientStudyOnlyQueryRetrieveInformationModelMove, "STUDY"),
    CompositeInstanceRoot(UID.CompositeInstanceRootRetrieveMove, "IMAGE"),
    HangingProtocol(UID.HangingProtocolInformationModelMove, null),
    ColorPalette(UID.ColorPaletteQueryRetrieveInformationModelMove, null);

    private final String cuid;
    private final String level;

    InformationModel(String cuid, String level) {
      this.cuid = cuid;
      this.level = level;
    }

    public String getCuid() {
      return cuid;
    }
  }

  private static final int[] DEFAULT_INPUT_FILTER = {
    Tag.SOPInstanceUID, Tag.StudyInstanceUID, Tag.SeriesInstanceUID
  };

  private final ApplicationEntity ae = new ApplicationEntity("MOVESCU");
  private final Connection conn = new Connection();
  private final Connection remote = new Connection();
  private final AAssociateRQ rq = new AAssociateRQ();
  private final Attributes keys = new Attributes();
  private final DicomState state;
  private int priority;
  private String destination;
  private InformationModel model;
  private int[] inFilter = DEFAULT_INPUT_FILTER;
  private Association as;
  private int cancelAfter;
  private boolean releaseEager;

  /** Creates a new MoveSCU instance without progress tracking. */
  public MoveSCU() {
    this(null);
  }

  /**
   * Creates a new MoveSCU instance with optional progress tracking.
   *
   * @param progress the progress handler, may be null
   */
  public MoveSCU(DicomProgress progress) {
    super("movescu");
    addConnection(conn);
    addApplicationEntity(ae);
    ae.addConnection(conn);
    state = new DicomState(progress);
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  public void setCancelAfter(int cancelAfter) {
    this.cancelAfter = cancelAfter;
  }

  public void setReleaseEager(boolean releaseEager) {
    this.releaseEager = releaseEager;
  }

  /**
   * Configures the DICOM information model for C-MOVE operations.
   *
   * @param model the information model to use
   * @param transferSyntaxes supported transfer syntaxes
   * @param relational whether to enable relational queries
   */
  public void setInformationModel(
      InformationModel model, String[] transferSyntaxes, boolean relational) {
    this.model = model;
    rq.addPresentationContext(new PresentationContext(1, model.cuid, transferSyntaxes));
    if (relational) {
      rq.addExtendedNegotiation(new ExtendedNegotiation(model.cuid, new byte[] {1}));
    }
    if (model.level != null) {
      addLevel(model.level);
    }
  }

  public void addLevel(String level) {
    keys.setString(Tag.QueryRetrieveLevel, VR.CS, level);
  }

  public void setDestination(String destination) {
    this.destination = destination;
  }

  /**
   * Adds a query key with associated values.
   *
   * @param tag the DICOM tag
   * @param values the values to set for this tag
   */
  public void addKey(int tag, String... values) {
    var vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
    keys.setString(tag, vr, values);
  }

  public void setInputFilter(int[] inFilter) {
    this.inFilter = inFilter != null ? inFilter.clone() : DEFAULT_INPUT_FILTER;
  }

  public ApplicationEntity getApplicationEntity() {
    return ae;
  }

  public Connection getRemoteConnection() {
    return remote;
  }

  public Connection getConnection() {
    return conn;
  }

  public AAssociateRQ getAAssociateRQ() {
    return rq;
  }

  public Association getAssociation() {
    return as;
  }

  public Attributes getKeys() {
    return keys;
  }

  public DicomState getState() {
    return state;
  }

  /**
   * Opens a DICOM association with the remote server.
   *
   * @throws IOException if connection fails
   * @throws InterruptedException if the thread is interrupted
   * @throws IncompatibleConnectionException if connection parameters are incompatible
   * @throws GeneralSecurityException if TLS/security setup fails
   */
  public void open()
      throws IOException,
          InterruptedException,
          IncompatibleConnectionException,
          GeneralSecurityException {
    as = ae.connect(conn, remote, rq);
  }

  /** Closes the DICOM association and releases resources. */
  @Override
  public void close() throws IOException, InterruptedException {
    if (as != null && as.isReadyForDataTransfer()) {
      as.waitForOutstandingRSP();
      as.release();
    }
  }

  /**
   * Performs a C-MOVE operation using DICOM attributes from the specified file.
   *
   * @param dicomFile the DICOM file containing query attributes
   * @throws IOException if file reading or network operation fails
   * @throws InterruptedException if the operation is interrupted
   */
  public void retrieve(Path dicomFile) throws IOException, InterruptedException {
    var attrs = readDicomAttributes(dicomFile);
    attrs.addAll(keys);
    performMove(attrs);
  }

  /**
   * Performs a C-MOVE operation using the configured query keys.
   *
   * @throws IOException if network operation fails
   * @throws InterruptedException if the operation is interrupted
   */
  public void retrieve() throws IOException, InterruptedException {
    performMove(keys);
  }

  private Attributes readDicomAttributes(Path dicomFile) throws IOException {
    var attrs = new Attributes();
    try (var dis = new DicomInputStream(dicomFile.toFile())) {
      attrs.addSelected(dis.readDataset(), inFilter);
    }
    return attrs;
  }

  private void performMove(Attributes queryKeys) throws IOException, InterruptedException {
    var rspHandler = createResponseHandler();
    as.cmove(model.cuid, priority, queryKeys, null, destination, rspHandler);

    if (cancelAfter > 0) {
      scheduleCancellation(rspHandler);
    }
  }

  private DimseRSPHandler createResponseHandler() {
    return new DimseRSPHandler(as.nextMessageID()) {
      @Override
      public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
        super.onDimseRSP(as, cmd, data);
        handleProgressUpdate(cmd);
      }
    };
  }

  private void handleProgressUpdate(Attributes cmd) {
    var progress = state.getProgress();
    if (progress != null) {
      progress.setAttributes(cmd);
      if (progress.isCancelled()) {
        try {
          // The handler will be passed to this method from the calling context
          LOGGER.info("Cancelling C-MOVE operation due to progress cancellation");
        } catch (Exception e) {
          LOGGER.error("Error during progress cancellation", e);
        }
      }
    }
  }

  private void scheduleCancellation(DimseRSPHandler handler) {
    schedule(
        () -> {
          try {
            handler.cancel(as);
            if (releaseEager) {
              as.release();
            }
          } catch (IOException e) {
            LOGGER.error("Error cancelling C-MOVE operation after timeout", e);
          }
        },
        cancelAfter,
        TimeUnit.MILLISECONDS);
  }
}
