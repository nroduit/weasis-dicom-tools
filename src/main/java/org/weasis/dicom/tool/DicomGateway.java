package org.weasis.dicom.tool;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;

import org.dcm4che3.net.Connection;
import org.dcm4che3.net.TransferCapability;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.DeviceListenerService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.param.GatewayParams;
import org.weasis.dicom.util.StoreScpForward;

public class DicomGateway {
    private final StoreScpForward storeSCP;
    private final DeviceListenerService deviceService;

    public DicomGateway(DicomNode callingNode) throws IOException {
        this(null, callingNode, null, null);
    }

    /**
     * Build a DICOM Gateway with one final destination
     * 
     * @param forwardParams
     *            the optional advanced parameters (proxy, authentication, connection and TLS) for the final destination
     * @param callingNode
     *            the calling DICOM node configuration
     * @param destinationNode
     *            the final DICOM node configuration
     * @throws IOException
     */
    public DicomGateway(AdvancedParams forwardParams, DicomNode callingNode, DicomNode destinationNode)
        throws IOException {
        this(forwardParams, callingNode, destinationNode, null);
    }

    /**
     * Build a DICOM Gateway with one final destination
     * 
     * @param forwardParams
     *            the optional advanced parameters (proxy, authentication, connection and TLS) for the final destination
     * @param callingNode
     *            the calling DICOM node configuration
     * @param destinationNode
     *            the final DICOM node configuration
     * @param attributesEditor
     *            the editor for modifying attributes on the fly (can be Null)
     * @throws IOException
     */
    public DicomGateway(AdvancedParams forwardParams, DicomNode callingNode, DicomNode destinationNode,
        AttributeEditor attributesEditor) throws IOException {
        this.storeSCP = new StoreScpForward(forwardParams, callingNode, destinationNode, attributesEditor);
        this.deviceService = new DeviceListenerService(storeSCP.getDevice());
    }
    
    public DicomGateway(Map<DicomNode, ForwardDestination> destinations) throws IOException {
        this.storeSCP = new StoreScpForward(destinations);
        this.deviceService = new DeviceListenerService(storeSCP.getDevice());
    }


    public boolean isRunning() {
        return storeSCP.getConnection().isListening();
    }

    public StoreScpForward getStoreScpForward() {
        return storeSCP;
    }

    public void start(DicomNode scpNode) throws Exception {
        start(scpNode, new GatewayParams(false));
    }

    public synchronized void start(DicomNode scpNode, GatewayParams params) throws Exception {
        if (isRunning()) {
            throw new IOException("Cannot start a DICOM Gateway because it is already running.");
        }
        storeSCP.setStatus(0);

        AdvancedParams options = Objects.requireNonNull(params).getParams();
        Connection conn = storeSCP.getConnection();
        if (params.isBindCallingAet()) {
            options.configureBind(storeSCP.getApplicationEntity(), conn, scpNode);
        } else {
            options.configureBind(conn, scpNode);
        }
        // configure
        options.configure(conn);
        options.configureTLS(conn, null);

        // Limit the calling AETs
        storeSCP.getApplicationEntity().setAcceptedCallingAETitles(params.getAcceptedCallingAETitles());

        URL transferCapabilityFile = params.getTransferCapabilityFile();
        if (transferCapabilityFile != null) {
            storeSCP.loadDefaultTransferCapability(transferCapabilityFile);
        } else {
            storeSCP.getApplicationEntity()
                .addTransferCapability(new TransferCapability(null, "*", TransferCapability.Role.SCP, "*"));
        }

        deviceService.start();
    }

    public synchronized void stop() {
        deviceService.stop();
        storeSCP.stop();
    }
}
