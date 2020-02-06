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
package org.weasis.dicom.util;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.net.DimseRSP;
import org.dcm4che6.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

public class ServiceUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceUtil.class);

    public enum ProgressStatus {
        FAILED, WARNING, COMPLETED
    }

    private ServiceUtil() {
    }

    /**
     * Get a connection from a <code>DicomNode</code>
     * 
     * @param dicomNode
     * @return
     */
    public static Connection getConnection(DicomNode dicomNode) {
        Connection con = new Connection();
        if (Objects.requireNonNull(dicomNode).getHostname() != null) {
            con.setHostname(dicomNode.getHostname());
        }
        if (dicomNode.getPort() != null) {
            con.setPort(dicomNode.getPort());
        }
        return con;
    }

    public static int getStatus(DimseRSP resp) {
        if (resp != null && resp.command != null) {
            return resp.command.getInt(Tag.Status).orElse(Status.UnableToProcess);
        }
        return Status.UnableToProcess;
    }

    public static ProgressStatus setDicomRSP(DimseRSP resp, DicomState state, int filesScanned) {
        if (resp == null || resp.command == null) {
            return ProgressStatus.FAILED;
        }
        int status = resp.command.getInt(Tag.Status).orElse(Status.Pending);
        state.setStatus(status);
        ProgressStatus ps;

        switch (status) {
            case Status.Success:
                ps = ProgressStatus.COMPLETED;
                break;
            case Status.CoercionOfDataElements:
            case Status.ElementsDiscarded:
            case Status.DataSetDoesNotMatchSOPClassWarning:
                ps = ProgressStatus.WARNING;
                break;
            default:
                ps = ProgressStatus.FAILED;
        }
        ServiceUtil.notifyProgession(state.getProgress(), resp.command, ps, filesScanned);
        return ps;
    }

    public static void shutdownService(ExecutorService executorService) {
        if (executorService != null) {
            try {
                executorService.shutdown();
            } catch (Exception e) {
                LOGGER.error("ExecutorService shutdown", e);
            }
        }
    }

    public static void forceGettingAttributes(DicomState dcmState, AutoCloseable closeable) {
        DicomProgress p = dcmState.getProgress();
        if (p != null) {
            FileUtil.safeClose(closeable);
        }
    }

    public static void safeClose(DicomInputStream in) {
        if (in != null) {
            // for (File file : in.getBulkDataFiles()) {
            // FileUtil.delete(file);
            // }
        }
    }

    public static void notifyProgession(DicomState state, String iuid, String cuid, int status, ProgressStatus ps,
        int numberOfSuboperations) {
        state.setStatus(status);
        DicomProgress p = state.getProgress();
        if (p != null) {
            DicomObject cmd = Optional.ofNullable(p.getAttributes()).orElseGet(() -> DicomObject.newDicomObject());
            cmd.setInt(Tag.Status, VR.US, status);
            cmd.setString(Tag.AffectedSOPInstanceUID, VR.UI, iuid);
            cmd.setString(Tag.AffectedSOPClassUID, VR.UI, cuid);
            notifyProgession(p, cmd, ps, numberOfSuboperations);
            p.setAttributes(cmd);
        }
    }

    public static void notifyProgession(DicomProgress p, DicomObject cmd, ProgressStatus ps,
        int numberOfSuboperations) {
        if (p != null && cmd != null) {
            int c;
            int f;
            int r;
            int w;
            if (p.getAttributes() == null) {
                c = 0;
                f = 0;
                w = 0;
                r = numberOfSuboperations;
            } else {
                c = p.getNumberOfCompletedSuboperations().orElse(0);
                f = p.getNumberOfFailedSuboperations().orElse(0);
                w = p.getNumberOfWarningSuboperations().orElse(0);
                r = numberOfSuboperations - (c + f + w);
            }

            if (r < 1) {
                r = 1;
            }

            if (ps == ProgressStatus.COMPLETED) {
                c++;
            } else if (ps == ProgressStatus.FAILED) {
                f++;
            } else if (ps == ProgressStatus.WARNING) {
                w++;
            }
            cmd.setInt(Tag.NumberOfCompletedSuboperations, VR.US, c);
            cmd.setInt(Tag.NumberOfFailedSuboperations, VR.US, f);
            cmd.setInt(Tag.NumberOfWarningSuboperations, VR.US, w);
            cmd.setInt(Tag.NumberOfRemainingSuboperations, VR.US, r - 1);
        }
    }

    public static int getTotalOfSuboperations(DicomObject cmd) {
        if (cmd != null) {
            int c = cmd.getInt(Tag.NumberOfCompletedSuboperations).orElse(0);
            int f = cmd.getInt(Tag.NumberOfFailedSuboperations).orElse(0);
            int w = cmd.getInt(Tag.NumberOfWarningSuboperations).orElse(0);
            int r = cmd.getInt(Tag.NumberOfRemainingSuboperations).orElse(0);
            return r + c + f + w;
        }
        return 0;
    }
}
