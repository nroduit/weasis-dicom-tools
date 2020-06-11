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
package org.weasis.dicom.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.dcm4che6.data.DataFragment;
import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.UID;
import org.dcm4che6.data.VR;
import org.dcm4che6.img.DicomOutputData;
import org.dcm4che6.img.DicomTranscodeParam;
import org.dcm4che6.img.Transcoder;
import org.dcm4che6.img.data.TransferSyntaxType;
import org.dcm4che6.img.stream.BytesWithImageDescriptor;
import org.dcm4che6.img.stream.ImageDescriptor;
import org.dcm4che6.io.DicomEncoding;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.net.Association;
import org.dcm4che6.net.Association.DataWriter;
import org.dcm4che6.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.dicom.param.AttributeEditorContext.Abort;
import org.weasis.dicom.param.DicomForwardDestination;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.ForwardDestination;
import org.weasis.dicom.param.ForwardDicomNode;
import org.weasis.dicom.util.ServiceUtil.ProgressStatus;
import org.weasis.dicom.web.DicomStowRS;
import org.weasis.dicom.web.WebForwardDestination;

public class ForwardUtil {
    private static final String ERROR_WHEN_FORWARDING = "Error when forwarding to the final destination";
    private static final Logger LOGGER = LoggerFactory.getLogger(ForwardUtil.class);
    protected static final byte[] EMPTY_BYTES = {};

    public static final class Params {
        private final String iuid;
        private final String cuid;
        private final String tsuid;
        private final Byte pcid;
        private final InputStream data;
        private final Association as;
        private String outputTsuid;

        public Params(String iuid, String cuid, Byte pcid, InputStream data, Association as) {
            super();
            this.iuid = iuid;
            this.cuid = cuid;
            this.pcid = pcid;
            this.tsuid = as.getTransferSyntax(pcid);
            this.as = as;
            this.data = data;
            this.outputTsuid = tsuid;
        }

        public Byte getPcid() {
            return pcid;
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

        public Association getAs() {
            return as;
        }

        public InputStream getData() {
            return data;
        }

        public String getOutputTsuid() {
            return outputTsuid;
        }

        public void setOutputTsuid(String outputTsuid) {
            this.outputTsuid = outputTsuid;
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
        throws IOException {
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
                DicomObject dcm = DicomObject.newDicomObject();
                ForwardDestination fistDest = destConList.get(0);
                if (fistDest instanceof DicomForwardDestination) {
                    transfer(fwdNode, (DicomForwardDestination) fistDest, dcm, p);
                } else if (fistDest instanceof WebForwardDestination) {
                    transfer(fwdNode, (WebForwardDestination) fistDest, dcm, p);
                }
                if (!dcm.isEmpty()) {
                    for (int i = 1; i < destConList.size(); i++) {
                        ForwardDestination dest = destConList.get(i);
                        if (dest instanceof DicomForwardDestination) {
                            transferOther(fwdNode, (DicomForwardDestination) dest, dcm, p);
                        } else if (dest instanceof WebForwardDestination) {
                            transferOther(fwdNode, (WebForwardDestination) dest, dcm, p);
                        }
                    }
                }
            }
        }

    }

    public static void storeOneDestination(ForwardDicomNode fwdNode, ForwardDestination destination, Params p)
        throws IOException {
        if (destination instanceof DicomForwardDestination) {
            DicomForwardDestination dest = (DicomForwardDestination) destination;
            prepareTransfer(dest, p.getCuid(), p.getTsuid());
            transfer(fwdNode, dest, null, p);
        } else if (destination instanceof WebForwardDestination) {
            transfer(fwdNode, (WebForwardDestination) destination, null, p);
        }
    }

