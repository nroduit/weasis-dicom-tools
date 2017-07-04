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
import java.net.URL;
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
import org.dcm4che3.tool.storescu.StoreSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.CstoreParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.tool.ServiceUtil;

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
        return process(params, callingNode, calledNode, files, null);
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
        return process(params, callingNode, calledNode, files, progress, null);
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
     * @param cstoreParams
     *            c-store options, see CstoreParams
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        List<String> files, DicomProgress progress, CstoreParams cstoreParams) {
        if (callingNode == null || calledNode == null) {
            throw new IllegalArgumentException("callingNode or calledNode cannot be null!");
        }

        AdvancedParams options = params == null ? new AdvancedParams() : params;
        CstoreParams storeOptions = cstoreParams == null ? new CstoreParams(false, null, false, null) : cstoreParams;

        StoreSCU storeSCU = null;

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

            storeSCU.setGenerateUIDs(storeOptions.isGenerateUIDs());
            storeSCU.setAttributes(
                storeOptions.getTagToOverride() == null ? new Attributes() : storeOptions.getTagToOverride());

            if (storeOptions.isExtendNegociation()) {
                configureRelatedSOPClass(storeSCU, storeOptions.getExtendSopClassesURL());
            }
            // storeSCU.setUIDSuffix(cl.getOptionValue("uid-suffix"));
            storeSCU.setPriority(options.getPriority());

            storeSCU.scanFiles(files, false);

            DicomState dcmState = storeSCU.getState();

            int n = storeSCU.getFilesScanned();
            if (n == 0) {
                return new DicomState(Status.UnableToProcess, "No DICOM file has been found!", null);
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
                    String timeMsg = MessageFormat.format("Sent files from {0} to {1} in {2}ms. Total size {3}",
                        storeSCU.getAAssociateRQ().getCallingAET(), storeSCU.getAAssociateRQ().getCalledAET(), t2 - t1,
                        FileUtil.formatSize(storeSCU.getTotalSize()));
                    ServiceUtil.forceGettingAttributes(dcmState, storeSCU);
                    return DicomState.buildMessage(dcmState, timeMsg, null);
                } catch (Exception e) {
                    LOGGER.error("storescu", e);
                    ServiceUtil.forceGettingAttributes(storeSCU.getState(), storeSCU);
                    return DicomState.buildMessage(storeSCU.getState(), null, e);
                } finally {
                    FileUtil.safeClose(storeSCU);
                    ServiceUtil.shutdownService(executorService);
                    ServiceUtil.shutdownService(scheduledExecutorService);
                }
            }
        } catch (Exception e) {
            LOGGER.error("storescu", e);
            return new DicomState(Status.UnableToProcess,
                "DICOM Store failed" + StringUtil.COLON_AND_SPACE + e.getMessage(), null);
        }
    }

    private static void configureRelatedSOPClass(StoreSCU storescu, URL url) throws IOException {
        storescu.enableSOPClassRelationshipExtNeg(true);
        Properties p = new Properties();
        try {
            if (url == null) {
                p.load(storescu.getClass().getResourceAsStream("rel-sop-classes.properties"));
            } else {
                p.load(url.openStream());
            }
        } catch (Exception e) {
            LOGGER.error("Read sop classes", e);
        }
        storescu.relSOPClasses.init(p);
    }
}
