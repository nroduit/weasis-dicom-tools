package org.weasis.dicom.op;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.Map.Entry;
import java.util.Properties;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.DataWriter;
import org.dcm4che3.net.DataWriterAdapter;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.InputStreamDataWriter;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4che3.tool.common.CLIUtils;
import org.dcm4che3.tool.getscu.GetSCU;
import org.dcm4che3.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.DeviceOpService;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil;
import org.weasis.dicom.util.StoreFromStreamSCU;

public class CGetForward implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CGetForward.class);

    public enum InformationModel {
        PatientRoot(UID.PatientRootQueryRetrieveInformationModelGET, "STUDY"),
        StudyRoot(UID.StudyRootQueryRetrieveInformationModelGET, "STUDY"),
        PatientStudyOnly(UID.PatientStudyOnlyQueryRetrieveInformationModelGETRetired, "STUDY"),
        CompositeInstanceRoot(UID.CompositeInstanceRootRetrieveGET, "IMAGE"),
        WithoutBulkData(UID.CompositeInstanceRetrieveWithoutBulkDataGET, null),
        HangingProtocol(UID.HangingProtocolInformationModelGET, null),
        ColorPalette(UID.ColorPaletteQueryRetrieveInformationModelGET, null);

        final String cuid;
        final String level;

        InformationModel(String cuid, String level) {
            this.cuid = cuid;
            this.level = level;
        }
    }

    private final Device device = new Device("getscu");
    private final ApplicationEntity ae;
    private final Connection conn = new Connection();
    private final Connection remote = new Connection();
    private final AAssociateRQ rq = new AAssociateRQ();
    private int priority;
    private InformationModel model;

    private Attributes keys = new Attributes();
    private Association as;

    private final StoreFromStreamSCU streamSCU;
    private final DeviceOpService streamSCUService;
    private final AttributeEditor attributesEditor;

    private final BasicCStoreSCP storageSCP = new BasicCStoreSCP("*") {

        @Override
        protected void store(Association as, PresentationContext pc, Attributes rq, PDVInputStream data, Attributes rsp)
            throws IOException {

            DicomProgress p = streamSCU.getState().getProgress();
            if (p != null) {
                if (p.isCancel()) {
                    stop();
                    return;
                }
            }

            try {
                String cuid = rq.getString(Tag.AffectedSOPClassUID);
                String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
                String tsuid = pc.getTransferSyntax();
                // Add Presentation Context for the association
                streamSCU.addData(cuid, tsuid);

                if (streamSCU.getAssociation() == null) {
                    streamSCUService.start();
                    streamSCU.open();
                } else if (!streamSCU.getAssociation().isReadyForDataTransfer()) {
                    // If connection has been closed just reopen
                    streamSCU.open();
                }

                if (streamSCU.getAssociation().isReadyForDataTransfer()) {
                    DicomInputStream in = null;
                    try {
                        DataWriter dataWriter;
                        if (attributesEditor == null) {
                            dataWriter = new InputStreamDataWriter(data);
                        } else {
                            in = new DicomInputStream(data);
                            in.setIncludeBulkData(IncludeBulkData.URI);
                            Attributes attributes = in.readDataset(-1, -1);
                            if (attributesEditor.apply(attributes, tsuid, DicomNode.buildRemoteDicomNode(as),
                                DicomNode.buildRemoteDicomNode(streamSCU.getAssociation()))) {
                                iuid = attributes.getString(Tag.SOPInstanceUID);
                            }
                            dataWriter = new DataWriterAdapter(attributes);
                        }

                        streamSCU.getAssociation().cstore(cuid, iuid, priority, dataWriter, tsuid,
                            streamSCU.getRspHandlerFactory().createDimseRSPHandler());
                    } catch (Exception e) {
                        LOGGER.error("Error when forwarding to the final destination", e);
                    } finally {
                        FileUtil.safeClose(in);
                        // Force to clean if tmp bulk files
                        ServiceUtil.safeClose(in);
                    }
                }
            } catch (Exception e) {
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }
        }
    };

    public CGetForward(DicomNode callingNode, DicomNode destinationNode, DicomProgress progress) throws IOException {
        this(callingNode, destinationNode, progress, null);
    }

    public CGetForward(DicomNode callingNode, DicomNode destinationNode, DicomProgress progress,
        AttributeEditor attributesEditor) throws IOException {
        this(null, callingNode, destinationNode, progress, attributesEditor);
    }

    /**
     * @param forwardParams
     *            the optional advanced parameters (proxy, authentication, connection and TLS) for the final destination
     * @param callingNode
     *            the calling DICOM node configuration
     * @param destinationNode
     *            the final DICOM node configuration
     * @param progress
     *            the progress handler
     * @param attributesEditor
     *            the editor for modifying attributes on the fly (can be Null)
     * @throws IOException
     */
    public CGetForward(AdvancedParams forwardParams, DicomNode callingNode, DicomNode destinationNode,
        DicomProgress progress, AttributeEditor attributesEditor) throws IOException {
        this.attributesEditor = attributesEditor;
        this.ae = new ApplicationEntity("GETSCU");
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.addConnection(conn);
        device.setDimseRQHandler(createServiceRegistry());

        this.streamSCU = new StoreFromStreamSCU(forwardParams, callingNode, destinationNode, progress);
        this.streamSCUService = new DeviceOpService(streamSCU.getDevice());
    }

    public ApplicationEntity getApplicationEntity() {
        return ae;
    }

    public Connection getRemoteConnection() {
        return remote;
    }

    public AAssociateRQ getAAssociateRQ() {
        return rq;
    }

    public Association getAssociation() {
        return as;
    }

    public Device getDevice() {
        return device;
    }

    public Attributes getKeys() {
        return keys;
    }

    private DicomServiceRegistry createServiceRegistry() {
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        serviceRegistry.addDicomService(storageSCP);
        return serviceRegistry;
    }

    public final void setPriority(int priority) {
        this.priority = priority;
    }

    public final void setInformationModel(InformationModel model, String[] tss, boolean relational) {
        this.model = model;
        rq.addPresentationContext(new PresentationContext(1, model.cuid, tss));
        if (relational) {
            rq.addExtendedNegotiation(new ExtendedNegotiation(model.cuid, new byte[] { 1 }));
        }
        if (model.level != null) {
            addLevel(model.level);
        }
    }

    public void addLevel(String s) {
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, s);
    }

    public void addKey(int tag, String... ss) {
        VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
        keys.setString(tag, vr, ss);
    }

    public void addOfferedStorageSOPClass(String cuid, String... tsuids) {
        if (!rq.containsPresentationContextFor(cuid)) {
            rq.addRoleSelection(new RoleSelection(cuid, false, true));
        }
        rq.addPresentationContext(new PresentationContext(2 * rq.getNumberOfPresentationContexts() + 1, cuid, tsuids));
    }

    public void open()
        throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        as = ae.connect(conn, remote, rq);
    }

    @Override
    public void close() throws IOException, InterruptedException {
        if (as != null && as.isReadyForDataTransfer()) {
            as.waitForOutstandingRSP();
            as.release();
        }
        streamSCU.close();
    }

    public void retrieve() throws IOException, InterruptedException {
        retrieve(keys);
    }

    private void retrieve(Attributes keys) throws IOException, InterruptedException {
        DimseRSPHandler rspHandler = new DimseRSPHandler(as.nextMessageID()) {

            @Override
            public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                super.onDimseRSP(as, cmd, data);
                DicomProgress p = streamSCU.getState().getProgress();
                if (p != null) {
                    // Set only the initial state
                    if (streamSCU.getNumberOfSuboperations() == 0) {
                        streamSCU.setNumberOfSuboperations(ServiceUtil.getTotalOfSuboperations(cmd));
                    }
                    if (p.isCancel()) {
                        try {
                            this.cancel(as);
                        } catch (IOException e) {
                            LOGGER.error("Cancel C-GET", e);
                        }
                    }
                }
            }
        };

        retrieve(keys, rspHandler);
    }

    private void retrieve(Attributes keys, DimseRSPHandler rspHandler) throws IOException, InterruptedException {
        as.cget(model.cuid, priority, keys, null, rspHandler);
    }

    public Connection getConnection() {
        return conn;
    }

    public DeviceOpService getStreamSCUService() {
        return streamSCUService;
    }

    public StoreFromStreamSCU getStreamSCU() {
        return streamSCU;
    }

    public DicomState getState() {
        return streamSCU.getState();
    }

    public void stop() {
        try {
            close();
        } catch (Exception e) {
            // Do nothing
        }
    }

    /**
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param destinationNode
     *            the final destination DICOM node configuration
     * @param progress
     *            the progress handler
     * @param studyUID
     *            the study instance UID to retrieve
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState processStudy(DicomNode callingNode, DicomNode calledNode, DicomNode destinationNode,
        DicomProgress progress, String studyUID) {
        return process(null, null, callingNode, calledNode, destinationNode, progress, "STUDY", studyUID, null);
    }

    /**
     * @param getParams
     *            the C-GET optional advanced parameters (proxy, authentication, connection and TLS)
     * @param forwardParams
     *            the C-Store optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param destinationNode
     *            the final destination DICOM node configuration
     * @param progress
     *            the progress handler
     * @param studyUID
     *            the study instance UID to retrieve
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState processStudy(AdvancedParams getParams, AdvancedParams forwardParams, DicomNode callingNode,
        DicomNode calledNode, DicomNode destinationNode, DicomProgress progress, String studyUID) {
        return process(getParams, forwardParams, callingNode, calledNode, destinationNode, progress, "STUDY", studyUID,
            null);
    }

    /**
     * @param getParams
     *            the C-GET optional advanced parameters (proxy, authentication, connection and TLS)
     * @param forwardParams
     *            the C-Store optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param destinationNode
     *            the final destination DICOM node configuration
     * @param progress
     *            the progress handler
     * @param studyUID
     *            the study instance UID to retrieve
     * @param attributesEditor
     *            the editor for modifying attributes on the fly. IT can be null.
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState processStudy(AdvancedParams getParams, AdvancedParams forwardParams, DicomNode callingNode,
        DicomNode calledNode, DicomNode destinationNode, DicomProgress progress, String studyUID,
        AttributeEditor attributesEditor) {
        return process(getParams, forwardParams, callingNode, calledNode, destinationNode, progress, "STUDY", studyUID,
            attributesEditor);
    }

    /**
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param destinationNode
     *            the final destination DICOM node configuration
     * @param progress
     *            the progress handler
     * @param seriesUID
     *            the series instance UID to retrieve
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState processSeries(DicomNode callingNode, DicomNode calledNode, DicomNode destinationNode,
        DicomProgress progress, String seriesUID) {
        return process(null, null, callingNode, calledNode, destinationNode, progress, "SERIES", seriesUID, null);
    }

    /**
     * @param getParams
     *            the C-GET optional advanced parameters (proxy, authentication, connection and TLS)
     * @param forwardParams
     *            the C-Store optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param destinationNode
     *            the final destination DICOM node configuration
     * @param progress
     *            the progress handler
     * @param seriesUID
     *            the series instance UID to retrieve
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState processSeries(AdvancedParams getParams, AdvancedParams forwardParams,
        DicomNode callingNode, DicomNode calledNode, DicomNode destinationNode, DicomProgress progress,
        String seriesUID) {
        return process(getParams, forwardParams, callingNode, calledNode, destinationNode, progress, "SERIES",
            seriesUID, null);
    }

    /**
     * @param getParams
     *            the C-GET optional advanced parameters (proxy, authentication, connection and TLS)
     * @param forwardParams
     *            the C-Store optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param destinationNode
     *            the final destination DICOM node configuration
     * @param progress
     *            the progress handler
     * @param seriesUID
     *            the series instance UID to retrieve
     * @param attributesEditor
     *            the editor for modifying attributes on the fly (can be Null)
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState processSeries(AdvancedParams getParams, AdvancedParams forwardParams,
        DicomNode callingNode, DicomNode calledNode, DicomNode destinationNode, DicomProgress progress,
        String seriesUID, AttributeEditor attributesEditor) {
        return process(getParams, forwardParams, callingNode, calledNode, destinationNode, progress, "SERIES",
            seriesUID, attributesEditor);
    }

    private static DicomState process(AdvancedParams getParams, AdvancedParams forwardParams, DicomNode callingNode,
        DicomNode calledNode, DicomNode destinationNode, DicomProgress progress, String queryRetrieveLevel,
        String queryUID, AttributeEditor attributesEditor) {
        if (callingNode == null || calledNode == null || destinationNode == null) {
            throw new IllegalArgumentException("callingNode, calledNode or destinationNode cannot be null!");
        }
        CGetForward forward = null;
        AdvancedParams options = getParams == null ? new AdvancedParams() : getParams;

        try {
            forward = new CGetForward(forwardParams, callingNode, destinationNode, progress, attributesEditor);
            Connection remote = forward.getRemoteConnection();
            Connection conn = forward.getConnection();
            options.configureConnect(forward.getAAssociateRQ(), remote, calledNode);
            options.configureBind(forward.getApplicationEntity(), conn, callingNode);
            DeviceOpService service = new DeviceOpService(forward.getDevice());

            // configure
            options.configure(conn);
            options.configureTLS(conn, remote);

            forward.setPriority(options.getPriority());

            forward.setInformationModel(getInformationModel(options), options.getTsuidOrder(),
                options.getQueryOptions().contains(QueryOption.RELATIONAL));

            configureRelatedSOPClass(forward, null);

            if ("SERIES".equals(queryRetrieveLevel)) {
                forward.addKey(Tag.QueryRetrieveLevel, "SERIES");
                forward.addKey(Tag.SeriesInstanceUID, queryUID);
            } else if ("STUDY".equals(queryRetrieveLevel)) {
                forward.addKey(Tag.QueryRetrieveLevel, "STUDY");
                forward.addKey(Tag.StudyInstanceUID, queryUID);
            } else {
                throw new IllegalArgumentException(queryRetrieveLevel + " is not supported as query retrieve level!");
            }

            service.start();
            try {
                DicomState dcmState = forward.getState();
                long t1 = System.currentTimeMillis();
                forward.open();
                forward.retrieve();
                long t2 = System.currentTimeMillis();
                String timeMsg = MessageFormat.format("Get files from {0} to {1} in {2}ms",
                    forward.getAAssociateRQ().getCallingAET(), forward.getAAssociateRQ().getCalledAET(), t2 - t1);
                ServiceUtil.forceGettingAttributes(dcmState, forward);
                return DicomState.buildMessage(dcmState, timeMsg, null);
            } catch (Exception e) {
                LOGGER.error("getscu", e);
                ServiceUtil.forceGettingAttributes(forward.getState(), forward);
                return DicomState.buildMessage(forward.getState(), null, e);
            } finally {
                FileUtil.safeClose(forward);
                service.stop();
                forward.getStreamSCUService().stop();
            }
        } catch (Exception e) {
            LOGGER.error("getscu", e);
            return new DicomState(Status.UnableToProcess,
                "DICOM Get failed" + StringUtil.COLON_AND_SPACE + e.getMessage(), null);
        }
    }

    private static void configureRelatedSOPClass(CGetForward getSCU, URL url) throws IOException {
        Properties p = new Properties();
        try {
            if (url == null) {
                p.load(GetSCU.class.getResourceAsStream("store-tcs.properties"));
            } else {
                p.load(url.openStream());
            }
            for (Entry<Object, Object> entry : p.entrySet()) {
                configureStorageSOPClass(getSCU, (String) entry.getKey(), (String) entry.getValue());
            }
        } catch (Exception e) {
            LOGGER.error("Read sop classes", e);
        }
    }

    private static void configureStorageSOPClass(CGetForward getSCU, String cuid, String tsuids) {
        String[] ts = StringUtils.split(tsuids, ';');
        for (int i = 0; i < ts.length; i++) {
            ts[i] = CLIUtils.toUID(ts[i]);
        }
        getSCU.addOfferedStorageSOPClass(CLIUtils.toUID(cuid), ts);
    }

    private static InformationModel getInformationModel(AdvancedParams options) {
        Object model = options.getInformationModel();
        if (model instanceof InformationModel) {
            return (InformationModel) model;
        }
        return InformationModel.StudyRoot;
    }
}
