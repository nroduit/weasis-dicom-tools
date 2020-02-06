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
package org.weasis.dicom.param;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.net.Status;



public class DicomProgress implements CancelListener {

    private final List<ProgressListener> listenerList;
    private DicomObject attributes;
    private volatile boolean cancel;
    private File processedFile;
    private volatile boolean lastFailed = false;

    public DicomProgress() {
        this.cancel = false;
        this.listenerList = new ArrayList<>();
    }

    public DicomObject getAttributes() {
        return attributes;
    }

    public void setAttributes(DicomObject attributes) {
        synchronized (this) {
            OptionalInt failed = getNumberOfFailedSuboperations();
            this.attributes = attributes;
            lastFailed =  getNumberOfFailedSuboperations().orElse(0) > failed.orElse(0);
        }

        fireProgress();
    }

    public boolean isLastFailed() {
        return lastFailed;
    }

    public synchronized File getProcessedFile() {
        return processedFile;
    }

    public synchronized void setProcessedFile(File processedFile) {
        this.processedFile = processedFile;
    }

    public void addProgressListener(ProgressListener listener) {
        if (listener != null && !listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    public void removeProgressListener(ProgressListener listener) {
        if (listener != null) {
            listenerList.remove(listener);
        }
    }

    private void fireProgress() {
        for (ProgressListener progressListener : listenerList) {
            progressListener.handleProgression(this);
        }
    }

    @Override
    public void cancel() {
        this.cancel = true;
    }

    public boolean isCancel() {
        return cancel;
    }

    private OptionalInt getIntTag(int tag) {
        DicomObject dcm = attributes;
        if (dcm == null) {
            return OptionalInt.empty();
        }
        return dcm.getInt(tag);
    }

    public OptionalInt getStatus() {
        if (isCancel()) {
            return OptionalInt.of(Status.Cancel);
        }
        DicomObject dcm = attributes;
        if (dcm == null) {
            return  OptionalInt.empty();
        }
        return dcm.getInt(Tag.Status);
    }

    public String getErrorComment() {
        DicomObject dcm = attributes;
        if (dcm == null) {
            return null;
        }
        return dcm.getString(Tag.ErrorComment).orElse(null);
    }

    public OptionalInt getNumberOfRemainingSuboperations() {
        return getIntTag(Tag.NumberOfRemainingSuboperations);
    }

    public OptionalInt getNumberOfCompletedSuboperations() {
        return getIntTag(Tag.NumberOfCompletedSuboperations);
    }

    public OptionalInt getNumberOfFailedSuboperations() {
        return getIntTag(Tag.NumberOfFailedSuboperations);
    }

    public OptionalInt getNumberOfWarningSuboperations() {
        return getIntTag(Tag.NumberOfWarningSuboperations);
    }

}
