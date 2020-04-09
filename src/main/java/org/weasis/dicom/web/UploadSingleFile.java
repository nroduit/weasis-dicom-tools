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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.dcm4che6.data.DicomObject;


public interface UploadSingleFile extends AutoCloseable {

    void uploadDicom(InputStream in, DicomObject fmi, String tsuid, String iuid) throws IOException;

    void uploadDicom(DicomObject metadata, String tsuid) throws IOException;

    void uploadEncapsulatedDocument(DicomObject metadata, File bulkDataFile, String mimeType, String sopClassUID)
        throws Exception;

    String getRequestURL();

    Multipart.ContentType getContentType();

    Map<String, String> getHeaders();
}