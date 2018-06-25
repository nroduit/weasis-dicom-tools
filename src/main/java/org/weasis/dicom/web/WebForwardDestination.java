package org.weasis.dicom.web;

import java.io.IOException;

import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.param.ForwardDicomNode;
import org.weasis.dicom.web.StowRS.ContentType;

public class WebForwardDestination extends ForwardDestination {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebForwardDestination.class);

    private final ForwardDicomNode callingNode;
    private final String requestURL;
    private final String authentication;
    private final DicomState state;

    private volatile StowRS stowRS;

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

    public WebForwardDestination(ForwardDicomNode fwdNode, String requestURL, String authentication,
        DicomProgress progress, AttributeEditor attributesEditor) {
        super(attributesEditor);
        this.callingNode = fwdNode;
        this.requestURL = requestURL;
        this.authentication = authentication;
        this.state = new DicomState(progress == null ? new DicomProgress() : progress);
    }

    @Override
    public ForwardDicomNode getForwardDicomNode() {
        return callingNode;
    }

    public String getRequestURL() {
        return requestURL;
    }

    public StowRS getStowRS() throws IOException {
        synchronized (this) {
            if (stowRS == null) {
                this.stowRS = new StowRS(requestURL, ContentType.DICOM, null, authentication);
            }
        }
        return stowRS;
    }

    @Override
    public void stop() {
        StowRS s = stowRS;
        this.stowRS = null;
        if (s != null) {
            try {
                String response = s.writeEndMarkers();
                if (response == null && Status.isPending(state.getStatus())) {
                    state.setStatus(Status.Success);
                } else {
                    state.setMessage(response);
                }
                DicomState.buildMessage(state, null, null);
            } catch (IOException e) {
                LOGGER.error("Writing end markers", e);
                DicomState.buildMessage(state, null, e);
            }
            FileUtil.safeClose(s);
        }
    }

    @Override
    public String toString() {
        return requestURL;
    }

    public DicomState getState() {
        return state;
    }
}
