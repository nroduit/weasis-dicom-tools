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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Parses multipart headers according to RFC 2822 format. */
public final class MultipartHeaderParser {

  private MultipartHeaderParser() {
    // Utility class
  }

  /**
   * Parses multipart headers from a header string.
   *
   * @param headerContent the raw header content
   * @return map of header names to values
   * @throws NullPointerException if headerContent is null
   */
  public static Map<String, String> parseHeaders(String headerContent) {
    Objects.requireNonNull(headerContent, "Header content cannot be null");

    Map<String, String> headers = new HashMap<>();
    String[] lines = headerContent.split("\r\n");

    String currentFieldName = null;
    StringBuilder currentField = new StringBuilder();

    for (String line : lines) {
      if (line.isEmpty()) break; // End of headers

      if (line.startsWith(" ") || line.startsWith("\t")) {
        // Continuation line
        if (currentFieldName != null) {
          currentField.append(" ").append(line.trim());
        }
      } else {
        // New field
        if (currentFieldName != null) {
          headers.merge(
              currentFieldName, currentField.toString(), (oldVal, newVal) -> oldVal + "," + newVal);
        }

        int colonIndex = line.indexOf(':');
        if (colonIndex > 0) {
          currentFieldName = line.substring(0, colonIndex).trim();
          currentField = new StringBuilder(line.substring(colonIndex + 1).trim());
        } else {
          currentFieldName = null;
          currentField.setLength(0);
        }
      }
    }

    if (currentFieldName != null) {
      headers.merge(
          currentFieldName, currentField.toString(), (oldVal, newVal) -> oldVal + "," + newVal);
    }

    return headers;
  }

  private static String combineExistingValue(
      Map<String, String> headers, String fieldName, String newValue) {
    String existingValue = headers.get(fieldName);
    return existingValue != null ? existingValue + "," + newValue : newValue;
  }
}
