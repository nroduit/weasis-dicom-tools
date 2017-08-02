package org.weasis.dicom.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.DataWriter;
import org.dcm4che3.net.DataWriterAdapter;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.InputStreamDataWriter;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4che3.tool.common.CLIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.dicom.param.AttributeEditorContext.Abort;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.util.ServiceUtil.ProgressStatus;

public class StoreScpForward {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreScpForward.class);

    private final Device device = new Device("storescp");
    private final ApplicationEntity ae = new ApplicationEntity("*");
    private final Connection conn = new Connection();
    private volatile int priority;
    private volatile int status = 0;

    private final Map<DicomNode, List<ForwardDestination>> destinations;
    private final ForwardDestination uniqueDestination;

    private final BasicCStoreSCP cstoreSCP = new BasicCStoreSCP("*") {
        class AbortException extends IllegalStateException {
            private static final long serialVersionUID = 3993065212756372490L;

            public AbortException(String s) {
                super(s);
            }
        }

        @Override
        protected void store(Association as, PresentationContext pc, Attributes rq, PDVInputStream data, Attributes rsp)
            throws IOException {

            DicomNode sourceNode = DicomNode.buildRemoteDicomNode(as);
            boolean valid =
                destinations.keySet().stream()
                    .filter(n -> n.getAet().equals(sourceNode.getAet())
                        && (!n.isValidateHostname() || n.equalsHostname(sourceNode.getHostname())))
                    .findFirst().isPresent();
            if (!valid) {
                rsp.setInt(Tag.Status, VR.US, Status.NotAuthorized);
                return;
            }

            rsp.setInt(Tag.Status, VR.US, status);

            try {
                String cuid = rq.getString(Tag.AffectedSOPClassUID);
                String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
                String tsuid = pc.getTransferSyntax();

                ForwardDestination destination = uniqueDestination;
                if (destination == null) {
                    storeMulitpleDestination(sourceNode, iuid, cuid, tsuid, as, data);
                } else {
                    storeOneDestination(sourceNode, destination, iuid, cuid, tsuid, as, data);
                }

            } catch (Exception e) {
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }
        }

        private void storeMulitpleDestination(DicomNode sourceNode, String iuid, String cuid, String tsuid,
            Association as, PDVInputStream data) throws Exception {
            List<ForwardDestination> destList =
                destinations.get(new DicomNode(sourceNode.getAet(), sourceNode.getHostname(), null));
            if (destList == null || destList.isEmpty()) {
                throw new IllegalStateException("Cannot find the DICOM destination from " + sourceNode.toString());
            }

            if (destList.size() == 1) {
                storeOneDestination(sourceNode, destList.get(0), iuid, cuid, tsuid, as, data);
            } else {
                List<ForwardDestination> destConList = new ArrayList<>();
                for (ForwardDestination fwDest : destList) {
                    try {
                        prepareTransfer(fwDest, cuid, tsuid);
                        destConList.add(fwDest);
                    } catch (Exception e) {
                        LOGGER.error("Cannot connect to the final destination", e);
                    }
                }

                if (destConList.isEmpty()) {
                    return;
                } else if (destConList.size() == 1) {
                    storeOneDestination(sourceNode, destConList.get(0), iuid, cuid, tsuid, as, data);
                } else {
                    List<File> files = null;
                    try {
                        Attributes attributes = new Attributes();
                        files = transferFirst(sourceNode, destConList.get(0), attributes, iuid, cuid, tsuid, as, data);
                        if (!attributes.isEmpty()) {
                            for (int i = 1; i < destConList.size(); i++) {
                                transferOther(sourceNode, destConList.get(i), attributes, iuid, cuid, tsuid, as, data);
                            }
                        }
                    } finally {
                        if (files != null) {
                            // Force to clean if tmp bulk files
                            for (File file : files) {
                                FileUtil.delete(file);
                            }
                        }
                    }
                }
            }
        }

        private void storeOneDestination(DicomNode sourceNode, ForwardDestination destination, String iuid, String cuid,
            String tsuid, Association as, PDVInputStream data) throws Exception {
            StoreFromStreamSCU streamSCU = prepareTransfer(destination, cuid, tsuid);
            if (streamSCU != null) {
                transfer(sourceNode, destination, iuid, cuid, tsuid, as, data);
            }
        }

        private StoreFromStreamSCU prepareTransfer(ForwardDestination destination, String cuid, String tsuid)
            throws Exception {
            StoreFromStreamSCU streamSCU = destination.getStreamSCU();
            if (streamSCU.getAssociation() == null) {
                destination.getStreamSCUService().start();
                // Add Presentation Context for the association
                streamSCU.addData(cuid, tsuid);
                streamSCU.open();
            } else {
                // Handle dynamically new SOPClassUID
                Set<String> tss = streamSCU.getAssociation().getTransferSyntaxesFor(cuid);
                if (!tss.contains(tsuid)) {
                    streamSCU.close();
                }

                // Add Presentation Context for the association
                streamSCU.addData(cuid, tsuid);

                if (!streamSCU.getAssociation().isReadyForDataTransfer()) {
                    // If connection has been closed just reopen
                    streamSCU.open();
                }
            }
            return streamSCU;
        }

        private void transfer(DicomNode sourceNode, ForwardDestination destination, String iuid, String cuid,
            String tsuid, Association as, PDVInputStream data) {
            StoreFromStreamSCU streamSCU = destination.getStreamSCU();
            DicomInputStream in = null;
            try {
                if (!streamSCU.getAssociation().isReadyForDataTransfer()) {
                    throw new IllegalStateException("Association not ready for transfer.");
                }
                DataWriter dataWriter;
                if (destination.getAttributesEditor() == null) {
                    dataWriter = new InputStreamDataWriter(data);
                } else {
                    AttributeEditorContext context = new AttributeEditorContext(tsuid, sourceNode,
                        DicomNode.buildRemoteDicomNode(streamSCU.getAssociation()));
                    in = new DicomInputStream(data);
                    in.setIncludeBulkData(IncludeBulkData.URI);
                    Attributes attributes = in.readDataset(-1, Tag.PixelData);
                    if (destination.getAttributesEditor().apply(attributes, context)) {
                        iuid = attributes.getString(Tag.SOPInstanceUID);
                    }

                    if (context.getAbort() == Abort.FILE_EXCEPTION) {
                        data.skipAll();
                        throw new IllegalStateException(context.getAbortMessage());
                    } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                        as.abort();
                        throw new AbortException("DICOM associtation abort. " + context.getAbortMessage());
                    }
                    if (in.tag() == Tag.PixelData) {
                        in.readValue(in, attributes);
                        in.readAttributes(attributes, -1, -1);
                    }
                    dataWriter = new DataWriterAdapter(attributes);
                }

                streamSCU.getAssociation().cstore(cuid, iuid, priority, dataWriter, tsuid,
                    streamSCU.getRspHandlerFactory().createDimseRSPHandler());
            } catch (AbortException e) {
                ServiceUtil.notifyProgession(streamSCU.getState(), Status.ProcessingFailure, ProgressStatus.FAILED,
                    streamSCU.getNumberOfSuboperations());
                throw e;
            } catch (Exception e) {
                LOGGER.error("Error when forwarding to the final destination", e);
                ServiceUtil.notifyProgession(streamSCU.getState(), Status.ProcessingFailure, ProgressStatus.FAILED,
                    streamSCU.getNumberOfSuboperations());
            } finally {
                FileUtil.safeClose(in);
                // Force to clean if tmp bulk files
                ServiceUtil.safeClose(in);
            }
        }

        private List<File> transferFirst(DicomNode sourceNode, ForwardDestination destination, Attributes copy,
            String iuid, String cuid, String tsuid, Association as, PDVInputStream data) {
            StoreFromStreamSCU streamSCU = destination.getStreamSCU();
            DicomInputStream in = null;
            List<File> files = null;
            try {
                if (!streamSCU.getAssociation().isReadyForDataTransfer()) {
                    throw new IllegalStateException("Association not ready for transfer.");
                }

                AttributeEditorContext context = new AttributeEditorContext(tsuid, sourceNode,
                    DicomNode.buildRemoteDicomNode(streamSCU.getAssociation()));
                in = new DicomInputStream(data);
                in.setIncludeBulkData(IncludeBulkData.URI);
                Attributes attributes = in.readDataset(-1, Tag.PixelData);
                if (destination.getAttributesEditor() != null
                    && destination.getAttributesEditor().apply(attributes, context)) {
                    iuid = attributes.getString(Tag.SOPInstanceUID);
                }

                if (context.getAbort() == Abort.FILE_EXCEPTION) {
                    data.skipAll();
                    throw new IllegalStateException(context.getAbortMessage());
                } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                    // Attention, this will also abort the transfer to the next destinations.
                    as.abort();
                    throw new AbortException("DICOM associtation abort. " + context.getAbortMessage());
                }
                if (in.tag() == Tag.PixelData) {
                    in.readValue(in, attributes);
                    in.readAttributes(attributes, -1, -1);
                }
                copy.addAll(attributes);
                DataWriter dataWriter = new DataWriterAdapter(attributes);

                streamSCU.getAssociation().cstore(cuid, iuid, priority, dataWriter, tsuid,
                    streamSCU.getRspHandlerFactory().createDimseRSPHandler());
            } catch (AbortException e) {
                ServiceUtil.notifyProgession(streamSCU.getState(), Status.ProcessingFailure, ProgressStatus.FAILED,
                    streamSCU.getNumberOfSuboperations());
                throw e;
            } catch (Exception e) {
                LOGGER.error("Error when forwarding to the final destination", e);
                ServiceUtil.notifyProgession(streamSCU.getState(), Status.ProcessingFailure, ProgressStatus.FAILED,
                    streamSCU.getNumberOfSuboperations());
            } finally {
                FileUtil.safeClose(in);
                // Return tmp bulk files
                if (in != null) {
                    files = in.getBulkDataFiles();
                }
            }
            return files;
        }

        private void transferOther(DicomNode sourceNode, ForwardDestination destination, Attributes copy, String iuid,
            String cuid, String tsuid, Association as, PDVInputStream data) {
            StoreFromStreamSCU streamSCU = destination.getStreamSCU();

            try {
                if (!streamSCU.getAssociation().isReadyForDataTransfer()) {
                    throw new IllegalStateException("Association not ready for transfer.");
                }
                DataWriter dataWriter;
                if (destination.getAttributesEditor() == null) {
                    dataWriter = new DataWriterAdapter(copy);
                } else {
                    AttributeEditorContext context = new AttributeEditorContext(tsuid, sourceNode,
                        DicomNode.buildRemoteDicomNode(streamSCU.getAssociation()));
                    Attributes attributes = new Attributes(copy);
                    if (destination.getAttributesEditor().apply(attributes, context)) {
                        iuid = attributes.getString(Tag.SOPInstanceUID);
                    }

                    if (context.getAbort() == Abort.FILE_EXCEPTION) {
                        data.skipAll();
                        throw new IllegalStateException(context.getAbortMessage());
                    } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                        // Attention, this will also abort the transfer to the next destinations.
                        as.abort();
                        throw new AbortException("DICOM associtation abort. " + context.getAbortMessage());
                    }
                    dataWriter = new DataWriterAdapter(attributes);
                }

                streamSCU.getAssociation().cstore(cuid, iuid, priority, dataWriter, tsuid,
                    streamSCU.getRspHandlerFactory().createDimseRSPHandler());
            } catch (AbortException e) {
                ServiceUtil.notifyProgession(streamSCU.getState(), Status.ProcessingFailure, ProgressStatus.FAILED,
                    streamSCU.getNumberOfSuboperations());
                throw e;
            } catch (Exception e) {
                LOGGER.error("Error when forwarding to the final destination", e);
                ServiceUtil.notifyProgession(streamSCU.getState(), Status.ProcessingFailure, ProgressStatus.FAILED,
                    streamSCU.getNumberOfSuboperations());
            }
        }
    };

    public StoreScpForward(DicomNode callingNode, DicomNode destinationNode) throws IOException {
        this(null, callingNode, destinationNode, null);
    }

    public StoreScpForward(AdvancedParams forwardParams, DicomNode callingNode, DicomNode destinationNode)
        throws IOException {
        this(forwardParams, callingNode, destinationNode, null);
    }

    /**
     * @param forwardParams
     *            the optional advanced parameters (proxy, authentication, connection and TLS) for the final destination
     * @param callingNode
     *            the calling DICOM node configuration
     * @param destinationNode
     *            the final DICOM node configuration
     * @param attributesEditor
     *            the editor for modifying attributes on the fly (can be Null)
     * @throws IOException
     */
    public StoreScpForward(AdvancedParams forwardParams, DicomNode callingNode, DicomNode destinationNode,
        AttributeEditor attributesEditor) throws IOException {
        device.setDimseRQHandler(createServiceRegistry());
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.setAssociationAcceptor(true);
        ae.addConnection(conn);

        this.uniqueDestination = new ForwardDestination(forwardParams, callingNode, destinationNode, attributesEditor);
        this.destinations = null;
    }

    public StoreScpForward(Map<DicomNode, List<ForwardDestination>> destinations) throws IOException {
        device.setDimseRQHandler(createServiceRegistry());
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.setAssociationAcceptor(true);
        ae.addConnection(conn);

        this.uniqueDestination = null;
        this.destinations = Objects.requireNonNull(destinations);
    }

    public final void setPriority(int priority) {
        this.priority = priority;
    }

    private DicomServiceRegistry createServiceRegistry() {
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        serviceRegistry.addDicomService(new BasicCEchoSCP());
        serviceRegistry.addDicomService(cstoreSCP);
        return serviceRegistry;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void loadDefaultTransferCapability(URL transferCapabilityFile) {
        Properties p = new Properties();

        try {
            if (transferCapabilityFile != null) {
                p.load(transferCapabilityFile.openStream());
            } else {
                p.load(this.getClass().getResourceAsStream("sop-classes.properties"));
            }
        } catch (IOException e) {
            LOGGER.error("Cannot read sop-classes", e);
        }

        for (String cuid : p.stringPropertyNames()) {
            String ts = p.getProperty(cuid);
            TransferCapability tc =
                new TransferCapability(null, CLIUtils.toUID(cuid), TransferCapability.Role.SCP, CLIUtils.toUIDs(ts));
            ae.addTransferCapability(tc);
        }
    }

    public ApplicationEntity getApplicationEntity() {
        return ae;
    }

    public Connection getConnection() {
        return conn;
    }

    public Device getDevice() {
        return device;
    }

    public void stop() {
        if (uniqueDestination != null) {
            uniqueDestination.stop();
        } else {
            destinations.values().forEach(l -> l.forEach(ForwardDestination::stop));
        }
    }

}
