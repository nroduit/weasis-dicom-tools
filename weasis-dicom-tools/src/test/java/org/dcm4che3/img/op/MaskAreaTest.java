/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.op;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.natives.NativeLibrary;

@DisplayNameGeneration(ReplaceUnderscores.class)
class MaskAreaTest {

  private static final int TEST_IMAGE_WIDTH = 16;
  private static final int TEST_IMAGE_HEIGHT = 16;
  private static final Color TEST_COLOR = new Color(192, 193, 194);
  private static final Scalar BACKGROUND_COLOR_8UC3 = new Scalar(1, 2, 3);
  private static final Scalar BACKGROUND_COLOR_16UC1 = new Scalar(1024);

  // Test data constants
  private static final byte[] ORIGINAL_BGR_PIXEL = {1, 2, 3};
  private static final byte[] TEST_COLOR_BGR = {(byte) 194, (byte) 193, (byte) 192};
  private static final short[] ORIGINAL_16UC1_PIXEL = {1024};
  private static final short[] BLACK_16UC1_PIXEL = {0};

  @BeforeAll
  static void load_native_lib() {
    NativeLibrary.loadLibraryFromLibraryName();
  }

  @Nested
  class Constructor_tests {

    @Test
    void create_mask_area_with_shapes_and_color() {
      var shapes = createTestShapes();

      var maskArea = new MaskArea(shapes, TEST_COLOR);

      assertSame(TEST_COLOR, maskArea.getColor());
      assertSame(shapes, maskArea.getShapeList());
    }

