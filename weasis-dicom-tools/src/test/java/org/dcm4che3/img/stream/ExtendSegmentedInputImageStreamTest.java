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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExtendSegmentedInputImageStreamTest {

  @Test
  @DisplayName("Verify ExtendSegmentedInputImageStream equals and hashCode")
  void checkEquals() {
    Attributes attributes = new Attributes();
    attributes.setInt(Tag.Rows, VR.US, 2);
    attributes.setInt(Tag.Columns, VR.US, 2);
    attributes.setInt(Tag.SamplesPerPixel, VR.US, 3);
    attributes.setInt(Tag.BitsAllocated, VR.US, 8);
    attributes.setInt(Tag.NumberOfFrames, VR.IS, 5);
    ImageDescriptor descriptor = new ImageDescriptor(attributes);
    ExtendSegmentedInputImageStream stream1 =
        new ExtendSegmentedInputImageStream(
            Path.of("/test/path"), new long[] {1, 2, 3}, new int[] {4, 5, 6}, descriptor);
    ExtendSegmentedInputImageStream stream2 =
        new ExtendSegmentedInputImageStream(
            Path.of("/test/path"), new long[] {1, 2, 3}, new int[] {4, 5, 6}, descriptor);

    assertTrue(stream1.toString().contains("/test/path"));
    assertEquals(stream1, stream1);
    assertNotEquals(stream1, null);
    assertNotEquals(stream1, new Object());
    assertEquals(stream1, stream2);
    assertEquals(stream1.hashCode(), stream2.hashCode());

    stream2 =
        new ExtendSegmentedInputImageStream(
            Path.of("/different/path"), new long[] {4, 5, 6}, new int[] {7, 8, 9}, descriptor);
    assertNotEquals(stream1, stream2);
    assertNotEquals(stream1.hashCode(), stream2.hashCode());
  }
}
