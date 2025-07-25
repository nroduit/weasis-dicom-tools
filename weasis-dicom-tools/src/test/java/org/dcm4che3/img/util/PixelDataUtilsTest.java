/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.osgi.OpenCVNativeLoader;
import org.weasis.core.util.Pair;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Test class for {@link PixelDataUtils}.
 *
 * <p>This test class validates pixel data calculations, color space conversions, and bit depth
 * operations using real test data instead of mocks.
 */
class PixelDataUtilsTest {
  @BeforeAll
  static void loadNativeLib() {
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();
  }

  @ParameterizedTest
  @CsvSource({
    "1, false, 0.0, 1.0", // 1-bit unsigned: [0, 1]
    "8, false, 0.0, 255.0", // 8-bit unsigned: [0, 255]
    "16, false, 0.0, 65535.0", // 16-bit unsigned: [0, 65535]
    "8, true, -128.0, 127.0", // 8-bit signed: [-128, 127]
    "16, true, -32768.0, 32767.0", // 16-bit signed: [-32768, 32767]
    "12, false, 0.0, 4095.0", // 12-bit unsigned: [0, 4095]
    "12, true, -2048.0, 2047.0" // 12-bit signed: [-2048, 2047]
  })
  @DisplayName("getMinMax should return correct ranges for various bit depths and signedness")
  void testGetMinMaxValidInputs(
      int bitsStored, boolean signed, double expectedMin, double expectedMax) {
    Pair<Double, Double> result = PixelDataUtils.getMinMax(bitsStored, signed);

    assertEquals(
        expectedMin,
        result.first(),
        0.001,
        String.format(
            "Min value for %d-bit %s should be %f",
            bitsStored, signed ? "signed" : "unsigned", expectedMin));
    assertEquals(
        expectedMax,
        result.second(),
        0.001,
        String.format(
            "Max value for %d-bit %s should be %f",
            bitsStored, signed ? "signed" : "unsigned", expectedMax));
  }

  @ParameterizedTest
  @ValueSource(ints = {-5, 0, 17, 32, 100})
  @DisplayName("getMinMax should clamp invalid bit depths to supported range")
  void testGetMinMaxWithInvalidBitDepths(int invalidBits) {
    // Test with unsigned - should clamp to 1-bit for values < 1, and 16-bit for values > 16
    Pair<Double, Double> unsignedResult = PixelDataUtils.getMinMax(invalidBits, false);
    assertNotNull(unsignedResult);
    assertTrue(unsignedResult.first() >= 0, "Unsigned min should be >= 0");
    assertTrue(unsignedResult.second() > unsignedResult.first(), "Max should be > min");

    // Test with signed
    Pair<Double, Double> signedResult = PixelDataUtils.getMinMax(invalidBits, true);
    assertNotNull(signedResult);
    assertTrue(signedResult.first() < 0, "Signed min should be < 0");
    assertTrue(signedResult.second() >= 0, "Signed max should be > 0");
  }

  @Test
  @DisplayName("bgr2rgb should return null for null input")
  void testBgr2rgbWithNullInput() {
    PlanarImage result = PixelDataUtils.bgr2rgb(null);
    assertNull(result);
  }

  @Test
  @DisplayName("rgb2bgr should return null for null input")
  void testRgb2bgrWithNullInput() {
    PlanarImage result = PixelDataUtils.rgb2bgr(null);
    assertNull(result);
  }

  @Test
  @DisplayName("bgr2rgb should return same image for single-channel (grayscale) input")
  void testBgr2rgbWithSingleChannel() {
    // Create a single-channel 8-bit grayscale image (100x100)
    Mat grayscaleMat = new Mat(100, 100, CvType.CV_8UC1, new Scalar(128));
    PlanarImage grayscaleImage = ImageCV.fromMat(grayscaleMat);

    PlanarImage result = PixelDataUtils.bgr2rgb(grayscaleImage);

    assertSame(grayscaleImage, result, "Single-channel image should be returned unchanged");
    assertEquals(1, result.channels(), "Result should still have 1 channel");
  }

  @Test
  @DisplayName("rgb2bgr should return same image for single-channel (grayscale) input")
  void testRgb2bgrWithSingleChannel() {
    // Create a single-channel 16-bit grayscale image (50x50)
    Mat grayscaleMat = new Mat(50, 50, CvType.CV_16UC1, new Scalar(1000));
    PlanarImage grayscaleImage = ImageCV.fromMat(grayscaleMat);

    PlanarImage result = PixelDataUtils.rgb2bgr(grayscaleImage);

    assertSame(grayscaleImage, result, "Single-channel image should be returned unchanged");
    assertEquals(1, result.channels(), "Result should still have 1 channel");
  }

  @Test
  @DisplayName("bgr2rgb should convert 3-channel BGR image to RGB")
  void testBgr2rgbWithThreeChannels() {
    // Create a 3-channel BGR image with distinct channel values
    // B=100, G=150, R=200
    Mat bgrMat = new Mat(10, 10, CvType.CV_8UC3, new Scalar(100, 150, 200));
    PlanarImage bgrImage = ImageCV.fromMat(bgrMat);

    PlanarImage result = PixelDataUtils.bgr2rgb(bgrImage);

    assertNotNull(result);
    assertNotSame(bgrImage, result, "Should return a new image");
    assertEquals(3, result.channels(), "Result should have 3 channels");
    assertEquals(bgrImage.width(), result.width(), "Width should be preserved");
    assertEquals(bgrImage.height(), result.height(), "Height should be preserved");

    // Verify the conversion happened by checking a pixel value
    // Original BGR (100, 150, 200) should become RGB (200, 150, 100)
    double[] rgbPixel = result.toMat().get(0, 0);
    assertEquals(200.0, rgbPixel[0], 0.1, "Red channel should be 200");
    assertEquals(150.0, rgbPixel[1], 0.1, "Green channel should be 150");
    assertEquals(100.0, rgbPixel[2], 0.1, "Blue channel should be 100");
  }

