/*
 * Copyright (c) 2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import static org.weasis.dicom.web.MultipartConstants.CONTENT_TYPE;

import java.io.InputStream;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.annotations.Generated;

/**
 * Represents a single part in a multipart message. Contains the content type, optional location,
 * and payload data.
 */
public record MultipartPart(String contentType, String location, Payload payload) {

  private static final Logger LOGGER = LoggerFactory.getLogger(MultipartPart.class);

  public MultipartPart {
    Objects.requireNonNull(contentType, "Content type cannot be null");
    Objects.requireNonNull(payload, "Payload cannot be null");
  }

  /**
   * Creates a new input stream for reading this part's data.
   *
   * @return a new input stream positioned at the beginning of the data
   */
  public InputStream newInputStream() {
    return payload.newInputStream();
  }

  /**
   * Generates the MIME header for this part.
   *
   * @param boundary the multipart boundary string
   * @return the complete header string including CRLF sequences
   */
  public String generateHeader(String boundary) {
    Objects.requireNonNull(boundary, "Boundary cannot be null");

    var header =
        new StringBuilder(256)
            .append("\r\n--")
            .append(boundary)
            .append("\r\n")
            .append(CONTENT_TYPE)
            .append(": ")
            .append(contentType);

    long size = payload.size();
    if (size < 0) {
      header.append("\r\nContent-Encoding: gzip, identity");
    } else {
      header.append("\r\nContent-Length: ").append(size);
    }

    if (location != null && !location.isEmpty()) {
      header.append("\r\nContent-Location: ").append(location);
    }

    return header.append("\r\n\r\n").toString();
  }

  /** Logs debug information about this part. */
  @Generated
  public void logDebugInfo(String boundary) {
    if (!LOGGER.isDebugEnabled()) {
      return;
    }

    LOGGER.debug("> --{}", boundary);
    LOGGER.debug("> Content-Type: {}", contentType);

    long size = payload.size();
    if (size < 0) {
      LOGGER.debug("> Content-Encoding: gzip, identity");
    } else {
      LOGGER.debug("> Content-Length: {}", size);
    }

    if (location != null) {
      LOGGER.debug("> Content-Location: {}", location);
    }

    LOGGER.debug(">");
    LOGGER.debug("> [payload data]");
  }
}
