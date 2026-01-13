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

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.natives.NativeLibrary;

/**
 * Test class for {@link PixelDataUtils}.
 *
 * <p>This test class validates pixel data calculations, color space conversions, and bit depth
 * operations using real test data instead of mocks.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PixelDataUtilsTest {

  private static final double PRECISION = 0.001;

  @BeforeAll
  static void loadNativeLib() {
    NativeLibrary.loadLibraryFromLibraryName();
  }

  @Nested
  class Get_Min_Max_Tests {

    @ParameterizedTest
    @CsvSource(
        textBlock =
            """
                    1, false, 0.0, 1.0
                    8, false, 0.0, 255.0
                    16, false, 0.0, 65535.0
                    8, true, -128.0, 127.0
                    16, true, -32768.0, 32767.0
                    12, false, 0.0, 4095.0
                    12, true, -2048.0, 2047.0
                    """)
    void should_return_correct_ranges_for_valid_bit_depths_and_signedness(
        int bitsStored, boolean signed, double expectedMin, double expectedMax) {
      var result = PixelDataUtils.getMinMax(bitsStored, signed);

      assertAll(
          () ->
              assertEquals(
                  expectedMin,
                  result.first(),
                  PRECISION,
                  () ->
                      "Min value for %d-bit %s should be %.1f"
                          .formatted(bitsStored, signed ? "signed" : "unsigned", expectedMin)),
          () ->
              assertEquals(
                  expectedMax,
                  result.second(),
                  PRECISION,
                  () ->
                      "Max value for %d-bit %s should be %.1f"
                          .formatted(bitsStored, signed ? "signed" : "unsigned", expectedMax)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, 0, 17, 32, 100})
    void should_clamp_invalid_bit_depths_to_supported_range(int invalidBits) {
      var unsignedResult = PixelDataUtils.getMinMax(invalidBits, false);
      var signedResult = PixelDataUtils.getMinMax(invalidBits, true);

      assertAll(
          () -> assertNotNull(unsignedResult),
          () -> assertTrue(unsignedResult.first() >= 0, "Unsigned min should be >= 0"),
          () -> assertTrue(unsignedResult.second() > unsignedResult.first(), "Max should be > min"),
          () -> assertNotNull(signedResult),
          () -> assertTrue(signedResult.first() < 0, "Signed min should be < 0"),
          () -> assertTrue(signedResult.second() >= 0, "Signed max should be >= 0"));
    }

    @Test
    void should_return_correct_range_for_1_bit_signed() {
      var result = PixelDataUtils.getMinMax(1, true);

      assertAll(
          () -> assertNotNull(result),
          () -> assertEquals(-1.0, result.first(), PRECISION),
          () -> assertEquals(0.0, result.second(), PRECISION),
          () -> assertTrue(result.first() < result.second(), "Min should be less than max"));
    }

    @Test
    void should_clamp_large_bit_depth_to_maximum_supported() {
      var result = PixelDataUtils.getMinMax(20, false);

      assertAll(
          () -> assertEquals(0.0, result.first(), PRECISION),
          () -> assertEquals(65535.0, result.second(), PRECISION));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -10})
    void should_clamp_zero_and_negative_bit_depths_to_minimum(int bits) {
      var result = PixelDataUtils.getMinMax(bits, false);

      assertAll(
          () -> assertEquals(0.0, result.first(), PRECISION),
          () -> assertEquals(1.0, result.second(), PRECISION));
    }
  }

  @Nested
  class Color_Conversion_Tests {

    @Test
    void bgr2rgb_should_return_null_for_null_input() {
      assertNull(PixelDataUtils.bgr2rgb(null));
    }

    @Test
    void rgb2bgr_should_return_null_for_null_input() {
      assertNull(PixelDataUtils.rgb2bgr(null));
    }

    @ParameterizedTest
    @MethodSource("singleChannelImageProvider")
    void bgr2rgb_should_return_same_image_for_single_channel_input(
        PlanarImage grayscaleImage, String description) {
      var result = PixelDataUtils.bgr2rgb(grayscaleImage);

      assertAll(
          () ->
              assertSame(grayscaleImage, result, "Should return the same instance: " + description),
          () ->
              assertEquals(1, result.channels(), "Should maintain single channel: " + description));
    }

    @ParameterizedTest
    @MethodSource("singleChannelImageProvider")
    void rgb2bgr_should_return_same_image_for_single_channel_input(
        PlanarImage grayscaleImage, String description) {
      var result = PixelDataUtils.rgb2bgr(grayscaleImage);

      assertAll(
          () ->
              assertSame(grayscaleImage, result, "Should return the same instance: " + description),
          () ->
              assertEquals(1, result.channels(), "Should maintain single channel:" + description));
    }

    @Test
    void bgr2rgb_should_convert_3_channel_BGR_image_to_RGB() {
      // B=100, G=150, R=200
      var bgrImage = createTestImage(CvType.CV_8UC3, new Scalar(100, 150, 200), 10, 10);

      var result = PixelDataUtils.bgr2rgb(bgrImage);

      assertColorConversion(
          bgrImage,
          result,
          new double[] {200.0, 150.0, 100.0}, // Expected RGB values
          "BGR to RGB conversion");
    }

    @Test
    void rgb2bgr_should_convert_3_channel_RGB_image_to_BGR() {
      // R=200, G=150, B=100
      var rgbImage = createTestImage(CvType.CV_8UC3, new Scalar(200, 150, 100), 15, 15);

      var result = PixelDataUtils.rgb2bgr(rgbImage);

      assertColorConversion(
          rgbImage,
          result,
          new double[] {100.0, 150.0, 200.0}, // Expected BGR values
          "RGB to BGR conversion");
    }

    @Test
    void color_conversions_should_be_inverse_operations() {
      var originalImage = createVariedPixelImage();

      // Convert BGR -> RGB -> BGR
      var rgbImage = PixelDataUtils.bgr2rgb(originalImage);
      var backToBgrImage = PixelDataUtils.rgb2bgr(rgbImage);

      assertDimensionsPreserved(originalImage, backToBgrImage);
      assertPixelValuesPreserved(originalImage, backToBgrImage);
    }

    @Test
    void bgr2rgb_should_handle_4_channel_BGRA_images() {
      var bgraImage = createTestImage(CvType.CV_8UC4, new Scalar(50, 100, 150, 128), 8, 8);

      var result = PixelDataUtils.bgr2rgb(bgraImage);

      assertAll(
          () -> assertNotNull(result),
          () -> assertNotSame(bgraImage, result, "Should return a new image"));

      var resultPixel = result.toMat().get(0, 0);
      assertAll(
          () -> assertEquals(150.0, resultPixel[0], 0.1, "Red channel (was blue)"),
          () -> assertEquals(100.0, resultPixel[1], 0.1, "Green channel"),
          () -> assertEquals(50.0, resultPixel[2], 0.1, "Blue channel (was red)"));
    }

    private static Stream<Arguments> singleChannelImageProvider() {
      return Stream.of(
          Arguments.of(
              createTestImage(CvType.CV_8UC1, new Scalar(128), 100, 100), "8-bit grayscale"),
          Arguments.of(
              createTestImage(CvType.CV_16UC1, new Scalar(1000), 50, 50), "16-bit grayscale"),
          Arguments.of(
              createTestImage(CvType.CV_32FC1, new Scalar(0.5), 25, 25), "32-bit float grayscale"));
    }

    private void assertColorConversion(
        PlanarImage original,
        PlanarImage result,
        double[] expectedPixelValues,
        String conversionType) {
      assertAll(
          () -> assertNotNull(result, "Result should not be null"),
          () -> assertNotSame(original, result, "Should return a new image"),
          () -> assertEquals(3, result.channels(), "Result should have 3 channels"),
          () -> assertEquals(original.width(), result.width(), "Width should be preserved"),
          () -> assertEquals(original.height(), result.height(), "Height should be preserved"));

      var resultPixel = result.toMat().get(0, 0);
      for (int i = 0; i < expectedPixelValues.length; i++) {
        final int channel = i;
        assertEquals(
            expectedPixelValues[i],
            resultPixel[i],
            0.1,
            () ->
                "%s: Channel %d should be %.1f"
                    .formatted(conversionType, channel, expectedPixelValues[channel]));
      }
    }

    private void assertDimensionsPreserved(PlanarImage original, PlanarImage result) {
      assertAll(
          () -> assertEquals(original.width(), result.width()),
          () -> assertEquals(original.height(), result.height()),
          () -> assertEquals(original.channels(), result.channels()));
    }

    private void assertPixelValuesPreserved(PlanarImage original, PlanarImage result) {
      var originalMat = original.toMat();
      var resultMat = result.toMat();

      for (int i = 0; i < originalMat.rows(); i++) {
        for (int j = 0; j < originalMat.cols(); j++) {
          var originalPixel = originalMat.get(i, j);
          var resultPixel = resultMat.get(i, j);

          for (int c = 0; c < originalPixel.length; c++) {
            final int row = i, col = j, channel = c;
            assertEquals(
                originalPixel[c],
                resultPixel[c],
                0.1,
                () ->
                    "Pixel value at (%d,%d) channel %d should be preserved after round-trip conversion"
                        .formatted(row, col, channel));
          }
        }
      }
    }
  }

  // Helper methods

  private static PlanarImage createTestImage(int cvType, Scalar color, int width, int height) {
    var mat = new Mat(height, width, cvType, color);
    return ImageCV.fromMat(mat);
  }

  private static PlanarImage createVariedPixelImage() {
    var mat = new Mat(5, 5, CvType.CV_8UC3);
    for (int i = 0; i < 5; i++) {
      for (int j = 0; j < 5; j++) {
        mat.put(i, j, i * 10, j * 20, (i + j) * 15);
      }
    }
    return ImageCV.fromMat(mat);
  }
}
