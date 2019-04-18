package org.weasis.dicom.web;

import java.io.IOException;
import java.util.Map;

import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.param.ForwardDicomNode;
import org.weasis.dicom.web.AbstractStowrs.ContentType;

public class WebForwardDestination extends ForwardDestination {

    private final ForwardDicomNode callingNode;
    private final DicomState state;
    private final UploadSingleFile stowRS;

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
        this.state = new DicomState(progress == null ? new DicomProgress() : progress);
        this.stowRS = new StowrsSingleFile(requestURL, ContentType.DICOM, null, headers);
    }
    
    public WebForwardDestination(ForwardDicomNode fwdNode, UploadSingleFile uploadManager,
        DicomProgress progress, AttributeEditor attributesEditor) {
        super(attributesEditor);
        this.callingNode = fwdNode;
        this.state = new DicomState(progress == null ? new DicomProgress() : progress);
        this.stowRS = uploadManager;
    }

    @Override
    public ForwardDicomNode getForwardDicomNode() {
        return callingNode;
    }

    public String getRequestURL() {
        return stowRS.getRequestURL();
    }

    public UploadSingleFile getStowrsSingleFile() throws IOException {
        return stowRS;
    }

    @Override
    public void stop() {
        FileUtil.safeClose(stowRS);
    }

    @Override
    public String toString() {
        return stowRS.getRequestURL();
    }

    public DicomState getState() {
        return state;
    }
}
