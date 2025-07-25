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

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.osgi.OpenCVNativeLoader;
import org.weasis.opencv.data.ImageCV;

class MaskAreaTest {

  private static final int TEST_IMAGE_WIDTH = 16;
  private static final int TEST_IMAGE_HEIGHT = 16;
  private static final Color TEST_COLOR = new Color(192, 193, 194);
  private static final Scalar BACKGROUND_COLOR_8UC3 = new Scalar(1, 2, 3);
  private static final Scalar BACKGROUND_COLOR_16UC1 = new Scalar(1024);

  @BeforeAll
  static void loadNativeLib() {
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Should create MaskArea with shapes and color")
    void shouldCreateMaskAreaWithShapesAndColor() {
      List<Shape> shapes = createTestShapes();

      MaskArea maskArea = new MaskArea(shapes, TEST_COLOR);

      assertSame(TEST_COLOR, maskArea.getColor());
      assertSame(shapes, maskArea.getShapeList());
    }

    @Test
    @DisplayName("Should create MaskArea with shapes only (blur mode)")
    void shouldCreateMaskAreaWithShapesOnly() {
      List<Shape> shapes = createTestShapes();

      MaskArea maskArea = new MaskArea(shapes);

      assertNull(maskArea.getColor());
      assertSame(shapes, maskArea.getShapeList());
    }

    @Test
    @DisplayName("Should throw NullPointerException when shapes list is null")
    void shouldThrowExceptionWhenShapesIsNull() {
      assertThrows(NullPointerException.class, () -> new MaskArea(null));
      assertThrows(NullPointerException.class, () -> new MaskArea(null, TEST_COLOR));
    }

    @Test
    @DisplayName("Should accept empty shapes list")
    void shouldAcceptEmptyShapesList() {
      List<Shape> emptyShapes = Collections.emptyList();

      MaskArea maskArea = new MaskArea(emptyShapes, TEST_COLOR);

      assertSame(TEST_COLOR, maskArea.getColor());
      assertSame(emptyShapes, maskArea.getShapeList());
    }
  }

  @Nested
  @DisplayName("Draw Shape Tests")
  class DrawShapeTests {

    @Test
    @DisplayName("Should return copy of original image when maskArea is null")
    void shouldReturnOriginalImageWhenMaskAreaIsNull() {
      try (ImageCV sourceImage = createTestImage8UC3()) {
        try (ImageCV result = MaskArea.drawShape(sourceImage, null)) {
          assertNotNull(result);
          assertImageDataEquals(sourceImage, result, CvType.CV_8UC3);
        }
      }
    }

    @Test
    @DisplayName("Should return copy of original image when shapes list is empty")
    void shouldReturnOriginalImageWhenShapesListIsEmpty() {
      MaskArea emptyMaskArea = new MaskArea(Collections.emptyList(), TEST_COLOR);

      try (ImageCV sourceImage = createTestImage8UC3()) {
        try (ImageCV result = MaskArea.drawShape(sourceImage, emptyMaskArea)) {
          assertNotNull(result);
          assertImageDataEquals(sourceImage, result, CvType.CV_8UC3);
        }
      }
    }

    @Test
    @DisplayName("Should fill rectangle with specified color on 8UC3 image")
    void shouldFillRectangleWithColorOn8UC3Image() {
      Rectangle maskRect = new Rectangle(4, 4, 8, 8);
      MaskArea maskArea = new MaskArea(List.of(maskRect), TEST_COLOR);

      try (ImageCV sourceImage = createTestImage8UC3()) {
        try (ImageCV result = MaskArea.drawShape(sourceImage, maskArea)) {
          assertNotNull(result);

          // Verify original pixels outside mask area remain unchanged
          assertPixelEquals(result, 0, 0, new byte[] {1, 2, 3});
          assertPixelEquals(result, 3, 3, new byte[] {1, 2, 3});

          // Verify pixels inside mask area are filled with color (BGR format)
          assertPixelEquals(result, 4, 4, new byte[] {(byte) 194, (byte) 193, (byte) 192});
          assertPixelEquals(result, 8, 8, new byte[] {(byte) 194, (byte) 193, (byte) 192});

          // Verify pixels just outside mask area remain unchanged
          assertPixelEquals(result, 13, 13, new byte[] {1, 2, 3});
        }
      }
    }

