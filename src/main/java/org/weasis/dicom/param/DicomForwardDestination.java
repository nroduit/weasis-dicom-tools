package org.weasis.dicom.param;

import java.io.IOException;

import org.dcm4che3.net.Association;
import org.weasis.dicom.util.StoreFromStreamSCU;

public class DicomForwardDestination extends ForwardDestination {

    private final StoreFromStreamSCU streamSCU;
    private final DeviceOpService streamSCUService;
    private final boolean useDestinationAetForKeyMap;

    private final ForwardDicomNode callingNode;
    private final DicomNode destinationNode;

    public DicomForwardDestination(ForwardDicomNode fwdNode, DicomNode destinationNode) throws IOException {
        this(null, fwdNode, destinationNode, null);
    }

    public DicomForwardDestination(AdvancedParams forwardParams, ForwardDicomNode fwdNode, DicomNode destinationNode)
        throws IOException {
        this(forwardParams, fwdNode, destinationNode, null);
    }

    public DicomForwardDestination(AdvancedParams forwardParams, ForwardDicomNode fwdNode, DicomNode destinationNode,
        AttributeEditor attributesEditor) throws IOException {
        this(forwardParams, fwdNode, destinationNode, false, null, attributesEditor);
    }

    /**
     * @param forwardParams
     *            optional advanced parameters (proxy, authentication, connection and TLS)
     * @param fwdNode
     *            the DICOM forwarding node. Cannot be null.
     * @param destinationNode
     *            the DICOM destination node. Cannot be null.
     * @param useDestinationAetForKeyMap
     * @param progress
     * @param attributesEditor
     * @throws IOException
     */
    public DicomForwardDestination(AdvancedParams forwardParams, ForwardDicomNode fwdNode, DicomNode destinationNode,
        boolean useDestinationAetForKeyMap, DicomProgress progress, AttributeEditor attributesEditor)
        throws IOException {
        super(attributesEditor);
        this.callingNode = fwdNode;
        this.destinationNode = destinationNode;
        this.streamSCU = new StoreFromStreamSCU(forwardParams, fwdNode, destinationNode, progress);
        this.streamSCUService = new DeviceOpService(streamSCU.getDevice());
        this.useDestinationAetForKeyMap = useDestinationAetForKeyMap;
    }

    public StoreFromStreamSCU getStreamSCU() {
        return streamSCU;
    }

    public DeviceOpService getStreamSCUService() {
        return streamSCUService;
    }

    public boolean isUseDestinationAetForKeyMap() {
        return useDestinationAetForKeyMap;
    }

    @Override
    public ForwardDicomNode getForwardDicomNode() {
        return callingNode;
    }

    public DicomNode getDestinationNode() {
        return destinationNode;
    }

    @Override
    public void stop() {
        Association as = streamSCU.getAssociation();
        if (as != null && as.isReadyForDataTransfer()) {
            as.abort();
        }
        streamSCUService.stop();
    }

    @Override
    public String toString() {
        return destinationNode.toString();
    }
}
