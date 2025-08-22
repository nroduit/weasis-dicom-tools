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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Represents a segmented input stream for DICOM image data with multiple segments.
 *
 * <p>This record encapsulates the file path and segmentation information needed to read image data
 * that is split across multiple segments within a file. Each segment is defined by its position
 * offset and length within the file.
 *
 * <p>The segments are processed sequentially to reconstruct the complete image data, making this
 * particularly useful for handling fragmented or multi-frame DICOM images.
 *
 * @param path the file path containing the segmented image data
 * @param segmentPositions array of byte offsets for each segment start position
 * @param segmentLengths array of byte lengths for each corresponding segment
 * @param imageDescriptor metadata describing the image characteristics and properties
 * @author Nicolas Roduit
 * @since Mar 2018
 */
public record ExtendSegmentedInputImageStream(
    Path path, long[] segmentPositions, int[] segmentLengths, ImageDescriptor imageDescriptor) {

  /**
   * Compact constructor with validation.
   *
   * @throws IllegalArgumentException if segment arrays have different lengths or are empty
   * @throws NullPointerException if any required parameter is null
   */
  public ExtendSegmentedInputImageStream {
    Objects.requireNonNull(path, "Path cannot be null");
    Objects.requireNonNull(segmentPositions, "Segment positions cannot be null");
    Objects.requireNonNull(segmentLengths, "Segment lengths cannot be null");
    Objects.requireNonNull(imageDescriptor, "Image descriptor cannot be null");

    validateArraysConsistency(segmentPositions, segmentLengths);
    validateSegmentValues(segmentPositions, segmentLengths);
  }

  public int getSegmentCount() {
    return segmentPositions.length;
  }

  public long getTotalLength() {
    return Arrays.stream(segmentLengths).asLongStream().sum();
  }

  public long[] segmentPositions() {
    return segmentPositions.clone();
  }

  public int[] segmentLengths() {
    return segmentLengths.clone();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ExtendSegmentedInputImageStream other
        && Objects.equals(path, other.path)
        && Arrays.equals(segmentPositions, other.segmentPositions)
        && Arrays.equals(segmentLengths, other.segmentLengths)
        && Objects.equals(imageDescriptor, other.imageDescriptor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        path, imageDescriptor, Arrays.hashCode(segmentPositions), Arrays.hashCode(segmentLengths));
  }

  @Override
  public String toString() {
    return "ExtendSegmentedInputImageStream{path=%s, segments=%d, totalLength=%d}"
        .formatted(path, getSegmentCount(), getTotalLength());
  }

  private static void validateArraysConsistency(long[] segmentPositions, int[] segmentLengths) {
    if (segmentPositions.length != segmentLengths.length) {
      throw new IllegalArgumentException(
          "Segment positions and lengths arrays must have the same length");
    }

    if (segmentPositions.length == 0) {
      throw new IllegalArgumentException("At least one segment must be defined");
    }
  }

  private static void validateSegmentValues(long[] segmentPositions, int[] segmentLengths) {
    IntStream.range(0, segmentPositions.length)
        .forEach(
            i -> {
              if (segmentPositions[i] < 0) {
                throw new IllegalArgumentException(
                    "Segment position cannot be negative: " + segmentPositions[i]);
              }
              if (segmentLengths[i] <= 0) {
                throw new IllegalArgumentException(
                    "Segment length must be positive: " + segmentLengths[i]);
              }
            });
  }
}
