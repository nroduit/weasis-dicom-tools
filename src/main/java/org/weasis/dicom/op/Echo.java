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
package org.weasis.dicom.op;

import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;

import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.data.UID;
import org.dcm4che6.net.AAssociate;
import org.dcm4che6.net.Association;
import org.dcm4che6.net.DicomServiceRegistry;
import org.dcm4che6.net.DimseRSP;
import org.dcm4che6.net.Status;
import org.dcm4che6.net.TCPConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;

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
     *            the optional advanced parameters (proxy, authentication, connection and TLS)
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
            DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
            TCPConnector<Association> inst =
                new TCPConnector<>((connector, role) -> new Association(connector, role, serviceRegistry));
            CompletableFuture<Void> task = CompletableFuture.runAsync(inst);
            AAssociate.RQ rq = new AAssociate.RQ();
            rq.setCallingAETitle(callingNode.getAet());
            rq.setCalledAETitle(calledNode.getAet());
            rq.putPresentationContext((byte) 1, UID.VerificationSOPClass, UID.ImplicitVRLittleEndian);
            Connection local = ServiceUtil.getConnection(callingNode);
            Connection remote = ServiceUtil.getConnection(calledNode);
            options.configureTLS(local, remote);
            long t1 = System.currentTimeMillis();
            Association as = inst.connect(local, remote).thenCompose(as1 -> as1.open(rq)).join();
            long t2 = System.currentTimeMillis();
            CompletableFuture<DimseRSP> echo = as.cecho();
            echo.join();
            DimseRSP resp = echo.get();
            as.release();
            long t3 = System.currentTimeMillis();
            as.onClose().join();
            task.cancel(true);
            String message = MessageFormat.format(
                "Successful DICOM Echo. Connected in {2}ms from {0} to {1}. Service execution in {3}ms.",
                rq.getCallingAETitle(), rq.getCalledAETitle(), t2 - t1, t3 - t2);
            return new DicomState(ServiceUtil.getStatus(resp), message, null); 
        } catch (Exception e) {
            String message = "DICOM Echo failed: " + e.getMessage();
            LOGGER.error(message, e);
            return new DicomState(Status.UnableToProcess, message, null);
        }
    }



}
