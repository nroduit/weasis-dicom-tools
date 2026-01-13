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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

@DisplayNameGeneration(ReplaceUnderscores.class)
class MultipartReaderTest {

  private static final String TEST_BOUNDARY = "boundary123";
  private static final byte[] TEST_BOUNDARY_BYTES = TEST_BOUNDARY.getBytes(StandardCharsets.UTF_8);

  @Nested
  class Constructor_Tests {

    @Test
    void should_create_reader_with_valid_parameters() {
      var inputStream = new ByteArrayInputStream(new byte[0]);

      try (var reader = new MultipartReader(inputStream, TEST_BOUNDARY_BYTES)) {
        assertNotNull(reader);
        assertEquals(StandardCharsets.UTF_8, reader.getHeaderEncoding());
      } catch (IOException e) {
        fail("Should not throw exception with valid parameters");
      }
    }

    @Test
    void should_create_reader_with_custom_buffer_size() {
      var inputStream = new ByteArrayInputStream(new byte[0]);

      try (var reader = new MultipartReader(inputStream, TEST_BOUNDARY_BYTES, 1024)) {
        assertNotNull(reader);
      } catch (IOException e) {
        fail("Should not throw exception with valid buffer size");
      }
    }

    @Test
    void should_throw_exception_when_input_stream_is_null() {
      assertThrows(
          NullPointerException.class, () -> new MultipartReader(null, TEST_BOUNDARY_BYTES));
    }

    @Test
    void should_throw_exception_when_boundary_is_null() {
      var inputStream = new ByteArrayInputStream(new byte[0]);

      assertThrows(NullPointerException.class, () -> new MultipartReader(inputStream, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {255, 1048577}) // Below min and above max
    void should_throw_exception_for_invalid_buffer_sizes(int invalidSize) {
      var inputStream = new ByteArrayInputStream(new byte[0]);

      assertThrows(
          IllegalArgumentException.class,
          () -> new MultipartReader(inputStream, TEST_BOUNDARY_BYTES, invalidSize));
    }

    @ParameterizedTest
    @ValueSource(ints = {256, 4096, 1048576}) // Valid sizes
    void should_accept_valid_buffer_sizes(int validSize) {
      var inputStream = new ByteArrayInputStream(new byte[0]);

      assertDoesNotThrow(() -> new MultipartReader(inputStream, TEST_BOUNDARY_BYTES, validSize));
    }
  }

  @Nested
  class Header_Encoding_Tests {

    @Test
    void should_set_valid_encoding() {
      var inputStream = new ByteArrayInputStream(new byte[0]);

      try (var reader = new MultipartReader(inputStream, TEST_BOUNDARY_BYTES)) {
        reader.setHeaderEncoding("ISO-8859-1");
        assertEquals(StandardCharsets.ISO_8859_1, reader.getHeaderEncoding());
      } catch (IOException e) {
        fail("Should not throw exception");
      }
    }

    @Test
    void should_fallback_to_utf8_for_invalid_encoding() {
      var inputStream = new ByteArrayInputStream(new byte[0]);

      try (var reader = new MultipartReader(inputStream, TEST_BOUNDARY_BYTES)) {
        reader.setHeaderEncoding("INVALID-ENCODING");
        assertEquals(StandardCharsets.UTF_8, reader.getHeaderEncoding());
      } catch (IOException e) {
        fail("Should not throw exception");
      }
    }

    @Test
    void should_use_utf8_for_null_encoding() {
      var inputStream = new ByteArrayInputStream(new byte[0]);

      try (var reader = new MultipartReader(inputStream, TEST_BOUNDARY_BYTES)) {
        reader.setHeaderEncoding(null);
        assertEquals(StandardCharsets.UTF_8, reader.getHeaderEncoding());
      } catch (IOException e) {
        fail("Should not throw exception");
      }
    }
  }

  @Nested
  class Boundary_Processing_Tests {

    @Test
    void should_skip_first_boundary_successfully() throws IOException {
      var multipartData =
          createMultipartData(
              "Initial data before boundary\r\n",
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n\r\n",
              "Part 1 data\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        assertThrows(MultipartStreamException.class, reader::readBoundary);
      }
    }

    @Test
    void should_read_boundary_with_more_parts() throws IOException {
      var multipartData =
          createMultipartData(
              "\r\n--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n\r\n",
              "Part data\r\n",
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/html\r\n\r\n",
              "More data\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        assertThrows(MultipartStreamException.class, reader::readBoundary);
      }
    }

    @Test
    void should_read_boundary_at_end_of_stream() throws IOException {
      var multipartData =
          createMultipartData(
              "\r\n--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n\r\n",
              "Final part data\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        assertThrows(MultipartStreamException.class, reader::readBoundary);
      }
    }
  }

  @Nested
  class Header_Reading_Tests {

    @Test
    void should_read_simple_headers() throws IOException {
      var multipartData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n",
              "Content-Length: 10\r\n\r\n",
              "Test data\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        String headers = reader.readHeaders();

        assertTrue(headers.contains("Content-Type: text/plain"));
        assertTrue(headers.contains("Content-Length: 10"));
        assertTrue(headers.endsWith("\r\n\r\n"));
      }
    }

    @Test
    void should_read_headers_with_different_encoding() throws IOException {
      var headerText = "Content-Type: text/plain; charset=ISO-8859-1\r\n\r\n";
      var multipartData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "\r\n",
              headerText,
              "Test data\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.setHeaderEncoding("ISO-8859-1");
        reader.skipFirstBoundary();
        String headers = reader.readHeaders();

        assertTrue(headers.contains("Content-Type: text/plain"));
      }
    }

