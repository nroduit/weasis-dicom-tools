/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.op;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Color;
import java.awt.Shape;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.osgi.OpenCVNativeLoader;
import org.weasis.opencv.data.ImageCV;

class MaskAreaTest {
  @BeforeAll
  public static void loadNativeLib() {
    // Load the native OpenCV library
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();
  }

  @Test
  @DisplayName("Check MaskArea constructor")
  void testConstructor() {
    int w = 16;
    int h = 16;
    List<Shape> shapeList = List.of(new java.awt.Rectangle(4, 0, 8, 8));
    Color color = new Color(192, 193, 194);
    MaskArea actualMaskArea = new MaskArea(shapeList, color);
    assertSame(color, actualMaskArea.getColor());
    assertSame(shapeList, actualMaskArea.getShapeList());

    try (ImageCV img = new ImageCV(new Size(w, h), CvType.CV_8UC3, new Scalar(1, 2, 3))) {
      try (ImageCV result = MaskArea.drawShape(img, actualMaskArea)) {
        assertNotNull(result);
        byte[] data = new byte[3 * 256];
        result.get(0, 0, data);
        assertEquals(1, data[0]);
        assertEquals(2, data[1]);
        assertEquals(3, data[2]);
        assertEquals(194, data[12] & 0xFF);
        assertEquals(193, data[13] & 0xFF);
        assertEquals(192, data[14] & 0xFF);
        assertEquals(194, data[420] & 0xFF);
        assertEquals(193, data[421] & 0xFF);
        assertEquals(192, data[422] & 0xFF);
        assertEquals(1, data[423]);
        assertEquals(2, data[424]);
        assertEquals(3, data[425]);
      }

      try (ImageCV result = MaskArea.drawShape(img, null)) {
        assertEquals(img, result);
      }
    }

    actualMaskArea = new MaskArea(shapeList, Color.BLACK);
    assertSame(Color.BLACK, actualMaskArea.getColor());
    assertSame(shapeList, actualMaskArea.getShapeList());
    try (ImageCV img = new ImageCV(new Size(w, h), CvType.CV_16UC1, new Scalar(1024))) {
      try (ImageCV result = MaskArea.drawShape(img, actualMaskArea)) {
        assertNotNull(result);
        short[] data = new short[256];
        result.get(0, 0, data);
        assertEquals(1024, data[0]);
        assertEquals(1024, data[3]);
        assertEquals(0, data[4] & 0xFFFF);
        assertEquals(0, data[5] & 0xFFFF);
        assertEquals(1024, data[255]);
      }
    }

    actualMaskArea = new MaskArea(shapeList);
    assertSame(null, actualMaskArea.getColor());
    assertSame(shapeList, actualMaskArea.getShapeList());
    try (ImageCV img = new ImageCV(new Size(w, h), CvType.CV_16UC1, new Scalar(1024))) {
      try (ImageCV result = MaskArea.drawShape(img, actualMaskArea)) {
        assertNotNull(result);
        short[] data = new short[256];
        result.get(0, 0, data);
        assertEquals(1024, data[0]);
        assertEquals(1024, data[3]);
        assertEquals(1024, data[4] & 0xFFFF); // Blur effect
        assertEquals(1024, data[5] & 0xFFFF); // Blur effect
        assertEquals(1024, data[255]);
      }
    }
  }
}
