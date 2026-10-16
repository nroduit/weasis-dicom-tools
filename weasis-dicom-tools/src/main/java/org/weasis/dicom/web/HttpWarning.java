/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A value of the HTTP {@code Warning} header (RFC 7234 §5.5), which DICOMweb origin servers use to
 * report how a request was handled (PS3.18 §8.6.1.2).
 *
 * @param code the warn-code, {@code 299} for the DICOMweb warnings
 * @param agent the warn-agent, the host or pseudonym of the server
 * @param text the warn-text, unquoted
 */
public record HttpWarning(int code, String agent, String text) {

  /** Miscellaneous persistent warning, the code of the PS3.18 warnings. */
  public static final int MISCELLANEOUS_PERSISTENT = 299;

  public HttpWarning {
    Objects.requireNonNull(agent, "Agent cannot be null");
    Objects.requireNonNull(text, "Text cannot be null");
  }

  /** Parses every header value, each of which may hold a comma-separated list of warnings. */
  public static List<HttpWarning> parse(Collection<String> headerValues) {
    List<HttpWarning> warnings = new ArrayList<>();
    if (headerValues != null) {
      headerValues.forEach(v -> warnings.addAll(parse(v)));
    }
    return warnings;
  }

  /**
   * Parses a header value holding a comma-separated list of warnings. A warn-text without quotes
   * runs to the end of the value.
   */
  public static List<HttpWarning> parse(String headerValue) {
    List<HttpWarning> warnings = new ArrayList<>();
    if (headerValue == null) {
      return warnings;
    }
    Cursor c = new Cursor(headerValue);
    while (c.skip(" \t,")) {
      String code = c.token();
      c.skip(" \t");
      String agent = c.token();
      c.skip(" \t");
      String text = c.peek() == '"' ? c.quoted() : c.rest();
      c.skip(" \t");
      if (c.peek() == '"') {
        c.quoted(); // warn-date
      }
      warnings.add(new HttpWarning(parseCode(code), agent, text));
    }
    return warnings;
  }

  private static int parseCode(String code) {
    try {
      return Integer.parseInt(code);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static final class Cursor {
    private final String value;
    private int pos;

    Cursor(String value) {
      this.value = value;
    }

    char peek() {
      return pos < value.length() ? value.charAt(pos) : 0;
    }

    /** Skips the given characters and returns whether input remains. */
    boolean skip(String chars) {
      while (pos < value.length() && chars.indexOf(value.charAt(pos)) >= 0) {
        pos++;
      }
      return pos < value.length();
    }

    String token() {
      int start = pos;
      while (pos < value.length() && " \t,\"".indexOf(value.charAt(pos)) < 0) {
        pos++;
      }
      return value.substring(start, pos);
    }

    String quoted() {
      StringBuilder buf = new StringBuilder();
      pos++;
      while (pos < value.length() && value.charAt(pos) != '"') {
        char ch = value.charAt(pos++);
        if (ch == '\\' && pos < value.length()) {
          ch = value.charAt(pos++);
        }
        buf.append(ch);
      }
      pos++;
      return buf.toString();
    }

    String rest() {
      String text = value.substring(pos).trim();
      pos = value.length();
      return text;
    }
  }
}
