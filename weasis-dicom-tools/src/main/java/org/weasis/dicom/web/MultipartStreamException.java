/*
 * Copyright (c) 2014-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import java.io.IOException;
import java.io.Serial;
import org.weasis.core.util.annotations.Generated;

/**
 * Exception thrown when errors occur during multipart stream processing in DICOM web services.
 * Indicates issues with multipart/related content parsing, boundary detection, or stream handling.
 */
@Generated
public class MultipartStreamException extends IOException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates a new exception with the specified error message.
   *
   * @param message the detailed error message
   */
  public MultipartStreamException(String message) {
    super(message);
  }

  /**
   * Creates a new exception with the specified message and underlying cause.
   *
   * @param message the detailed error message
   * @param cause the underlying cause of this exception
   */
  public MultipartStreamException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Factory method for boundary-related errors.
   *
   * @param boundary the problematic boundary string
   * @return new MultipartStreamException for boundary issues
   */
  public static MultipartStreamException boundaryError(String boundary) {
    return new MultipartStreamException("Invalid or missing boundary: " + boundary);
  }

  /**
   * Factory method for stream parsing errors.
   *
   * @param position the stream position where error occurred
   * @return new MultipartStreamException for parsing issues
   */
  public static MultipartStreamException parsingError(long position) {
    return new MultipartStreamException("Parsing error at stream position: " + position);
  }

  /**
   * Factory method for header size limit exceeded.
   *
   * @param actualSize the actual header size
   * @param maxSize the maximum allowed size
   * @return new MultipartStreamException for header size issues
   */
  public static MultipartStreamException headerSizeExceeded(int actualSize, int maxSize) {
    return new MultipartStreamException(
        "Header size %d exceeds maximum allowed size %d".formatted(actualSize, maxSize));
  }

  /**
   * Factory method for unexpected end of stream.
   *
   * @return new MultipartStreamException for premature stream end
   */
  public static MultipartStreamException unexpectedEndOfStream() {
    return new MultipartStreamException("Unexpected end of multipart stream");
  }
}
