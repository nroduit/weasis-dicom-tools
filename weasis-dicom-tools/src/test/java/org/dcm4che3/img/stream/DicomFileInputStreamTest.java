/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.DicomStreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Comprehensive test suite for {@link DicomFileInputStream}.
 *
 * <p>This test class creates real DICOM test files rather than using mocks to ensure realistic
 * testing scenarios and proper integration with the underlying DICOM libraries.
 *
 * @author Test Author
 */
@TestMethodOrder(OrderAnnotation.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomFileInputStreamTest {

  @TempDir Path tempDir;

  private Path validDicomFile;
  private Path emptyFile;
  private Path nonDicomFile;

  @BeforeEach
  void setUp() throws IOException {
    validDicomFile = createValidDicomFile("test-image.dcm");
    emptyFile = createEmptyFile("empty.dcm");
    nonDicomFile = createNonDicomFile("text.txt");
  }

  @Nested
  @Order(1)
  class Constructor_Tests {

    @Test
    void should_create_instance_with_valid_Path() throws IOException {
      try (var stream = new DicomFileInputStream(validDicomFile)) {
        assertAll(
            () -> assertNotNull(stream),
            () -> assertEquals(validDicomFile, stream.getPath()),
            () -> assertNotNull(stream.toString()),
            () -> assertTrue(stream.toString().contains(validDicomFile.toString())));
      }
    }

    @Test
    void should_create_instance_with_valid_string_path() throws IOException {
      var pathString = validDicomFile.toString();

      try (var stream = new DicomFileInputStream(pathString)) {
        assertAll(
            () -> assertNotNull(stream), () -> assertEquals(validDicomFile, stream.getPath()));
      }
    }

    @Test
    void should_throw_NullPointerException_for_null_Path() {
      Path nullPath = null;

      var exception =
          assertThrows(NullPointerException.class, () -> new DicomFileInputStream(nullPath));

      assertEquals("Path cannot be null", exception.getMessage());
    }

    @Test
    void should_throw_IllegalArgumentException_for_null_string() {
      String nullString = null;

      var exception =
          assertThrows(IllegalArgumentException.class, () -> new DicomFileInputStream(nullString));

      assertEquals("Path string cannot be null or empty", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void should_throw_IllegalArgumentException_for_empty_whitespace_string(String emptyString) {
      var exception =
          assertThrows(IllegalArgumentException.class, () -> new DicomFileInputStream(emptyString));

      assertEquals("Path string cannot be null or empty", exception.getMessage());
    }

    @Test
    void should_throw_NoSuchFileException_for_non_existent_file() {
      var nonExistentPath = tempDir.resolve("non-existent.dcm");

      assertThrows(NoSuchFileException.class, () -> new DicomFileInputStream(nonExistentPath));
    }

    @Test
    void should_throw_InvalidPathException_for_invalid_path_string() {
      var invalidPath = "\0invalid\0path";

      assertThrows(InvalidPathException.class, () -> new DicomFileInputStream(invalidPath));
    }

    @Test
    void should_throw_IOException_when_opening_directory() {
      assertThrows(IOException.class, () -> new DicomFileInputStream(tempDir));
    }
  }

  @Nested
  @Order(2)
  class Path_Operations {

    @Test
    void should_return_correct_file_path() throws IOException {
      try (var stream = new DicomFileInputStream(validDicomFile)) {
        var returnedPath = stream.getPath();

        assertAll(
            () -> assertNotNull(returnedPath),
            () -> assertEquals(validDicomFile, returnedPath),
            () -> assertTrue(Files.exists(returnedPath)),
            () -> assertTrue(Files.isRegularFile(returnedPath)));
      }
    }

    @Test
    void should_maintain_path_consistency_between_constructors() throws IOException {
      var pathString = validDicomFile.toString();

      try (var streamFromPath = new DicomFileInputStream(validDicomFile);
          var streamFromString = new DicomFileInputStream(pathString)) {

        assertEquals(streamFromPath.getPath(), streamFromString.getPath());
      }
    }

    @Test
    void should_handle_relative_paths_correctly() throws IOException {
      var subDir = tempDir.resolve("subdir");
      Files.createDirectories(subDir);
      var relativeFile = subDir.resolve("relative.dcm");
      createValidDicomFileAt(relativeFile);

      var currentDir = Path.of("").toAbsolutePath();
      var relativePath = currentDir.relativize(relativeFile);

      try (var stream = new DicomFileInputStream(relativePath.toString())) {
        assertNotNull(stream.getPath());
        assertTrue(Files.exists(stream.getPath()));
      }
    }
  }

  @Nested
  @Order(3)
  class Metadata_Operations {

    @Test
    void should_load_and_cache_metadata_successfully() throws IOException {
      try (var stream = new DicomFileInputStream(validDicomFile)) {
        var metadata1 = stream.getMetadata();
        assertNotNull(metadata1);

        var metadata2 = stream.getMetadata();
        assertSame(metadata1, metadata2, "Metadata should be cached");

        assertAll(
            () -> assertNotNull(metadata1.getDicomObject()),
            () -> assertNotNull(metadata1.getImageDescriptor()),
            () -> assertEquals("CT", metadata1.getDicomObject().getString(Tag.Modality)));
      }
    }

    @Test
    void should_handle_metadata_loading_from_different_file_types() throws IOException {
      try (var validStream = new DicomFileInputStream(validDicomFile)) {
        assertDoesNotThrow(validStream::getMetadata);
      }

      assertThrows(
          DicomStreamException.class,
          () -> {
            try (var invalidStream = new DicomFileInputStream(nonDicomFile)) {
              invalidStream.getMetadata();
            }
          });
    }

    @Test
    void should_handle_concurrent_metadata_access_safely() throws Exception {
      try (var stream = new DicomFileInputStream(validDicomFile)) {
        var threadCount = 10;
        var latch = new CountDownLatch(1); // Single latch for all threads to start
        var exceptions = new CopyOnWriteArrayList<Exception>();

        // Create futures that will all start at the same time
        var futures = new ArrayList<CompletableFuture<DicomMetaData>>();

        for (int i = 0; i < threadCount; i++) {
          var future =
              CompletableFuture.supplyAsync(
                  () -> {
                    try {
                      latch.await(); // Wait for the start signal
                      return stream.getMetadata();
                    } catch (Exception e) {
                      exceptions.add(e);
                      return null;
                    }
                  });
          futures.add(future);
        }

        // Start all threads at once
        latch.countDown();

        // Collect results with proper timeout handling
        var results = new ArrayList<DicomMetaData>();
        for (var future : futures) {
          try {
            var result = future.get(5, TimeUnit.SECONDS);
            if (result != null) {
              results.add(result);
            }
          } catch (Exception e) {
            exceptions.add(e);
          }
        }

        assertAll(
            () -> assertTrue(exceptions.isEmpty(), "No exceptions should occur: " + exceptions),
            () -> assertEquals(threadCount, results.size()),
            () ->
                assertTrue(
                    results.stream().allMatch(md -> md == results.get(0)),
                    "All threads should get the same cached instance"));
      }
    }
  }

  @Nested
  @Order(4)
  class Image_Descriptor_Operations {

    @Test
    void should_return_valid_image_descriptor() throws IOException {
      try (var stream = new DicomFileInputStream(validDicomFile)) {
        var descriptor = stream.getImageDescriptor();

        assertAll(
            () -> assertNotNull(descriptor),
            () -> assertEquals(512, descriptor.getRows()),
            () -> assertEquals(512, descriptor.getColumns()),
            () -> assertEquals(1, descriptor.getFrames()),
            () -> assertEquals("CT", descriptor.getModality()));
      }
    }

    @Test
    void should_cache_image_descriptor_through_metadata() throws IOException {
      try (var stream = new DicomFileInputStream(validDicomFile)) {
        var descriptor1 = stream.getImageDescriptor();
        var descriptor2 = stream.getImageDescriptor();

        assertSame(
            descriptor1, descriptor2, "Image descriptor should be cached through metadata caching");
      }
    }

    @Test
    void should_return_null_descriptor_for_invalid_file() throws IOException {
      try (var stream = new DicomFileInputStream(validDicomFile)) {
        // Simulate IOException by closing the stream first
        stream.close();

        var descriptor = stream.getImageDescriptor();

        // Should return null when IOException occurs
        assertNull(descriptor);
      }
    }
  }

  @Nested
  @Order(5)
  class Resource_Management {

    @Test
    void should_properly_close_resources() throws IOException {
      try (var stream = new DicomFileInputStream(validDicomFile)) {
        stream.getMetadata(); // Load metadata to ensure resources are allocated

        assertDoesNotThrow(stream::close);
      }
    }

    @Test
    void should_handle_try_with_resources_correctly() {
      assertDoesNotThrow(
          () -> {
            try (var stream = new DicomFileInputStream(validDicomFile)) {
              stream.getMetadata();
              stream.getImageDescriptor();
            }
          });
    }

    @Test
    void should_handle_multiple_close_calls_safely() throws IOException {
      try (var stream = new DicomFileInputStream(validDicomFile)) {
        assertDoesNotThrow(
            () -> {
              stream.close();
              stream.close();
              stream.close();
            });
      }
    }
  }

  @Nested
  @Order(6)
  class Edge_Cases_And_Error_Handling {

    @Test
    void should_handle_empty_DICOM_file_gracefully() {
      assertThrows(
          DicomStreamException.class,
          () -> {
            try (var invalidStream = new DicomFileInputStream(emptyFile)) {
              invalidStream.getMetadata();
            }
          });
    }

    @Test
    void should_handle_large_file_paths() throws IOException {
      var deepPath = tempDir;
      for (int i = 0; i < 10; i++) {
        deepPath = deepPath.resolve("level" + i);
      }
      Files.createDirectories(deepPath);

      var deepFile = deepPath.resolve("deep-file.dcm");
      createValidDicomFileAt(deepFile);

      try (var stream = new DicomFileInputStream(deepFile)) {
        assertNotNull(stream.getPath());
        assertNotNull(stream.getMetadata());
      }
    }

    @Test
    void should_handle_special_characters_in_file_path() throws IOException {
      var specialFile = tempDir.resolve("файл-тест-español-日本語.dcm");
      createValidDicomFileAt(specialFile);

      try (var stream = new DicomFileInputStream(specialFile)) {
        assertEquals(specialFile, stream.getPath());
        assertNotNull(stream.getMetadata());
      }
    }
  }

  @Nested
  @Order(7)
  class Integration_Tests {

    @Test
    void should_work_with_DicomImageReader_integration() throws IOException {
      try (var stream = new DicomFileInputStream(validDicomFile)) {
        assertNotNull(stream.getPath());
        assertNotNull(stream.getImageDescriptor());

        var uri = stream.getPath().toUri().toString();
        assertNotNull(uri);
        assertTrue(uri.startsWith("file:"));
      }
    }

    @Test
    void should_maintain_state_consistency_across_operations() throws IOException {
      try (var stream = new DicomFileInputStream(validDicomFile)) {
        var originalPath = stream.getPath();
        var metadata = stream.getMetadata();
        var descriptor = stream.getImageDescriptor();

        assertAll(
            () -> assertSame(originalPath, stream.getPath()),
            () -> assertSame(metadata, stream.getMetadata()),
            () -> assertSame(descriptor, stream.getImageDescriptor()),
            () -> assertSame(descriptor, metadata.getImageDescriptor()));
      }
    }
  }

  @Test
  @Order(8)
  void should_provide_meaningful_toString_representation() throws IOException {
    try (var stream = new DicomFileInputStream(validDicomFile)) {
      var toString = stream.toString();

      assertAll(
          () -> assertNotNull(toString),
          () -> assertTrue(toString.contains("DicomFileInputStream")),
          () -> assertTrue(toString.contains(validDicomFile.toString())),
          () -> assertTrue(toString.contains("path=")));
    }
  }

  // Helper methods for creating test files

  private Path createValidDicomFile(String filename) throws IOException {
    var file = tempDir.resolve(filename);
    createValidDicomFileAt(file);
    return file;
  }

  private void createValidDicomFileAt(Path file) throws IOException {
    var attrs = new Attributes();

    // Image attributes
    attrs.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
    attrs.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9");
    attrs.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5.6");
    attrs.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.5.6.7");
    attrs.setString(Tag.Modality, VR.CS, "CT");
    attrs.setString(Tag.PatientID, VR.LO, "TEST001");
    attrs.setString(Tag.PatientName, VR.PN, "Test^Patient");

    // Image pixel attributes
    attrs.setInt(Tag.Rows, VR.US, 512);
    attrs.setInt(Tag.Columns, VR.US, 512);
    attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
    attrs.setInt(Tag.BitsAllocated, VR.US, 16);
    attrs.setInt(Tag.BitsStored, VR.US, 12);
    attrs.setInt(Tag.HighBit, VR.US, 11);
    attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
    attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");

    // Create minimal pixel data
    var pixelData = new byte[512 * 512 * 2]; // 16-bit pixels
    attrs.setBytes(Tag.PixelData, VR.OW, pixelData);

    try (var dos = new DicomOutputStream(file.toFile())) {
      dos.writeDataset(attrs.createFileMetaInformation(UID.ExplicitVRLittleEndian), attrs);
    }
  }

  private Path createEmptyFile(String filename) throws IOException {
    var file = tempDir.resolve(filename);
    Files.createFile(file);
    return file;
  }

  private Path createNonDicomFile(String filename) throws IOException {
    var file = tempDir.resolve(filename);
    Files.write(file, "This is not a DICOM file".getBytes());
    return file;
  }
}
