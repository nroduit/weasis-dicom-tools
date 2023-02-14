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

public class StoreFromStreamSCU {

  private static final Logger LOGGER = LoggerFactory.getLogger(StoreFromStreamSCU.class);

  @FunctionalInterface
  public interface RSPHandlerFactory {
    DimseRSPHandler createDimseRSPHandler();
  }

  // Map which corresponds to the instances UIDs currently processed: the value corresponds to the
  // number of occurence
  // of this uid currently processed
  private final Map<String, Integer> instanceUidsCurrentlyProcessed = new ConcurrentHashMap<>();

  private final ApplicationEntity ae;
  private final Connection remote;
  private final AAssociateRQ rq = new AAssociateRQ();
  public final RelatedGeneralSOPClasses relSOPClasses = new RelatedGeneralSOPClasses();
  private Attributes attrs;
  private boolean relExtNeg;
  private Association as;

  private final Device device;
  private final Connection conn;
  private int lastStatusCode = Integer.MIN_VALUE;
  private int nbStatusLog = 0;
  private int numberOfSuboperations = 0;
  private final DicomState state;
  private final AdvancedParams options;
  private final AtomicBoolean countdown = new AtomicBoolean(false);

  private final TimerTask closeAssociationTask =
      new TimerTask() {
        public void run() {
          close(false);
        }
      };
  private final ScheduledExecutorService closeAssociationExecutor =
      Executors.newSingleThreadScheduledExecutor();
  private ScheduledFuture<?> scheduledFuture;

  private final RSPHandlerFactory rspHandlerFactory =
      () ->
          new DimseRSPHandler(as.nextMessageID()) {

            @Override
            public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
              super.onDimseRSP(as, cmd, data);
              onCStoreRSP(cmd);

              DicomProgress progress = state.getProgress();
              if (progress != null) {
                progress.setAttributes(cmd);
              }
            }

