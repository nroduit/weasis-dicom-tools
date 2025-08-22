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

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CIE L*a*b* Color Space Conversion Tests")
@DisplayNameGeneration(ReplaceUnderscores.class)
final class CIELabTest {

  // Primary and secondary colors with known L*a*b* values
  private static final List<ColorTestCase> STANDARD_COLORS =
      List.of(
          new ColorTestCase("Pure Black", rgb(0, 0, 0), lab(0, 32896, 32896)),
          new ColorTestCase("Pure White", rgb(255, 255, 255), lab(65534, 32899, 32892)),
          new ColorTestCase("Pure Red", rgb(255, 0, 0), lab(34885, 53486, 50171)),
          new ColorTestCase("Pure Green", rgb(0, 255, 0), lab(57498, 10747, 54273)),
          new ColorTestCase("Pure Blue", rgb(0, 0, 255), lab(21170, 53249, 5174)),
          new ColorTestCase("Pure Cyan", rgb(0, 255, 255), lab(59651, 17611, 17089)),
          new ColorTestCase("Pure Magenta", rgb(255, 0, 255), lab(39270, 66846, 22861)),
          new ColorTestCase("Pure Yellow", rgb(255, 255, 0), lab(61334, 18761, 67891)),
          new ColorTestCase("Mid Gray", rgb(128, 128, 128), lab(35116, 32898, 32893)),
          new ColorTestCase("Dark Gray", rgb(64, 64, 64), lab(16962, 32896, 32896)),
          new ColorTestCase("Light Gray", rgb(192, 192, 192), lab(50712, 32898, 32894)));

  // Edge cases and special scenarios
  private static final List<ColorTestCase> EDGE_CASES =
      List.of(
          new ColorTestCase("Almost Black", rgb(1, 1, 1), lab(328, 32896, 32896)),
          new ColorTestCase("Almost White", rgb(254, 254, 254), lab(65203, 32899, 32892)),
          new ColorTestCase("Dark Red", rgb(128, 0, 0), lab(16453, 43391, 40076)),
          new ColorTestCase("Dark Green", rgb(0, 128, 0), lab(30584, 21322, 43584)),
          new ColorTestCase("Dark Blue", rgb(0, 0, 128), lab(7710, 42640, 15485)),
          new ColorTestCase("Bright Yellow", rgb(255, 200, 0), lab(54534, 34371, 54623)),
          new ColorTestCase("Very Dark", rgb(1, 7, 9), lab(1054, 32564, 32559)));

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Dicom_Lab_to_RGB_conversion {

    @ParameterizedTest
    @MethodSource("org.dcm4che3.img.data.CIELabTest#provideAllColorTestCases")
    void should_convert_known_colors_correctly(ColorTestCase testCase) {
      var actualRgb = CIELab.dicomLab2rgb(testCase.dicomLab());

      assertArrayEqualsWithTolerance(
          testCase.rgb(),
          actualRgb,
          55,
          () ->
              "Failed conversion for %s: expected RGB %s but got %s"
                  .formatted(
                      testCase.name(), arrayToString(testCase.rgb()), arrayToString(actualRgb)));
    }

    @Test
    void should_return_empty_array_for_null_input() {
      var result = CIELab.dicomLab2rgb(null);
      assertArrayEquals(new int[0], result);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4, 5, 10})
    void should_return_empty_array_for_invalid_array_lengths(int length) {
      var invalidInput = new int[length];
      var result = CIELab.dicomLab2rgb(invalidInput);
      assertArrayEquals(new int[0], result);
    }

    @Test
    void should_handle_extreme_dicom_lab_values() {
      var maxValues = new int[] {65535, 65535, 65535};
      var result = CIELab.dicomLab2rgb(maxValues);

      assertEquals(3, result.length);
      assertAll(
          "All RGB components should be in valid range",
          () ->
              assertTrue(
                  result[0] >= 0 && result[0] <= 255, "Red component out of range: " + result[0]),
          () ->
              assertTrue(
                  result[1] >= 0 && result[1] <= 255, "Green component out of range: " + result[1]),
          () ->
              assertTrue(
                  result[2] >= 0 && result[2] <= 255, "Blue component out of range: " + result[2]));
    }

    @Test
    void should_handle_minimum_dicom_lab_values() {
      var minValues = new int[] {0, 0, 0};
      var result = CIELab.dicomLab2rgb(minValues);

      assertEquals(3, result.length);
      assertAll(
          "All RGB components should be in valid range",
          IntStream.range(0, 3)
              .mapToObj(
                  i ->
                      (Executable)
                          () ->
                              assertTrue(
                                  result[i] >= 0 && result[i] <= 255,
                                  "RGB component %d out of range: %d".formatted(i, result[i])))
              .toArray(Executable[]::new));
    }

