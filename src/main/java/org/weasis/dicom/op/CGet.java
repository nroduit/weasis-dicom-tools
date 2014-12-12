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
package org.weasis.dicom.op;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.net.Connection;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.getscu.GetSCU;
import org.dcm4che3.tool.getscu.GetSCU.InformationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.StringUtil;

public class CGet {

    private static final Logger LOGGER = LoggerFactory.getLogger(CGet.class);

    private CGet() {
    }

    /**
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param progress
     *            the progress handler.
     * @param keys
     *            the matching and returning keys. DicomParam with no value is a returning key.
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static GetSCU build(DicomNode callingNode, DicomNode calledNode, DicomProgress progress, File outputDir,
        DicomParam... keys) {
        return CGet.build(null, callingNode, calledNode, progress, outputDir, keys);
    }

    /**
     * @param params
     *            optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param progress
     *            the progress handler.
     * @param keys
     *            the matching and returning keys. DicomParam with no value is a returning key.
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static GetSCU build(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        DicomProgress progress, File outputDir, DicomParam... keys) {
        if (callingNode == null || calledNode == null || outputDir == null) {
            throw new IllegalArgumentException("callingNode, calledNode or outputDir cannot be null!");
        }
        GetSCU getSCU = null;
        String message = null;
        AdvancedParams options = params == null ? new AdvancedParams() : params;

        try {
            getSCU = new GetSCU(progress);
            Connection remote = getSCU.getRemoteConnection();
            Connection conn = getSCU.getConnection();
            options.configureConnect(getSCU.getAAssociateRQ(), remote, calledNode);
            options.configureBind(getSCU.getApplicationEntity(), conn, callingNode);

            // configure
            options.configure(conn);
            options.configureTLS(conn, remote);

            getSCU.setStorageDirectory(outputDir);

            getSCU.setInformationModel(getInformationModel(options), options.getTsuidOrder(), options.getQueryOptions()
                .contains(QueryOption.RELATIONAL));

            getSCU.addOfferedStorageSOPClass("1.2.840.10008.5.1.4.1.1.2", "1.2.840.10008.1.2", "1.2.840.10008.1.2.1");

            for (DicomParam p : keys) {
                getSCU.addKey(p.getTag(), p.getValues());
            }

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            getSCU.getDevice().setExecutor(executorService);
            getSCU.getDevice().setScheduledExecutor(scheduledExecutorService);
            try {
                getSCU.open();
                getSCU.retrieve();
            } finally {
                getSCU.close();
                executorService.shutdown();
                scheduledExecutorService.shutdown();
            }
        } catch (Exception e) {
            message = "getscu: " + e.getMessage();
            StringUtil.logError(LOGGER, e, message);
            DicomState dcmState = getSCU == null ? null : getSCU.getState();
            if (dcmState != null) {
                dcmState.setStatus(Status.UnableToProcess);
            }
        }
        return getSCU;
    }

    private static InformationModel getInformationModel(AdvancedParams options) {
        Object model = options.getInformationModel();
        if (model instanceof InformationModel) {
            return (InformationModel) model;
        }
        return InformationModel.StudyRoot;
    }

}