            /**
             * @see <a href="ERROR CODE
             *     STATUS">https://github.com/dcm4che/dcm4che/blob/master/dcm4che-net/src/main/java/org/dcm4che3/net/Status.java</a>
             */
            private void onCStoreRSP(Attributes cmd) {
              int status = cmd.getInt(Tag.Status, -1);
              state.setStatus(status);
              ProgressStatus ps;

              switch (status) {
                case Status.Success:
                  ps = ProgressStatus.COMPLETED;
                  break;
                case Status.CoercionOfDataElements:
                case Status.ElementsDiscarded:

                case Status.DataSetDoesNotMatchSOPClassWarning:
                  ps = ProgressStatus.WARNING;
                  if (lastStatusCode != status && nbStatusLog < 3) {
                    nbStatusLog++;
                    lastStatusCode = status;
                    if (LOGGER.isDebugEnabled()) {
                      LOGGER.warn(
                          "Received C-STORE-RSP with Status {}H{}",
                          TagUtils.shortToHexString(status),
                          "\r\n" + cmd);
                    } else {
                      LOGGER.warn(
                          "Received C-STORE-RSP with Status {}H",
                          TagUtils.shortToHexString(status));
                    }
                  }
                  break;

                default:
                  ps = ProgressStatus.FAILED;
                  if (lastStatusCode != status && nbStatusLog < 3) {
                    nbStatusLog++;
                    lastStatusCode = status;
                    if (LOGGER.isDebugEnabled()) {
                      LOGGER.error(
                          "Received C-STORE-RSP with Status {}H{}",
                          TagUtils.shortToHexString(status),
                          "\r\n" + cmd);
                    } else {
                      LOGGER.error(
                          "Received C-STORE-RSP with Status {}H",
                          TagUtils.shortToHexString(status));
                    }
                  }
              }
              ServiceUtil.notifyProgession(state.getProgress(), cmd, ps, numberOfSuboperations);
            }
          };

  public StoreFromStreamSCU(DicomNode callingNode, DicomNode calledNode) throws IOException {
    this(null, callingNode, calledNode, null);
  }

  public StoreFromStreamSCU(AdvancedParams params, DicomNode callingNode, DicomNode calledNode)
      throws IOException {
    this(params, callingNode, calledNode, null);
  }

  public StoreFromStreamSCU(
      AdvancedParams params, DicomNode callingNode, DicomNode calledNode, DicomProgress progress)
      throws IOException {
    Objects.requireNonNull(callingNode);
    Objects.requireNonNull(calledNode);
    this.options = params == null ? new AdvancedParams() : params;
    this.state = new DicomState(progress);
    this.device = new Device("storescu");
    this.conn = new Connection();
    device.addConnection(conn);
    this.ae = new ApplicationEntity(callingNode.getAet());
    device.addApplicationEntity(ae);
    ae.addConnection(conn);

    this.remote = new Connection();

    rq.addPresentationContext(
        new PresentationContext(1, UID.Verification, UID.ImplicitVRLittleEndian));

    options.configureConnect(rq, remote, calledNode);
    options.configureBind(ae, conn, callingNode);

    // configure
    options.configure(conn);
    options.configureTLS(conn, remote);

    setAttributes(new Attributes());
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
    if (as == null) {
      return null;
    }
    return DicomNode.buildLocalDicomNode(as);
  }

  public DicomNode getRemoteDicomNode() {
    if (as == null) {
      return null;
    }
    return DicomNode.buildRemoteDicomNode(as);
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

  public final void enableSOPClassRelationshipExtNeg(boolean enable) {
    relExtNeg = enable;
  }

  public AdvancedParams getOptions() {
    return options;
  }

  public boolean hasAssociation() {
    return as != null;
  }

  public boolean isReadyForDataTransfer() {
    if (as == null) {
      return false;
    }
    return as.isReadyForDataTransfer();
  }

  public Set<String> getTransferSyntaxesFor(String cuid) {
    if (as == null) {
      return Collections.emptySet();
    }
    return as.getTransferSyntaxesFor(cuid);
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

  public synchronized void open() throws IOException {
    countdown.set(false);
    try {
      as = ae.connect(remote, rq);
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      as = null;
      LOGGER.trace("Connecting to remote destination", e);
    }
    if (as == null) {
      throw new IOException("Cannot connect to the remote destination");
    }
  }

  public synchronized void close(boolean force) {
    if (force || countdown.compareAndSet(true, false)) {
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
  }

  public boolean addData(String cuid, String tsuid) {
    countdown.set(false);
    if (cuid == null || tsuid == null) {
      return false;
    }

    if (rq.containsPresentationContextFor(cuid, tsuid)) {
      return true;
    }

    if (!rq.containsPresentationContextFor(cuid)) {
      if (relExtNeg) {
        rq.addCommonExtendedNegotiation(relSOPClasses.getCommonExtendedNegotiation(cuid));
      }
      if (!tsuid.equals(UID.ExplicitVRLittleEndian)) {
        rq.addPresentationContext(
            new PresentationContext(
                rq.getNumberOfPresentationContexts() * 2 + 1, cuid, UID.ExplicitVRLittleEndian));
      }
      if (!tsuid.equals(UID.ImplicitVRLittleEndian)) {
        rq.addPresentationContext(
            new PresentationContext(
                rq.getNumberOfPresentationContexts() * 2 + 1, cuid, UID.ImplicitVRLittleEndian));
      }
    }
    rq.addPresentationContext(
        new PresentationContext(rq.getNumberOfPresentationContexts() * 2 + 1, cuid, tsuid));
    return true;
  }

  public synchronized void triggerCloseExecutor() {
    if ((scheduledFuture == null || scheduledFuture.isDone())
        && countdown.compareAndSet(false, true)) {
      scheduledFuture =
          closeAssociationExecutor.schedule(closeAssociationTask, 15, TimeUnit.SECONDS);
    }
  }

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

  public void prepareTransfer(DeviceOpService service, String iuid, String cuid, String dstTsuid)
      throws IOException {
    synchronized (this) {
      if (hasAssociation()) {
        // Handle dynamically new SOPClassUID
        checkNewSopClassUID(cuid, dstTsuid);

        // Add Presentation Context for the association
        addData(cuid, dstTsuid);
        if (DicomOutputData.isAdaptableSyntax(dstTsuid)) {
          addData(cuid, UID.JPEGLosslessSV1);
        }

        if (!isReadyForDataTransfer()) {
          LOGGER.debug("prepareTransfer: as not ready for data transfer, reopen");
          // If connection has been closed just reopen
          open();
        }
      } else {
        service.start();
        // Add Presentation Context for the association
        addData(cuid, dstTsuid);
        if (!dstTsuid.equals(UID.ExplicitVRLittleEndian)) {
          addData(cuid, UID.ExplicitVRLittleEndian);
        }
        if (DicomOutputData.isAdaptableSyntax(dstTsuid)) {
          addData(cuid, UID.JPEGLosslessSV1);
        }
        LOGGER.debug("prepareTransfer: connecting to the remote destination");
        open();
      }

      // Add IUID to process
      addIUIDProcessed(iuid);
    }
  }

  /**
   * Check if a new transfer syntax needs to be dynamically added to the association. If yes, wait
   * until the end of the current transfers of the streamSCU and close association to add new
   * transfer syntax.
   *
   * @param cuid cuid
   * @param dstTsuid List of transfer syntax of the association
   */
  private void checkNewSopClassUID(String cuid, String dstTsuid) {
    Set<String> tss = getTransferSyntaxesFor(cuid);
    if (!tss.contains(dstTsuid)) {
      LOGGER.debug("prepareTransfer: New output transfer syntax {}: closing streamSCU", dstTsuid);
      countdown.set(false);
      int loop = 0;
      boolean runLoop = true;
      while (runLoop) {
        try {
          if (instanceUidsCurrentlyProcessed.isEmpty()) {
            LOGGER.debug("prepareTransfer: StreamSCU has no more IUID to process: stop waiting");
            break;
          }
          LOGGER.debug("prepareTransfer: StreamSCU has some IUID to process: waiting 20 ms");
          TimeUnit.MILLISECONDS.sleep(20);
          loop++;
          if (loop > 3000) { // Let 1 min max
            LOGGER.warn("prepareTransfer: StreamSCU timeout reached");
            runLoop = false;
            instanceUidsCurrentlyProcessed.clear();
          }
        } catch (InterruptedException e) {
          LOGGER.error(String.format("prepareTransfer: InterruptedException %s", e.getMessage()));
          runLoop = false;
          Thread.currentThread().interrupt();
        }
      }
      LOGGER.info(
          "prepareTransfer: Close association to handle dynamically new SOPClassUID: {}", cuid);
      close(true);
    }
  }

  /**
   * Manage the map corresponding to the uids currently processed: remove the uid of the map if only
   * 1 occurence, otherwise remove 1 occurence number
   *
   * @param iuid Uid to remove from the map
   */
  public void removeIUIDProcessed(String iuid) {
    if (instanceUidsCurrentlyProcessed.containsKey(iuid)
        && instanceUidsCurrentlyProcessed.get(iuid) < 2) {
      instanceUidsCurrentlyProcessed.remove(iuid);
    } else {
      instanceUidsCurrentlyProcessed.computeIfPresent(iuid, (k, v) -> v - 1);
    }
  }

  /**
   * Manage the map corresponding to the uids currently processed: add the uid to the map if uid not
   * existing add 1 occurrence, otherwise add 1 occurence number to the existing uid
   *
   * @param iuid Uid to add in the map
   */
  private void addIUIDProcessed(String iuid) {
    if (instanceUidsCurrentlyProcessed.isEmpty()
        || !instanceUidsCurrentlyProcessed.containsKey(iuid)) {
      instanceUidsCurrentlyProcessed.put(iuid, 1);
    } else {
      instanceUidsCurrentlyProcessed.computeIfPresent(iuid, (k, v) -> v + 1);
    }
  }
}
