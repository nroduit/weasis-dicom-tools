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
package org.weasis.dicom.web;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.util.DateTimeUtils;
import org.dcm4che6.util.UIDUtils;
import org.dcm4che6.xml.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.web.Multipart.ContentType;
import org.xml.sax.SAXException;

public class AbstractStowrs implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStowrs.class);
    /**
     * @see <a href="https://tools.ietf.org/html/rfc2387">multipart specifications</a>
     */
    protected static final String MULTIPART_BOUNDARY = "mimeTypeBoundary";

    private final List<HttpURLConnection> connections;
    private final ContentType contentType;
    private final String requestURL;
    private final String agentName;
    private final Map<String, String> headers;

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
    public AbstractStowrs(String requestURL, Multipart.ContentType contentType, String agentName,
        Map<String, String> headers) {
        this.contentType = Objects.requireNonNull(contentType);
        this.requestURL = Objects.requireNonNull(requestURL, "requestURL cannot be null");
        this.headers = headers;
        this.agentName = agentName;
        this.connections = new ArrayList<>();
    }

    protected HttpURLConnection buildConnection() throws IOException {
        try {

            URL url = new URL(requestURL);
            HttpURLConnection httpPost = (HttpURLConnection) url.openConnection();

            httpPost.setUseCaches(false);
            httpPost.setDoOutput(true);// indicates POST method
            httpPost.setDoInput(true);
            httpPost.setRequestMethod("POST");
            httpPost.setConnectTimeout(10000);
            httpPost.setReadTimeout(60000);
            httpPost.setRequestProperty("Content-Type", //$NON-NLS-1$
                Multipart.MULTIPART_RELATED + "; type=\"" + contentType + "\"; boundary=" + MULTIPART_BOUNDARY); //$NON-NLS-1$
            httpPost.setRequestProperty("User-Agent", agentName == null ? "Weasis STOWRS" : agentName);
            httpPost.setRequestProperty("Accept",
                contentType == ContentType.JSON ? ContentType.JSON.toString() : ContentType.XML.toString());

            if (headers != null && !headers.isEmpty()) {
                for (Entry<String, String> element : headers.entrySet()) {
                    httpPost.setRequestProperty(element.getKey(), element.getValue());
                }
            }
            connections.add(httpPost);
            return httpPost;

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
        out.write(Multipart.Separator.BOUNDARY.getType());
        out.writeBytes(MULTIPART_BOUNDARY);
        out.write(Multipart.Separator.STREAM.getType());
        out.flush();
        out.close();
    }

    protected void writeContentMarkers(DataOutputStream out) throws IOException {
        out.write(Multipart.Separator.BOUNDARY.getType());
        out.writeBytes(MULTIPART_BOUNDARY);
        out.write(Multipart.Separator.FIELD.getType());
        out.writeBytes("Content-Type: "); //$NON-NLS-1$
        out.writeBytes(contentType.toString());
        out.write(Multipart.Separator.HEADER.getType());
    }

    protected void writeEndMarkers(HttpURLConnection httpPost, DataOutputStream out, String iuid) throws IOException {
        endMarkers(out);

        int code = httpPost.getResponseCode();
        if (code == HttpURLConnection.HTTP_OK) {
            LOGGER.info("STOWRS server response message: HTTP Status-Code 200: OK for {}", iuid);//$NON-NLS-1$
        } else {
            throw new HttpServerErrorException(
                String.format("STOWRS server response message: %s", httpPost.getResponseMessage()));
        }
    }

    protected DicomObject writeEndMarkers(HttpURLConnection httpPost, DataOutputStream out)
        throws IOException, ParserConfigurationException, SAXException {
        endMarkers(out);

        int code = httpPost.getResponseCode();
        if (code == HttpURLConnection.HTTP_OK) {
            LOGGER.info("STOWRS server response message: HTTP Status-Code 200: OK for all the image set"); //$NON-NLS-1$
        } else if (code == HttpURLConnection.HTTP_ACCEPTED || code == HttpURLConnection.HTTP_CONFLICT) {
            LOGGER.warn("STOWRS server response message: HTTP Status-Code {}: {}", code, httpPost.getResponseMessage()); //$NON-NLS-1$
            DicomObject metadata = DicomObject.newDicomObject();
            // See http://dicom.nema.org/medical/dicom/current/output/chtml/part18/sect_6.6.html#table_6.6.1-1
            return SAXReader.parse(httpPost.getInputStream(), metadata);
        } else {
            throw new HttpServerErrorException(String.format("STOWRS server response message: HTTP Status-Code %d: %s",
                code, httpPost.getResponseMessage()));
        }
        return null;
    }

    protected static void ensureUID(DicomObject attrs, int tag) {
        if (attrs.get(tag).isEmpty()) {
            attrs.setString(tag, VR.UI, UIDUtils.randomUID());
        }
    }

    protected static void setEncapsulatedDocumentAttributes(Path bulkDataFile, DicomObject metadata, String mimeType) {
        metadata.setInt(Tag.InstanceNumber, VR.IS, 1);
        LocalDateTime dt = LocalDateTime.ofEpochSecond(bulkDataFile.toFile().lastModified(), 0, null);
        metadata.setString(Tag.ContentDate, VR.DA, DateTimeUtils.formatDA(dt));
        metadata.setString(Tag.ContentTime, VR.TM, DateTimeUtils.formatTM(dt));
        metadata.setString(Tag.AcquisitionDateTime, VR.DT, DateTimeUtils.formatDT(dt));
        metadata.setString(Tag.BurnedInAnnotation, VR.CS, "YES");
        metadata.setNull(Tag.DocumentTitle, VR.ST);
        metadata.setNull(Tag.ConceptNameCodeSequence, VR.SQ);
        metadata.setString(Tag.MIMETypeOfEncapsulatedDocument, VR.LO, mimeType);
    }

    protected String getContentLocation(DicomObject metadata) {
        if (metadata.get(Tag.EncapsulatedDocument).isPresent()) {
            return metadata.get(Tag.EncapsulatedDocument).get().bulkDataURI();
        }
        return null;
    }

    protected void removeConnection(HttpURLConnection httpPost) {
        connections.remove(httpPost);
    }

    @Override
    public void close() throws Exception {
        connections.forEach(HttpURLConnection::disconnect);
        connections.clear();
    }

    public ContentType getContentType() {
        return contentType;
    }

    public String getRequestURL() {
        return requestURL;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    protected TransformerHandler getTransformerHandler(URL url) throws TransformerConfigurationException {
        SAXTransformerFactory tf = (SAXTransformerFactory) TransformerFactory.newInstance();
        if (url == null)
            return tf.newTransformerHandler();

        TransformerHandler th = tf.newTransformerHandler(new StreamSource(url.toExternalForm()));
        return th;
    }

}