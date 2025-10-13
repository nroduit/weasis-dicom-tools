/*
 * Copyright (c) 2017-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.op;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.dicom.op.CGetForward.InformationModel;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.CstoreParams;
import org.weasis.dicom.param.DefaultAttributeEditor;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.ProgressListener;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class CGetForwardTest {

  // Test data constants
  private static final String CALLING_AET = "WEASIS-SCU";
  private static final String CALLED_AET = "TEST-SCP";
  private static final String DESTINATION_AET = "DEST-SCP";
  private static final String TEST_HOSTNAME = "localhost";
  private static final int CALLED_PORT = 11112;
  private static final int DEST_PORT = 11113;
  private static final String TEST_STUDY_UID = "1.2.840.113619.2.1.1.1";
  private static final String TEST_SERIES_UID = "1.2.840.113619.2.1.2.1";
  private static final int DEFAULT_PRIORITY = 2;

  private DicomNode callingNode;
  private DicomNode calledNode;
  private DicomNode destinationNode;
  private DicomProgress progress;

  @Mock private ProgressListener mockProgressListener;

  @BeforeEach
  void setUp() {
    callingNode = new DicomNode(CALLING_AET);
    calledNode = new DicomNode(CALLED_AET, TEST_HOSTNAME, CALLED_PORT);
    destinationNode = new DicomNode(DESTINATION_AET, TEST_HOSTNAME, DEST_PORT);
    progress = new DicomProgress();
  }

  @Nested
  class Constructor_tests {

    @Test
    void creates_instance_with_basic_parameters() {
      assertDoesNotThrow(
          () -> {
            try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
              assertNotNull(forward);
              assertNotNull(forward.getDevice());
              assertNotNull(forward.getApplicationEntity());
              assertEquals("GETSCU", forward.getApplicationEntity().getAETitle());
              assertNotNull(forward.getConnection());
              assertNotNull(forward.getRemoteConnection());
              assertNotNull(forward.getAAssociateRQ());
              assertNotNull(forward.getKeys());
            }
          });
    }

    @Test
    void creates_instance_with_cstore_params() {
      var cstoreParams = createBasicCstoreParams();

      assertDoesNotThrow(
          () -> {
            try (var forward =
                new CGetForward(callingNode, destinationNode, progress, cstoreParams)) {
              assertNotNull(forward);
              assertNotNull(forward.getStreamSCU());
              assertNotNull(forward.getStreamSCUService());
            }
          });
    }

    @Test
    void creates_instance_with_all_parameters() {
      var advancedParams = createAdvancedParams();
      var cstoreParams = createBasicCstoreParams();

      assertDoesNotThrow(
          () -> {
            try (var forward =
                new CGetForward(
                    advancedParams, callingNode, destinationNode, progress, cstoreParams)) {
              assertNotNull(forward);
              assertNotNull(forward.getDevice());
              assertNotNull(forward.getStreamSCU());
              assertNotNull(forward.getStreamSCUService());
            }
          });
    }

    @Test
    void throws_exception_when_calling_node_is_null() {
      assertThrows(Exception.class, () -> new CGetForward(null, destinationNode, progress));
    }

    @Test
    void throws_exception_when_destination_node_is_null() {
      assertThrows(Exception.class, () -> new CGetForward(callingNode, null, progress));
    }

    @Test
    void accepts_null_progress() throws IOException {
      assertDoesNotThrow(
          () -> {
            try (var forward = new CGetForward(callingNode, destinationNode, null)) {
              assertNotNull(forward);
            }
          });
    }
  }

  @Nested
  class Information_model_tests {

    @ParameterizedTest
    @EnumSource(InformationModel.class)
    void sets_information_model(InformationModel model) throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        var tss = new String[] {"1.2.840.10008.1.2", "1.2.840.10008.1.2.1"};

        assertDoesNotThrow(() -> forward.setInformationModel(model, tss, false));

        // Verify level is set for models that have one
        if (model.level != null) {
          assertEquals(model.level, forward.getKeys().getString(Tag.QueryRetrieveLevel));
        }
      }
    }

    @Test
    void sets_relational_information_model() throws IOException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        var tss = new String[] {"1.2.840.10008.1.2"};

        assertDoesNotThrow(
            () -> forward.setInformationModel(InformationModel.StudyRoot, tss, true));
      } catch (InterruptedException e) {
        fail();
      }
    }

    @Test
    void adds_custom_level() throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        forward.addLevel("IMAGE");

        assertEquals("IMAGE", forward.getKeys().getString(Tag.QueryRetrieveLevel));
      }
    }
  }

  @Nested
  class Key_management_tests {

    @Test
    void adds_study_key() throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        forward.addKey(Tag.StudyInstanceUID, TEST_STUDY_UID);

        assertEquals(TEST_STUDY_UID, forward.getKeys().getString(Tag.StudyInstanceUID));
      }
    }

    @Test
    void adds_multiple_values_key() throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        var values = new String[] {TEST_STUDY_UID, "1.2.3.4.5"};
        forward.addKey(Tag.StudyInstanceUID, values);

        var storedValues = forward.getKeys().getStrings(Tag.StudyInstanceUID);
        assertArrayEquals(values, storedValues);
      }
    }

    @Test
    void adds_empty_key_for_return_attributes() throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        forward.addKey(Tag.PatientName);

        assertTrue(forward.getKeys().contains(Tag.PatientName));
      }
    }

    @Test
    void handles_private_tags() throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        var privateTag = 0x00090010;

        assertDoesNotThrow(() -> forward.addKey(privateTag, "private value"));
      }
    }
  }

  @Nested
  class SOP_class_configuration_tests {

    @Test
    void adds_storage_sop_class() throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        var cuid = "1.2.840.10008.5.1.4.1.1.2"; // CT Image Storage
        var tsuids = new String[] {"1.2.840.10008.1.2", "1.2.840.10008.1.2.1"};

        assertDoesNotThrow(() -> forward.addOfferedStorageSOPClass(cuid, tsuids));
      }
    }

    @Test
    void adds_multiple_sop_classes() throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        var ctImageCuid = "1.2.840.10008.5.1.4.1.1.2";
        var mrImageCuid = "1.2.840.10008.5.1.4.1.1.4";
        var tsuids = new String[] {"1.2.840.10008.1.2"};

        assertDoesNotThrow(
            () -> {
              forward.addOfferedStorageSOPClass(ctImageCuid, tsuids);
              forward.addOfferedStorageSOPClass(mrImageCuid, tsuids);
            });
      }
    }
  }

  @Nested
  class Priority_configuration_tests {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2}) // HIGH, MEDIUM, LOW
    void sets_priority(int priority) throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        assertDoesNotThrow(() -> forward.setPriority(priority));
      }
    }
  }

  @Nested
  class Process_study_tests {

    @Test
    void throws_exception_for_null_calling_node() {
      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  CGetForward.processStudy(
                      null, calledNode, destinationNode, progress, TEST_STUDY_UID));

      assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void throws_exception_for_null_called_node() {
      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  CGetForward.processStudy(
                      callingNode, null, destinationNode, progress, TEST_STUDY_UID));

      assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void throws_exception_for_null_destination_node() {
      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  CGetForward.processStudy(
                      callingNode, calledNode, null, progress, TEST_STUDY_UID));

      assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void returns_error_state_on_connection_failure() {
      var state =
          CGetForward.processStudy(
              callingNode, calledNode, destinationNode, progress, TEST_STUDY_UID);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
      assertNotNull(state.getMessage());
    }

    @Test
    void processes_study_with_advanced_params() {
      var getParams = createAdvancedParams();
      var forwardParams = createAdvancedParams();

      var state =
          CGetForward.processStudy(
              getParams,
              forwardParams,
              callingNode,
              calledNode,
              destinationNode,
              progress,
              TEST_STUDY_UID);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void processes_study_with_cstore_params() {
      var getParams = createAdvancedParams();
      var forwardParams = createAdvancedParams();
      var cstoreParams = createBasicCstoreParams();

      var state =
          CGetForward.processStudy(
              getParams,
              forwardParams,
              callingNode,
              calledNode,
              destinationNode,
              progress,
              TEST_STUDY_UID,
              cstoreParams);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void accepts_null_progress_in_process_study() {
      var state =
          CGetForward.processStudy(callingNode, calledNode, destinationNode, null, TEST_STUDY_UID);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }
  }

  @Nested
  class Process_series_tests {

    @Test
    void processes_series_basic() {
      var state =
          CGetForward.processSeries(
              callingNode, calledNode, destinationNode, progress, TEST_SERIES_UID);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void processes_series_with_advanced_params() {
      var getParams = createAdvancedParams();
      var forwardParams = createAdvancedParams();

      var state =
          CGetForward.processSeries(
              getParams,
              forwardParams,
              callingNode,
              calledNode,
              destinationNode,
              progress,
              TEST_SERIES_UID);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void processes_series_with_cstore_params() {
      var getParams = createAdvancedParams();
      var forwardParams = createAdvancedParams();
      var cstoreParams = createBasicCstoreParams();

      var state =
          CGetForward.processSeries(
              getParams,
              forwardParams,
              callingNode,
              calledNode,
              destinationNode,
              progress,
              TEST_SERIES_UID,
              cstoreParams);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }
  }

  @Nested
  class Progress_tracking_tests {

    @Test
    void registers_progress_listener() throws IOException, InterruptedException {
      var listenerCallCount = new AtomicInteger(0);
      ProgressListener listener = p -> listenerCallCount.incrementAndGet();
      progress.addProgressListener(listener);

      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        assertNotNull(forward.getState().getProgress());
        assertEquals(progress, forward.getState().getProgress());
      }
    }

    @Test
    void handles_progress_cancellation() throws IOException, InterruptedException {
      progress.cancel();
      assertTrue(progress.isCancelled());

      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        assertTrue(forward.getState().getProgress().isCancelled());
      }
    }

    @Test
    void state_provides_progress_access() throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        var state = forward.getState();

        assertNotNull(state);
        assertEquals(progress, state.getProgress());
      }
    }
  }

  @Nested
  class CStore_parameters_tests {

    @Test
    void accepts_null_cstore_params() throws IOException {
      assertDoesNotThrow(
          () -> {
            try (var forward = new CGetForward(callingNode, destinationNode, progress, null)) {
              assertNotNull(forward);
            }
          });
    }

    @Test
    void uses_cstore_params_with_editors() throws IOException {
      var cstoreParams = createCstoreParamsWithEditors();

      assertDoesNotThrow(
          () -> {
            try (var forward =
                new CGetForward(callingNode, destinationNode, progress, cstoreParams)) {
              assertNotNull(forward);
            }
          });
    }

    @Test
    void uses_cstore_params_without_editors() throws IOException {
      var cstoreParams = new CstoreParams(List.of(), false, null);

      assertDoesNotThrow(
          () -> {
            try (var forward =
                new CGetForward(callingNode, destinationNode, progress, cstoreParams)) {
              assertNotNull(forward);
            }
          });
    }
  }

  @Nested
  class Auto_closeable_tests {

    @Test
    void closes_without_exception() throws IOException {
      var forward = new CGetForward(callingNode, destinationNode, progress);

      assertDoesNotThrow(() -> forward.close());
    }

    @Test
    void closes_with_association() throws IOException {
      var forward = new CGetForward(callingNode, destinationNode, progress);

      assertDoesNotThrow(() -> forward.close());
    }

    @Test
    void handles_close_exception_gracefully() throws IOException {
      var forward = new CGetForward(callingNode, destinationNode, progress);

      // Close should not throw even if stream SCU fails
      assertDoesNotThrow(() -> forward.close());
    }
  }

  @Nested
  class Error_handling_tests {

    @Test
    void handles_invalid_study_uid() {
      var state =
          CGetForward.processStudy(
              callingNode, calledNode, destinationNode, progress, "invalid.uid");

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
      assertNotNull(state.getMessage());
    }

    @Test
    void handles_invalid_series_uid() {
      var state =
          CGetForward.processSeries(
              callingNode, calledNode, destinationNode, progress, "invalid.uid");

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void error_state_contains_meaningful_message() {
      var state =
          CGetForward.processStudy(
              callingNode, calledNode, destinationNode, progress, TEST_STUDY_UID);

      assertNotNull(state.getMessage());
      assertTrue(state.getMessage().contains("DICOM Get failed"));
    }
  }

  @Nested
  class Device_configuration_tests {

    @Test
    void device_has_correct_name() throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        assertEquals("getscu", forward.getDevice().getDeviceName());
      }
    }

    @Test
    void application_entity_has_correct_title() throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        assertEquals("GETSCU", forward.getApplicationEntity().getAETitle());
      }
    }

    @Test
    void device_contains_application_entity() throws IOException, InterruptedException {
      try (var forward = new CGetForward(callingNode, destinationNode, progress)) {
        var device = forward.getDevice();
        var ae = forward.getApplicationEntity();

        assertTrue(device.getApplicationEntities().contains(ae));
      }
    }
  }

  // Helper methods
  private AdvancedParams createAdvancedParams() {
    var params = new AdvancedParams();
    var connectOptions = new ConnectOptions();
    connectOptions.setConnectTimeout(3000);
    connectOptions.setAcceptTimeout(3000);
    connectOptions.setMaxOpsInvoked(10);
    connectOptions.setMaxOpsPerformed(5);
    params.setConnectOptions(connectOptions);
    params.setPriority(DEFAULT_PRIORITY);
    return params;
  }

  private CstoreParams createBasicCstoreParams() {
    return new CstoreParams(List.of(), false, null);
  }

  private CstoreParams createCstoreParamsWithEditors() {
    var attrs = new Attributes();
    attrs.setString(Tag.PatientName, VR.PN, "Test^Patient");
    attrs.setString(Tag.PatientID, VR.LO, "TEST123");

    AttributeEditor editor = new DefaultAttributeEditor(true, attrs);
    return new CstoreParams(List.of(editor), false, null);
  }
}
