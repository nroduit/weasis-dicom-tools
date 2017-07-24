package org.weasis.dicom.param;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.net.Device;
import org.weasis.dicom.util.ServiceUtil;

public class DeviceOpService {

    protected final Device device;
    protected ExecutorService executor;
    protected ScheduledExecutorService scheduledExecutor;

    public DeviceOpService(Device device) {
        this.device = Objects.requireNonNull(device);
    }

    public Device getDevice() {
        return device;
    }

    public boolean isRunning() {
        return executor != null;
    }

    public synchronized void start() {
        if (!isRunning()) {
            executor = Executors.newSingleThreadExecutor();
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            device.setExecutor(executor);
            device.setScheduledExecutor(scheduledExecutor);
        }
    }

    public synchronized void stop() {
        ServiceUtil.shutdownService(scheduledExecutor);
        ServiceUtil.shutdownService(executor);
        executor = null;
        scheduledExecutor = null;
    }

}
