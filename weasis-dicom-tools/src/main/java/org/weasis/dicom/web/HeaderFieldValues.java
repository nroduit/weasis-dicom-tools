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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.weasis.core.util.StringUtil;

/**
 * Parser for HTTP header field values supporting RFC 2616 syntax. Handles comma-separated values
 * with parameters, quoted strings, and escaping.
 */
public final class HeaderFieldValues {

  private final List<Map<String, String>> parsedValues;

  /**
   * Creates a parser for the given header field value string.
   *
   * @param headerFieldValue the header field value to parse
   * @throws NullPointerException if headerFieldValue is null
   */
  public HeaderFieldValues(String headerFieldValue) {
    Objects.requireNonNull(headerFieldValue, "Header field value cannot be null");
    this.parsedValues = parseHeaderFieldValue(headerFieldValue);
  }

  /**
   * Returns all parsed parameter maps.
   *
   * @return unmodifiable list of parameter maps
   */
  public List<Map<String, String>> getValues() {
    return Collections.unmodifiableList(parsedValues);
  }

  /**
   * Checks if any parameter map contains the specified key.
   *
   * @param key the parameter name to search for
   * @return true if the key is found in any parameter map
   */
  public boolean hasKey(String key) {
    return parsedValues.stream().anyMatch(map -> map.containsKey(key));
  }

  /**
   * Returns the first non-empty value for the specified key.
   *
   * @param key the parameter name to search for
   * @return the first non-empty value, or null if not found
   */
  public String getValue(String key) {
    return parsedValues.stream()
        .map(map -> map.get(key))
        .filter(StringUtil::hasText)
        .findFirst()
        .orElse(null);
  }

  /**
   * Returns all non-empty values for the specified key.
   *
   * @param key the parameter name to search for
   * @return list of all non-empty values for the key
   */
  public List<String> getValues(String key) {
    return parsedValues.stream()
        .map(map -> map.get(key))
        .filter(StringUtil::hasText)
        .collect(Collectors.toList());
  }

  /** Parses the header field value string into parameter maps. */
  private List<Map<String, String>> parseHeaderFieldValue(String content) {
    if (!StringUtil.hasText(content)) {
      return new ArrayList<>();
    }

    var parser = new FieldValueParser(content);
    return parser.parseAll();
  }

  /** Internal parser for handling the actual parsing logic. */
  private static class FieldValueParser {
    private char[] chars;
    private int position;
    private int start;
    private int end;

    FieldValueParser(String content) {
      this.chars = content.toCharArray();
      this.position = 0;
    }

    List<Map<String, String>> parseAll() {
      List<Map<String, String>> results = new ArrayList<>();

      // Split on commas outside of quotes
      String[] elements = splitPreservingQuotes();

      for (String element : elements) {
        Map<String, String> params = parseElement(element);
        if (!params.isEmpty()) {
          results.add(params);
        }
      }

      return results;
    }

    private String[] splitPreservingQuotes() {
      return new String(chars).split(",(?=(?:[^\"]*\"[^\"]*\")*+[^\"]*$)");
    }

    private Map<String, String> parseElement(String element) {
      Map<String, String> params = new HashMap<>();
      this.chars = element.toCharArray();
      this.position = 0;

      while (hasMoreCharacters()) {
        String name = parseValue();
        String value = null;

        if (hasMoreCharacters() && chars[position] == '=') {
          position++;
          value = parseQuotedValue();
        }

        skipSeparator();

        if (StringUtil.hasText(name)) {
          params.put(name.toLowerCase(), value);
        }
      }

      return params;
    }

    private boolean hasMoreCharacters() {
      return position < chars.length;
    }

    private String parseValue() {
      start = position;
      end = position;

      while (hasMoreCharacters()) {
        char c = chars[position];
        if (c == '=' || c == ';') {
          break;
        }
        end++;
        position++;
      }
      return extractValue(false);
    }

    private String parseQuotedValue() {
      start = position;
      end = position;

      boolean quoted = false;
      boolean escaped = false;

      while (hasMoreCharacters()) {
        char c = chars[position];
        if (!quoted && c == ';') {
          break;
        }
        if (!escaped && c == '"') {
          quoted = !quoted;
        }
        escaped = (!escaped && c == '\\');
        end++;
        position++;
      }
      return extractValue(true);
    }

    private String extractValue(boolean quoted) {
      // Remove leading and trailing whitespace
      while ((start < end) && Character.isWhitespace(chars[start])) {
        start++;
      }
      while ((end > start) && Character.isWhitespace(chars[end - 1])) {
        end--;
      }
      // Remove surrounding quotes if present
      if (quoted && (end - start) >= 2 && chars[start] == '"' && chars[end - 1] == '"') {
        start++;
        end--;
      }
      return end > start ? new String(chars, start, end - start) : null;
    }

    private void skipSeparator() {
      if (hasMoreCharacters() && chars[position] == ';') {
        position++;
      }
    }
  }
}
