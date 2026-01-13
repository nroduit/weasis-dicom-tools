/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HttpTagTest {

  @Nested
  class Constructor_Tests {

    @Test
    void constructor_with_valid_parameters_creates_http_tag() {
      var httpTag = new HttpTag("Content-Type", "application/json");

      assertAll(
          () -> assertEquals("Content-Type", httpTag.getKey()),
          () -> assertEquals("application/json", httpTag.getValue()));
    }

    @Test
    void constructor_throws_null_pointer_exception_for_null_key() {
      var exception = assertThrows(NullPointerException.class, () -> new HttpTag(null, "value"));

      assertEquals("HTTP header key cannot be null", exception.getMessage());
    }

    @Test
    void constructor_throws_null_pointer_exception_for_null_value() {
      var exception = assertThrows(NullPointerException.class, () -> new HttpTag("key", null));

      assertEquals("HTTP header value cannot be null", exception.getMessage());
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "\r"})
    void constructor_throws_illegal_argument_exception_for_blank_key(String blankKey) {
      var exception =
          assertThrows(IllegalArgumentException.class, () -> new HttpTag(blankKey, "value"));

      assertEquals("HTTP header key cannot be blank", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"key with space", "key@invalid", "key[bracket]", "key{brace}", "key(paren)"})
    void constructor_throws_illegal_argument_exception_for_invalid_key_characters(
        String invalidKey) {
      var exception =
          assertThrows(IllegalArgumentException.class, () -> new HttpTag(invalidKey, "value"));

      assertTrue(exception.getMessage().contains("HTTP header key contains invalid characters"));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"Content-Type", "Authorization", "Accept", "User-Agent", "X-Custom-Header"})
    void constructor_accepts_valid_http_header_names(String validKey) {
      assertDoesNotThrow(() -> new HttpTag(validKey, "value"));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "Accept-Language",
          "Cache-Control",
          "Content-Encoding",
          "If-Modified-Since",
          "X-Forwarded-For",
          "X-API-Key"
        })
    void constructor_accepts_common_http_headers(String commonHeader) {
      var httpTag = new HttpTag(commonHeader, "test-value");
      assertEquals(commonHeader, httpTag.getKey());
    }

    @Test
    void constructor_trims_whitespace_from_key() {
      assertThrows(
          IllegalArgumentException.class,
          () -> new HttpTag("  Content-Type  ", "application/json"));
      assertThrows(
          IllegalArgumentException.class, () -> new HttpTag("Content-Type  ", "application/json"));
    }

    @Test
    void constructor_trims_whitespace_from_value() {
      var httpTag = new HttpTag("Content-Type", "  application/json  ");
      assertEquals("application/json", httpTag.getValue());
    }

    @Test
    void constructor_preserves_internal_whitespace_in_value() {
      var httpTag = new HttpTag("User-Agent", "Mozilla/5.0 (compatible; test agent)");
      assertEquals("Mozilla/5.0 (compatible; test agent)", httpTag.getValue());
    }

    @Test
    void constructor_allows_empty_value() {
      var httpTag = new HttpTag("X-Empty-Header", "");
      assertEquals("", httpTag.getValue());
    }

    @Test
    void constructor_allows_tab_character_in_value() {
      var httpTag = new HttpTag("X-Tab-Header", "value\twith\ttabs");
      assertEquals("value\twith\ttabs", httpTag.getValue());
    }

    @Test
    void constructor_throws_exception_for_control_characters_in_value() {
      assertThrows(
          IllegalArgumentException.class,
          () -> new HttpTag("Content-Type", "value\u0001with\u0002control"));
    }

    @Test
    void constructor_throws_exception_for_value_exceeding_max_length() {
      String longValue = "a".repeat(8193); // Exceeds MAX_HEADER_LENGTH
      var exception =
          assertThrows(IllegalArgumentException.class, () -> new HttpTag("Long-Header", longValue));

      assertTrue(exception.getMessage().contains("HTTP header value exceeds maximum length"));
    }

    @Test
    void constructor_accepts_value_at_max_length() {
      String maxLengthValue = "a".repeat(8192);
      assertDoesNotThrow(() -> new HttpTag("Max-Length-Header", maxLengthValue));
    }
  }

  @Nested
  class Factory_Method_Tests {

    @Test
    void of_factory_method_creates_http_tag() {
      var httpTag = HttpTag.of("Accept", "application/json");

      assertAll(
          () -> assertEquals("Accept", httpTag.getKey()),
          () -> assertEquals("application/json", httpTag.getValue()));
    }

    @Test
    void authorization_factory_method_creates_bearer_token_header() {
      var httpTag = HttpTag.authorization("abc123token");

      assertAll(
          () -> assertEquals("Authorization", httpTag.getKey()),
          () -> assertEquals("Bearer abc123token", httpTag.getValue()),
          () -> assertTrue(httpTag.isAuthorizationHeader()));
    }

    @Test
    void authorization_factory_method_throws_exception_for_null_token() {
      var exception = assertThrows(NullPointerException.class, () -> HttpTag.authorization(null));

      assertEquals("Token cannot be null", exception.getMessage());
    }

    @Test
    void contentType_factory_method_creates_content_type_header() {
      var httpTag = HttpTag.contentType("application/dicom+json");

      assertAll(
          () -> assertEquals("Content-Type", httpTag.getKey()),
          () -> assertEquals("application/dicom+json", httpTag.getValue()));
    }

    @Test
    void contentType_factory_method_throws_exception_for_null_content_type() {
      var exception = assertThrows(NullPointerException.class, () -> HttpTag.contentType(null));

      assertEquals("Content type cannot be null", exception.getMessage());
    }

    static Stream<Arguments> commonContentTypes() {
      return Stream.of(
          Arguments.of("application/json"),
          Arguments.of("application/xml"),
          Arguments.of("text/html"),
          Arguments.of("application/dicom"),
          Arguments.of("application/dicom+json"),
          Arguments.of("multipart/related"));
    }

    @ParameterizedTest
    @MethodSource("commonContentTypes")
    void contentType_factory_method_accepts_common_content_types(String contentType) {
      var httpTag = HttpTag.contentType(contentType);

      assertAll(
          () -> assertEquals("Content-Type", httpTag.getKey()),
          () -> assertEquals(contentType, httpTag.getValue()));
    }
  }

  @Nested
  class Validation_Method_Tests {

    @Test
    void isAuthorizationHeader_returns_true_for_authorization_header() {
      var httpTag = new HttpTag("Authorization", "Bearer token");
      assertTrue(httpTag.isAuthorizationHeader());
    }

    @ParameterizedTest
    @ValueSource(strings = {"authorization", "AUTHORIZATION", "Authorization", "AuThOrIzAtIoN"})
    void isAuthorizationHeader_is_case_insensitive(String authKey) {
      var httpTag = new HttpTag(authKey, "Bearer token");
      assertTrue(httpTag.isAuthorizationHeader());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Content-Type", "Accept", "User-Agent", "X-API-Key"})
    void isAuthorizationHeader_returns_false_for_non_authorization_headers(String nonAuthKey) {
      var httpTag = new HttpTag(nonAuthKey, "value");
      assertFalse(httpTag.isAuthorizationHeader());
    }

    static Stream<Arguments> sensitiveHeaders() {
      return Stream.of(
          Arguments.of("Authorization", "Bearer token123"),
          Arguments.of("X-API-Key", "secret-key"),
          Arguments.of("X-Auth-Token", "token123"),
          Arguments.of("Password", "secret"),
          Arguments.of("Secret-Header", "value"),
          Arguments.of("X-Secret-Key", "key123"),
          Arguments.of("Token", "abc123"));
    }

    @ParameterizedTest
    @MethodSource("sensitiveHeaders")
    void isSensitive_returns_true_for_sensitive_headers(String key, String value) {
      var httpTag = new HttpTag(key, value);
      assertTrue(httpTag.isSensitive());
    }

    static Stream<Arguments> nonSensitiveHeaders() {
      return Stream.of(
          Arguments.of("Content-Type", "application/json"),
          Arguments.of("Accept", "application/xml"),
          Arguments.of("User-Agent", "Mozilla/5.0"),
          Arguments.of("Cache-Control", "no-cache"),
          Arguments.of("X-Custom-Header", "custom-value"));
    }

    @ParameterizedTest
    @MethodSource("nonSensitiveHeaders")
    void isSensitive_returns_false_for_non_sensitive_headers(String key, String value) {
      var httpTag = new HttpTag(key, value);
      assertFalse(httpTag.isSensitive());
    }

    @Test
    void isSensitive_is_case_insensitive() {
      var httpTag1 = new HttpTag("AUTH-HEADER", "value");
      var httpTag2 = new HttpTag("auth-header", "value");
      var httpTag3 = new HttpTag("Auth-Header", "value");

      assertAll(
          () -> assertTrue(httpTag1.isSensitive()),
          () -> assertTrue(httpTag2.isSensitive()),
          () -> assertTrue(httpTag3.isSensitive()));
    }
  }

  @Nested
  class String_Representation_Tests {

    @Test
    void toHeaderString_formats_as_http_header() {
      var httpTag = new HttpTag("Content-Type", "application/json");
      assertEquals("Content-Type: application/json", httpTag.toHeaderString());
    }

    @Test
    void toString_shows_non_sensitive_header_details() {
      var httpTag = new HttpTag("Accept", "application/xml");
      String result = httpTag.toString();

      assertAll(
          () -> assertTrue(result.contains("Accept")),
          () -> assertTrue(result.contains("application/xml")),
          () -> assertTrue(result.contains("HttpTag")));
    }

    @Test
    void toString_redacts_sensitive_header_values() {
      var httpTag = new HttpTag("Authorization", "Bearer secret-token");
      String result = httpTag.toString();

      assertAll(
          () -> assertTrue(result.contains("Authorization")),
          () -> assertTrue(result.contains("[REDACTED]")),
          () -> assertFalse(result.contains("secret-token")),
          () -> assertTrue(result.contains("HttpTag")));
    }

    @Test
    void toString_redacts_api_key_headers() {
      var httpTag = new HttpTag("X-API-Key", "secret-api-key");
      String result = httpTag.toString();

      assertAll(
          () -> assertTrue(result.contains("X-API-Key")),
          () -> assertTrue(result.contains("[REDACTED]")),
          () -> assertFalse(result.contains("secret-api-key")));
    }
  }

  @Nested
  class Equality_And_HashCode_Tests {

    @Test
    void equals_returns_true_for_identical_http_tags() {
      var httpTag1 = new HttpTag("Content-Type", "application/json");
      var httpTag2 = new HttpTag("Content-Type", "application/json");

      assertEquals(httpTag1, httpTag2);
    }

    @Test
    void equals_returns_true_for_same_instance() {
      var httpTag = new HttpTag("Accept", "text/html");
      assertEquals(httpTag, httpTag);
    }

    @Test
    void equals_returns_false_for_different_keys() {
      var httpTag1 = new HttpTag("Content-Type", "application/json");
      var httpTag2 = new HttpTag("Accept", "application/json");

      assertNotEquals(httpTag1, httpTag2);
    }

    @Test
    void equals_returns_false_for_different_values() {
      var httpTag1 = new HttpTag("Content-Type", "application/json");
      var httpTag2 = new HttpTag("Content-Type", "application/xml");

      assertNotEquals(httpTag1, httpTag2);
    }

    @Test
    void equals_returns_false_for_null() {
      var httpTag = new HttpTag("Content-Type", "application/json");
      assertNotEquals(httpTag, null);
    }

    @Test
    void equals_returns_false_for_different_object_type() {
      var httpTag = new HttpTag("Content-Type", "application/json");
      assertNotEquals(httpTag, "not an HttpTag");
    }

    @Test
    void hashCode_is_consistent_for_equal_objects() {
      var httpTag1 = new HttpTag("Authorization", "Bearer token");
      var httpTag2 = new HttpTag("Authorization", "Bearer token");

      assertEquals(httpTag1.hashCode(), httpTag2.hashCode());
    }

    @Test
    void hashCode_is_different_for_different_objects() {
      var httpTag1 = new HttpTag("Content-Type", "application/json");
      var httpTag2 = new HttpTag("Accept", "application/json");

      assertNotEquals(httpTag1.hashCode(), httpTag2.hashCode());
    }

    @Test
    void hashCode_is_consistent_across_multiple_calls() {
      var httpTag = new HttpTag("User-Agent", "Mozilla/5.0");
      int firstHash = httpTag.hashCode();
      int secondHash = httpTag.hashCode();

      assertEquals(firstHash, secondHash);
    }
  }

  @Nested
  class Edge_Cases_And_Special_Characters_Tests {

    @Test
    void constructor_accepts_special_valid_characters_in_key() {
      var validKeys =
          new String[] {
            "Accept-Encoding",
            "X_Custom_Header",
            "X.Custom.Header",
            "X+Custom+Header",
            "Custom~Header",
            "Header123",
            "X-API-Key",
            "Content-MD5"
          };

      for (String key : validKeys) {
        assertDoesNotThrow(() -> new HttpTag(key, "value"), "Should accept valid key: " + key);
      }
    }

    @Test
    void constructor_handles_unicode_in_value() {
      var httpTag = new HttpTag("X-Unicode", "Weasis™ DICOM™ 🩺");
      assertEquals("Weasis™ DICOM™ 🩺", httpTag.getValue());
    }

    @Test
    void constructor_handles_newlines_and_carriage_returns_in_value() {
      assertAll(
          () ->
              assertThrows(
                  IllegalArgumentException.class,
                  () -> new HttpTag("X-Newline", "value\nwith\nnewlines")),
          () ->
              assertThrows(
                  IllegalArgumentException.class,
                  () -> new HttpTag("X-CarriageReturn", "value\rwith\rreturns")));
    }

    @Test
    void value_can_contain_colon_and_semicolon() {
      var httpTag = new HttpTag("Content-Type", "text/html; charset=utf-8");
      assertEquals("text/html; charset=utf-8", httpTag.getValue());
    }

    @Test
    void key_validation_is_strict() {
      var invalidKeys =
          new String[] {
            "key space",
            "key\ttab",
            "key\nnewline",
            "key@at",
            "key[bracket",
            "key{brace",
            "key\"quote",
            "key\\backslash"
          };

      for (String invalidKey : invalidKeys) {
        assertThrows(
            IllegalArgumentException.class,
            () -> new HttpTag(invalidKey, "value"),
            "Should reject invalid key: " + invalidKey);
      }
    }

    @Test
    void common_dicom_headers_work_correctly() {
      assertAll(
          () -> assertDoesNotThrow(() -> HttpTag.contentType("application/dicom")),
          () -> assertDoesNotThrow(() -> HttpTag.contentType("application/dicom+json")),
          () -> assertDoesNotThrow(() -> HttpTag.contentType("application/dicom+xml")),
          () ->
              assertDoesNotThrow(() -> HttpTag.of("Accept", "application/dicom, application/json")),
          () -> assertDoesNotThrow(() -> HttpTag.of("Transfer-Encoding", "chunked")));
    }
  }

  @Nested
  class Real_World_Usage_Tests {

    @Test
    void wado_rs_headers_can_be_created() {
      var acceptHeader = HttpTag.of("Accept", "multipart/related; type=application/dicom");
      var authHeader = HttpTag.authorization("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");

      assertAll(
          () -> assertEquals("Accept", acceptHeader.getKey()),
          () -> assertTrue(acceptHeader.getValue().contains("multipart/related")),
          () -> assertTrue(authHeader.isAuthorizationHeader()),
          () -> assertTrue(authHeader.isSensitive()));
    }

    @Test
    void stow_rs_headers_can_be_created() {
      var contentType = HttpTag.contentType("multipart/related; type=application/dicom");
      var boundary =
          HttpTag.of(
              "Content-Type", "multipart/related; type=application/dicom; boundary=--boundary123");

      assertAll(
          () -> assertEquals("Content-Type", contentType.getKey()),
          () -> assertTrue(contentType.getValue().contains("application/dicom")),
          () -> assertTrue(boundary.getValue().contains("boundary")));
    }

    @Test
    void custom_authentication_headers_work() {
      var apiKeyHeader = HttpTag.of("X-API-Key", "ak_live_123456789");
      var customAuth = HttpTag.of("X-Custom-Auth", "CustomToken abc123def456");

      assertAll(
          () -> assertTrue(apiKeyHeader.isSensitive()),
          () -> assertTrue(customAuth.isSensitive()),
          () -> assertFalse(apiKeyHeader.isAuthorizationHeader()),
          () -> assertFalse(customAuth.isAuthorizationHeader()));
    }

    @Test
    void cors_and_proxy_headers_work() {
      assertAll(
          () -> assertDoesNotThrow(() -> HttpTag.of("Access-Control-Allow-Origin", "*")),
          () -> assertDoesNotThrow(() -> HttpTag.of("X-Forwarded-For", "203.0.113.195")),
          () -> assertDoesNotThrow(() -> HttpTag.of("X-Real-IP", "203.0.113.195")),
          () -> assertDoesNotThrow(() -> HttpTag.of("X-Forwarded-Proto", "https")));
    }
  }
}
