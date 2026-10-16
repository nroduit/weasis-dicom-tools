/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.weasis.opencv.natives.NativeLibrary;
import org.weasis.opencv.op.ImageTransformer;

/**
 * The shutter keeps exactly the pixels it names: rows and columns from 1\1, edges included (PS3.3
 * C.7.6.11).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ShutterPixelTest {

  private static final int SIZE = 6;
  private static final int VISIBLE = 100;

  @BeforeAll
  static void loadNativeLibrary() {
    NativeLibrary.loadLibraryFromLibraryName();
  }

  /** Visible pixels as 1 and shuttered ones as 0, row by row. */
  private static int[][] visible(Attributes shutter) {
    Mat src = new Mat(SIZE, SIZE, CvType.CV_8U, new Scalar(VISIBLE));
    Mat dst =
        ImageTransformer.applyShutter(src, DicomObjectUtil.getShutterShape(shutter), Color.BLACK);
    int[][] mask = new int[SIZE][SIZE];
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        mask[y][x] = dst.get(y, x)[0] == VISIBLE ? 1 : 0;
      }
    }
    return mask;
  }

  private static Attributes rectangle(int left, int top, int right, int bottom) {
    Attributes a = new Attributes();
    a.setString(Tag.ShutterShape, VR.CS, "RECTANGULAR");
    a.setInt(Tag.ShutterLeftVerticalEdge, VR.IS, left);
    a.setInt(Tag.ShutterUpperHorizontalEdge, VR.IS, top);
    a.setInt(Tag.ShutterRightVerticalEdge, VR.IS, right);
    a.setInt(Tag.ShutterLowerHorizontalEdge, VR.IS, bottom);
    return a;
  }

  @Test
  void rectangle_over_the_whole_image_hides_nothing() {
    int[][] mask = visible(rectangle(1, 1, SIZE, SIZE));
    for (int[] row : mask) {
      assertArrayEquals(new int[] {1, 1, 1, 1, 1, 1}, row);
    }
  }

  @Test
  void rectangle_keeps_its_edge_pixels() {
    // Columns 2 to 4 and rows 3 to 5, from 1\1
    int[][] mask = visible(rectangle(2, 3, 4, 5));
    int[] hidden = {0, 0, 0, 0, 0, 0};
    int[] shown = {0, 1, 1, 1, 0, 0};
    assertArrayEquals(new int[][] {hidden, hidden, shown, shown, shown, hidden}, mask);
  }

  @Test
  void circle_is_centred_on_the_named_pixel() {
    Attributes a = new Attributes();
    a.setString(Tag.ShutterShape, VR.CS, "CIRCULAR");
    a.setInt(Tag.CenterOfCircularShutter, VR.IS, 3, 4); // row 3, column 4
    a.setInt(Tag.RadiusOfCircularShutter, VR.IS, 1);

    int[][] mask = visible(a);
    // Row 3, column 4 from 1\1 is mask[2][3]; the radius keeps its four neighbours
    int count = 0;
    for (int[] row : mask) {
      for (int v : row) {
        count += v;
      }
    }
    assertEquals(1, mask[2][3]);
    assertEquals(1, mask[1][3]);
    assertEquals(1, mask[3][3]);
    assertEquals(1, mask[2][2]);
    assertEquals(1, mask[2][4]);
    assertEquals(5, count);
  }

  @Test
  void polygon_vertices_are_pixels_from_one() {
    Attributes a = new Attributes();
    a.setString(Tag.ShutterShape, VR.CS, "POLYGONAL");
    // Square on rows 2 to 3 and columns 2 to 3, from 1\1, as row\column pairs
    a.setInt(Tag.VerticesOfThePolygonalShutter, VR.IS, 2, 2, 2, 3, 3, 3, 3, 2);

    int[][] mask = visible(a);
    int[] hidden = {0, 0, 0, 0, 0, 0};
    int[] shown = {0, 1, 1, 0, 0, 0};
    assertArrayEquals(new int[][] {hidden, shown, shown, hidden, hidden, hidden}, mask);
  }
}