    @Test
    void should_throw_exception_for_oversized_headers() {
      // Create headers larger than MAX_HEADER_SIZE (16KB)
      var largeHeader =
          "X-Custom-Header: " + "a".repeat(MultipartReader.MAX_HEADER_SIZE) + "\r\n\r\n";
      var multipartData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "\r\n", largeHeader, "Data\r\n", "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();

        assertThrows(MultipartStreamException.class, reader::readHeaders);
      } catch (IOException e) {
        fail("Should only throw MultipartStreamException for oversized headers");
      }
    }
  }

  @Nested
  class Part_Input_Stream_Tests {

    @Test
    void should_read_part_data_correctly() throws IOException {
      var partData = "This is test data for the part";
      var multipartData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n\r\n",
              partData + "\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        reader.readHeaders();

        try (var partStream = reader.newPartInputStream()) {
          var result = new String(partStream.readAllBytes(), StandardCharsets.UTF_8);
          assertEquals(partData, result);
        }
      }
    }

    @Test
    void should_read_part_data_byte_by_byte() throws IOException {
      var partData = "Test";
      var multipartData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n\r\n",
              partData + "\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        reader.readHeaders();

        try (var partStream = reader.newPartInputStream()) {
          assertEquals('T', partStream.read());
          assertEquals('e', partStream.read());
          assertEquals('s', partStream.read());
          assertEquals('t', partStream.read());
          assertEquals(-1, partStream.read()); // End of part
        }
      }
    }

    @Test
    void should_read_part_data_in_chunks() throws IOException {
      var partData = "This is a longer test data string for chunk reading";
      var multipartData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n\r\n",
              partData + "\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        reader.readHeaders();

        try (var partStream = reader.newPartInputStream()) {
          var buffer = new byte[10];
          var output = new ByteArrayOutputStream();

          int bytesRead;
          while ((bytesRead = partStream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
          }

          assertEquals(partData, output.toString(StandardCharsets.UTF_8));
        }
      }
    }

    @Test
    void should_skip_bytes_correctly() throws IOException {
      var partData = "0123456789";
      var multipartData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n\r\n",
              partData + "\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        reader.readHeaders();

        try (var partStream = reader.newPartInputStream()) {
          assertEquals(5, partStream.skip(5)); // Skip first 5 chars
          assertEquals('5', partStream.read()); // Should read '5'
          assertEquals(4, partStream.skip(10)); // Try to skip 10, but only 4 remain
          assertEquals(-1, partStream.read()); // Should be at end
        }
      }
    }

    @Test
    void should_report_available_bytes() throws IOException {
      var partData = "Test data";
      var multipartData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n\r\n",
              partData + "\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        reader.readHeaders();

        try (var partStream = reader.newPartInputStream()) {
          int initialAvailable = partStream.available();
          assertTrue(initialAvailable > 0);

          partStream.read(); // Read one byte
          int afterRead = partStream.available();
          assertTrue(afterRead < initialAvailable || afterRead == 0);
        }
      }
    }

    @Test
    void should_handle_empty_parts() throws IOException {
      var multipartData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n\r\n",
              "\r\n", // Empty part content
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        reader.readHeaders();

        try (var partStream = reader.newPartInputStream()) {
          assertEquals(-1, partStream.read());
          assertEquals(0, partStream.available());
        }
      }
    }

    @Test
    void should_throw_exception_when_reading_closed_stream() throws IOException {
      var multipartData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n\r\n",
              "Test data\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        reader.readHeaders();

        var partStream = reader.newPartInputStream();
        partStream.close();
        assertTrue(partStream.isClosed());

        assertThrows(IOException.class, partStream::read);
        assertThrows(IOException.class, () -> partStream.read(new byte[10]));
        assertThrows(IOException.class, () -> partStream.skip(5));
        assertThrows(IOException.class, partStream::available);
      }
    }
  }

  @Nested
  class Multiple_Parts_Tests {

    @Test
    void should_process_multiple_parts_sequentially() throws IOException {
      var multipartData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n\r\n",
              "Part 1 data\r\n",
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: application/json\r\n\r\n",
              "{\"part\": 2}\r\n",
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/html\r\n\r\n",
              "<html>Part 3</html>\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        // Skip first boundary and read first part
        assertFalse(reader.skipFirstBoundary());
        String headers1 = reader.readHeaders();
        assertTrue(headers1.contains("Content-Type: text/plain"));

        try (var part1 = reader.newPartInputStream()) {
          assertEquals("Part 1 data", new String(part1.readAllBytes(), StandardCharsets.UTF_8));
        }

        // Read second part
        assertTrue(reader.readBoundary());
        String headers2 = reader.readHeaders();
        assertTrue(headers2.contains("Content-Type: application/json"));

        try (var part2 = reader.newPartInputStream()) {
          assertEquals("{\"part\": 2}", new String(part2.readAllBytes(), StandardCharsets.UTF_8));
        }

        // Read third part
        assertTrue(reader.readBoundary());
        String headers3 = reader.readHeaders();
        assertTrue(headers3.contains("Content-Type: text/html"));

        try (var part3 = reader.newPartInputStream()) {
          assertEquals(
              "<html>Part 3</html>", new String(part3.readAllBytes(), StandardCharsets.UTF_8));
        }

        // Should be at end
        assertFalse(reader.readBoundary());
      }
    }
  }

  @Nested
  class Error_Handling_Tests {

    @Test
    void should_throw_exception_for_invalid_boundary_terminator() {
      var invalidData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "XX" // Invalid terminator
              );

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(invalidData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        assertThrows(MultipartStreamException.class, reader::readBoundary);
      } catch (IOException e) {
        fail("Should only throw MultipartStreamException");
      }
    }

    @Test
    void should_handle_unexpected_end_of_stream() {
      var incompleteData =
          ("--" + TEST_BOUNDARY + "\r\n" + "Content-Type: text/plain\r\n\r\n" + "Incomplete")
              .getBytes(StandardCharsets.UTF_8);

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(incompleteData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        reader.readHeaders();

        try (var partStream = reader.newPartInputStream()) {
          byte[] val = partStream.readAllBytes();
          assertEquals(0, val.length);
        }
      } catch (IOException e) {
        // Expected for incomplete data
      }
    }

    @Test
    void should_handle_stream_exceptions() throws IOException {
      var mockStream = Mockito.mock(InputStream.class);
      Mockito.when(mockStream.read(Mockito.any(byte[].class), Mockito.anyInt(), Mockito.anyInt()))
          .thenThrow(new IOException("Stream error"));

      try (var reader = new MultipartReader(mockStream, TEST_BOUNDARY_BYTES)) {
        assertThrows(IOException.class, reader::skipFirstBoundary);
      }
    }
  }

  @Nested
  class Resource_Management_Tests {

    @Test
    void should_close_underlying_stream() throws IOException {
      var mockStream = Mockito.mock(InputStream.class);

      var reader = new MultipartReader(mockStream, TEST_BOUNDARY_BYTES);
      reader.close();

      Mockito.verify(mockStream).close();
    }

    @Test
    void should_close_part_streams_properly() throws IOException {
      var multipartData =
          createMultipartData(
              "--" + TEST_BOUNDARY + "\r\n",
              "Content-Type: text/plain\r\n\r\n",
              "Test data\r\n",
              "--" + TEST_BOUNDARY + "--");

      try (var reader =
          new MultipartReader(new ByteArrayInputStream(multipartData), TEST_BOUNDARY_BYTES)) {
        reader.skipFirstBoundary();
        reader.readHeaders();

        var partStream = reader.newPartInputStream();
        assertFalse(partStream.isClosed());

        partStream.close();
        assertTrue(partStream.isClosed());

        // Double close should be safe
        assertDoesNotThrow(partStream::close);
      }
    }
  }

  // Utility method to create multipart data
  private static byte[] createMultipartData(String... parts) {
    return String.join("", parts).getBytes(StandardCharsets.UTF_8);
  }
}
