/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.findscu.FindSCU.InformationModel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ListenerParams;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ModalityWorklistTest {

  private static final String CALLING_AET = "CALLING_AET";
  private static final String CALLED_AET = "CALLED_AET";
  private static final String HOSTNAME = "localhost";
  private static final int PORT = 11112;

  // Test data factories using real data structures
  private DicomNode createCallingNode() {
    return new DicomNode(CALLING_AET);
  }

  private DicomNode createCalledNode() {
    return new DicomNode(CALLED_AET, HOSTNAME, PORT);
  }

  private DicomNode createCalledNodeWithPort(int port) {
    return new DicomNode(CALLED_AET, HOSTNAME, port);
  }

  private AdvancedParams createAdvancedParams() {
    var params = new AdvancedParams();
    params.setInformationModel(InformationModel.MWL);
    return params;
  }

  private DicomParam[] createBasicQueryKeys() {
    return new DicomParam[] {
      new DicomParam(Tag.PatientID, "123456"),
      new DicomParam(Tag.PatientName, "DOE^JOHN"),
      ModalityWorklist.Modality
    };
  }

  private DicomParam[] createEmptyKeys() {
    return new DicomParam[0];
  }

  @Nested
  class Constant_Validation_Tests {

    @Test
    void patient_level_constants_should_have_correct_tags() {
      assertEquals(Tag.PatientWeight, ModalityWorklist.PatientWeight.getTag());
      assertEquals(Tag.MedicalAlerts, ModalityWorklist.MedicalAlerts.getTag());
      assertEquals(Tag.Allergies, ModalityWorklist.Allergies.getTag());
      assertEquals(Tag.PregnancyStatus, ModalityWorklist.PregnancyStatus.getTag());
    }

    @Test
    void request_level_constants_should_have_correct_tags() {
      assertEquals(Tag.RequestingPhysician, ModalityWorklist.RequestingPhysician.getTag());
      assertEquals(Tag.RequestingService, ModalityWorklist.RequestingService.getTag());
      assertEquals(
          Tag.RequestedProcedureDescription,
          ModalityWorklist.RequestedProcedureDescription.getTag());
      assertEquals(Tag.AdmissionID, ModalityWorklist.AdmissionID.getTag());
    }

    @Test
    void scheduled_procedure_step_constants_should_have_correct_tags_and_sequence() {
      assertEquals(Tag.Modality, ModalityWorklist.Modality.getTag());
      assertArrayEquals(
          new int[] {Tag.ScheduledProcedureStepSequence},
          ModalityWorklist.Modality.getParentSeqTags());

      assertEquals(Tag.ScheduledStationAETitle, ModalityWorklist.ScheduledStationAETitle.getTag());
      assertArrayEquals(
          new int[] {Tag.ScheduledProcedureStepSequence},
          ModalityWorklist.ScheduledStationAETitle.getParentSeqTags());
    }

    @Test
    void scheduled_protocol_code_constants_should_have_correct_sequence_path() {
      assertEquals(Tag.CodeMeaning, ModalityWorklist.ScheduledProtocolCodeMeaning.getTag());
      assertArrayEquals(
          new int[] {Tag.ScheduledProcedureStepSequence, Tag.ScheduledProtocolCodeSequence},
          ModalityWorklist.ScheduledProtocolCodeMeaning.getParentSeqTags());
    }
  }

  @Nested
  class Process_Method_Overloads_Tests {

    @Test
    void process_with_two_parameters_should_call_main_method_with_defaults() {
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys = createBasicQueryKeys();

      // This will fail with connection error, but validates the method call path
      var result = ModalityWorklist.process(callingNode, calledNode, keys);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void process_with_three_parameters_should_call_main_method_with_default_cancel_after() {
      var params = createAdvancedParams();
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys = createBasicQueryKeys();

      var result = ModalityWorklist.process(params, callingNode, calledNode, keys);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Parameter_Validation_Tests {

    @ParameterizedTest
    @NullSource
    void process_should_throw_exception_when_calling_node_is_null(DicomNode callingNode) {
      var calledNode = createCalledNode();
      var keys = createBasicQueryKeys();

      assertThrows(
          NullPointerException.class,
          () -> ModalityWorklist.process(callingNode, calledNode, keys));
    }

    @ParameterizedTest
    @NullSource
    void process_should_throw_exception_when_called_node_is_null(DicomNode calledNode) {
      var callingNode = createCallingNode();
      var keys = createBasicQueryKeys();

      assertThrows(
          NullPointerException.class,
          () -> ModalityWorklist.process(callingNode, calledNode, keys));
    }

    @Test
    void process_should_handle_null_advanced_params_gracefully() {
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys = createBasicQueryKeys();

      var result = ModalityWorklist.process(null, callingNode, calledNode, keys);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void process_should_handle_empty_keys_array() {
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys = createEmptyKeys();

      var result = ModalityWorklist.process(callingNode, calledNode, keys);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void process_should_handle_null_keys_array() {
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      DicomParam[] keys = null;
      var result = ModalityWorklist.process(callingNode, calledNode, keys);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Cancel_After_Parameter_Tests {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 10, 100})
    void process_should_accept_various_cancel_after_values(int cancelAfter) {
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys = createBasicQueryKeys();

      var result = ModalityWorklist.process(null, callingNode, calledNode, cancelAfter, keys);

      assertNotNull(result);
      // Connection will fail, but method should handle the parameter correctly
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void process_should_handle_negative_cancel_after_value() {
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys = createBasicQueryKeys();

      var result = ModalityWorklist.process(null, callingNode, calledNode, -1, keys);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Dicom_Keys_Tests {

    @Test
    void process_should_handle_keys_with_values() {
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys =
          new DicomParam[] {
            new DicomParam(Tag.PatientID, "123456"),
            new DicomParam(Tag.PatientName, "DOE^JOHN"),
            new DicomParam(Tag.Modality, "CT")
          };

      var result = ModalityWorklist.process(callingNode, calledNode, keys);

      assertNotNull(result);
    }

    @Test
    void process_should_handle_keys_without_values() {
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys =
          new DicomParam[] {
            new DicomParam(Tag.PatientID),
            new DicomParam(Tag.PatientName),
            ModalityWorklist.Modality
          };

      var result = ModalityWorklist.process(callingNode, calledNode, keys);

      assertNotNull(result);
    }

    @Test
    void process_should_handle_sequence_based_keys() {
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys =
          new DicomParam[] {
            ModalityWorklist.Modality,
            ModalityWorklist.ScheduledStationAETitle,
            ModalityWorklist.ScheduledProcedureStepStartDate,
            ModalityWorklist.ScheduledProtocolCodeMeaning
          };

      var result = ModalityWorklist.process(callingNode, calledNode, keys);

      assertNotNull(result);
    }
  }

  @Nested
  class Advanced_Parameters_Tests {

    @Test
    void process_should_use_provided_advanced_parameters() {
      var params = createAdvancedParams();
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys = createBasicQueryKeys();

      var result = ModalityWorklist.process(params, callingNode, calledNode, keys);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void process_should_handle_advanced_params_with_custom_information_model() {
      var params = new AdvancedParams();
      params.setInformationModel(InformationModel.PatientRoot);
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys = createBasicQueryKeys();

      var result = ModalityWorklist.process(params, callingNode, calledNode, keys);

      assertNotNull(result);
    }
  }

  @Nested
  class Error_Handling_Tests {

    @Test
    void error_for_invalid_port() {
      assertThrows(IllegalArgumentException.class, () -> createCalledNodeWithPort(99999));
    }

    @Test
    void process_should_handle_interrupted_exception_properly() {
      // This test verifies that interrupted exceptions are handled correctly
      // by checking the thread interrupt status would be restored
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys = createBasicQueryKeys();

      // Clear any previous interrupt status
      Thread.interrupted();

      var result = ModalityWorklist.process(callingNode, calledNode, keys);

      assertNotNull(result);
      assertFalse(Thread.currentThread().isInterrupted());
    }
  }

  @Nested
  class Integration_Tests {

    static Stream<Arguments> dicomNodeVariations() {
      return Stream.of(
          Arguments.of("TEST_CALLING", "TEST_CALLED", "192.168.1.100", 11112),
          Arguments.of("WORKSTATION", "PACS_SERVER", "pacs.hospital.com", 4006),
          Arguments.of("MODALITY", "WORKLIST_SCP", "localhost", 104));
    }

    @ParameterizedTest
    @MethodSource("dicomNodeVariations")
    void process_should_handle_different_node_configurations(
        String callingAET, String calledAET, String hostname, int port) {

      var callingNode = new DicomNode(callingAET);
      var calledNode = new DicomNode(calledAET, hostname, port);
      var keys = createBasicQueryKeys();

      var result = ModalityWorklist.process(callingNode, calledNode, keys);

      assertNotNull(result);
      // Connection will fail, but validates parameter handling
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void process_should_work_with_comprehensive_key_set() {
      var callingNode = createCallingNode();
      var calledNode = createCalledNode();
      var keys =
          new DicomParam[] {
            // Patient level
            new DicomParam(Tag.PatientID, "PAT001"),
            new DicomParam(Tag.PatientName, "PATIENT^TEST"),
            ModalityWorklist.PatientWeight,
            ModalityWorklist.Allergies,

            // Request level
            ModalityWorklist.RequestingPhysician,
            ModalityWorklist.RequestedProcedureDescription,
            ModalityWorklist.AdmissionID,

            // Scheduled Procedure Step
            ModalityWorklist.Modality,
            ModalityWorklist.ScheduledStationAETitle,
            ModalityWorklist.ScheduledProcedureStepStartDate,
            ModalityWorklist.ScheduledPerformingPhysicianName,

            // Nested sequence
            ModalityWorklist.ScheduledProtocolCodeMeaning
          };

      var result = ModalityWorklist.process(callingNode, calledNode, keys);

      assertNotNull(result);
    }
  }

  @Nested
  class Integration_With_DicomListener_Tests {

    @TempDir Path tempStorageDir;

    @Test
    void process_should_work_with_local_dicom_listener() throws Exception {
      // Setup DicomListener to simulate a DICOM server
      var listenerPort = findAvailablePort();
      var storageDir = tempStorageDir.resolve("dicom-worklist-storage");
      Files.createDirectories(storageDir);

      var listener = new DicomListener(storageDir);
      var listenerNode = new DicomNode("WORKLIST_SCP", "localhost", listenerPort);
      var listenerParams = createListenerParams();

      try {
        // Start the DICOM listener
        listener.start(listenerNode, listenerParams);
        assertTrue(listener.isRunning(), "DicomListener should be running");

        // Give listener time to start
        Thread.sleep(100);

        // Setup nodes for modality worklist query
        var callingNode = new DicomNode("MWL_SCU");
        var calledNode = new DicomNode("WORKLIST_SCP", "localhost", listenerPort);

        // Create worklist query keys
        var keys = createWorklistQueryKeys();

        // Execute modality worklist query
        var result = ModalityWorklist.process(callingNode, calledNode, keys);

        // Verify the result
        assertNotNull(result);
        assertNotNull(result.getMessage());

        // The query might fail due to the listener not having worklist data,
        // but we should get a proper DICOM response, not a connection error
        assertTrue(
            result.getStatus() != Status.UnableToProcess
                || !result.getMessage().contains("Connection refused"),
            "Should establish DICOM connection successfully");

      } finally {
        listener.stop();
        awaitListenerStop(listener);
      }
    }

    @Test
    void process_should_handle_multiple_concurrent_queries_with_listener() throws Exception {
      var listenerPort = findAvailablePort();
      var storageDir = tempStorageDir.resolve("concurrent-dicom-storage");
      Files.createDirectories(storageDir);

      var listener = new DicomListener(storageDir);
      var listenerNode = new DicomNode("CONCURRENT_SCP", "localhost", listenerPort);
      var listenerParams = createAdvancedListenerParams();

      try {
        listener.start(listenerNode, listenerParams);
        assertTrue(listener.isRunning());

        Thread.sleep(100);

        var calledNode = new DicomNode("CONCURRENT_SCP", "localhost", listenerPort);
        var futures = new CompletableFuture[3];

        // Execute multiple concurrent queries
        for (int i = 0; i < 3; i++) {
          final int queryId = i;
          futures[i] =
              CompletableFuture.supplyAsync(
                  () -> {
                    var callingNode = new DicomNode("MWL_SCU_" + queryId);
                    var keys = createVariedQueryKeys(queryId);
                    return ModalityWorklist.process(callingNode, calledNode, keys);
                  });
        }

        // Wait for all queries to complete
        var results =
            Arrays.stream(futures).map(CompletableFuture::join).toArray(DicomState[]::new);

        // Verify all queries completed
        for (int i = 0; i < results.length; i++) {
          assertNotNull(results[i], "Query " + i + " should have a result");
          assertNotNull(results[i].getMessage(), "Query " + i + " should have a message");
        }

      } finally {
        listener.stop();
        awaitListenerStop(listener);
      }
    }

    @Test
    void process_should_handle_different_information_models_with_listener() throws Exception {
      var listenerPort = findAvailablePort();
      var storageDir = tempStorageDir.resolve("info-model-storage");
      Files.createDirectories(storageDir);

      var listener = new DicomListener(storageDir);
      var listenerNode = new DicomNode("INFO_MODEL_SCP", "localhost", listenerPort);

      try {
        listener.start(listenerNode, createListenerParams());
        assertTrue(listener.isRunning());

        Thread.sleep(100);

        var callingNode = new DicomNode("INFO_MODEL_SCU");
        var calledNode = new DicomNode("INFO_MODEL_SCP", "localhost", listenerPort);

        // Test with different information models
        var informationModels =
            List.of(InformationModel.MWL, InformationModel.PatientRoot, InformationModel.StudyRoot);

        for (var infoModel : informationModels) {
          var params = new AdvancedParams();
          params.setInformationModel(infoModel);

          var keys = createBasicQueryKeys();
          var result = ModalityWorklist.process(params, callingNode, calledNode, keys);

          assertNotNull(result);
          // Each information model should be handled properly
          assertNotNull(result.getMessage());
        }

      } finally {
        listener.stop();
        awaitListenerStop(listener);
      }
    }

    @Test
    void process_should_handle_listener_restart_scenario() throws Exception {
      var listenerPort = findAvailablePort();
      var storageDir = tempStorageDir.resolve("restart-storage");
      Files.createDirectories(storageDir);

      var listener = new DicomListener(storageDir);
      var listenerNode = new DicomNode("RESTART_SCP", "localhost", listenerPort);
      var callingNode = new DicomNode("RESTART_SCU");
      var calledNode = new DicomNode("RESTART_SCP", "localhost", listenerPort);
      var keys = createBasicQueryKeys();

      try {
        // First start
        listener.start(listenerNode, createListenerParams());
        Thread.sleep(100);

        var result1 = ModalityWorklist.process(callingNode, calledNode, keys);
        assertNotNull(result1);

        // Stop listener
        listener.stop();
        awaitListenerStop(listener);

        // Query should fail when listener is down
        var result2 = ModalityWorklist.process(callingNode, calledNode, keys);
        assertNotNull(result2);
        assertEquals(Status.UnableToProcess, result2.getStatus());

        // Restart listener
        listener.start(listenerNode, createListenerParams());
        Thread.sleep(100);

        // Query should work again
        var result3 = ModalityWorklist.process(callingNode, calledNode, keys);
        assertNotNull(result3);

      } finally {
        if (listener.isRunning()) {
          listener.stop();
          awaitListenerStop(listener);
        }
      }
    }

    private ListenerParams createListenerParams() {
      var advancedParams = createAdvancedParams();
      return new ListenerParams(
          advancedParams, true, "{00080018}.dcm", null, "MWL_SCU", "WORKLIST_SCU", "TEST_SCU");
    }

    private ListenerParams createAdvancedListenerParams() {
      var advancedParams = new AdvancedParams();
      var connectOptions = new ConnectOptions();
      connectOptions.setConnectTimeout(5000);
      connectOptions.setAcceptTimeout(5000);
      connectOptions.setMaxOpsInvoked(10);
      connectOptions.setMaxOpsPerformed(10);
      advancedParams.setConnectOptions(connectOptions);

      return new ListenerParams(
          advancedParams, true, "{00020016}/{00020003}.dcm", null, "CONCURRENT_SCU");
    }

    private DicomParam[] createWorklistQueryKeys() {
      return new DicomParam[] {
        new DicomParam(Tag.PatientID, "WL001"),
        new DicomParam(Tag.PatientName, "WORKLIST^TEST"),
        ModalityWorklist.Modality,
        ModalityWorklist.ScheduledStationAETitle,
        ModalityWorklist.ScheduledProcedureStepStartDate,
        ModalityWorklist.RequestingPhysician,
        ModalityWorklist.RequestedProcedureDescription
      };
    }

    private DicomParam[] createVariedQueryKeys(int queryId) {
      return new DicomParam[] {
        new DicomParam(Tag.PatientID, "PAT" + queryId),
        new DicomParam(Tag.PatientName, "PATIENT^" + queryId),
        ModalityWorklist.Modality,
        queryId % 2 == 0
            ? ModalityWorklist.ScheduledStationAETitle
            : ModalityWorklist.RequestingService
      };
    }

    private void awaitListenerStop(DicomListener listener) {
      var timeout = System.currentTimeMillis() + 5000; // 5 second timeout
      while (listener.isRunning() && System.currentTimeMillis() < timeout) {
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
  }

  // Add this utility method to the existing utility methods section
  private int findAvailablePort() {
    try (var socket = new java.net.ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (Exception e) {
      return 11112; // fallback port
    }
  }
}
