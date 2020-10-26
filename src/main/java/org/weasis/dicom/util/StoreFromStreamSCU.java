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

import java.io.IOException;
import java.util.Objects;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.net.AAssociate;
import org.dcm4che6.net.Association;
import org.dcm4che6.net.DicomServiceRegistry;
import org.dcm4che6.net.TCPConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.dicom.param.CstoreParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

public class StoreFromStreamSCU {

    private static Logger LOGGER = LoggerFactory.getLogger(StoreFromStreamSCU.class);

    private final AAssociate.RQ rq = new AAssociate.RQ();
    private Association as;
    private int numberOfSuboperations = 0;
    private DicomNode callingNode;
    private DicomNode calledNode;
    private final DicomState state;
    private final TCPConnector<Association> inst;
    private final AdvancedParams options;
    private final AtomicBoolean countdown = new AtomicBoolean(false);

    private final TimerTask closeAssociationTask = new TimerTask() {
        public void run() {
            close(false);
        }
    };
    private final ScheduledExecutorService closeAssociationExecutor = Executors.newSingleThreadScheduledExecutor();
    private volatile ScheduledFuture<?> scheduledFuture;

    public StoreFromStreamSCU(DicomNode callingNode, DicomNode calledNode) throws IOException {
        this(null, callingNode, calledNode, null, null);
    }

    public StoreFromStreamSCU(AdvancedParams params, DicomNode callingNode, DicomNode calledNode) throws IOException {
        this(params, callingNode, calledNode, null, null);
    }

    public StoreFromStreamSCU(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        DicomProgress progress, CstoreParams cstoreParams) throws IOException {
        this.callingNode = Objects.requireNonNull(callingNode);
        this.calledNode = Objects.requireNonNull(calledNode);

        this.options = params == null ? new AdvancedParams() : params;

        rq.setCallingAETitle(callingNode.getAet());
        rq.setCalledAETitle(calledNode.getAet());

        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        this.inst = new TCPConnector<>((connector, role) -> new Association(connector, role, serviceRegistry));
        CompletableFuture.runAsync(inst);
        this.state = new DicomState(progress);
    }

    public DicomNode getCallingNode() {
        return callingNode;
    }

    public DicomNode getCalledNode() {
        return calledNode;
    }

    public synchronized Association getAssociation() {
        return as;
    }

    public AAssociate.RQ getAssociationRq() {
        return rq;
    }

    public int getNumberOfSuboperations() {
        return numberOfSuboperations;
    }

    public void setNumberOfSuboperations(int numberOfSuboperations) {
        this.numberOfSuboperations = numberOfSuboperations;
    }

    public DicomState getState() {
        return state;
    }

    public AdvancedParams getOptions() {
        return options;
    }

    public synchronized void open() throws IOException {
        countdown.set(false);

        Connection local = ServiceUtil.getConnection(callingNode);
        Connection remote = ServiceUtil.getConnection(calledNode);
        options.configureTLS(local, remote);
        try {
            as = inst.connect(local, remote).thenCompose(as1 -> as1.open(rq)).get(3000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            as = null;
            LOGGER.trace("Connecting to remote destination", e);
        }
        if (as == null) {
            throw new IOException("Cannot connect to the remote destination");
        }
    }

    public synchronized void close(boolean force) {
        if (force || countdown.compareAndSet(true,false)) {
            if (as != null) {
                try {
                    LOGGER.info("Closing DICOM association");
                    as.release();
                    as.onClose().get(3000, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    LOGGER.trace("Cannot close association", e);
                    try {
                        as.onClose().get(5000, TimeUnit.MILLISECONDS);
                    } catch (Exception e2) {
                        LOGGER.trace("Cannot close association (second attempt)", e2);
                    }
                }
                as = null;
            }
        }
    }

    public void addData(String cuid, String tsuid) {
        rq.findOrAddPresentationContext(cuid, tsuid);
        countdown.set(false);
    }

    public synchronized void triggerCloseExecutor() {
        if ((scheduledFuture == null || scheduledFuture.isDone()) && countdown.compareAndSet(false,true)) {
            scheduledFuture = closeAssociationExecutor.schedule(closeAssociationTask, 15, TimeUnit.SECONDS);
        }
    }
}
