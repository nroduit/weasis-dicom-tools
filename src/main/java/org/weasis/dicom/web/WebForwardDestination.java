package org.weasis.dicom.web;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.param.ForwardDicomNode;
import org.weasis.dicom.web.AbstractStowrs.ContentType;

public class WebForwardDestination extends ForwardDestination {

    private final ForwardDicomNode callingNode;
    private final String requestURL;
    private final Map<String, String> headers;
    private final DicomState state;

    private volatile StowrsSingleFile stowRS;

    public WebForwardDestination(ForwardDicomNode fwdNode, String requestURL) {
        this(fwdNode, requestURL, null);
    }

    public WebForwardDestination(ForwardDicomNode fwdNode, String requestURL, AttributeEditor attributesEditor) {
        this(fwdNode, requestURL, null, attributesEditor);
    }

    public WebForwardDestination(ForwardDicomNode fwdNode, String requestURL, DicomProgress progress,
        AttributeEditor attributesEditor) {
        this(fwdNode, requestURL, null, progress, attributesEditor);
    }

    public WebForwardDestination(ForwardDicomNode fwdNode, String requestURL, Map<String, String> headers,
        DicomProgress progress, AttributeEditor attributesEditor) {
        super(attributesEditor);
        this.callingNode = fwdNode;
        this.requestURL = Objects.requireNonNull(requestURL, "requestURL cannot be null");
        this.headers = headers;
        this.state = new DicomState(progress == null ? new DicomProgress() : progress);
    }

    @Override
    public ForwardDicomNode getForwardDicomNode() {
        return callingNode;
    }

    public String getRequestURL() {
        return requestURL;
    }

    public StowrsSingleFile getStowrsSingleFile() throws IOException {
        synchronized (this) {
            if (stowRS == null) {
                this.stowRS = new StowrsSingleFile(requestURL, ContentType.DICOM, null, headers);
            }
        }
        return stowRS;
    }

    @Override
    public void stop() {
        StowrsSingleFile s = stowRS;
        this.stowRS = null;
        FileUtil.safeClose(s);
    }

    @Override
    public String toString() {
        return requestURL;
    }

    public DicomState getState() {
        return state;
    }
}
