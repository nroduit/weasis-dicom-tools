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
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.BasicCFindSCP;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.param.DicomNode;

/**
 * Regression test for executor threads leaked by the SCU operations: each operation starts a
 * DeviceOpService and must stop it once the association is closed (issue #64).
 */
@DisplayName("SCU operations release their device executors")
@DisplayNameGeneration(ReplaceUnderscores.class)
class DeviceExecutorLeakTest {

  private static final String SCP_AET = "LEAK-SCP";
  private static final String DEST_AET = "LEAK-DEST";
  private static final int ITERATIONS = 20;

  private static final DicomNode CALLING_NODE = new DicomNode("LEAK-SCU");

  private static final AtomicInteger RECEIVED_INSTANCES = new AtomicInteger();

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
    scpDevice = new Device("leak-scp");
    scpDevice.addConnection(conn);
    scpDevice.addApplicationEntity(newAcceptAllAE(SCP_AET, conn));
    scpDevice.addApplicationEntity(newAcceptAllAE(DEST_AET, conn));

    var registry = new DicomServiceRegistry();
    registry.addDicomService(new BasicCEchoSCP());
    registry.addDicomService(
        new BasicCFindSCP(
            UID.StudyRootQueryRetrieveInformationModelFind,
            UID.PatientRootQueryRetrieveInformationModelFind));
    registry.addDicomService(new SingleInstanceGetSCP());
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
            RECEIVED_INSTANCES.incrementAndGet();
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

  private static ApplicationEntity newAcceptAllAE(String aet, Connection conn) {
    var ae = new ApplicationEntity(aet);
    ae.addConnection(conn);
    ae.setAssociationAcceptor(true);
    ae.addTransferCapability(new TransferCapability(null, "*", TransferCapability.Role.SCP, "*"));
    ae.addTransferCapability(new TransferCapability(null, "*", TransferCapability.Role.SCU, "*"));
    return ae;
  }

  @Test
  void repeated_cfind_does_not_leak_executor_threads() throws InterruptedException {
    int before = countThreads("findscu-");
    for (int i = 0; i < ITERATIONS; i++) {
      var state = CFind.process(CALLING_NODE, scpNode, CFind.PatientID, CFind.StudyInstanceUID);
      assertEquals(Status.Success, state.getStatus(), state.getMessage());
    }
    assertEquals(before, awaitThreadCount("findscu-", before));
  }

  @Test
  void repeated_cget_forward_does_not_leak_executor_threads() throws InterruptedException {
    int getBefore = countThreads("getscu-");
    int storeBefore = countThreads("storescu-");
    RECEIVED_INSTANCES.set(0);
    for (int i = 0; i < ITERATIONS; i++) {
      var state =
          CGetForward.processStudy(
              CALLING_NODE, scpNode, destNode, null, "1.2.826.0.1.3680043.2.1125.9." + i);
      assertEquals(Status.Success, state.getStatus(), state.getMessage());
    }
    assertEquals(ITERATIONS, RECEIVED_INSTANCES.get());
    assertEquals(getBefore, awaitThreadCount("getscu-", getBefore));
    assertEquals(storeBefore, awaitThreadCount("storescu-", storeBefore));
  }

  @Test
  void repeated_echo_does_not_leak_executor_threads() throws InterruptedException {
    int before = countThreads("echo-scu-");
    for (int i = 0; i < ITERATIONS; i++) {
      var state = Echo.process(CALLING_NODE, scpNode);
      assertEquals(Status.Success, state.getStatus(), state.getMessage());
    }
    assertEquals(before, awaitThreadCount("echo-scu-", before));
  }

  @Test
  void repeated_cstore_does_not_leak_executor_threads() throws Exception {
    var files = List.of(Path.of(getClass().getResource("/dicom/mr.dcm").toURI()).toString());
    int before = countThreads("storescu-");
    RECEIVED_INSTANCES.set(0);
    // Stopping the executors before releasing the association made each call wait for the
    // 30 s executor shutdown timeout
    assertTimeout(
        Duration.ofSeconds(20),
        () -> {
          for (int i = 0; i < ITERATIONS; i++) {
            var state = CStore.process(CALLING_NODE, destNode, files);
            assertEquals(Status.Success, state.getStatus(), state.getMessage());
          }
        });
    assertEquals(ITERATIONS, RECEIVED_INSTANCES.get());
    assertEquals(before, awaitThreadCount("storescu-", before));
  }

  /** Executor threads may still be exiting right after shutdown; give them a short grace period. */
  private static int awaitThreadCount(String prefix, int expected) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    int count = countThreads(prefix);
    while (count > expected && System.nanoTime() < deadline) {
      TimeUnit.MILLISECONDS.sleep(50);
      count = countThreads(prefix);
    }
    return count;
  }

  private static int countThreads(String prefix) {
    return (int)
        Thread.getAllStackTraces().keySet().stream()
            .filter(t -> t.isAlive() && t.getName().startsWith(prefix))
            .count();
  }

  /** C-GET SCP answering every request with one Secondary Capture instance. */
  private static class SingleInstanceGetSCP extends AbstractDicomService {

    SingleInstanceGetSCP() {
      super(
          UID.StudyRootQueryRetrieveInformationModelGet,
          UID.PatientRootQueryRetrieveInformationModelGet);
    }

    @Override
    protected void onDimseRQ(
        Association as, PresentationContext pc, Dimse dimse, Attributes cmd, Attributes keys) {
      // Sub-operations need the reader thread to receive their responses: run them aside.
      as.getApplicationEntity().getDevice().execute(() -> retrieve(as, pc, cmd, keys));
    }

    private void retrieve(Association as, PresentationContext pc, Attributes cmd, Attributes keys) {
      int status = Status.Success;
      try {
        var cuid = UID.SecondaryCaptureImageStorage;
        var tsuid = as.getTransferSyntaxesFor(cuid).iterator().next();
        var iuid = keys.getString(Tag.StudyInstanceUID) + ".1";
        var rspHandler = new DimseRSPHandler(as.nextMessageID());
        as.cstore(
            cuid, iuid, 0, new DataWriterAdapter(newInstance(keys, cuid, iuid)), tsuid, rspHandler);
        as.waitForOutstandingRSP();
      } catch (Exception e) {
        status = Status.UnableToPerformSubOperations;
      }
      var rsp = Commands.mkCGetRSP(cmd, status);
      rsp.setInt(Tag.NumberOfCompletedSuboperations, VR.US, status == Status.Success ? 1 : 0);
      as.tryWriteDimseRSP(pc, rsp);
    }

    private static Attributes newInstance(Attributes keys, String cuid, String iuid) {
      var attrs = new Attributes();
      attrs.setString(Tag.SOPClassUID, VR.UI, cuid);
      attrs.setString(Tag.SOPInstanceUID, VR.UI, iuid);
      attrs.setString(Tag.StudyInstanceUID, VR.UI, keys.getString(Tag.StudyInstanceUID));
      attrs.setString(Tag.SeriesInstanceUID, VR.UI, iuid + ".1");
      attrs.setString(Tag.PatientID, VR.LO, "LEAK");
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
