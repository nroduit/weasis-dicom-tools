package org.weasis.dicom.tool;

import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

public class ServiceUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceUtil.class);

    private ServiceUtil() {
    }

    public static void shutdownService(ExecutorService executorService) {
        try {
            executorService.shutdown();
        } catch (Exception e) {
            LOGGER.error("ExecutorService shutdown", e);
        }
    }

    public static void forceGettingAttributes(DicomState dcmState, AutoCloseable closeable) {
        DicomProgress p = dcmState.getProgress();
        if (p != null) {
            FileUtil.safeClose(closeable);
        }
    }
}
