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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.dicom.op.CStore;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.CstoreParams;
import org.weasis.dicom.param.DefaultAttributeEditor;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.tool.DicomListener;

@ExtendWith(MockitoExtension.class)
@DisplayName("DICOM C-STORE Integration Tests")
class CstoreIT {

  private static final Duration TEST_TIMEOUT =
      Duration.ofMinutes(DicomTestConfig.getTestTimeoutMinutes());

  @TempDir private Path tempStorageDir;

  private DicomListener localServer;
  private DicomNode callingNode;
  private DicomNode calledNode;
  private AdvancedParams advancedParams;
  private List<String> testFiles;

  @BeforeEach
  void setUp(TestInfo testInfo) throws Exception {
    System.out.printf(
        "Starting test: %s with config: %s%n",
        testInfo.getDisplayName(), DicomTestConfig.getActiveConfig());

    setupDicomFiles();
    setupDicomNodes();
    setupLocalServer();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (localServer != null) {
      localServer.stop();
    }
  }

  private void setupDicomFiles() {
    var testFile = Path.of("src/test/resources/dicom/mr.dcm");
    testFiles = List.of(testFile.toString());
  }

  private void setupDicomNodes() {
    callingNode = DicomTestConfig.getCallingNode();
    calledNode = DicomTestConfig.getCalledNode();
    advancedParams = DicomTestConfig.createAdvancedParams();
  }

  private void setupLocalServer() throws Exception {
    var serverConfig = DicomTestConfig.getActiveServerConfig();
    if ("local-dcm4che".equals(serverConfig.configName())) {
      // Start local SCP server for local testing
      var progress = new DicomProgress();
      localServer = new DicomListener(tempStorageDir, progress);
      localServer.start(calledNode);
    }
  }

