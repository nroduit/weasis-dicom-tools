package org.weasis.dicom.op;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
import org.dcm4che3.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

public class CGetForward {

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

    private final DicomState state;

    private final StoreFromStreamSCU streamSCU;
    private final Map<Integer, String> attributesToOverride;

    private final BasicCStoreSCP storageSCP = new BasicCStoreSCP("*") {

        @Override
        protected void store(Association as, PresentationContext pc, Attributes rq, PDVInputStream data, Attributes rsp)
            throws IOException {

            DicomProgress p = state.getProgress();
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
                streamSCU.addData(cuid, iuid, tsuid);

                if (streamSCU.getAssociation() == null) {
                    Device storeDevice = streamSCU.getDevice();
                    storeDevice.setExecutor(Executors.newSingleThreadExecutor());
                    storeDevice.setScheduledExecutor(Executors.newSingleThreadScheduledExecutor());
                    streamSCU.open();
                }

                if (streamSCU.getAssociation().isReadyForDataTransfer()) {
                    DicomInputStream in = null;
                    try {
                        DataWriter dataWriter;
                        if (attributesToOverride == null || attributesToOverride.isEmpty()) {
                            dataWriter = new InputStreamDataWriter(data);
                        } else {
                            in = new DicomInputStream(data);
                            in.setIncludeBulkData(IncludeBulkData.URI);
                            Attributes attributes = in.readDataset(-1, -1);
                            for (Entry<Integer, String> entry : attributesToOverride.entrySet()) {
                                int tag = entry.getKey();
                                VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                                attributes.setString(tag, vr, entry.getValue());
                            }
                            dataWriter = new DataWriterAdapter(attributes);
                        }

                        streamSCU.getAssociation().cstore(cuid, iuid, priority, dataWriter, tsuid,
                            streamSCU.rspHandlerFactory.createDimseRSPHandler());
                        notify(true);
                    } catch (Exception e) {
                        LOGGER.error("Error when forwarding to the final destination", e);
                        notify(false);
                    } finally {
                        FileUtil.safeClose(in);
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
                notify(false);
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }
        }

        private void notify(boolean success) {
            DicomProgress p = state.getProgress();
            if (p != null) {
                Attributes cmd = p.getAttributes();
                if (cmd != null) {
                    int c = p.getNumberOfCompletedSuboperations();
                    int f = p.getNumberOfFailedSuboperations();
                    int r = p.getNumberOfRemainingSuboperations();

                    if (success) {
                        cmd.setInt(Tag.NumberOfCompletedSuboperations, VR.US, c + 1);
                    } else {
                        cmd.setInt(Tag.NumberOfFailedSuboperations, VR.US, f + 1);
                    }
                    cmd.setInt(Tag.NumberOfRemainingSuboperations, VR.US, r - 1);
                }
            }
        }
    };

    public CGetForward(DicomNode callingNode, DicomNode destinationNode, DicomProgress progress,
        Map<Integer, String> attributesToOverride) throws IOException {
        this.attributesToOverride = attributesToOverride;
        ae = new ApplicationEntity("GETSCU");
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.addConnection(conn);
        device.setDimseRQHandler(createServiceRegistry());
        state = new DicomState(progress);

        streamSCU = new StoreFromStreamSCU(null, callingNode, destinationNode);
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

    public void close() throws IOException, InterruptedException {
        if (as != null && as.isReadyForDataTransfer()) {
            as.waitForOutstandingRSP();
            as.release();
        }
        streamSCU.close();
        Device storeDevice = streamSCU.getDevice();
        if (storeDevice != null) {
            if (storeDevice.getExecutor() instanceof ExecutorService) {
                ((ExecutorService) storeDevice.getExecutor()).shutdown();
            }
            if (storeDevice.getScheduledExecutor() instanceof ScheduledExecutorService) {
                storeDevice.getScheduledExecutor().shutdown();
            }
        }
    }

    public void retrieve() throws IOException, InterruptedException {
        retrieve(keys);
    }

    private void retrieve(Attributes keys) throws IOException, InterruptedException {
        DimseRSPHandler rspHandler = new DimseRSPHandler(as.nextMessageID()) {

            @Override
            public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                super.onDimseRSP(as, cmd, data);
                DicomProgress p = state.getProgress();
                if (p != null) {
                    // if (cmd != null) {
                    // p.setAttributes(cmd);
                    // }
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

    public DicomState getState() {
        return state;
    }

    public void stop() {
        try {
            close();
        } catch (Exception e) {
            // Do nothing
        }

        Executor executorService = device.getExecutor();
        if (executorService instanceof ExecutorService) {
            ((ExecutorService) executorService).shutdown();
        }

        ScheduledExecutorService scheduledExecutorService = device.getScheduledExecutor();
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
        }
    }

    public static DicomState processStudy(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        DicomNode destinationNode, DicomProgress progress, String studyUID, Map<Integer, String> attributesToOverride) {
        return process(params, callingNode, calledNode, destinationNode, progress, "STUDY", studyUID,
            attributesToOverride);
    }

    public static DicomState processSeries(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        DicomNode destinationNode, DicomProgress progress, String seriesUID,
        Map<Integer, String> attributesToOverride) {
        return process(params, callingNode, calledNode, destinationNode, progress, "SERIES", seriesUID,
            attributesToOverride);
    }

    private static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        DicomNode destinationNode, DicomProgress progress, String queryRetrieveLevel, String queryUID,
        Map<Integer, String> attributesToOverride) {
        if (callingNode == null || calledNode == null || destinationNode == null) {
            throw new IllegalArgumentException("callingNode, calledNode or destinationNode cannot be null!");
        }
        CGetForward forward = null;
        AdvancedParams options = params == null ? new AdvancedParams() : params;

        try {
            forward = new CGetForward(callingNode, destinationNode, progress, attributesToOverride);
            Connection remote = forward.getRemoteConnection();
            Connection conn = forward.getConnection();
            options.configureConnect(forward.getAAssociateRQ(), remote, calledNode);
            options.configureBind(forward.getApplicationEntity(), conn, callingNode);

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

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            forward.getDevice().setExecutor(executorService);
            forward.getDevice().setScheduledExecutor(scheduledExecutorService);
            try {
                DicomState dcmState = forward.getState();
                long t1 = System.currentTimeMillis();
                forward.open();
                forward.retrieve();
                long t2 = System.currentTimeMillis();
                String timeMsg = MessageFormat.format("Get files from {0} to {1} in {2}ms",
                    forward.getAAssociateRQ().getCallingAET(), forward.getAAssociateRQ().getCalledAET(), t2 - t1);
                forceGettingAttributes(forward);
                return DicomState.buildMessage(dcmState, timeMsg, null);
            } catch (Exception e) {
                LOGGER.error("getscu", e);
                forceGettingAttributes(forward);
                return DicomState.buildMessage(forward.getState(), null, e);
            } finally {
                closeProcess(forward);
                Echo.shutdownService(executorService);
                Echo.shutdownService(scheduledExecutorService);
            }
        } catch (Exception e) {
            LOGGER.error("getscu", e);
            return new DicomState(Status.UnableToProcess,
                "DICOM Get failed" + StringUtil.COLON_AND_SPACE + e.getMessage(), null);
        }
    }

    private static void closeProcess(CGetForward getSCU) {
        try {
            getSCU.close();
        } catch (IOException e) {
            LOGGER.error("Closing CGetForward", e);
        } catch (InterruptedException e) {
            LOGGER.warn("Closing GetSCU Interruption"); //$NON-NLS-1$
        }
    }

    private static void forceGettingAttributes(CGetForward getSCU) {
        DicomProgress p = getSCU.getState().getProgress();
        if (p != null) {
            try {
                getSCU.close();
            } catch (Exception e) {
                // Do nothing
            }
        }
    }

    private static void configureRelatedSOPClass(CGetForward getSCU, URL url) throws IOException {
        Properties p = new Properties();
        try {
            if (url == null) {
                p.load(getSCU.getClass().getResourceAsStream("store-tcs.properties"));
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