    @Test
    @DisplayName("Should fill rectangle with black color on 16UC1 image")
    void shouldFillRectangleWithBlackColorOn16UC1Image() {
      Rectangle maskRect = new Rectangle(4, 4, 8, 8);
      MaskArea maskArea = new MaskArea(List.of(maskRect), Color.BLACK);

      try (ImageCV sourceImage = createTestImage16UC1()) {
        try (ImageCV result = MaskArea.drawShape(sourceImage, maskArea)) {
          assertNotNull(result);

          short[] originalPixel = {1024};
          short[] blackPixel = {0};

          // Verify original pixels outside mask remain unchanged
          assertPixelEquals16UC1(result, 0, 0, originalPixel);
          assertPixelEquals16UC1(result, 3, 3, originalPixel);

          // Verify pixels inside mask are filled with black
          assertPixelEquals16UC1(result, 4, 4, blackPixel);
          assertPixelEquals16UC1(result, 8, 8, blackPixel);

          // Verify pixels outside mask remain unchanged
          assertPixelEquals16UC1(result, 13, 13, originalPixel);
        }
      }
    }

    @Test
    @DisplayName("Should apply blur effect when no color is specified")
    void shouldApplyBlurEffectWhenNoColorSpecified() {
      Rectangle blurRect = new Rectangle(4, 4, 8, 8);
      MaskArea maskArea = new MaskArea(List.of(blurRect));

      try (ImageCV sourceImage = createTestImage16UC1()) {
        try (ImageCV result = MaskArea.drawShape(sourceImage, maskArea)) {
          assertNotNull(result);

          // Pixels outside blur area should remain unchanged
          short[] originalData = new short[1];
          result.get(0, 0, originalData);
          assertEquals(1024, originalData[0]);

          // Pixels inside blur area should still have values (blur effect, not masking)
          short[] blurredData = new short[1];
          result.get(6, 6, blurredData);
          assertTrue(blurredData[0] > 0, "Blurred pixel should have non-zero value");
        }
      }
    }

    @Test
    @DisplayName("Should handle multiple shapes with different types")
    void shouldHandleMultipleShapesWithDifferentTypes() {
      List<Shape> shapes =
          List.of(
              new Rectangle(2, 2, 4, 4), new Ellipse2D.Float(8, 8, 6, 6), createTrianglePolygon());
      MaskArea maskArea = new MaskArea(shapes, Color.RED);

      try (ImageCV sourceImage = createTestImage8UC3()) {
        try (ImageCV result = MaskArea.drawShape(sourceImage, maskArea)) {
          assertNotNull(result);

          // Verify that the image has been modified (contains red pixels)
          byte[] redPixelBGR = {0, 0, (byte) 255}; // Red in BGR format
          boolean hasRedPixel = hasPixelWithColor(result, redPixelBGR);
          assertTrue(hasRedPixel, "Image should contain red pixels from masking");
        }
      }
    }

    @Test
    @DisplayName("Should handle rectangle smaller than blur threshold")
    void shouldHandleSmallRectangleBelowBlurThreshold() {
      Rectangle tinyRect = new Rectangle(5, 5, 2, 2); // Smaller than MIN_BLUR_DIMENSION (3)
      MaskArea maskArea = new MaskArea(List.of(tinyRect));

      try (ImageCV sourceImage = createTestImage16UC1()) {
        try (ImageCV result = MaskArea.drawShape(sourceImage, maskArea)) {
          assertNotNull(result);

          // Image should remain unchanged since rectangle is too small for blur
          assertImageDataEquals(sourceImage, result, CvType.CV_16UC1);
        }
      }
    }

    @Test
    @DisplayName("Should handle rectangle that extends beyond image boundaries")
    void shouldHandleRectangleBeyondImageBoundaries() {
      Rectangle oversizedRect = new Rectangle(-2, -2, 25, 25); // Extends beyond 16x16 image
      MaskArea maskArea = new MaskArea(List.of(oversizedRect), Color.BLUE);

      try (ImageCV sourceImage = createTestImage8UC3()) {
        try (ImageCV result = MaskArea.drawShape(sourceImage, maskArea)) {
          assertNotNull(result);

          // Entire image should be filled with blue (clipped to image boundaries)
          byte[] bluePixelBGR = {(byte) 255, 0, 0}; // Blue in BGR format
          assertPixelEquals(result, 0, 0, bluePixelBGR);
          assertPixelEquals(result, 8, 8, bluePixelBGR);
          assertPixelEquals(result, 15, 15, bluePixelBGR);
        }
      }
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle zero-dimension rectangle")
    void shouldHandleZeroDimensionRectangle() {
      Rectangle zeroRect = new Rectangle(5, 5, 0, 0);
      MaskArea maskArea = new MaskArea(List.of(zeroRect), Color.GREEN);

      try (ImageCV sourceImage = createTestImage8UC3()) {
        try (ImageCV result = MaskArea.drawShape(sourceImage, maskArea)) {
          assertNotNull(result);
          // Image should remain mostly unchanged (zero-area rectangle)
          assertImageHasOriginalPixels(result);
        }
      }
    }

    @Test
    @DisplayName("Should handle complex polygon shape")
    void shouldHandleComplexPolygonShape() {
      Polygon complexPolygon = createComplexPolygon();
      MaskArea maskArea = new MaskArea(List.of(complexPolygon), Color.YELLOW);

      try (ImageCV sourceImage = createTestImage8UC3()) {
        try (ImageCV result = MaskArea.drawShape(sourceImage, maskArea)) {
          assertNotNull(result);

          // Verify that some pixels have been modified
          boolean hasYellowPixel =
              hasPixelWithColor(result, new byte[] {0, (byte) 255, (byte) 255});
          assertTrue(hasYellowPixel, "Image should contain yellow pixels from polygon masking");
        }
      }
    }
  }

