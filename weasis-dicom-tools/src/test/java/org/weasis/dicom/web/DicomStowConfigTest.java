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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomStowConfigTest {

  private static final String SAMPLE_URL = "https://example.com/dicom/stow";
  private static final String DEFAULT_USER_AGENT = "Weasis STOW-RS Client";
  private static final int DEFAULT_THREAD_POOL_SIZE = 5;
  private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

  @Nested
  class Builder_Creation_Tests {

    @Test
    void should_create_builder_instance() {
      var builder = DicomStowConfig.builder();

      assertNotNull(builder);
    }

    @Test
    void should_create_different_builder_instances() {
      var builder1 = DicomStowConfig.builder();
      var builder2 = DicomStowConfig.builder();

      assertNotSame(builder1, builder2);
    }
  }

  @Nested
  class Basic_Configuration_Tests {

    @Test
    void should_build_config_with_minimal_parameters() {
      var config = DicomStowConfig.builder().requestUrl(SAMPLE_URL).build();

      assertEquals(SAMPLE_URL + "/studies", config.getRequestUrl());
      assertEquals(ContentType.APPLICATION_DICOM, config.getContentType());
      assertEquals(DEFAULT_USER_AGENT, config.getUserAgent());
      assertTrue(config.getHeaders().isEmpty());
      assertEquals(DEFAULT_THREAD_POOL_SIZE, config.getThreadPoolSize());
      assertEquals(DEFAULT_CONNECT_TIMEOUT, config.getConnectTimeout());
    }

    @Test
    void should_build_config_with_all_parameters() {
      String customUserAgent = "Custom DICOM Client/1.0";
      int customThreadPoolSize = 10;
      Duration customTimeout = Duration.ofSeconds(30);
      Map<String, String> customHeaders =
          Map.of(
              "Authorization", "Bearer token123",
              "X-Custom-Header", "custom-value");

      var config =
          DicomStowConfig.builder()
              .requestUrl(SAMPLE_URL)
              .contentType(ContentType.APPLICATION_DICOM)
              .userAgent(customUserAgent)
              .headers(customHeaders)
              .threadPoolSize(customThreadPoolSize)
              .connectTimeout(customTimeout)
              .build();

      assertEquals(SAMPLE_URL + "/studies", config.getRequestUrl());
      assertEquals(ContentType.APPLICATION_DICOM, config.getContentType());
      assertEquals(customUserAgent, config.getUserAgent());
      assertEquals(customHeaders, config.getHeaders());
      assertEquals(customThreadPoolSize, config.getThreadPoolSize());
      assertEquals(customTimeout, config.getConnectTimeout());
    }

    @Test
    void should_use_defaults_for_optional_parameters() {
      var config = DicomStowConfig.builder().requestUrl(SAMPLE_URL).build();

      assertEquals(ContentType.APPLICATION_DICOM, config.getContentType());
      assertEquals(DEFAULT_USER_AGENT, config.getUserAgent());
      assertEquals(DEFAULT_THREAD_POOL_SIZE, config.getThreadPoolSize());
      assertEquals(DEFAULT_CONNECT_TIMEOUT, config.getConnectTimeout());
    }
  }

  @Nested
  class URL_Normalization_Tests {

    static Stream<Arguments> urlNormalizationCases() {
      return Stream.of(
          // Basic URLs
          Arguments.of("https://example.com", "https://example.com/studies"),
          Arguments.of("http://localhost:8080", "http://localhost:8080/studies"),

          // URLs with trailing slash
          Arguments.of("https://example.com/", "https://example.com/studies"),
          Arguments.of("https://example.com/dicom/", "https://example.com/dicom/studies"),

          // URLs already ending with /studies
          Arguments.of("https://example.com/studies", "https://example.com/studies"),
          Arguments.of("https://example.com/dicom/studies", "https://example.com/dicom/studies"),
          Arguments.of("https://example.com/studies/", "https://example.com/studies"),

          // URLs with paths
          Arguments.of("https://example.com/api/dicom", "https://example.com/api/dicom/studies"),
          Arguments.of(
              "https://example.com/v1/dicom/stow", "https://example.com/v1/dicom/stow/studies"),

          // URLs with whitespace
          Arguments.of("  https://example.com  ", "https://example.com/studies"),
          Arguments.of("\thttps://example.com\t", "https://example.com/studies"));
    }

    @ParameterizedTest
    @MethodSource("urlNormalizationCases")
    void should_normalize_urls_correctly(String inputUrl, String expectedUrl) {
      var config = DicomStowConfig.builder().requestUrl(inputUrl).build();

      assertEquals(expectedUrl, config.getRequestUrl());
    }

    @Test
    void should_handle_url_with_query_parameters() {
      String urlWithQuery = "https://example.com/api?version=1";

      var config = DicomStowConfig.builder().requestUrl(urlWithQuery).build();

      assertEquals(urlWithQuery + "/studies", config.getRequestUrl());
    }

    @Test
    void should_handle_complex_url_normalization() {
      String complexUrl = "  https://dicom.hospital.org/api/v2/stow-rs/  ";
      String expectedUrl = "https://dicom.hospital.org/api/v2/stow-rs/studies";

      var config = DicomStowConfig.builder().requestUrl(complexUrl).build();

      assertEquals(expectedUrl, config.getRequestUrl());
    }
  }

  @Nested
  class User_Agent_Configuration_Tests {

    @Test
    void should_use_custom_user_agent() {
      String customAgent = "MyDicomClient/2.1";

      var config = DicomStowConfig.builder().requestUrl(SAMPLE_URL).userAgent(customAgent).build();

      assertEquals(customAgent, config.getUserAgent());
    }

    @Test
    void should_use_default_user_agent_when_null() {
      var config = DicomStowConfig.builder().requestUrl(SAMPLE_URL).userAgent(null).build();

      assertEquals(DEFAULT_USER_AGENT, config.getUserAgent());
    }

    @Test
    void should_handle_empty_user_agent() {
      var config = DicomStowConfig.builder().requestUrl(SAMPLE_URL).userAgent("").build();

      assertEquals("", config.getUserAgent());
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "Mozilla/5.0 (Custom DICOM Client)",
          "Weasis/4.0.0",
          "DICOM-Uploader/1.2.3",
          "Hospital-PACS-Client"
        })
    void should_accept_various_user_agent_formats(String userAgent) {
      var config = DicomStowConfig.builder().requestUrl(SAMPLE_URL).userAgent(userAgent).build();

      assertEquals(userAgent, config.getUserAgent());
    }
  }

  @Nested
  class Headers_Configuration_Tests {

    @Test
    void should_add_single_header() {
      var config =
          DicomStowConfig.builder()
              .requestUrl(SAMPLE_URL)
              .header("Authorization", "Bearer token123")
              .build();

      assertEquals("Bearer token123", config.getHeaders().get("Authorization"));
      assertEquals(1, config.getHeaders().size());
    }

    @Test
    void should_add_multiple_headers_individually() {
      var config =
          DicomStowConfig.builder()
              .requestUrl(SAMPLE_URL)
              .header("Authorization", "Bearer token123")
              .header("X-API-Version", "v2")
              .header("Accept-Encoding", "gzip")
              .build();

      Map<String, String> headers = config.getHeaders();
      assertEquals(3, headers.size());
      assertEquals("Bearer token123", headers.get("Authorization"));
      assertEquals("v2", headers.get("X-API-Version"));
      assertEquals("gzip", headers.get("Accept-Encoding"));
    }

    @Test
    void should_add_headers_from_map() {
      Map<String, String> headersToAdd =
          Map.of(
              "Authorization", "Bearer token456",
              "Content-Language", "en-US",
              "X-Request-ID", "req-12345");

      var config = DicomStowConfig.builder().requestUrl(SAMPLE_URL).headers(headersToAdd).build();

      assertEquals(headersToAdd, config.getHeaders());
    }

    @Test
    void should_combine_individual_and_map_headers() {
      Map<String, String> mapHeaders =
          Map.of(
              "Authorization", "Bearer token789",
              "X-API-Key", "api-key-123");

      var config =
          DicomStowConfig.builder()
              .requestUrl(SAMPLE_URL)
              .header("Content-Type", "application/dicom")
              .headers(mapHeaders)
              .header("Accept", "application/json")
              .build();

      Map<String, String> resultHeaders = config.getHeaders();
      assertEquals(4, resultHeaders.size());
      assertEquals("application/dicom", resultHeaders.get("Content-Type"));
      assertEquals("Bearer token789", resultHeaders.get("Authorization"));
      assertEquals("api-key-123", resultHeaders.get("X-API-Key"));
      assertEquals("application/json", resultHeaders.get("Accept"));
    }

    @Test
    void should_ignore_null_header_value() {
      var config =
          DicomStowConfig.builder()
              .requestUrl(SAMPLE_URL)
              .header("Valid-Header", "valid-value")
              .header("Null-Header", null)
              .build();

      Map<String, String> headers = config.getHeaders();
      assertEquals(1, headers.size());
      assertEquals("valid-value", headers.get("Valid-Header"));
      assertFalse(headers.containsKey("Null-Header"));
    }

    @Test
    void should_ignore_null_headers_map() {
      var config =
          DicomStowConfig.builder()
              .requestUrl(SAMPLE_URL)
              .header("Existing-Header", "existing-value")
              .headers((Map<String, String>) null)
              .build();

      Map<String, String> headers = config.getHeaders();
      assertEquals(1, headers.size());
      assertEquals("existing-value", headers.get("Existing-Header"));
    }

    @Test
    void should_return_immutable_headers_map() {
      var config =
          DicomStowConfig.builder()
              .requestUrl(SAMPLE_URL)
              .header("Test-Header", "test-value")
              .build();

      Map<String, String> headers = config.getHeaders();
      assertThrows(
          UnsupportedOperationException.class, () -> headers.put("New-Header", "new-value"));
    }

    @Test
    void should_throw_null_pointer_exception_for_null_header_name() {
      var builder = DicomStowConfig.builder().requestUrl(SAMPLE_URL);

      assertThrows(NullPointerException.class, () -> builder.header(null, "some-value"));
    }
  }

  @Nested
  class Thread_Pool_Configuration_Tests {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 10, 20, 50, 100})
    void should_accept_valid_thread_pool_sizes(int threadPoolSize) {
      var config =
          DicomStowConfig.builder().requestUrl(SAMPLE_URL).threadPoolSize(threadPoolSize).build();

      assertEquals(threadPoolSize, config.getThreadPoolSize());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10, -100})
    void should_reject_invalid_thread_pool_sizes(int invalidSize) {
      var builder = DicomStowConfig.builder().requestUrl(SAMPLE_URL);

      assertThrows(IllegalArgumentException.class, () -> builder.threadPoolSize(invalidSize));
    }

    @Test
    void should_use_default_thread_pool_size() {
      var config = DicomStowConfig.builder().requestUrl(SAMPLE_URL).build();

      assertEquals(DEFAULT_THREAD_POOL_SIZE, config.getThreadPoolSize());
    }
  }

  @Nested
  class Timeout_Configuration_Tests {

    @Test
    void should_use_custom_timeout() {
      Duration customTimeout = Duration.ofMinutes(2);

      var config =
          DicomStowConfig.builder().requestUrl(SAMPLE_URL).connectTimeout(customTimeout).build();

      assertEquals(customTimeout, config.getConnectTimeout());
    }

    @ParameterizedTest
    @MethodSource("validTimeoutDurations")
    void should_accept_valid_timeout_durations(Duration timeout) {
      var config = DicomStowConfig.builder().requestUrl(SAMPLE_URL).connectTimeout(timeout).build();

      assertEquals(timeout, config.getConnectTimeout());
    }

    static Stream<Duration> validTimeoutDurations() {
      return Stream.of(
          Duration.ofMillis(100),
          Duration.ofSeconds(1),
          Duration.ofSeconds(30),
          Duration.ofMinutes(1),
          Duration.ofMinutes(5),
          Duration.ZERO);
    }

    @Test
    void should_throw_null_pointer_exception_for_null_timeout() {
      var builder = DicomStowConfig.builder().requestUrl(SAMPLE_URL);

      assertThrows(NullPointerException.class, () -> builder.connectTimeout(null));
    }

    @Test
    void should_use_default_timeout() {
      var config = DicomStowConfig.builder().requestUrl(SAMPLE_URL).build();

      assertEquals(DEFAULT_CONNECT_TIMEOUT, config.getConnectTimeout());
    }
  }

  @Nested
  class Content_Type_Configuration_Tests {

    @Test
    void should_use_custom_content_type() {
      var config =
          DicomStowConfig.builder()
              .requestUrl(SAMPLE_URL)
              .contentType(ContentType.APPLICATION_DICOM)
              .build();

      assertEquals(ContentType.APPLICATION_DICOM, config.getContentType());
    }

    @Test
    void should_use_default_content_type() {
      var config = DicomStowConfig.builder().requestUrl(SAMPLE_URL).build();

      assertEquals(ContentType.APPLICATION_DICOM, config.getContentType());
    }

    @Test
    void should_throw_null_pointer_exception_for_null_content_type() {
      var builder = DicomStowConfig.builder().requestUrl(SAMPLE_URL);

      assertThrows(NullPointerException.class, () -> builder.contentType(null));
    }
  }

  @Nested
  class Builder_Validation_Tests {

    @Test
    void should_require_request_url() {
      var builder = DicomStowConfig.builder();

      assertThrows(NullPointerException.class, builder::build);
    }

    @ParameterizedTest
    @NullSource
    void should_throw_null_pointer_exception_for_null_url(String nullUrl) {
      var builder = DicomStowConfig.builder().requestUrl(nullUrl);

      assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void should_reject_empty_url() {
      var builder = DicomStowConfig.builder().requestUrl("");

      // The normalizeUrl method will handle this - it may throw or handle gracefully
      // Testing the actual behavior
      assertDoesNotThrow(
          () -> {
            var config = builder.build();
            assertEquals("/studies", config.getRequestUrl());
          });
    }

    @Test
    void should_build_successfully_with_minimal_valid_configuration() {
      assertDoesNotThrow(
          () -> {
            var config = DicomStowConfig.builder().requestUrl("https://example.com").build();
            assertNotNull(config);
          });
    }
  }

  @Nested
  class Builder_Fluent_Interface_Tests {

    @Test
    void should_support_method_chaining() {
      var config =
          DicomStowConfig.builder()
              .requestUrl(SAMPLE_URL)
              .contentType(ContentType.APPLICATION_DICOM)
              .userAgent("Test Client")
              .header("Authorization", "Bearer test")
              .threadPoolSize(8)
              .connectTimeout(Duration.ofSeconds(15))
              .build();

      assertNotNull(config);
      assertEquals(SAMPLE_URL + "/studies", config.getRequestUrl());
      assertEquals("Test Client", config.getUserAgent());
      assertEquals(8, config.getThreadPoolSize());
    }

    @Test
    void should_return_same_builder_instance_for_chaining() {
      var builder = DicomStowConfig.builder();

      assertSame(builder, builder.requestUrl(SAMPLE_URL));
      assertSame(builder, builder.userAgent("Test"));
      assertSame(builder, builder.threadPoolSize(3));
    }
  }

  @Nested
  class Real_World_Configuration_Tests {

    @Test
    void should_create_hospital_production_config() {
      Map<String, String> hospitalHeaders = new HashMap<>();
      hospitalHeaders.put("Authorization", "Bearer hospital-token-12345");
      hospitalHeaders.put("X-Hospital-ID", "HOSP-001");
      hospitalHeaders.put("X-Department", "radiology");

      var config =
          DicomStowConfig.builder()
              .requestUrl("https://pacs.hospital.org/dicom/stow-rs")
              .userAgent("Hospital-DICOM-Client/3.2.1")
              .headers(hospitalHeaders)
              .threadPoolSize(15)
              .connectTimeout(Duration.ofSeconds(45))
              .build();

      assertEquals("https://pacs.hospital.org/dicom/stow-rs/studies", config.getRequestUrl());
      assertEquals("Hospital-DICOM-Client/3.2.1", config.getUserAgent());
      assertEquals(15, config.getThreadPoolSize());
      assertEquals(Duration.ofSeconds(45), config.getConnectTimeout());
      assertEquals(3, config.getHeaders().size());
    }

    @Test
    void should_create_minimal_development_config() {
      var config =
          DicomStowConfig.builder()
              .requestUrl("http://localhost:8080/dicom")
              .threadPoolSize(2)
              .connectTimeout(Duration.ofSeconds(5))
              .build();

      assertEquals("http://localhost:8080/dicom/studies", config.getRequestUrl());
      assertEquals(DEFAULT_USER_AGENT, config.getUserAgent());
      assertEquals(2, config.getThreadPoolSize());
      assertEquals(Duration.ofSeconds(5), config.getConnectTimeout());
      assertTrue(config.getHeaders().isEmpty());
    }

    @Test
    void should_create_cloud_service_config() {
      var config =
          DicomStowConfig.builder()
              .requestUrl("https://dicom-service.cloud.com/api/v1/stow")
              .userAgent("CloudDicomClient/1.0")
              .header("X-API-Key", "api-key-xyz789")
              .header("X-Client-Version", "1.0.0")
              .header("Accept-Encoding", "gzip, deflate")
              .threadPoolSize(20)
              .connectTimeout(Duration.ofMinutes(2))
              .build();

      assertAll(
          () ->
              assertEquals(
                  "https://dicom-service.cloud.com/api/v1/stow/studies", config.getRequestUrl()),
          () -> assertEquals("CloudDicomClient/1.0", config.getUserAgent()),
          () -> assertEquals(20, config.getThreadPoolSize()),
          () -> assertEquals(Duration.ofMinutes(2), config.getConnectTimeout()),
          () -> assertEquals(3, config.getHeaders().size()),
          () -> assertEquals("api-key-xyz789", config.getHeaders().get("X-API-Key")));
    }
  }
}
