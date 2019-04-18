package org.weasis.dicom.web;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.dcm4che3.data.Attributes;
import org.weasis.dicom.web.AbstractStowrs.ContentType;

public interface UploadSingleFile extends AutoCloseable {

    void uploadDicom(InputStream in, Attributes fmi, String tsuid, String iuid) throws IOException;

    void uploadDicom(Attributes metadata, String tsuid) throws IOException;

    void uploadEncapsulatedDocument(Attributes metadata, File bulkDataFile, String mimeType, String sopClassUID)
        throws Exception;

    String getRequestURL();

    ContentType getContentType();

    Map<String, String> getHeaders();
}