/*
 * Copyright (c) 2017-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.util;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.DicomOutputData;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.DataWriter;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.tool.storescu.RelatedGeneralSOPClasses;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.*;
import org.weasis.dicom.util.ServiceUtil.ProgressStatus;

/**
 * DICOM Store SCU (Service Class User) implementation for streaming data transfer. This class
 * provides functionality to store DICOM objects from streams to a remote SCP.
 */
public class StoreFromStreamSCU {

  private static final Logger LOGGER = LoggerFactory.getLogger(StoreFromStreamSCU.class);

  private static final int MAX_STATUS_LOG_ENTRIES = 3;
  private static final int CLOSE_DELAY_SECONDS = 15;
  private static final int WAIT_SLEEP_MS = 20;
  private static final int MAX_WAIT_LOOPS = 3000; // 1 minute max

  @FunctionalInterface
  public interface RSPHandlerFactory {
    DimseRSPHandler createDimseRSPHandler();
  }

  private final Map<String, Integer> instanceUidsCurrentlyProcessed = new ConcurrentHashMap<>();

  private final ApplicationEntity ae;
  private final Connection remote;
  private final AAssociateRQ rq = new AAssociateRQ();
  private final RelatedGeneralSOPClasses relSOPClasses = new RelatedGeneralSOPClasses();
  private final Device device;
  private final Connection conn;
  private final DicomState state;
  private final AdvancedParams options;
  private final AtomicBoolean countdown = new AtomicBoolean(false);
  private final ScheduledExecutorService closeAssociationExecutor =
      Executors.newSingleThreadScheduledExecutor();
  private final RSPHandlerFactory rspHandlerFactory = this::createResponseHandler;
  private Attributes attrs;
  private boolean relExtNeg;
  private Association as;

  private int lastStatusCode = Integer.MIN_VALUE;
  private int nbStatusLog = 0;
  private int numberOfSuboperations = 0;
  private ScheduledFuture<?> scheduledFuture;

  private final TimerTask closeAssociationTask =
      new TimerTask() {
        @Override
        public void run() {
          close(false);
        }
      };

  public StoreFromStreamSCU(DicomNode callingNode, DicomNode calledNode) throws IOException {
    this(null, callingNode, calledNode, null);
  }

  public StoreFromStreamSCU(AdvancedParams params, DicomNode callingNode, DicomNode calledNode)
      throws IOException {
    this(params, callingNode, calledNode, null);
  }

  /**
   * Creates a new StoreFromStreamSCU instance with the specified parameters.
   *
   * @param params advanced parameters for the connection
   * @param callingNode the calling DICOM node
   * @param calledNode the called DICOM node
   * @param progress optional progress monitor
   * @throws IOException if initialization fails
   */
  public StoreFromStreamSCU(
      AdvancedParams params, DicomNode callingNode, DicomNode calledNode, DicomProgress progress)
      throws IOException {
    Objects.requireNonNull(callingNode, "callingNode cannot be null");
    Objects.requireNonNull(calledNode, "calledNode cannot be null");

    this.options = Objects.requireNonNullElseGet(params, AdvancedParams::new);
    this.state = new DicomState(progress);
    this.device = new Device("storescu");
    this.conn = new Connection();
    device.addConnection(conn);
    this.ae = new ApplicationEntity(callingNode.getAet());
    device.addApplicationEntity(ae);
    ae.addConnection(conn);
    this.remote = new Connection();

    configureAssociation(callingNode, calledNode);
    setAttributes(new Attributes());
  }

  private void configureAssociation(DicomNode callingNode, DicomNode calledNode)
      throws IOException {

    rq.addPresentationContext(
        new PresentationContext(1, UID.Verification, UID.ImplicitVRLittleEndian));

    options.configureConnect(rq, remote, calledNode);
    options.configureBind(ae, conn, callingNode);
    options.configure(conn);
    options.configureTLS(conn, remote);
  }

