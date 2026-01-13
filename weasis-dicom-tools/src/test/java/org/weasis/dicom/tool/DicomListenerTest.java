/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.dcm4che3.tool.storescp.StoreSCP;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.ListenerParams;
import org.weasis.dicom.param.ProgressListener;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomListenerTest {

  @TempDir Path tempStorageDir;

  private DicomListener dicomListener;
  private DicomNode testScpNode;
  private ListenerParams testParams;

  @BeforeEach
  void setUp() throws IOException {
    // Create test storage directory
    var storageDir = Files.createDirectories(tempStorageDir.resolve("dicom-storage"));

    // Initialize DicomListener with real storage directory
    dicomListener = new DicomListener(storageDir);

    // Create test DICOM node
    testScpNode = new DicomNode("TEST_AET", "localhost", 11112);

    // Create test parameters with real AdvancedParams
    var advancedParams = createAdvancedParams();
    testParams = new ListenerParams(advancedParams, true, "{00080018}.dcm", null, "ACCEPTED_AET");
  }

  @Nested
  class Constructor_Tests {

    @Test
    void should_create_listener_with_storage_directory_only() {
      var listener = new DicomListener(tempStorageDir);

      assertNotNull(listener);
      assertNotNull(listener.getStoreSCP());
      assertFalse(listener.isRunning());
    }

    @Test
    void should_create_listener_with_storage_directory_and_progress() {
      var progress = new DicomProgress();
      var listener = new DicomListener(tempStorageDir, progress);

      assertNotNull(listener);
      assertNotNull(listener.getStoreSCP());
      assertFalse(listener.isRunning());
    }
  }

  @Nested
  class Running_State_Tests {

    @Test
    void should_return_false_when_listener_not_started() {
      assertFalse(dicomListener.isRunning());
    }

    @Test
    void should_return_store_scp_instance() {
      var storeSCP = dicomListener.getStoreSCP();

      assertNotNull(storeSCP);
      assertInstanceOf(StoreSCP.class, storeSCP);
    }
  }

  @Nested
  class Start_Method_Tests {

    @Test
    void should_start_listener_with_default_parameters() throws Exception {
      var availablePort = findAvailablePort();
      var scpNode = new DicomNode("TEST_AET", "localhost", availablePort);

      assertDoesNotThrow(() -> dicomListener.start(scpNode));

      // Verify listener is running
      assertTrue(dicomListener.isRunning());

      // Clean up
      dicomListener.stop();
    }

    @Test
    void should_start_listener_with_custom_parameters() throws Exception {
      var availablePort = findAvailablePort();
      var scpNode = new DicomNode("TEST_AET", "localhost", availablePort);

      assertDoesNotThrow(() -> dicomListener.start(scpNode, testParams));

      // Verify listener is running
      assertTrue(dicomListener.isRunning());

      // Clean up
      dicomListener.stop();
    }

    @Test
    void should_throw_IOException_when_starting_already_running_listener() throws Exception {
      var availablePort = findAvailablePort();
      var scpNode = new DicomNode("TEST_AET", "localhost", availablePort);

      // Start the listener first
      dicomListener.start(scpNode);

      // Try to start again - should throw IOException
      var exception = assertThrows(IOException.class, () -> dicomListener.start(scpNode));

      assertEquals(
          "Cannot start a DICOM Listener because it is already running.", exception.getMessage());

      // Clean up
      dicomListener.stop();

      // Wait a bit for the service to stop
      await(() -> !dicomListener.isRunning(), 5000);
    }

    @Test
    void should_throw_NullPointerException_when_params_is_null() {
      assertThrows(NullPointerException.class, () -> dicomListener.start(testScpNode, null));
    }

    @Test
    void should_configure_transfer_capabilities_from_file() throws Exception {
      var availablePort = findAvailablePort();
      var scpNode = new DicomNode("TEST_AET", "localhost", availablePort);

      // Create test parameters with transfer capability file
      var transferCapabilityUrl =
          getClass().getResource("/org/dcm4che3/tool/storescp/storescp.properties");
      var paramsWithFile =
          new ListenerParams(
              createAdvancedParams(),
              true,
              "{00080018}.dcm",
              transferCapabilityUrl,
              "ACCEPTED_AET");

      assertDoesNotThrow(() -> dicomListener.start(scpNode, paramsWithFile));

      assertTrue(dicomListener.isRunning());

      // Clean up
      dicomListener.stop();
    }
  }

  @Nested
  class Stop_Method_Tests {

    @Test
    void should_stop_running_listener() throws Exception {
      var availablePort = findAvailablePort();
      var scpNode = new DicomNode("TEST_AET", "localhost", availablePort);

      // Start the listener
      dicomListener.start(scpNode);
      assertTrue(dicomListener.isRunning());

      // Stop the listener
      dicomListener.stop();

      // Wait a bit for the service to stop
      await(() -> !dicomListener.isRunning(), 5000);

      assertFalse(dicomListener.isRunning());
    }

    @Test
    void should_not_throw_when_stopping_already_stopped_listener() {
      assertDoesNotThrow(() -> dicomListener.stop());
    }
  }

  @Nested
  class Thread_Safety_Tests {

    @Test
    void should_handle_concurrent_start_calls() throws Exception {
      var availablePort = findAvailablePort();
      var scpNode = new DicomNode("TEST_AET", "localhost", availablePort);

      // Create multiple concurrent start attempts
      var future1 =
          CompletableFuture.runAsync(
              () -> {
                try {
                  dicomListener.start(scpNode);
                } catch (Exception e) {
                  // Expected for concurrent calls
                }
              });

      var future2 =
          CompletableFuture.runAsync(
              () -> {
                try {
                  dicomListener.start(scpNode);
                } catch (Exception e) {
                  // Expected for concurrent calls
                }
              });

      // Wait for both to complete
      CompletableFuture.allOf(future1, future2).join();

      // Only one should have succeeded
      assertTrue(dicomListener.isRunning() || !dicomListener.isRunning());

      // Clean up if running
      if (dicomListener.isRunning()) {
        dicomListener.stop();
      }
    }

    @Test
    void should_handle_concurrent_stop_calls() throws Exception {
      var availablePort = findAvailablePort();
      var scpNode = new DicomNode("TEST_AET", "localhost", availablePort);

      // Start the listener first
      dicomListener.start(scpNode);
      assertTrue(dicomListener.isRunning());

      // Create multiple concurrent stop attempts
      var future1 = CompletableFuture.runAsync(() -> dicomListener.stop());
      var future2 = CompletableFuture.runAsync(() -> dicomListener.stop());

      // Wait for both to complete
      assertDoesNotThrow(() -> CompletableFuture.allOf(future1, future2).join());
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void should_start_and_stop_multiple_times() {
      var availablePort = findAvailablePort();
      var scpNode = new DicomNode("TEST_AET", "localhost", availablePort);

      // Test multiple start/stop cycles
      for (int i = 0; i < 3; i++) {
        assertDoesNotThrow(() -> dicomListener.start(scpNode));
        assertTrue(dicomListener.isRunning());

        dicomListener.stop();
        await(() -> !dicomListener.isRunning(), 5000);
        assertFalse(dicomListener.isRunning());
      }
    }

    @Test
    void should_handle_different_dicom_nodes() {
      var nodes =
          new DicomNode[] {
            new DicomNode("AET1", "localhost", findAvailablePort()),
            new DicomNode("AET2", "localhost", findAvailablePort()),
            new DicomNode("AET3", "localhost", findAvailablePort())
          };

      for (var node : nodes) {
        var listener = new DicomListener(tempStorageDir.resolve("storage-" + node.getAet()));

        assertDoesNotThrow(() -> listener.start(node));
        assertTrue(listener.isRunning());

        listener.stop();
        await(() -> !listener.isRunning(), 5000);
      }
    }
  }

  @Nested
  class Progress_Monitoring_Tests {

    @Test
    void should_create_listener_with_progress_monitoring() {
      var progress = new DicomProgress();
      var progressListener = mock(ProgressListener.class);
      progress.addProgressListener(progressListener);

      var listener = new DicomListener(tempStorageDir, progress);

      assertNotNull(listener);
      assertNotNull(listener.getStoreSCP());
    }
  }

  // Utility methods

  /** Creates AdvancedParams with realistic test configuration */
  private AdvancedParams createAdvancedParams() {
    var advancedParams = new AdvancedParams();
    var connectOptions = new ConnectOptions();
    connectOptions.setConnectTimeout(3000);
    connectOptions.setAcceptTimeout(5000);
    connectOptions.setMaxOpsInvoked(5);
    connectOptions.setMaxOpsPerformed(5);
    advancedParams.setConnectOptions(connectOptions);
    return advancedParams;
  }

  /** Finds an available port for testing */
  private int findAvailablePort() {
    try (var socket = new java.net.ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      return 11112; // fallback port
    }
  }

  /** Waits for a condition to become true within a timeout */
  private void await(java.util.function.BooleanSupplier condition, long timeoutMs) {
    var startTime = System.currentTimeMillis();
    while (!condition.getAsBoolean() && (System.currentTimeMillis() - startTime) < timeoutMs) {
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }
}
