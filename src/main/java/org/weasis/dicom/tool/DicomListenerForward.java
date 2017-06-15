package org.weasis.dicom.tool;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.TransferCapability;
import org.weasis.dicom.op.StoreScpForward;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.DicomNode;

public class DicomListenerForward {
    private final StoreScpForward storeSCP;
    private AdvancedParams params;
    private URL transferCapabilityFile;

    public DicomListenerForward(DicomNode callingNode, DicomNode destinationNode, AttributeEditor attributesEditor)
        throws IOException {
        this.storeSCP = new StoreScpForward(callingNode, destinationNode, attributesEditor);
    }

    public AdvancedParams getParams() {
        return params;
    }

    public void setParams(AdvancedParams params) {
        this.params = params;
    }

    public URL getTransferCapabilityFile() {
        return transferCapabilityFile;
    }

    public void setTransferCapabilityFile(URL url) {
        this.transferCapabilityFile = url;
    }

    public boolean isRunning() {
        return storeSCP.getConnection().isListening();
    }

    public void start(DicomNode scpNode) throws IOException, GeneralSecurityException {
        start(scpNode, false, true);
    }

    public void start(DicomNode scpNode, boolean bindOnlyHostnameFromDicomNode, boolean acceptAllSopClasses)
        throws IOException, GeneralSecurityException {
        AdvancedParams options = params == null ? new AdvancedParams() : params;
        Connection conn = storeSCP.getConnection();
        Device device = storeSCP.getDevice();
        DicomNode node = bindOnlyHostnameFromDicomNode ? scpNode : new DicomNode(scpNode.getAet(), scpNode.getPort());
        options.configureBind(storeSCP.getApplicationEntity(), conn, node);
        // configure
        options.configure(conn);
        // Allow more than 1 operations
        conn.setMaxOpsInvoked(0);
        conn.setMaxOpsPerformed(0);
        options.configureTLS(conn, null);
        if (acceptAllSopClasses) {
            storeSCP.getApplicationEntity()
                .addTransferCapability(new TransferCapability(null, "*", TransferCapability.Role.SCP, "*"));
        } else {
            storeSCP.loadDefaultTransferCapability(transferCapabilityFile);
        }

        // SCP requires a cache thread pool
        ExecutorService executorService = Executors.newCachedThreadPool();
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        device.setExecutor(executorService);
        device.setScheduledExecutor(scheduledExecutorService);
        device.bindConnections();
    }

    public void stop() {
        storeSCP.getDevice().unbindConnections();

        Executor executorService = storeSCP.getDevice().getExecutor();
        if (executorService instanceof ExecutorService) {
            ((ExecutorService) executorService).shutdown();
        }

        ScheduledExecutorService scheduledExecutorService = storeSCP.getDevice().getScheduledExecutor();
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
        }
    }
}
