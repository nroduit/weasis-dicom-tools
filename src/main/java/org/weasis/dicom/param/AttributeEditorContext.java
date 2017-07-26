package org.weasis.dicom.param;

public class AttributeEditorContext {
    public enum Abort {
        NONE, FILE_EXCEPTION, CONNECTION_EXCEPTION
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
