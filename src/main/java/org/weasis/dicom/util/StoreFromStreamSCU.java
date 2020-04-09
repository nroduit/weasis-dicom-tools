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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

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

    private int lastStatusCode = Integer.MIN_VALUE;
    private int nbStatusLog = 0;
    private int numberOfSuboperations = 0;
    private final DicomState state;

    private DicomNode callingNode;

    private DicomNode calledNode;

    private CompletableFuture<Void> task;

    private final AdvancedParams options;

    private final AttributeEditorContext context;

    private final AtomicBoolean countdown = new AtomicBoolean(false);

    // private final RSPHandlerFactory rspHandlerFactory = () -> new DimseRSPHandler(as.nextMessageID()) {
    //
    // @Override
    // public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
    // super.onDimseRSP(as, cmd, data);
    // onCStoreRSP(cmd);
    //
    // DicomProgress progress = state.getProgress();
    // if (progress != null) {
    // progress.setAttributes(cmd);
    // }
    // }
    //
    // /**
    // * @see <a
    // * href="ERROR CODE
    // STATUS">https://github.com/dcm4che/dcm4che/blob/master/dcm4che-net/src/main/java/org/dcm4che3/net/Status.java</a>
    // */
    // private void onCStoreRSP(Attributes cmd) {
    // int status = cmd.getInt(Tag.Status, -1);
    // state.setStatus(status);
    // ProgressStatus ps;
    //
    // switch (status) {
    // case Status.Success:
    // ps = ProgressStatus.COMPLETED;
    // break;
    // case Status.CoercionOfDataElements:
    // case Status.ElementsDiscarded:
    //
    // case Status.DataSetDoesNotMatchSOPClassWarning:
    // ps = ProgressStatus.WARNING;
    // if (lastStatusCode != status && nbStatusLog < 3) {
    // nbStatusLog++;
    // lastStatusCode = status;
    // if (LOGGER.isDebugEnabled()) {
    // LOGGER.warn("Received C-STORE-RSP with Status {}H{}", TagUtils.shortToHexString(status),
    // "\r\n" + cmd.toString());
    // } else {
    // LOGGER.warn("Received C-STORE-RSP with Status {}H", TagUtils.shortToHexString(status));
    // }
    // }
    // break;
    //
    // default:
    // ps = ProgressStatus.FAILED;
    // if (lastStatusCode != status && nbStatusLog < 3) {
    // nbStatusLog++;
    // lastStatusCode = status;
    // if (LOGGER.isDebugEnabled()) {
    // LOGGER.error("Received C-STORE-RSP with Status {}H{}", TagUtils.shortToHexString(status),
    // "\r\n" + cmd.toString());
    // } else {
    // LOGGER.error("Received C-STORE-RSP with Status {}H", TagUtils.shortToHexString(status));
    // }
    // }
    // }
    // ServiceUtil.notifyProgession(state.getProgress(), cmd, ps, numberOfSuboperations);
    // }
    // };

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
        this.context = new AttributeEditorContext(callingNode, calledNode);

        rq.setCallingAETitle(callingNode.getAet());
        rq.setCalledAETitle(calledNode.getAet());

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

    public AttributeEditorContext getContext() {
        return context;
    }

    public synchronized void open() throws IOException {
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        TCPConnector<Association> inst =
            new TCPConnector<>((connector, role) -> new Association(connector, role, serviceRegistry));
        this.task = CompletableFuture.runAsync(inst);
        Connection local = ServiceUtil.getConnection(callingNode);
        Connection remote = ServiceUtil.getConnection(calledNode);
        options.configureTLS(local, remote);
        try {
            as = inst.connect(local, remote).thenCompose(as1 -> as1.open(rq)).get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            as = null;
            LOGGER.trace("Connecting to remote destination", e);
        }
        if (as == null) {
            throw new IOException("Cannot connect to the remote destination");
        }
    }

    public synchronized void close() {
        if (countdown.get()) {
            if (as != null) {
                try {
                    as.release();
                    as.onClose().get(3000, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                as = null;
            }
            task.cancel(true);
        }
    }

    public void addData(String cuid, String tsuid) {
        rq.findOrAddPresentationContext(cuid, tsuid);
        countdown.set(false);
    }

    public void triggerCloseExecutor() {
        countdown.set(true);
        CompletableFuture.delayedExecutor(5000, TimeUnit.MILLISECONDS).execute(this::close);
    }
}
