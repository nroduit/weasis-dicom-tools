/*******************************************************************************
 * Copyright (c) 2014 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.param;

import java.util.ArrayList;
import java.util.List;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Status;

public class DicomState {
    private int status;
    private String message;
    private final List<Attributes> dicomRSP;
    private final DicomProgress progress;

    public DicomState() {
        this(Status.Pending, null, null);
    }

    public DicomState(DicomProgress progress) {
        this(Status.Pending, null, progress);
    }

    public DicomState(int status, String message, DicomProgress progress) {
        this.status = status;
        this.message = message;
        this.progress = progress;
        this.dicomRSP = new ArrayList<>();
    }

    /**
     * Get the DICOM status
     * 
     * @ see org.dcm4che3.net.Status
     * 
     * @return the DICOM status of the process
     */
    public int getStatus() {
        if (progress != null && progress.getAttributes() != null) {
            return progress.getStatus();
        }
        return status;
    }

    /**
     * Set the DICOM status
     * 
     * @ see org.dcm4che3.net.Status
     * 
     * @param the
     *            DICOM status of the process
     */
    public void setStatus(int status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public DicomProgress getProgress() {
        return progress;
    }

    public List<Attributes> getDicomRSP() {
        return dicomRSP;
    }

    public void addDicomRSP(Attributes dicomRSP) {
        if (dicomRSP != null) {
            this.dicomRSP.add(dicomRSP);
        }
    }

}
