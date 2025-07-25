/*
 * Copyright (c) 2009-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CIE L*a*b* Color Space Conversion Tests")
class CIELabTest {

  // Test data representing various color scenarios
  private static final TestColorData BRIGHT_YELLOW =
      new TestColorData("Bright Yellow", new int[] {255, 200, 0}, new int[] {54534, 34371, 54623});

  private static final TestColorData VERY_DARK =
      new TestColorData("Very Dark", new int[] {1, 7, 9}, new int[] {1054, 32564, 32559});

  private static final TestColorData PURE_WHITE =
      new TestColorData("Pure White", new int[] {255, 255, 255}, new int[] {65534, 32899, 32892});

  private static final TestColorData PURE_BLACK =
      new TestColorData("Pure Black", new int[] {0, 0, 0}, new int[] {0, 32896, 32896});

  private static final TestColorData PURE_RED =
      new TestColorData("Pure Red", new int[] {255, 0, 0}, new int[] {34885, 53486, 50171});

  private static final TestColorData PURE_GREEN =
      new TestColorData("Pure Green", new int[] {0, 255, 0}, new int[] {57498, 10747, 54273});

  private static final TestColorData PURE_BLUE =
      new TestColorData("Pure Blue", new int[] {0, 0, 255}, new int[] {21170, 53249, 5174});

  private static final TestColorData MID_GRAY =
      new TestColorData("Mid Gray", new int[] {128, 128, 128}, new int[] {35116, 32898, 32893});

  @Nested
  @DisplayName("DICOM L*a*b* to RGB Conversion Tests")
  class DicomLabToRgbTests {

    @ParameterizedTest(name = "Should convert {0} correctly")
    @MethodSource("org.dcm4che3.img.data.CIELabTest#provideValidColorData")
    @DisplayName("Valid color conversions")
    void shouldConvertValidColorsCorrectly(TestColorData colorData) {
      int[] actualRgb = CIELab.dicomLab2rgb(colorData.dicomLab);
      assertArrayEquals(
          colorData.rgb,
          actualRgb,
          String.format(
              "Failed to convert %s from DICOM Lab %s to RGB %s",
              colorData.name,
              java.util.Arrays.toString(colorData.dicomLab),
              java.util.Arrays.toString(colorData.rgb)));
    }

    @Test
    @DisplayName("Should return empty array for null input")
    void shouldReturnEmptyArrayForNullInput() {
      int[] result = CIELab.dicomLab2rgb(null);
      assertArrayEquals(new int[0], result);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4, 5})
    @DisplayName("Should return empty array for invalid array lengths")
    void shouldReturnEmptyArrayForInvalidLength(int length) {
      int[] invalidInput = new int[length];
      int[] result = CIELab.dicomLab2rgb(invalidInput);
      assertArrayEquals(new int[0], result);
    }

    @Test
    @DisplayName("Should handle extreme DICOM Lab values")
    void shouldHandleExtremeValues() {
      // Test with maximum possible DICOM Lab values
      int[] maxValues = {65535, 65535, 65535};
      int[] result = CIELab.dicomLab2rgb(maxValues);
      assertEquals(3, result.length, "Should return 3 RGB components");

      // All RGB values should be in valid range [0, 255]
      for (int i = 0; i < result.length; i++) {
        assertTrue(
            result[i] >= 0 && result[i] <= 255,
            String.format("RGB component %d (%d) should be in range [0, 255]", i, result[i]));
      }
    }

    @Test
    @DisplayName("Should handle minimum DICOM Lab values")
    void shouldHandleMinimumValues() {
      int[] minValues = {0, 0, 0};
      int[] result = CIELab.dicomLab2rgb(minValues);
      assertEquals(3, result.length, "Should return 3 RGB components");

      for (int i = 0; i < result.length; i++) {
        assertTrue(
            result[i] >= 0 && result[i] <= 255,
            String.format("RGB component %d (%d) should be in range [0, 255]", i, result[i]));
      }
    }
  }

  @Nested
  @DisplayName("RGB to DICOM L*a*b* Conversion Tests")
  class RgbToDicomLabTests {

    @ParameterizedTest(name = "Should convert {0} correctly using int values")
    @MethodSource("org.dcm4che3.img.data.CIELabTest#provideValidColorData")
    @DisplayName("Valid color conversions from RGB int values")
    void shouldConvertValidColorsFromInts(TestColorData colorData) {
      int[] actualLab = CIELab.rgbToDicomLab(colorData.rgb[0], colorData.rgb[1], colorData.rgb[2]);
      assertArrayEquals(
          colorData.dicomLab,
          actualLab,
          String.format(
              "Failed to convert %s from RGB %s to DICOM Lab %s",
              colorData.name,
              java.util.Arrays.toString(colorData.rgb),
              java.util.Arrays.toString(colorData.dicomLab)));
    }

    @ParameterizedTest(name = "Should convert {0} correctly using Color object")
    @MethodSource("org.dcm4che3.img.data.CIELabTest#provideValidColorData")
    @DisplayName("Valid color conversions from Color objects")
    void shouldConvertValidColorsFromColorObject(TestColorData colorData) {
      Color color = new Color(colorData.rgb[0], colorData.rgb[1], colorData.rgb[2]);
      int[] actualLab = CIELab.rgbToDicomLab(color);
      assertArrayEquals(
          colorData.dicomLab,
          actualLab,
          String.format(
              "Failed to convert %s from Color to DICOM Lab %s",
              colorData.name, java.util.Arrays.toString(colorData.dicomLab)));
    }

    @Test
    @DisplayName("Should throw exception for null Color")
    void shouldThrowExceptionForNullColor() {
      assertThrows(
          NullPointerException.class,
          () -> CIELab.rgbToDicomLab(null),
          "Should throw NullPointerException for null Color");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 256, 300, -50})
    @DisplayName("Should handle out-of-range RGB values gracefully")
    void shouldHandleOutOfRangeRgbValues(int invalidValue) {
      // The method should handle out-of-range values without throwing exceptions
      // The behavior might clamp or wrap values - we just ensure no exceptions
      int[] result = CIELab.rgbToDicomLab(invalidValue, 128, 128);
      assertEquals(3, result.length, "Should return 3 Lab components");
    }
  }

  @Nested
  @DisplayName("Round-trip Conversion Tests")
  class RoundTripTests {

    @ParameterizedTest(name = "Should maintain accuracy in round-trip conversion for {0}")
    @MethodSource("org.dcm4che3.img.data.CIELabTest#provideValidColorData")
    @DisplayName("Round-trip conversion accuracy")
    void shouldMaintainAccuracyInRoundTripConversion(TestColorData colorData) {
      // RGB -> DICOM Lab -> RGB
      int[] labFromRgb = CIELab.rgbToDicomLab(colorData.rgb[0], colorData.rgb[1], colorData.rgb[2]);
      int[] rgbFromLab = CIELab.dicomLab2rgb(labFromRgb);

      assertArrayEquals(
          colorData.rgb,
          rgbFromLab,
          () ->
              String.format(
                  "Round-trip conversion failed for %s: RGB %s -> Lab %s -> RGB %s",
                  colorData.name,
                  java.util.Arrays.toString(colorData.rgb),
                  java.util.Arrays.toString(labFromRgb),
                  java.util.Arrays.toString(rgbFromLab)));
    }

    @ParameterizedTest(name = "Should maintain accuracy in reverse round-trip conversion for {0}")
    @MethodSource("org.dcm4che3.img.data.CIELabTest#provideValidColorData")
    @DisplayName("Reverse round-trip conversion accuracy")
    void shouldMaintainAccuracyInReverseRoundTripConversion(TestColorData colorData) {
      // DICOM Lab -> RGB -> DICOM Lab
      int[] rgbFromLab = CIELab.dicomLab2rgb(colorData.dicomLab);
      int[] labFromRgb = CIELab.rgbToDicomLab(rgbFromLab[0], rgbFromLab[1], rgbFromLab[2]);

      assertArrayEquals(
          colorData.dicomLab,
          labFromRgb,
          String.format(
              "Reverse round-trip conversion failed for %s: Lab %s -> RGB %s -> Lab %s",
              colorData.name,
              java.util.Arrays.toString(colorData.dicomLab),
              java.util.Arrays.toString(rgbFromLab),
              java.util.Arrays.toString(labFromRgb)));
    }
  }

  @Nested
  @DisplayName("Color Space Properties Tests")
  class ColorSpacePropertiesTests {

    @Test
    @DisplayName("Should handle grayscale colors correctly")
    void shouldHandleGrayscaleColors() {
      // For grayscale colors, a* and b* should be approximately neutral (around 32768 in DICOM
      // encoding)
      int[] grayRgb = {128, 128, 128};
      int[] grayLab = CIELab.rgbToDicomLab(grayRgb[0], grayRgb[1], grayRgb[2]);

      // a* and b* should be close to neutral (32768)
      int neutralValue = 32768;
      int tolerance = 150; // Allow small deviation due to rounding

      assertTrue(
          Math.abs(grayLab[1] - neutralValue) <= tolerance,
          String.format(
              "a* component for gray should be near %d, got %d", neutralValue, grayLab[1]));
      assertTrue(
          Math.abs(grayLab[2] - neutralValue) <= tolerance,
          String.format(
              "b* component for gray should be near %d, got %d", neutralValue, grayLab[2]));
    }

    @Test
    @DisplayName("Should produce higher L* values for brighter colors")
    void shouldProduceHigherLValuesForBrighterColors() {
      int[] darkLab = CIELab.rgbToDicomLab(50, 50, 50);
      int[] brightLab = CIELab.rgbToDicomLab(200, 200, 200);

      assertTrue(
          brightLab[0] > darkLab[0],
          String.format(
              "Bright color L* (%d) should be greater than dark color L* (%d)",
              brightLab[0], darkLab[0]));
    }
  }

  // Helper method to provide test data for parameterized tests
  static Stream<Arguments> provideValidColorData() {
    return Stream.of(
        Arguments.of(BRIGHT_YELLOW),
        Arguments.of(VERY_DARK),
        Arguments.of(PURE_WHITE),
        Arguments.of(PURE_BLACK),
        Arguments.of(PURE_RED),
        Arguments.of(PURE_GREEN),
        Arguments.of(PURE_BLUE),
        Arguments.of(MID_GRAY));
  }

  /** Helper class to represent test color data with descriptive names */
  private record TestColorData(String name, int[] rgb, int[] dicomLab) {
    private TestColorData(String name, int[] rgb, int[] dicomLab) {
      this.name = name;
      this.rgb = rgb.clone();
      this.dicomLab = dicomLab.clone();
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
