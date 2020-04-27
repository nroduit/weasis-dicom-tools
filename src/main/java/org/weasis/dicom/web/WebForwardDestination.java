/*******************************************************************************
 * Copyright (c) 2009-2019 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.web;

import java.io.IOException;
import java.util.Map;

import org.weasis.core.util.FileUtil;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.param.ForwardDicomNode;

public class WebForwardDestination extends ForwardDestination {

    private final ForwardDicomNode callingNode;
    private final DicomState state;
    private final DicomStowRS stowRS;

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
        this(null, fwdNode, requestURL, headers, progress, attributesEditor);
    }
    public WebForwardDestination(Long id, ForwardDicomNode fwdNode, String requestURL, Map<String, String> headers,
        DicomProgress progress, AttributeEditor attributesEditor) {
        super(id, attributesEditor);
        this.callingNode = fwdNode;
        this.state = new DicomState(progress == null ? new DicomProgress() : progress);
        this.stowRS = new DicomStowRS(requestURL, ContentType.APPLICATION_DICOM, null, headers);
    }
    public WebForwardDestination(ForwardDicomNode fwdNode, DicomStowRS uploadManager, DicomProgress progress,
        AttributeEditor attributesEditor) {
        this(null, fwdNode, uploadManager, progress, attributesEditor);
    }
    public WebForwardDestination(Long id, ForwardDicomNode fwdNode, DicomStowRS uploadManager, DicomProgress progress,
        AttributeEditor attributesEditor) {
        super(id, attributesEditor);
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

    public DicomStowRS getStowrsSingleFile() throws IOException {
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