  @Test
  @DisplayName("rgb2bgr should convert 3-channel RGB image to BGR")
  void testRgb2bgrWithThreeChannels() {
    // Create a 3-channel RGB image with distinct channel values
    // R=200, G=150, B=100
    Mat rgbMat = new Mat(15, 15, CvType.CV_8UC3, new Scalar(200, 150, 100));
    PlanarImage rgbImage = ImageCV.fromMat(rgbMat);

    PlanarImage result = PixelDataUtils.rgb2bgr(rgbImage);

    assertNotNull(result);
    assertNotSame(rgbImage, result, "Should return a new image");
    assertEquals(3, result.channels(), "Result should have 3 channels");
    assertEquals(rgbImage.width(), result.width(), "Width should be preserved");
    assertEquals(rgbImage.height(), result.height(), "Height should be preserved");

    // Verify the conversion happened by checking a pixel value
    // Original RGB (200, 150, 100) should become BGR (100, 150, 200)
    double[] bgrPixel = result.toMat().get(0, 0);
    assertEquals(100.0, bgrPixel[0], 0.1, "Blue channel should be 100");
    assertEquals(150.0, bgrPixel[1], 0.1, "Green channel should be 150");
    assertEquals(200.0, bgrPixel[2], 0.1, "Red channel should be 200");
  }

  @Test
  @DisplayName("bgr2rgb and rgb2bgr should be inverse operations")
  void testColorConversionsAreInverse() {
    // Create a test image with varied pixel values
    Mat originalMat = new Mat(5, 5, CvType.CV_8UC3);
    // Fill with different values per pixel to test conversion accuracy
    for (int i = 0; i < 5; i++) {
      for (int j = 0; j < 5; j++) {
        originalMat.put(i, j, new double[] {i * 10, j * 20, (i + j) * 15});
      }
    }
    PlanarImage originalImage = ImageCV.fromMat(originalMat);

    // Convert BGR -> RGB -> BGR
    PlanarImage rgbImage = PixelDataUtils.bgr2rgb(originalImage);
    PlanarImage backToBgrImage = PixelDataUtils.rgb2bgr(rgbImage);

    // Verify dimensions are preserved
    assertEquals(originalImage.width(), backToBgrImage.width());
    assertEquals(originalImage.height(), backToBgrImage.height());
    assertEquals(originalImage.channels(), backToBgrImage.channels());

    // Verify pixel values are preserved (within floating point precision)
    for (int i = 0; i < 5; i++) {
      for (int j = 0; j < 5; j++) {
        double[] originalPixel = originalImage.toMat().get(i, j);
        double[] resultPixel = backToBgrImage.toMat().get(i, j);

        for (int c = 0; c < 3; c++) {
          assertEquals(
              originalPixel[c],
              resultPixel[c],
              0.1,
              String.format(
                  "Pixel value at (%d,%d) channel %d should be preserved after round-trip conversion",
                  i, j, c));
        }
      }
    }
  }

  @Test
  @DisplayName("bgr2rgb should handle 4-channel BGRA images")
  void testBgr2rgbWithFourChannels() {
    // Create a 4-channel BGRA image
    Mat bgraMat = new Mat(8, 8, CvType.CV_8UC4, new Scalar(50, 100, 150, 128));
    PlanarImage bgraImage = ImageCV.fromMat(bgraMat);

    PlanarImage result = PixelDataUtils.bgr2rgb(bgraImage);

    assertNotNull(result);
    assertNotSame(bgraImage, result, "Should return a new image");

    // Check that color conversion occurred. Assuming the last channel (alpha) is ignored in
    // conversion.
    double[] resultPixel = result.toMat().get(0, 0);
    assertEquals(150.0, resultPixel[0], 0.1, "Red channel (was blue)");
    assertEquals(100.0, resultPixel[1], 0.1, "Green channel");
    assertEquals(50.0, resultPixel[2], 0.1, "Blue channel (was red)");
  }

  @Test
  @DisplayName("Edge case: 1-bit signed should return valid range")
  void testOnebitSigned() {
    Pair<Double, Double> result = PixelDataUtils.getMinMax(1, true);

    // 1-bit signed should have range [-1, 0] mathematically, but implementation might differ
    assertNotNull(result);
    assertTrue(result.first() < result.second(), "Min should be less than max");
    assertEquals(-1.0, result.first(), 0.001, "1-bit signed min");
    assertEquals(0.0, result.second(), 0.001, "1-bit signed max");
  }

  @Test
  @DisplayName("Large bit depth should be clamped to maximum supported")
  void testLargeBitDepth() {
    Pair<Double, Double> result = PixelDataUtils.getMinMax(20, false);

    // Should be clamped to 16-bit unsigned
    assertEquals(0.0, result.first(), 0.001);
    assertEquals(65535.0, result.second(), 0.001);
  }

  @Test
  @DisplayName("Zero and negative bit depths should be clamped to minimum")
  void testZeroAndNegativeBitDepths() {
    Pair<Double, Double> resultZero = PixelDataUtils.getMinMax(0, false);
    Pair<Double, Double> resultNegative = PixelDataUtils.getMinMax(-10, false);

    // Both should be clamped to 1-bit unsigned
    assertEquals(0.0, resultZero.first(), 0.001);
    assertEquals(1.0, resultZero.second(), 0.001);

    assertEquals(0.0, resultNegative.first(), 0.001);
    assertEquals(1.0, resultNegative.second(), 0.001);
  }
}
