package org.weasis.dicom.tool;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.jpeg.JPEG;
import org.dcm4che3.imageio.codec.jpeg.JPEGHeader;
import org.dcm4che3.imageio.codec.mpeg.MPEGHeader;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.util.FileUtil;

public class Dicomizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Dicomizer.class);

    private Dicomizer() {
    }

    public static void pdf(final Attributes attrs, File pdfFile, File dcmFile) throws IOException {
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.EncapsulatedPDFStorage);
        ensureString(attrs, Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192");// UTF-8
        ensureUID(attrs, Tag.StudyInstanceUID);
        ensureUID(attrs, Tag.SeriesInstanceUID);
        ensureUID(attrs, Tag.SOPInstanceUID);
        setCreationDate(attrs);

        BulkData bulk = new BulkData(pdfFile.toURI().toString(), 0, (int) pdfFile.length(), false);
        attrs.setValue(Tag.EncapsulatedDocument, VR.OB, bulk);
        attrs.setString(Tag.MIMETypeOfEncapsulatedDocument, VR.LO, "application/pdf");
        Attributes fmi = attrs.createFileMetaInformation(UID.ExplicitVRLittleEndian);
        DicomOutputStream dos = new DicomOutputStream(dcmFile);
        try {
            dos.writeDataset(fmi, attrs);
        } finally {
            dos.close();
        }
    }

    public static void jpeg(final Attributes attrs, File jpgFile, File dcmFile, boolean noAPPn) throws IOException {
        buildDicom(attrs, jpgFile, dcmFile, noAPPn, false);
    }

    public static void mpeg2(final Attributes attrs, File mpegFile, File dcmFile) throws IOException {
        buildDicom(attrs, mpegFile, dcmFile, false, true);
    }

    private static void buildDicom(final Attributes attrs, File jpgFile, File dcmFile, boolean noAPPn, boolean mpeg)
        throws IOException {
        Parameters p = new Parameters();
        p.fileLength = (int) jpgFile.length();

        try (DataInputStream jpgInput = new DataInputStream(new BufferedInputStream(new FileInputStream(jpgFile)));
                        DicomOutputStream dos = new DicomOutputStream(dcmFile);) {
            ensureString(attrs, Tag.SOPClassUID, VR.UI,
                mpeg ? UID.VideoPhotographicImageStorage : UID.VLPhotographicImageStorage);
            ensureString(attrs, Tag.TransferSyntaxUID, VR.UI, mpeg ? UID.MPEG2 : UID.JPEGBaseline1);

            ensureString(attrs, Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192");// UTF-8
            ensureUID(attrs, Tag.StudyInstanceUID);
            ensureUID(attrs, Tag.SeriesInstanceUID);
            ensureUID(attrs, Tag.SOPInstanceUID);

            readPixelHeader(p, attrs, jpgInput, mpeg);

            setCreationDate(attrs);
            Attributes fmi = attrs.createFileMetaInformation(attrs.getString(Tag.TransferSyntaxUID));

            dos.writeDataset(fmi, attrs);
            dos.writeHeader(Tag.PixelData, VR.OB, -1);
            dos.writeHeader(Tag.Item, null, 0);

            if (noAPPn && p.jpegHeader != null) {
                int off = p.jpegHeader.offsetAfterAPP();
                dos.writeHeader(Tag.Item, null, p.fileLength - off + 3);
                dos.write((byte) -1);
                dos.write((byte) JPEG.SOI);
                dos.write((byte) -1);
                dos.write(p.buffer, off, p.buffer.length -off);
            } else {
                dos.writeHeader(Tag.Item, null, (p.fileLength + 1) & ~1);
                dos.write(p.buffer, 0, p.buffer.length);
            }

            byte[] buf = new byte[FileUtil.FILE_BUFFER];
            int r;
            while ((r = jpgInput.read(buf)) > 0) {
                dos.write(buf, 0, r);
            }

            if ((p.fileLength & 1) != 0) {
                dos.write(0);
            }

            dos.writeHeader(Tag.SequenceDelimitationItem, null, 0);

        } catch (Exception e) {
            LOGGER.error("Building {}", mpeg ? "mpeg" : "jpg", e);
        }
    }

    private static void readPixelHeader(Parameters p, Attributes metadata, DataInputStream jpgInput, boolean mpeg)
        throws IOException {

        int bLength = p.buffer.length;
        StreamUtils.readAvailable(jpgInput, p.buffer, 0, bLength);
        if (mpeg) {
            MPEGHeader mpegHeader = new MPEGHeader(p.buffer);
            mpegHeader.toAttributes(metadata, p.fileLength);
        } else {
            p.jpegHeader = new JPEGHeader(p.buffer, JPEG.SOS);
            p.jpegHeader.toAttributes(metadata);
        }
    }

    private static void setCreationDate(Attributes attrs) {
        Date now = new Date();
        attrs.setDate(Tag.InstanceCreationDate, VR.DA, now);
        attrs.setDate(Tag.InstanceCreationTime, VR.TM, now);
    }

    private static void ensureString(Attributes attrs, int tag, VR vr, String value) {
        if (!attrs.containsValue(tag)) {
            attrs.setString(tag, vr, value);
        }
    }

    private static void ensureUID(Attributes attrs, int tag) {
        if (!attrs.containsValue(tag)) {
            attrs.setString(tag, VR.UI, UIDUtils.createUID());
        }
    }

    private static class Parameters {
        byte[] buffer = new byte[16384];
        int fileLength = 0;
        JPEGHeader jpegHeader;
    }
}
