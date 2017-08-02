package org.weasis.dicom.param;

public class AttributeEditorContext {
    /**
     * Abort status allows to skip the file transfer or abort the DICOM association
     *
     */
    public enum Abort {
        // Do nothing
        NONE,
        // Allows to skip the bulk data transfer to go to the next file
        FILE_EXCEPTION,
        // Stop the DICOM connection. Attention, this will abort other transfers when there are several destinations for one source.
        CONNECTION_EXCEPTION
    }

    private final String tsuid;
    private final DicomNode sourceNode;
    private final DicomNode destinationNode;

    private Abort abort;
    private String abortMessage;

    public AttributeEditorContext(String tsuid, DicomNode sourceNode, DicomNode destinationNode) {
        this.tsuid = tsuid;
        this.sourceNode = sourceNode;
        this.destinationNode = destinationNode;
        this.abort = Abort.NONE;
    }

    public Abort getAbort() {
        return abort;
    }

    public void setAbort(Abort abort) {
        this.abort = abort;
    }

    public String getAbortMessage() {
        return abortMessage;
    }

    public void setAbortMessage(String abortMessage) {
        this.abortMessage = abortMessage;
    }

    public String getTsuid() {
        return tsuid;
    }

    public DicomNode getSourceNode() {
        return sourceNode;
    }

    public DicomNode getDestinationNode() {
        return destinationNode;
    }

}
