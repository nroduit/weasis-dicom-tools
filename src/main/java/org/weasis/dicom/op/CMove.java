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

import java.text.MessageFormat;

import org.dcm4che3.net.Connection;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.movescu.MoveSCU;
import org.dcm4che3.tool.movescu.MoveSCU.InformationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DeviceOpService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;

public class CMove {

    private static final Logger LOGGER = LoggerFactory.getLogger(CMove.class);

    private CMove() {
    }

    /**
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param destinationAet
     *            the destination AET
     * @param progress
     *            the progress handler
     * @param keys
     *            the matching and returning keys. DicomParam with no value is a returning key.
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(DicomNode callingNode, DicomNode calledNode, String destinationAet,
        DicomProgress progress, DicomParam... keys) {
        return CMove.process(null, callingNode, calledNode, destinationAet, progress, keys);
    }

    /**
     * @param params
     *            the optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param destinationAet
     *            the destination AET
     * @param progress
     *            the progress handler
     * @param keys
     *            the matching and returning keys. DicomParam with no value is a returning key.
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        String destinationAet, DicomProgress progress, DicomParam... keys) {
        if (callingNode == null || calledNode == null || destinationAet == null) {
            throw new IllegalArgumentException("callingNode, calledNode or destinationAet cannot be null!");
        }
        MoveSCU moveSCU = null;
        AdvancedParams options = params == null ? new AdvancedParams() : params;

        try {
            moveSCU = new MoveSCU(progress);
            Connection remote = moveSCU.getRemoteConnection();
            Connection conn = moveSCU.getConnection();
            options.configureConnect(moveSCU.getAAssociateRQ(), remote, calledNode);
            options.configureBind(moveSCU.getApplicationEntity(), conn, callingNode);
            DeviceOpService service = new DeviceOpService(moveSCU);

            // configure
            options.configure(conn);
            options.configureTLS(conn, remote);

            moveSCU.setInformationModel(getInformationModel(options), options.getTsuidOrder(),
                options.getQueryOptions().contains(QueryOption.RELATIONAL));

            for (DicomParam p : keys) {
                moveSCU.addKey(p.getTag(), p.getValues());
            }
            moveSCU.setDestination(destinationAet);

            service.start();
            try {
                DicomState dcmState = moveSCU.getState();
                long t1 = System.currentTimeMillis();
                moveSCU.open();
                moveSCU.retrieve();
                long t2 = System.currentTimeMillis();
                String timeMsg = MessageFormat.format("Move files from {0} to {1} in {2}ms",
                    moveSCU.getAAssociateRQ().getCallingAET(), moveSCU.getAAssociateRQ().getCalledAET(), t2 - t1);
                ServiceUtil.forceGettingAttributes(dcmState, moveSCU);
                return DicomState.buildMessage(dcmState, timeMsg, null);
            } catch (Exception e) {
                LOGGER.error("movescu", e);
                ServiceUtil.forceGettingAttributes(moveSCU.getState(), moveSCU);
                return DicomState.buildMessage(moveSCU.getState(), null, e);
            } finally {
                FileUtil.safeClose(moveSCU);
                service.stop();
            }
        } catch (Exception e) {
            LOGGER.error("movescu", e);
            return new DicomState(Status.UnableToProcess,
                "DICOM Move failed" + StringUtil.COLON_AND_SPACE + e.getMessage(), null);
        }
    }

    private static InformationModel getInformationModel(AdvancedParams options) {
        Object model = options.getInformationModel();
        if (model instanceof InformationModel) {
            return (InformationModel) model;
        }
        return InformationModel.StudyRoot;
    }

}