  // Helper methods for creating test data

  private ImageCV createTestImage8UC3() {
    return new ImageCV(
        new Size(TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT), CvType.CV_8UC3, BACKGROUND_COLOR_8UC3);
  }

  private ImageCV createTestImage16UC1() {
    return new ImageCV(
        new Size(TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT), CvType.CV_16UC1, BACKGROUND_COLOR_16UC1);
  }

  private List<Shape> createTestShapes() {
    List<Shape> shapes = new ArrayList<>();
    shapes.add(new Rectangle(4, 4, 8, 8));
    shapes.add(new Ellipse2D.Float(2, 2, 4, 4));
    return shapes;
  }

  private Polygon createTrianglePolygon() {
    Polygon triangle = new Polygon();
    triangle.addPoint(12, 2);
    triangle.addPoint(14, 6);
    triangle.addPoint(10, 6);
    return triangle;
  }

  private Polygon createComplexPolygon() {
    Polygon polygon = new Polygon();
    polygon.addPoint(3, 3);
    polygon.addPoint(7, 2);
    polygon.addPoint(9, 5);
    polygon.addPoint(8, 9);
    polygon.addPoint(4, 8);
    polygon.addPoint(2, 6);
    return polygon;
  }

  // Helper methods for assertions

  private void assertPixelEquals(ImageCV image, int row, int col, byte[] expectedBGR) {
    byte[] actualPixel = new byte[3];
    image.get(row, col, actualPixel);
    assertArrayEquals(
        expectedBGR,
        actualPixel,
        String.format("Pixel at (%d,%d) should match expected BGR values", row, col));
  }

  private void assertPixelEquals16UC1(ImageCV image, int row, int col, short[] expected) {
    short[] actualPixel = new short[1];
    image.get(row, col, actualPixel);
    assertArrayEquals(
        expected,
        actualPixel,
        String.format("Pixel at (%d,%d) should match expected value", row, col));
  }

  private void assertImageDataEquals(ImageCV expected, ImageCV actual, int cvType) {
    assertEquals(expected.width(), actual.width(), "Image widths should match");
    assertEquals(expected.height(), actual.height(), "Image heights should match");
    assertEquals(expected.type(), actual.type(), "Image types should match");

    if (cvType == CvType.CV_8UC3) {
      byte[] expectedData = new byte[TEST_IMAGE_WIDTH * TEST_IMAGE_HEIGHT * 3];
      byte[] actualData = new byte[TEST_IMAGE_WIDTH * TEST_IMAGE_HEIGHT * 3];
      expected.get(0, 0, expectedData);
      actual.get(0, 0, actualData);
      assertArrayEquals(expectedData, actualData, "Image pixel data should match");
    } else if (cvType == CvType.CV_16UC1) {
      short[] expectedData = new short[TEST_IMAGE_WIDTH * TEST_IMAGE_HEIGHT];
      short[] actualData = new short[TEST_IMAGE_WIDTH * TEST_IMAGE_HEIGHT];
      expected.get(0, 0, expectedData);
      actual.get(0, 0, actualData);
      assertArrayEquals(expectedData, actualData, "Image pixel data should match");
    }
  }

  private boolean hasPixelWithColor(ImageCV image, byte[] targetBGR) {
    byte[] pixelData = new byte[TEST_IMAGE_WIDTH * TEST_IMAGE_HEIGHT * 3];
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
    byte[] originalBGR = {1, 2, 3};
    boolean hasOriginalPixels = hasPixelWithColor(image, originalBGR);
    assertTrue(hasOriginalPixels, "Image should still contain some original pixels");
  }
}
