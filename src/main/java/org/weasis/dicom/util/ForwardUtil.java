package org.weasis.dicom.util;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.dicom.param.AttributeEditorContext.Abort;
import org.weasis.dicom.param.DicomForwardDestination;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.param.ForwardDicomNode;
import org.weasis.dicom.util.ServiceUtil.ProgressStatus;
import org.weasis.dicom.web.UploadSingleFile;
import org.weasis.dicom.web.WebForwardDestination;

public class ForwardUtil {
    private static final String ERROR_WHEN_FORWARDING = "Error when forwarding to the final destination";
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

        public AbortException(String string, Exception e) {
            super(string, e);
        }

        @Override
        public String toString() {
            return getMessage();
        }
    }

    private ForwardUtil() {
    }

    public static void storeMulitpleDestination(ForwardDicomNode fwdNode, List<ForwardDestination> destList, Params p)
        throws Exception {
        if (destList == null || destList.isEmpty()) {
            throw new IllegalStateException("Cannot find the DICOM destination from " + fwdNode.toString());
        }
        // Exclude DICOMDIR
        if ("1.2.840.10008.1.3.10".equals(p.cuid)) {
            LOGGER.warn("Cannot send DICOMDIR {}", p.iuid);
            return;
        }

        if (destList.size() == 1) {
            storeOneDestination(fwdNode, destList.get(0), p);
        } else {
            List<ForwardDestination> destConList = new ArrayList<>();
            for (ForwardDestination fwDest : destList) {
                try {
                    if (fwDest instanceof DicomForwardDestination) {
                        prepareTransfer((DicomForwardDestination) fwDest, p.getCuid(), p.getTsuid());
                    }
                    destConList.add(fwDest);
                } catch (Exception e) {
                    LOGGER.error("Cannot connect to the final destination", e);
                }
            }

            if (destConList.isEmpty()) {
                return;
            } else if (destConList.size() == 1) {
                storeOneDestination(fwdNode, destConList.get(0), p);
            } else {
                List<File> files = null;
                try {
                    Attributes attributes = new Attributes();
                    ForwardDestination fistDest = destConList.get(0);
                    if (fistDest instanceof DicomForwardDestination) {
                        files = transfer(fwdNode, (DicomForwardDestination) fistDest, attributes, p);
                    } else if (fistDest instanceof WebForwardDestination) {
                        files = transfer(fwdNode, (WebForwardDestination) fistDest, null, p);
                    }
                    if (!attributes.isEmpty()) {
                        for (int i = 1; i < destConList.size(); i++) {
                            ForwardDestination dest = destConList.get(i);
                            if (dest instanceof DicomForwardDestination) {
                                transferOther(fwdNode, (DicomForwardDestination) dest, attributes, p);
                            } else if (dest instanceof WebForwardDestination) {
                                transferOther(fwdNode, (WebForwardDestination) dest, attributes, p);
                            }
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

    public static void storeOneDestination(ForwardDicomNode fwdNode, ForwardDestination destination, Params p)
        throws Exception {
        if (destination instanceof DicomForwardDestination) {
            DicomForwardDestination dest = (DicomForwardDestination) destination;
            prepareTransfer(dest, p.getCuid(), p.getTsuid());
            transfer(fwdNode, dest, null, p);
        } else if (destination instanceof WebForwardDestination) {
            transfer(fwdNode, (WebForwardDestination) destination, null, p);
        }
    }

    public static StoreFromStreamSCU prepareTransfer(DicomForwardDestination destination, String cuid, String tsuid)
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

    public static List<File> transfer(ForwardDicomNode sourceNode, DicomForwardDestination destination, Attributes copy,
        Params p) {
        StoreFromStreamSCU streamSCU = destination.getStreamSCU();
        DicomInputStream in = null;
        List<File> files = null;
        try {
            if (!streamSCU.getAssociation().isReadyForDataTransfer()) {
                throw new IllegalStateException("Association not ready for transfer.");
            }
            DataWriter dataWriter;
            String tsuid = p.getTsuid();
            String iuid = p.getIuid();
            String supportedTsuid = selectTransferSyntax(streamSCU.getAssociation(), p.getCuid(), tsuid);
            AttributeEditor editor = destination.getAttributesEditor();

            if (copy == null && editor == null && supportedTsuid.equals(tsuid)) {
                dataWriter = new InputStreamDataWriter(p.getData());
            } else {
                AttributeEditorContext context = new AttributeEditorContext(tsuid, sourceNode,
                    DicomNode.buildRemoteDicomNode(streamSCU.getAssociation()));
                in = new DicomInputStream(p.getData(), tsuid);
                in.setIncludeBulkData(IncludeBulkData.URI);
                Attributes attributes = in.readDataset(-1, -1);
                if (editor != null && editor.apply(attributes, context)) {
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
                    throw new AbortException("DICOM association abort: " + context.getAbortMessage());
                }

                if (copy != null) {
                    copy.addAll(attributes);
                }

                if (!supportedTsuid.equals(tsuid)) {
                    Decompressor.decompress(attributes, tsuid);
                }
                dataWriter = new DataWriterAdapter(attributes);
            }

            streamSCU.getAssociation().cstore(p.getCuid(), iuid, p.getPriority(), dataWriter, supportedTsuid,
                streamSCU.getRspHandlerFactory().createDimseRSPHandler());
        } catch (AbortException e) {
            ServiceUtil.notifyProgession(streamSCU.getState(), p.getIuid(), p.getCuid(), Status.ProcessingFailure,
                ProgressStatus.FAILED, streamSCU.getNumberOfSuboperations());
            throw e;
        } catch (Exception e) {
            LOGGER.error(ERROR_WHEN_FORWARDING, e);
            ServiceUtil.notifyProgession(streamSCU.getState(), p.getIuid(), p.getCuid(), Status.ProcessingFailure,
                ProgressStatus.FAILED, streamSCU.getNumberOfSuboperations());
            throw new AbortException("StoreSCU abort: " + e.getMessage(), e);
        } finally {
            files = cleanOrGetBulkDataFiles(in, copy == null);
        }
        return files;
    }

    private static List<File> cleanOrGetBulkDataFiles(DicomInputStream in, boolean clean) {
        FileUtil.safeClose(in);
        if (clean) {
            // Force to clean if tmp bulk files
            ServiceUtil.safeClose(in);
        } else if (in != null) {
            // Return tmp bulk files
            return in.getBulkDataFiles();
        }
        return null;
    }

    public static void transferOther(ForwardDicomNode fwdNode, DicomForwardDestination destination, Attributes copy,
        Params p) {
        StoreFromStreamSCU streamSCU = destination.getStreamSCU();

        try {
            if (!streamSCU.getAssociation().isReadyForDataTransfer()) {
                throw new IllegalStateException("Association not ready for transfer.");
            }

            DataWriter dataWriter;
            String tsuid = p.getTsuid();
            String iuid = p.getIuid();
            String supportedTsuid = selectTransferSyntax(streamSCU.getAssociation(), p.getCuid(), tsuid);
            AttributeEditor editor = destination.getAttributesEditor();
            if (editor == null && supportedTsuid.equals(tsuid)) {
                dataWriter = new DataWriterAdapter(copy);
            } else {
                AttributeEditorContext context = new AttributeEditorContext(tsuid, fwdNode,
                    DicomNode.buildRemoteDicomNode(streamSCU.getAssociation()));
                Attributes attributes = new Attributes(copy);
                if (editor != null && editor.apply(attributes, context)) {
                    iuid = attributes.getString(Tag.SOPInstanceUID);
                }

                if (context.getAbort() == Abort.FILE_EXCEPTION) {
                    throw new IllegalStateException(context.getAbortMessage());
                } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                    throw new AbortException("DICOM associtation abort. " + context.getAbortMessage());
                }

                if (!supportedTsuid.equals(tsuid)) {
                    Decompressor.decompress(attributes, tsuid);
                }
                dataWriter = new DataWriterAdapter(attributes);
            }

            streamSCU.getAssociation().cstore(p.getCuid(), iuid, p.getPriority(), dataWriter, supportedTsuid,
                streamSCU.getRspHandlerFactory().createDimseRSPHandler());
        } catch (AbortException e) {
            ServiceUtil.notifyProgession(streamSCU.getState(), p.getIuid(), p.getCuid(), Status.ProcessingFailure,
                ProgressStatus.FAILED, streamSCU.getNumberOfSuboperations());
            throw e;
        } catch (Exception e) {
            LOGGER.error(ERROR_WHEN_FORWARDING, e);
            ServiceUtil.notifyProgession(streamSCU.getState(), p.getIuid(), p.getCuid(), Status.ProcessingFailure,
                ProgressStatus.FAILED, streamSCU.getNumberOfSuboperations());
            throw new AbortException("StoreSCU abort: " + e.getMessage(), e);
        }
    }

    public static List<File> transfer(ForwardDicomNode fwdNode, WebForwardDestination destination, Attributes copy,
        Params p) {
        DicomInputStream in = null;
        List<File> files = null;
        try {
            UploadSingleFile stow = destination.getStowrsSingleFile();
            String tsuid = p.getTsuid();
            if (copy == null && destination.getAttributesEditor() == null) {
                Attributes fmi = Attributes.createFileMetaInformation(p.getIuid(), p.getCuid(), tsuid);
                try (InputStream stream = p.getData()) {
                    stow.uploadDicom(p.getData(), fmi, tsuid, p.getIuid());
                }
            } else {
                AttributeEditorContext context = new AttributeEditorContext(tsuid, fwdNode, null);
                in = new DicomInputStream(p.getData(), tsuid);
                in.setIncludeBulkData(IncludeBulkData.URI);
                Attributes attributes = in.readDataset(-1, -1);
                destination.getAttributesEditor().apply(attributes, context);

                if (context.getAbort() == Abort.FILE_EXCEPTION) {
                    if (p.getData() instanceof PDVInputStream) {
                        ((PDVInputStream) p.getData()).skipAll();
                    }
                    throw new IllegalStateException(context.getAbortMessage());
                } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                    if (p.getAs() != null) {
                        p.getAs().abort();
                    }
                    throw new AbortException("STOWRS abort: " + context.getAbortMessage());
                }

                if (copy != null) {
                    copy.addAll(attributes);
                }

                stow.uploadDicom(attributes, tsuid);
                ServiceUtil.notifyProgession(destination.getState(), p.getIuid(), p.getCuid(), Status.Success,
                    ProgressStatus.COMPLETED, 0);
            }
        } catch (AbortException e) {
            ServiceUtil.notifyProgession(destination.getState(), p.getIuid(), p.getCuid(), Status.ProcessingFailure,
                ProgressStatus.FAILED, 0);
            throw e;
        } catch (Exception e) {
            LOGGER.error(ERROR_WHEN_FORWARDING, e);
            ServiceUtil.notifyProgession(destination.getState(), p.getIuid(), p.getCuid(), Status.ProcessingFailure,
                ProgressStatus.FAILED, 0);
            throw new AbortException("STOWRS abort: " + e.getMessage(), e);
        } finally {
            files = cleanOrGetBulkDataFiles(in, copy == null);
        }
        return files;
    }

    public static void transferOther(ForwardDicomNode fwdNode, WebForwardDestination destination, Attributes copy,
        Params p) {
        try {
            UploadSingleFile stow = destination.getStowrsSingleFile();
            String tsuid = p.getTsuid();
            if (destination.getAttributesEditor() == null) {
                stow.uploadDicom(copy, tsuid);
            } else {
                AttributeEditorContext context = new AttributeEditorContext(tsuid, fwdNode, null);
                Attributes attributes = new Attributes(copy);
                destination.getAttributesEditor().apply(attributes, context);

                if (context.getAbort() == Abort.FILE_EXCEPTION) {
                    throw new IllegalStateException(context.getAbortMessage());
                } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                    throw new AbortException("DICOM associtation abort. " + context.getAbortMessage());
                }

                stow.uploadDicom(attributes, tsuid);
                ServiceUtil.notifyProgession(destination.getState(), p.getIuid(), p.getCuid(), Status.Success,
                    ProgressStatus.COMPLETED, 0);
            }
        } catch (AbortException e) {
            ServiceUtil.notifyProgession(destination.getState(), p.getIuid(), p.getCuid(), Status.ProcessingFailure,
                ProgressStatus.FAILED, 0);
            throw e;
        } catch (Exception e) {
            LOGGER.error(ERROR_WHEN_FORWARDING, e);
            ServiceUtil.notifyProgession(destination.getState(), p.getIuid(), p.getCuid(), Status.ProcessingFailure,
                ProgressStatus.FAILED, 0);
            throw new AbortException("STOWRS abort: " + e.getMessage(), e);
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
