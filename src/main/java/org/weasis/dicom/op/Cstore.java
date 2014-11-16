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
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.StringUtil;

public class Cstore {

    private static final Logger LOGGER = LoggerFactory.getLogger(Cstore.class);

    private Cstore() {
    }

    /**
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(DicomNode callingNode, DicomNode calledNode, List<String> files) {
        return process(null, callingNode, calledNode, files);
    }

    /**
     * @param params
     *            optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param files
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        List<String> files) {
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
            StoreSCU storeSCU = new StoreSCU(ae);
            Connection remote = storeSCU.getRemoteConnection();

            options.configureConnect(storeSCU.getAAssociateRQ(), remote, calledNode);
            options.configureBind(ae, conn, callingNode);

            // configure
            options.configure(conn);
            options.configureTLS(conn, remote);

            storeSCU.setAttributes(new Attributes());

            // TODO implement
            // configureRelatedSOPClass(storeSCU);
            // CLIUtils.addAttributes(main.attrs, cl.getOptionValues("s"));
            // storeSCU.setUIDSuffix(cl.getOptionValue("uid-suffix"));
            storeSCU.setPriority(options.getPriority());

            storeSCU.scanFiles(files, false);

            int n = storeSCU.getFilesScanned();
            if (n == 0) {
                return new DicomState(Status.UnableToProcess, "No DICOM file has been found!", null);
            }

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            device.setExecutor(executorService);
            device.setScheduledExecutor(scheduledExecutorService);
            try {
                long t1 = System.currentTimeMillis();
                storeSCU.open();
                storeSCU.sendFiles();
                long t2 = System.currentTimeMillis();
                String message =
                    MessageFormat.format("Successful DICOM Store. Send files from {0} to {1} in {2}ms", storeSCU
                        .getAAssociateRQ().getCallingAET(), storeSCU.getAAssociateRQ().getCalledAET(), t2 - t1);
                return new DicomState(Status.Success, message, null);
            } finally {
                storeSCU.close();
                executorService.shutdown();
                scheduledExecutorService.shutdown();
            }
        } catch (Exception e) {
            String message = "DICOM Store failed, storescu: " + e.getMessage();
            StringUtil.logError(LOGGER, e, message);
            return new DicomState(Status.UnableToProcess, message, null);
        }

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
