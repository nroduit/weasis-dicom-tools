/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.geom;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PatientOrientationTest {

  // Test vectors for different orientations
  private static final Vector3 NEGATIVE_X = new Vector3(-1.0, 0.0, 0.0);
  private static final Vector3 POSITIVE_X = new Vector3(1.0, 0.0, 0.0);
  private static final Vector3 NEGATIVE_Y = new Vector3(0.0, -1.0, 0.0);
  private static final Vector3 POSITIVE_Y = new Vector3(0.0, 1.0, 0.0);
  private static final Vector3 NEGATIVE_Z = new Vector3(0.0, 0.0, -1.0);
  private static final Vector3 POSITIVE_Z = new Vector3(0.0, 0.0, 1.0);

  @Nested
  class Color_Constants_Tests {

    @Test
    void color_constants_have_correct_values() {
      assertEquals(new Color(44783), PatientOrientation.BLUE);
      assertEquals(new Color(15539236), PatientOrientation.RED);
      assertEquals(new Color(897355), PatientOrientation.GREEN);
    }
  }

  @Nested
  class Biped_Enum_Tests {

    @ParameterizedTest
    @EnumSource(PatientOrientation.Biped.class)
    void all_biped_orientations_implement_orientation_interface(PatientOrientation.Biped biped) {
      assertNotNull(biped.name());
      assertNotNull(biped.getFullName());
      assertNotNull(biped.getColor());
      assertEquals(biped.getFullName(), biped.toString());
    }

    @ParameterizedTest
    @MethodSource("bipedOrientationTestData")
    void biped_orientations_have_correct_properties(
        PatientOrientation.Biped biped, String expectedFullName, Color expectedColor) {

      assertEquals(expectedFullName, biped.getFullName());
      assertEquals(expectedColor, biped.getColor());
      assertEquals(expectedFullName, biped.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"R", "L", "A", "P", "F", "H", "r", "l", "a", "p", "f", "h"})
    void fromString_with_valid_values_returns_correct_orientation(String value) {
      var result = PatientOrientation.Biped.fromString(value);

      assertNotNull(result);
      assertEquals(value.toUpperCase(), result.name());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "X", "INVALID", "123", "null"})
    void fromString_with_invalid_values_returns_null(String value) {
      var result = PatientOrientation.Biped.fromString(value);

      assertNull(result);
    }

    @Test
    void fromString_with_null_returns_null() {
      var result = PatientOrientation.Biped.fromString(null);

      assertNull(result);
    }

    @Test
    void fromString_with_whitespace_trims_correctly() {
      var result = PatientOrientation.Biped.fromString("  R  ");

      assertEquals(PatientOrientation.Biped.R, result);
    }

    static Stream<Arguments> bipedOrientationTestData() {
      return Stream.of(
          Arguments.of(PatientOrientation.Biped.R, "Right", PatientOrientation.BLUE),
          Arguments.of(PatientOrientation.Biped.L, "Left", PatientOrientation.BLUE),
          Arguments.of(PatientOrientation.Biped.A, "Anterior", PatientOrientation.RED),
          Arguments.of(PatientOrientation.Biped.P, "Posterior", PatientOrientation.RED),
          Arguments.of(PatientOrientation.Biped.F, "Foot", PatientOrientation.GREEN),
          Arguments.of(PatientOrientation.Biped.H, "Head", PatientOrientation.GREEN));
    }
  }

  @Nested
  class Quadruped_Enum_Tests {

    @ParameterizedTest
    @EnumSource(PatientOrientation.Quadruped.class)
    void all_quadruped_orientations_implement_orientation_interface(
        PatientOrientation.Quadruped quadruped) {
      assertNotNull(quadruped.name());
      assertNotNull(quadruped.getFullName());
      assertNotNull(quadruped.getColor());
      assertEquals(quadruped.getFullName(), quadruped.toString());
    }

    @ParameterizedTest
    @MethodSource("quadrupedOrientationTestData")
    void quadruped_orientations_have_correct_properties(
        PatientOrientation.Quadruped quadruped, String expectedFullName, Color expectedColor) {

      assertEquals(expectedFullName, quadruped.getFullName());
      assertEquals(expectedColor, quadruped.getColor());
      assertEquals(expectedFullName, quadruped.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RT", "LE", "V", "D", "CR", "CD", "rt", "le", "v", "d", "cr", "cd"})
    void fromString_with_valid_values_returns_correct_orientation(String value) {
      var result = PatientOrientation.Quadruped.fromString(value);

      assertNotNull(result);
      assertEquals(value.toUpperCase(), result.name());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "X", "INVALID", "123", "R", "L"})
    void fromString_with_invalid_values_returns_null(String value) {
      var result = PatientOrientation.Quadruped.fromString(value);

      assertNull(result);
    }

    @Test
    void fromString_with_null_returns_null() {
      var result = PatientOrientation.Quadruped.fromString(null);

      assertNull(result);
    }

    @Test
    void fromString_with_whitespace_trims_correctly() {
      var result = PatientOrientation.Quadruped.fromString("  RT  ");

      assertEquals(PatientOrientation.Quadruped.RT, result);
    }

    static Stream<Arguments> quadrupedOrientationTestData() {
      return Stream.of(
          Arguments.of(PatientOrientation.Quadruped.RT, "Right", PatientOrientation.BLUE),
          Arguments.of(PatientOrientation.Quadruped.LE, "Left", PatientOrientation.BLUE),
          Arguments.of(PatientOrientation.Quadruped.V, "Ventral", PatientOrientation.RED),
          Arguments.of(PatientOrientation.Quadruped.D, "Dorsal", PatientOrientation.RED),
          Arguments.of(PatientOrientation.Quadruped.CR, "Cranial", PatientOrientation.GREEN),
          Arguments.of(PatientOrientation.Quadruped.CD, "Caudal", PatientOrientation.GREEN));
    }
  }

  @Nested
  class Biped_Axis_Orientation_Tests {

    @ParameterizedTest
    @MethodSource("bipedXAxisTestData")
    void getBipedXOrientation_returns_correct_orientation(
        Vector3 vector, PatientOrientation.Biped expected) {
      var result = PatientOrientation.getBipedXOrientation(vector);

      assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("bipedYAxisTestData")
    void getBipedYOrientation_returns_correct_orientation(
        Vector3 vector, PatientOrientation.Biped expected) {
      var result = PatientOrientation.getBipedYOrientation(vector);

      assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("bipedZAxisTestData")
    void getBipedZOrientation_returns_correct_orientation(
        Vector3 vector, PatientOrientation.Biped expected) {
      var result = PatientOrientation.getBipedZOrientation(vector);

      assertEquals(expected, result);
    }

    @Test
    void getBipedXOrientation_with_null_vector_throws_exception() {
      assertThrows(NullPointerException.class, () -> PatientOrientation.getBipedXOrientation(null));
    }

    @Test
    void getBipedYOrientation_with_null_vector_throws_exception() {
      assertThrows(NullPointerException.class, () -> PatientOrientation.getBipedYOrientation(null));
    }

    @Test
    void getBipedZOrientation_with_null_vector_throws_exception() {
      assertThrows(NullPointerException.class, () -> PatientOrientation.getBipedZOrientation(null));
    }

    static Stream<Arguments> bipedXAxisTestData() {
      return Stream.of(
          Arguments.of(NEGATIVE_X, PatientOrientation.Biped.R),
          Arguments.of(POSITIVE_X, PatientOrientation.Biped.L),
          Arguments.of(new Vector3(-0.5, 0.0, 0.0), PatientOrientation.Biped.R),
          Arguments.of(new Vector3(0.5, 0.0, 0.0), PatientOrientation.Biped.L),
          Arguments.of(new Vector3(0.0, 1.0, 1.0), PatientOrientation.Biped.L) // Zero X = positive
          );
    }

    static Stream<Arguments> bipedYAxisTestData() {
      return Stream.of(
          Arguments.of(NEGATIVE_Y, PatientOrientation.Biped.A),
          Arguments.of(POSITIVE_Y, PatientOrientation.Biped.P),
          Arguments.of(new Vector3(0.0, -0.5, 0.0), PatientOrientation.Biped.A),
          Arguments.of(new Vector3(0.0, 0.5, 0.0), PatientOrientation.Biped.P),
          Arguments.of(new Vector3(1.0, 0.0, 1.0), PatientOrientation.Biped.P) // Zero Y = positive
          );
    }

    static Stream<Arguments> bipedZAxisTestData() {
      return Stream.of(
          Arguments.of(NEGATIVE_Z, PatientOrientation.Biped.F),
          Arguments.of(POSITIVE_Z, PatientOrientation.Biped.H),
          Arguments.of(new Vector3(0.0, 0.0, -0.5), PatientOrientation.Biped.F),
          Arguments.of(new Vector3(0.0, 0.0, 0.5), PatientOrientation.Biped.H),
          Arguments.of(new Vector3(1.0, 1.0, 0.0), PatientOrientation.Biped.H) // Zero Z = positive
          );
    }
  }

  @Nested
  class Quadruped_Axis_Orientation_Tests {

    @ParameterizedTest
    @MethodSource("quadrupedXAxisTestData")
    void getQuadrupedXOrientation_returns_correct_orientation(
        Vector3 vector, PatientOrientation.Quadruped expected) {
      var result = PatientOrientation.getQuadrupedXOrientation(vector);

      assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("quadrupedYAxisTestData")
    void getQuadrupedYOrientation_returns_correct_orientation(
        Vector3 vector, PatientOrientation.Quadruped expected) {
      var result = PatientOrientation.getQuadrupedYOrientation(vector);

      assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("quadrupedZAxisTestData")
    void getQuadrupedZOrientation_returns_correct_orientation(
        Vector3 vector, PatientOrientation.Quadruped expected) {
      var result = PatientOrientation.getQuadrupedZOrientation(vector);

      assertEquals(expected, result);
    }

    @Test
    void getQuadrupedXOrientation_with_null_vector_throws_exception() {
      assertThrows(
          NullPointerException.class, () -> PatientOrientation.getQuadrupedXOrientation(null));
    }

    @Test
    void getQuadrupedYOrientation_with_null_vector_throws_exception() {
      assertThrows(
          NullPointerException.class, () -> PatientOrientation.getQuadrupedYOrientation(null));
    }

    @Test
    void getQuadrupedZOrientation_with_null_vector_throws_exception() {
      assertThrows(
          NullPointerException.class, () -> PatientOrientation.getQuadrupedZOrientation(null));
    }

    static Stream<Arguments> quadrupedXAxisTestData() {
      return Stream.of(
          Arguments.of(NEGATIVE_X, PatientOrientation.Quadruped.RT),
          Arguments.of(POSITIVE_X, PatientOrientation.Quadruped.LE),
          Arguments.of(new Vector3(-0.5, 0.0, 0.0), PatientOrientation.Quadruped.RT),
          Arguments.of(new Vector3(0.5, 0.0, 0.0), PatientOrientation.Quadruped.LE));
    }

    static Stream<Arguments> quadrupedYAxisTestData() {
      return Stream.of(
          Arguments.of(NEGATIVE_Y, PatientOrientation.Quadruped.V),
          Arguments.of(POSITIVE_Y, PatientOrientation.Quadruped.D),
          Arguments.of(new Vector3(0.0, -0.5, 0.0), PatientOrientation.Quadruped.V),
          Arguments.of(new Vector3(0.0, 0.5, 0.0), PatientOrientation.Quadruped.D));
    }

    static Stream<Arguments> quadrupedZAxisTestData() {
      return Stream.of(
          Arguments.of(NEGATIVE_Z, PatientOrientation.Quadruped.CD),
          Arguments.of(POSITIVE_Z, PatientOrientation.Quadruped.CR),
          Arguments.of(new Vector3(0.0, 0.0, -0.5), PatientOrientation.Quadruped.CD),
          Arguments.of(new Vector3(0.0, 0.0, 0.5), PatientOrientation.Quadruped.CR));
    }
  }

  @Nested
  class Generic_Axis_Orientation_Tests {

    @ParameterizedTest
    @MethodSource("axisOrientationTestData")
    void getAxisOrientation_returns_correct_orientation(
        Vector3 vector, PatientOrientation.Axis axis, boolean quadruped, Orientation expected) {

      var result = PatientOrientation.getAxisOrientation(vector, axis, quadruped);

      assertEquals(expected, result);
    }

    @Test
    void getAxisOrientation_with_null_vector_throws_exception() {
      assertThrows(
          NullPointerException.class,
          () -> PatientOrientation.getAxisOrientation(null, PatientOrientation.Axis.X, false));
    }

    @Test
    void getAxisOrientation_with_null_axis_throws_exception() {
      assertThrows(
          NullPointerException.class,
          () -> PatientOrientation.getAxisOrientation(POSITIVE_X, null, false));
    }

    static Stream<Arguments> axisOrientationTestData() {
      return Stream.of(
          // Biped X-axis
          Arguments.of(NEGATIVE_X, PatientOrientation.Axis.X, false, PatientOrientation.Biped.R),
          Arguments.of(POSITIVE_X, PatientOrientation.Axis.X, false, PatientOrientation.Biped.L),
          // Biped Y-axis
          Arguments.of(NEGATIVE_Y, PatientOrientation.Axis.Y, false, PatientOrientation.Biped.A),
          Arguments.of(POSITIVE_Y, PatientOrientation.Axis.Y, false, PatientOrientation.Biped.P),
          // Biped Z-axis
          Arguments.of(NEGATIVE_Z, PatientOrientation.Axis.Z, false, PatientOrientation.Biped.F),
          Arguments.of(POSITIVE_Z, PatientOrientation.Axis.Z, false, PatientOrientation.Biped.H),
          // Quadruped X-axis
          Arguments.of(
              NEGATIVE_X, PatientOrientation.Axis.X, true, PatientOrientation.Quadruped.RT),
          Arguments.of(
              POSITIVE_X, PatientOrientation.Axis.X, true, PatientOrientation.Quadruped.LE),
          // Quadruped Y-axis
          Arguments.of(NEGATIVE_Y, PatientOrientation.Axis.Y, true, PatientOrientation.Quadruped.V),
          Arguments.of(POSITIVE_Y, PatientOrientation.Axis.Y, true, PatientOrientation.Quadruped.D),
          // Quadruped Z-axis
          Arguments.of(
              NEGATIVE_Z, PatientOrientation.Axis.Z, true, PatientOrientation.Quadruped.CD),
          Arguments.of(
              POSITIVE_Z, PatientOrientation.Axis.Z, true, PatientOrientation.Quadruped.CR));
    }
  }

  @Nested
  class Axis_Enum_Tests {

    @ParameterizedTest
    @EnumSource(PatientOrientation.Axis.class)
    void all_axis_values_are_defined(PatientOrientation.Axis axis) {
      assertNotNull(axis);
      assertNotNull(axis.name());
    }

    @Test
    void axis_enum_has_expected_values() {
      var axes = PatientOrientation.Axis.values();

      assertEquals(3, axes.length);
      assertEquals(PatientOrientation.Axis.X, axes[0]);
      assertEquals(PatientOrientation.Axis.Y, axes[1]);
      assertEquals(PatientOrientation.Axis.Z, axes[2]);
    }
  }

  @Nested
  class Opposite_Orientation_Tests {

    @ParameterizedTest
    @MethodSource("bipedOppositeTestData")
    void getOppositeOrientation_biped_returns_correct_opposite(
        PatientOrientation.Biped orientation, PatientOrientation.Biped expected) {

      var result = PatientOrientation.getOppositeOrientation(orientation);

      assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("quadrupedOppositeTestData")
    void getOppositeOrientation_quadruped_returns_correct_opposite(
        PatientOrientation.Quadruped orientation, PatientOrientation.Quadruped expected) {

      var result = PatientOrientation.getOppositeOrientation(orientation);

      assertEquals(expected, result);
    }

    @Test
    void getOppositeOrientation_biped_with_null_throws_exception() {
      assertThrows(
          NullPointerException.class,
          () -> PatientOrientation.getOppositeOrientation((PatientOrientation.Biped) null));
    }

    @Test
    void getOppositeOrientation_quadruped_with_null_throws_exception() {
      assertThrows(
          NullPointerException.class,
          () -> PatientOrientation.getOppositeOrientation((PatientOrientation.Quadruped) null));
    }

    @Test
    void opposite_orientation_is_symmetric_for_biped() {
      for (var orientation : PatientOrientation.Biped.values()) {
        var opposite = PatientOrientation.getOppositeOrientation(orientation);
        var doubleOpposite = PatientOrientation.getOppositeOrientation(opposite);

        assertEquals(
            orientation,
            doubleOpposite,
            "Double opposite should return original orientation for " + orientation);
      }
    }

    @Test
    void opposite_orientation_is_symmetric_for_quadruped() {
      for (var orientation : PatientOrientation.Quadruped.values()) {
        var opposite = PatientOrientation.getOppositeOrientation(orientation);
        var doubleOpposite = PatientOrientation.getOppositeOrientation(opposite);

        assertEquals(
            orientation,
            doubleOpposite,
            "Double opposite should return original orientation for " + orientation);
      }
    }

    static Stream<Arguments> bipedOppositeTestData() {
      return Stream.of(
          Arguments.of(PatientOrientation.Biped.R, PatientOrientation.Biped.L),
          Arguments.of(PatientOrientation.Biped.L, PatientOrientation.Biped.R),
          Arguments.of(PatientOrientation.Biped.A, PatientOrientation.Biped.P),
          Arguments.of(PatientOrientation.Biped.P, PatientOrientation.Biped.A),
          Arguments.of(PatientOrientation.Biped.F, PatientOrientation.Biped.H),
          Arguments.of(PatientOrientation.Biped.H, PatientOrientation.Biped.F));
    }

    static Stream<Arguments> quadrupedOppositeTestData() {
      return Stream.of(
          Arguments.of(PatientOrientation.Quadruped.RT, PatientOrientation.Quadruped.LE),
          Arguments.of(PatientOrientation.Quadruped.LE, PatientOrientation.Quadruped.RT),
          Arguments.of(PatientOrientation.Quadruped.V, PatientOrientation.Quadruped.D),
          Arguments.of(PatientOrientation.Quadruped.D, PatientOrientation.Quadruped.V),
          Arguments.of(PatientOrientation.Quadruped.CD, PatientOrientation.Quadruped.CR),
          Arguments.of(PatientOrientation.Quadruped.CR, PatientOrientation.Quadruped.CD));
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void biped_and_quadruped_orientations_use_same_color_scheme() {
      // Blue colors (lateral)
      assertEquals(
          PatientOrientation.Biped.R.getColor(), PatientOrientation.Quadruped.RT.getColor());
      assertEquals(
          PatientOrientation.Biped.L.getColor(), PatientOrientation.Quadruped.LE.getColor());

      // Red colors (sagittal)
      assertEquals(
          PatientOrientation.Biped.A.getColor(), PatientOrientation.Quadruped.V.getColor());
      assertEquals(
          PatientOrientation.Biped.P.getColor(), PatientOrientation.Quadruped.D.getColor());

      // Green colors (longitudinal)
      assertEquals(
          PatientOrientation.Biped.H.getColor(), PatientOrientation.Quadruped.CR.getColor());
      assertEquals(
          PatientOrientation.Biped.F.getColor(), PatientOrientation.Quadruped.CD.getColor());
    }

    @Test
    void complete_workflow_from_vector_to_orientation() {
      // Test complete workflow for typical DICOM vectors
      var anteriorVector = new Vector3(0.0, -1.0, 0.0);

      // Test biped workflow
      var bipedOrientation = PatientOrientation.getBipedYOrientation(anteriorVector);
      assertEquals(PatientOrientation.Biped.A, bipedOrientation);
      assertEquals("Anterior", bipedOrientation.getFullName());
      assertEquals(PatientOrientation.RED, bipedOrientation.getColor());

      var oppositeOrientation = PatientOrientation.getOppositeOrientation(bipedOrientation);
      assertEquals(PatientOrientation.Biped.P, oppositeOrientation);

      // Test generic method
      var genericOrientation =
          PatientOrientation.getAxisOrientation(anteriorVector, PatientOrientation.Axis.Y, false);
      assertEquals(bipedOrientation, genericOrientation);
    }

    @Test
    void orientation_interface_methods_work_correctly() {
      var orientation = PatientOrientation.Biped.R;

      // Test Orientation interface methods
      assertEquals("R", orientation.name());
      assertEquals("Right", orientation.getFullName());
      assertEquals(PatientOrientation.BLUE, orientation.getColor());
      assertEquals("Right", orientation.toString());
    }
  }

  @Nested
  class Edge_Cases_And_Error_Handling_Tests {

    @Test
    void zero_vector_handling() {
      var zeroVector = Vector3.ZERO;

      // All orientations should handle zero vectors consistently
      assertDoesNotThrow(() -> PatientOrientation.getBipedXOrientation(zeroVector));
      assertDoesNotThrow(() -> PatientOrientation.getBipedYOrientation(zeroVector));
      assertDoesNotThrow(() -> PatientOrientation.getBipedZOrientation(zeroVector));

      // Zero components should be treated as positive
      assertEquals(PatientOrientation.Biped.L, PatientOrientation.getBipedXOrientation(zeroVector));
      assertEquals(PatientOrientation.Biped.P, PatientOrientation.getBipedYOrientation(zeroVector));
      assertEquals(PatientOrientation.Biped.H, PatientOrientation.getBipedZOrientation(zeroVector));
    }

    @Test
    void very_small_vector_components_handling() {
      var smallVector = new Vector3(1e-15, -1e-15, 1e-15);

      // Should handle very small components correctly
      assertEquals(
          PatientOrientation.Biped.L, PatientOrientation.getBipedXOrientation(smallVector));
      assertEquals(
          PatientOrientation.Biped.A, PatientOrientation.getBipedYOrientation(smallVector));
      assertEquals(
          PatientOrientation.Biped.H, PatientOrientation.getBipedZOrientation(smallVector));
    }

    @Test
    void fromString_with_mixed_case_and_spaces() {
      assertEquals(PatientOrientation.Biped.R, PatientOrientation.Biped.fromString("  r  "));
      assertEquals(
          PatientOrientation.Quadruped.RT, PatientOrientation.Quadruped.fromString("  rt  "));
    }
  }
}
