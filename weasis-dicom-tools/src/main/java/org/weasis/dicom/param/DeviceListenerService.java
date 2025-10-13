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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.net.Device;

/**
 * A service that manages a DICOM device's execution environment and connection lifecycle. Provides
 * thread pool management for DICOM operations and connection handling.
 */
public class DeviceListenerService {

  private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

  private final Device device;
  private volatile ExecutorService executor;
  private volatile ScheduledExecutorService scheduledExecutor;

  /**
   * Creates a new device listener service.
   *
   * @param device the DICOM device to manage (must not be null)
   * @throws NullPointerException if device is null
   */
  public DeviceListenerService(Device device) {
    this.device = Objects.requireNonNull(device, "Device must not be null");
  }

  /**
   * Gets the managed DICOM device.
   *
   * @return the DICOM device
   */
  public Device getDevice() {
    return device;
  }

  /**
   * Checks if the service is currently running.
   *
   * @return true if the service is running, false otherwise
   */
  public boolean isRunning() {
    return executor != null && !executor.isShutdown();
  }

  /**
   * Starts the device listener service by initializing executors and binding connections.
   *
   * @throws IOException if connection binding fails
   * @throws GeneralSecurityException if security configuration is invalid
   * @throws IllegalStateException if the service is already running
   */
  public synchronized void start() throws IOException, GeneralSecurityException {
    if (isRunning()) {
      throw new IllegalStateException("Service is already running");
    }

    initializeExecutors();
    configureDevice();
    try {
      device.bindConnections();
    } catch (IOException | GeneralSecurityException e) {
      stop();
      throw e;
    }
  }

  /**
   * Stops the device listener service by unbinding connections and shutting down executors. This
   * method is safe to call multiple times and will gracefully handle already stopped services.
   */
  public synchronized void stop() {
    unbindDeviceConnections();
    shutdownExecutors();
    clearExecutorReferences();
  }

  private void initializeExecutors() {
    executor = Executors.newCachedThreadPool();
    scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
  }

  private void configureDevice() {
    device.setExecutor(executor);
    device.setScheduledExecutor(scheduledExecutor);
  }

  private void unbindDeviceConnections() {
    try {
      device.unbindConnections();
    } catch (Exception e) {
      // Log the exception if logging is available, but don't propagate it
      // as stop() should be safe to call and shouldn't throw exceptions
    }
  }

  private void shutdownExecutors() {
    shutdownExecutor(scheduledExecutor);
    shutdownExecutor(executor);
  }

  private void shutdownExecutor(ExecutorService executorService) {
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  private void clearExecutorReferences() {
    executor = null;
    scheduledExecutor = null;
  }
}
