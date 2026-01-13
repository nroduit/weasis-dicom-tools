/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Interface for generating XML manifest documents for DICOM archive queries. Provides methods to
 * generate XML content and specify character encoding.
 */
public interface XmlManifest {

  /**
   * Generates an XML manifest string for the specified version.
   *
   * @param version the manifest version (e.g., "1", "2.5")
   * @return the XML manifest as a string, or null if generation fails
   */
  String xmlManifest(String version);

  /**
   * Returns the character encoding used for the XML manifest.
   *
   * @return the charset encoding name
   */
  default String getCharsetEncoding() {
    return StandardCharsets.UTF_8.name();
  }

  /**
   * Writes the XML manifest to the specified writer for the given version.
   *
   * @param writer the writer to output the XML manifest
   * @param version the manifest version
   * @throws IOException if an I/O error occurs during writing
   */
  void writeManifest(Writer writer, String version) throws IOException;
}
