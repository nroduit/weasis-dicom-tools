/*
 * Copyright (c) 2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.dcm4che3.data.Attributes;

public interface UploadSingleFile extends AutoCloseable {

  void uploadDicom(InputStream in, Attributes fmi, String tsuid, String iuid) throws IOException;

  void uploadDicom(Attributes metadata, String tsuid) throws IOException;

  void uploadEncapsulatedDocument(
      Attributes metadata, File bulkDataFile, String mimeType, String sopClassUID) throws Exception;

  String getRequestURL();

  Multipart.ContentType getContentType();

  Map<String, String> getHeaders();
}
