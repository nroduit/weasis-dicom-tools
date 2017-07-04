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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.storescu.StoreSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.tool.ServiceUtil;

public class Echo {

    private static final Logger LOGGER = LoggerFactory.getLogger(Echo.class);

    private Echo() {
    }

    /**
     * @param callingAET
     *            the calling AET
     * @param calledNode
     *            the called DICOM node configuration
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(String callingAET, DicomNode calledNode) {
        return process(new DicomNode(callingAET), calledNode);
    }

    /**
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(DicomNode callingNode, DicomNode calledNode) {
        return process(null, callingNode, calledNode);
    }

    /**
     * @param params
     *            optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode) {
        if (callingNode == null || calledNode == null) {
            throw new IllegalArgumentException("callingNode or calledNode cannot be null!");
        }

        AdvancedParams options = params == null ? new AdvancedParams() : params;

        try {
            Device device = new Device("storescu");
            Connection conn = new Connection();
            device.addConnection(conn);
            ApplicationEntity ae = new ApplicationEntity(callingNode.getAet());
            device.addApplicationEntity(ae);
            ae.addConnection(conn);
            StoreSCU storeSCU = new StoreSCU(ae, null);
            Connection remote = storeSCU.getRemoteConnection();

            options.configureConnect(storeSCU.getAAssociateRQ(), remote, calledNode);
            options.configureBind(ae, conn, callingNode);

            // configure
            options.configure(conn);
            options.configureTLS(conn, remote);

            storeSCU.setPriority(options.getPriority());

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            device.setExecutor(executorService);
            device.setScheduledExecutor(scheduledExecutorService);
            try {
                long t1 = System.currentTimeMillis();
                storeSCU.open();
                storeSCU.echo();
                long t2 = System.currentTimeMillis();
                String message = MessageFormat.format("Successful DICOM Echo. Connected from {0} to {1} in {2}ms",
                    storeSCU.getAAssociateRQ().getCallingAET(), storeSCU.getAAssociateRQ().getCalledAET(), t2 - t1);
                return new DicomState(Status.Success, message, null);
            } finally {
                FileUtil.safeClose(storeSCU);
                ServiceUtil.shutdownService(executorService);
                ServiceUtil.shutdownService(scheduledExecutorService);
            }
        } catch (Exception e) {
            String message = "DICOM Echo failed, storescu: " + e.getMessage();
            LOGGER.error(message, e);
            return new DicomState(Status.UnableToProcess, message, null);
        }
    }

}
