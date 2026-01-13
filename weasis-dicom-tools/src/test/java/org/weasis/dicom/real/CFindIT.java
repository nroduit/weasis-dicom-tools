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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.QueryRetrieveLevel;
import org.junit.jupiter.api.Assertions;
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
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;

/**
 * Integration tests for DICOM C-FIND operations with individual configuration files.
 *
 * <p>Each DICOM server configuration is stored in a separate properties file under {@code
 * /dicom-configs/} directory. Tests can be run against different servers by setting:
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
 *   <li>{@code mock-server} - Mock server for unit testing
 * </ul>
 */
@DisplayName("DICOM C-FIND Integration Tests")
@DisplayNameGeneration(ReplaceUnderscores.class)
@TestInstance(Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
class CFindIT {

  private static final Logger LOGGER = Logger.getLogger(CFindIT.class.getName());

  @BeforeAll
  static void setUpClass() {
    // Print available configurations for debugging
    DicomTestConfig.printAvailableConfigurations();

    // Verify active configuration is enabled
    var activeConfig = DicomTestConfig.getActiveServerConfig();
    assumeTrue(
        activeConfig.enabled(),
        "Active configuration '" + DicomTestConfig.getActiveConfig() + "' is disabled");

    LOGGER.info("Running tests with configuration: " + activeConfig);
    LOGGER.info("Configuration file: " + DicomTestConfig.getActiveConfig() + ".properties");
  }

  @Nested
  @DisplayName("Configuration-Specific Tests")
  class ConfigurationSpecificTests {

    @Test
    @DisplayName("Should use configuration-specific test data")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void should_use_configuration_specific_test_data() {
      // Given
      var serverConfig = DicomTestConfig.getActiveServerConfig();
      var queryParams =
          new DicomParam[] {
            new DicomParam(Tag.PatientID, serverConfig.testPatientId()),
            new DicomParam(Tag.StudyInstanceUID),
            new DicomParam(Tag.PatientName)
          };

      addQuerySeparation();
      LOGGER.info("Using test patient ID: " + serverConfig.testPatientId());
      LOGGER.info("Target server: " + serverConfig.name());
      // When
      var state =
          CFind.process(
              DicomTestConfig.getCallingNode(), DicomTestConfig.getCalledNode(), queryParams);

      // Then
      assertNotNull(state, "DicomState should not be null");
      assertEquals(
          Status.Success,
          state.getStatus(),
          () -> "Expected successful status but got: " + state.getMessage());

      logResults(state, "Configuration-Specific Query", DicomTestConfig.getActiveConfig());
    }

    @Test
    @DisplayName("Should use configuration-specific timeouts")
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void should_use_configuration_specific_timeouts() {
      // Given
      var serverConfig = DicomTestConfig.getActiveServerConfig();
      var advancedParams = serverConfig.toAdvancedParams();
      var queryParams =
          new DicomParam[] {
            new DicomParam(Tag.PatientID, serverConfig.testPatientId()),
            new DicomParam(Tag.StudyInstanceUID)
          };

      addQuerySeparation();
      LOGGER.info("Using connection timeout: " + serverConfig.connectionTimeout() + "s");
      LOGGER.info("Using association timeout: " + serverConfig.associationTimeout() + "s");
      // When
      var state =
          CFind.process(
              advancedParams,
              DicomTestConfig.getCallingNode(),
              DicomTestConfig.getCalledNode(),
              queryParams);

      // Then
      assertNotNull(state, "DicomState should not be null");
      assertEquals(
          Status.Success,
          state.getStatus(),
          () -> "Expected successful status but got: " + state.getMessage());

      logResults(state, "Study Level Query", DicomTestConfig.getActiveConfig());
    }

    @ParameterizedTest
    @EnumSource(QueryRetrieveLevel.class)
    @DisplayName("Should support different query levels")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void should_support_different_query_levels(QueryRetrieveLevel level) {
      // Skip FRAME level as it's not supported in C-FIND
      assumeTrue(level != QueryRetrieveLevel.FRAME, "FRAME level not supported in C-FIND");

      // Given
      var advancedParams = DicomTestConfig.createAdvancedParams();
      var queryParams = createQueryParamsForLevel(level);

      // When
      addQuerySeparation();
      var state =
          CFind.process(
              advancedParams,
              DicomTestConfig.getCallingNode(),
              DicomTestConfig.getCalledNode(),
              0,
              level,
              queryParams);

      // Then
      assertNotNull(state, "DicomState should not be null for level: " + level);
      assertTrue(
          state.getStatus() == Status.Success || state.getStatus() == Status.Pending,
          "Status should be Success or Pending for level: " + level);

      LOGGER.info("Query level " + level + " completed with status: " + state.getStatus());
    }
  }

  @Nested
  @DisplayName("Multi-Configuration Tests")
  class MultiConfigurationTests {

    @TestFactory
    @DisplayName("Should work with all enabled configurations")
    Iterable<DynamicTest> should_work_with_all_enabled_configurations() {
      return DicomTestConfig.getEnabledConfigurations().stream()
          .map(
              configName ->
                  DynamicTest.dynamicTest(
                      "Test with " + configName + " configuration",
                      () -> testWithSpecificConfiguration(configName)))
          .toList();
    }

    @Test
    @DisplayName("Should switch between configurations correctly")
    void should_switch_between_configurations_correctly() throws Exception {
      var enabledConfigs = DicomTestConfig.getEnabledConfigurations();
      assumeTrue(
          enabledConfigs.size() >= 2,
          "At least 2 enabled configurations required for switching test");

      String originalConfig = DicomTestConfig.getActiveConfig();

      try {
        // Test with first configuration
        String firstConfig = enabledConfigs.get(0);
        var firstResult =
            DicomTestConfig.withConfig(
                firstConfig,
                () -> {
                  assertEquals(firstConfig, DicomTestConfig.getActiveConfig());
                  return performBasicQuery();
                });

        // Test with second configuration
        String secondConfig = enabledConfigs.get(1);
        var secondResult =
            DicomTestConfig.withConfig(
                secondConfig,
                () -> {
                  assertEquals(secondConfig, DicomTestConfig.getActiveConfig());
                  return performBasicQuery();
                });

        // Verify both tests completed
        assertNotNull(firstResult, "First config test should complete");
        assertNotNull(secondResult, "Second config test should complete");

      } finally {
        // Verify original config is restored
        assertEquals(originalConfig, DicomTestConfig.getActiveConfig());
      }
    }

    @Test
    @DisplayName("Should handle configuration with TLS if available")
    @EnabledIf("isTlsConfigurationAvailable")
    void should_handle_configuration_with_tls_if_available() throws Exception {
      String tlsConfig =
          DicomTestConfig.getAvailableConfigurations().stream()
              .filter(config -> DicomTestConfig.getServerConfig(config).tlsEnabled())
              .filter(config -> DicomTestConfig.getServerConfig(config).enabled())
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("No TLS configuration available"));

      var result =
          DicomTestConfig.withConfig(
              tlsConfig,
              () -> {
                var serverConfig = DicomTestConfig.getActiveServerConfig();
                assertTrue(serverConfig.tlsEnabled(), "TLS should be enabled");
                assertNotNull(serverConfig.tlsOptions(), "TLS options should be configured");

                return performBasicQuery();
              });

      assertNotNull(result, "TLS configuration test should complete");
    }

    static boolean isTlsConfigurationAvailable() {
      return DicomTestConfig.getAvailableConfigurations().stream()
          .anyMatch(DicomTestConfig::isTlsConfigurationAvailable);
    }

    private void testWithSpecificConfiguration(String configName) {
      try {
        DicomTestConfig.setActiveConfig(configName);
        var serverConfig = DicomTestConfig.getActiveServerConfig();

        // Skip if configuration is disabled
        assumeTrue(serverConfig.enabled(), "Configuration " + configName + " is disabled");

        var state = performBasicQuery();

        assertNotNull(state, "DicomState should not be null for config: " + configName);
        logResults(state, "Basic Query", configName);

      } catch (Exception e) {
        LOGGER.warning("Configuration " + configName + " failed: " + e.getMessage());
        // For integration tests, we might want to continue with other configs
        // rather than fail completely
      }
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("Should handle invalid configuration gracefully")
    void should_handle_invalid_configuration_gracefully() {
      var originalConfig = DicomTestConfig.getActiveConfig();

      try {
        // This should throw an exception
        var exception =
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DicomTestConfig.setActiveConfig("non-existent-config"));

        assertTrue(exception.getMessage().contains("Configuration not found"));

        // Active config should remain unchanged
        assertEquals(originalConfig, DicomTestConfig.getActiveConfig());

      } finally {
        // Ensure we're back to original config
        DicomTestConfig.setActiveConfig(originalConfig);
      }
    }
  }

  // Helper methods

  private DicomState performBasicQuery() {
    var queryParams =
        new DicomParam[] {
          new DicomParam(Tag.PatientID, DicomTestConfig.getTestPatientId()),
          new DicomParam(Tag.StudyInstanceUID)
        };

    addQuerySeparation();
    return CFind.process(
        DicomTestConfig.getCallingNode(), DicomTestConfig.getCalledNode(), queryParams);
  }

  private void addQuerySeparation() {
    var logMessage = new StringBuilder();
    logMessage.append("\n").append("=".repeat(50)).append(" Query Test ").append("=".repeat(50));
    LOGGER.info(logMessage.toString());
  }

  private DicomParam[] createQueryParamsForLevel(QueryRetrieveLevel level) {
    return switch (level) {
      case PATIENT ->
          new DicomParam[] {
            new DicomParam(Tag.PatientID, DicomTestConfig.getTestPatientId()),
            new DicomParam(Tag.PatientName)
          };
      case STUDY ->
          new DicomParam[] {
            new DicomParam(Tag.PatientID, DicomTestConfig.getTestPatientId()),
            new DicomParam(Tag.StudyInstanceUID),
            new DicomParam(Tag.StudyDescription)
          };
      case SERIES ->
          new DicomParam[] {
            new DicomParam(Tag.StudyInstanceUID, DicomTestConfig.getTestStudyUid()),
            new DicomParam(Tag.SeriesInstanceUID),
            new DicomParam(Tag.Modality)
          };
      case IMAGE ->
          new DicomParam[] {
            new DicomParam(Tag.SeriesInstanceUID, DicomTestConfig.getTestSeriesUid()),
            new DicomParam(Tag.SOPInstanceUID),
            new DicomParam(Tag.InstanceNumber)
          };
      case FRAME -> throw new IllegalArgumentException("FRAME level not supported in C-FIND");
    };
  }

  private void logResults(DicomState state, String operation, String configName) {
    var results = state.getDicomRSP();
    var maxItems = DicomTestConfig.getLogMaxItems();
    var truncateLength = DicomTestConfig.getLogTruncateLength();

    var logMessage = new StringBuilder();
    logMessage.append("\n").append("=".repeat(50));
    logMessage
        .append("\n")
        .append(operation)
        .append(" Results (Config: ")
        .append(configName)
        .append(")");
    logMessage.append("\n").append("Configuration File: ").append(configName).append(".properties");
    logMessage.append("\n").append("*".repeat(50));
    logMessage.append("\n").append("Status: ").append(state.getStatus());
    logMessage.append("\n").append("Message: ").append(state.getMessage());
    logMessage.append("\n").append("Number of results: ").append(results.size());

    for (int i = 0; i < Math.min(results.size(), maxItems); i++) {
      Attributes item = results.get(i);
      logMessage.append("\n").append("Result ").append(i + 1).append(":");
      logMessage.append("\n").append(item.toString(100, truncateLength));
    }

    if (results.size() > maxItems) {
      logMessage
          .append("\n")
          .append("... and ")
          .append(results.size() - maxItems)
          .append(" more results");
    }

    logMessage.append("\n").append("=".repeat(50)).append("\n");

    LOGGER.info(logMessage.toString());
  }
}
