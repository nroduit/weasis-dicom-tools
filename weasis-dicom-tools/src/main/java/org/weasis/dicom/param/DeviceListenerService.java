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
import org.dcm4che3.net.Device;

public class DeviceListenerService {

  protected final Device device;
  protected ExecutorService executor;
  protected ScheduledExecutorService scheduledExecutor;

  public DeviceListenerService(Device device) {
    this.device = Objects.requireNonNull(device);
  }

  public Device getDevice() {
    return device;
  }

  public boolean isRunning() {
    return executor != null;
  }

  public synchronized void start() throws IOException, GeneralSecurityException {
    if (!isRunning()) {
      executor = Executors.newCachedThreadPool();
      scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
      try {
        device.setExecutor(executor);
        device.setScheduledExecutor(scheduledExecutor);
        device.bindConnections();
      } catch (IOException | GeneralSecurityException e) {
        stop();
        throw e;
      }
    }
  }

  public synchronized void stop() {
    if (device != null) {
      device.unbindConnections();
    }
    if (scheduledExecutor != null) {
      scheduledExecutor.shutdown();
    }
    if (executor != null) {
      executor.shutdown();
    }
    executor = null;
    scheduledExecutor = null;
  }
}
