package org.weasis.dicom.web;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.xml.transform.stream.StreamResult;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.json.JSONWriter;
import org.weasis.core.api.util.FileUtil;

public class StowrsSingleFile extends AbstractStowrs {

    public StowrsSingleFile(String requestURL, ContentType contentType) throws IOException {
        this(requestURL, contentType, null, null);
    }

    public StowrsSingleFile(String requestURL, ContentType contentType, String agentName, Map<String, String> headers)
        throws IOException {
        super(requestURL, contentType, agentName, headers);
    }

    public void uploadDicom(InputStream in, Attributes fmi, String tsuid, String iuid) throws IOException {
        try (DataOutputStream out = new DataOutputStream(httpPost.getOutputStream());
                        DicomOutputStream dos = new DicomOutputStream(out, tsuid)) {
            writeContentMarkers(out);
            dos.writeFileMetaInformation(fmi);

            byte[] buf = new byte[FileUtil.FILE_BUFFER];
            int offset;
            while ((offset = in.read(buf)) > 0) {
                dos.write(buf, 0, offset);
            }
            writeEndMarkers(out, iuid);
        }
    }

    public void uploadDicom(Attributes metadata, String tsuid) throws IOException {
        try (DataOutputStream out = new DataOutputStream(httpPost.getOutputStream());
                        DicomOutputStream dos = new DicomOutputStream(out, tsuid)) {
            writeContentMarkers(out);
            Attributes fmi = metadata.createFileMetaInformation(tsuid);
            dos.writeDataset(fmi, metadata);
            writeEndMarkers(out, metadata.getString(Tag.SOPInstanceUID));
        }
    }

    public void uploadEncapsulatedDocument(Attributes metadata, File bulkDataFile, String mimeType, String sopClassUID) throws Exception {
        setEncapsulatedDocumentAttributes(bulkDataFile.toPath(), metadata, mimeType);
        if (metadata.getValue(Tag.EncapsulatedDocument) == null) {
            metadata.setValue(Tag.EncapsulatedDocument, VR.OB, new BulkData(null, "bulk", false));
        }
        metadata.setValue(Tag.SOPClassUID, VR.UI, sopClassUID);
        ensureUID(metadata, Tag.StudyInstanceUID);
        ensureUID(metadata, Tag.SeriesInstanceUID);
        ensureUID(metadata, Tag.SOPInstanceUID);

        try (ByteArrayOutputStream bOut = new ByteArrayOutputStream();
                        DataOutputStream out = new DataOutputStream(httpPost.getOutputStream())) {
            if (contentType == ContentType.JSON)
                try (JsonGenerator gen = Json.createGenerator(bOut)) {
                    new JSONWriter(gen).write(metadata);
                }
            else {
                SAXTransformer.getSAXWriter(new StreamResult(bOut)).write(metadata);
            }

            writeContentMarkers(out);

            out.write(bOut.toByteArray());

            // Segment for a part
            out.writeBytes(CRLF);
            out.writeBytes(BOUNDARY_PREFIX);
            out.writeBytes(MULTIPART_BOUNDARY);
            out.writeBytes(CRLF);
            out.writeBytes("Content-Type: "); //$NON-NLS-1$
            out.writeBytes(mimeType);
            out.writeBytes(CRLF);
            out.writeBytes("Content-Location: "); //$NON-NLS-1$
            out.writeBytes(getContentLocation(metadata));
            // Two CRLF before content
            out.writeBytes(CRLF);
            out.writeBytes(CRLF);

            Files.copy(bulkDataFile.toPath(), out);

            writeEndMarkers(out, metadata.getString(Tag.SOPInstanceUID));
        }
    }
}
