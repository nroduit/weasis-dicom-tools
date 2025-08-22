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
import java.util.stream.IntStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ExtendSegmentedInputImageStreamTest {

  @Nested
  class Constructor_tests {

    @Test
    void should_create_stream_with_valid_parameters() {
      var path = Path.of("test", "image.dcm");
      var positions = new long[] {0L, 1024L, 2048L};
      var lengths = new int[] {512, 1024, 256};
      var descriptor = createBasicImageDescriptor();

      var stream = new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor);

      assertAll(
          () -> assertEquals(path, stream.path()),
          () -> assertArrayEquals(positions, stream.segmentPositions()),
          () -> assertArrayEquals(lengths, stream.segmentLengths()),
          () -> assertEquals(descriptor, stream.imageDescriptor()));
    }

    @Test
    void should_create_stream_with_single_segment() {
      var path = Path.of("single", "segment.dcm");
      var positions = new long[] {100L};
      var lengths = new int[] {2048};
      var descriptor = createMultiFrameImageDescriptor();

      var stream = new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor);

      assertAll(
          () -> assertEquals(1, stream.getSegmentCount()),
          () -> assertEquals(2048L, stream.getTotalLength()));
    }

    @ParameterizedTest
    @CsvSource(
        textBlock =
            """
                    # field,                errorMessage
                    path,                   'Path cannot be null'
                    segmentPositions,       'Segment positions cannot be null'
                    segmentLengths,         'Segment lengths cannot be null'
                    imageDescriptor,        'Image descriptor cannot be null'
                    """)
    void should_throw_exception_for_null_parameters(String field, String errorMessage) {
      var path = Path.of("test.dcm");
      var positions = new long[] {0L};
      var lengths = new int[] {512};
      var descriptor = createBasicImageDescriptor();

      Executable executable =
          switch (field) {
            case "path" ->
                () -> new ExtendSegmentedInputImageStream(null, positions, lengths, descriptor);
            case "segmentPositions" ->
                () -> new ExtendSegmentedInputImageStream(path, null, lengths, descriptor);
            case "segmentLengths" ->
                () -> new ExtendSegmentedInputImageStream(path, positions, null, descriptor);
            case "imageDescriptor" ->
                () -> new ExtendSegmentedInputImageStream(path, positions, lengths, null);
            default -> throw new IllegalArgumentException("Unknown field: " + field);
          };

      var exception = assertThrows(NullPointerException.class, executable);
      assertEquals(errorMessage, exception.getMessage());
    }

    @Test
    void should_throw_exception_for_mismatched_array_lengths() {
      var path = Path.of("test.dcm");
      var positions = new long[] {0L, 1024L};
      var lengths = new int[] {512};
      var descriptor = createBasicImageDescriptor();

      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor));

      assertEquals(
          "Segment positions and lengths arrays must have the same length", exception.getMessage());
    }

    @Test
    void should_throw_exception_for_empty_arrays() {
      var path = Path.of("test.dcm");
      var positions = new long[] {};
      var lengths = new int[] {};
      var descriptor = createBasicImageDescriptor();

      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor));

      assertEquals("At least one segment must be defined", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, -100L, -1024L})
    void should_throw_exception_for_negative_positions(long negativePosition) {
      var path = Path.of("test.dcm");
      var positions = new long[] {0L, negativePosition};
      var lengths = new int[] {512, 1024};
      var descriptor = createBasicImageDescriptor();

      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor));

      assertTrue(exception.getMessage().contains("Segment position cannot be negative"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void should_throw_exception_for_non_positive_lengths(int invalidLength) {
      var path = Path.of("test.dcm");
      var positions = new long[] {0L, 1024L};
      var lengths = new int[] {512, invalidLength};
      var descriptor = createBasicImageDescriptor();

      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor));

      assertTrue(exception.getMessage().contains("Segment length must be positive"));
    }
  }

  @Nested
  class Method_tests {

    @Test
    void should_return_correct_segment_count() {
      var stream = createStreamWithMultipleSegments();
      assertEquals(4, stream.getSegmentCount());
    }

    @Test
    void should_calculate_correct_total_length() {
      var positions = new long[] {0L, 512L, 1536L};
      var lengths = new int[] {512, 1024, 256};
      var stream =
          new ExtendSegmentedInputImageStream(
              Path.of("test.dcm"), positions, lengths, createBasicImageDescriptor());

      assertEquals(1792L, stream.getTotalLength()); // 512 + 1024 + 256
    }

    @Test
    void should_return_defensive_copies_of_arrays() {
      var originalPositions = new long[] {0L, 1024L};
      var originalLengths = new int[] {512, 1024};
      var stream =
          new ExtendSegmentedInputImageStream(
              Path.of("test.dcm"),
              originalPositions,
              originalLengths,
              createBasicImageDescriptor());

      var returnedPositions = stream.segmentPositions();
      var returnedLengths = stream.segmentLengths();

      // Modify returned arrays
      returnedPositions[0] = 999L;
      returnedLengths[0] = 999;

      // Original data should be unchanged
      assertAll(
          () -> assertEquals(0L, stream.segmentPositions()[0]),
          () -> assertEquals(512, stream.segmentLengths()[0]));
    }
  }

  @Nested
  class Equals_and_hashCode_tests {

    @Test
    void should_be_equal_to_itself() {
      var stream = createStreamWithMultipleSegments();

      assertAll(
          () -> assertEquals(stream, stream),
          () -> assertEquals(stream.hashCode(), stream.hashCode()));
    }

    @Test
    void should_be_equal_when_all_fields_match() {
      var path = Path.of("identical", "path.dcm");
      var positions = new long[] {100L, 200L, 300L};
      var lengths = new int[] {50, 100, 75};
      var descriptor = createBasicImageDescriptor();

      var stream1 = new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor);
      var stream2 =
          new ExtendSegmentedInputImageStream(path, positions.clone(), lengths.clone(), descriptor);

      assertAll(
          () -> assertEquals(stream1, stream2),
          () -> assertEquals(stream1.hashCode(), stream2.hashCode()));
    }

    @Test
    void should_not_be_equal_to_null_or_different_class() {
      var stream = createStreamWithMultipleSegments();

      assertAll(
          () -> assertNotEquals(null, stream),
          () -> assertNotEquals("not a stream", stream),
          () -> assertNotEquals(new Object(), stream));
    }

    @Test
    void should_not_be_equal_when_paths_differ() {
      var positions = new long[] {0L, 1024L};
      var lengths = new int[] {512, 1024};
      var descriptor = createBasicImageDescriptor();

      var stream1 =
          new ExtendSegmentedInputImageStream(Path.of("path1.dcm"), positions, lengths, descriptor);
      var stream2 =
          new ExtendSegmentedInputImageStream(Path.of("path2.dcm"), positions, lengths, descriptor);

      assertAll(
          () -> assertNotEquals(stream1, stream2),
          () -> assertNotEquals(stream1.hashCode(), stream2.hashCode()));
    }

    @Test
    void should_not_be_equal_when_segment_positions_differ() {
      var path = Path.of("same.dcm");
      var lengths = new int[] {512, 1024};
      var descriptor = createBasicImageDescriptor();

      var stream1 =
          new ExtendSegmentedInputImageStream(path, new long[] {0L, 1024L}, lengths, descriptor);
      var stream2 =
          new ExtendSegmentedInputImageStream(path, new long[] {100L, 1124L}, lengths, descriptor);

      assertAll(
          () -> assertNotEquals(stream1, stream2),
          () -> assertNotEquals(stream1.hashCode(), stream2.hashCode()));
    }

    @Test
    void should_not_be_equal_when_segment_lengths_differ() {
      var path = Path.of("same.dcm");
      var positions = new long[] {0L, 1024L};
      var descriptor = createBasicImageDescriptor();

      var stream1 =
          new ExtendSegmentedInputImageStream(path, positions, new int[] {512, 1024}, descriptor);
      var stream2 =
          new ExtendSegmentedInputImageStream(path, positions, new int[] {256, 2048}, descriptor);

      assertAll(
          () -> assertNotEquals(stream1, stream2),
          () -> assertNotEquals(stream1.hashCode(), stream2.hashCode()));
    }

    @Test
    void should_not_be_equal_when_image_descriptors_differ() {
      var path = Path.of("same.dcm");
      var positions = new long[] {0L, 1024L};
      var lengths = new int[] {512, 1024};

      var stream1 =
          new ExtendSegmentedInputImageStream(
              path, positions, lengths, createBasicImageDescriptor());
      var stream2 =
          new ExtendSegmentedInputImageStream(
              path, positions, lengths, createMultiFrameImageDescriptor());

      assertAll(
          () -> assertNotEquals(stream1, stream2),
          () -> assertNotEquals(stream1.hashCode(), stream2.hashCode()));
    }
  }

  @Nested
  class ToString_tests {

    @Test
    void should_contain_essential_information_in_string_representation() {
      var path = Path.of("test", "data", "image.dcm");
      var positions = new long[] {0L, 1024L, 2048L};
      var lengths = new int[] {512, 1024, 256};
      var stream =
          new ExtendSegmentedInputImageStream(
              path, positions, lengths, createBasicImageDescriptor());

      var toString = stream.toString();

      assertAll(
          () -> assertTrue(toString.contains("ExtendSegmentedInputImageStream")),
          () -> assertTrue(toString.contains("image.dcm")),
          () -> assertTrue(toString.contains("segments=3")),
          () -> assertTrue(toString.contains("totalLength=1792")));
    }

    @Test
    void should_format_single_segment_correctly() {
      var stream =
          new ExtendSegmentedInputImageStream(
              Path.of("single.dcm"),
              new long[] {0L},
              new int[] {1024},
              createBasicImageDescriptor());

      var toString = stream.toString();

      assertAll(
          () -> assertTrue(toString.contains("single.dcm")),
          () -> assertTrue(toString.contains("segments=1")),
          () -> assertTrue(toString.contains("totalLength=1024")));
    }
  }

  @Nested
  class Edge_cases_and_boundary_tests {

    @Test
    void should_handle_maximum_long_values_for_positions() {
      var path = Path.of("large.dcm");
      var positions = new long[] {Long.MAX_VALUE - 1000, Long.MAX_VALUE - 500};
      var lengths = new int[] {500, 500};
      var descriptor = createBasicImageDescriptor();

      assertDoesNotThrow(
          () -> new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor));
    }

    @Test
    void should_handle_maximum_integer_values_for_lengths() {
      var path = Path.of("huge.dcm");
      var positions = new long[] {0L, Integer.MAX_VALUE};
      var lengths = new int[] {Integer.MAX_VALUE, 1000};
      var descriptor = createBasicImageDescriptor();

      assertDoesNotThrow(
          () -> new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor));
    }

    @Test
    void should_handle_large_number_of_segments() {
      var segmentCount = 1000;
      var path = Path.of("many-segments.dcm");
      var positions = new long[segmentCount];
      var lengths = new int[segmentCount];

      IntStream.range(0, segmentCount)
          .forEach(
              i -> {
                positions[i] = i * 1024L;
                lengths[i] = 1024;
              });

      var descriptor = createBasicImageDescriptor();
      var stream = new ExtendSegmentedInputImageStream(path, positions, lengths, descriptor);

      assertAll(
          () -> assertEquals(segmentCount, stream.getSegmentCount()),
          () -> assertEquals(segmentCount * 1024L, stream.getTotalLength()));
    }

    @Test
    void should_handle_complex_path_structures() {
      var complexPath =
          Path.of(
              "very",
              "deep",
              "directory",
              "structure",
              "with",
              "spaces in name",
              "file name with spaces.dcm");
      var positions = new long[] {0L};
      var lengths = new int[] {1024};
      var descriptor = createBasicImageDescriptor();

      var stream = new ExtendSegmentedInputImageStream(complexPath, positions, lengths, descriptor);

      assertAll(
          () -> assertEquals(complexPath, stream.path()),
          () -> assertTrue(stream.toString().contains("file name with spaces.dcm")));
    }
  }

  // Test data factory methods

  private ExtendSegmentedInputImageStream createStreamWithMultipleSegments() {
    return new ExtendSegmentedInputImageStream(
        Path.of("multi", "segment.dcm"),
        new long[] {0L, 512L, 1024L, 2048L},
        new int[] {512, 512, 1024, 256},
        createBasicImageDescriptor());
  }

  private ImageDescriptor createBasicImageDescriptor() {
    var attributes = new Attributes();
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
    var attributes = new Attributes();
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
