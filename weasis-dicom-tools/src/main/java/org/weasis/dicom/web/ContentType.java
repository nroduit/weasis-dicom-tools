/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import static org.weasis.dicom.web.MultipartConstants.MULTIPART_RELATED;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Enumeration of supported DICOM content types for multipart/related operations. Used primarily in
 * DICOM web services for content type negotiation and validation.
 */
public enum ContentType {
  /** DICOM Part 10 file format (application/dicom) */
  APPLICATION_DICOM("application/dicom", -1);

  private final String type;
  private final int bulkdataTag;

  ContentType(String type, int bulkdataTag) {
    this.type = Objects.requireNonNull(type, "Content type cannot be null");
    this.bulkdataTag = bulkdataTag;
  }

  public String getType() {
    return type;
  }

  public int getBulkdataTag() {
    return bulkdataTag;
  }

  /**
   * Probes the content type of a file and returns the corresponding ContentType enum.
   *
   * @param path the file path to probe, must not be null
   * @return the corresponding ContentType
   * @throws UncheckedIOException if an I/O error occurs or content type cannot be determined
   * @throws UnsupportedOperationException if the detected content type is not supported
   * @throws NullPointerException if path is null
   */
  static ContentType probe(Path path) {
    Objects.requireNonNull(path, "Path cannot be null");
    try {
      String detectedType = Files.probeContentType(path);
      if (detectedType == null) {
        throw new IOException("Failed to determine content type of file: '%s'".formatted(path));
      }
      if (APPLICATION_DICOM.type.equalsIgnoreCase(detectedType)) {
        return APPLICATION_DICOM;
      }
      throw new UnsupportedOperationException(
          "Unsupported content type: '%s' of file: '%s'".formatted(detectedType, path));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Returns a string representation suitable for multipart/related Content-Type headers.
   *
   * @param boundary the boundary string for multipart content
   * @return formatted content type string
   */
  public String toMultipartContentType(String boundary) {
    Objects.requireNonNull(boundary, "Boundary cannot be null");
    return MULTIPART_RELATED + "; type=\"%s\";boundary=%s".formatted(type, boundary);
  }

  @Override
  public String toString() {
    return type;
  }
}
