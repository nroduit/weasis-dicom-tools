package org.weasis.dicom.param;

import java.net.URL;

import org.dcm4che3.data.Attributes;

public class CstoreParams {
    private final boolean generateUIDs;
    private final Attributes tagToOverride;
    private final boolean extendNegociation;
    private final URL extendSopClassesURL;

    /**
     * @param generateUIDs
     *            generate new UIDS (Study/Series/Instance)
     * @param tagToOverride
     *            list of DICOM attributes to override
     * @param extendNegociation
     *            extends SOP classes negotiation
     * @param extendSopClassesURL
     *            configuration file of the SOP classes negotiation extension
     */
    public CstoreParams(boolean generateUIDs, Attributes tagToOverride, boolean extendNegociation,
        URL extendSopClassesURL) {
        this.generateUIDs = generateUIDs;
        this.tagToOverride = tagToOverride;
        this.extendNegociation = extendNegociation;
        this.extendSopClassesURL = extendSopClassesURL;
    }

    public boolean isGenerateUIDs() {
        return generateUIDs;
    }

    public Attributes getTagToOverride() {
        return tagToOverride;
    }

    public boolean isExtendNegociation() {
        return extendNegociation;
    }

    public URL getExtendSopClassesURL() {
        return extendSopClassesURL;
    }
}
