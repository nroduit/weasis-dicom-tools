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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.TlsOptions;

/**
 * Configuration utility for DICOM integration tests with individual configuration files.
 *
 * <p>Each DICOM server configuration is stored in its own properties file under {@code
 * /dicom-configs/} directory. Configurations can be selected via:
 *
 * <ul>
 *   <li>System property: -Ddicom.test.config=&lt;config-name&gt;
 *   <li>Environment variable: DICOM_TEST_CONFIG=&lt;config-name&gt;
 *   <li>Programmatically: {@link #setActiveConfig(String)}
 * </ul>
 *
 * <p>Configuration files should follow the naming pattern: {@code <config-name>.properties}
 *
 * <p>Usage examples:
 *
 * <pre>{@code
 * // Use default configuration
 * DicomNode node = DicomTestConfig.getCalledNode();
 *
 * // Switch to different configuration
 * DicomTestConfig.setActiveConfig("local-dcm4che");
 * DicomNode localNode = DicomTestConfig.getCalledNode();
 *
 * // Execute test with specific configuration
 * DicomTestConfig.withConfig("secure-server", () -> {
 *     // test code here
 *     return testResult;
 * });
 * }</pre>
 */
public final class DicomTestConfig {

  private static final String MAIN_CONFIG_FILE = "/dicom-configs/local-dcm4che.properties";
  private static final String CONFIG_DIR = "/dicom-configs/";
  private static final String SYSTEM_PROPERTY = "dicom.test.config";
  private static final String ENV_VARIABLE = "DICOM_TEST_CONFIG";

  private static final Properties mainProperties = new Properties();
  private static final Map<String, Properties> configCache = new ConcurrentHashMap<>();
  private static volatile String activeConfig;
  private static final Object configLock = new Object();

  static {
    loadMainConfiguration();
    initializeActiveConfig();
    discoverConfigurations();
  }

  private DicomTestConfig() {
    // Utility class
  }

  /** Server configuration record containing all server-specific settings. */
  public record ServerConfig(
      String configName,
      String name,
      String description,
      String aet,
      String hostname,
      int port,
      boolean tlsEnabled,
      TlsOptions tlsOptions,
      boolean enabled,
      int connectionTimeout,
      int associationTimeout,
      String testPatientId,
      String testStudyUid,
      String testSeriesUid,
      Map<String, String> additionalProperties) {

    public DicomNode toDicomNode() {
      return new DicomNode(aet, hostname, port);
    }

    public AdvancedParams toAdvancedParams() {
      var advancedParams = new AdvancedParams();

      // Connection options
      var connectOptions = new ConnectOptions();
      connectOptions.setConnectTimeout(connectionTimeout * 1000);
      connectOptions.setAcceptTimeout(associationTimeout * 1000);
      advancedParams.setConnectOptions(connectOptions);

      // TLS configuration
      if (tlsEnabled && tlsOptions != null) {
        advancedParams.setTlsOptions(tlsOptions);
      }

      return advancedParams;
    }

    @Override
    public String toString() {
      var status = enabled ? "✓ ENABLED" : "✗ DISABLED";
      var security = tlsEnabled ? " (TLS)" : " (Plain)";
      return String.format("%s - %s@%s:%d%s %s", name, aet, hostname, port, security, status);
    }
  }

  private static void loadMainConfiguration() {
    try (InputStream input = DicomTestConfig.class.getResourceAsStream(MAIN_CONFIG_FILE)) {
      if (input == null) {
        throw new RuntimeException("Main configuration file not found: " + MAIN_CONFIG_FILE);
      }
      mainProperties.load(input);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load main test configuration", e);
    }
  }

  private static void initializeActiveConfig() {
    // Priority: System property > Environment variable > Default from main properties
    String config = System.getProperty(SYSTEM_PROPERTY);
    if (config == null || config.trim().isEmpty()) {
      config = System.getenv(ENV_VARIABLE);
    }
    if (config == null || config.trim().isEmpty()) {
      config = mainProperties.getProperty("default.config", "public-server");
    }
    activeConfig = config.trim();
  }

