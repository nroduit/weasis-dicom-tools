package org.weasis.dicom.param;

public abstract class ForwardDestination {

    protected final AttributeEditor attributesEditor;

    public ForwardDestination(AttributeEditor attributesEditor) {
        this.attributesEditor = attributesEditor;
    }

    public AttributeEditor getAttributesEditor() {
        return attributesEditor;
    }

    public abstract ForwardDicomNode getForwardDicomNode();

    public abstract void stop();
}