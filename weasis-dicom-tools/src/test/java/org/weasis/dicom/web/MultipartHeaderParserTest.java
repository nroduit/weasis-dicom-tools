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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
final class MultipartHeaderParserTest {

  @Test
  void parseHeaders_with_null_input_throws_NullPointerException() {
    assertThrows(NullPointerException.class, () -> MultipartHeaderParser.parseHeaders(null));
  }

  @Test
  void parseHeaders_with_empty_string_returns_empty_map() {
    Map<String, String> result = MultipartHeaderParser.parseHeaders("");

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void parseHeaders_with_single_simple_header_returns_correct_map() {
    String headerContent = "Content-Type: text/plain\r\n";

    Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

    assertEquals(1, result.size());
    assertEquals("text/plain", result.get("Content-Type"));
  }

  @Test
  void parseHeaders_with_multiple_headers_returns_all_headers() {
    String headerContent =
        """
        Content-Type: multipart/related
        Content-Length: 1234
        Authorization: Bearer token123
        """
            .replace("\n", "\r\n");

    Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

    assertEquals(3, result.size());
    assertEquals("multipart/related", result.get("Content-Type"));
    assertEquals("1234", result.get("Content-Length"));
    assertEquals("Bearer token123", result.get("Authorization"));
  }

  @Test
  void parseHeaders_with_header_continuation_lines_combines_values() {
    String headerContent =
        """
        Content-Type: multipart/related;
         boundary=boundary123;
        \ttype="application/dicom"
        """
            .replace("\n", "\r\n");

    Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

    assertEquals(1, result.size());
    assertEquals(
        "multipart/related; boundary=boundary123; type=\"application/dicom\"",
        result.get("Content-Type"));
  }

  @Test
  void parseHeaders_with_multiple_same_header_names_combines_with_comma() {
    String headerContent =
        """
        Accept: text/plain
        Accept: application/json
        Accept: text/html
        """
            .replace("\n", "\r\n");

    Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

    assertEquals(1, result.size());
    assertEquals("text/plain,application/json,text/html", result.get("Accept"));
  }

  @Test
  void parseHeaders_stops_parsing_at_empty_line() {
    String headerContent =
        """
        Content-Type: text/plain
        Content-Length: 1234

        This should be ignored
        Another-Header: should not appear
        """
            .replace("\n", "\r\n");

    Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

    assertEquals(2, result.size());
    assertEquals("text/plain", result.get("Content-Type"));
    assertEquals("1234", result.get("Content-Length"));
    assertNull(result.get("Another-Header"));
  }

  @Nested
  class Edge_Cases {

    @Test
    void parseHeaders_with_header_without_colon_is_ignored() {
      String headerContent =
          """
          Content-Type: text/plain
          InvalidHeaderWithoutColon
          Content-Length: 1234
          """
              .replace("\n", "\r\n");

      Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

      assertEquals(2, result.size());
      assertEquals("text/plain", result.get("Content-Type"));
      assertEquals("1234", result.get("Content-Length"));
    }

    @Test
    void parseHeaders_with_header_starting_with_colon_is_ignored() {
      String headerContent =
          """
          Content-Type: text/plain
          : invalid header
          Content-Length: 1234
          """
              .replace("\n", "\r\n");

      Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

      assertEquals(2, result.size());
      assertEquals("text/plain", result.get("Content-Type"));
      assertEquals("1234", result.get("Content-Length"));
    }

    @Test
    void parseHeaders_with_empty_header_value_stores_empty_string() {
      String headerContent = "Content-Type:\r\n";

      Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

      assertEquals(1, result.size());
      assertEquals("", result.get("Content-Type"));
    }

    @Test
    void parseHeaders_with_whitespace_around_header_name_and_value_trims_correctly() {
      String headerContent = "Content-Type  :  text/plain  \r\n";

      Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

      assertEquals(1, result.size());
      assertEquals("text/plain", result.get("Content-Type"));
    }

    @Test
    void parseHeaders_with_continuation_line_without_previous_header_is_ignored() {
      String headerContent =
          """
           this is a continuation line without previous header
          Content-Type: text/plain
          """
              .replace("\n", "\r\n");

      Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

      assertEquals(1, result.size());
      assertEquals("text/plain", result.get("Content-Type"));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", " \t", "\t "})
    void parseHeaders_with_various_whitespace_continuation_markers_work(String whitespace) {
      String headerContent =
          "Content-Type: multipart/related;\r\n" + whitespace + "boundary=test\r\n";

      Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

      assertEquals(1, result.size());
      assertEquals("multipart/related; boundary=test", result.get("Content-Type"));
    }
  }

  @Nested
  class Real_World_Examples {

    @Test
    void parseHeaders_with_typical_multipart_headers() {
      String headerContent =
          """
          Content-Type: multipart/related;
           boundary="boundary123";
           type="application/dicom"
          Content-Length: 123456
          Transfer-Encoding: chunked
          """
              .replace("\n", "\r\n");

      Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

      assertEquals(3, result.size());
      assertEquals(
          "multipart/related; boundary=\"boundary123\"; type=\"application/dicom\"",
          result.get("Content-Type"));
      assertEquals("123456", result.get("Content-Length"));
      assertEquals("chunked", result.get("Transfer-Encoding"));
    }

    @Test
    void parseHeaders_with_http_request_headers() {
      String headerContent =
          """
          Host: example.com
          User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36
          Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
          Accept-Language: en-US,en;q=0.5
          Accept-Encoding: gzip, deflate
          Connection: keep-alive
          """
              .replace("\n", "\r\n");

      Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

      assertEquals(6, result.size());
      assertEquals("example.com", result.get("Host"));
      assertEquals(
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", result.get("User-Agent"));
      assertEquals(
          "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", result.get("Accept"));
      assertEquals("en-US,en;q=0.5", result.get("Accept-Language"));
      assertEquals("gzip, deflate", result.get("Accept-Encoding"));
      assertEquals("keep-alive", result.get("Connection"));
    }

    @Test
    void parseHeaders_with_complex_continuation_and_duplicate_headers() {
      String headerContent =
          """
          Content-Disposition: form-data;
           name="file";
          \tfilename="example.txt"
          Set-Cookie: session=abc123; Path=/
          Set-Cookie: preference=dark; Path=/user
          Cache-Control: no-cache,
           no-store,
          \tmust-revalidate
          """
              .replace("\n", "\r\n");

      Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

      assertEquals(3, result.size());
      assertEquals(
          "form-data; name=\"file\"; filename=\"example.txt\"", result.get("Content-Disposition"));
      assertEquals("session=abc123; Path=/,preference=dark; Path=/user", result.get("Set-Cookie"));
      assertEquals("no-cache, no-store, must-revalidate", result.get("Cache-Control"));
    }
  }

  @ParameterizedTest
  @MethodSource("provideSingleLineVariations")
  void parseHeaders_handles_single_line_variations_correctly(
      String input, String expectedKey, String expectedValue) {
    Map<String, String> result = MultipartHeaderParser.parseHeaders(input);

    assertEquals(1, result.size());
    assertEquals(expectedValue, result.get(expectedKey));
  }

  private static Stream<Arguments> provideSingleLineVariations() {
    return Stream.of(
        Arguments.of("Key:Value\r\n", "Key", "Value"),
        Arguments.of("Key: Value\r\n", "Key", "Value"),
        Arguments.of("Key :Value\r\n", "Key", "Value"),
        Arguments.of("Key : Value\r\n", "Key", "Value"),
        Arguments.of("Content-Type:application/json\r\n", "Content-Type", "application/json"),
        Arguments.of(
            "X-Custom-Header: custom value with spaces\r\n",
            "X-Custom-Header",
            "custom value with spaces"));
  }

  @Test
  void parseHeaders_with_only_crlf_returns_empty_map() {
    String headerContent = "\r\n";

    Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void parseHeaders_with_only_whitespace_continuation_lines_returns_empty_map() {
    String headerContent =
        """

        \t
         \t
        """
            .replace("\n", "\r\n");

    Map<String, String> result = MultipartHeaderParser.parseHeaders(headerContent);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }
}
