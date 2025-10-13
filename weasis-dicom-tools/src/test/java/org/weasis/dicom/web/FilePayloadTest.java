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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayNameGeneration(ReplaceUnderscores.class)
class FilePayloadTest {

  @TempDir Path tempDir;

  private Path testFile;
  private final byte[] testData = "Hello, World! This is test data for FilePayload.".getBytes();

  @BeforeEach
  void setUp() throws IOException {
    testFile = tempDir.resolve("test-file.txt");
    Files.write(testFile, testData);
  }

  @Nested
  class Constructor_Tests {

    @Test
    void should_create_payload_with_valid_path() {
      var payload = new Payload.FilePayload(testFile);

      assertEquals(testFile, payload.path());
    }

    @Test
    void should_throw_exception_when_path_is_null() {
      assertThrows(NullPointerException.class, () -> new Payload.FilePayload(null));
    }
  }

  @Nested
  class Size_Tests {

    @Test
    void should_return_correct_file_size() {
      var payload = new Payload.FilePayload(testFile);

      assertEquals(testData.length, payload.size());
    }

    @Test
    void should_return_zero_for_empty_file() throws IOException {
      Path emptyFile = tempDir.resolve("empty-file.txt");
      Files.createFile(emptyFile);

      var payload = new Payload.FilePayload(emptyFile);

      assertEquals(0, payload.size());
    }

    @Test
    void should_throw_unchecked_io_exception_when_file_does_not_exist() {
      Path nonExistentFile = tempDir.resolve("non-existent.txt");
      var payload = new Payload.FilePayload(nonExistentFile);

      UncheckedIOException exception = assertThrows(UncheckedIOException.class, payload::size);
      assertInstanceOf(NoSuchFileException.class, exception.getCause());
    }

    @Test
    void should_handle_large_file() throws IOException {
      Path largeFile = tempDir.resolve("large-file.dat");
      byte[] largeData = new byte[10000];
      Files.write(largeFile, largeData);

      var payload = new Payload.FilePayload(largeFile);

      assertEquals(10000, payload.size());
    }
  }

  @Nested
  class New_Input_Stream_Tests {

    @Test
    void should_create_input_stream_for_existing_file() throws IOException {
      var payload = new Payload.FilePayload(testFile);

      try (InputStream inputStream = payload.newInputStream()) {
        assertNotNull(inputStream);
        byte[] readData = inputStream.readAllBytes();
        assertArrayEquals(testData, readData);
      }
    }

    @Test
    void should_create_fresh_stream_on_each_call() throws IOException {
      var payload = new Payload.FilePayload(testFile);

      try (InputStream stream1 = payload.newInputStream();
          InputStream stream2 = payload.newInputStream()) {

        assertNotSame(stream1, stream2);

        byte[] data1 = stream1.readAllBytes();
        byte[] data2 = stream2.readAllBytes();

        assertArrayEquals(testData, data1);
        assertArrayEquals(testData, data2);
        assertArrayEquals(data1, data2);
      }
    }

    @Test
    void should_create_stream_positioned_at_beginning() throws IOException {
      var payload = new Payload.FilePayload(testFile);

      try (InputStream stream1 = payload.newInputStream()) {
        // Read first few bytes
        byte[] firstBytes = new byte[5];
        assertEquals(5, stream1.read(firstBytes));
        assertArrayEquals("Hello".getBytes(), firstBytes);

        // Create new stream - should be at beginning
        try (InputStream stream2 = payload.newInputStream()) {
          byte[] newFirstBytes = new byte[5];
          assertEquals(5, stream2.read(newFirstBytes));
          assertArrayEquals("Hello".getBytes(), newFirstBytes);
        }
      }
    }

    @Test
    void should_handle_empty_file() throws IOException {
      Path emptyFile = tempDir.resolve("empty.txt");
      Files.createFile(emptyFile);
      var payload = new Payload.FilePayload(emptyFile);

      try (InputStream inputStream = payload.newInputStream()) {
        assertNotNull(inputStream);
        assertEquals(-1, inputStream.read());
        assertEquals(0, inputStream.readAllBytes().length);
      }
    }

    @Test
    void should_throw_unchecked_io_exception_when_file_does_not_exist() {
      Path nonExistentFile = tempDir.resolve("missing.txt");
      var payload = new Payload.FilePayload(nonExistentFile);

      UncheckedIOException exception =
          assertThrows(UncheckedIOException.class, payload::newInputStream);
      assertInstanceOf(NoSuchFileException.class, exception.getCause());
    }
  }

  @Nested
  class Factory_Method_Tests {

    @Test
    void should_create_file_payload_using_factory_method() {
      Payload payload = Payload.ofPath(testFile);

      assertInstanceOf(Payload.FilePayload.class, payload);
      assertEquals(testData.length, payload.size());
    }

    @Test
    void should_throw_exception_when_factory_method_receives_null() {
      assertThrows(NullPointerException.class, () -> Payload.ofPath(null));
    }
  }

  @Nested
  class Record_Behavior_Tests {

    @Test
    void should_implement_equals_correctly() {
      var payload1 = new Payload.FilePayload(testFile);
      var payload2 = new Payload.FilePayload(testFile);
      Path otherFile = tempDir.resolve("other.txt");
      var payload3 = new Payload.FilePayload(otherFile);

      assertEquals(payload1, payload2);
      assertNotEquals(payload1, payload3);
    }

    @Test
    void should_implement_hashcode_correctly() {
      var payload1 = new Payload.FilePayload(testFile);
      var payload2 = new Payload.FilePayload(testFile);

      assertEquals(payload1.hashCode(), payload2.hashCode());
    }

    @Test
    void should_implement_toString_correctly() {
      var payload = new Payload.FilePayload(testFile);
      String toString = payload.toString();

      assertTrue(toString.contains("FilePayload"));
      assertTrue(toString.contains(testFile.toString()));
    }

    @Test
    void should_provide_path_accessor() {
      var payload = new Payload.FilePayload(testFile);

      assertEquals(testFile, payload.path());
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void should_work_with_different_file_types() throws IOException {
      // Test with binary data
      Path binaryFile = tempDir.resolve("binary.dat");
      byte[] binaryData = {0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
      Files.write(binaryFile, binaryData);

      var payload = new Payload.FilePayload(binaryFile);

      assertEquals(binaryData.length, payload.size());
      try (InputStream stream = payload.newInputStream()) {
        byte[] readData = stream.readAllBytes();
        assertArrayEquals(binaryData, readData);
      }
    }

    @Test
    void should_handle_file_with_special_characters_in_name() throws IOException {
      Path specialFile = tempDir.resolve("test-file-with-spaces and symbols!@#.txt");
      Files.write(specialFile, testData);

      var payload = new Payload.FilePayload(specialFile);

      assertEquals(testData.length, payload.size());
      try (InputStream stream = payload.newInputStream()) {
        byte[] readData = stream.readAllBytes();
        assertArrayEquals(testData, readData);
      }
    }
  }
}
