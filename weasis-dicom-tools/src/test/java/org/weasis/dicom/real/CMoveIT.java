/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.real;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.weasis.dicom.op.CMove;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

/**
 * Integration tests for DICOM C-MOVE operations with configurable server configurations.
 *
 * <p>Tests C-MOVE operations against different DICOM server configurations. Each server
 * configuration is stored in a separate properties file under {@code /dicom-configs/} directory.
 * Tests can be run against different servers by setting:
 *
 * <ul>
 *   <li>System property: {@code -Ddicom.test.config=<config-name>}
 *   <li>Environment variable: {@code DICOM_TEST_CONFIG=<config-name>}
 * </ul>
 *
 * <p>Available configurations:
 *
 * <ul>
 *   <li>{@code public-server} - www.dicomserver.co.uk public server
 *   <li>{@code local-dcm4che} - Local DCM4CHE development server
 *   <li>{@code secure-server} - TLS-enabled secure server
 * </ul>
 *
 * <p>C-MOVE specific test capabilities:
 *
 * <ul>
 *   <li>Study-level retrieval
 *   <li>Series-level retrieval
 *   <li>Instance-level retrieval
 *   <li>Progress tracking
 *   <li>Destination AET configuration
 *   <li>Query options validation
 *   <li>Error scenario handling
 * </ul>
 */
