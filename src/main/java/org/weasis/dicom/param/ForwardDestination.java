package org.weasis.dicom.param;

import java.io.IOException;

import org.dcm4che3.net.Association;
import org.weasis.dicom.util.StoreFromStreamSCU;

public class ForwardDestination {

    private final StoreFromStreamSCU streamSCU;
    private final DeviceOpService streamSCUService;
    private final AttributeEditor attributesEditor;

    public ForwardDestination(AdvancedParams forwardParams, DicomNode callingNode, DicomNode destinationNode,
        AttributeEditor attributesEditor) throws IOException {
        this.streamSCU = new StoreFromStreamSCU(forwardParams, callingNode, destinationNode);
        this.streamSCUService = new DeviceOpService(streamSCU.getDevice());
        this.attributesEditor = attributesEditor;
    }

    public StoreFromStreamSCU getStreamSCU() {
        return streamSCU;
    }

    public DeviceOpService getStreamSCUService() {
        return streamSCUService;
    }

    public AttributeEditor getAttributesEditor() {
        return attributesEditor;
    }

    public void stop() {
        Association as = streamSCU.getAssociation();
        if(as != null && as.isReadyForDataTransfer()) {
            as.abort();
        }
        streamSCUService.stop();
    }
}