    public static StoreFromStreamSCU prepareTransfer(DicomForwardDestination destination, String cuid, String tsuid)
        throws IOException {
        StoreFromStreamSCU streamSCU = destination.getStreamSCU();
        if (streamSCU.getAssociation() == null || !streamSCU.getAssociation().isOpen()) {
            // Add Presentation Context for the association
            streamSCU.addData(cuid, tsuid);
            streamSCU.addData(cuid, UID.ExplicitVRLittleEndian);
            streamSCU.open();
        } else {
            // Handle dynamically new SOPClassUID
            Stream<Byte> val = streamSCU.getAssociationRq().pcidsFor(cuid, tsuid);
            boolean missingTsuid = val.findFirst().isEmpty();
            // Add Presentation Context for the association
            streamSCU.addData(cuid, tsuid);
            streamSCU.addData(cuid, UID.ExplicitVRLittleEndian);
            if (missingTsuid) {
                streamSCU.close();
                streamSCU.open();
            }
        }
        return streamSCU;
    }

    public static void transfer(ForwardDicomNode sourceNode, DicomForwardDestination destination, DicomObject copy,
        Params p) throws IOException {
        StoreFromStreamSCU streamSCU = destination.getStreamSCU();
        DicomInputStream in = null;
        try {
            if (!streamSCU.getAssociation().isOpen()) {
                throw new IllegalStateException("Association not ready for transfer.");
            }
            DataWriter dataWriter;
            String tsuid = p.getTsuid();
            String iuid = p.getIuid();
            Optional<Byte> pcid = selectTransferSyntax(streamSCU.getAssociation(), p);
            if (pcid.isEmpty()) {
                throw new IOException("The remote destination has no matching Presentation Context");
            }
            String supportedTsuid = streamSCU.getAssociation().getTransferSyntax(pcid.get());
            List<AttributeEditor> editors = destination.getDicomEditors();

            if (copy == null && editors.isEmpty() && supportedTsuid.equals(tsuid)) {
                dataWriter = (out, t) -> p.getData().transferTo(out);
            } else {
                AttributeEditorContext context = streamSCU.getContext();
                DicomObject data = null;
                try (DicomInputStream dis = new DicomInputStream(p.getData())) {
                    data = dis.readDataSet();
                    if (data == null) {
                        throw new IllegalStateException("Cannot read DICOM dataset");
                    }
                }
                if (copy != null) {
                    copy.forEach(data::add);
                }

                if (!editors.isEmpty()) {
                    DicomObject finalData = data;
                    editors.forEach(e -> e.apply(finalData, context));
                    iuid = data.getString(Tag.SOPInstanceUID).orElse(null);
                    // sopClassUID = data.getString(Tag.SOPClassUID).orElse(null);
                }

                if (context.getAbort() == Abort.FILE_EXCEPTION) {
                    p.getData().transferTo(OutputStream.nullOutputStream());
                    throw new IllegalStateException(context.getAbortMessage());
                } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                    if (p.getAs() != null) {
                        p.getAs().release();
                    }
                    throw new AbortException("DICOM association abort: " + context.getAbortMessage());
                }
                dataWriter = imageTranscode(data, tsuid, supportedTsuid);
            }

            streamSCU.getAssociation().cstore(p.getCuid(), iuid, dataWriter, supportedTsuid);
        } catch (AbortException e) {
            ServiceUtil.notifyProgession(streamSCU.getState(), p.getIuid(), p.getCuid(), Status.ProcessingFailure,
                ProgressStatus.FAILED, streamSCU.getNumberOfSuboperations());
            throw e;
        } catch (Exception e) {
            LOGGER.error(ERROR_WHEN_FORWARDING, e);
            ServiceUtil.notifyProgession(streamSCU.getState(), p.getIuid(), p.getCuid(), Status.ProcessingFailure,
                ProgressStatus.FAILED, streamSCU.getNumberOfSuboperations());
          //  throw new IOException("StoreSCU abort: " + e.getMessage(), e);
        } finally {
            FileUtil.safeClose(in);
            streamSCU.triggerCloseExecutor();
        }
    }

