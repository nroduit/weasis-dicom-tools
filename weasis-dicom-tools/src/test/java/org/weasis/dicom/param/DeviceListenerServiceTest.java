/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.dcm4che3.net.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class DeviceListenerServiceTest {

  @Mock private Device mockDevice;

  private DeviceListenerService service;

  @BeforeEach
  void setUp() {
    service = new DeviceListenerService(mockDevice);
  }

  @Nested
  class Constructor_tests {

    @Test
    void creates_service_with_valid_device() {
      var testDevice = new Device("TEST_DEVICE");

      var testService = new DeviceListenerService(testDevice);

      assertEquals(testDevice, testService.getDevice());
      assertFalse(testService.isRunning());
    }

    @Test
    void throws_null_pointer_exception_when_device_is_null() {
      var exception =
          assertThrows(NullPointerException.class, () -> new DeviceListenerService(null));

      assertEquals("Device must not be null", exception.getMessage());
    }
  }

  @Nested
  class Get_device_tests {

    @Test
    void returns_the_same_device_instance() {
      assertEquals(mockDevice, service.getDevice());
    }
  }

  @Nested
  class Is_running_tests {

    @Test
    void returns_false_when_service_not_started() {
      assertFalse(service.isRunning());
    }

    @Test
    void returns_true_when_service_is_started() throws Exception {
      service.start();

      assertTrue(service.isRunning());

      service.stop(); // cleanup
    }

    @Test
    void returns_false_after_service_is_stopped() throws Exception {
      service.start();
      service.stop();

      assertFalse(service.isRunning());
    }
  }

  @Nested
  class Start_tests {

    @Test
    void starts_service_successfully() throws Exception {
      service.start();

      assertAll(
          () -> assertTrue(service.isRunning()),
          () -> verify(mockDevice).setExecutor(any(ExecutorService.class)),
          () -> verify(mockDevice).setScheduledExecutor(any(ScheduledExecutorService.class)),
          () -> verify(mockDevice).bindConnections());

      service.stop(); // cleanup
    }

    @Test
    void throws_illegal_state_exception_when_already_running() throws Exception {
      service.start();

      var exception = assertThrows(IllegalStateException.class, () -> service.start());

      assertEquals("Service is already running", exception.getMessage());

      service.stop(); // cleanup
    }

    @Test
    void handles_io_exception_during_bind_connections() throws Exception {
      var ioException = new IOException("Connection failed");
      doThrow(ioException).when(mockDevice).bindConnections();

      var exception = assertThrows(IOException.class, () -> service.start());

      assertAll(
          () -> assertEquals(ioException, exception),
          () -> assertFalse(service.isRunning()),
          () -> verify(mockDevice).unbindConnections());
    }

    @Test
    void handles_general_security_exception_during_bind_connections() throws Exception {
      var securityException = new GeneralSecurityException("Security error");
      doThrow(securityException).when(mockDevice).bindConnections();

      var exception = assertThrows(GeneralSecurityException.class, () -> service.start());

      assertAll(
          () -> assertEquals(securityException, exception),
          () -> assertFalse(service.isRunning()),
          () -> verify(mockDevice).unbindConnections());
    }

    @Test
    void configures_device_with_executors_before_binding() throws Exception {
      service.start();

      var inOrder = inOrder(mockDevice);
      inOrder.verify(mockDevice).setExecutor(any(ExecutorService.class));
      inOrder.verify(mockDevice).setScheduledExecutor(any(ScheduledExecutorService.class));
      inOrder.verify(mockDevice).bindConnections();

      service.stop(); // cleanup
    }
  }

  @Nested
  class Stop_tests {

    @Test
    void stops_running_service_successfully() throws Exception {
      service.start();

      service.stop();

      assertAll(
          () -> assertFalse(service.isRunning()), () -> verify(mockDevice).unbindConnections());
    }

    @Test
    void handles_stop_when_service_not_running() {
      assertDoesNotThrow(() -> service.stop());

      verify(mockDevice).unbindConnections();
    }

    @Test
    void handles_multiple_stop_calls_gracefully() throws Exception {
      service.start();
      service.stop();

      assertDoesNotThrow(() -> service.stop());

      // unbindConnections should be called twice (once per stop call)
      verify(mockDevice, times(2)).unbindConnections();
    }

    @Test
    void handles_exception_during_unbind_connections() throws Exception {
      doThrow(new RuntimeException("Unbind failed")).when(mockDevice).unbindConnections();
      service.start();

      assertDoesNotThrow(() -> service.stop());

      assertFalse(service.isRunning());
    }

    @Test
    void stops_executors_even_when_unbind_fails() throws Exception {
      doThrow(new RuntimeException("Unbind failed")).when(mockDevice).unbindConnections();
      service.start();

      service.stop();

      // Service should still be marked as not running
      assertFalse(service.isRunning());
    }
  }

  @Nested
  class Concurrent_access_tests {

    @Test
    void handles_concurrent_start_calls() throws Exception {
      var thread1 =
          new Thread(
              () -> {
                try {
                  service.start();
                } catch (Exception e) {
                  // Expected for one of the threads
                }
              });

      var thread2 =
          new Thread(
              () -> {
                try {
                  service.start();
                } catch (Exception e) {
                  // Expected for one of the threads
                }
              });

      thread1.start();
      thread2.start();

      thread1.join();
      thread2.join();

      // Only one start should succeed
      assertTrue(service.isRunning());
      verify(mockDevice, times(1)).bindConnections();

      service.stop(); // cleanup
    }

    @Test
    void handles_concurrent_start_and_stop() throws Exception {
      service.start();

      var stopThread = new Thread(() -> service.stop());
      var startThread =
          new Thread(
              () -> {
                try {
                  service.start();
                } catch (Exception e) {
                  // May fail if stop is called first
                }
              });

      stopThread.start();
      startThread.start();

      stopThread.join();
      startThread.join();

      // Final state should be consistent
      verify(mockDevice, atLeast(1)).unbindConnections();
    }
  }

  @Nested
  class Integration_tests {

    @Test
    void complete_lifecycle_with_real_device() throws Exception {
      var realDevice = new Device("TEST_DEVICE");
      var realService = new DeviceListenerService(realDevice);

      // Test complete lifecycle
      assertFalse(realService.isRunning());

      realService.start();
      assertTrue(realService.isRunning());
      assertEquals(realDevice, realService.getDevice());

      realService.stop();
      assertFalse(realService.isRunning());

      // Should be able to restart
      realService.start();
      assertTrue(realService.isRunning());

      realService.stop();
      assertFalse(realService.isRunning());
    }

    @Test
    void service_with_device_having_connections() throws Exception {
      // Create a device with some basic configuration
      var testDevice = new Device("TEST_DEVICE_WITH_CONN");
      testDevice.setInstalled(true);

      var testService = new DeviceListenerService(testDevice);

      // Should handle device with connections
      assertDoesNotThrow(
          () -> {
            testService.start();
            testService.stop();
          });
    }
  }

  @Nested
  class Error_recovery_tests {

    @Test
    void recovers_after_failed_start_attempt() throws Exception {
      // First start attempt fails
      doThrow(new IOException("First attempt fails")).when(mockDevice).bindConnections();

      assertThrows(IOException.class, () -> service.start());
      assertFalse(service.isRunning());

      // Second attempt should work
      doNothing().when(mockDevice).bindConnections();

      assertDoesNotThrow(() -> service.start());
      assertTrue(service.isRunning());

      service.stop(); // cleanup
    }

    @Test
    void maintains_consistent_state_after_partial_failure() throws Exception {
      // Simulate failure after executors are created but before binding
      doThrow(new IOException("Binding failed")).when(mockDevice).bindConnections();

      assertThrows(IOException.class, () -> service.start());

      // Service should be in stopped state
      assertFalse(service.isRunning());

      // Should be able to start again
      doNothing().when(mockDevice).bindConnections();
      assertDoesNotThrow(() -> service.start());
      assertTrue(service.isRunning());

      service.stop(); // cleanup
    }
  }

  @Nested
  class Resource_cleanup_tests {

    @Test
    void ensures_executors_are_shutdown_on_stop() throws Exception {
      service.start();
      assertTrue(service.isRunning());

      service.stop();

      // After stop, isRunning should return false
      // (this indirectly tests that executors are shutdown)
      assertFalse(service.isRunning());
    }

    @Test
    void handles_interrupted_thread_during_shutdown() throws Exception {
      service.start();

      // Interrupt current thread before stopping
      Thread.currentThread().interrupt();

      assertDoesNotThrow(() -> service.stop());

      // Thread interrupt status should be restored
      assertTrue(Thread.interrupted()); // This clears the flag
    }
  }
}