  private static void discoverConfigurations() {
    try {
      // Get all .properties files in the config directory
      var configFiles = getConfigFiles();
      for (String configFile : configFiles) {
        if (!configFile.equals("dicom-test.properties")) {
          String configName = configFile.substring(0, configFile.lastIndexOf('.'));
          loadConfigurationFile(configName); // Pre-load to validate
        }
      }
    } catch (Exception e) {
      System.err.println("Warning: Could not discover all configurations: " + e.getMessage());
    }
  }

  private static List<String> getConfigFiles() {
    return List.of(
        "public-server.properties", "local-dcm4che.properties", "secure-server.properties");
  }

  private static Properties loadConfigurationFile(String configName) {
    return configCache.computeIfAbsent(
        configName,
        name -> {
          String configFile = CONFIG_DIR + name + ".properties";
          var properties = new Properties();

          try (InputStream input = DicomTestConfig.class.getResourceAsStream(configFile)) {
            if (input == null) {
              throw new RuntimeException("Configuration file not found: " + configFile);
            }
            properties.load(input);
            return properties;
          } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration: " + configFile, e);
          }
        });
  }

  /**
   * Sets the active configuration to use for subsequent operations.
   *
   * @param configName the name of the configuration to activate
   * @throws IllegalArgumentException if the configuration doesn't exist
   */
  public static void setActiveConfig(String configName) {
    if (configName == null || configName.trim().isEmpty()) {
      throw new IllegalArgumentException("Configuration name cannot be null or empty");
    }

    String trimmedName = configName.trim();
    if (!isConfigurationAvailable(trimmedName)) {
      throw new IllegalArgumentException(
          "Configuration not found: "
              + trimmedName
              + ". Available: "
              + getAvailableConfigurations());
    }

    synchronized (configLock) {
      activeConfig = trimmedName;
    }
  }

  /**
   * Gets the currently active configuration name.
   *
   * @return the active configuration name
   */
  public static String getActiveConfig() {
    synchronized (configLock) {
      return activeConfig;
    }
  }

  /**
   * Gets all available configuration names.
   *
   * @return list of available configuration names
   */
  public static List<String> getAvailableConfigurations() {
    return configCache.keySet().stream().sorted().collect(Collectors.toList());
  }

  /**
   * Gets all enabled configurations.
   *
   * @return list of enabled configuration names
   */
  public static List<String> getEnabledConfigurations() {
    return getAvailableConfigurations().stream()
        .filter(config -> getServerConfig(config).enabled())
        .collect(Collectors.toList());
  }

  /**
   * Checks if a configuration is available.
   *
   * @param configName the configuration name to check
   * @return true if the configuration exists
   */
  public static boolean isConfigurationAvailable(String configName) {
    try {
      loadConfigurationFile(configName);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Gets the server configuration for the specified config name.
   *
   * @param configName the configuration name
   * @return the server configuration
   * @throws IllegalArgumentException if the configuration doesn't exist
   */
  public static ServerConfig getServerConfig(String configName) {
    var properties = loadConfigurationFile(configName);

    // Basic server information
    String name = properties.getProperty("config.name", configName);
    String description = properties.getProperty("config.description", "");
    boolean enabled = Boolean.parseBoolean(properties.getProperty("config.enabled", "true"));

    // Connection settings
    String aet = properties.getProperty("server.aet", "UNKNOWN");
    String hostname = properties.getProperty("server.hostname", "localhost");
    int port = getIntProperty(properties, "server.port", 104);

    // Timeouts
    int connectionTimeout =
        getIntProperty(
            properties,
            "connection.timeout",
            getIntProperty(mainProperties, "default.connection.timeout", 30));
    int associationTimeout =
        getIntProperty(
            properties,
            "association.timeout",
            getIntProperty(mainProperties, "default.association.timeout", 60));

    // TLS settings
    boolean tlsEnabled = Boolean.parseBoolean(properties.getProperty("tls.enabled", "false"));
    TlsOptions tlsOptions = null;
    if (tlsEnabled) {
      tlsOptions = createTlsOptions(properties);
    }

    // Test data
    String testPatientId = properties.getProperty("test.patient.id", "TEST_PATIENT");
    String testStudyUid = properties.getProperty("test.study.uid", "1.2.3.4.5.6.7.8.9");
    String testSeriesUid = properties.getProperty("test.series.uid", "1.2.3.4.5.6.7.8.10");

    // Additional properties
    Map<String, String> additionalProperties = new HashMap<>();
    properties.stringPropertyNames().stream()
        .filter(
            key ->
                key.startsWith("server.")
                    && !Set.of("server.aet", "server.hostname", "server.port").contains(key))
        .forEach(key -> additionalProperties.put(key, properties.getProperty(key)));

    return new ServerConfig(
        configName,
        name,
        description,
        aet,
        hostname,
        port,
        tlsEnabled,
        tlsOptions,
        enabled,
        connectionTimeout,
        associationTimeout,
        testPatientId,
        testStudyUid,
        testSeriesUid,
        additionalProperties);
  }

  /**
   * Gets the server configuration for the currently active config.
   *
   * @return the active server configuration
   */
  public static ServerConfig getActiveServerConfig() {
    return getServerConfig(getActiveConfig());
  }

  /**
   * Gets the calling DICOM node for tests.
   *
   * @return the calling DICOM node
   */
  public static DicomNode getCallingNode() {
    String aet = mainProperties.getProperty("calling.aet", "WEASIS-SCU");
    return new DicomNode(aet);
  }

  /**
   * Gets the called DICOM node for the active configuration.
   *
   * @return the called DICOM node
   */
  public static DicomNode getCalledNode() {
    return getActiveServerConfig().toDicomNode();
  }

  /**
   * Gets the called DICOM node for a specific configuration.
   *
   * @param configName the configuration name
   * @return the called DICOM node
   */
  public static DicomNode getCalledNode(String configName) {
    return getServerConfig(configName).toDicomNode();
  }

  /**
   * Creates advanced parameters for the active configuration.
   *
   * @return configured AdvancedParams
   */
  public static AdvancedParams createAdvancedParams() {
    return getActiveServerConfig().toAdvancedParams();
  }

  /**
   * Creates advanced parameters for a specific configuration.
   *
   * @param configName the configuration name
   * @return configured AdvancedParams
   */
  public static AdvancedParams createAdvancedParams(String configName) {
    return getServerConfig(configName).toAdvancedParams();
  }

  /**
   * Executes a test with a specific configuration, then restores the previous config.
   *
   * @param configName the configuration to use for the test
   * @param test the test to execute
   * @param <T> the return type
   * @return the result of the test execution
   * @throws Exception if the test throws an exception
   */
  public static <T> T withConfig(String configName, TestWithConfig<T> test) throws Exception {
    String originalConfig = getActiveConfig();
    try {
      setActiveConfig(configName);
      return test.execute();
    } finally {
      setActiveConfig(originalConfig);
    }
  }

  /**
   * Functional interface for tests that need to run with a specific configuration.
   *
   * @param <T> the return type
   */
  @FunctionalInterface
  public interface TestWithConfig<T> {
    T execute() throws Exception;
  }

  // Configuration-specific test data getters

  public static String getTestPatientId() {
    return getActiveServerConfig().testPatientId();
  }

  public static String getTestPatientId(String configName) {
    return getServerConfig(configName).testPatientId();
  }

  public static String getTestStudyUid() {
    return getActiveServerConfig().testStudyUid();
  }

  public static String getTestStudyUid(String configName) {
    return getServerConfig(configName).testStudyUid();
  }

  public static String getTestSeriesUid() {
    return getActiveServerConfig().testSeriesUid();
  }

  public static String getTestSeriesUid(String configName) {
    return getServerConfig(configName).testSeriesUid();
  }

  // C-MOVE specific configuration getters

  public static String getTestDestinationAet() {
    return getActiveServerConfig()
        .additionalProperties()
        .getOrDefault(
            "test.destination.aet", mainProperties.getProperty("calling.aet", "WEASIS-SCU"));
  }

  public static String getTestDestinationAet(String configName) {
    return getServerConfig(configName)
        .additionalProperties()
        .getOrDefault(
            "test.destination.aet", mainProperties.getProperty("calling.aet", "WEASIS-SCU"));
  }

  public static String getMoveDestinationAet() {
    var serverConfig = getActiveServerConfig();
    return serverConfig
        .additionalProperties()
        .getOrDefault(
            "move.destination.aet",
            serverConfig
                .additionalProperties()
                .getOrDefault(
                    "test.destination.aet",
                    mainProperties.getProperty("calling.aet", "WEASIS-SCU")));
  }

  public static String getMoveDestinationAet(String configName) {
    var serverConfig = getServerConfig(configName);
    return serverConfig
        .additionalProperties()
        .getOrDefault(
            "move.destination.aet",
            serverConfig
                .additionalProperties()
                .getOrDefault(
                    "test.destination.aet",
                    mainProperties.getProperty("calling.aet", "WEASIS-SCU")));
  }

  public static int getMoveTimeoutSeconds() {
    return getIntProperty(
        getActiveServerConfig().additionalProperties(),
        "move.timeout.seconds",
        getIntProperty(mainProperties, "default.move.timeout.seconds", 180));
  }

  public static int getMoveTimeoutSeconds(String configName) {
    return getIntProperty(
        getServerConfig(configName).additionalProperties(),
        "move.timeout.seconds",
        getIntProperty(mainProperties, "default.move.timeout.seconds", 180));
  }

  public static int getMaxMoveOperations() {
    return getIntProperty(
        getActiveServerConfig().additionalProperties(),
        "move.max.operations",
        getIntProperty(mainProperties, "default.move.max.operations", 100));
  }

  public static int getMaxMoveOperations(String configName) {
    return getIntProperty(
        getServerConfig(configName).additionalProperties(),
        "move.max.operations",
        getIntProperty(mainProperties, "default.move.max.operations", 100));
  }

  // Helper methods

  private static int getIntProperty(Map<String, String> properties, String key, int defaultValue) {
    String value = properties.get(key);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  // ... existing code continues ...

  // Global configuration getters

  public static int getTestTimeoutMinutes() {
    return getIntProperty(mainProperties, "test.timeout.minutes", 2);
  }

  public static int getEchoTimeout() {
    return getIntProperty(mainProperties, "echo.timeout.seconds", 30);
  }

  public static boolean isParallelTestingEnabled() {
    return Boolean.parseBoolean(mainProperties.getProperty("test.parallel.enabled", "true"));
  }

  public static int getRetryCount() {
    return getIntProperty(mainProperties, "test.retry.count", 1);
  }

  public static int getLogMaxItems() {
    return getIntProperty(mainProperties, "log.results.max.items", 3);
  }

  public static int getLogTruncateLength() {
    return getIntProperty(mainProperties, "log.results.truncate.length", 150);
  }

  public static boolean isEchoPerformanceTestingEnabled() {
    return Boolean.parseBoolean(
        mainProperties.getProperty("echo.performance.testing.enabled", "true"));
  }

  public static int getEchoConcurrentOperations() {
    return getIntProperty(mainProperties, "echo.concurrent.operations", 3);
  }

  // C-STORE specific configuration getters

  public static int getCstoreRetryCount() {
    return getCstoreRetryCount(getActiveConfig());
  }

  public static int getCstoreRetryCount(String configName) {
    var properties = loadConfigurationFile(configName);
    return getIntProperty(
        properties,
        "cstore.retry.count",
        getIntProperty(mainProperties, "cstore.default.retry.count", 1));
  }

  public static int getCstoreRetryDelay() {
    return getCstoreRetryDelay(getActiveConfig());
  }

  public static int getCstoreRetryDelay(String configName) {
    var properties = loadConfigurationFile(configName);
    return getIntProperty(
        properties,
        "cstore.retry.delay",
        getIntProperty(mainProperties, "cstore.default.retry.delay", 1000));
  }

  public static int getCstoreTimeoutSeconds() {
    return getCstoreTimeoutSeconds(getActiveConfig());
  }

  public static int getCstoreTimeoutSeconds(String configName) {
    var properties = loadConfigurationFile(configName);
    return getIntProperty(
        properties,
        "cstore.timeout.seconds",
        getIntProperty(mainProperties, "cstore.default.timeout.seconds", 120));
  }

  public static int getCstoreConcurrentOperations() {
    return getCstoreConcurrentOperations(getActiveConfig());
  }

  public static int getCstoreConcurrentOperations(String configName) {
    var properties = loadConfigurationFile(configName);
    return getIntProperty(
        properties,
        "cstore.concurrent.operations",
        getIntProperty(mainProperties, "cstore.default.concurrent.operations", 3));
  }

  public static boolean isCstoreAttributeModificationEnabled() {
    return isCstoreAttributeModificationEnabled(getActiveConfig());
  }

  public static boolean isCstoreAttributeModificationEnabled(String configName) {
    var properties = loadConfigurationFile(configName);
    return Boolean.parseBoolean(
        properties.getProperty(
            "cstore.attribute.modification.enabled",
            mainProperties.getProperty("cstore.default.attribute.modification.enabled", "true")));
  }

  // Helper methods

  private static int getIntProperty(Properties properties, String key, int defaultValue) {
    String value = properties.getProperty(key);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static TlsOptions createTlsOptions(Properties properties) {
    String keystorePath = properties.getProperty("tls.keystore.path", "");
    String truststorePath = properties.getProperty("tls.truststore.path", "");

    if (keystorePath.isEmpty() || truststorePath.isEmpty()) {
      return null;
    }

    String keystoreType = properties.getProperty("tls.keystore.type", "JKS");
    String keystorePassword = properties.getProperty("tls.keystore.password", "");
    String keystoreKeyPassword =
        properties.getProperty("tls.keystore.key.password", keystorePassword);
    String truststoreType = properties.getProperty("tls.truststore.type", "JKS");
    String truststorePassword = properties.getProperty("tls.truststore.password", "");

    return new TlsOptions(
        true,
        keystorePath,
        keystoreType,
        keystorePassword,
        keystoreKeyPassword,
        truststorePath,
        truststoreType,
        truststorePassword);
  }

  /** Prints all available configurations and their status. */
  public static void printAvailableConfigurations() {
    System.out.println("Available DICOM Test Configurations:");
    System.out.println("=====================================");
    System.out.println("Active Configuration: " + getActiveConfig());
    System.out.println("Main Config File: " + MAIN_CONFIG_FILE);
    System.out.println("Config Directory: " + CONFIG_DIR);
    System.out.println();

    for (String configName : getAvailableConfigurations()) {
      try {
        ServerConfig config = getServerConfig(configName);
        System.out.printf("%-20s %s%n", configName, config.toString());
        if (!config.description().isEmpty()) {
          System.out.printf("%-20s %s%n", "", config.description());
        }
        if (config.tlsEnabled()) {
          System.out.printf(
              "%-20s TLS Configuration: %s%n",
              "", config.tlsOptions() != null ? "Valid" : "Invalid");
        }
        System.out.printf(
            "%-20s Test Data: Patient=%s, Study=%s%n",
            "", config.testPatientId(), config.testStudyUid());
        System.out.println();
      } catch (Exception e) {
        System.out.printf("%-20s ERROR: %s%n", configName, e.getMessage());
        System.out.println();
      }
    }

    System.out.println("Global Settings:");
    System.out.println("- Calling AET: " + mainProperties.getProperty("calling.aet", "WEASIS-SCU"));
    System.out.println("- Test Timeout: " + getTestTimeoutMinutes() + " minutes");
    System.out.println("- Parallel Testing: " + isParallelTestingEnabled());
    System.out.println("- Retry Count: " + getRetryCount());
  }

  /** Reloads all configurations from disk. Useful for testing configuration changes. */
  public static void reloadConfigurations() {
    synchronized (configLock) {
      configCache.clear();
      loadMainConfiguration();
      discoverConfigurations();
    }
  }
}
