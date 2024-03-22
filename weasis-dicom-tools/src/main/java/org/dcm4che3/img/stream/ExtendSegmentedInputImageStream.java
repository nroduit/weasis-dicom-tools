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

/**
 * @author Nicolas Roduit
 * @since Mar 2018
 */
public record ExtendSegmentedInputImageStream(
    Path path, long[] segmentPositions, int[] segmentLengths, ImageDescriptor imageDescriptor) {

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExtendSegmentedInputImageStream that = (ExtendSegmentedInputImageStream) o;
    return Objects.equals(path, that.path)
        && Arrays.equals(segmentPositions, that.segmentPositions)
        && Arrays.equals(segmentLengths, that.segmentLengths)
        && Objects.equals(imageDescriptor, that.imageDescriptor);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(path, imageDescriptor);
    result = 31 * result + Arrays.hashCode(segmentPositions);
    result = 31 * result + Arrays.hashCode(segmentLengths);
    return result;
  }

  @Override
  public String toString() {
    return "ExtendSegmentedInputImageStream{" + "path=" + path + '}';
  }
}
