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
package org.weasis.dicom.tool;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.dcm4che6.conf.model.ApplicationEntity;
import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.conf.model.Device;
import org.dcm4che6.conf.model.TransferCapability;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.net.Association;
import org.dcm4che6.net.DicomServiceException;
import org.dcm4che6.net.DicomServiceRegistry;
import org.dcm4che6.net.Dimse;
import org.dcm4che6.net.DimseHandler;
import org.dcm4che6.net.Status;
import org.dcm4che6.net.TCPConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.param.ForwardDicomNode;
import org.weasis.dicom.param.GatewayParams;
import org.weasis.dicom.util.ForwardUtil;
import org.weasis.dicom.util.ForwardUtil.Params;
import org.weasis.dicom.util.ServiceUtil;

public class DicomGateway implements DimseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomGateway.class);

    private final Map<ForwardDicomNode, List<ForwardDestination>> destinations;

    private final DicomServiceRegistry serviceRegistry;
    private TCPConnector<Association> tcp;
    private CompletableFuture<Void> task;

    /**
     * Build a DICOM Gateway with the mapping between input and output streams
     * 
     * @param destinations
     *            the mapping between a ForwardDicomNode and a list of ForwardDestination
     * 
     * 
     * @throws IOException
     */
    public DicomGateway(Map<ForwardDicomNode, List<ForwardDestination>> destinations) throws IOException {
        this.destinations = Objects.requireNonNull(destinations);
        this.serviceRegistry = new DicomServiceRegistry().setDefaultRQHandler(this);
    }

    public void start(DicomNode scpNode) throws Exception {
        start(scpNode, new GatewayParams(false));
    }

    public synchronized void start(DicomNode scpNode, GatewayParams params) throws Exception {
        stop();

        AdvancedParams options = Objects.requireNonNull(params).getParams();

        Connection local = ServiceUtil.getConnection(scpNode);
        ApplicationEntity ae = new ApplicationEntity().addConnection(local);
        // configure
        if (params.isBindCallingAet()) {
            options.configureBind(ae, local, scpNode);
        } else {
            ae.setAETitle("*");
            options.configureBind(local, scpNode);
        }
        options.configure(local);
        options.configureTLS(local, null);

        URL transferCapabilityFile = params.getTransferCapabilityFile();
        if (transferCapabilityFile != null) {
            DicomListener.loadDefaultTransferCapability(ae, transferCapabilityFile);
        } else {
            ae.addTransferCapability(new TransferCapability().setSOPClass("*").setTransferSyntaxes("*")
                .setRole(TransferCapability.Role.SCP));
        }
        // Limit the calling AETs
        // storeSCP.getApplicationEntity().setAcceptedCallingAETitles(params.getAcceptedCallingAETitles());

        new Device().addApplicationEntity(ae);
        tcp = new TCPConnector<>((connector, role) -> new Association(connector, role, serviceRegistry));
        task = CompletableFuture.runAsync(tcp);
        tcp.bind(local);
    }

    public synchronized void stop() throws IOException {
        if (task != null) {
            task.cancel(true);
            destinations.values().forEach(l -> l.forEach(ForwardDestination::stop));
        }
        if (tcp != null && tcp.getSelector().isOpen()) {
            tcp.getSelector().keys().forEach(k -> {
                try {
                    k.channel().close();
                } catch (IOException e) {
                    LOGGER.error("Closing socket channel", e);
                }
                k.cancel();
            });
            tcp.getSelector().close();
        }
    }

    @Override
    public void accept(Association as, Byte pcid, Dimse dimse, DicomObject commandSet, InputStream dataStream)
        throws IOException {
        if (dimse != Dimse.C_STORE_RQ) {
            throw new IOException("Unsupported DIMSE: " + dimse);
        }

        try {
            String calledAet = as.getAarq().getCalledAETitle();
            Optional<ForwardDicomNode> sourceNode =
                destinations.keySet().stream().filter(n -> n.getForwardAETitle().equals(calledAet)).findFirst();
            if (!sourceNode.isPresent()) {
                LOGGER.warn("Cannot find the forward AeTitle {}", calledAet);
                return;
            }
            ForwardDicomNode fwdNode = sourceNode.get();
            List<ForwardDestination> destList = destinations.get(fwdNode);
            if (destList == null || destList.isEmpty()) {
                LOGGER.warn("No destination configured for {}", fwdNode);
                return;
            }

            DicomNode callingNode = DicomNode.buildRemoteDicomNode(as);
            Set<DicomNode> srcNodes = fwdNode.getAcceptedSourceNodes();
            boolean valid =
                srcNodes.isEmpty() || srcNodes.stream().anyMatch(n -> n.getAet().equals(callingNode.getAet())
                    && (!n.isValidateHostname() || n.equalsHostname(callingNode.getHostname())));

            String iuid = commandSet.getStringOrElseThrow(Tag.AffectedSOPInstanceUID);
            if (!valid) {
                // rsp.setInt(Tag.Status, VR.US, Status.NotAuthorized);
                LOGGER.error("Refused: not authorized (124H). Source node: {}. SopUID: {}", callingNode, iuid);
                return;
            }

            Params p = new Params(iuid, commandSet.getStringOrElseThrow(Tag.AffectedSOPClassUID), pcid, dataStream, as);
            ForwardUtil.storeMulitpleDestination(fwdNode, destList, p);
        } catch (IOException e) {
            throw e;
        } finally {
            if(dataStream != null){
                while(true) {
                    try {
                        if ((dataStream.read() < 0))
                            break;
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            }
            as.writeDimse(pcid, Dimse.C_STORE_RSP, dimse.mkRSP(commandSet));
        }
    }
}
