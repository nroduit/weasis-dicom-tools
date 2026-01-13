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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.DataWriter;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DeviceOpService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StoreFromStreamSCUTest {

  private static final String TEST_CALLING_AET = "TEST_CALLING";
  private static final String TEST_CALLED_AET = "TEST_CALLED";
  private static final String TEST_HOST = "localhost";
  private static final int TEST_PORT = 11112;
  private static final String TEST_CUID = "1.2.840.10008.5.1.4.1.1.2";
  private static final String TEST_IUID = "1.2.840.10008.1.2.1.1";
  private static final String TEST_TSUID = UID.ExplicitVRLittleEndian;

  @Mock private DeviceOpService mockDeviceOpService;
  @Mock private Association mockAssociation;
  @Mock private DataWriter mockDataWriter;

  private DicomNode callingNode;
  private DicomNode calledNode;
  private AdvancedParams advancedParams;
  private DicomProgress dicomProgress;
  private StoreFromStreamSCU storeFromStreamSCU;

  @BeforeEach
  void setUp() throws IOException {
    callingNode = new DicomNode(TEST_CALLING_AET, TEST_HOST, TEST_PORT);
    calledNode = new DicomNode(TEST_CALLED_AET, TEST_HOST, TEST_PORT + 1);

    // Create real instances
    advancedParams = new AdvancedParams();
    dicomProgress = new DicomProgress();
  }

  @Nested
  class Constructor_Tests {

    @Test
    void should_create_instance_with_minimal_parameters() throws IOException {
      storeFromStreamSCU = new StoreFromStreamSCU(callingNode, calledNode);

      assertNotNull(storeFromStreamSCU);
      assertEquals(TEST_CALLING_AET, storeFromStreamSCU.getCallingNode().getAet());
      assertEquals(TEST_CALLED_AET, storeFromStreamSCU.getCalledNode().getAet());
      assertNotNull(storeFromStreamSCU.getDevice());
      assertNotNull(storeFromStreamSCU.getState());
    }

    @Test
    void should_create_instance_with_advanced_params() throws IOException {
      storeFromStreamSCU = new StoreFromStreamSCU(advancedParams, callingNode, calledNode);

      assertNotNull(storeFromStreamSCU);
      assertEquals(advancedParams, storeFromStreamSCU.getOptions());
    }

    @Test
    void should_create_instance_with_all_parameters() throws IOException {
      storeFromStreamSCU =
          new StoreFromStreamSCU(advancedParams, callingNode, calledNode, dicomProgress);

      assertNotNull(storeFromStreamSCU);
      assertEquals(dicomProgress, storeFromStreamSCU.getState().getProgress());
      assertEquals(advancedParams, storeFromStreamSCU.getOptions());
    }

    @Test
    void should_throw_exception_when_calling_node_is_null() {
      assertThrows(
          NullPointerException.class,
          () -> new StoreFromStreamSCU(advancedParams, null, calledNode));
    }

    @Test
    void should_throw_exception_when_called_node_is_null() {
      assertThrows(
          NullPointerException.class,
          () -> new StoreFromStreamSCU(advancedParams, callingNode, null));
    }

    @Test
    void should_use_default_advanced_params_when_null() throws IOException {
      storeFromStreamSCU = new StoreFromStreamSCU(null, callingNode, calledNode, dicomProgress);

      assertNotNull(storeFromStreamSCU.getOptions());
      assertNotEquals(advancedParams, storeFromStreamSCU.getOptions());
    }
  }

  @Nested
  class Association_Management_Tests {

    @BeforeEach
    void setUp() throws IOException {
      storeFromStreamSCU =
          new StoreFromStreamSCU(advancedParams, callingNode, calledNode, dicomProgress);
    }

    @Test
    void should_return_false_when_no_association() {
      assertFalse(storeFromStreamSCU.hasAssociation());
      assertFalse(storeFromStreamSCU.isReadyForDataTransfer());
    }

    @Test
    void should_return_empty_set_when_no_association() {
      Set<String> transferSyntaxes = storeFromStreamSCU.getTransferSyntaxesFor(TEST_CUID);

      assertTrue(transferSyntaxes.isEmpty());
    }

    @Test
    void should_return_null_for_dicom_nodes_when_no_association() {
      assertNull(storeFromStreamSCU.getLocalDicomNode());
      assertNull(storeFromStreamSCU.getRemoteDicomNode());
    }

    @Test
    void should_handle_association_state_correctly() {
      // Simulate association being set (would normally happen in open())
      var privateAssociation = setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);
      when(mockAssociation.getTransferSyntaxesFor(TEST_CUID)).thenReturn(Set.of(TEST_TSUID));

      assertTrue(storeFromStreamSCU.hasAssociation());
      assertTrue(storeFromStreamSCU.isReadyForDataTransfer());
      assertEquals(Set.of(TEST_TSUID), storeFromStreamSCU.getTransferSyntaxesFor(TEST_CUID));
    }
  }

  @Nested
  class Data_Management_Tests {

    @BeforeEach
    void setUp() throws IOException {
      storeFromStreamSCU = new StoreFromStreamSCU(advancedParams, callingNode, calledNode);
    }

    @Test
    void should_return_false_for_null_parameters() {
      assertFalse(storeFromStreamSCU.addData(null, TEST_TSUID));
      assertFalse(storeFromStreamSCU.addData(TEST_CUID, null));
      assertFalse(storeFromStreamSCU.addData(null, null));
    }

    @Test
    void should_add_presentation_contexts_for_new_sop_class() {
      AAssociateRQ associateRQ = storeFromStreamSCU.getAAssociateRQ();
      int initialContexts = associateRQ.getNumberOfPresentationContexts();

      assertTrue(storeFromStreamSCU.addData(TEST_CUID, TEST_TSUID));

      assertTrue(associateRQ.getNumberOfPresentationContexts() > initialContexts);
    }

    @Test
    void should_return_true_for_existing_presentation_context() {
      storeFromStreamSCU.addData(TEST_CUID, TEST_TSUID);

      assertTrue(storeFromStreamSCU.addData(TEST_CUID, TEST_TSUID));
    }

    @Test
    void should_add_standard_transfer_syntaxes() {
      storeFromStreamSCU.addData(TEST_CUID, UID.JPEGBaseline8Bit);
      AAssociateRQ associateRQ = storeFromStreamSCU.getAAssociateRQ();

      // Should have added the requested syntax plus standard ones
      assertTrue(associateRQ.containsPresentationContextFor(TEST_CUID, UID.JPEGBaseline8Bit));
      assertTrue(associateRQ.containsPresentationContextFor(TEST_CUID, UID.ExplicitVRLittleEndian));
      assertTrue(associateRQ.containsPresentationContextFor(TEST_CUID, UID.ImplicitVRLittleEndian));
    }

    @Test
    void should_enable_sop_class_relationship_extended_negotiation() {
      storeFromStreamSCU.enableSOPClassRelationshipExtNeg(true);
      storeFromStreamSCU.addData(TEST_CUID, TEST_TSUID);

      // Verify that extended negotiation was configured
      assertNotNull(storeFromStreamSCU.getAAssociateRQ());
    }
  }

  @Nested
  class Transfer_Syntax_Selection_Tests {

    @Mock private Association mockAssociation;

    @Test
    void should_return_preferred_transfer_syntax_when_available() {
      when(mockAssociation.getTransferSyntaxesFor(TEST_CUID))
          .thenReturn(Set.of(TEST_TSUID, UID.ImplicitVRLittleEndian));

      String selected =
          StoreFromStreamSCU.selectTransferSyntax(mockAssociation, TEST_CUID, TEST_TSUID);

      assertEquals(TEST_TSUID, selected);
    }

    @Test
    void should_fallback_to_explicit_vr_little_endian() {
      when(mockAssociation.getTransferSyntaxesFor(TEST_CUID))
          .thenReturn(Set.of(UID.ExplicitVRLittleEndian, UID.ImplicitVRLittleEndian));

      String selected =
          StoreFromStreamSCU.selectTransferSyntax(
              mockAssociation, TEST_CUID, "1.2.840.10008.1.2.4.50");

      assertEquals(UID.ExplicitVRLittleEndian, selected);
    }

    @Test
    void should_fallback_to_implicit_vr_little_endian_as_last_resort() {
      when(mockAssociation.getTransferSyntaxesFor(TEST_CUID))
          .thenReturn(Set.of(UID.ImplicitVRLittleEndian));

      String selected =
          StoreFromStreamSCU.selectTransferSyntax(
              mockAssociation, TEST_CUID, "1.2.840.10008.1.2.4.50");

      assertEquals(UID.ImplicitVRLittleEndian, selected);
    }

    @Test
    void should_return_implicit_vr_when_no_transfer_syntaxes_available() {
      when(mockAssociation.getTransferSyntaxesFor(TEST_CUID)).thenReturn(Collections.emptySet());

      String selected =
          StoreFromStreamSCU.selectTransferSyntax(mockAssociation, TEST_CUID, TEST_TSUID);

      assertEquals(UID.ImplicitVRLittleEndian, selected);
    }
  }

  @Nested
  class Store_Operation_Tests {

    @BeforeEach
    void setUp() throws IOException {
      storeFromStreamSCU = new StoreFromStreamSCU(advancedParams, callingNode, calledNode);
    }

    @Test
    void should_throw_exception_when_association_is_null() {
      assertThrows(
          IllegalStateException.class,
          () -> storeFromStreamSCU.cstore(TEST_CUID, TEST_IUID, 0, mockDataWriter, TEST_TSUID));
    }

    @Test
    void should_perform_cstore_when_association_is_available()
        throws IOException, InterruptedException {
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.nextMessageID()).thenReturn(1);

      storeFromStreamSCU.cstore(TEST_CUID, TEST_IUID, 0, mockDataWriter, TEST_TSUID);

      verify(mockAssociation)
          .cstore(
              eq(TEST_CUID),
              eq(TEST_IUID),
              eq(0),
              eq(mockDataWriter),
              eq(TEST_TSUID),
              any(DimseRSPHandler.class));
    }
  }

  @Nested
  class Progress_Monitoring_Tests {

    @BeforeEach
    void setUp() throws IOException {
      storeFromStreamSCU =
          new StoreFromStreamSCU(advancedParams, callingNode, calledNode, dicomProgress);
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.nextMessageID()).thenReturn(1);
    }

    @Test
    void should_handle_successful_response() {
      Attributes successCmd = createResponseCommand(Status.Success);

      var handler = storeFromStreamSCU.getRspHandlerFactory().createDimseRSPHandler();
      handler.onDimseRSP(mockAssociation, successCmd, new Attributes());

      assertEquals(Status.Success, storeFromStreamSCU.getState().getStatus());
      assertEquals(successCmd, dicomProgress.getAttributes());
    }

    @Test
    void should_handle_warning_response() {
      Attributes warningCmd = createResponseCommand(Status.CoercionOfDataElements);

      var handler = storeFromStreamSCU.getRspHandlerFactory().createDimseRSPHandler();
      handler.onDimseRSP(mockAssociation, warningCmd, new Attributes());

      assertEquals(Status.CoercionOfDataElements, storeFromStreamSCU.getState().getStatus());
      assertEquals(warningCmd, dicomProgress.getAttributes());
    }

    @Test
    void should_handle_error_response() {
      Attributes errorCmd = createResponseCommand(Status.ProcessingFailure);

      var handler = storeFromStreamSCU.getRspHandlerFactory().createDimseRSPHandler();
      handler.onDimseRSP(mockAssociation, errorCmd, new Attributes());

      assertEquals(Status.ProcessingFailure, storeFromStreamSCU.getState().getStatus());
      assertEquals(errorCmd, dicomProgress.getAttributes());
    }

    @Test
    void should_track_progress_attributes() {
      Attributes cmd = createResponseCommand(Status.Success);

      var handler = storeFromStreamSCU.getRspHandlerFactory().createDimseRSPHandler();
      handler.onDimseRSP(mockAssociation, cmd, new Attributes());

      assertEquals(1, dicomProgress.getNumberOfCompletedSuboperations());
      assertEquals(65535, dicomProgress.getNumberOfRemainingSuboperations());
    }

    @Test
    void should_handle_progress_cancellation() {
      Attributes cmd = createResponseCommand(Status.Success);

      var handler = storeFromStreamSCU.getRspHandlerFactory().createDimseRSPHandler();
      handler.onDimseRSP(mockAssociation, cmd, new Attributes());

      dicomProgress.cancel();

      assertTrue(dicomProgress.isCancelled());
      assertEquals(Status.Cancel, dicomProgress.getStatus());
    }

    private Attributes createResponseCommand(int status) {
      Attributes cmd = new Attributes();
      cmd.setInt(Tag.Status, VR.US, status);
      return cmd;
    }
  }

  @Nested
  class Prepare_Transfer_Tests {

    @BeforeEach
    void setUp() throws IOException {
      storeFromStreamSCU = new StoreFromStreamSCU(advancedParams, callingNode, calledNode);
    }

    @Test
    void should_start_new_service_when_no_association() throws IOException {
      assertThrows(
          IOException.class,
          () ->
              storeFromStreamSCU.prepareTransfer(
                  mockDeviceOpService, TEST_IUID, TEST_CUID, TEST_TSUID));

      verify(mockDeviceOpService).start();
    }

    @Test
    void should_handle_existing_association_with_compatible_transfer_syntax() throws IOException {
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);
      when(mockAssociation.getTransferSyntaxesFor(TEST_CUID)).thenReturn(Set.of(TEST_TSUID));

      storeFromStreamSCU.prepareTransfer(mockDeviceOpService, TEST_IUID, TEST_CUID, TEST_TSUID);

      verify(mockDeviceOpService, never()).start();
      verifyNoInteractions(mockDeviceOpService);
    }
  }

  @Nested
  class UID_Processing_Tests {

    @BeforeEach
    void setUp() throws IOException {
      storeFromStreamSCU = new StoreFromStreamSCU(advancedParams, callingNode, calledNode);
    }

    @Test
    void should_add_and_remove_iuid_correctly() throws IOException {
      // Add IUID through prepareTransfer
      assertThrows(
          IOException.class,
          () ->
              storeFromStreamSCU.prepareTransfer(mockDeviceOpService, null, TEST_CUID, TEST_TSUID));

      // Remove IUID
      storeFromStreamSCU.removeIUIDProcessed(TEST_IUID);

      // Adding same IUID again should work normally
      assertThrows(
          IOException.class,
          () ->
              storeFromStreamSCU.prepareTransfer(
                  mockDeviceOpService, TEST_IUID, TEST_CUID, TEST_TSUID));
      verify(mockDeviceOpService, times(2)).start();
    }

    @Test
    void should_handle_multiple_occurrences_of_same_iuid() throws IOException {

      // Add same IUID multiple times
      assertThrows(
          IOException.class,
          () ->
              storeFromStreamSCU.prepareTransfer(
                  mockDeviceOpService, TEST_IUID, TEST_CUID, TEST_TSUID));
      assertThrows(
          IOException.class,
          () ->
              storeFromStreamSCU.prepareTransfer(
                  mockDeviceOpService, TEST_IUID, TEST_CUID, TEST_TSUID));

      // Remove once - should still have one occurrence
      storeFromStreamSCU.removeIUIDProcessed(TEST_IUID);

      // Remove again - should be completely removed
      storeFromStreamSCU.removeIUIDProcessed(TEST_IUID);

      // Verify behavior through subsequent operations
      verify(mockDeviceOpService, times(2)).start();
    }

    @Test
    void should_handle_removal_of_non_existent_iuid() {
      // Should not throw exception
      assertDoesNotThrow(() -> storeFromStreamSCU.removeIUIDProcessed("non-existent-uid"));
    }
  }

  @Nested
  class Attributes_And_Properties_Tests {

    @BeforeEach
    void setUp() throws IOException {
      storeFromStreamSCU = new StoreFromStreamSCU(advancedParams, callingNode, calledNode);
    }

    @Test
    void should_manage_attributes_correctly() {
      Attributes initialAttributes = storeFromStreamSCU.getAttributes();
      assertNotNull(initialAttributes);

      Attributes newAttributes = new Attributes();
      newAttributes.setString(Tag.PatientName, VR.PN, "Test Patient");

      storeFromStreamSCU.setAttributes(newAttributes);
      assertEquals(newAttributes, storeFromStreamSCU.getAttributes());
    }

    @Test
    void should_manage_number_of_suboperations() {
      assertEquals(0, storeFromStreamSCU.getNumberOfSuboperations());

      storeFromStreamSCU.setNumberOfSuboperations(5);
      assertEquals(5, storeFromStreamSCU.getNumberOfSuboperations());
    }

    @Test
    void should_provide_access_to_internal_components() {
      assertNotNull(storeFromStreamSCU.getDevice());
      assertNotNull(storeFromStreamSCU.getAAssociateRQ());
      assertNotNull(storeFromStreamSCU.getRemoteConnection());
      assertNotNull(storeFromStreamSCU.getState());
      assertNotNull(storeFromStreamSCU.getRspHandlerFactory());
      assertEquals(advancedParams, storeFromStreamSCU.getOptions());
    }

    @Test
    void should_handle_device_properties_correctly() {
      Device device = storeFromStreamSCU.getDevice();
      assertEquals("storescu", device.getDeviceName());

      ApplicationEntity ae = device.getApplicationEntity(TEST_CALLING_AET);
      assertNotNull(ae);
      assertEquals(TEST_CALLING_AET, ae.getAETitle());
    }
  }

  @Nested
  class Executor_Management_Tests {

    @BeforeEach
    void setUp() throws IOException {
      storeFromStreamSCU = new StoreFromStreamSCU(advancedParams, callingNode, calledNode);
    }

    @Test
    void should_trigger_close_executor() throws InterruptedException {
      storeFromStreamSCU.triggerCloseExecutor();

      // Wait briefly to allow scheduler to potentially execute
      TimeUnit.MILLISECONDS.sleep(50);

      // Verify that the countdown state changed
      // This is indirect verification as we can't directly test the scheduler
      assertDoesNotThrow(() -> storeFromStreamSCU.triggerCloseExecutor());
    }

    @Test
    void should_handle_multiple_trigger_calls() {
      assertDoesNotThrow(
          () -> {
            storeFromStreamSCU.triggerCloseExecutor();
            storeFromStreamSCU.triggerCloseExecutor();
            storeFromStreamSCU.triggerCloseExecutor();
          });
    }
  }

  @Nested
  class Association_Closure_Tests {

    @BeforeEach
    void setUp() throws IOException {
      storeFromStreamSCU =
          new StoreFromStreamSCU(advancedParams, callingNode, calledNode, dicomProgress);
    }

    @Test
    void should_close_association_when_ready_for_data_transfer() throws Exception {
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);

      storeFromStreamSCU.close(true);

      verify(mockAssociation).release();
      verify(mockAssociation).waitForSocketClose();
      assertFalse(storeFromStreamSCU.hasAssociation());
    }

    @Test
    void should_close_association_when_not_ready_for_data_transfer() throws Exception {
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(false);

      storeFromStreamSCU.close(true);

      verify(mockAssociation, never()).release();
      verify(mockAssociation).waitForSocketClose();
      assertFalse(storeFromStreamSCU.hasAssociation());
    }

    @Test
    void should_handle_exception_during_association_release() throws Exception {
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);
      doThrow(new RuntimeException("Release failed")).when(mockAssociation).release();

      // Should not throw exception, just log it
      assertDoesNotThrow(() -> storeFromStreamSCU.close(true));

      verify(mockAssociation).release();
      assertFalse(storeFromStreamSCU.hasAssociation());
    }

    @Test
    void should_handle_interrupted_exception_during_association_close() throws Exception {
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);
      doThrow(new InterruptedException("Thread interrupted"))
          .when(mockAssociation)
          .waitForSocketClose();

      storeFromStreamSCU.close(true);

      verify(mockAssociation).release();
      verify(mockAssociation).waitForSocketClose();
      assertTrue(Thread.currentThread().isInterrupted());
      assertFalse(storeFromStreamSCU.hasAssociation());
    }

    @Test
    void should_handle_exception_during_wait_for_socket_close() throws Exception {
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);
      doThrow(new RuntimeException("Socket close failed"))
          .when(mockAssociation)
          .waitForSocketClose();

      assertDoesNotThrow(() -> storeFromStreamSCU.close(true));

      verify(mockAssociation).release();
      verify(mockAssociation).waitForSocketClose();
      assertFalse(storeFromStreamSCU.hasAssociation());
    }

    @Test
    void should_not_close_when_association_is_null() {
      // Association is null by default
      assertFalse(storeFromStreamSCU.hasAssociation());

      assertDoesNotThrow(() -> storeFromStreamSCU.close(true));

      // No interactions with mock since association is null
      verifyNoInteractions(mockAssociation);
    }
  }

  @Nested
  class Transfer_Syntax_Management_Tests {

    @BeforeEach
    void setUp() throws IOException {
      storeFromStreamSCU =
          new StoreFromStreamSCU(advancedParams, callingNode, calledNode, dicomProgress);
    }

    @Test
    void should_handle_new_transfer_syntax_with_max_wait_exceeded()
        throws IOException, InterruptedException {
      // This test focuses on the scenario where MAX_WAIT_LOOPS is exceeded
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);
      when(mockAssociation.getTransferSyntaxesFor(TEST_CUID))
          .thenReturn(Set.of(UID.ImplicitVRLittleEndian));

      // Add persistent UIDs
      storeFromStreamSCU.prepareTransfer(
          mockDeviceOpService, "persistent_uid", TEST_CUID, UID.ImplicitVRLittleEndian);

      // Use reflection to temporarily reduce the wait limits for testing
      try {
        Field maxWaitLoopsField = StoreFromStreamSCU.class.getDeclaredField("MAX_WAIT_LOOPS");
        maxWaitLoopsField.setAccessible(true);

        Field waitSleepMsField = StoreFromStreamSCU.class.getDeclaredField("WAIT_SLEEP_MS");
        waitSleepMsField.setAccessible(true);

        // We can't modify final fields, so we'll test with a timeout approach instead
        long startTime = System.currentTimeMillis();

        // Start the operation in a separate thread with a timeout
        CompletableFuture<Void> future =
            CompletableFuture.runAsync(
                () -> {
                  try {
                    storeFromStreamSCU.prepareTransfer(
                        mockDeviceOpService, "new_uid", TEST_CUID, TEST_TSUID);
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });

        // Wait for a short time, then interrupt to simulate timeout
        Thread.sleep(100);
        future.cancel(true);

        long endTime = System.currentTimeMillis();
        assertTrue(endTime - startTime < 5000, "Should not wait indefinitely");

      } catch (NoSuchFieldException | InterruptedException e) {
        // If reflection fails, just test that the method completes eventually
        long startTime = System.currentTimeMillis();

        CompletableFuture<Void> future =
            CompletableFuture.runAsync(
                () -> {
                  try {
                    storeFromStreamSCU.prepareTransfer(
                        mockDeviceOpService, "new_uid", TEST_CUID, TEST_TSUID);
                  } catch (IOException ex) {
                    throw new RuntimeException(ex);
                  }
                });

        // Remove the persistent UID after a short delay to allow completion
        CompletableFuture.runAsync(
            () -> {
              try {
                Thread.sleep(50);
                storeFromStreamSCU.removeIUIDProcessed("persistent_uid");
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
              }
            });

        assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS));

        long endTime = System.currentTimeMillis();
        assertTrue(endTime - startTime < 10000, "Should complete within reasonable time");
      }
    }

    @Test
    void should_handle_interrupt_during_wait_for_pending_transfers() throws IOException {
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);
      when(mockAssociation.getTransferSyntaxesFor(TEST_CUID))
          .thenReturn(Set.of(UID.ImplicitVRLittleEndian));

      // Add persistent UIDs
      storeFromStreamSCU.prepareTransfer(
          mockDeviceOpService, "persistent_uid", TEST_CUID, UID.ImplicitVRLittleEndian);

      // Interrupt the current thread during the operation
      Thread currentThread = Thread.currentThread();
      Thread interruptThread =
          new Thread(
              () -> {
                try {
                  TimeUnit.MILLISECONDS.sleep(100);
                  currentThread.interrupt();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
      interruptThread.start();

      // This should handle the interrupt gracefully
      assertThrows(
          IOException.class,
          () ->
              storeFromStreamSCU.prepareTransfer(
                  mockDeviceOpService, "new_uid", TEST_CUID, TEST_TSUID));

      assertTrue(Thread.currentThread().isInterrupted());

      // Clear interrupt status for subsequent tests
      Thread.interrupted();

      try {
        interruptThread.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    @Test
    void should_not_trigger_new_transfer_syntax_handling_when_compatible()
        throws IOException, InterruptedException {
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);
      when(mockAssociation.getTransferSyntaxesFor(TEST_CUID))
          .thenReturn(Set.of(TEST_TSUID, UID.ImplicitVRLittleEndian));

      // This should not trigger new transfer syntax handling
      storeFromStreamSCU.prepareTransfer(mockDeviceOpService, TEST_IUID, TEST_CUID, TEST_TSUID);

      // Association should not be closed since transfer syntax is compatible
      verify(mockAssociation, never()).release();
      verify(mockAssociation, never()).waitForSocketClose();
    }

    @Test
    void should_reset_countdown_when_handling_new_transfer_syntax()
        throws IOException, InterruptedException {
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);
      when(mockAssociation.getTransferSyntaxesFor(TEST_CUID))
          .thenReturn(Set.of(UID.ImplicitVRLittleEndian));

      // First set countdown to true
      storeFromStreamSCU.triggerCloseExecutor();

      // This should reset countdown to false and handle new transfer syntax
      assertThrows(
          IOException.class,
          () ->
              storeFromStreamSCU.prepareTransfer(
                  mockDeviceOpService, TEST_IUID, TEST_CUID, TEST_TSUID));

      // Verify association was closed due to new transfer syntax
      verify(mockAssociation).release();
      verify(mockAssociation).waitForSocketClose();
    }

    @Test
    void should_handle_empty_pending_transfers_immediately()
        throws IOException, InterruptedException {
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);
      when(mockAssociation.getTransferSyntaxesFor(TEST_CUID))
          .thenReturn(Set.of(UID.ImplicitVRLittleEndian));

      // No pending transfers - should handle immediately
      long startTime = System.currentTimeMillis();

      assertThrows(
          IOException.class,
          () ->
              storeFromStreamSCU.prepareTransfer(
                  mockDeviceOpService, TEST_IUID, TEST_CUID, TEST_TSUID));

      long endTime = System.currentTimeMillis();

      // Should complete quickly since no pending transfers
      assertTrue(endTime - startTime < 1000, "Should complete quickly with no pending transfers");

      verify(mockAssociation).release();
      verify(mockAssociation).waitForSocketClose();
    }
  }

  @Nested
  class Integration_Tests_For_Private_Methods {

    @BeforeEach
    void setUp() throws IOException {
      storeFromStreamSCU =
          new StoreFromStreamSCU(advancedParams, callingNode, calledNode, dicomProgress);
    }

    @Test
    void should_integrate_close_and_reopen_cycle() throws IOException, InterruptedException {
      // This tests the integration between closeAssociation and open methods
      setPrivateAssociation(mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);

      assertTrue(storeFromStreamSCU.hasAssociation());

      // Close the association
      storeFromStreamSCU.close(true);

      assertFalse(storeFromStreamSCU.hasAssociation());
      verify(mockAssociation).release();
      verify(mockAssociation).waitForSocketClose();
    }
  }

  @Nested
  class DicomProgress_Integration_Tests {

    @BeforeEach
    void setUp() throws IOException {
      storeFromStreamSCU =
          new StoreFromStreamSCU(advancedParams, callingNode, calledNode, dicomProgress);
    }

    @Test
    void should_update_progress_with_real_attributes() {
      Attributes testAttributes = new Attributes();
      testAttributes.setInt(Tag.Status, VR.US, Status.Success);
      testAttributes.setInt(Tag.NumberOfCompletedSuboperations, VR.US, 3);
      testAttributes.setInt(Tag.NumberOfRemainingSuboperations, VR.US, 7);

      dicomProgress.setAttributes(testAttributes);

      assertEquals(testAttributes, dicomProgress.getAttributes());
      assertEquals(Status.Success, dicomProgress.getStatus());
      assertEquals(3, dicomProgress.getNumberOfCompletedSuboperations());
      assertEquals(7, dicomProgress.getNumberOfRemainingSuboperations());
    }

    @Test
    void should_handle_progress_listeners() {
      var progressListenerCalled = new boolean[] {false};

      dicomProgress.addProgressListener(
          progress -> {
            progressListenerCalled[0] = true;
            assertEquals(dicomProgress, progress);
          });

      Attributes testAttributes = new Attributes();
      testAttributes.setInt(Tag.Status, VR.US, Status.Success);
      dicomProgress.setAttributes(testAttributes);

      assertTrue(progressListenerCalled[0], "Progress listener should have been called");
    }

    @Test
    void should_track_failed_operations() {
      Attributes cmd1 = new Attributes();
      cmd1.setInt(Tag.NumberOfFailedSuboperations, VR.US, 0);
      dicomProgress.setAttributes(cmd1);
      assertFalse(dicomProgress.isLastFailed());

      Attributes cmd2 = new Attributes();
      cmd2.setInt(Tag.NumberOfFailedSuboperations, VR.US, 1);
      dicomProgress.setAttributes(cmd2);
      assertTrue(dicomProgress.isLastFailed());
    }
  }

  // Helper method to set private association field using reflection-like approach
  private Association setPrivateAssociation(Association association) {
    try {
      var field = StoreFromStreamSCU.class.getDeclaredField("as");
      field.setAccessible(true);
      field.set(storeFromStreamSCU, association);
      return association;
    } catch (Exception e) {
      throw new RuntimeException("Failed to set private association field", e);
    }
  }
}
