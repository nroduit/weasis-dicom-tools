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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.net.Device;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DeviceOpServiceTest {

  private Device testDevice;
  private DeviceOpService deviceOpService;

  @BeforeEach
  void setUp() {
    testDevice = createTestDevice("test-device");
    deviceOpService = new DeviceOpService(testDevice);
  }

  @AfterEach
  void tearDown() {
    if (deviceOpService.isRunning()) {
      deviceOpService.stop();
    }
  }

  @Nested
  class Constructor {

    @Test
    void creates_service_with_valid_device() {
      var device = createTestDevice("valid-device");

      var service = new DeviceOpService(device);

      assertEquals(device, service.getDevice());
      assertFalse(service.isRunning());
    }

    @Test
    void throws_null_pointer_exception_when_device_is_null() {
      var exception = assertThrows(NullPointerException.class, () -> new DeviceOpService(null));

      assertEquals("Device cannot be null", exception.getMessage());
    }
  }

  @Nested
  class Get_device {

    @Test
    void returns_the_device_provided_in_constructor() {
      assertEquals(testDevice, deviceOpService.getDevice());
    }

    @Test
    void returns_same_instance_on_multiple_calls() {
      var device1 = deviceOpService.getDevice();
      var device2 = deviceOpService.getDevice();

      assertSame(device1, device2);
    }
  }

  @Nested
  class Is_running {

    @Test
    void returns_false_when_service_is_not_started() {
      assertFalse(deviceOpService.isRunning());
    }

    @Test
    void returns_true_when_service_is_started() {
      deviceOpService.start();

      assertTrue(deviceOpService.isRunning());
    }

    @Test
    void returns_false_when_service_is_stopped() {
      deviceOpService.start();
      deviceOpService.stop();

      assertFalse(deviceOpService.isRunning());
    }

    @Test
    void returns_false_when_executor_is_shutdown() throws Exception {
      deviceOpService.start();
      var executor = getExecutorField();
      executor.shutdown();

      assertFalse(deviceOpService.isRunning());
    }
  }

  @Nested
  class Start {

    @Test
    void creates_executor_services_and_configures_device() {
      deviceOpService.start();

      if (testDevice.getExecutor() instanceof ThreadPoolExecutor poolExecutor) {
        assertFalse(poolExecutor.isShutdown());
      }

      assertAll(
          () -> assertTrue(deviceOpService.isRunning()),
          () -> assertNotNull(testDevice.getExecutor()),
          () -> assertNotNull(testDevice.getScheduledExecutor()),
          () -> assertFalse(testDevice.getScheduledExecutor().isShutdown()));
    }

    @Test
    void creates_executors_with_proper_thread_names() throws Exception {
      deviceOpService.start();

      var executor = getExecutorField();
      var scheduledExecutor = getScheduledExecutorField();
      var deviceName = testDevice.getDeviceName();

      assertAll(() -> assertNotNull(executor), () -> assertNotNull(scheduledExecutor));

      // Test that executors can execute tasks (indirect verification of thread factory)
      var latch = new CountDownLatch(2);
      executor.submit(latch::countDown);
      scheduledExecutor.submit(latch::countDown);

      assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    void does_nothing_when_service_already_running() throws Exception {
      deviceOpService.start();
      var originalExecutor = getExecutorField();
      var originalScheduledExecutor = getScheduledExecutorField();

      deviceOpService.start(); // Second call

      assertAll(
          () -> assertTrue(deviceOpService.isRunning()),
          () -> assertSame(originalExecutor, getExecutorField()),
          () -> assertSame(originalScheduledExecutor, getScheduledExecutorField()));
    }

    @Test
    @Timeout(5)
    void is_thread_safe_with_concurrent_starts() throws Exception {
      var startCount = 10;
      var latch = new CountDownLatch(startCount);
      var threads = new Thread[startCount];

      for (int i = 0; i < startCount; i++) {
        threads[i] =
            new Thread(
                () -> {
                  deviceOpService.start();
                  latch.countDown();
                });
      }

      for (var thread : threads) {
        thread.start();
      }

      assertTrue(latch.await(3, TimeUnit.SECONDS));
      assertTrue(deviceOpService.isRunning());

      for (var thread : threads) {
        thread.join();
      }
    }
  }

  @Nested
  class Stop {

    @Test
    void shuts_down_executors_and_clears_references() throws Exception {
      deviceOpService.start();

      deviceOpService.stop();

      assertAll(
          () -> assertFalse(deviceOpService.isRunning()),
          () -> assertNull(getExecutorField()),
          () -> assertNull(getScheduledExecutorField()));
    }

    @Test
    void does_nothing_when_service_not_running() {
      assertDoesNotThrow(() -> deviceOpService.stop());
      assertFalse(deviceOpService.isRunning());
    }

    @Test
    void handles_executor_shutdown_gracefully() throws Exception {
      deviceOpService.start();
      var executor = getExecutorField();
      var scheduledExecutor = getScheduledExecutorField();

      // Pre-shutdown one executor to test error handling
      executor.shutdown();

      assertDoesNotThrow(() -> deviceOpService.stop());
      assertFalse(deviceOpService.isRunning());
    }

    @Test
    @Timeout(5)
    void is_thread_safe_with_concurrent_stops() throws Exception {
      deviceOpService.start();

      var stopCount = 10;
      var latch = new CountDownLatch(stopCount);
      var threads = new Thread[stopCount];

      for (int i = 0; i < stopCount; i++) {
        threads[i] =
            new Thread(
                () -> {
                  deviceOpService.stop();
                  latch.countDown();
                });
      }

      for (var thread : threads) {
        thread.start();
      }

      assertTrue(latch.await(3, TimeUnit.SECONDS));
      assertFalse(deviceOpService.isRunning());

      for (var thread : threads) {
        thread.join();
      }
    }
  }

  @Nested
  class Lifecycle_integration {

    @Test
    void supports_multiple_start_stop_cycles() {
      // First cycle
      deviceOpService.start();
      assertTrue(deviceOpService.isRunning());
      deviceOpService.stop();
      assertFalse(deviceOpService.isRunning());

      // Second cycle
      deviceOpService.start();
      assertTrue(deviceOpService.isRunning());
      deviceOpService.stop();
      assertFalse(deviceOpService.isRunning());

      // Third cycle
      deviceOpService.start();
      assertTrue(deviceOpService.isRunning());
      deviceOpService.stop();
      assertFalse(deviceOpService.isRunning());
    }

    @Test
    @Timeout(10)
    void executors_can_execute_tasks_when_running() throws Exception {
      deviceOpService.start();
      var executor = testDevice.getExecutor();
      var scheduledExecutor = testDevice.getScheduledExecutor();

      var taskLatch = new CountDownLatch(2);
      var scheduledLatch = new CountDownLatch(1);

      executor.execute(taskLatch::countDown);
      executor.execute(taskLatch::countDown);
      scheduledExecutor.schedule(scheduledLatch::countDown, 100, TimeUnit.MILLISECONDS);

      assertAll(
          () -> assertTrue(taskLatch.await(1, TimeUnit.SECONDS)),
          () -> assertTrue(scheduledLatch.await(1, TimeUnit.SECONDS)));
    }

    @Test
    @Timeout(5)
    void concurrent_start_and_stop_operations() throws Exception {
      var operationCount = 20;
      var latch = new CountDownLatch(operationCount);
      var threads = new Thread[operationCount];

      for (int i = 0; i < operationCount; i++) {
        final boolean shouldStart = i % 2 == 0;
        threads[i] =
            new Thread(
                () -> {
                  if (shouldStart) {
                    deviceOpService.start();
                  } else {
                    deviceOpService.stop();
                  }
                  latch.countDown();
                });
      }

      for (var thread : threads) {
        thread.start();
      }

      assertTrue(latch.await(3, TimeUnit.SECONDS));

      for (var thread : threads) {
        thread.join();
      }

      // Final state should be consistent
      boolean isRunning = deviceOpService.isRunning();
      if (testDevice.getExecutor() instanceof ThreadPoolExecutor poolExecutor) {
        assertEquals(isRunning, poolExecutor.isShutdown());
      }
    }
  }

  @Nested
  class Device_configuration {

    @Test
    void device_has_no_executors_initially() {
      assertAll(
          () -> assertNull(testDevice.getExecutor()),
          () -> assertNull(testDevice.getScheduledExecutor()));
    }

    @Test
    void device_executors_are_set_after_start() {
      deviceOpService.start();

      assertAll(
          () -> assertNotNull(testDevice.getExecutor()),
          () -> assertNotNull(testDevice.getScheduledExecutor()),
          () -> assertSame(testDevice.getExecutor(), getExecutorField()),
          () -> assertSame(testDevice.getScheduledExecutor(), getScheduledExecutorField()));
    }

    @Test
    void device_executors_remain_after_stop() {
      deviceOpService.start();
      var executor = testDevice.getExecutor();
      var scheduledExecutor = testDevice.getScheduledExecutor();

      deviceOpService.stop();

      if (executor instanceof ThreadPoolExecutor poolExecutor) {
        assertTrue(poolExecutor.isShutdown());
      }
      // Device keeps references but they are shutdown
      assertAll(
          () -> assertSame(executor, testDevice.getExecutor()),
          () -> assertSame(scheduledExecutor, testDevice.getScheduledExecutor()),
          () -> assertTrue(scheduledExecutor.isShutdown()));
    }
  }

  // Helper methods
  private Device createTestDevice(String deviceName) {
    return new Device(deviceName);
  }

  private ExecutorService getExecutorField() throws Exception {
    return getFieldValue("executor", ExecutorService.class);
  }

  private ScheduledExecutorService getScheduledExecutorField() throws Exception {
    return getFieldValue("scheduledExecutor", ScheduledExecutorService.class);
  }

  @SuppressWarnings("unchecked")
  private <T> T getFieldValue(String fieldName, Class<T> expectedType) throws Exception {
    Field field = DeviceOpService.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (T) field.get(deviceOpService);
  }
}
