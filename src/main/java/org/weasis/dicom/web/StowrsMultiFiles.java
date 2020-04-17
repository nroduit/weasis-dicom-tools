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

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

public class StowrsMultiFiles extends AbstractStowrs {
    private static final Logger LOGGER = LoggerFactory.getLogger(StowrsMultiFiles.class);

    public StowrsMultiFiles(String requestURL, Multipart.ContentType contentType) throws IOException {
        this(requestURL, contentType, null, null);
    }

    public StowrsMultiFiles(String requestURL, Multipart.ContentType contentType, String agentName, Map<String, String> headers)
        throws IOException {
        super(requestURL, contentType, agentName, headers);
    }

    public DicomState uploadDicom(List<String> filesOrFolders, boolean recursive) throws IOException {
        HttpURLConnection httpPost = buildConnection();
        DicomState state = new DicomState(new DicomProgress());
        String message = null;
        int nbFile = 0;
        try (DataOutputStream out = new DataOutputStream(httpPost.getOutputStream())) {
            for (String entry : filesOrFolders) {
                File file = new File(entry);
                if (file.isDirectory()) {
                    List<File> fileList = new ArrayList<>();
                    FileUtil.getAllFilesInDirectory(file, fileList, recursive);
                    for (File f : fileList) {
                        uploadFile(f, out);
                        nbFile++;
                    }
                } else {
                    uploadFile(file, out);
                    nbFile++;
                }
            }
            Attributes error = writeEndMarkers(httpPost, out);
            if (error == null) {
                state.setStatus(Status.Success);
                message = "all the files has been tranfered";
            } else {
                message = "one or more files has not been tranfered";
                state.setStatus(Status.OneOrMoreFailures);
                DicomProgress p = state.getProgress();
                if (p != null) {
                    Sequence seq = error.getSequence(Tag.FailedSOPSequence);
                    if (seq != null && !seq.isEmpty()) {
                        Attributes cmd = Optional.ofNullable(p.getAttributes()).orElseGet(Attributes::new);
                        cmd.setInt(Tag.Status, VR.US, Status.OneOrMoreFailures);
                        cmd.setInt(Tag.NumberOfCompletedSuboperations, VR.US, nbFile);
                        cmd.setInt(Tag.NumberOfFailedSuboperations, VR.US, seq.size());
                        cmd.setInt(Tag.NumberOfWarningSuboperations, VR.US, 0);
                        cmd.setInt(Tag.NumberOfRemainingSuboperations, VR.US, 0);
                        p.setAttributes(cmd);
                        message = seq.stream().map(s -> s.getString(Tag.ReferencedSOPInstanceUID, "Unknown SopUID")
                            + " -> " + s.getString(Tag.FailureReason)).collect(Collectors.joining(","));
                        return DicomState.buildMessage(state, null,
                            new RuntimeException("Failed instances: " + message));
                    }
                }
            }
        } catch (Exception e) {
            state.setStatus(Status.UnableToProcess);
            LOGGER.error("STOWRS: error when posting data", e); //$NON-NLS-1$
            return DicomState.buildMessage(state, "STOWRS: error when posting data", e);
        } finally {
            removeConnection(httpPost);
        }
        return DicomState.buildMessage(state, message, null);
    }

    private void uploadFile(File file, DataOutputStream out) throws IOException {
        writeContentMarkers(out);

        // write dicom binary file
        Files.copy(file.toPath(), out);
    }

}