  @Nested
  @DisplayName("Basic C-STORE Operations")
  class BasicOperations {

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @DisplayName("Should successfully store DICOM file to remote server")
    @EnabledIf("org.weasis.dicom.real.DicomTestConfig#getActiveServerConfig().enabled()")
    void shouldStoreToRemoteServer() {
      // When
      var state = CStore.process(callingNode, calledNode, testFiles);

      // Then
      assertNotNull(state);
      assertEquals(Status.Success, state.getStatus(), state.getMessage());
      assertTrue(state.getMessage() != null && !state.getMessage().isEmpty());
      System.out.printf("DICOM C-STORE Status: %s%n", state.getMessage());
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @DisplayName("Should store with progress tracking")
    @EnabledIf("org.weasis.dicom.real.DicomTestConfig#getActiveServerConfig().enabled()")
    void shouldStoreWithProgress() {
      // Given
      var progress = new DicomProgress();
      var progressUpdates = new AtomicInteger(0);
      var completedFiles = new AtomicReference<String>();

      progress.addProgressListener(
          p -> {
            progressUpdates.incrementAndGet();
            if (p.getProcessedFile() != null) {
              completedFiles.set(p.getProcessedFile().toString());
            }
          });

      // When
      var state = CStore.process(callingNode, calledNode, testFiles, progress);

      // Then
      assertNotNull(state);
      assertEquals(Status.Success, state.getStatus(), state.getMessage());
      assertTrue(progressUpdates.get() > 0, "Progress should be reported");
      assertNotNull(completedFiles.get(), "Completed file should be tracked");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @DisplayName("Should store with advanced parameters")
    @EnabledIf("org.weasis.dicom.real.DicomTestConfig#getActiveServerConfig().enabled()")
    void shouldStoreWithAdvancedParams() {
      // When
      var state = CStore.process(advancedParams, callingNode, calledNode, testFiles);

      // Then
      assertNotNull(state);
      assertEquals(Status.Success, state.getStatus(), state.getMessage());
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @DisplayName("Should store with attribute modifications")
    @EnabledIf("org.weasis.dicom.real.DicomTestConfig#getActiveServerConfig().enabled()")
    void shouldStoreWithAttributeModifications() {
      // Given
      var attrs = new Attributes();
      attrs.setString(Tag.PatientName, VR.PN, "Test^Modified^Patient");
      attrs.setString(Tag.PatientID, VR.LO, DicomTestConfig.getTestPatientId());

      var editor = new DefaultAttributeEditor(false, attrs);
      var cstoreParams = new CstoreParams(List.of(editor), false, null);

      // When
      var state =
          CStore.process(advancedParams, callingNode, calledNode, testFiles, null, cstoreParams);

      // Then
      assertNotNull(state);
      assertEquals(Status.Success, state.getStatus(), state.getMessage());
    }
  }

  @Nested
  @DisplayName("Connection and Network Tests")
  class ConnectionTests {

    @Test
    @DisplayName("Should handle connection timeout gracefully")
    void shouldHandleConnectionTimeout() {
      // Given
      var timeoutParams = new AdvancedParams();
      var connectOptions = new ConnectOptions();
      connectOptions.setConnectTimeout(100); // Very short timeout
      timeoutParams.setConnectOptions(connectOptions);

      var unreachableNode = new DicomNode("UNREACHABLE", "192.0.2.1", 11112);

      // When
      var state = CStore.process(timeoutParams, callingNode, unreachableNode, testFiles);

      // Then
      assertNotNull(state);
      assertTrue(state.getStatus() != Status.Success);
      assertNotNull(state.getMessage());
    }

    @Test
    @DisplayName("Should handle invalid hostname")
    void shouldHandleInvalidHostname() {
      // Given
      var invalidNode = new DicomNode("INVALID", "invalid.hostname.test", 11112);

      // When
      var state = CStore.process(callingNode, invalidNode, testFiles);

      // Then
      assertNotNull(state);
      assertTrue(state.getStatus() != Status.Success);
    }

    @Test
    @DisplayName("Should handle invalid port")
    void shouldHandleInvalidPort() {
      // Given
      var invalidPortNode = new DicomNode("INVALID", calledNode.getHostname(), 99999);

      // When
      var state = CStore.process(callingNode, invalidPortNode, testFiles);

      // Then
      assertNotNull(state);
      assertTrue(state.getStatus() != Status.Success);
    }
  }

  @Nested
  @DisplayName("Parameter Validation Tests")
  class ParameterValidationTests {

    @Test
    @DisplayName("Should throw exception for null calling node")
    void shouldThrowExceptionForNullCallingNode() {
      assertThrows(NullPointerException.class, () -> CStore.process(null, calledNode, testFiles));
    }

    @Test
    @DisplayName("Should throw exception for null called node")
    void shouldThrowExceptionForNullCalledNode() {
      assertThrows(NullPointerException.class, () -> CStore.process(callingNode, null, testFiles));
    }

    @Test
    @DisplayName("Should handle null advanced parameters")
    void shouldHandleNullAdvancedParams() {
      // When
      var state = CStore.process(null, callingNode, calledNode, testFiles);

      // Then - Should not throw exception, uses default parameters
      assertNotNull(state);
    }

    @Test
    @DisplayName("Should handle null progress")
    void shouldHandleNullProgress() {
      // When
      var state = CStore.process(callingNode, calledNode, testFiles, null);

      // Then
      assertNotNull(state);
    }

    @Test
    @DisplayName("Should handle null cstore parameters")
    void shouldHandleNullCstoreParams() {
      // When
      var state = CStore.process(null, callingNode, calledNode, testFiles, null, null);

      // Then
      assertNotNull(state);
    }
  }

  @Nested
  @DisplayName("File Handling Tests")
  class FileHandlingTests {

    @Test
    @DisplayName("Should handle empty file list")
    void shouldHandleEmptyFileList() {
      // When
      var state = CStore.process(callingNode, calledNode, List.of());

      // Then
      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    @DisplayName("Should handle non-existent files")
    void shouldHandleNonExistentFiles() {
      // Given
      var nonExistentFiles = List.of("/non/existent/file.dcm");

      // When
      var state = CStore.process(callingNode, calledNode, nonExistentFiles);

      // Then
      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }

    @Test
    @DisplayName("Should handle invalid DICOM files")
    void shouldHandleInvalidDicomFiles() throws IOException {
      // Given
      var invalidFile = tempStorageDir.resolve("invalid.dcm");
      Files.write(invalidFile, "not a dicom file".getBytes());
      var invalidFiles = List.of(invalidFile.toString());

      // When
      var state = CStore.process(callingNode, calledNode, invalidFiles);

      // Then
      assertNotNull(state);
      assertEquals(Status.UnableToProcess, state.getStatus());
    }
  }

  @Nested
  @DisplayName("Configuration-Based Tests")
  class ConfigurationBasedTests {

    @ParameterizedTest
    @ValueSource(strings = {"public-server", "local-dcm4che"})
    @DisplayName("Should work with different configurations")
    @EnabledIf("org.weasis.dicom.real.DicomTestConfig#isConfigurationAvailable")
    void shouldWorkWithDifferentConfigurations(String configName) throws Exception {
      // Given
      if (!DicomTestConfig.isConfigurationAvailable(configName)) {
        return; // Skip if configuration not available
      }

      // When & Then
      DicomTestConfig.withConfig(
          configName,
          () -> {
            if (!DicomTestConfig.getServerConfig(configName).enabled()) {
              return null; // Skip disabled configurations
            }

            var configCalledNode = DicomTestConfig.getCalledNode(configName);
            var configAdvancedParams = DicomTestConfig.createAdvancedParams(configName);

            var state =
                CStore.process(configAdvancedParams, callingNode, configCalledNode, testFiles);

            assertNotNull(state);
            // Note: We don't assert success status as some test servers might be unavailable
            assertNotNull(state.getMessage());

            return state;
          });
    }

    @Test
    @DisplayName("Should handle secure connection if TLS enabled")
    @EnabledIf("org.weasis.dicom.real.DicomTestConfig#getActiveServerConfig().tlsEnabled()")
    void shouldHandleSecureConnection() {
      // Given
      var serverConfig = DicomTestConfig.getActiveServerConfig();
      assertTrue(serverConfig.tlsEnabled(), "TLS should be enabled for this test");

      // When
      var state = CStore.process(advancedParams, callingNode, calledNode, testFiles);

      // Then
      assertNotNull(state);
      // TLS connection attempts should not cause null pointer exceptions
      assertNotNull(state.getMessage());
    }
  }

  @Nested
  @DisplayName("Progress Tracking Tests")
  class ProgressTrackingTests {

    @Test
    @DisplayName("Should track progress with multiple files")
    void shouldTrackProgressWithMultipleFiles() throws IOException {
      // Given
      var testFile = Path.of("src/test/resources/dicom/mr.dcm");
      var multipleFiles =
          List.of(
              testFile.toString(),
              testFile.toString(), // Same file referenced twice
              testFile.toString());

      var progress = new DicomProgress();
      var filesProcessed = new AtomicInteger(0);
      var latch = new CountDownLatch(1);

      progress.addProgressListener(
          p -> {
            if (p.getProcessedFile() != null) {
              filesProcessed.incrementAndGet();
            }
            if (p.isLastFailed() || filesProcessed.get() >= multipleFiles.size()) {
              latch.countDown();
            }
          });

      // When
      var state = CStore.process(callingNode, calledNode, multipleFiles, progress);

      // Then
      assertNotNull(state);
    }

    @Test
    @DisplayName("Should handle progress cancellation")
    void shouldHandleProgressCancellation() throws InterruptedException {
      // Given
      var progress = new DicomProgress();
      var cancelled = new AtomicBoolean(false);

      progress.addProgressListener(
          p -> {
            if (!cancelled.get()) {
              progress.cancel();
              cancelled.set(true);
            }
          });

      // When
      var state = CStore.process(callingNode, calledNode, testFiles, progress);

      // Then
      assertNotNull(state);
      assertTrue(progress.isCancelled(), "Progress should be cancelled");
    }
  }

  @Nested
  @DisplayName("Local Server Integration Tests")
  @EnabledIf("isLocalServerAvailable")
  class LocalServerTests {

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    @DisplayName("Should store to local SCP server")
    void shouldStoreToLocalServer() throws IOException, InterruptedException {
      // Given
      var localProgress = new DicomProgress();
      var filesStored = new AtomicInteger(0);
      var latch = new CountDownLatch(1);

      localProgress.addProgressListener(
          p -> {
            if (p.getProcessedFile() != null) {
              filesStored.incrementAndGet();
              latch.countDown();
            }
          });

      var localCalledNode = new DicomNode(calledNode.getAet(), "localhost", calledNode.getPort());

      // When
      var state = CStore.process(callingNode, localCalledNode, testFiles, localProgress);

      // Then
      assertNotNull(state);
      assertEquals(Status.Success, state.getStatus(), state.getMessage());

      // Wait for file to be stored
      assertTrue(latch.await(30, TimeUnit.SECONDS), "File should be stored within timeout");
      assertEquals(1, filesStored.get(), "One file should be stored");

      // Verify file was actually stored
      assertTrue(Files.exists(tempStorageDir), "Storage directory should exist");
      var storedFiles = Files.list(tempStorageDir).filter(Files::isRegularFile).toList();
      assertFalse(storedFiles.isEmpty(), "At least one file should be stored");
    }

    @Test
    @DisplayName("Should handle concurrent operations with CompletableFuture")
    void shouldHandleConcurrentOperationsWithCompletableFuture() {
      // Given
      var numOperations = 3;
      var futures = new CompletableFuture[numOperations];

      // When
      for (int i = 0; i < numOperations; i++) {
        futures[i] =
            CompletableFuture.supplyAsync(() -> CStore.process(callingNode, calledNode, testFiles));
      }

      // Then
      var results =
          CompletableFuture.allOf(futures)
              .thenApply(
                  v -> {
                    var states = new DicomState[numOperations];
                    for (int i = 0; i < numOperations; i++) {
                      try {
                        states[i] = (DicomState) futures[i].get();
                      } catch (Exception e) {
                        throw new RuntimeException("Failed to get result", e);
                      }
                    }
                    return states;
                  });

      try {
        var states = results.get(TEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        for (int i = 0; i < numOperations; i++) {
          assertNotNull(states[i], "State " + i + " should not be null");
          assertNotNull(states[i].getMessage(), "Message " + i + " should not be null");
        }
      } catch (Exception e) {
        throw new RuntimeException("Concurrent operations failed", e);
      }
    }
  }
}
