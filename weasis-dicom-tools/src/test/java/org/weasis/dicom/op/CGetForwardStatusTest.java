/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.op;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Commands;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.DataWriterAdapter;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.AbstractDicomService;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;

/**
 * The state returned by a C-GET forward against an in-process C-GET SCP serving two instances, when
 * the final destination refuses one of them.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CGetForwardStatusTest {

  private static final String SCP_AET = "STATUS-SCP";
  private static final String DEST_AET = "STATUS-DEST";
  private static final DicomNode CALLING_NODE = new DicomNode("STATUS-SCU");
  private static final String STUDY_UID = "1.2.826.0.1.3680043.2.1125.7.1";
  private static final String REFUSED_IUID = STUDY_UID + ".2";

  /** Whether the C-GET SCP reports the failed sub-operations in its final response. */
  private static final AtomicBoolean CONFORMANT_SCP = new AtomicBoolean(true);

  private static final AtomicBoolean REFUSE_SECOND = new AtomicBoolean();
  private static final List<String> STORED = new CopyOnWriteArrayList<>();

  private static Device scpDevice;
  private static ExecutorService scpExecutor;
  private static ScheduledExecutorService scpScheduledExecutor;
  private static DicomNode scpNode;
  private static DicomNode destNode;

  @BeforeAll
  static void startScp() throws Exception {
    int port;
    try (var socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    var conn = new Connection(null, "localhost", port);
    scpDevice = new Device("status-scp");
    scpDevice.addConnection(conn);
    scpDevice.addApplicationEntity(newAcceptAllAE(SCP_AET, conn));
    scpDevice.addApplicationEntity(newAcceptAllAE(DEST_AET, conn));

    var registry = new DicomServiceRegistry();
    registry.addDicomService(new TwoInstancesGetSCP());
    registry.addDicomService(
        new BasicCStoreSCP("*") {
          @Override
          protected void store(
              Association as,
              PresentationContext pc,
              Attributes rq,
              PDVInputStream data,
              Attributes rsp)
              throws IOException {
            data.readDataset(pc.getTransferSyntax());
            String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
            if (REFUSE_SECOND.get() && REFUSED_IUID.equals(iuid)) {
              throw new DicomServiceException(Status.OutOfResources, "Refused by the test");
            }
            STORED.add(iuid);
          }
        });
    scpDevice.setDimseRQHandler(registry);

    scpExecutor = Executors.newCachedThreadPool();
    scpScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    scpDevice.setExecutor(scpExecutor);
    scpDevice.setScheduledExecutor(scpScheduledExecutor);
    scpDevice.bindConnections();

    scpNode = new DicomNode(SCP_AET, "localhost", port);
    destNode = new DicomNode(DEST_AET, "localhost", port);
  }

  @AfterAll
  static void stopScp() {
    if (scpDevice != null) {
      scpDevice.unbindConnections();
    }
    if (scpExecutor != null) {
      scpExecutor.shutdownNow();
    }
    if (scpScheduledExecutor != null) {
      scpScheduledExecutor.shutdownNow();
    }
  }

  @BeforeEach
  void reset() {
    STORED.clear();
    REFUSE_SECOND.set(false);
    CONFORMANT_SCP.set(true);
  }

  private static ApplicationEntity newAcceptAllAE(String aet, Connection conn) {
    var ae = new ApplicationEntity(aet);
    ae.setAssociationAcceptor(true);
    ae.addConnection(conn);
    ae.addTransferCapability(new TransferCapability(null, "*", TransferCapability.Role.SCP, "*"));
    ae.addTransferCapability(new TransferCapability(null, "*", TransferCapability.Role.SCU, "*"));
    return ae;
  }

  @Test
  void reports_success_with_timing_when_every_instance_is_stored() {
    var progress = new DicomProgress();

    var state = CGetForward.processStudy(CALLING_NODE, scpNode, destNode, progress, STUDY_UID);

    assertEquals(Status.Success, state.getStatus(), state.getMessage());
    assertTrue(state.getMessage().contains("connected in"), state.getMessage());
    assertEquals(2, STORED.size());
    assertEquals(0, progress.getNumberOfFailedSuboperations());
  }

  @Test
  void returns_the_failure_status_of_the_final_cget_response() {
    REFUSE_SECOND.set(true);
    var progress = new DicomProgress();

    var state = CGetForward.processStudy(CALLING_NODE, scpNode, destNode, progress, STUDY_UID);

    assertEquals(Status.OneOrMoreFailures, state.getStatus(), state.getMessage());
    assertTrue(state.getMessage().contains("1/2 operations has failed"), state.getMessage());
    assertEquals(List.of(STUDY_UID + ".1"), STORED);
    assertEquals(1, progress.getNumberOfFailedSuboperations());
  }

  @Test
  void does_not_report_success_when_the_scp_ignores_the_refusal() {
    REFUSE_SECOND.set(true);
    CONFORMANT_SCP.set(false);
    var progress = new DicomProgress();

    var state = CGetForward.processStudy(CALLING_NODE, scpNode, destNode, progress, STUDY_UID);

    assertNotEquals(Status.Success, state.getStatus(), state.getMessage());
    assertEquals(Status.OneOrMoreFailures, state.getStatus(), state.getMessage());
    assertTrue(state.getMessage().contains("1/2 operations has failed"), state.getMessage());
    assertEquals(1, progress.getNumberOfFailedSuboperations());
  }

  /**
   * C-GET SCP sending two Secondary Capture instances. A conformant SCP counts the C-STORE failures
   * and answers B000 when one occurred; otherwise it answers Success regardless.
   */
  private static class TwoInstancesGetSCP extends AbstractDicomService {

    TwoInstancesGetSCP() {
      super(
          UID.StudyRootQueryRetrieveInformationModelGet,
          UID.PatientRootQueryRetrieveInformationModelGet);
    }

    @Override
    protected void onDimseRQ(
        Association as, PresentationContext pc, Dimse dimse, Attributes cmd, Attributes keys) {
      as.getApplicationEntity().getDevice().execute(() -> retrieve(as, pc, cmd, keys));
    }

    private void retrieve(Association as, PresentationContext pc, Attributes cmd, Attributes keys) {
      var failed = new AtomicInteger();
      var completed = new AtomicInteger();
      int status;
      try {
        var cuid = UID.SecondaryCaptureImageStorage;
        var tsuid = as.getTransferSyntaxesFor(cuid).iterator().next();
        String studyUID = keys.getString(Tag.StudyInstanceUID);
        for (int i = 1; i <= 2; i++) {
          var iuid = studyUID + "." + i;
          var rspHandler =
              new DimseRSPHandler(as.nextMessageID()) {
                @Override
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                  super.onDimseRSP(as, cmd, data);
                  if (cmd.getInt(Tag.Status, -1) == Status.Success) {
                    completed.incrementAndGet();
                  } else {
                    failed.incrementAndGet();
                  }
                }
              };
          as.cstore(
              cuid,
              iuid,
              0,
              new DataWriterAdapter(newInstance(studyUID, cuid, iuid)),
              tsuid,
              rspHandler);
          as.waitForOutstandingRSP();
        }
        status =
            CONFORMANT_SCP.get() && failed.get() > 0 ? Status.OneOrMoreFailures : Status.Success;
      } catch (Exception e) {
        status = Status.UnableToPerformSubOperations;
      }
      var rsp = Commands.mkCGetRSP(cmd, status);
      if (CONFORMANT_SCP.get()) {
        rsp.setInt(Tag.NumberOfCompletedSuboperations, VR.US, completed.get());
        rsp.setInt(Tag.NumberOfFailedSuboperations, VR.US, failed.get());
      } else {
        rsp.setInt(Tag.NumberOfCompletedSuboperations, VR.US, 2);
        rsp.setInt(Tag.NumberOfFailedSuboperations, VR.US, 0);
      }
      as.tryWriteDimseRSP(pc, rsp);
    }

    private static Attributes newInstance(String studyUID, String cuid, String iuid) {
      var attrs = new Attributes();
      attrs.setString(Tag.SOPClassUID, VR.UI, cuid);
      attrs.setString(Tag.SOPInstanceUID, VR.UI, iuid);
      attrs.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
      attrs.setString(Tag.SeriesInstanceUID, VR.UI, studyUID + ".0");
      attrs.setString(Tag.PatientID, VR.LO, "STATUS");
      attrs.setString(Tag.Modality, VR.CS, "OT");
      attrs.setInt(Tag.Rows, VR.US, 2);
      attrs.setInt(Tag.Columns, VR.US, 2);
      attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
      attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
      attrs.setInt(Tag.BitsAllocated, VR.US, 8);
      attrs.setInt(Tag.BitsStored, VR.US, 8);
      attrs.setInt(Tag.HighBit, VR.US, 7);
      attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
      attrs.setBytes(Tag.PixelData, VR.OB, new byte[4]);
      return attrs;
    }
  }
}
