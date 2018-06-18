package org.weasis.dicom.web;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
import org.dcm4che3.util.DateUtils;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.FileUtil;

public class StowRS implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StowRS.class);

    /**
     * @see <a href="https://tools.ietf.org/html/rfc2387">multipart specifications</a>
     */
    private static final String MULTIPART_BOUNDARY = "mimeTypeBoundary"; //$NON-NLS-1$
    private static final String BOUNDARY_PREFIX = "--"; //$NON-NLS-1$
    private static final String CRLF = "\r\n"; //$NON-NLS-1$

    public enum ContentType {
        DICOM("application/dicom"), XML("application/dicom+xml"), JSON("application/dicom+json");

        private final String type;

        private ContentType(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return type;
        }
    }

    private final ContentType contentType;
    private final HttpURLConnection httpPost;
    private final DataOutputStream out;

    public StowRS(String requestURL, ContentType contentType) throws IOException {
        this(requestURL, contentType, null);
    }

    public StowRS(String requestURL, ContentType contentType, String agentName) throws IOException {
        try {
            this.contentType = Objects.requireNonNull(contentType);
            URL url = new URL(requestURL);
            httpPost = (HttpURLConnection) url.openConnection();

            httpPost.setUseCaches(false);
            httpPost.setDoOutput(true);// indicates POST method
            httpPost.setDoInput(true);
            httpPost.setRequestMethod("POST");
            httpPost.setConnectTimeout(5000);
            httpPost.setReadTimeout(5000);
            httpPost.setRequestProperty("Content-Type", //$NON-NLS-1$
                "multipart/related; type=" + contentType + "; boundary=" + MULTIPART_BOUNDARY); //$NON-NLS-1$
            httpPost.setRequestProperty("User-Agent", agentName == null ? "Weasis STOWRS" : agentName);
            httpPost.setRequestProperty("Accept",
                contentType == ContentType.JSON ? ContentType.JSON.toString() : ContentType.XML.toString());

            out = new DataOutputStream(httpPost.getOutputStream());
        } catch (IOException e) {
            try {
                close();
            } catch (Exception e1) {
                // Do nothing
            }
            throw e;
        }
    }

    public String uploadDicom(List<String> filesOrFolders, boolean recursive) {
        try {
            for (String entry : filesOrFolders) {
                File file = new File(entry);
                if (file.isDirectory()) {
                    List<File> fileList = new ArrayList<>();
                    FileUtil.getAllFilesInDirectory(file, fileList, recursive);
                    for (File f : fileList) {
                        uploadFile(f);
                    }
                } else {
                    uploadFile(file);
                }
            }

            return writeEndMarkers();
        } catch (Exception e) {
            LOGGER.error("STOWRS: error when posting data", e); //$NON-NLS-1$
        } finally {
            Optional.ofNullable(httpPost).ifPresent(HttpURLConnection::disconnect);
        }
        return null;
    }

    private void uploadFile(File file) throws IOException {
        writeContentMarkers();

        // write dicom binary file
        Files.copy(file.toPath(), out);
    }

    private void writeContentMarkers() throws IOException {
        out.writeBytes(CRLF);
        out.writeBytes(BOUNDARY_PREFIX);
        out.writeBytes(MULTIPART_BOUNDARY);
        out.writeBytes(CRLF);
        out.writeBytes("Content-Type: "); //$NON-NLS-1$
        out.writeBytes(contentType.toString());
        // Two CRLF before content
        out.writeBytes(CRLF);
        out.writeBytes(CRLF);
    }

    public String writeEndMarkers() throws IOException {
        // Final part segment
        out.writeBytes(CRLF);
        out.writeBytes(BOUNDARY_PREFIX);
        out.writeBytes(MULTIPART_BOUNDARY);
        out.writeBytes(BOUNDARY_PREFIX);
        out.flush();
        out.close();

        LOGGER.info("STOWRS: server response: {}", httpPost.getResponseMessage()); //$NON-NLS-1$
        if (httpPost.getResponseCode() == HttpURLConnection.HTTP_ACCEPTED) { // Failed or Warning
            // See http://dicom.nema.org/medical/dicom/current/output/chtml/part18/sect_6.6.html#table_6.6.1-1
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpPost.getInputStream()))) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        }
        return null;
    }

    public void uploadDicom(InputStream in, Attributes fmi, String tsuid) throws IOException {
        writeContentMarkers();
        DicomOutputStream dos = new DicomOutputStream(out, tsuid);
        dos.writeFileMetaInformation(fmi);

        byte[] buf = new byte[FileUtil.FILE_BUFFER];
        int offset;
        while ((offset = in.read(buf)) > 0) {
            dos.write(buf, 0, offset);
        }
        dos.flush();
    }

    public void uploadDicom(Attributes metadata, String tsuid) throws IOException {
        writeContentMarkers();
        Attributes fmi = metadata.createFileMetaInformation(tsuid);
        // DicomOutputStream does not to be closed and out must be kept open
        DicomOutputStream dos = new DicomOutputStream(out, tsuid);
        dos.writeDataset(fmi, metadata);
        dos.flush();
    }

    public void uploadEncapsulatedDocument(Attributes metadata, File bulkDataFile, String mimeType, String sopClassUID)
        throws Exception {
        setEncapsulatedDocumentAttributes(bulkDataFile.toPath(), metadata, mimeType);
        if (metadata.getValue(Tag.EncapsulatedDocument) == null) {
            metadata.setValue(Tag.EncapsulatedDocument, VR.OB, new BulkData(null, "bulk", false));
        }
        metadata.setValue(Tag.SOPClassUID, VR.UI, sopClassUID);
        ensureUID(metadata, Tag.StudyInstanceUID);
        ensureUID(metadata, Tag.SeriesInstanceUID);
        ensureUID(metadata, Tag.SOPInstanceUID);

        try (ByteArrayOutputStream bOut = new ByteArrayOutputStream()) {
            if (contentType == ContentType.JSON)
                try (JsonGenerator gen = Json.createGenerator(bOut)) {
                    new JSONWriter(gen).write(metadata);
                }
            else {
                SAXTransformer.getSAXWriter(new StreamResult(bOut)).write(metadata);
            }
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
        }
    }

    private static void ensureUID(Attributes attrs, int tag) {
        if (!attrs.containsValue(tag)) {
            attrs.setString(tag, VR.UI, UIDUtils.createUID());
        }
    }

    private String getContentLocation(Attributes metadata) {
        BulkData data = ((BulkData) metadata.getValue(Tag.EncapsulatedDocument));
        if (data != null) {
            return data.getURI();
        }

        data = ((BulkData) metadata.getValue(Tag.PixelData));
        if (data != null) {
            return data.getURI();
        }
        return null;
    }

    private static void setEncapsulatedDocumentAttributes(Path bulkDataFile, Attributes metadata, String mimeType) {
        metadata.setInt(Tag.InstanceNumber, VR.IS, 1);
        metadata.setString(Tag.ContentDate, VR.DA,
            DateUtils.formatDA(null, new Date(bulkDataFile.toFile().lastModified())));
        metadata.setString(Tag.ContentTime, VR.TM,
            DateUtils.formatTM(null, new Date(bulkDataFile.toFile().lastModified())));
        metadata.setString(Tag.AcquisitionDateTime, VR.DT,
            DateUtils.formatTM(null, new Date(bulkDataFile.toFile().lastModified())));
        metadata.setString(Tag.BurnedInAnnotation, VR.CS, "YES");
        metadata.setNull(Tag.DocumentTitle, VR.ST);
        metadata.setNull(Tag.ConceptNameCodeSequence, VR.SQ);
        metadata.setString(Tag.MIMETypeOfEncapsulatedDocument, VR.LO, mimeType);
    }

    @Override
    public void close() throws Exception {
        Optional.ofNullable(httpPost).ifPresent(HttpURLConnection::disconnect);
        FileUtil.safeClose(out);
    }

}
