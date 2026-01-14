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

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.dcm4che3.net.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.util.ServiceUtil;

/**
 * Service for managing the lifecycle of a DICOM Device with executor services. Provides thread-safe
 * start/stop operations and proper resource cleanup.
 */
public class DeviceOpService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeviceOpService.class);

  private static final long SHUTDOWN_TIMEOUT_SECONDS = 30L;
  protected final Device device;
  private final AtomicReference<ExecutorService> executor = new AtomicReference<>();
  private final AtomicReference<ScheduledExecutorService> scheduledExecutor =
      new AtomicReference<>();

  /**
   * Creates a device operation service for the specified device.
   *
   * @param device the DICOM device to manage
   * @throws NullPointerException if device is null
   */
  public DeviceOpService(Device device) {
    this.device = Objects.requireNonNull(device, "Device cannot be null");
  }

  public Device getDevice() {
    return device;
  }

  public boolean isRunning() {
    ExecutorService exec = executor.get();
    return exec != null && !exec.isShutdown();
  }

  public synchronized void start() {
    if (isRunning()) {
      LOGGER.debug("Device service is already running");
      return;
    }

    createExecutorServices();
    configureDevice();
    LOGGER.info("Device service started for device: {}", device.getDeviceName());
  }

  public synchronized void stop() {
    if (!isRunning()) {
      LOGGER.debug("Device service is not running");
      return;
    }

    shutdownExecutorServices();
    clearExecutorReferences();
    LOGGER.info("Device service stopped for device: {}", device.getDeviceName());
  }

  private void createExecutorServices() {
    var deviceName = device.getDeviceName();
    executor.set(
        Executors.newSingleThreadExecutor(ServiceUtil.getThreadFactory(deviceName + "-executor")));
    scheduledExecutor.set(
        Executors.newSingleThreadScheduledExecutor(
            ServiceUtil.getThreadFactory(deviceName + "-scheduled")));
  }

  private void configureDevice() {
    device.setExecutor(executor.get());
    device.setScheduledExecutor(scheduledExecutor.get());
  }

  private void shutdownExecutorServices() {
    shutdownExecutor(scheduledExecutor.get(), "Scheduled executor");
    shutdownExecutor(executor.get(), "Main executor");
  }

  private void shutdownExecutor(ExecutorService executor, String executorType) {
    if (executor == null) {
      return;
    }

    try {
      executor.shutdown();
      if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        LOGGER.warn("{} did not terminate gracefully, forcing shutdown", executorType);
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.warn("{} shutdown interrupted, forcing shutdown", executorType);
      executor.shutdownNow();
    } catch (Exception e) {
      LOGGER.error("Error during {} shutdown", executorType, e);
    }
  }

  private void clearExecutorReferences() {
    executor.set(null);
    scheduledExecutor.set(null);
  }
}
