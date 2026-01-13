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

import java.nio.charset.StandardCharsets;
import org.weasis.core.util.StringUtil;

/** Utility for extracting boundary values from multipart content type headers. */
public final class BoundaryExtractor {

  private BoundaryExtractor() {
    // Utility class
  }

  /**
   * Extracts boundary from a content type header value.
   *
   * @param contentType the content type header value
   * @param requiredType the required multipart type (e.g., "multipart/related")
   * @return the boundary bytes, or null if not found or type doesn't match
   */
  public static byte[] extractBoundary(String contentType, String requiredType) {
    if (!StringUtil.hasText(contentType)) {
      return null;
    }

    var parser = new HeaderFieldValues(contentType);
    String boundaryValue = parser.getValue("boundary");

    if (boundaryValue == null) {
      return null;
    }

    if (requiredType != null && !hasRequiredType(parser, requiredType)) {
      return null;
    }

    return boundaryValue.getBytes(StandardCharsets.ISO_8859_1);
  }

  private static boolean hasRequiredType(HeaderFieldValues parser, String requiredType) {
    return parser.hasKey(requiredType)
        || parser.getValues().stream().anyMatch(map -> map.containsValue(requiredType));
  }
}
