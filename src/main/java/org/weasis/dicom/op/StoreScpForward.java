package org.weasis.dicom.op;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.Executors;

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
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.DicomNode;

public class StoreScpForward {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreScpForward.class);

    private final Device device = new Device("storescp");
    private final ApplicationEntity ae = new ApplicationEntity("*");
    private final Connection conn = new Connection();

    private int priority;

    private final StoreFromStreamSCU streamSCU;
    private final AttributeEditor attributesEditor;

    private int status = 0;
    private final BasicCStoreSCP cstoreSCP = new BasicCStoreSCP("*") {

        @Override
        protected void store(Association as, PresentationContext pc, Attributes rq, PDVInputStream data, Attributes rsp)
            throws IOException {
            rsp.setInt(Tag.Status, VR.US, status);

            try {
                String cuid = rq.getString(Tag.AffectedSOPClassUID);
                String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
                String tsuid = pc.getTransferSyntax();
                // Add Presentation Context for the association
                streamSCU.addData(cuid, iuid, tsuid);

                if (streamSCU.getAssociation() == null) {
                    Device storeDevice = streamSCU.getDevice();
                    storeDevice.setExecutor(Executors.newSingleThreadExecutor());
                    storeDevice.setScheduledExecutor(Executors.newSingleThreadScheduledExecutor());
                    streamSCU.open();
                }
                else if (!streamSCU.getAssociation().isReadyForDataTransfer()) {
                    streamSCU.open();
                }
                
                if (streamSCU.getAssociation().isReadyForDataTransfer()) {
                    DicomInputStream in = null;
                    try {
                        in = new DicomInputStream(data);
                        in.setIncludeBulkData(IncludeBulkData.URI);
                        Attributes attributes = in.readDataset(-1, -1);
                        attributesEditor.apply(attributes);
                        DataWriter dataWriter = new DataWriterAdapter(attributes);

                        streamSCU.getAssociation().cstore(cuid, iuid, priority, dataWriter, tsuid,
                            streamSCU.rspHandlerFactory.createDimseRSPHandler());
                    } catch (Exception e) {
                        LOGGER.error("Error when forwarding to the final destination", e);
                    } finally {
                        FileUtil.safeClose(in);
                        // Force to clean if tmp bulk files
                        for (File file : in.getBulkDataFiles()) {
                            try {
                                file.delete();
                            } catch (Exception e) {
                                LOGGER.warn("Cannot delete: {}", file.getPath());
                            }

                        }
                    }
                }
            } catch (Exception e) {
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }
        }
    };

    public StoreScpForward(DicomNode callingNode, DicomNode destinationNode, AttributeEditor attributesEditor)
        throws IOException {
        this.attributesEditor = attributesEditor;
        device.setDimseRQHandler(createServiceRegistry());
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.setAssociationAcceptor(true);
        ae.addConnection(conn);

        streamSCU = new StoreFromStreamSCU(null, callingNode, destinationNode);
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

}
