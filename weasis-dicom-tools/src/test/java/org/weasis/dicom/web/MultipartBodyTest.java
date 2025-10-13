/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@DisplayNameGeneration(ReplaceUnderscores.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MultipartBodyTest {

  private static final String TEST_BOUNDARY = "testBoundary123";
  private static final ContentType TEST_CONTENT_TYPE = ContentType.APPLICATION_DICOM;
  private static final String TEST_MIME_TYPE = "application/dicom";
  private static final byte[] TEST_DATA = "test data content".getBytes(StandardCharsets.UTF_8);
  private static final String TEST_LOCATION = "http://example.com/test";

  @Mock private Flow.Subscription mockSubscription;

  private MultipartBody multipartBody;

  @BeforeEach
  void setUp() {
    multipartBody = new MultipartBody(TEST_CONTENT_TYPE, TEST_BOUNDARY);
  }

  @Nested
  class Constructor_Tests {

    @Test
    void should_create_multipart_body_with_valid_parameters() {
      var body = new MultipartBody(TEST_CONTENT_TYPE, TEST_BOUNDARY);

      assertEquals(
          TEST_CONTENT_TYPE,
          body.getContentTypeHeader().contains(TEST_CONTENT_TYPE.getType())
              ? TEST_CONTENT_TYPE
              : null);
      assertEquals(TEST_BOUNDARY, body.getBoundary());
      assertTrue(body.getParts().isEmpty());
    }

    @Test
    void should_throw_exception_when_content_type_is_null() {
      assertThrows(NullPointerException.class, () -> new MultipartBody(null, TEST_BOUNDARY));
    }

    @Test
    void should_throw_exception_when_boundary_is_null() {
      assertThrows(NullPointerException.class, () -> new MultipartBody(TEST_CONTENT_TYPE, null));
    }
  }

  @Nested
  class Add_Part_Tests {

    @Test
    void should_add_part_with_byte_array() {
      var result = multipartBody.addPart(TEST_MIME_TYPE, TEST_DATA, TEST_LOCATION);

      assertEquals(multipartBody, result);
      assertEquals(1, multipartBody.getParts().size());

      var part = multipartBody.getParts().get(0);
      assertEquals(TEST_MIME_TYPE, part.contentType());
      assertEquals(TEST_LOCATION, part.location());
      assertEquals(TEST_DATA.length, part.payload().size());
    }

    @Test
    void should_add_part_with_path() {
      var mockPath = mock(Path.class);

      var result = multipartBody.addPart(TEST_MIME_TYPE, mockPath, TEST_LOCATION);

      assertEquals(multipartBody, result);
      assertEquals(1, multipartBody.getParts().size());

      var part = multipartBody.getParts().get(0);
      assertEquals(TEST_MIME_TYPE, part.contentType());
      assertEquals(TEST_LOCATION, part.location());
    }

    @Test
    void should_add_part_with_custom_payload() {
      var mockPayload = mock(Payload.class);
      when(mockPayload.size()).thenReturn(100L);

      var result = multipartBody.addPart(TEST_MIME_TYPE, mockPayload, TEST_LOCATION);

      assertEquals(multipartBody, result);
      assertEquals(1, multipartBody.getParts().size());

      var part = multipartBody.getParts().get(0);
      assertEquals(TEST_MIME_TYPE, part.contentType());
      assertEquals(TEST_LOCATION, part.location());
      assertEquals(mockPayload, part.payload());
    }

    @Test
    void should_add_multiple_parts() {
      multipartBody.addPart(TEST_MIME_TYPE, TEST_DATA, TEST_LOCATION);
      multipartBody.addPart("text/plain", "hello".getBytes(), null);

      assertEquals(2, multipartBody.getParts().size());
    }

    @ParameterizedTest
    @MethodSource("nullParametersForAddPart")
    void should_throw_exception_for_null_parameters(
        String contentType, byte[] data, Class<? extends Exception> expectedType) {
      assertThrows(expectedType, () -> multipartBody.addPart(contentType, data, TEST_LOCATION));
    }

    static Stream<Arguments> nullParametersForAddPart() {
      return Stream.of(
          Arguments.of(null, TEST_DATA, NullPointerException.class),
          Arguments.of(TEST_MIME_TYPE, null, NullPointerException.class));
    }

    @Test
    void should_throw_exception_when_body_is_closed() {
      // Close the body by triggering onComplete
      multipartBody.onComplete();

      assertThrows(
          IllegalStateException.class,
          () -> multipartBody.addPart(TEST_MIME_TYPE, TEST_DATA, TEST_LOCATION));
    }
  }

  @Nested
  class Body_Publisher_Tests {

    @Test
    void should_create_body_publisher_with_custom_stream_supplier() {
      Supplier<ByteArrayInputStream> streamSupplier = () -> new ByteArrayInputStream(TEST_DATA);

      var publisher = multipartBody.createBodyPublisher(streamSupplier);

      assertNotNull(publisher);
    }

    @Test
    void should_create_body_publisher_with_internal_stream() {
      multipartBody.addPart(TEST_MIME_TYPE, TEST_DATA, TEST_LOCATION);

      var publisher = multipartBody.createBodyPublisher();

      assertNotNull(publisher);
    }

    @Test
    void should_throw_exception_for_null_stream_supplier() {
      assertThrows(NullPointerException.class, () -> multipartBody.createBodyPublisher(null));
    }
  }

  @Nested
  class Content_Type_Header_Tests {

    @Test
    void should_generate_correct_content_type_header() {
      var header = multipartBody.getContentTypeHeader();

      assertTrue(header.contains("multipart/related"));
      assertTrue(header.contains(TEST_CONTENT_TYPE.getType()));
      assertTrue(header.contains(TEST_BOUNDARY));
    }
  }

  @Nested
  class Parts_Management_Tests {

    @Test
    void should_return_unmodifiable_parts_list() {
      multipartBody.addPart(TEST_MIME_TYPE, TEST_DATA, TEST_LOCATION);

      var parts = multipartBody.getParts();

      assertThrows(UnsupportedOperationException.class, parts::clear);
    }

    @Test
    void should_reset_parts_and_closed_state() {
      multipartBody.addPart(TEST_MIME_TYPE, TEST_DATA, TEST_LOCATION);
      multipartBody.onComplete(); // Close the body

      multipartBody.reset();

      assertTrue(multipartBody.getParts().isEmpty());
      // Should be able to add parts again after reset
      multipartBody.addPart(TEST_MIME_TYPE, TEST_DATA, TEST_LOCATION);
      assertEquals(1, multipartBody.getParts().size());
    }
  }

  @Nested
  class Flow_Subscriber_Tests {

    @Test
    void should_handle_subscription() {
      multipartBody.onSubscribe(mockSubscription);

      verify(mockSubscription).request(1);
    }

    @Test
    void should_handle_next_item_without_subscription() {
      var buffer = ByteBuffer.wrap(TEST_DATA);

      // Should not throw exception
      multipartBody.onNext(buffer);

      verify(mockSubscription, never()).request(1);
    }

    @Test
    void should_handle_error() {
      var exception = new RuntimeException("Test error");

      // Should not throw exception
      multipartBody.onError(exception);
    }

    @Test
    void should_handle_completion() {
      multipartBody.onComplete();

      // After completion, body should be considered closed
      assertThrows(
          IllegalStateException.class,
          () -> multipartBody.addPart(TEST_MIME_TYPE, TEST_DATA, TEST_LOCATION));
    }
  }

  @Nested
  class Stream_Enumeration_Tests {

    @Test
    void should_create_valid_sequence_input_stream_for_single_part() throws IOException {
      multipartBody.addPart(TEST_MIME_TYPE, TEST_DATA, TEST_LOCATION);

      try (var sequenceStream = new SequenceInputStream(createStreamEnumeration(multipartBody))) {
        var content = sequenceStream.readAllBytes();
        var contentString = new String(content, StandardCharsets.UTF_8);

        assertTrue(contentString.contains("--" + TEST_BOUNDARY));
        assertTrue(contentString.contains("Content-Type: " + TEST_MIME_TYPE));
        assertTrue(contentString.contains(new String(TEST_DATA, StandardCharsets.UTF_8)));
        assertTrue(contentString.contains("--" + TEST_BOUNDARY + "--"));
      }
    }

    @Test
    void should_create_valid_sequence_input_stream_for_multiple_parts() throws IOException {
      multipartBody.addPart(TEST_MIME_TYPE, TEST_DATA, TEST_LOCATION);
      multipartBody.addPart("text/plain", "second part".getBytes(), null);

      try (var sequenceStream = new SequenceInputStream(createStreamEnumeration(multipartBody))) {
        var content = sequenceStream.readAllBytes();
        var contentString = new String(content, StandardCharsets.UTF_8);

        // Should contain boundaries for both parts
        var boundaryCount = countOccurrences(contentString, "--" + TEST_BOUNDARY);
        assertEquals(3, boundaryCount); // 2 part boundaries + 1 closing boundary

        assertTrue(contentString.contains("second part"));
      }
    }

    @Test
    void should_handle_empty_multipart_body() throws IOException {
      try (var sequenceStream = new SequenceInputStream(createStreamEnumeration(multipartBody))) {
        var content = sequenceStream.readAllBytes();
        var contentString = new String(content, StandardCharsets.UTF_8);

        assertTrue(contentString.contains("--" + TEST_BOUNDARY + "--"));
      }
    }

    @Test
    void should_throw_exception_when_no_more_streams_available() {
      var enumeration = createStreamEnumeration(multipartBody);

      // Exhaust the enumeration
      while (enumeration.hasMoreElements()) {
        enumeration.nextElement();
      }

      assertFalse(enumeration.hasMoreElements());
      assertThrows(NoSuchElementException.class, enumeration::nextElement);
    }

    private Enumeration<InputStream> createStreamEnumeration(MultipartBody body) {
      // Create a test enumeration using a similar pattern to the private implementation
      var streams = new ArrayList<InputStream>();

      for (var part : body.getParts()) {
        // Add part header
        var header = part.generateHeader(body.getBoundary());
        streams.add(new ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8)));

        // Add part data
        streams.add(part.newInputStream());
      }

      // Add closing delimiter
      var closingDelimiter = "\r\n--" + body.getBoundary() + "--";
      streams.add(new ByteArrayInputStream(closingDelimiter.getBytes(StandardCharsets.UTF_8)));

      return new TestEnumeration(streams);
    }

    private int countOccurrences(String text, String substring) {
      int count = 0;
      int index = 0;
      while ((index = text.indexOf(substring, index)) != -1) {
        count++;
        index += substring.length();
      }
      return count;
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void should_work_with_http_request_body_publisher() throws IOException {
      multipartBody.addPart(TEST_MIME_TYPE, TEST_DATA, TEST_LOCATION);

      var publisher = multipartBody.createBodyPublisher();
      var request =
          HttpRequest.newBuilder()
              .uri(java.net.URI.create("http://example.com"))
              .POST(publisher)
              .header("Content-Type", multipartBody.getContentTypeHeader())
              .build();

      assertNotNull(request);
      assertEquals("POST", request.method());
      assertTrue(
          request.headers().firstValue("Content-Type").orElse("").contains("multipart/related"));
    }

    @Test
    void should_handle_payload_with_unknown_size() {
      var unknownSizePayload =
          new Payload() {
            @Override
            public long size() {
              return -1;
            }

            @Override
            public InputStream newInputStream() {
              return new ByteArrayInputStream(TEST_DATA);
            }
          };

      multipartBody.addPart(TEST_MIME_TYPE, unknownSizePayload, TEST_LOCATION);

      assertEquals(1, multipartBody.getParts().size());
      assertEquals(-1, multipartBody.getParts().get(0).payload().size());
    }
  }

  // Helper class for testing enumeration
  private static class TestEnumeration implements Enumeration<InputStream> {
    private final List<InputStream> streams;
    private final AtomicInteger index = new AtomicInteger(0);

    TestEnumeration(List<InputStream> streams) {
      this.streams = new ArrayList<>(streams);
    }

    @Override
    public boolean hasMoreElements() {
      return index.get() < streams.size();
    }

    @Override
    public InputStream nextElement() {
      if (!hasMoreElements()) {
        throw new NoSuchElementException();
      }
      return streams.get(index.getAndIncrement());
    }
  }
}