    @Test
    void create_mask_area_with_shapes_only_blur_mode() {
      var shapes = createTestShapes();

      var maskArea = new MaskArea(shapes);

      assertNull(maskArea.getColor());
      assertSame(shapes, maskArea.getShapeList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void throw_exception_when_shapes_is_null(boolean withColor) {
      if (withColor) {
        assertThrows(NullPointerException.class, () -> new MaskArea(null, TEST_COLOR));
      } else {
        assertThrows(NullPointerException.class, () -> new MaskArea(null));
      }
    }

    @Test
    void accept_empty_shapes_list() {
      var emptyShapes = Collections.<Shape>emptyList();

      var maskArea = new MaskArea(emptyShapes, TEST_COLOR);

      assertSame(TEST_COLOR, maskArea.getColor());
      assertSame(emptyShapes, maskArea.getShapeList());
    }
  }

  @Nested
  class Draw_shape_tests {

    @Test
    void return_copy_of_original_image_when_mask_area_is_null() {
      try (var sourceImage = createTestImage8UC3();
          var result = MaskArea.drawShape(sourceImage, null)) {
        assertNotNull(result);
        assertImageDataEquals(sourceImage, result, CvType.CV_8UC3);
      }
    }

    @Test
    void return_copy_of_original_image_when_shapes_list_is_empty() {
      var emptyMaskArea = new MaskArea(Collections.emptyList(), TEST_COLOR);

      try (var sourceImage = createTestImage8UC3();
          var result = MaskArea.drawShape(sourceImage, emptyMaskArea)) {
        assertNotNull(result);
        assertImageDataEquals(sourceImage, result, CvType.CV_8UC3);
      }
    }

    @Test
    void fill_rectangle_with_specified_color_on_8UC3_image() {
      var maskRect = new Rectangle(4, 4, 8, 8);
      var maskArea = new MaskArea(List.of(maskRect), TEST_COLOR);

      try (var sourceImage = createTestImage8UC3();
          var result = MaskArea.drawShape(sourceImage, maskArea)) {

        assertNotNull(result);

        // Verify original pixels outside mask area remain unchanged
        assertPixelEquals(result, 0, 0, ORIGINAL_BGR_PIXEL);
        assertPixelEquals(result, 3, 3, ORIGINAL_BGR_PIXEL);

        // Verify pixels inside mask area are filled with color (BGR format)
        assertPixelEquals(result, 4, 4, TEST_COLOR_BGR);
        assertPixelEquals(result, 8, 8, TEST_COLOR_BGR);

        // Verify pixels just outside mask area remain unchanged
        assertPixelEquals(result, 13, 13, ORIGINAL_BGR_PIXEL);
      }
    }

    @Test
    void fill_rectangle_with_black_color_on_16UC1_image() {
      var maskRect = new Rectangle(4, 4, 8, 8);
      var maskArea = new MaskArea(List.of(maskRect), Color.BLACK);

      try (var sourceImage = createTestImage16UC1();
          var result = MaskArea.drawShape(sourceImage, maskArea)) {

        assertNotNull(result);

        // Verify original pixels outside mask remain unchanged
        assertPixelEquals16UC1(result, 0, 0, ORIGINAL_16UC1_PIXEL);
        assertPixelEquals16UC1(result, 3, 3, ORIGINAL_16UC1_PIXEL);

        // Verify pixels inside mask are filled with black
        assertPixelEquals16UC1(result, 4, 4, BLACK_16UC1_PIXEL);
        assertPixelEquals16UC1(result, 8, 8, BLACK_16UC1_PIXEL);

        // Verify pixels outside mask remain unchanged
        assertPixelEquals16UC1(result, 13, 13, ORIGINAL_16UC1_PIXEL);
      }
    }

    @Test
    void apply_blur_effect_when_no_color_is_specified() {
      var blurRect = new Rectangle(4, 4, 8, 8);
      var maskArea = new MaskArea(List.of(blurRect));

      try (var sourceImage = createTestImage16UC1();
          var result = MaskArea.drawShape(sourceImage, maskArea)) {

        assertNotNull(result);

        // Pixels outside blur area should remain unchanged
        var originalData = new short[1];
        result.get(0, 0, originalData);
        assertEquals(1024, originalData[0]);

        // Pixels inside blur area should still have values (blur effect, not masking)
        var blurredData = new short[1];
        result.get(6, 6, blurredData);
        assertTrue(blurredData[0] > 0, "Blurred pixel should have non-zero value");
      }
    }

    @Test
    void handle_multiple_shapes_with_different_types() {
      List<Shape> shapes =
          List.of(
              new Rectangle(2, 2, 4, 4), new Ellipse2D.Float(8, 8, 6, 6), createTrianglePolygon());
      var maskArea = new MaskArea(shapes, Color.RED);

      try (var sourceImage = createTestImage8UC3();
          var result = MaskArea.drawShape(sourceImage, maskArea)) {

        assertNotNull(result);

        // Verify that the image has been modified (contains red pixels)
        var redPixelBGR = new byte[] {0, 0, (byte) 255}; // Red in BGR format
        assertTrue(
            hasPixelWithColor(result, redPixelBGR), "Image should contain red pixels from masking");
      }
    }

    @Test
    void handle_rectangle_smaller_than_blur_threshold() {
      var tinyRect = new Rectangle(5, 5, 2, 2); // Smaller than MIN_BLUR_DIMENSION (3)
      var maskArea = new MaskArea(List.of(tinyRect));

      try (var sourceImage = createTestImage16UC1();
          var result = MaskArea.drawShape(sourceImage, maskArea)) {

        assertNotNull(result);
        // Image should remain unchanged since rectangle is too small for blur
        assertImageDataEquals(sourceImage, result, CvType.CV_16UC1);
      }
    }

    @ParameterizedTest
    @MethodSource("provideBoundaryTestCases")
    void handle_rectangles_at_image_boundaries(Rectangle rect, Color color, String description) {
      var maskArea = new MaskArea(List.of(rect), color);

      try (var sourceImage = createTestImage8UC3();
          var result = MaskArea.drawShape(sourceImage, maskArea)) {

        assertNotNull(result, description);

        if (color == Color.BLUE) {
          // Entire image should be filled with blue (clipped to image boundaries)
          var bluePixelBGR = new byte[] {(byte) 255, 0, 0}; // Blue in BGR format
          assertPixelEquals(result, 0, 0, bluePixelBGR);
          assertPixelEquals(result, 8, 8, bluePixelBGR);
          assertPixelEquals(result, 15, 15, bluePixelBGR);
        }
      }
    }

    private static Stream<Arguments> provideBoundaryTestCases() {
      return Stream.of(
          Arguments.of(
              new Rectangle(-2, -2, 25, 25),
              Color.BLUE,
              "Rectangle extending beyond image boundaries"),
          Arguments.of(new Rectangle(5, 5, 0, 0), Color.GREEN, "Zero-dimension rectangle"),
          Arguments.of(
              new Rectangle(0, 0, TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT),
              Color.RED,
              "Rectangle covering entire image"));
    }
  }

  @Nested
  class Edge_cases {

    @Test
    void handle_zero_dimension_rectangle() {
      var zeroRect = new Rectangle(5, 5, 0, 0);
      var maskArea = new MaskArea(List.of(zeroRect), Color.GREEN);

      try (var sourceImage = createTestImage8UC3();
          var result = MaskArea.drawShape(sourceImage, maskArea)) {

        assertNotNull(result);
        // Image should remain mostly unchanged (zero-area rectangle)
        assertImageHasOriginalPixels(result);
      }
    }

    @Test
    void handle_complex_polygon_shape() {
      var complexPolygon = createComplexPolygon();
      var maskArea = new MaskArea(List.of(complexPolygon), Color.YELLOW);

      try (var sourceImage = createTestImage8UC3();
          var result = MaskArea.drawShape(sourceImage, maskArea)) {

        assertNotNull(result);

        // Verify that some pixels have been modified
        var yellowPixelBGR = new byte[] {0, (byte) 255, (byte) 255};
        assertTrue(
            hasPixelWithColor(result, yellowPixelBGR),
            "Image should contain yellow pixels from polygon masking");
      }
    }
  }

  // Test data creation methods
  private ImageCV createTestImage8UC3() {
    return new ImageCV(
        new Size(TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT), CvType.CV_8UC3, BACKGROUND_COLOR_8UC3);
  }

  private ImageCV createTestImage16UC1() {
    return new ImageCV(
        new Size(TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT), CvType.CV_16UC1, BACKGROUND_COLOR_16UC1);
  }

  private List<Shape> createTestShapes() {
    return List.of(new Rectangle(4, 4, 8, 8), new Ellipse2D.Float(2, 2, 4, 4));
  }

  private Polygon createTrianglePolygon() {
    var triangle = new Polygon();
    triangle.addPoint(12, 2);
    triangle.addPoint(14, 6);
    triangle.addPoint(10, 6);
    return triangle;
  }

  private Polygon createComplexPolygon() {
    var polygon = new Polygon();
    int[] xPoints = {3, 7, 9, 8, 4, 2};
    int[] yPoints = {3, 2, 5, 9, 8, 6};
    for (int i = 0; i < xPoints.length; i++) {
      polygon.addPoint(xPoints[i], yPoints[i]);
    }
    return polygon;
  }

  // Assertion helper methods
  private void assertPixelEquals(ImageCV image, int row, int col, byte[] expectedBGR) {
    var actualPixel = new byte[3];
    image.get(row, col, actualPixel);
    assertArrayEquals(
        expectedBGR,
        actualPixel,
        () -> "Pixel at (%d,%d) should match expected BGR values".formatted(row, col));
  }

  private void assertPixelEquals16UC1(ImageCV image, int row, int col, short[] expected) {
    var actualPixel = new short[1];
    image.get(row, col, actualPixel);
    assertArrayEquals(
        expected,
        actualPixel,
        () -> "Pixel at (%d,%d) should match expected value".formatted(row, col));
  }

  private void assertImageDataEquals(ImageCV expected, ImageCV actual, int cvType) {
    assertAll(
        "Image comparison",
        () -> assertEquals(expected.width(), actual.width(), "Image widths should match"),
        () -> assertEquals(expected.height(), actual.height(), "Image heights should match"),
        () -> assertEquals(expected.type(), actual.type(), "Image types should match"));

    var dataSize = TEST_IMAGE_WIDTH * TEST_IMAGE_HEIGHT;
    if (cvType == CvType.CV_8UC3) {
      var expectedData = new byte[dataSize * 3];
      var actualData = new byte[dataSize * 3];
      expected.get(0, 0, expectedData);
      actual.get(0, 0, actualData);
      assertArrayEquals(expectedData, actualData, "Image pixel data should match");
    } else if (cvType == CvType.CV_16UC1) {
      var expectedData = new short[dataSize];
      var actualData = new short[dataSize];
      expected.get(0, 0, expectedData);
      actual.get(0, 0, actualData);
      assertArrayEquals(expectedData, actualData, "Image pixel data should match");
    }
  }

  private boolean hasPixelWithColor(ImageCV image, byte[] targetBGR) {
    var pixelData = new byte[TEST_IMAGE_WIDTH * TEST_IMAGE_HEIGHT * 3];
    image.get(0, 0, pixelData);

    for (int i = 0; i < pixelData.length; i += 3) {
      if (pixelData[i] == targetBGR[0]
          && pixelData[i + 1] == targetBGR[1]
          && pixelData[i + 2] == targetBGR[2]) {
        return true;
      }
    }
    return false;
  }

  private void assertImageHasOriginalPixels(ImageCV image) {
    assertTrue(
        hasPixelWithColor(image, ORIGINAL_BGR_PIXEL),
        "Image should still contain some original pixels");
  }
}
