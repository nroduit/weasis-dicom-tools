package org.weasis.dicom.web;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import javax.xml.parsers.ParserConfigurationException;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.util.DateUtils;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class AbstractStowrs implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStowrs.class);
    /**
     * @see <a href="https://tools.ietf.org/html/rfc2387">multipart specifications</a>
     */
    protected static final String MULTIPART_BOUNDARY = "mimeTypeBoundary";
    protected static final String BOUNDARY_PREFIX = "--";
    protected static final String CRLF = "\r\n";

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

    protected final ContentType contentType;
    protected final HttpURLConnection httpPost;

    /**
     * @param requestURL
     *            the URL of the STOW service
     * @param contentType
     *            the value of the type in the Content-Type HTTP property
     * @param agentName
     *            the value of the User-Agent HTTP property
     * @param headers
     *            some additional header properties.
     * @throws IOException
     *             Exception during the POST initialization
     */
    public AbstractStowrs(String requestURL, ContentType contentType, String agentName, Map<String, String> headers)
        throws IOException {
        try {
            this.contentType = Objects.requireNonNull(contentType);
            URL url = new URL(requestURL);
            httpPost = (HttpURLConnection) url.openConnection();

            httpPost.setUseCaches(false);
            httpPost.setDoOutput(true);// indicates POST method
            httpPost.setDoInput(true);
            httpPost.setRequestMethod("POST");
            httpPost.setConnectTimeout(10000);
            httpPost.setReadTimeout(60000);
            httpPost.setRequestProperty("Content-Type", //$NON-NLS-1$
                "multipart/related; type=\"" + contentType + "\"; boundary=" + MULTIPART_BOUNDARY); //$NON-NLS-1$
            httpPost.setRequestProperty("User-Agent", agentName == null ? "Weasis STOWRS" : agentName);
            httpPost.setRequestProperty("Accept",
                contentType == ContentType.JSON ? ContentType.JSON.toString() : ContentType.XML.toString());

            if (headers != null && !headers.isEmpty()) {
                for (Iterator<Entry<String, String>> iter = headers.entrySet().iterator(); iter.hasNext();) {
                    Entry<String, String> element = iter.next();
                    httpPost.setRequestProperty(element.getKey(), element.getValue());
                }
            }

        } catch (IOException e) {
            try {
                close();
            } catch (Exception e1) {
                // Do nothing
            }
            throw e;
        }
    }

    private void endMarkers(DataOutputStream out) throws IOException {
        // Final part segment
        out.writeBytes(CRLF);
        out.writeBytes(BOUNDARY_PREFIX);
        out.writeBytes(MULTIPART_BOUNDARY);
        out.writeBytes(BOUNDARY_PREFIX);
        out.flush();
        out.close();
    }

    protected void writeContentMarkers(DataOutputStream out) throws IOException {
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

    protected void writeEndMarkers(DataOutputStream out, String iuid) throws IOException {
        endMarkers(out);

        int code = httpPost.getResponseCode();
        if (code == HttpURLConnection.HTTP_OK) {
            LOGGER.info("STOWRS server response message: HTTP Status-Code 200: OK for {}", iuid);//$NON-NLS-1$
        } else {
            throw new HttpServerErrorException(
                String.format("STOWRS server response message: %s", httpPost.getResponseMessage()));
        }
    }

    protected Attributes writeEndMarkers(DataOutputStream out) throws IOException, ParserConfigurationException, SAXException {
        endMarkers(out);

        int code = httpPost.getResponseCode();
        if (code == HttpURLConnection.HTTP_OK) {
            LOGGER.info("STOWRS server response message: HTTP Status-Code 200: OK for all the image set"); //$NON-NLS-1$
        } else if (code == HttpURLConnection.HTTP_ACCEPTED || code == HttpURLConnection.HTTP_CONFLICT) {
            LOGGER.warn("STOWRS server response message: HTTP Status-Code {}: {}", code, httpPost.getResponseMessage()); //$NON-NLS-1$
            // See http://dicom.nema.org/medical/dicom/current/output/chtml/part18/sect_6.6.html#table_6.6.1-1
            return SAXReader.parse(httpPost.getInputStream());
        } else {
            throw new HttpServerErrorException(
                String.format("STOWRS server response message: HTTP Status-Code %d: %s", code, httpPost.getResponseMessage()));
        }
        return null;
    }

    protected String getDicomState(DataOutputStream out) throws IOException {

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpPost.getInputStream()))) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    protected static void ensureUID(Attributes attrs, int tag) {
        if (!attrs.containsValue(tag)) {
            attrs.setString(tag, VR.UI, UIDUtils.createUID());
        }
    }

    protected static void setEncapsulatedDocumentAttributes(Path bulkDataFile, Attributes metadata, String mimeType) {
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

    protected String getContentLocation(Attributes metadata) {
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

    @Override
    public void close() throws Exception {
        Optional.ofNullable(httpPost).ifPresent(HttpURLConnection::disconnect);
    }

}