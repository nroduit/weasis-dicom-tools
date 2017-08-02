package org.weasis.dicom.util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Objects;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.tool.storescu.RelatedGeneralSOPClasses;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.ServiceUtil.ProgressStatus;

public class StoreFromStreamSCU {

    private static Logger LOGGER = LoggerFactory.getLogger(StoreFromStreamSCU.class);

    @FunctionalInterface
    public interface RSPHandlerFactory {
        DimseRSPHandler createDimseRSPHandler();
    }

    private final ApplicationEntity ae;
    private final Connection remote;
    private final AAssociateRQ rq = new AAssociateRQ();
    public final RelatedGeneralSOPClasses relSOPClasses = new RelatedGeneralSOPClasses();
    private Attributes attrs;
    private boolean relExtNeg;
    private Association as;

    private final Device device;
    private int lastStatusCode = Integer.MIN_VALUE;
    private int nbStatusLog = 0;
    private int numberOfSuboperations = 0;
    private final DicomState state;

    private final RSPHandlerFactory rspHandlerFactory = () -> new DimseRSPHandler(as.nextMessageID()) {

        @Override
        public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
            super.onDimseRSP(as, cmd, data);
            StoreFromStreamSCU.this.onCStoreRSP(cmd);

            DicomProgress progress = state.getProgress();
            if (progress != null) {
                progress.setAttributes(cmd);
            }
        }
    };

    public StoreFromStreamSCU(DicomNode callingNode, DicomNode calledNode) throws IOException {
        this(null, callingNode, calledNode, null);
    }

    public StoreFromStreamSCU(AdvancedParams params, DicomNode callingNode, DicomNode calledNode) throws IOException {
        this(params, callingNode, calledNode, null);
    }

    public StoreFromStreamSCU(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        DicomProgress progress) throws IOException {
        Objects.requireNonNull(callingNode);
        Objects.requireNonNull(calledNode);
        AdvancedParams options = params == null ? new AdvancedParams() : params;
        this.state = new DicomState(progress);
        this.device = new Device("storescu");
        Connection conn = new Connection();
        device.addConnection(conn);
        this.ae = new ApplicationEntity(callingNode.getAet());
        device.addApplicationEntity(ae);
        ae.addConnection(conn);

        this.remote = new Connection();

        rq.addPresentationContext(new PresentationContext(1, UID.VerificationSOPClass, UID.ImplicitVRLittleEndian));

        options.configureConnect(rq, remote, calledNode);
        options.configureBind(ae, conn, callingNode);

        // configure
        options.configure(conn);
        options.configureTLS(conn, remote);

        setAttributes(new Attributes());
    }

    public Device getDevice() {
        return device;
    }

    public AAssociateRQ getAAssociateRQ() {
        return rq;
    }

    public Connection getRemoteConnection() {
        return remote;
    }

    public Attributes getAttributes() {
        return attrs;
    }

    public void setAttributes(Attributes attrs) {
        this.attrs = attrs;
    }

    public final void enableSOPClassRelationshipExtNeg(boolean enable) {
        relExtNeg = enable;
    }

    public boolean addData(String cuid, String tsuid) throws IOException {
        if (cuid == null || tsuid == null) {
            return false;
        }

        if (rq.containsPresentationContextFor(cuid, tsuid)) {
            return true;
        }

        if (!rq.containsPresentationContextFor(cuid)) {
            if (relExtNeg) {
                rq.addCommonExtendedNegotiation(relSOPClasses.getCommonExtendedNegotiation(cuid));
            }
            if (!tsuid.equals(UID.ExplicitVRLittleEndian)) {
                rq.addPresentationContext(new PresentationContext(rq.getNumberOfPresentationContexts() * 2 + 1, cuid,
                    UID.ExplicitVRLittleEndian));
            }
            if (!tsuid.equals(UID.ImplicitVRLittleEndian)) {
                rq.addPresentationContext(new PresentationContext(rq.getNumberOfPresentationContexts() * 2 + 1, cuid,
                    UID.ImplicitVRLittleEndian));
            }
        }
        rq.addPresentationContext(new PresentationContext(rq.getNumberOfPresentationContexts() * 2 + 1, cuid, tsuid));
        return true;
    }

    public void close() throws IOException, InterruptedException {
        if (as != null) {
            if (as.isReadyForDataTransfer()) {
                as.release();
            }
            as.waitForSocketClose();
        }
    }

    public void open()
        throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        as = ae.connect(remote, rq);
        // TODO check inactivity of 30 sec and close
    }

    public Association getAssociation() {
        return as;
    }

    /**
     * @see <a
     *      href="ERROR CODE STATUS">https://github.com/dcm4che/dcm4che/blob/master/dcm4che-net/src/main/java/org/dcm4che3/net/Status.java</a>
     */
    private void onCStoreRSP(Attributes cmd) {
        int status = cmd.getInt(Tag.Status, -1);
        state.setStatus(status);
        ProgressStatus ps;

        switch (status) {
            case Status.Success:
                ps = ProgressStatus.COMPLETED;
                break;
            case Status.CoercionOfDataElements:
            case Status.ElementsDiscarded:

            case Status.DataSetDoesNotMatchSOPClassWarning:
                ps = ProgressStatus.WARNING;
                if (lastStatusCode != status && nbStatusLog < 3) {
                    nbStatusLog++;
                    lastStatusCode = status;
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.warn("Received C-STORE-RSP with Status {}H{}", TagUtils.shortToHexString(status),
                            "\r\n" + cmd.toString());
                    } else {
                        LOGGER.warn("Received C-STORE-RSP with Status {}H", TagUtils.shortToHexString(status));
                    }
                }
                break;

            default:
                ps = ProgressStatus.FAILED;
                if (lastStatusCode != status && nbStatusLog < 3) {
                    nbStatusLog++;
                    lastStatusCode = status;
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.error("Received C-STORE-RSP with Status {}H{}", TagUtils.shortToHexString(status),
                            "\r\n" + cmd.toString());
                    } else {
                        LOGGER.error("Received C-STORE-RSP with Status {}H", TagUtils.shortToHexString(status));
                    }
                }
        }
        ServiceUtil.notifyProgession(state.getProgress(), cmd, ps, numberOfSuboperations);
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

    public RSPHandlerFactory getRspHandlerFactory() {
        return rspHandlerFactory;
    }
}
