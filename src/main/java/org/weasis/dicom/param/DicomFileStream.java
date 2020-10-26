package org.weasis.dicom.param;

import java.util.Objects;
import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.img.data.TransferSyntaxType;
import org.dcm4che6.io.DicomEncoding;
import org.dcm4che6.io.DicomInputHandler;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.net.Association;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DicomFileStream implements Association.DataWriter, DicomInputHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomFileStream.class);

    private final Path path;
    private final AttributeEditorContext context;
    
    private long position = 0;
    private long length = 0;
    private String transferSyntax;
    private String sopClassUID;
    private String sopInstanceUID;

    public DicomFileStream(Path path) {
        this(path, null);
    }

    public DicomFileStream(Path path, AttributeEditorContext context) {
        this.path = Objects.requireNonNull(path);
        this.context = Objects.requireNonNull(context);
        if (Files.isRegularFile(path)) {
            try (DicomInputStream dis = new DicomInputStream(Files.newInputStream(path))) {
                DicomObject fmi = dis.readFileMetaInformation();
                if (fmi != null) {
                    this.sopClassUID = fmi.getStringOrElseThrow(Tag.MediaStorageSOPClassUID);
                    this.sopInstanceUID = fmi.getStringOrElseThrow(Tag.MediaStorageSOPInstanceUID);
                    this.transferSyntax = fmi.getStringOrElseThrow(Tag.TransferSyntaxUID);
                    this.position = dis.getStreamPosition();
                } else {
                    dis.withInputHandler(this).readDataSet();
                    this.transferSyntax = dis.getEncoding().transferSyntaxUID;
                }
                this.length = Files.size(path);
            } catch (org.dcm4che6.io.DicomParseException e) {
                LOGGER.warn(String.format("%s : %s", e.getMessage(), path));
            } catch (IOException e) {
                LOGGER.error("Scanning path issue", e);
            }
        }
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public String getTransferSyntax() {
        return transferSyntax;
    }

    public void setTransferSyntax(String transferSyntax) {
        this.transferSyntax = transferSyntax;
    }

    public String getSopClassUID() {
        return sopClassUID;
    }

    public void setSopClassUID(String sopClassUID) {
        this.sopClassUID = sopClassUID;
    }

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    public void setSopInstanceUID(String sopInstanceUID) {
        this.sopInstanceUID = sopInstanceUID;
    }

    public Path getPath() {
        return path;
    }

    public AttributeEditorContext getContext() {
        return context;
    }
    
    public List<AttributeEditor> getDicomEditors() {
        return context.getDestination().getDicomEditors();
    }

    public boolean isValid() {
        return length != 0 && sopInstanceUID != null && sopClassUID != null && transferSyntax != null;
    }

    @Override
    public void writeTo(OutputStream out, String tsuid) throws IOException {
        List<AttributeEditor> editors = context.getDestination().getDicomEditors();
        if (editors.isEmpty()) {
            try (InputStream in = Files.newInputStream(path)) {
                in.skipNBytes(position);
                in.transferTo(out);
            }
        } else {
            // .withBulkData(DicomInputStream::isBulkData)
            try (DicomInputStream dis = new DicomInputStream(Files.newInputStream(path))) {
                dis.withBulkData(DicomInputStream::isBulkData).withBulkDataURI(path);
                // Handle input stream with not random stream reading
                // Path tmp = Files.createTempFile("dcm", ".blk");
                // dis.spoolBulkDataTo(tmp); // delete file
                DicomObject data = dis.readDataSet();
                editors.forEach(e -> e.apply(data, context));
                sopInstanceUID = data.getString(Tag.SOPInstanceUID).orElse(null);
                sopClassUID = data.getString(Tag.SOPClassUID).orElse(null);

                // TODO handle transcoding
                TransferSyntaxType.forUID(tsuid);
                try (DicomOutputStream writer = new DicomOutputStream(out).withEncoding(DicomEncoding.of(tsuid))) {
                    writer.writeDataSet(data);
                    writer.writeHeader(Tag.ItemDelimitationItem, VR.NONE, 0);
                }
            } catch (IOException e) {
                LOGGER.error("Writing dataset", e);
            }
        }
    }

    @Override
    public boolean endElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
        switch (dcmElm.tag()) {
            case Tag.SOPInstanceUID:
                sopInstanceUID = dcmElm.stringValue(0).orElse(null);
                return false;
            case Tag.SOPClassUID:
                sopClassUID = dcmElm.stringValue(0).orElse(null);
        }
        return true;
    }
}
