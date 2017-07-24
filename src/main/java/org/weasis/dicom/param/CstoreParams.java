package org.weasis.dicom.param;

import java.net.URL;

public class CstoreParams {
    private final DefaultAttributeEditor attributeEditor;
    private final boolean extendNegociation;
    private final URL extendSopClassesURL;

    /**
     * @param attributeEditor
     *            a editor to modify DICOM attributes
     * @param extendNegociation
     *            extends SOP classes negotiation
     * @param extendSopClassesURL
     *            configuration file of the SOP classes negotiation extension
     */
    public CstoreParams(DefaultAttributeEditor attributeEditor, boolean extendNegociation, URL extendSopClassesURL) {
        this.attributeEditor = attributeEditor;
        this.extendNegociation = extendNegociation;
        this.extendSopClassesURL = extendSopClassesURL;
    }

    public DefaultAttributeEditor getAttributeEditor() {
        return attributeEditor;
    }

    public boolean isExtendNegociation() {
        return extendNegociation;
    }

    public URL getExtendSopClassesURL() {
        return extendSopClassesURL;
    }
}
