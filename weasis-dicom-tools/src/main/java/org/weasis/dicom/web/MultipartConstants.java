/*
 * Copyright (c) 2019-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

/** Constants for multipart MIME processing. */
public final class MultipartConstants {

  public static final String CONTENT_TYPE = "Content-Type";
  public static final String MULTIPART_RELATED = "multipart/related";

  /** Carriage return, '\r', 0x0D, 13 in decimal */
  public static final byte CR = 0x0D;

  /** Line Feed, '\n', 0x0A, 10 in decimal */
  public static final byte LF = 0x0A;

  /** Dash, '-', 0x2D, 45 in decimal */
  public static final byte DASH = 0x2D;

  /** Multipart separator types. */
  public enum Separator {
    /** Header separator (CRLF CRLF) */
    HEADER(new byte[] {CR, LF, CR, LF}),

    /** Field separator (CRLF) */
    FIELD(new byte[] {CR, LF}),

    /** Boundary separator (CRLF --) */
    BOUNDARY(new byte[] {CR, LF, DASH, DASH}),

    /** Stream terminator (--) */
    STREAM(new byte[] {DASH, DASH});

    private final byte[] bytes;

    Separator(byte[] bytes) {
      this.bytes = bytes.clone();
    }

    public byte[] getBytes() {
      return bytes.clone();
    }

    @Override
    public String toString() {
      return new String(bytes);
    }
  }

  /** DICOM content types for multipart. */
  public enum DicomContentType {
    DICOM("application/dicom"),
    XML("application/dicom+xml"),
    JSON("application/dicom+json");

    private final String mimeType;

    DicomContentType(String mimeType) {
      this.mimeType = mimeType;
    }

    public String getMimeType() {
      return mimeType;
    }

    @Override
    public String toString() {
      return mimeType;
    }
  }

  private MultipartConstants() {
    // Utility class
  }
}
