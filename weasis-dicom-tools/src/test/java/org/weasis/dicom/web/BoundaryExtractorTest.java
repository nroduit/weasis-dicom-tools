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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class BoundaryExtractorTest {

  private static final String MULTIPART_RELATED = "multipart/related";
  private static final String MULTIPART_MIXED = "multipart/mixed";
  private static final String SAMPLE_BOUNDARY = "----=_NextPart_01D98B23.45678901";

  @Nested
  class Valid_Boundary_Extraction_Tests {

    @Test
    void should_extract_simple_boundary() {
      String contentType = "multipart/related; boundary=" + SAMPLE_BOUNDARY;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals(SAMPLE_BOUNDARY, new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void should_extract_quoted_boundary() {
      String quotedBoundary = "\"" + SAMPLE_BOUNDARY + "\"";
      String contentType = "multipart/related; boundary=" + quotedBoundary;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals(SAMPLE_BOUNDARY, new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void should_extract_boundary_with_type_parameter() {
      String contentType =
          "multipart/related; type=\"application/dicom\"; boundary=" + SAMPLE_BOUNDARY;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals(SAMPLE_BOUNDARY, new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void should_extract_boundary_with_charset_parameter() {
      String contentType = "multipart/related; charset=UTF-8; boundary=" + SAMPLE_BOUNDARY;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals(SAMPLE_BOUNDARY, new String(result, StandardCharsets.ISO_8859_1));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "multipart/related;boundary=simple123",
          "multipart/related; boundary=simple123",
          "multipart/related;  boundary=simple123",
          "multipart/related;boundary=\"quoted123\"",
          "multipart/related; boundary=\"quoted123\"",
          "MULTIPART/RELATED; BOUNDARY=UPPERCASE123"
        })
    void should_handle_various_boundary_formats(String contentType) {
      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertTrue(result.length > 0);
    }

    @Test
    void should_extract_complex_boundary_with_special_characters() {
      String complexBoundary = "----=_Part_0_1234567890.0987654321";
      String contentType = "multipart/related; boundary=" + complexBoundary;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals(complexBoundary, new String(result, StandardCharsets.ISO_8859_1));
    }
  }

  @Nested
  class Required_Type_Validation_Tests {

    @Test
    void should_accept_matching_required_type() {
      String contentType = "multipart/related; boundary=" + SAMPLE_BOUNDARY;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals(SAMPLE_BOUNDARY, new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void should_reject_non_matching_required_type() {
      String contentType = "multipart/mixed; boundary=" + SAMPLE_BOUNDARY;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNull(result);
    }

    @Test
    void should_accept_when_required_type_is_null() {
      String contentType = "multipart/mixed; boundary=" + SAMPLE_BOUNDARY;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, null);

      assertNotNull(result);
      assertEquals(SAMPLE_BOUNDARY, new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void should_find_required_type_in_parameter_values() {
      String contentType =
          "multipart/related; type=\"" + MULTIPART_RELATED + "\"; boundary=" + SAMPLE_BOUNDARY;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals(SAMPLE_BOUNDARY, new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void should_handle_case_insensitive_type_matching() {
      String contentType = "MULTIPART/RELATED; boundary=" + SAMPLE_BOUNDARY;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, "multipart/related");

      // Note: This depends on the HeaderFieldValues implementation behavior
      // The test validates current behavior - adjust if case sensitivity is implemented
      assertNotNull(result);
    }
  }

  @Nested
  class Invalid_Input_Handling_Tests {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void should_return_null_for_invalid_content_type(String contentType) {
      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNull(result);
    }

    @Test
    void should_return_null_when_boundary_parameter_missing() {
      String contentType = "multipart/related; type=\"application/dicom\"";

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNull(result);
    }

    @Test
    void should_return_null_when_boundary_value_empty() {
      String contentType = "multipart/related; boundary=";

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNull(result);
    }

    @Test
    void should_return_null_when_boundary_value_whitespace_only() {
      String contentType = "multipart/related; boundary=\"   \"";

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      // This depends on HeaderFieldValues parsing behavior
      // May return whitespace or null depending on implementation
      assertNull(result); // Adjust based on actual behavior
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/json", "image/jpeg", "not-a-content-type"})
    void should_return_null_for_non_multipart_content_types(String contentType) {
      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNull(result);
    }
  }

  @Nested
  class Boundary_Encoding_Tests {

    @Test
    void should_use_iso_8859_1_encoding() {
      String boundary = "test-boundary-123";
      String contentType = "multipart/related; boundary=" + boundary;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);
      byte[] expected = boundary.getBytes(StandardCharsets.ISO_8859_1);

      assertNotNull(result);
      assertArrayEquals(expected, result);
    }

    @Test
    void should_handle_boundary_with_extended_ascii_characters() {
      // Extended ASCII characters that differ between UTF-8 and ISO-8859-1
      String boundary = "boundary-àáâãäå";
      String contentType = "multipart/related; boundary=" + boundary;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      // Verify it uses ISO-8859-1 encoding, not UTF-8
      assertArrayEquals(boundary.getBytes(StandardCharsets.ISO_8859_1), result);
    }
  }

  @Nested
  class Real_World_Content_Type_Tests {

    static Stream<Arguments> realWorldContentTypes() {
      return Stream.of(
          // Standard DICOM STOW-RS content types
          Arguments.of(
              "multipart/related; type=\"application/dicom\"; boundary=----=_NextPart_01D98B23.45678901",
              MULTIPART_RELATED,
              "----=_NextPart_01D98B23.45678901"),
          // Content type with multiple parameters
          Arguments.of(
              "multipart/related; type=\"application/dicom\"; boundary=\"simple-boundary\"; charset=UTF-8",
              MULTIPART_RELATED,
              "simple-boundary"),
          // Content type from common HTTP libraries
          Arguments.of(
              "multipart/related;boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW",
              MULTIPART_RELATED,
              "----WebKitFormBoundary7MA4YWxkTrZu0gW"),
          // Mixed case parameters
          Arguments.of(
              "multipart/related; Type=\"application/dicom\"; Boundary=mixed-case-boundary",
              MULTIPART_RELATED,
              "mixed-case-boundary"),
          // Parameters in different order
          Arguments.of(
              "multipart/related; boundary=order-test; type=\"application/dicom\"",
              MULTIPART_RELATED,
              "order-test"));
    }

    @ParameterizedTest
    @MethodSource("realWorldContentTypes")
    void should_handle_real_world_content_types(
        String contentType, String requiredType, String expectedBoundary) {
      byte[] result = BoundaryExtractor.extractBoundary(contentType, requiredType);

      assertNotNull(result, "Should extract boundary from: " + contentType);
      assertEquals(expectedBoundary, new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void should_handle_content_type_from_apache_http_client() {
      String contentType =
          "multipart/related; type=application/dicom; boundary=apache-mime-boundary-123456789";

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals(
          "apache-mime-boundary-123456789", new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void should_handle_content_type_with_additional_attributes() {
      String contentType =
          "multipart/related; type=\"application/dicom\"; start=\"<rootpart>\"; "
              + "start-info=\"application/dicom\"; boundary=complex-boundary-test";

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals("complex-boundary-test", new String(result, StandardCharsets.ISO_8859_1));
    }
  }

  @Nested
  class Edge_Cases_Tests {

    @Test
    void should_handle_boundary_with_equals_sign() {
      String boundary = "boundary-with=equals";
      String contentType = "multipart/related; boundary=\"" + boundary + "\"";

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals(boundary, new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void should_handle_very_long_boundary() {
      String boundary = "very-long-boundary-" + "x".repeat(200);
      String contentType = "multipart/related; boundary=" + boundary;

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals(boundary, new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void should_handle_boundary_with_semicolon_in_quotes() {
      String boundary = "boundary;with;semicolons";
      String contentType = "multipart/related; boundary=\"" + boundary + "\"";

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals(boundary, new String(result, StandardCharsets.ISO_8859_1));
    }

    @Test
    void should_handle_malformed_but_parseable_content_type() {
      // Missing space after semicolon
      String contentType = "multipart/related;boundary=malformed-boundary";

      byte[] result = BoundaryExtractor.extractBoundary(contentType, MULTIPART_RELATED);

      assertNotNull(result);
      assertEquals("malformed-boundary", new String(result, StandardCharsets.ISO_8859_1));
    }
  }
}
