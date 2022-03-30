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

/**
 * @author Nicolas Roduit
 * @since Mar 2018
 */
public class ExtendSegmentedInputImageStream {
  private final Path path;
  private final long[] segmentPositions;
  private final int[] segmentLengths;
  private final ImageDescriptor imageDescriptor;

  public ExtendSegmentedInputImageStream(
      Path path, long[] segmentPositions, int[] segmentLengths, ImageDescriptor imageDescriptor) {
    this.path = path;
    this.segmentPositions = segmentPositions;
    this.segmentLengths = segmentLengths;
    this.imageDescriptor = imageDescriptor;
  }

  public long[] getSegmentPositions() {
    return segmentPositions;
  }

  public int[] getSegmentLengths() {
    return segmentLengths;
  }

  public Path getPath() {
    return path;
  }

  public ImageDescriptor getImageDescriptor() {
    return imageDescriptor;
  }
}