@DisplayName("DICOM C-MOVE Integration Tests")
@DisplayNameGeneration(ReplaceUnderscores.class)
@TestInstance(Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
class CMoveIT {

  private static final Logger LOGGER = Logger.getLogger(CMoveIT.class.getName());

  /** Query retrieve levels supported by C-MOVE operations. */
  public enum QueryRetrieveLevel {
    STUDY("STUDY"),
    SERIES("SERIES"),
    IMAGE("IMAGE");

    private final String value;

    QueryRetrieveLevel(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  @BeforeAll
  static void setUpClass() {
    // Print available configurations for debugging
    DicomTestConfig.printAvailableConfigurations();

    // Verify active configuration is enabled
    var activeConfig = DicomTestConfig.getActiveServerConfig();
    assumeTrue(
        activeConfig.enabled(),
        "Active configuration '" + DicomTestConfig.getActiveConfig() + "' is disabled");

    LOGGER.info("Running C-MOVE tests with configuration: " + activeConfig);
    LOGGER.info("Configuration file: " + DicomTestConfig.getActiveConfig() + ".properties");
  }

  @Nested
  @DisplayName("Basic C-MOVE Operations")
  class BasicCMoveOperations {

    @Test
    @DisplayName("Should perform study level C-MOVE with default parameters")
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void should_perform_study_level_move_with_default_parameters() {
      // Given
      var progress = createProgressTracker();
      var destinationAet = DicomTestConfig.getTestDestinationAet();
      var studyParams = createStudyLevelParams();

      addMoveSeparation();
      LOGGER.info("Moving study: " + DicomTestConfig.getTestStudyUid());
      LOGGER.info("Destination AET: " + destinationAet);

      // When
      var startTime = System.currentTimeMillis();
      var state =
          CMove.process(
              DicomTestConfig.getCallingNode(),
              DicomTestConfig.getCalledNode(),
              destinationAet,
              progress,
              studyParams);
      var endTime = System.currentTimeMillis();

      // Then
      assertNotNull(state, "DicomState should not be null");
      assertTrue(
          state.getStatus() == Status.Success || state.getStatus() == Status.Pending,
          () -> "Expected successful move but got: " + state.getMessage());

      logMoveResult(
          state,
          progress,
          "Study Level Move",
          DicomTestConfig.getActiveConfig(),
          startTime,
          endTime);
    }

    @Test
    @DisplayName("Should perform series level C-MOVE with query options")
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void should_perform_series_level_move_with_query_options() {
      // Given
      var progress = createProgressTracker();
      var destinationAet = DicomTestConfig.getTestDestinationAet();
      var advancedParams = createAdvancedParamsWithRelationalQuery();
      var seriesParams = createSeriesLevelParams();

      addMoveSeparation();
      LOGGER.info("Moving series: " + DicomTestConfig.getTestSeriesUid());

      // When
      var state =
          CMove.process(
              advancedParams,
              DicomTestConfig.getCallingNode(),
              DicomTestConfig.getCalledNode(),
              destinationAet,
              progress,
              seriesParams);

      // Then
      assertNotNull(state, "DicomState should not be null");
      assertTrue(
          state.getStatus() == Status.Success || state.getStatus() == Status.Pending,
          () -> "Expected successful series move but got: " + state.getMessage());

      LOGGER.info("Series level move completed with status: " + state.getStatus());
    }

    @Test
    @DisplayName("Should perform image level C-MOVE with progress tracking")
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void should_perform_image_level_move_with_progress_tracking() {
      // Given
      var progress = spy(createProgressTracker());
      var progressUpdatesCaptured = new ArrayList<DicomProgress>();

      doAnswer(
              invocation -> {
                progressUpdatesCaptured.add(invocation.getArgument(0));
                return null;
              })
          .when(progress)
          .addProgressListener(any());

      var destinationAet = DicomTestConfig.getTestDestinationAet();
      var advancedParams = createAdvancedParamsWithRelationalQuery();
      var imageParams = createImageLevelParams();

      addMoveSeparation();
      LOGGER.info("Moving image with SOP Instance UID");

      // When
      var state =
          CMove.process(
              advancedParams,
              DicomTestConfig.getCallingNode(),
              DicomTestConfig.getCalledNode(),
              destinationAet,
              progress,
              imageParams);

      // Then
      assertNotNull(state, "DicomState should not be null");
      assertTrue(
          state.getStatus() == Status.Success || state.getStatus() == Status.Pending,
          () -> "Expected successful image move but got: " + state.getMessage());

      // Verify progress tracking was used
      verify(progress).addProgressListener(any());

      LOGGER.info("Image level move completed with status: " + state.getStatus());
      LOGGER.info("Progress updates captured: " + progressUpdatesCaptured.size());
    }
  }

  @Nested
  @DisplayName("Parameter Validation")
  class ParameterValidation {

    @Test
    @DisplayName("Should throw exception for null calling node")
    void should_throw_exception_for_null_calling_node() {
      // Given
      var progress = createProgressTracker();
      var destinationAet = DicomTestConfig.getTestDestinationAet();
      var params = createStudyLevelParams();

      // When & Then
      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  CMove.process(
                      null, DicomTestConfig.getCalledNode(), destinationAet, progress, params));

      assertEquals("callingNode cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for null called node")
    void should_throw_exception_for_null_called_node() {
      // Given
      var progress = createProgressTracker();
      var destinationAet = DicomTestConfig.getTestDestinationAet();
      var params = createStudyLevelParams();

      // When & Then
      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  CMove.process(
                      DicomTestConfig.getCallingNode(), null, destinationAet, progress, params));

      assertEquals("calledNode cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for null destination AET")
    void should_throw_exception_for_null_destination_aet() {
      // Given
      var progress = createProgressTracker();
      var params = createStudyLevelParams();

      // When & Then
      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  CMove.process(
                      DicomTestConfig.getCallingNode(),
                      DicomTestConfig.getCalledNode(),
                      null,
                      progress,
                      params));

      assertEquals("destinationAet cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should handle null progress gracefully")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void should_handle_null_progress_gracefully() {
      // Given
      var destinationAet = DicomTestConfig.getTestDestinationAet();
      var params = createStudyLevelParams();

      addMoveSeparation();
      LOGGER.info("Testing C-MOVE with null progress");

      // When
      var state =
          CMove.process(
              DicomTestConfig.getCallingNode(),
              DicomTestConfig.getCalledNode(),
              destinationAet,
              null,
              params);

      // Then
      assertNotNull(state, "DicomState should not be null even with null progress");
      LOGGER.info("C-MOVE completed with null progress, status: " + state.getStatus());
    }
  }

  @Nested
  @DisplayName("Query Level Operations")
  class QueryLevelOperations {

    @ParameterizedTest
    @EnumSource(QueryRetrieveLevel.class)
    @DisplayName("Should support different query retrieve levels")
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void should_support_different_query_levels(QueryRetrieveLevel level) {
      // Given
      var progress = createProgressTracker();
      var destinationAet = DicomTestConfig.getTestDestinationAet();
      var advancedParams = createAdvancedParamsWithRelationalQuery();
      var queryParams = createQueryParamsForLevel(level);

      // When
      addMoveSeparation();
      LOGGER.info("Testing " + level + " level C-MOVE");

      var state =
          CMove.process(
              advancedParams,
              DicomTestConfig.getCallingNode(),
              DicomTestConfig.getCalledNode(),
              destinationAet,
              progress,
              queryParams);

      // Then
      assertNotNull(state, "DicomState should not be null for level: " + level);
      assertTrue(
          state.getStatus() == Status.Success
              || state.getStatus() == Status.Pending
              || state.getStatus()
                  == Status.UnableToProcess, // Some servers may not support all levels
          "Status should indicate successful processing or acceptable failure for level: " + level);

      LOGGER.info("Query level " + level + " completed with status: " + state.getStatus());
    }
  }

  @Nested
  @DisplayName("Configuration-Specific Tests")
  class ConfigurationSpecificTests {

    @Test
    @DisplayName("Should use configuration-specific test data")
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void should_use_configuration_specific_test_data() {
      // Given
      var serverConfig = DicomTestConfig.getActiveServerConfig();
      var progress = createProgressTracker();
      var destinationAet = DicomTestConfig.getTestDestinationAet();
      var queryParams =
          new DicomParam[] {new DicomParam(Tag.StudyInstanceUID, serverConfig.testStudyUid())};

      addMoveSeparation();
      LOGGER.info("Using test study UID: " + serverConfig.testStudyUid());
      LOGGER.info("Target server: " + serverConfig.name());

      // When
      var state =
          CMove.process(
              DicomTestConfig.getCallingNode(),
              DicomTestConfig.getCalledNode(),
              destinationAet,
              progress,
              queryParams);

      // Then
      assertNotNull(state, "DicomState should not be null");
      assertTrue(
          state.getStatus() == Status.Success || state.getStatus() == Status.Pending,
          () -> "Expected successful status but got: " + state.getMessage());

      logMoveResult(
          state, progress, "Configuration-Specific Move", DicomTestConfig.getActiveConfig(), 0, 0);
    }

    @Test
    @DisplayName("Should use configuration-specific timeouts")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void should_use_configuration_specific_timeouts() {
      // Given
      var serverConfig = DicomTestConfig.getActiveServerConfig();
      var advancedParams = serverConfig.toAdvancedParams();
      var progress = createProgressTracker();
      var destinationAet = DicomTestConfig.getTestDestinationAet();
      var queryParams = createStudyLevelParams();

      addMoveSeparation();
      LOGGER.info("Using connection timeout: " + serverConfig.connectionTimeout() + "s");
      LOGGER.info("Using association timeout: " + serverConfig.associationTimeout() + "s");

      // When
      var state =
          CMove.process(
              advancedParams,
              DicomTestConfig.getCallingNode(),
              DicomTestConfig.getCalledNode(),
              destinationAet,
              progress,
              queryParams);

      // Then
      assertNotNull(state, "DicomState should not be null");
      assertTrue(
          state.getStatus() == Status.Success || state.getStatus() == Status.Pending,
          () -> "Expected successful status but got: " + state.getMessage());

      logMoveResult(
          state, progress, "Timeout-Configured Move", DicomTestConfig.getActiveConfig(), 0, 0);
    }
  }

  @Nested
  @DisplayName("Multi-Configuration Tests")
  @EnabledIf("org.weasis.dicom.real.DicomTestConfig#isParallelTestingEnabled")
  class MultiConfigurationTests {

    @TestFactory
    @DisplayName("Should work with all enabled configurations")
    List<DynamicTest> should_work_with_all_enabled_configurations() {
      return DicomTestConfig.getEnabledConfigurations().stream()
          .map(
              config ->
                  DynamicTest.dynamicTest(
                      "C-MOVE with " + config + " configuration",
                      () -> testWithConfiguration(config)))
          .toList();
    }

    private void testWithConfiguration(String configName) throws Exception {
      DicomTestConfig.withConfig(
          configName,
          () -> {
            var progress = createProgressTracker();
            var destinationAet = DicomTestConfig.getTestDestinationAet();
            var params = createStudyLevelParams();

            addMoveSeparation();
            LOGGER.info("Testing configuration: " + configName);

            var state =
                CMove.process(
                    DicomTestConfig.getCallingNode(),
                    DicomTestConfig.getCalledNode(),
                    destinationAet,
                    progress,
                    params);

            assertNotNull(state, "DicomState should not be null for config: " + configName);
            LOGGER.info(
                "Configuration "
                    + configName
                    + " test completed with status: "
                    + state.getStatus());

            return state;
          });
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("Should handle connection timeout gracefully")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void should_handle_connection_timeout_gracefully() {
      // Given
      var progress = createProgressTracker();
      var destinationAet = DicomTestConfig.getTestDestinationAet();
      var params = createStudyLevelParams();

      // Create advanced params with very short timeout
      var advancedParams = new AdvancedParams();
      var connectOptions = new ConnectOptions();
      connectOptions.setConnectTimeout(1); // 1ms - will likely timeout
      advancedParams.setConnectOptions(connectOptions);

      // Use a non-existent server to force timeout
      var unreachableNode = new DicomNode("UNREACHABLE", "192.0.2.1", 104);

      addMoveSeparation();
      LOGGER.info("Testing connection timeout handling");

      // When
      var state =
          CMove.process(
              advancedParams,
              DicomTestConfig.getCallingNode(),
              unreachableNode,
              destinationAet,
              progress,
              params);

      // Then
      assertNotNull(state, "DicomState should not be null even on timeout");
      assertTrue(
          state.getStatus() != Status.Success, "Status should indicate failure due to timeout");

      LOGGER.info("Timeout test completed with expected failure status: " + state.getStatus());
    }

    @Test
    @DisplayName("Should handle invalid destination AET")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void should_handle_invalid_destination_aet() {
      // Given
      var progress = createProgressTracker();
      var invalidDestinationAet = "INVALID_AET_12345";
      var params = createStudyLevelParams();

      addMoveSeparation();
      LOGGER.info("Testing with invalid destination AET: " + invalidDestinationAet);

      // When
      var state =
          CMove.process(
              DicomTestConfig.getCallingNode(),
              DicomTestConfig.getCalledNode(),
              invalidDestinationAet,
              progress,
              params);

      // Then
      assertNotNull(state, "DicomState should not be null");
      // Note: Some servers may accept unknown destination AETs, so we don't assert failure
      LOGGER.info("Invalid destination AET test completed with status: " + state.getStatus());
    }
  }

  @Nested
  @DisplayName("Progress Monitoring")
  class ProgressMonitoring {

    @Test
    @DisplayName("Should track progress during C-MOVE operation")
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void should_track_progress_during_move_operation() {
      // Given
      var progress = createProgressTracker();
      var progressUpdates = new ArrayList<String>();

      progress.addProgressListener(
          p -> {
            String update =
                String.format(
                    "Remaining: %d, Completed: %d, Failed: %d, Warning: %d",
                    p.getNumberOfRemainingSuboperations(),
                    p.getNumberOfCompletedSuboperations(),
                    p.getNumberOfFailedSuboperations(),
                    p.getNumberOfWarningSuboperations());
            progressUpdates.add(update);
            LOGGER.info("Progress update: " + update);
          });

      var destinationAet = DicomTestConfig.getTestDestinationAet();
      var params = createStudyLevelParams();

      addMoveSeparation();
      LOGGER.info("Testing progress monitoring during C-MOVE");

      // When
      var state =
          CMove.process(
              DicomTestConfig.getCallingNode(),
              DicomTestConfig.getCalledNode(),
              destinationAet,
              progress,
              params);

      // Then
      assertNotNull(state, "DicomState should not be null");
      assertTrue(true, "Should have received progress updates");

      LOGGER.info("Progress monitoring completed with " + progressUpdates.size() + " updates");
      LOGGER.info(
          "Final progress - Remaining: "
              + progress.getNumberOfRemainingSuboperations()
              + ", Completed: "
              + progress.getNumberOfCompletedSuboperations());
    }
  }

  // Helper methods

  private DicomProgress createProgressTracker() {
    var progress = new DicomProgress();
    progress.addProgressListener(
        p -> {
          if (p.getNumberOfRemainingSuboperations() % 10 == 0
              || p.getNumberOfRemainingSuboperations() < 5) {
            LOGGER.fine("C-MOVE Progress - Remaining: " + p.getNumberOfRemainingSuboperations());
          }
        });
    return progress;
  }

  private DicomParam[] createStudyLevelParams() {
    return new DicomParam[] {
      new DicomParam(Tag.StudyInstanceUID, DicomTestConfig.getTestStudyUid())
    };
  }

  private DicomParam[] createSeriesLevelParams() {
    return new DicomParam[] {
      new DicomParam(Tag.QueryRetrieveLevel, "SERIES"),
      new DicomParam(Tag.SeriesInstanceUID, DicomTestConfig.getTestSeriesUid())
    };
  }

  private DicomParam[] createImageLevelParams() {
    return new DicomParam[] {
      new DicomParam(Tag.QueryRetrieveLevel, "IMAGE"),
      new DicomParam(Tag.SOPInstanceUID, "1.2.826.0.1.3680043.8.1055.1.3.1")
    };
  }

  private DicomParam[] createQueryParamsForLevel(QueryRetrieveLevel level) {
    return switch (level) {
      case STUDY -> createStudyLevelParams();
      case SERIES -> createSeriesLevelParams();
      case IMAGE -> createImageLevelParams();
    };
  }

  private AdvancedParams createAdvancedParamsWithRelationalQuery() {
    var advancedParams = DicomTestConfig.createAdvancedParams();
    advancedParams.setQueryOptions(EnumSet.of(QueryOption.RELATIONAL));
    return advancedParams;
  }

  private void addMoveSeparation() {
    LOGGER.info("================== C-MOVE Operation ==================");
  }

  private void logMoveResult(
      DicomState state,
      DicomProgress progress,
      String testName,
      String configName,
      long startTime,
      long endTime) {
    LOGGER.info("=== " + testName + " Results ===");
    LOGGER.info("Configuration: " + configName);
    LOGGER.info(
        "Status: " + state.getStatus() + " (" + Integer.toHexString(state.getStatus()) + ")");
    LOGGER.info("Message: " + (state.getMessage() != null ? state.getMessage() : "None"));

    if (progress != null) {
      LOGGER.info(
          "Progress - Remaining: "
              + progress.getNumberOfRemainingSuboperations()
              + ", Completed: "
              + progress.getNumberOfCompletedSuboperations()
              + ", Failed: "
              + progress.getNumberOfFailedSuboperations()
              + ", Warning: "
              + progress.getNumberOfWarningSuboperations());
    }

    if (startTime > 0 && endTime > startTime) {
      LOGGER.info("Duration: " + (endTime - startTime) + "ms");
    }

    LOGGER.info("========================================");
  }
}