    public static void transferOther(ForwardDicomNode fwdNode, DicomForwardDestination destination, DicomObject copy,
        Params p) throws IOException {
        StoreFromStreamSCU streamSCU = destination.getStreamSCU();

        try {
            if (!streamSCU.getAssociation().isOpen()) {
                throw new IllegalStateException("Association not ready for transfer.");
            }

            DataWriter dataWriter;
            String tsuid = p.getTsuid();
            String iuid = p.getIuid();
            Optional<Byte> pcid = selectTransferSyntax(streamSCU.getAssociation(), p);
            if (pcid.isEmpty()) {
                throw new IOException("The remote destination has no matching Presentation Context");
            }
            String supportedTsuid = streamSCU.getAssociation().getTransferSyntax(pcid.get());
            List<AttributeEditor> editors = destination.getDicomEditors();
            if (editors.isEmpty() && supportedTsuid.equals(tsuid)) {
                dataWriter = (out, t) -> p.getData().transferTo(out);
            } else {
                AttributeEditorContext context =
                    new AttributeEditorContext(fwdNode, DicomNode.buildRemoteDicomNode(streamSCU.getAssociation()));
                DicomObject dcm = DicomObject.newDicomObject();
                copy.forEach(dcm::add);
                if (!editors.isEmpty()) {
                    editors.forEach(e -> e.apply(dcm, context));
                    iuid = dcm.getString(Tag.SOPInstanceUID).orElse(null);
                }

                if (context.getAbort() == Abort.FILE_EXCEPTION) {
                    throw new IllegalStateException(context.getAbortMessage());
                } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                    throw new AbortException("DICOM associtation abort. " + context.getAbortMessage());
                }

                dataWriter = imageTranscode(dcm, tsuid, supportedTsuid);
            }

            streamSCU.getAssociation().cstore(p.getCuid(), iuid, dataWriter, supportedTsuid);
        } catch (AbortException e) {
            ServiceUtil.notifyProgession(streamSCU.getState(), p.getIuid(), p.getCuid(), Status.ProcessingFailure,
                ProgressStatus.FAILED, streamSCU.getNumberOfSuboperations());
            throw e;
        } catch (Exception e) {
            LOGGER.error(ERROR_WHEN_FORWARDING, e);
            ServiceUtil.notifyProgession(streamSCU.getState(), p.getIuid(), p.getCuid(), Status.ProcessingFailure,
                ProgressStatus.FAILED, streamSCU.getNumberOfSuboperations());
     //       throw new IOException("StoreSCU abort: " + e.getMessage(), e);
        }
    }

    private static DataWriter imageTranscode(DicomObject data, String originalTsuid, String supportedTsuid)
        throws Exception {
        if (!supportedTsuid.equals(originalTsuid)
            && TransferSyntaxType.forUID(originalTsuid) != TransferSyntaxType.NATIVE) {
            Optional<DicomElement> pixdata = data.get(Tag.PixelData);
            ImageDescriptor imdDesc = new ImageDescriptor(data);
            BytesWithImageDescriptor desc = new BytesWithImageDescriptor() {

                @Override
                public ImageDescriptor getImageDescriptor() {
                    return imdDesc;
                }

                @Override
                public ByteBuffer getBytes(int frame) throws IOException {
                    ImageDescriptor desc = getImageDescriptor();
                    int bitsStored = desc.getBitsStored();
                    if (pixdata.isEmpty() || bitsStored < 1) {
                        return ByteBuffer.wrap(EMPTY_BYTES);
                    } else {
                        DicomElement pix = pixdata.get();
                        int index = pix.getDataFragment(0).valueLength() == 0 ? frame + 1 : frame;
                        DataFragment fragment = pix.getDataFragment(index);
                        ByteArrayOutputStream out = new ByteArrayOutputStream(fragment.valueLength());
                        fragment.writeTo(out);
                        return ByteBuffer.wrap(out.toByteArray());
                    }
                }

                @Override
                public String getTransferSyntax() {
                    return originalTsuid;
                }

                @Override
                public boolean forceYbrToRgbConversion() {
                    // TODO Auto-generated method stub
                    return false;
                }

            };

            DicomTranscodeParam tparams = new DicomTranscodeParam(supportedTsuid);
            DicomOutputData imgData = Transcoder.dcm2dcm(desc, tparams);
            return (out, tsuid) -> {
                DicomObject dataSet = DicomObject.newDicomObject();
                for (DicomElement el : data) {
                    if (el.tag() == Tag.PixelData) {
                        break;
                    }
                    dataSet.add(el);
                }
                try (DicomOutputStream dos =
                    new DicomOutputStream(out).withEncoding(DicomEncoding.of(supportedTsuid))) {
                    if (DicomOutputData.isNativeSyntax(supportedTsuid)) {
                        imgData.writRawImageData(dos, dataSet);
                    } else {
                        int[] jpegWriteParams = DicomOutputData.adaptTagsToImage(dataSet,
                            imgData.getImages().get(0), desc.getImageDescriptor(), tparams.getWriteJpegParam());
                        dos.writeDataSet(dataSet);
                        imgData.writCompressedImageData(dos, jpegWriteParams);
                    }
                } catch (Exception e) {
                    LOGGER.error("Transcoding image data", e);
                }
            };
        }
        return (out, tsuid) -> {
            try (DicomOutputStream writer = new DicomOutputStream(out).withEncoding(DicomEncoding.of(tsuid))) {
                writer.writeDataSet(data);
                writer.writeHeader(Tag.ItemDelimitationItem, VR.NONE, 0);
            }
        };
    }

    public static void transfer(ForwardDicomNode fwdNode, WebForwardDestination destination, DicomObject copy,
        Params p) {
        DicomInputStream in = null;
        try {
            DicomStowRS stow = destination.getStowrsSingleFile();
            String tsuid = p.getTsuid();
            boolean originalTsuid = true;
            if (UID.ImplicitVRLittleEndian.equals(tsuid) || UID.ExplicitVRBigEndianRetired.equals(tsuid)) {
                p.setOutputTsuid(UID.ExplicitVRLittleEndian);
                originalTsuid = false;
            }
            if (originalTsuid && copy == null && destination.getDicomEditors().isEmpty()) {
                DicomObject fmi = DicomObject.createFileMetaInformation(p.getCuid(), p.getIuid(), p.getOutputTsuid());
                try (InputStream stream = p.getData()) {
                    stow.uploadDicom(stream, fmi);
                }
            } else {
                AttributeEditorContext context = new AttributeEditorContext(fwdNode, null);
                try (DicomInputStream dis = new DicomInputStream(p.getData())) {
                    DicomObject data = dis.readDataSet();
                    if (copy != null) {
                        copy.forEach(data::add);
                    }
                    destination.getDicomEditors().forEach(e -> e.apply(data, context));

                    if (context.getAbort() == Abort.FILE_EXCEPTION) {
                        p.getData().transferTo(OutputStream.nullOutputStream());
                        throw new IllegalStateException(context.getAbortMessage());
                    } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                        if (p.getAs() != null) {
                            p.getAs().release();
                        }
                        throw new AbortException("STOW-RS abort: " + context.getAbortMessage());
                    }

                    stow.uploadDicom(data, p.getOutputTsuid());
                }
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
            FileUtil.safeClose(in);
        }
    }

    public static void transferOther(ForwardDicomNode fwdNode, WebForwardDestination destination, DicomObject copy,
        Params p) {
        try {
            DicomStowRS stow = destination.getStowrsSingleFile();
            String tsuid = p.getOutputTsuid();
            if (destination.getDicomEditors().isEmpty()) {
                stow.uploadDicom(copy, tsuid);
            } else {
                AttributeEditorContext context = new AttributeEditorContext(fwdNode, null);
                DicomObject dcm = DicomObject.newDicomObject();
                copy.forEach(dcm::add);
                destination.getDicomEditors().forEach(e -> e.apply(dcm, context));

                if (context.getAbort() == Abort.FILE_EXCEPTION) {
                    throw new IllegalStateException(context.getAbortMessage());
                } else if (context.getAbort() == Abort.CONNECTION_EXCEPTION) {
                    throw new AbortException("DICOM associtation abort. " + context.getAbortMessage());
                }

                stow.uploadDicom(dcm, tsuid);
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

    public static Optional<Byte> selectTransferSyntax(Association as, Params p) {
        if (as.getAaac().acceptedTransferSyntax(p.getPcid(), p.getTsuid())) {
            return Optional.of(p.getPcid());
        }
        return as.getAarq().pcidsFor(p.getCuid())
            .filter(b -> as.getAaac().acceptedTransferSyntax(b, UID.ExplicitVRLittleEndian))
            .findFirst();
    }
}
