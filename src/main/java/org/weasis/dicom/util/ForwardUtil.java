package org.weasis.dicom.util;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.codec.Decompressor;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.DataWriter;
import org.dcm4che3.net.DataWriterAdapter;
import org.dcm4che3.net.InputStreamDataWriter;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.dicom.param.AttributeEditorContext.Abort;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.util.ServiceUtil.ProgressStatus;

public class ForwardUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForwardUtil.class);

    public static final class Params {
        private final String iuid;
        private final String cuid;
        private final String tsuid;
        private final InputStream data;
        private final Association as;
        private final int priority;

        public Params(String iuid, String cuid, String tsuid, int priority, InputStream data, Association as) {
            super();
            this.iuid = iuid;
            this.cuid = cuid;
            this.tsuid = tsuid;
            this.priority = priority;
            this.as = as;
            this.data = data;
        }

        public String getIuid() {
            return iuid;
        }

        public String getCuid() {
            return cuid;
        }

        public String getTsuid() {
            return tsuid;
        }

        public int getPriority() {
            return priority;
        }

        public Association getAs() {
            return as;
        }

        public InputStream getData() {
            return data;
        }
    }

    private static final class AbortException extends IllegalStateException {
        private static final long serialVersionUID = 3993065212756372490L;

        public AbortException(String s) {
            super(s);
        }
    }

    private ForwardUtil() {
    }

    public static void storeMulitpleDestination(DicomNode sourceNode,
        Map<DicomNode, List<ForwardDestination>> destinations, Params p) throws Exception {
        List<ForwardDestination> destList =
            destinations.get(new DicomNode(sourceNode.getAet(), sourceNode.getHostname(), null));
        if (destList == null || destList.isEmpty()) {
            throw new IllegalStateException("Cannot find the DICOM destination from " + sourceNode.toString());
        }

        if (destList.size() == 1) {
            storeOneDestination(sourceNode, destList.get(0), p);
        } else {
            List<ForwardDestination> destConList = new ArrayList<>();
            for (ForwardDestination fwDest : destList) {
                try {
                    prepareTransfer(fwDest, p.getCuid(), p.getTsuid());
                    destConList.add(fwDest);
                } catch (Exception e) {
                    LOGGER.error("Cannot connect to the final destination", e);
                }
            }

            if (destConList.isEmpty()) {
                return;
            } else if (destConList.size() == 1) {
                storeOneDestination(sourceNode, destConList.get(0), p);
            } else {
                List<File> files = null;
                try {
                    Attributes attributes = new Attributes();
                    files = transferFirst(sourceNode, destConList.get(0), attributes, p);
                    if (!attributes.isEmpty()) {
                        for (int i = 1; i < destConList.size(); i++) {
                            transferOther(sourceNode, destConList.get(i), attributes, p);
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

    public static void storeOneDestination(DicomNode sourceNode, ForwardDestination destination, Params p)
        throws Exception {
        StoreFromStreamSCU streamSCU = prepareTransfer(destination, p.getCuid(), p.getTsuid());
        if (streamSCU != null) {
            transfer(sourceNode, destination, p);
        }
    }

    public static StoreFromStreamSCU prepareTransfer(ForwardDestination destination, String cuid, String tsuid)
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

    public static void transfer(DicomNode sourceNode, ForwardDestination destination, Params p) {
        StoreFromStreamSCU streamSCU = destination.getStreamSCU();
        DicomInputStream in = null;
        try {
            if (!streamSCU.getAssociation().isReadyForDataTransfer()) {
                throw new IllegalStateException("Association not ready for transfer.");
            }
            DataWriter dataWriter;
            String iuid = p.getIuid();
            if (destination.getAttributesEditor() == null) {
                dataWriter = new InputStreamDataWriter(p.getData());
            } else {
                AttributeEditorContext context = new AttributeEditorContext(p.getTsuid(), sourceNode,
                    DicomNode.buildRemoteDicomNode(streamSCU.getAssociation()));
                in = new DicomInputStream(p.getData());
                in.setIncludeBulkData(IncludeBulkData.URI);
                Attributes attributes = in.readDataset(-1, Tag.PixelData);
                if (destination.getAttributesEditor().apply(attributes, context)) {
                    iuid = attributes.getString(Tag.SOPInstanceUID);
                }

                if (context.getAbort() == Abort.FILE_EXCEPTION) {
                    if (p.getData() instanceof PDVInputStream) {
                        ((PDVInputStream) p.getData()).skipAll();
                    }
                    throw new IllegalStateException(context.getAbortMessage());
                } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                    if (p.getAs() != null) {
                        p.getAs().abort();
                    }
                    throw new AbortException("DICOM associtation abort. " + context.getAbortMessage());
                }
                if (in.tag() == Tag.PixelData) {
                    in.readValue(in, attributes);
                    in.readAttributes(attributes, -1, -1);
                }
                dataWriter = new DataWriterAdapter(attributes);
            }

            streamSCU.getAssociation().cstore(p.getCuid(), iuid, p.getPriority(), dataWriter, p.getTsuid(),
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

    public static List<File> transferFirst(DicomNode sourceNode, ForwardDestination destination, Attributes copy,
        Params p) {
        StoreFromStreamSCU streamSCU = destination.getStreamSCU();
        DicomInputStream in = null;
        List<File> files = null;
        try {
            if (!streamSCU.getAssociation().isReadyForDataTransfer()) {
                throw new IllegalStateException("Association not ready for transfer.");
            }

            String tsuid = p.getTsuid();
            String iuid = p.getIuid();
            AttributeEditorContext context = new AttributeEditorContext(tsuid, sourceNode,
                DicomNode.buildRemoteDicomNode(streamSCU.getAssociation()));
            in = new DicomInputStream(p.getData());
            in.setIncludeBulkData(IncludeBulkData.URI);
            Attributes attributes = in.readDataset(-1, Tag.PixelData);
            if (destination.getAttributesEditor() != null
                && destination.getAttributesEditor().apply(attributes, context)) {
                iuid = attributes.getString(Tag.SOPInstanceUID);
            }

            if (context.getAbort() == Abort.FILE_EXCEPTION) {
                if (p.getData() instanceof PDVInputStream) {
                    ((PDVInputStream) p.getData()).skipAll();
                }
                throw new IllegalStateException(context.getAbortMessage());
            } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                if (p.getAs() != null) {
                    // Attention, this will also abort the transfer to the next destinations.
                    p.getAs().abort();
                }
                throw new AbortException("DICOM associtation abort. " + context.getAbortMessage());
            }
            if (in.tag() == Tag.PixelData) {
                in.readValue(in, attributes);
                in.readAttributes(attributes, -1, -1);
            }
            copy.addAll(attributes);
            
            String supportedTsuid = selectTransferSyntax(streamSCU.getAssociation(), p.getCuid(), tsuid);
            if (!supportedTsuid.equals(tsuid)) {
                Decompressor.decompress(copy, tsuid);
            }
            
            DataWriter dataWriter = new DataWriterAdapter(attributes);

            streamSCU.getAssociation().cstore(p.getCuid(), iuid, p.getPriority(), dataWriter, p.getTsuid(),
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

    public static void transferOther(DicomNode sourceNode, ForwardDestination destination, Attributes copy, Params p) {
        StoreFromStreamSCU streamSCU = destination.getStreamSCU();

        try {
            if (!streamSCU.getAssociation().isReadyForDataTransfer()) {
                throw new IllegalStateException("Association not ready for transfer.");
            }
     
            DataWriter dataWriter;
            String iuid = p.getIuid();
            if (destination.getAttributesEditor() == null) {
                dataWriter = new DataWriterAdapter(copy);
            } else {
                AttributeEditorContext context = new AttributeEditorContext(p.getTsuid(), sourceNode,
                    DicomNode.buildRemoteDicomNode(streamSCU.getAssociation()));
                Attributes attributes = new Attributes(copy);
                if (destination.getAttributesEditor().apply(attributes, context)) {
                    iuid = attributes.getString(Tag.SOPInstanceUID);
                }

                if (context.getAbort() == Abort.FILE_EXCEPTION) {
                    if (p.getData() instanceof PDVInputStream) {
                        ((PDVInputStream) p.getData()).skipAll();
                    }
                    throw new IllegalStateException(context.getAbortMessage());
                } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                    if (p.getAs() != null) {
                        // Attention, this will also abort the transfer to the next destinations.
                        p.getAs().abort();
                    }
                    throw new AbortException("DICOM associtation abort. " + context.getAbortMessage());
                }
                dataWriter = new DataWriterAdapter(attributes);
            }

            streamSCU.getAssociation().cstore(p.getCuid(), iuid, p.getPriority(), dataWriter, p.getTsuid(),
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
    
    public static String selectTransferSyntax(Association as, String cuid, String filets) {
        Set<String> tss = as.getTransferSyntaxesFor(cuid);
        if (tss.contains(filets)) {
            return filets;
        }

        if (tss.contains(UID.ExplicitVRLittleEndian)) {
            return UID.ExplicitVRLittleEndian;
        }

        return UID.ImplicitVRLittleEndian;
    }

}
