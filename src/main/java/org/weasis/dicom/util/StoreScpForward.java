package org.weasis.dicom.util;

import java.io.IOException;
import java.net.URL;
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
import org.weasis.dicom.util.ServiceUtil.ProgressStatus;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.ForwardDestination;

public class StoreScpForward {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreScpForward.class);

    private final Device device = new Device("storescp");
    private final ApplicationEntity ae = new ApplicationEntity("*");
    private final Connection conn = new Connection();
    private volatile int priority;
    private volatile int status = 0;

    private final Map<DicomNode, ForwardDestination> destinations;
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
            rsp.setInt(Tag.Status, VR.US, status);

            // TODO check inactivity of 30 sec and call stop()
            try {
                String cuid = rq.getString(Tag.AffectedSOPClassUID);
                String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
                String tsuid = pc.getTransferSyntax();

                DicomNode sourceNode = DicomNode.buildRemoteDicomNode(as);
                ForwardDestination destination = uniqueDestination;
                if (destination == null) {
                    destination = destinations.get(new DicomNode(sourceNode.getAet(), sourceNode.getHostname(), null));
                    if (destination == null) {
                        throw new IllegalStateException(
                            "Cannot find the DICOM destination from " + sourceNode.toString());
                    }
                }

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

            } catch (Exception e) {
                throw new DicomServiceException(Status.ProcessingFailure, e);
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

    public StoreScpForward(Map<DicomNode, ForwardDestination> destinations) throws IOException {
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
            destinations.values().forEach(ForwardDestination::stop);
        }
    }

}
