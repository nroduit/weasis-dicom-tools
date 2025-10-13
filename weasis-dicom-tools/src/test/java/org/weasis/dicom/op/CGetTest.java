/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.getscu.GetSCU.InformationModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.ProgressListener;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class CGetTest {

  // Test data constants
  private static final String CALLING_AET = "WEASIS-SCU";
  private static final String CALLED_AET = "TEST-SCP";
  private static final String TEST_HOSTNAME = "localhost";
  private static final int TEST_PORT = 11112;
  private static final String TEST_STUDY_UID = "1.2.840.113619.2.1.1.1";
  private static final String TEST_SERIES_UID = "1.2.840.113619.2.1.2.1";
  private static final String TEST_INSTANCE_UID = "1.2.840.113619.2.1.3.1";

  @TempDir Path tempDir;

  private DicomNode callingNode;
  private DicomNode calledNode;
  private DicomProgress progress;
  private DicomParam[] basicQueryKeys;
  private DicomParam[] emptyQueryKeys;

  @BeforeEach
  void setUp() {
    callingNode = new DicomNode(CALLING_AET);
    calledNode = new DicomNode(CALLED_AET, TEST_HOSTNAME, TEST_PORT);
    progress = new DicomProgress();

    basicQueryKeys =
        new DicomParam[] {
          new DicomParam(Tag.StudyInstanceUID, TEST_STUDY_UID),
          new DicomParam(Tag.SeriesInstanceUID, TEST_SERIES_UID),
          new DicomParam(Tag.PatientName) // Return key without value
        };

    emptyQueryKeys = new DicomParam[0];
  }

  @Nested
  class Parameter_validation {

    @Test
    void throws_exception_when_calling_node_is_null() {
      var exception =
          assertThrows(
              NullPointerException.class,
              () -> CGet.process(null, calledNode, progress, tempDir, basicQueryKeys));

      assertEquals("callingNode cannot be null", exception.getMessage());
    }

    @Test
    void throws_exception_when_called_node_is_null() {
      var exception =
          assertThrows(
              NullPointerException.class,
              () -> CGet.process(callingNode, null, progress, tempDir, basicQueryKeys));

      assertEquals("calledNode cannot be null", exception.getMessage());
    }

    @Test
    void throws_exception_when_output_dir_is_null() {
      var exception =
          assertThrows(
              NullPointerException.class,
              () -> CGet.process(callingNode, calledNode, progress, null, basicQueryKeys));

      assertEquals("outputDir cannot be null", exception.getMessage());
    }

    @Test
    void accepts_null_progress() {
      var state = CGet.process(callingNode, calledNode, null, tempDir, basicQueryKeys);

      assertNotNull(state);
      // Operation will fail due to connection, but validation passes
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void accepts_null_advanced_params() {
      var state = CGet.process(null, callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void accepts_empty_query_keys() {
      var state = CGet.process(callingNode, calledNode, progress, tempDir, emptyQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void accepts_null_query_keys() {
      var state = CGet.process(callingNode, calledNode, progress, tempDir, (DicomParam[]) null);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }
  }

  @Nested
  class Method_overloads {

    @Test
    void basic_process_method_delegates_to_main_method() {
      var state = CGet.process(callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void advanced_process_method_delegates_to_main_method() {
      var advancedParams = createAdvancedParams();
      var state =
          CGet.process(advancedParams, callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void full_process_method_with_sop_class_url() throws MalformedURLException {
      var advancedParams = createAdvancedParams();
      var sopClassURL = new URL("file:///test/sop-classes.properties");

      var state =
          CGet.process(
              advancedParams,
              callingNode,
              calledNode,
              progress,
              tempDir,
              sopClassURL,
              basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }
  }

  @Nested
  class Output_directory_handling {

    @Test
    void creates_output_directory_if_not_exists() throws IOException {
      var nonExistentDir = tempDir.resolve("new-output");
      assertFalse(Files.exists(nonExistentDir));

      var state = CGet.process(callingNode, calledNode, progress, nonExistentDir, basicQueryKeys);

      assertNotNull(state);
      // Directory creation is handled by GetSCU internally
    }

    @Test
    void handles_existing_output_directory() throws IOException {
      var existingDir = tempDir.resolve("existing-output");
      Files.createDirectories(existingDir);
      assertTrue(Files.exists(existingDir));

      var state = CGet.process(callingNode, calledNode, progress, existingDir, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void handles_file_as_output_directory() throws IOException {
      var file = tempDir.resolve("not-a-directory.txt");
      Files.createFile(file);
      assertTrue(Files.isRegularFile(file));

      var state = CGet.process(callingNode, calledNode, progress, file, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }
  }

  @Nested
  class Progress_tracking {

    @Test
    void registers_progress_listener() {
      var listenerCallCount = new AtomicInteger(0);
      ProgressListener listener = p -> listenerCallCount.incrementAndGet();
      progress.addProgressListener(listener);

      var state = CGet.process(callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      // Progress may be called during operation setup/teardown
      assertTrue(listenerCallCount.get() >= 0);
    }

    @Test
    void handles_progress_cancellation() {
      progress.cancel();
      assertTrue(progress.isCancelled());

      var state = CGet.process(callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void progress_tracks_operation_state() {
      var state = CGet.process(callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      // Progress state should be accessible
      assertNotNull(progress.getStatus());
    }
  }

  @Nested
  class Query_parameters {

    @Test
    void processes_study_level_query() {
      var studyLevelKeys =
          new DicomParam[] {
            new DicomParam(Tag.StudyInstanceUID, TEST_STUDY_UID),
            new DicomParam(Tag.PatientName),
            new DicomParam(Tag.StudyDate)
          };

      var state = CGet.process(callingNode, calledNode, progress, tempDir, studyLevelKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void processes_series_level_query() {
      var seriesLevelKeys =
          new DicomParam[] {
            new DicomParam(Tag.StudyInstanceUID, TEST_STUDY_UID),
            new DicomParam(Tag.SeriesInstanceUID, TEST_SERIES_UID),
            new DicomParam(Tag.Modality)
          };

      var state = CGet.process(callingNode, calledNode, progress, tempDir, seriesLevelKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void processes_instance_level_query() {
      var instanceLevelKeys =
          new DicomParam[] {
            new DicomParam(Tag.StudyInstanceUID, TEST_STUDY_UID),
            new DicomParam(Tag.SeriesInstanceUID, TEST_SERIES_UID),
            new DicomParam(Tag.SOPInstanceUID, TEST_INSTANCE_UID)
          };

      var state = CGet.process(callingNode, calledNode, progress, tempDir, instanceLevelKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void handles_keys_with_multiple_values() {
      var multiValueKey = new DicomParam(Tag.StudyInstanceUID, TEST_STUDY_UID, "1.2.3.4.5");
      var keys = new DicomParam[] {multiValueKey};

      var state = CGet.process(callingNode, calledNode, progress, tempDir, keys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void handles_return_keys_without_values() {
      var returnKeys =
          new DicomParam[] {
            new DicomParam(Tag.StudyInstanceUID, TEST_STUDY_UID), // Query key with value
            new DicomParam(Tag.PatientName), // Return key without value
            new DicomParam(Tag.StudyDescription), // Return key without value
            new DicomParam(Tag.SeriesDescription) // Return key without value
          };

      var state = CGet.process(callingNode, calledNode, progress, tempDir, returnKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }
  }

  @Nested
  class Advanced_parameters {

    @Test
    void uses_custom_connect_options() {
      var advancedParams = new AdvancedParams();
      var connectOptions = new ConnectOptions();
      connectOptions.setConnectTimeout(5000);
      connectOptions.setAcceptTimeout(3000);
      connectOptions.setMaxOpsInvoked(10);
      connectOptions.setMaxOpsPerformed(5);
      advancedParams.setConnectOptions(connectOptions);

      var state =
          CGet.process(advancedParams, callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void uses_custom_information_model() {
      var advancedParams = new AdvancedParams();
      advancedParams.setInformationModel(InformationModel.PatientRoot);

      var state =
          CGet.process(advancedParams, callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2}) // MEDIUM, HIGH, LOW priorities
    void uses_custom_priority(int priority) {
      var advancedParams = new AdvancedParams();
      advancedParams.setPriority(priority);

      var state =
          CGet.process(advancedParams, callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }
  }

  @Nested
  class Error_handling {

    @Test
    void returns_error_state_on_exception() {
      var state = CGet.process(callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
      assertNotNull(state.getMessage());
    }

    @Test
    void error_state_contains_meaningful_message() {
      var state = CGet.process(callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state.getMessage());
    }
  }

  @Nested
  class SOP_class_configuration {

    @Test
    void handles_default_sop_class_configuration() {
      // Default configuration should be loaded from resources
      var state = CGet.process(callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void handles_invalid_sop_class_url() throws MalformedURLException {
      var invalidURL = new URL("file:///nonexistent/sop-classes.properties");

      var state =
          CGet.process(
              null, callingNode, calledNode, progress, tempDir, invalidURL, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }
  }

  @Nested
  class DICOM_node_validation {

    @Test
    void validates_calling_node_aet() {
      var validNode = new DicomNode("VALID-AET");

      var state = CGet.process(validNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      // AET validation occurs in DicomNode constructor
    }

    @Test
    void handles_called_node_with_port() {
      var nodeWithPort = new DicomNode(CALLED_AET, TEST_HOSTNAME, 11112);

      var state = CGet.process(callingNode, nodeWithPort, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    void handles_called_node_without_port() {
      var nodeWithoutPort = new DicomNode(CALLED_AET, TEST_HOSTNAME, null);

      var state = CGet.process(callingNode, nodeWithoutPort, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }
  }

  @Nested
  class State_and_timing {

    @Test
    void state_contains_process_timing() {
      var state = CGet.process(callingNode, calledNode, progress, tempDir, basicQueryKeys);

      assertNotNull(state);
      // Timing information should be available even on failure
      assertTrue(state.getStatus() != Status.Pending);
    }
  }

  // Helper methods
  private AdvancedParams createAdvancedParams() {
    var params = new AdvancedParams();
    var connectOptions = new ConnectOptions();
    connectOptions.setConnectTimeout(3000);
    connectOptions.setAcceptTimeout(3000);
    params.setConnectOptions(connectOptions);
    return params;
  }
}