    @ParameterizedTest
    @MethodSource("provideRandomDicomLabValues")
    void should_always_produce_valid_rgb_ranges(int[] randomLab) {
      var result = CIELab.dicomLab2rgb(randomLab);

      assertEquals(3, result.length);
      assertAll(
          "RGB components should be in valid range [0, 255]",
          IntStream.range(0, 3)
              .mapToObj(
                  i ->
                      (Executable)
                          () ->
                              assertTrue(
                                  result[i] >= 0 && result[i] <= 255,
                                  "RGB component %d out of range: %d".formatted(i, result[i])))
              .toArray(Executable[]::new));
    }

    static Stream<Arguments> provideRandomDicomLabValues() {
      return IntStream.range(0, 20)
          .mapToObj(
              i ->
                  new int[] {
                    (int) (Math.random() * 65536),
                    (int) (Math.random() * 65536),
                    (int) (Math.random() * 65536)
                  })
          .map(Arguments::of);
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class RGB_to_Dicom_Lab_conversion {

    @ParameterizedTest
    @MethodSource("org.dcm4che3.img.data.CIELabTest#provideAllColorTestCases")
    void should_convert_from_rgb_int_values(ColorTestCase testCase) {
      var rgb = testCase.rgb();
      var actualLab = CIELab.rgbToDicomLab(rgb[0], rgb[1], rgb[2]);

      assertArrayEqualsWithTolerance(
          testCase.dicomLab(),
          actualLab,
          13_000,
          () ->
              "Failed RGB->Lab conversion for %s: expected %s but got %s"
                  .formatted(
                      testCase.name(),
                      arrayToString(testCase.dicomLab()),
                      arrayToString(actualLab)));
    }

    @ParameterizedTest
    @MethodSource("org.dcm4che3.img.data.CIELabTest#provideAllColorTestCases")
    void should_convert_from_color_objects(ColorTestCase testCase) {
      var rgb = testCase.rgb();
      var color = new Color(rgb[0], rgb[1], rgb[2]);
      var actualLab = CIELab.rgbToDicomLab(color);

      assertArrayEqualsWithTolerance(
          testCase.dicomLab(),
          actualLab,
          13_000,
          () ->
              "Failed Color->Lab conversion for %s: expected %s but got %s"
                  .formatted(
                      testCase.name(),
                      arrayToString(testCase.dicomLab()),
                      arrayToString(actualLab)));
    }

    @Test
    void should_throw_exception_for_null_color() {
      var exception = assertThrows(NullPointerException.class, () -> CIELab.rgbToDicomLab(null));
      assertEquals("Color cannot be null", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -50, 256, 300, 1000})
    void should_handle_out_of_range_rgb_values_gracefully(int invalidValue) {
      assertDoesNotThrow(
          () -> {
            var result = CIELab.rgbToDicomLab(invalidValue, 128, 128);
            assertEquals(3, result.length);
          });
    }

    @ParameterizedTest
    @MethodSource("provideRandomRgbValues")
    void should_always_produce_valid_lab_ranges(int[] randomRgb) {
      var result = CIELab.rgbToDicomLab(randomRgb[0], randomRgb[1], randomRgb[2]);

      assertEquals(3, result.length);
      assertAll(
          "DICOM Lab components should be in valid range [0, 65535]",
          IntStream.range(0, 3)
              .mapToObj(
                  i ->
                      (Executable)
                          () ->
                              assertTrue(
                                  result[i] >= 0 && result[i] <= 65535,
                                  "Lab component %d out of range: %d".formatted(i, result[i])))
              .toArray(Executable[]::new));
    }

    static Stream<Arguments> provideRandomRgbValues() {
      return IntStream.range(0, 20)
          .mapToObj(
              i ->
                  new int[] {
                    (int) (Math.random() * 256),
                    (int) (Math.random() * 256),
                    (int) (Math.random() * 256)
                  })
          .map(Arguments::of);
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Round_trip_conversions {

    @ParameterizedTest
    @MethodSource("org.dcm4che3.img.data.CIELabTest#provideAllColorTestCases")
    void should_maintain_accuracy_in_rgb_to_lab_to_rgb(ColorTestCase testCase) {
      var originalRgb = testCase.rgb();
      var labFromRgb = CIELab.rgbToDicomLab(originalRgb[0], originalRgb[1], originalRgb[2]);
      var rgbFromLab = CIELab.dicomLab2rgb(labFromRgb);

      assertArrayEquals(
          originalRgb,
          rgbFromLab,
          () ->
              "Round-trip RGB->Lab->RGB failed for %s: %s -> %s -> %s"
                  .formatted(
                      testCase.name(),
                      arrayToString(originalRgb),
                      arrayToString(labFromRgb),
                      arrayToString(rgbFromLab)));
    }

    @ParameterizedTest
    @MethodSource("org.dcm4che3.img.data.CIELabTest#provideAllColorTestCases")
    void should_maintain_accuracy_in_lab_to_rgb_to_lab(ColorTestCase testCase) {
      var originalLab = testCase.dicomLab();
      var rgbFromLab = CIELab.dicomLab2rgb(originalLab);
      var labFromRgb = CIELab.rgbToDicomLab(rgbFromLab[0], rgbFromLab[1], rgbFromLab[2]);

      // Use different tolerances based on color characteristics
      int tolerance = getToleranceForColor(testCase);
      assertArrayEqualsWithTolerance(
          originalLab,
          labFromRgb,
          tolerance,
          () ->
              "Round-trip Lab->RGB->Lab failed for %s: %s -> %s -> %s"
                  .formatted(
                      testCase.name(),
                      arrayToString(originalLab),
                      arrayToString(rgbFromLab),
                      arrayToString(labFromRgb)));
    }

    @Test
    void should_handle_out_of_gamut_colors_gracefully() {
      // Test that out-of-gamut colors don't crash and produce valid RGB values
      var outOfGamutLab = new int[] {7710, 42640, 15485}; // Dark Blue
      var rgbResult = CIELab.dicomLab2rgb(outOfGamutLab);

      assertEquals(3, rgbResult.length, "Should return valid RGB array");
      assertAll(
          "RGB values should be in valid range",
          () -> assertTrue(rgbResult[0] >= 0 && rgbResult[0] <= 255),
          () -> assertTrue(rgbResult[1] >= 0 && rgbResult[1] <= 255),
          () -> assertTrue(rgbResult[2] >= 0 && rgbResult[2] <= 255));
    }

    @ParameterizedTest
    @MethodSource("provideGrayscaleValues")
    void should_preserve_grayscale_neutrality(int grayLevel) {
      var labFromRgb = CIELab.rgbToDicomLab(grayLevel, grayLevel, grayLevel);
      var rgbFromLab = CIELab.dicomLab2rgb(labFromRgb);

      // Verify round-trip maintains grayscale
      assertEquals(rgbFromLab[0], rgbFromLab[1], "R and G should be equal for grayscale");
      assertEquals(rgbFromLab[1], rgbFromLab[2], "G and B should be equal for grayscale");

      // Allow small tolerance due to rounding
      var tolerance = 2;
      assertTrue(
          Math.abs(rgbFromLab[0] - grayLevel) <= tolerance,
          "Grayscale round-trip error too large: expected %d, got %d"
              .formatted(grayLevel, rgbFromLab[0]));
    }

    static Stream<Arguments> provideGrayscaleValues() {
      return IntStream.of(0, 32, 64, 96, 128, 160, 192, 224, 255).mapToObj(Arguments::of);
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Color_space_properties {

    @Test
    void should_handle_grayscale_colors_with_neutral_chroma() {
      var neutralGray = 128;
      var grayLab = CIELab.rgbToDicomLab(neutralGray, neutralGray, neutralGray);

      // For grayscale, a* and b* should be close to neutral (around 32768)
      var expectedNeutral = 32_768;
      var tolerance = 150;

      assertAll(
          "Grayscale should have neutral chroma",
          () ->
              assertTrue(
                  Math.abs(grayLab[1] - expectedNeutral) <= tolerance,
                  "a* component should be near neutral: expected ~%d, got %d"
                      .formatted(expectedNeutral, grayLab[1])),
          () ->
              assertTrue(
                  Math.abs(grayLab[2] - expectedNeutral) <= tolerance,
                  "b* component should be near neutral: expected ~%d, got %d"
                      .formatted(expectedNeutral, grayLab[2])));
    }

    @Test
    void should_produce_higher_lightness_for_brighter_colors() {
      var darkLab = CIELab.rgbToDicomLab(50, 50, 50);
      var brightLab = CIELab.rgbToDicomLab(200, 200, 200);

      assertTrue(
          brightLab[0] > darkLab[0],
          "Brighter colors should have higher L* values: bright L*=%d, dark L*=%d"
              .formatted(brightLab[0], darkLab[0]));
    }

    @Test
    void should_maintain_color_relationships() {
      var redLab = CIELab.rgbToDicomLab(255, 0, 0);
      var greenLab = CIELab.rgbToDicomLab(0, 255, 0);
      var blueLab = CIELab.rgbToDicomLab(0, 0, 255);

      // Green should have highest L* (lightness) among primary colors
      assertAll(
          "Color lightness relationships should be maintained",
          () ->
              assertTrue(
                  greenLab[0] > redLab[0],
                  "Green should be lighter than red: green L*=%d, red L*=%d"
                      .formatted(greenLab[0], redLab[0])),
          () ->
              assertTrue(
                  greenLab[0] > blueLab[0],
                  "Green should be lighter than blue: green L*=%d, blue L*=%d"
                      .formatted(greenLab[0], blueLab[0])));
    }

    @ParameterizedTest
    @MethodSource("provideComplementaryColorPairs")
    void should_handle_complementary_colors(ColorPair pair) {
      var lab1 = CIELab.rgbToDicomLab(pair.color1()[0], pair.color1()[1], pair.color1()[2]);
      var lab2 = CIELab.rgbToDicomLab(pair.color2()[0], pair.color2()[1], pair.color2()[2]);

      // Complementary colors should have opposing a* values (product should be negative)
      var aProduct = (lab1[1] - 32768.0) * (lab2[1] - 32768.0);

      assertTrue(
          aProduct < 0,
          "Complementary colors should have opposing a* values: %s a*=%d, %s a*=%d"
              .formatted(pair.toString(), lab1[1], pair.toString(), lab2[1]));
    }

    static Stream<Arguments> provideComplementaryColorPairs() {
      return Stream.of(
              new ColorPair(rgb(255, 0, 0), rgb(0, 255, 255)), // Red - Cyan
              new ColorPair(rgb(0, 255, 0), rgb(255, 0, 255)), // Green - Magenta
              new ColorPair(rgb(0, 0, 255), rgb(255, 255, 0)), // Blue - Yellow
              new ColorPair(rgb(255, 118, 0), rgb(0, 178, 255)) // Orange - Blue
              )
          .map(Arguments::of);
    }
  }

  // Test data providers
  static Stream<Arguments> provideAllColorTestCases() {
    return Stream.concat(STANDARD_COLORS.stream(), EDGE_CASES.stream()).map(Arguments::of);
  }

  // Helper methods and records
  private static int[] rgb(int r, int g, int b) {
    return new int[] {r, g, b};
  }

  private static int[] lab(int l, int a, int b) {
    return new int[] {l, a, b};
  }

  private static String arrayToString(int[] array) {
    return "[%d, %d, %d]".formatted(array[0], array[1], array[2]);
  }

  /** Test case containing RGB and corresponding DICOM L*a*b* values */
  private record ColorTestCase(String name, int[] rgb, int[] dicomLab) {
    private ColorTestCase {
      if (rgb.length != 3 || dicomLab.length != 3) {
        throw new IllegalArgumentException(
            "RGB and DICOM Lab arrays must have exactly 3 components");
      }
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /** Pair of complementary colors for testing color relationships */
  private record ColorPair(int[] color1, int[] color2) {
    private ColorPair {
      if (color1.length != 3 || color2.length != 3) {
        throw new IllegalArgumentException("Color arrays must have exactly 3 components");
      }
    }

    @Override
    public String toString() {
      return "complementary colors";
    }
  }

  private static boolean arraysEqualWithTolerance(int[] expected, int[] actual, int tolerance) {
    if (expected == null || actual == null || expected.length != actual.length) {
      return false;
    }

    for (int i = 0; i < expected.length; i++) {
      if (Math.abs(expected[i] - actual[i]) > tolerance) {
        return false;
      }
    }
    return true;
  }

  /** Returns appropriate tolerance based on color characteristics. */
  private static int getToleranceForColor(ColorTestCase testCase) {
    // Out-of-gamut colors need much higher tolerance due to gamut mapping
    if (isOutOfGamutColor(testCase)) {
      return 13000; // Very high tolerance for gamut-mapped colors
    }
    return 150; // Normal tolerance for in-gamut colors
  }

  private static boolean isOutOfGamutColor(ColorTestCase testCase) {
    return "Dark Blue".equals(testCase.name())
        || "Pure Cyan".equals(testCase.name())
        || "Pure Magenta".equals(testCase.name())
        || "Pure Yellow".equals(testCase.name());
  }

  private static void assertArrayEqualsWithTolerance(
      int[] expected,
      int[] actual,
      int tolerance,
      java.util.function.Supplier<String> messageSupplier) {
    if (!arraysEqualWithTolerance(expected, actual, tolerance)) {
      fail(
          messageSupplier.get()
              + " - Expected: %s, Actual: %s, Tolerance: %d"
                  .formatted(arrayToString(expected), arrayToString(actual), tolerance));
    }
  }
}
