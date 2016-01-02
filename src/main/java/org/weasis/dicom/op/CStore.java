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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.common.CLIUtils;
import org.dcm4che3.tool.storescu.StoreSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.StringUtil;

public class CStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(CStore.class);

    private CStore() {
    }

    /**
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param files
     *            the list of file paths
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(DicomNode callingNode, DicomNode calledNode, List<String> files) {
        return process(null, callingNode, calledNode, files);
    }

    /**
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param files
     *            the list of file paths
     * @param progress
     *            the progress handler
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */

    public static DicomState process(DicomNode callingNode, DicomNode calledNode, List<String> files,
        DicomProgress progress) {
        return process(null, callingNode, calledNode, files, progress);
    }

    /**
     * @param params
     *            optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param files
     *            the list of file paths
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        List<String> files) {
        return process(null, callingNode, calledNode, files, null);
    }

    /**
     * @param params
     *            optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param files
     *            the list of file paths
     * @param progress
     *            the progress handler
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        List<String> files, DicomProgress progress) {
        return process(params, callingNode, calledNode, files, progress, false);
    }

    /**
     * @param params
     *            optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param files
     *            the list of file paths
     * @param progress
     *            the progress handler
     * @param generateUIDs
     *            generate new UIDS (Study/Series/Instance)
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        List<String> files, DicomProgress progress, boolean generateUIDs) {
        if (callingNode == null || calledNode == null) {
            throw new IllegalArgumentException("callingNode or calledNode cannot be null!");
        }

        AdvancedParams options = params == null ? new AdvancedParams() : params;
        StoreSCU storeSCU = null;
        String message = null;

        try {
            Device device = new Device("storescu");
            Connection conn = new Connection();
            device.addConnection(conn);
            ApplicationEntity ae = new ApplicationEntity(callingNode.getAet());
            device.addApplicationEntity(ae);
            ae.addConnection(conn);
            storeSCU = new StoreSCU(ae, progress);
            Connection remote = storeSCU.getRemoteConnection();

            options.configureConnect(storeSCU.getAAssociateRQ(), remote, calledNode);
            options.configureBind(ae, conn, callingNode);

            // configure
            options.configure(conn);
            options.configureTLS(conn, remote);

            storeSCU.setGenerateUIDs(generateUIDs);
            storeSCU.setAttributes(new Attributes());

            // TODO implement
            // configureRelatedSOPClass(storeSCU);
            // CLIUtils.addAttributes(main.attrs, cl.getOptionValues("s"));
            // storeSCU.setUIDSuffix(cl.getOptionValue("uid-suffix"));
            storeSCU.setPriority(options.getPriority());

            storeSCU.scanFiles(files, false);

            DicomState dcmState = storeSCU.getState();

            int n = storeSCU.getFilesScanned();
            if (n == 0) {
                dcmState.setMessage("No DICOM file has been found!");
                dcmState.setStatus(Status.UnableToProcess);
            } else {
                ExecutorService executorService = Executors.newSingleThreadExecutor();
                ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
                device.setExecutor(executorService);
                device.setScheduledExecutor(scheduledExecutorService);
                try {
                    long t1 = System.currentTimeMillis();
                    storeSCU.open();
                    storeSCU.sendFiles();
                    long t2 = System.currentTimeMillis();
                    dcmState.setMessage(MessageFormat.format("Sent files from {0} to {1} in {2}ms",
                        storeSCU.getAAssociateRQ().getCallingAET(), storeSCU.getAAssociateRQ().getCalledAET(),
                        t2 - t1));
                    dcmState.setStatus(Status.Success);
                } finally {
                    storeSCU.close();
                    executorService.shutdown();
                    scheduledExecutorService.shutdown();
                }
            }
        } catch (Exception e) {
            message = "DICOM Store failed, storescu: " + e.getMessage();
            StringUtil.logError(LOGGER, e, message);
            DicomState dcmState = storeSCU == null ? null : storeSCU.getState();
            if (dcmState != null) {
                dcmState.setStatus(Status.UnableToProcess);
            }
        }

        DicomState dcmState = storeSCU == null ? null : storeSCU.getState();
        if (dcmState == null) {
            dcmState = new DicomState(Status.UnableToProcess, message, null);
        }
        return dcmState;
    }

    public static void configureRelatedSOPClass(StoreSCU storescu) throws IOException {
        storescu.enableSOPClassRelationshipExtNeg(true);
        Properties p = new Properties();
        CLIUtils.loadProperties("resource:rel-sop-classes.properties", p);
        // CLIUtils.loadProperties(cl.hasOption("rel-sop-classes") ? cl.getOptionValue("rel-ext-neg")
        // : "resource:rel-sop-classes.properties", p);
        storescu.relSOPClasses.init(p);
    }
}
