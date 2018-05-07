package org.weasis.dicom.util;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
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
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.util.ForwardUtil.Params;

public class StoreScpForward {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreScpForward.class);

    private final Device device = new Device("storescp");
    private final ApplicationEntity ae = new ApplicationEntity("*");
    private final Connection conn = new Connection();
    private volatile int priority;
    private volatile int status = 0;

    private final Map<DicomNode, List<ForwardDestination>> destinations;

    private final BasicCStoreSCP cstoreSCP = new BasicCStoreSCP("*") {

        @Override
        protected void store(Association as, PresentationContext pc, Attributes rq, PDVInputStream data, Attributes rsp)
            throws IOException {

            DicomNode sourceNode = DicomNode.buildRemoteDicomNode(as);
            boolean valid =
                destinations.keySet().stream()
                    .anyMatch(n -> n.getAet().equals(sourceNode.getAet())
                        && (!n.isValidateHostname() || n.equalsHostname(sourceNode.getHostname())));
            if (!valid) {
                rsp.setInt(Tag.Status, VR.US, Status.NotAuthorized);
                return;
            }

            rsp.setInt(Tag.Status, VR.US, status);

            try {
                Params p = new Params(rq.getString(Tag.AffectedSOPInstanceUID), rq.getString(Tag.AffectedSOPClassUID),
                    pc.getTransferSyntax(), priority, data, as);

                ForwardUtil.storeMulitpleDestination(sourceNode, destinations, p);

            } catch (Exception e) {
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }
        }
    };

    public StoreScpForward(DicomNode callingNode, DicomNode destinationNode) throws IOException {
        this(null, callingNode, callingNode.getAet(), destinationNode, null);
    }

    public StoreScpForward(AdvancedParams forwardParams, DicomNode callingNode, DicomNode destinationNode)
        throws IOException {
        this(forwardParams, callingNode, callingNode.getAet(), destinationNode, null);
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
    public StoreScpForward(AdvancedParams forwardParams, DicomNode callingNode, String forwardAETitle,
        DicomNode destinationNode, AttributeEditor attributesEditor) throws IOException {
        device.setDimseRQHandler(createServiceRegistry());
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.setAssociationAcceptor(true);
        ae.addConnection(conn);

        ForwardDestination uniqueDestination =
            new ForwardDestination(forwardParams, new DicomNode(forwardAETitle), destinationNode, attributesEditor);
        this.destinations = new HashMap<>();
        destinations.put(callingNode, Arrays.asList(uniqueDestination));
    }

    public StoreScpForward(Map<DicomNode, List<ForwardDestination>> destinations) throws IOException {
        device.setDimseRQHandler(createServiceRegistry());
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.setAssociationAcceptor(true);
        ae.addConnection(conn);
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
        destinations.values().forEach(l -> l.forEach(ForwardDestination::stop));
    }

}