  private DimseRSPHandler createResponseHandler() {
    return new DimseRSPHandler(as.nextMessageID()) {
      @Override
      public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
        super.onDimseRSP(as, cmd, data);
        handleCStoreResponse(cmd);
        updateProgress(cmd);
      }
    };
  }

  /**
   * Handles C-STORE response and updates progress status.
   *
   * @see <a
   *     href="https://github.com/dcm4che/dcm4che/blob/master/dcm4che-net/src/main/java/org/dcm4che3/net/Status.java">ERROR
   *     CODE STATUS</a>
   */
  private void handleCStoreResponse(Attributes cmd) {
    int status = cmd.getInt(Tag.Status, -1);
    state.setStatus(status);

    ProgressStatus progressStatus = determineProgressStatus(status);
    logStatusIfNeeded(status, cmd);

    ServiceUtil.notifyProgression(state.getProgress(), cmd, progressStatus, numberOfSuboperations);
  }

  private ProgressStatus determineProgressStatus(int status) {
    return switch (status) {
      case Status.Success -> ProgressStatus.COMPLETED;
      case Status.CoercionOfDataElements,
          Status.ElementsDiscarded,
          Status.DataSetDoesNotMatchSOPClassWarning ->
          ProgressStatus.WARNING;
      default -> ProgressStatus.FAILED;
    };
  }

  private void logStatusIfNeeded(int status, Attributes cmd) {
    if (lastStatusCode == status || nbStatusLog >= MAX_STATUS_LOG_ENTRIES) {
      return;
    }

    nbStatusLog++;
    lastStatusCode = status;
    String hexStatus = TagUtils.shortToHexString(status);

    if (isWarningStatus(status)) {
      logWarningStatus(hexStatus, cmd);
    } else {
      logErrorStatus(hexStatus, cmd);
    }
  }

  private boolean isWarningStatus(int status) {
    return status == Status.CoercionOfDataElements
        || status == Status.ElementsDiscarded
        || status == Status.DataSetDoesNotMatchSOPClassWarning;
  }

  private void logWarningStatus(String hexStatus, Attributes cmd) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.warn("Received C-STORE-RSP with Status {}H{}", hexStatus, "\r\n" + cmd);
    } else {
      LOGGER.warn("Received C-STORE-RSP with Status {}H", hexStatus);
    }
  }

  private void logErrorStatus(String hexStatus, Attributes cmd) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.error("Received C-STORE-RSP with Status {}H{}", hexStatus, "\r\n" + cmd);
    } else {
      LOGGER.error("Received C-STORE-RSP with Status {}H", hexStatus);
    }
  }

  private void updateProgress(Attributes cmd) {
    DicomProgress progress = state.getProgress();
    if (progress != null) {
      progress.setAttributes(cmd);
    }
  }

  public void cstore(String cuid, String iuid, int priority, DataWriter dataWriter, String tsuid)
      throws IOException, InterruptedException {
    if (as == null) {
      throw new IllegalStateException("Association is null!");
    }
    as.cstore(cuid, iuid, priority, dataWriter, tsuid, rspHandlerFactory.createDimseRSPHandler());
  }

  public DicomNode getCallingNode() {
    return new DicomNode(ae.getAETitle(), conn.getHostname(), conn.getPort());
  }

  public DicomNode getCalledNode() {
    return new DicomNode(rq.getCalledAET(), remote.getHostname(), remote.getPort());
  }

  public DicomNode getLocalDicomNode() {
    return as != null ? DicomNode.buildLocalDicomNode(as) : null;
  }

  public DicomNode getRemoteDicomNode() {
    return as != null ? DicomNode.buildRemoteDicomNode(as) : null;
  }

  public String selectTransferSyntax(String cuid, String tsuid) {
    return selectTransferSyntax(as, cuid, tsuid);
  }

  public Device getDevice() {
    return device;
  }

  public AAssociateRQ getAAssociateRQ() {
    return rq;
  }

  public Connection getRemoteConnection() {
    return remote;
  }

  public Attributes getAttributes() {
    return attrs;
  }

  public void setAttributes(Attributes attrs) {
    this.attrs = attrs;
  }

  public void enableSOPClassRelationshipExtNeg(boolean enable) {
    relExtNeg = enable;
  }

  public AdvancedParams getOptions() {
    return options;
  }

  public boolean hasAssociation() {
    return as != null;
  }

  public boolean isReadyForDataTransfer() {
    return as != null && as.isReadyForDataTransfer();
  }

  public Set<String> getTransferSyntaxesFor(String cuid) {
    return as != null ? as.getTransferSyntaxesFor(cuid) : Collections.emptySet();
  }

  public int getNumberOfSuboperations() {
    return numberOfSuboperations;
  }

  public void setNumberOfSuboperations(int numberOfSuboperations) {
    this.numberOfSuboperations = numberOfSuboperations;
  }

  public DicomState getState() {
    return state;
  }

  public RSPHandlerFactory getRspHandlerFactory() {
    return rspHandlerFactory;
  }

  /**
   * Opens a DICOM association with the remote node.
   *
   * @throws IOException if connection fails
   */
  public synchronized void open() throws IOException {
    countdown.set(false);
    try {
      as = ae.connect(remote, rq);
    } catch (Exception e) {
      handleConnectionException(e);
    }
    if (as == null) {
      throw new IOException("Cannot connect to the remote destination");
    }
  }

  private void handleConnectionException(Exception e) {
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    as = null;
    LOGGER.trace("Connecting to remote destination", e);
  }

  /**
   * Closes the DICOM association.
   *
   * @param force true to force immediate closure
   */
  public synchronized void close(boolean force) {
    if (force || countdown.compareAndSet(true, false)) {
      closeAssociation();
    }
  }

  private void closeAssociation() {
    if (as != null) {
      try {
        LOGGER.info("Closing DICOM association");
        if (as.isReadyForDataTransfer()) {
          as.release();
        }
        as.waitForSocketClose();
      } catch (Exception e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        LOGGER.trace("Cannot close association", e);
      }
      as = null;
    }
  }

  /**
   * Adds presentation contexts for the given SOP Class and Transfer Syntax.
   *
   * @param cuid SOP Class UID
   * @param tsuid Transfer Syntax UID
   * @return true if successfully added or already present
   */
  public boolean addData(String cuid, String tsuid) {
    countdown.set(false);
    if (cuid == null || tsuid == null) {
      return false;
    }

    if (rq.containsPresentationContextFor(cuid, tsuid)) {
      return true;
    }

    addPresentationContextsForSopClass(cuid, tsuid);
    return true;
  }

  private void addPresentationContextsForSopClass(String cuid, String tsuid) {
    if (!rq.containsPresentationContextFor(cuid)) {
      addRelatedNegotiationIfEnabled(cuid);
      addStandardTransferSyntaxes(cuid, tsuid);
    }
    addPresentationContext(cuid, tsuid);
  }

  private void addRelatedNegotiationIfEnabled(String cuid) {
    if (relExtNeg) {
      rq.addCommonExtendedNegotiation(relSOPClasses.getCommonExtendedNegotiation(cuid));
    }
  }

  private void addStandardTransferSyntaxes(String cuid, String tsuid) {
    if (!UID.ExplicitVRLittleEndian.equals(tsuid)) {
      addPresentationContext(cuid, UID.ExplicitVRLittleEndian);
    }
    if (!UID.ImplicitVRLittleEndian.equals(tsuid)) {
      addPresentationContext(cuid, UID.ImplicitVRLittleEndian);
    }
  }

  private void addPresentationContext(String cuid, String tsuid) {
    rq.addPresentationContext(
        new PresentationContext(rq.getNumberOfPresentationContexts() * 2 + 1, cuid, tsuid));
  }

  /** Triggers the scheduled association closure. */
  public synchronized void triggerCloseExecutor() {
    if (shouldScheduleClose()) {
      scheduledFuture =
          closeAssociationExecutor.schedule(
              closeAssociationTask, CLOSE_DELAY_SECONDS, TimeUnit.SECONDS);
    }
  }

  private boolean shouldScheduleClose() {
    return (scheduledFuture == null || scheduledFuture.isDone())
        && countdown.compareAndSet(false, true);
  }

  /**
   * Selects the best available transfer syntax for the given SOP Class.
   *
   * @param as the association
   * @param cuid SOP Class UID
   * @param filets preferred transfer syntax
   * @return the selected transfer syntax
   */
  public static String selectTransferSyntax(Association as, String cuid, String filets) {
    Set<String> tss = as.getTransferSyntaxesFor(cuid);
    if (tss.contains(filets)) {
      return filets;
    }

    if (tss.contains(UID.ExplicitVRLittleEndian)) {
      return UID.ExplicitVRLittleEndian;
    }

    return UID.ImplicitVRLittleEndian;
  }

  /**
   * Prepares the association for data transfer with the specified parameters.
   *
   * @param service the device operation service
   * @param iuid instance UID
   * @param cuid SOP class UID
   * @param dstTsuid destination transfer syntax UID
   * @throws IOException if preparation fails
   */
  public void prepareTransfer(DeviceOpService service, String iuid, String cuid, String dstTsuid)
      throws IOException {
    synchronized (this) {
      if (hasAssociation()) {
        handleExistingAssociation(cuid, dstTsuid);
      } else {
        handleNewAssociation(service, cuid, dstTsuid);
      }
      addIUIDProcessed(iuid);
    }
  }

  private void handleExistingAssociation(String cuid, String dstTsuid) throws IOException {
    checkNewSopClassUID(cuid, dstTsuid);

    addPresentationContexts(cuid, dstTsuid);

    if (!isReadyForDataTransfer()) {
      LOGGER.debug("prepareTransfer: as not ready for data transfer, reopen");
      open();
    }
  }

  private void handleNewAssociation(DeviceOpService service, String cuid, String dstTsuid)
      throws IOException {
    service.start();
    addPresentationContexts(cuid, dstTsuid);

    if (!UID.ExplicitVRLittleEndian.equals(dstTsuid)) {
      addData(cuid, UID.ExplicitVRLittleEndian);
    }
    LOGGER.debug("prepareTransfer: connecting to the remote destination");
    open();
  }

  private void addPresentationContexts(String cuid, String dstTsuid) {
    addData(cuid, dstTsuid);
    if (DicomOutputData.isAdaptableSyntax(dstTsuid)) {
      addData(cuid, UID.JPEGLosslessSV1);
    }
  }

  private void checkNewSopClassUID(String cuid, String dstTsuid) {
    Set<String> tss = getTransferSyntaxesFor(cuid);
    if (!tss.contains(dstTsuid)) {
      handleNewTransferSyntax(cuid, dstTsuid);
    }
  }

  private void handleNewTransferSyntax(String cuid, String dstTsuid) {
    LOGGER.debug("prepareTransfer: New output transfer syntax {}: closing streamSCU", dstTsuid);
    countdown.set(false);
    waitForPendingTransfers();

    LOGGER.info(
        "prepareTransfer: Close association to handle dynamically new SOPClassUID: {}", cuid);
    close(true);
  }

  private void waitForPendingTransfers() {
    int loop = 0;
    while (!instanceUidsCurrentlyProcessed.isEmpty() && loop < MAX_WAIT_LOOPS) {
      try {
        LOGGER.debug(
            "prepareTransfer: StreamSCU has some IUID to process: waiting {} ms", WAIT_SLEEP_MS);
        TimeUnit.MILLISECONDS.sleep(WAIT_SLEEP_MS);
        loop++;
      } catch (InterruptedException e) {
        LOGGER.error("prepareTransfer: InterruptedException {}", e.getMessage());
        Thread.currentThread().interrupt();
        break;
      }
    }

    if (loop >= MAX_WAIT_LOOPS) {
      LOGGER.warn("prepareTransfer: StreamSCU timeout reached");
      instanceUidsCurrentlyProcessed.clear();
    } else {
      LOGGER.debug("prepareTransfer: StreamSCU has no more IUID to process: stop waiting");
    }
  }

  public void removeIUIDProcessed(String iuid) {
    instanceUidsCurrentlyProcessed.compute(iuid, (k, v) -> v == null || v <= 1 ? null : v - 1);
  }

  private void addIUIDProcessed(String iuid) {
    instanceUidsCurrentlyProcessed.merge(iuid, 1, Integer::sum);
  }
}
