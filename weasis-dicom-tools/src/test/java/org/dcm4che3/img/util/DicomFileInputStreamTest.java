/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.DicomStreamException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

  @AfterEach
  void tearDown() throws IOException {
    // Cleanup is handled by @TempDir
  }

  @Nested
  @Order(1)
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Should create instance with valid Path")
    void shouldCreateInstanceWithValidPath() throws IOException {
      DicomFileInputStream stream = new DicomFileInputStream(validDicomFile);

      assertAll(
          "Valid path constructor",
          () -> assertNotNull(stream),
          () -> assertEquals(validDicomFile, stream.getPath()),
          () -> assertNotNull(stream.toString()),
          () -> assertTrue(stream.toString().contains(validDicomFile.toString())));

      stream.close();
    }

    @Test
    @DisplayName("Should create instance with valid string path")
    void shouldCreateInstanceWithValidStringPath() throws IOException {
      String pathString = validDicomFile.toString();
      DicomFileInputStream stream = new DicomFileInputStream(pathString);

      assertAll(
          "Valid string path constructor",
          () -> assertNotNull(stream),
          () -> assertEquals(validDicomFile, stream.getPath()));

      stream.close();
    }

    @Test
    @DisplayName("Should throw NullPointerException for null Path")
    void shouldThrowNullPointerExceptionForNullPath() {
      Path nullPath = null;

      NullPointerException exception =
          assertThrows(NullPointerException.class, () -> new DicomFileInputStream(nullPath));

      assertEquals("Path cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null string")
    void shouldThrowIllegalArgumentExceptionForNullString() {
      String nullString = null;

      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> new DicomFileInputStream(nullString));

      assertEquals("Path string cannot be null or empty", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("Should throw IllegalArgumentException for empty/whitespace string")
    void shouldThrowIllegalArgumentExceptionForEmptyString(String emptyString) {
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> new DicomFileInputStream(emptyString));

      assertEquals("Path string cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw NoSuchFileException for non-existent file")
    void shouldThrowNoSuchFileExceptionForNonExistentFile() {
      Path nonExistentPath = tempDir.resolve("non-existent.dcm");

      assertThrows(NoSuchFileException.class, () -> new DicomFileInputStream(nonExistentPath));
    }

    @Test
    @DisplayName("Should throw InvalidPathException for invalid path string")
    void shouldThrowInvalidPathExceptionForInvalidPathString() {
      String invalidPath = "\0invalid\0path";

      assertThrows(InvalidPathException.class, () -> new DicomFileInputStream(invalidPath));
    }

    @Test
    @DisplayName("Should handle directory instead of file")
    void shouldHandleDirectoryInsteadOfFile() {
      // This should throw an IOException when trying to read from a directory
      assertThrows(IOException.class, () -> new DicomFileInputStream(tempDir));
    }
  }

  @Nested
  @Order(2)
  @DisplayName("Path Operations")
  class PathOperationTests {

    @Test
    @DisplayName("Should return correct file path")
    void shouldReturnCorrectFilePath() throws IOException {
      try (DicomFileInputStream stream = new DicomFileInputStream(validDicomFile)) {
        Path returnedPath = stream.getPath();

        assertAll(
            "Path operations",
            () -> assertNotNull(returnedPath),
            () -> assertEquals(validDicomFile, returnedPath),
            () -> assertTrue(Files.exists(returnedPath)),
            () -> assertTrue(Files.isRegularFile(returnedPath)));
      }
    }

    @Test
    @DisplayName("Should maintain path consistency between constructors")
    void shouldMaintainPathConsistencyBetweenConstructors() throws IOException {
      String pathString = validDicomFile.toString();

      try (DicomFileInputStream streamFromPath = new DicomFileInputStream(validDicomFile);
          DicomFileInputStream streamFromString = new DicomFileInputStream(pathString)) {

        assertEquals(streamFromPath.getPath(), streamFromString.getPath());
      }
    }

    @Test
    @DisplayName("Should handle relative paths correctly")
    void shouldHandleRelativePathsCorrectly() throws IOException {
      // Create a file in a subdirectory
      Path subDir = tempDir.resolve("subdir");
      Files.createDirectories(subDir);
      Path relativeFile = subDir.resolve("relative.dcm");
      createValidDicomFileAt(relativeFile);

      // Change working directory context by using relative path
      Path currentDir = Paths.get("").toAbsolutePath();
      Path relativePath = currentDir.relativize(relativeFile);

      try (DicomFileInputStream stream = new DicomFileInputStream(relativePath.toString())) {
        assertNotNull(stream.getPath());
        assertTrue(Files.exists(stream.getPath()));
      }
    }
  }

  @Nested
  @Order(3)
  @DisplayName("Metadata Operations")
  class MetadataOperationTests {

    @Test
    @DisplayName("Should load and cache metadata successfully")
    void shouldLoadAndCacheMetadataSuccessfully() throws IOException {
      try (DicomFileInputStream stream = new DicomFileInputStream(validDicomFile)) {
        // First call should load metadata
        DicomMetaData metadata1 = stream.getMetadata();
        assertNotNull(metadata1);

        // Second call should return cached instance
        DicomMetaData metadata2 = stream.getMetadata();
        assertSame(metadata1, metadata2, "Metadata should be cached");

        // Verify metadata content
        assertAll(
            "Metadata validation",
            () -> assertNotNull(metadata1.getDicomObject()),
            () -> assertNotNull(metadata1.getImageDescriptor()),
            () -> assertEquals("CT", metadata1.getDicomObject().getString(Tag.Modality)));
      }
    }

    @Test
    @DisplayName("Should handle metadata loading from different file types")
    void shouldHandleMetadataLoadingFromDifferentFileTypes() throws IOException {
      // Test with valid DICOM file
      try (DicomFileInputStream validStream = new DicomFileInputStream(validDicomFile)) {
        assertDoesNotThrow(validStream::getMetadata);
      }

      // Test with non-DICOM file should throw IOException
      assertThrows(
          DicomStreamException.class,
          () -> {
            try (DicomFileInputStream invalidStream = new DicomFileInputStream(nonDicomFile)) {
              invalidStream.getMetadata();
            }
          });
    }

    @Test
    @DisplayName("Should handle concurrent metadata access safely")
    void shouldHandleConcurrentMetadataAccessSafely() throws Exception {
      try (DicomFileInputStream stream = new DicomFileInputStream(validDicomFile)) {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<DicomMetaData>> futures = new ArrayList<>();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // Submit concurrent metadata access tasks
        for (int i = 0; i < threadCount; i++) {
          futures.add(
              executor.submit(
                  () -> {
                    try {
                      latch.countDown();
                      latch.await(); // Wait for all threads to be ready
                      return stream.getMetadata();
                    } catch (Exception e) {
                      exceptions.add(e);
                      return null;
                    }
                  }));
        }

        // Collect results
        List<DicomMetaData> results = new ArrayList<>();
        for (Future<DicomMetaData> future : futures) {
          DicomMetaData result = future.get(5, TimeUnit.SECONDS);
          if (result != null) {
            results.add(result);
          }
        }

        executor.shutdown();

        assertAll(
            "Concurrent metadata access",
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
  @DisplayName("Image Descriptor Operations")
  class ImageDescriptorOperationTests {

    @Test
    @DisplayName("Should return valid image descriptor")
    void shouldReturnValidImageDescriptor() throws IOException {
      try (DicomFileInputStream stream = new DicomFileInputStream(validDicomFile)) {
        ImageDescriptor descriptor = stream.getImageDescriptor();

        assertAll(
            "Image descriptor validation",
            () -> assertNotNull(descriptor),
            () -> assertEquals(512, descriptor.getRows()),
            () -> assertEquals(512, descriptor.getColumns()),
            () -> assertEquals(1, descriptor.getFrames()),
            () -> assertEquals("CT", descriptor.getModality()));
      }
    }

    @Test
    @DisplayName("Should cache image descriptor through metadata")
    void shouldCacheImageDescriptorThroughMetadata() throws IOException {
      try (DicomFileInputStream stream = new DicomFileInputStream(validDicomFile)) {
        ImageDescriptor descriptor1 = stream.getImageDescriptor();
        ImageDescriptor descriptor2 = stream.getImageDescriptor();

        assertSame(
            descriptor1, descriptor2, "Image descriptor should be cached through metadata caching");
      }
    }
  }

  @Nested
  @Order(5)
  @DisplayName("Resource Management")
  class ResourceManagementTests {

    @Test
    @DisplayName("Should properly close resources")
    void shouldProperlyCloseResources() throws IOException {
      DicomFileInputStream stream = new DicomFileInputStream(validDicomFile);

      // Load metadata to ensure resources are allocated
      stream.getMetadata();

      // Close should not throw exception
      assertDoesNotThrow(stream::close);
    }

    @Test
    @DisplayName("Should handle try-with-resources correctly")
    void shouldHandleTryWithResourcesCorrectly() {
      assertDoesNotThrow(
          () -> {
            try (DicomFileInputStream stream = new DicomFileInputStream(validDicomFile)) {
              stream.getMetadata();
              stream.getImageDescriptor();
              // Resource should be automatically closed
            }
          });
    }

    @Test
    @DisplayName("Should handle multiple close calls safely")
    void shouldHandleMultipleCloseCallsSafely() throws IOException {
      try (DicomFileInputStream stream = new DicomFileInputStream(validDicomFile)) {

        assertDoesNotThrow(
            () -> {
              stream.close();
              stream.close(); // Second close should be safe
              stream.close(); // Third close should be safe
            });
      }
    }
  }

  @Nested
  @Order(6)
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesAndErrorHandlingTests {

    @Test
    @DisplayName("Should handle empty DICOM file gracefully")
    void shouldHandleEmptyDicomFileGracefully() {
      assertThrows(
          DicomStreamException.class,
          () -> {
            try (DicomFileInputStream invalidStream = new DicomFileInputStream(emptyFile)) {
              invalidStream.getMetadata();
            }
          });
    }

    @Test
    @DisplayName("Should handle large file paths")
    void shouldHandleLargeFilePaths() throws IOException {
      // Create a deeply nested directory structure
      Path deepPath = tempDir;
      for (int i = 0; i < 10; i++) {
        deepPath = deepPath.resolve("level" + i);
      }
      Files.createDirectories(deepPath);

      Path deepFile = deepPath.resolve("deep-file.dcm");
      createValidDicomFileAt(deepFile);

      try (DicomFileInputStream stream = new DicomFileInputStream(deepFile)) {
        assertNotNull(stream.getPath());
        assertNotNull(stream.getMetadata());
      }
    }

    @Test
    @DisplayName("Should handle special characters in file path")
    void shouldHandleSpecialCharactersInFilePath() throws IOException {
      Path specialFile = tempDir.resolve("файл-тест-español-日本語.dcm");
      createValidDicomFileAt(specialFile);

      try (DicomFileInputStream stream = new DicomFileInputStream(specialFile)) {
        assertEquals(specialFile, stream.getPath());
        assertNotNull(stream.getMetadata());
      }
    }
  }

  @Nested
  @Order(7)
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should work with DicomImageReader integration")
    void shouldWorkWithDicomImageReaderIntegration() throws IOException {
      try (DicomFileInputStream stream = new DicomFileInputStream(validDicomFile)) {
        // This simulates how DicomImageReader would use the stream
        assertNotNull(stream.getPath());
        assertNotNull(stream.getImageDescriptor());

        // Verify URI conversion works
        String uri = stream.getPath().toUri().toString();
        assertNotNull(uri);
        assertTrue(uri.startsWith("file:"));
      }
    }

    @Test
    @DisplayName("Should maintain state consistency across operations")
    void shouldMaintainStateConsistencyAcrossOperations() throws IOException {
      try (DicomFileInputStream stream = new DicomFileInputStream(validDicomFile)) {
        Path originalPath = stream.getPath();
        DicomMetaData metadata = stream.getMetadata();
        ImageDescriptor descriptor = stream.getImageDescriptor();

        // Multiple calls should return consistent results
        assertAll(
            "State consistency",
            () -> assertSame(originalPath, stream.getPath()),
            () -> assertSame(metadata, stream.getMetadata()),
            () -> assertSame(descriptor, stream.getImageDescriptor()),
            () -> assertSame(descriptor, metadata.getImageDescriptor()));
      }
    }
  }

  @Test
  @Order(8)
  @DisplayName("Should provide meaningful toString representation")
  void shouldProvideMeaningfulToStringRepresentation() throws IOException {
    try (DicomFileInputStream stream = new DicomFileInputStream(validDicomFile)) {
      String toString = stream.toString();

      assertAll(
          "toString validation",
          () -> assertNotNull(toString),
          () -> assertTrue(toString.contains("DicomFileInputStream")),
          () -> assertTrue(toString.contains(validDicomFile.toString())),
          () -> assertTrue(toString.contains("path=")));
    }
  }

  // Helper methods for creating test files

  private Path createValidDicomFile(String filename) throws IOException {
    Path file = tempDir.resolve(filename);
    createValidDicomFileAt(file);
    return file;
  }

  private void createValidDicomFileAt(Path file) throws IOException {
    Attributes attrs = new Attributes();

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
    byte[] pixelData = new byte[512 * 512 * 2]; // 16-bit pixels
    attrs.setBytes(Tag.PixelData, VR.OW, pixelData);

    try (DicomOutputStream dos = new DicomOutputStream(file.toFile())) {
      dos.writeDataset(attrs.createFileMetaInformation(UID.ExplicitVRLittleEndian), attrs);
    }
  }

  private Path createEmptyFile(String filename) throws IOException {
    Path file = tempDir.resolve(filename);
    Files.createFile(file);
    return file;
  }

  private Path createNonDicomFile(String filename) throws IOException {
    Path file = tempDir.resolve(filename);
    Files.write(file, "This is not a DICOM file".getBytes());
    return file;
  }
}
