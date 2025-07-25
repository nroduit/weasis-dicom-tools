/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExtendSegmentedInputImageStreamTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Should create stream with valid parameters")
    void shouldCreateStreamWithValidParameters() {
      // Given
      Path path = Paths.get("test", "image.dcm");
      long[] positions = {0L, 1024L, 2048L};
      int[] lengths = {512, 1024, 256};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      // When
      ExtendSegmentedInputImageStream stream =
          new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor);

      // Then
      assertEquals(path, stream.path());
      assertArrayEquals(positions, stream.segmentPositions());
      assertArrayEquals(lengths, stream.segmentLengths());
      assertEquals(descriptor, stream.imageDescriptor());
    }

    @Test
    @DisplayName("Should create stream with single segment")
    void shouldCreateStreamWithSingleSegment() {
      // Given
      Path path = Paths.get("single", "segment.dcm");
      long[] positions = {100L};
      int[] lengths = {2048};
      ImageDescriptor descriptor = createMultiFrameImageDescriptor();

      // When
      ExtendSegmentedInputImageStream stream =
          new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor);

      // Then
      assertEquals(1, stream.getSegmentCount());
      assertEquals(2048L, stream.getTotalLength());
    }

    @Test
    @DisplayName("Should throw NullPointerException for null path")
    void shouldThrowExceptionForNullPath() {
      // Given
      long[] positions = {0L};
      int[] lengths = {512};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      // When & Then
      NullPointerException exception =
          assertThrows(
              NullPointerException.class,
              () -> new ExtendSegmentedInputImageStream(null, positions, lengths, descriptor));
      assertEquals("Path cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw NullPointerException for null segment positions")
    void shouldThrowExceptionForNullSegmentPositions() {
      // Given
      Path path = Paths.get("test.dcm");
      int[] lengths = {512};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      // When & Then
      NullPointerException exception =
          assertThrows(
              NullPointerException.class,
              () -> new ExtendSegmentedInputImageStream(path, null, lengths, descriptor));
      assertEquals("Segment positions cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw NullPointerException for null segment lengths")
    void shouldThrowExceptionForNullSegmentLengths() {
      // Given
      Path path = Paths.get("test.dcm");
      long[] positions = {0L};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      // When & Then
      NullPointerException exception =
          assertThrows(
              NullPointerException.class,
              () -> new ExtendSegmentedInputImageStream(path, positions, null, descriptor));
      assertEquals("Segment lengths cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw NullPointerException for null image descriptor")
    void shouldThrowExceptionForNullImageDescriptor() {
      // Given
      Path path = Paths.get("test.dcm");
      long[] positions = {0L};
      int[] lengths = {512};

      // When & Then
      NullPointerException exception =
          assertThrows(
              NullPointerException.class,
              () -> new ExtendSegmentedInputImageStream(path, positions, lengths, null));
      assertEquals("Image descriptor cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for mismatched array lengths")
    void shouldThrowExceptionForMismatchedArrayLengths() {
      // Given
      Path path = Paths.get("test.dcm");
      long[] positions = {0L, 1024L};
      int[] lengths = {512};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      // When & Then
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor));
      assertEquals(
          "Segment positions and lengths arrays must have the same length", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for empty arrays")
    void shouldThrowExceptionForEmptyArrays() {
      // Given
      Path path = Paths.get("test.dcm");
      long[] positions = {};
      int[] lengths = {};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      // When & Then
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor));
      assertEquals("At least one segment must be defined", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, -100L, -1024L})
    @DisplayName("Should throw IllegalArgumentException for negative positions")
    void shouldThrowExceptionForNegativePositions(long negativePosition) {
      // Given
      Path path = Paths.get("test.dcm");
      long[] positions = {0L, negativePosition};
      int[] lengths = {512, 1024};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      // When & Then
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor));
      assertTrue(exception.getMessage().contains("Segment position cannot be negative"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    @DisplayName("Should throw IllegalArgumentException for non-positive lengths")
    void shouldThrowExceptionForNonPositiveLengths(int invalidLength) {
      // Given
      Path path = Paths.get("test.dcm");
      long[] positions = {0L, 1024L};
      int[] lengths = {512, invalidLength};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      // When & Then
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor));
      assertTrue(exception.getMessage().contains("Segment length must be positive"));
    }
  }

  @Nested
  @DisplayName("Method Tests")
  class MethodTests {

    @Test
    @DisplayName("Should return correct segment count")
    void shouldReturnCorrectSegmentCount() {
      // Given
      ExtendSegmentedInputImageStream stream = createStreamWithMultipleSegments();

      // When & Then
      assertEquals(4, stream.getSegmentCount());
    }

    @Test
    @DisplayName("Should calculate correct total length")
    void shouldCalculateCorrectTotalLength() {
      // Given
      long[] positions = {0L, 512L, 1536L};
      int[] lengths = {512, 1024, 256};
      ExtendSegmentedInputImageStream stream =
          new ExtendSegmentedInputImageStream(
              Paths.get("test.dcm"), positions, lengths, createBasicImageDescriptor());

      // When & Then
      assertEquals(1792L, stream.getTotalLength()); // 512 + 1024 + 256
    }

    @Test
    @DisplayName("Should return defensive copies of arrays")
    void shouldReturnDefensiveCopiesOfArrays() {
      // Given
      long[] originalPositions = {0L, 1024L};
      int[] originalLengths = {512, 1024};
      ExtendSegmentedInputImageStream stream =
          new ExtendSegmentedInputImageStream(
              Paths.get("test.dcm"),
              originalPositions,
              originalLengths,
              createBasicImageDescriptor());

      // When
      long[] returnedPositions = stream.segmentPositions();
      int[] returnedLengths = stream.segmentLengths();

      // Modify the returned arrays
      returnedPositions[0] = 999L;
      returnedLengths[0] = 999;

      // Then - original data should be unchanged
      assertEquals(0L, stream.segmentPositions()[0]);
      assertEquals(512, stream.segmentLengths()[0]);
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("Should be equal to itself")
    void shouldBeEqualToItself() {
      // Given
      ExtendSegmentedInputImageStream stream = createStreamWithMultipleSegments();

      // When & Then
      assertEquals(stream, stream);
      assertEquals(stream.hashCode(), stream.hashCode());
    }

    @Test
    @DisplayName("Should be equal when all fields match")
    void shouldBeEqualWhenAllFieldsMatch() {
      // Given
      Path path = Paths.get("identical", "path.dcm");
      long[] positions = {100L, 200L, 300L};
      int[] lengths = {50, 100, 75};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      ExtendSegmentedInputImageStream stream1 =
          new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor);
      ExtendSegmentedInputImageStream stream2 =
          new ExtendSegmentedInputImageStream(path, positions.clone(), lengths.clone(), descriptor);

      // When & Then
      assertEquals(stream1, stream2);
      assertEquals(stream1.hashCode(), stream2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal to null")
    void shouldNotBeEqualToNull() {
      // Given
      ExtendSegmentedInputImageStream stream = createStreamWithMultipleSegments();

      // When & Then
      assertNotEquals(stream, null);
    }

    @Test
    @DisplayName("Should not be equal to different class")
    void shouldNotBeEqualToDifferentClass() {
      // Given
      ExtendSegmentedInputImageStream stream = createStreamWithMultipleSegments();

      // When & Then
      assertNotEquals(stream, "not a stream");
      assertNotEquals(stream, new Object());
    }

    @Test
    @DisplayName("Should not be equal when paths differ")
    void shouldNotBeEqualWhenPathsDiffer() {
      // Given
      long[] positions = {0L, 1024L};
      int[] lengths = {512, 1024};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      ExtendSegmentedInputImageStream stream1 =
          new ExtendSegmentedInputImageStream(
              Paths.get("path1.dcm"), positions, lengths, descriptor);
      ExtendSegmentedInputImageStream stream2 =
          new ExtendSegmentedInputImageStream(
              Paths.get("path2.dcm"), positions, lengths, descriptor);

      // When & Then
      assertNotEquals(stream1, stream2);
      assertNotEquals(stream1.hashCode(), stream2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when segment positions differ")
    void shouldNotBeEqualWhenSegmentPositionsDiffer() {
      // Given
      Path path = Paths.get("same.dcm");
      int[] lengths = {512, 1024};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      ExtendSegmentedInputImageStream stream1 =
          new ExtendSegmentedInputImageStream(path, new long[] {0L, 1024L}, lengths, descriptor);
      ExtendSegmentedInputImageStream stream2 =
          new ExtendSegmentedInputImageStream(path, new long[] {100L, 1124L}, lengths, descriptor);

      // When & Then
      assertNotEquals(stream1, stream2);
      assertNotEquals(stream1.hashCode(), stream2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when segment lengths differ")
    void shouldNotBeEqualWhenSegmentLengthsDiffer() {
      // Given
      Path path = Paths.get("same.dcm");
      long[] positions = {0L, 1024L};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      ExtendSegmentedInputImageStream stream1 =
          new ExtendSegmentedInputImageStream(path, positions, new int[] {512, 1024}, descriptor);
      ExtendSegmentedInputImageStream stream2 =
          new ExtendSegmentedInputImageStream(path, positions, new int[] {256, 2048}, descriptor);

      // When & Then
      assertNotEquals(stream1, stream2);
      assertNotEquals(stream1.hashCode(), stream2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when image descriptors differ")
    void shouldNotBeEqualWhenImageDescriptorsDiffer() {
      // Given
      Path path = Paths.get("same.dcm");
      long[] positions = {0L, 1024L};
      int[] lengths = {512, 1024};

      ExtendSegmentedInputImageStream stream1 =
          new ExtendSegmentedInputImageStream(
              path, positions, lengths, createBasicImageDescriptor());
      ExtendSegmentedInputImageStream stream2 =
          new ExtendSegmentedInputImageStream(
              path, positions, lengths, createMultiFrameImageDescriptor());

      // When & Then
      assertNotEquals(stream1, stream2);
      assertNotEquals(stream1.hashCode(), stream2.hashCode());
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {

    @Test
    @DisplayName("Should contain essential information in string representation")
    void shouldContainEssentialInformationInStringRepresentation() {
      // Given
      Path path = Paths.get("test", "data", "image.dcm");
      long[] positions = {0L, 1024L, 2048L};
      int[] lengths = {512, 1024, 256};
      ExtendSegmentedInputImageStream stream =
          new ExtendSegmentedInputImageStream(
              path, positions, lengths, createBasicImageDescriptor());

      // When
      String toString = stream.toString();

      // Then
      assertAll(
          () -> assertTrue(toString.contains("ExtendSegmentedInputImageStream")),
          () -> assertTrue(toString.contains("image.dcm")),
          () -> assertTrue(toString.contains("segments=3")),
          () -> assertTrue(toString.contains("totalLength=1792")));
    }

    @Test
    @DisplayName("Should format single segment correctly in string representation")
    void shouldFormatSingleSegmentCorrectlyInStringRepresentation() {
      // Given
      ExtendSegmentedInputImageStream stream =
          new ExtendSegmentedInputImageStream(
              Paths.get("single.dcm"),
              new long[] {0L},
              new int[] {1024},
              createBasicImageDescriptor());

      // When
      String toString = stream.toString();

      // Then
      assertAll(
          () -> assertTrue(toString.contains("single.dcm")),
          () -> assertTrue(toString.contains("segments=1")),
          () -> assertTrue(toString.contains("totalLength=1024")));
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Tests")
  class EdgeCasesAndBoundaryTests {

    @Test
    @DisplayName("Should handle maximum long values for positions")
    void shouldHandleMaximumLongValuesForPositions() {
      // Given
      Path path = Paths.get("large.dcm");
      long[] positions = {Long.MAX_VALUE - 1000, Long.MAX_VALUE - 500};
      int[] lengths = {500, 500};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      // When & Then
      assertDoesNotThrow(
          () -> new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor));
    }

    @Test
    @DisplayName("Should handle maximum integer values for lengths")
    void shouldHandleMaximumIntegerValuesForLengths() {
      // Given
      Path path = Paths.get("huge.dcm");
      long[] positions = {0L, Integer.MAX_VALUE};
      int[] lengths = {Integer.MAX_VALUE, 1000};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      // When & Then
      assertDoesNotThrow(
          () -> new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor));
    }

    @Test
    @DisplayName("Should handle large number of segments")
    void shouldHandleLargeNumberOfSegments() {
      // Given
      int segmentCount = 1000;
      Path path = Paths.get("many-segments.dcm");
      long[] positions = new long[segmentCount];
      int[] lengths = new int[segmentCount];

      for (int i = 0; i < segmentCount; i++) {
        positions[i] = i * 1024L;
        lengths[i] = 1024;
      }

      ImageDescriptor descriptor = createBasicImageDescriptor();

      // When
      ExtendSegmentedInputImageStream stream =
          new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor);

      // When & Then
      assertAll(
          () -> assertEquals(segmentCount, stream.getSegmentCount()),
          () -> assertEquals(segmentCount * 1024L, stream.getTotalLength()));
    }

    @Test
    @DisplayName("Should handle complex path structures")
    void shouldHandleComplexPathStructures() {
      // Given
      Path complexPath =
          Paths.get(
              "very",
              "deep",
              "directory",
              "structure",
              "with",
              "spaces in name",
              "file name with spaces.dcm");
      long[] positions = {0L};
      int[] lengths = {1024};
      ImageDescriptor descriptor = createBasicImageDescriptor();

      // When
      ExtendSegmentedInputImageStream stream =
          new ExtendSegmentedInputImageStream(complexPath, positions, lengths, descriptor);

      // Then
      assertEquals(complexPath, stream.path());
      assertTrue(stream.toString().contains("file name with spaces.dcm"));
    }
  }

  // Helper methods for creating test data

  private ExtendSegmentedInputImageStream createStreamWithMultipleSegments() {
    Path path = Paths.get("multi", "segment.dcm");
    long[] positions = {0L, 512L, 1024L, 2048L};
    int[] lengths = {512, 512, 1024, 256};
    ImageDescriptor descriptor = createBasicImageDescriptor();

    return new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor);
  }

  private ImageDescriptor createBasicImageDescriptor() {
    Attributes attributes = new Attributes();
    attributes.setInt(Tag.Rows, VR.US, 256);
    attributes.setInt(Tag.Columns, VR.US, 256);
    attributes.setInt(Tag.SamplesPerPixel, VR.US, 1);
    attributes.setInt(Tag.BitsAllocated, VR.US, 8);
    attributes.setInt(Tag.BitsStored, VR.US, 8);
    attributes.setInt(Tag.HighBit, VR.US, 7);
    attributes.setInt(Tag.PixelRepresentation, VR.US, 0);
    attributes.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
    return new ImageDescriptor(attributes);
  }

  private ImageDescriptor createMultiFrameImageDescriptor() {
    Attributes attributes = new Attributes();
    attributes.setInt(Tag.Rows, VR.US, 512);
    attributes.setInt(Tag.Columns, VR.US, 512);
    attributes.setInt(Tag.SamplesPerPixel, VR.US, 3);
    attributes.setInt(Tag.BitsAllocated, VR.US, 8);
    attributes.setInt(Tag.BitsStored, VR.US, 8);
    attributes.setInt(Tag.HighBit, VR.US, 7);
    attributes.setInt(Tag.PixelRepresentation, VR.US, 0);
    attributes.setInt(Tag.NumberOfFrames, VR.IS, 10);
    attributes.setString(Tag.PhotometricInterpretation, VR.CS, "RGB");
    return new ImageDescriptor(attributes);
  }
}
