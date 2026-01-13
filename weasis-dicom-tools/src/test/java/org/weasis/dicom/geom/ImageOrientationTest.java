/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.geom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
class ImageOrientationTest {

  // Test data for common DICOM orientations
  private static final Vector3 AXIAL_ROW = new Vector3(1.0, 0.0, 0.0); // Right->Left
  private static final Vector3 AXIAL_COL = new Vector3(0.0, 1.0, 0.0); // Anterior->Posterior
  private static final Vector3 SAGITTAL_ROW = new Vector3(0.0, 1.0, 0.0); // Anterior->Posterior
  private static final Vector3 SAGITTAL_COL = new Vector3(0.0, 0.0, 1.0); // Head->Foot
  private static final Vector3 CORONAL_ROW = new Vector3(1.0, 0.0, 0.0); // Right->Left
  private static final Vector3 CORONAL_COL = new Vector3(0.0, 0.0, 1.0); // Head->Foot

  @Nested
  class Plan_Enum_Tests {

    @ParameterizedTest
    @EnumSource(ImageOrientation.Plan.class)
    void toString_returns_display_name(ImageOrientation.Plan plan) {
      String displayName = plan.toString();

      assertNotNull(displayName);
      assertFalse(displayName.isEmpty());

      // Verify expected display names
      switch (plan) {
        case UNKNOWN -> assertEquals("Unknown", displayName);
        case TRANSVERSE -> assertEquals("Axial", displayName);
        case SAGITTAL -> assertEquals("Sagittal", displayName);
        case CORONAL -> assertEquals("Coronal", displayName);
        case OBLIQUE -> assertEquals("Oblique", displayName);
      }
    }
  }

  @Nested
  class GetPlan_With_Default_Threshold_Tests {

    @Test
    void getPlan_with_axial_vectors_returns_transverse() {
      var result = ImageOrientation.getPlan(AXIAL_ROW, AXIAL_COL);

      assertEquals(ImageOrientation.Plan.TRANSVERSE, result);
    }

    @Test
    void getPlan_with_sagittal_vectors_returns_sagittal() {
      var result = ImageOrientation.getPlan(SAGITTAL_ROW, SAGITTAL_COL);

      assertEquals(ImageOrientation.Plan.SAGITTAL, result);
    }

    @Test
    void getPlan_with_coronal_vectors_returns_coronal() {
      var result = ImageOrientation.getPlan(CORONAL_ROW, CORONAL_COL);

      assertEquals(ImageOrientation.Plan.CORONAL, result);
    }

    @Test
    void getPlan_with_null_row_vector_throws_exception() {
      assertThrows(NullPointerException.class, () -> ImageOrientation.getPlan(null, AXIAL_COL));
    }

    @Test
    void getPlan_with_null_column_vector_throws_exception() {
      assertThrows(NullPointerException.class, () -> ImageOrientation.getPlan(AXIAL_ROW, null));
    }
  }

  @Nested
  class GetPlan_With_Threshold_Tests {

    @ParameterizedTest
    @MethodSource("validAnatomicalPlanTestData")
    void getPlan_with_valid_anatomical_vectors_returns_expected_plan(
        Vector3 rowVector, Vector3 colVector, ImageOrientation.Plan expectedPlan) {

      var result = ImageOrientation.getPlan(rowVector, colVector, 0.8);

      assertEquals(expectedPlan, result);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, 2.0, Double.NaN, Double.POSITIVE_INFINITY})
    void getPlan_with_invalid_threshold_throws_exception(double invalidThreshold) {
      assertThrows(
          IllegalArgumentException.class,
          () -> ImageOrientation.getPlan(AXIAL_ROW, AXIAL_COL, invalidThreshold));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.5, 0.8, 0.9, 1.0})
    void getPlan_with_valid_thresholds_does_not_throw(double validThreshold) {
      assertDoesNotThrow(() -> ImageOrientation.getPlan(AXIAL_ROW, AXIAL_COL, validThreshold));
    }

    @Test
    void getPlan_with_oblique_vectors_returns_oblique() {
      var obliqueRow = new Vector3(0.5, 0.5, 0.5);
      var obliqueCol = new Vector3(0.3, 0.3, 0.3);

      var result = ImageOrientation.getPlan(obliqueRow, obliqueCol, 0.8);

      assertEquals(ImageOrientation.Plan.OBLIQUE, result);
    }

    static Stream<Arguments> validAnatomicalPlanTestData() {
      return Stream.of(
          Arguments.of(AXIAL_ROW, AXIAL_COL, ImageOrientation.Plan.TRANSVERSE),
          Arguments.of(SAGITTAL_ROW, SAGITTAL_COL, ImageOrientation.Plan.SAGITTAL),
          Arguments.of(CORONAL_ROW, CORONAL_COL, ImageOrientation.Plan.CORONAL),
          // Reversed vectors should give same results
          Arguments.of(AXIAL_COL, AXIAL_ROW, ImageOrientation.Plan.TRANSVERSE),
          Arguments.of(SAGITTAL_COL, SAGITTAL_ROW, ImageOrientation.Plan.SAGITTAL),
          Arguments.of(CORONAL_COL, CORONAL_ROW, ImageOrientation.Plan.CORONAL));
    }
  }

  @Nested
  class GetPlan_With_Orientations_Tests {

    @Test
    void getPlan_with_blue_and_red_orientations_returns_transverse() {
      var blueOrientation = PatientOrientation.Biped.R; // Blue color
      var redOrientation = PatientOrientation.Biped.A; // Red color

      var result = ImageOrientation.getPlan(blueOrientation, redOrientation);

      assertEquals(ImageOrientation.Plan.TRANSVERSE, result);
    }

    @Test
    void getPlan_with_blue_and_green_orientations_returns_coronal() {
      var blueOrientation = PatientOrientation.Biped.L; // Blue color
      var greenOrientation = PatientOrientation.Biped.H; // Green color

      var result = ImageOrientation.getPlan(blueOrientation, greenOrientation);

      assertEquals(ImageOrientation.Plan.CORONAL, result);
    }

    @Test
    void getPlan_with_red_and_green_orientations_returns_sagittal() {
      var redOrientation = PatientOrientation.Biped.P; // Red color
      var greenOrientation = PatientOrientation.Biped.F; // Green color

      var result = ImageOrientation.getPlan(redOrientation, greenOrientation);

      assertEquals(ImageOrientation.Plan.SAGITTAL, result);
    }

    @Test
    void getPlan_with_null_orientations_returns_oblique() {
      var result = ImageOrientation.getPlan((Orientation) null, null);

      assertEquals(ImageOrientation.Plan.OBLIQUE, result);
    }

    @Test
    void getPlan_with_same_color_orientations_returns_oblique() {
      var orientation1 = PatientOrientation.Biped.R; // Blue
      var orientation2 = PatientOrientation.Biped.L; // Blue

      var result = ImageOrientation.getPlan(orientation1, orientation2);

      assertEquals(ImageOrientation.Plan.OBLIQUE, result);
    }
  }

  @Nested
  class GetPatientOrientation_Tests {

    @Test
    void getPatientOrientation_with_dominant_x_axis_returns_x_orientation() {
      var vector = new Vector3(0.9, 0.1, 0.1);

      var result = ImageOrientation.getPatientOrientation(vector, 0.8, false);

      assertNotNull(result);
      assertEquals(PatientOrientation.Biped.L, result); // Positive X = Left
    }

    @Test
    void getPatientOrientation_with_dominant_y_axis_returns_y_orientation() {
      var vector = new Vector3(0.1, 0.9, 0.1);

      var result = ImageOrientation.getPatientOrientation(vector, 0.8, false);

      assertNotNull(result);
      assertEquals(PatientOrientation.Biped.P, result); // Positive Y = Posterior
    }

    @Test
    void getPatientOrientation_with_dominant_z_axis_returns_z_orientation() {
      var vector = new Vector3(0.1, 0.1, 0.9);

      var result = ImageOrientation.getPatientOrientation(vector, 0.8, false);

      assertNotNull(result);
      assertEquals(PatientOrientation.Biped.H, result); // Positive Z = Head
    }

    @Test
    void getPatientOrientation_with_no_dominant_axis_returns_null() {
      var vector = new Vector3(0.5, 0.5, 0.5);

      var result = ImageOrientation.getPatientOrientation(vector, 0.8, false);

      assertNull(result);
    }

    @ParameterizedTest
    @MethodSource("bipedOrientationTestData")
    void getPatientOrientation_with_biped_vectors_returns_correct_orientation(
        Vector3 vector, PatientOrientation.Biped expected) {

      var result = ImageOrientation.getPatientOrientation(vector, 0.5, false);

      assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("quadrupedOrientationTestData")
    void getPatientOrientation_with_quadruped_vectors_returns_correct_orientation(
        Vector3 vector, PatientOrientation.Quadruped expected) {

      var result = ImageOrientation.getPatientOrientation(vector, 0.5, true);

      assertEquals(expected, result);
    }

    static Stream<Arguments> bipedOrientationTestData() {
      return Stream.of(
          Arguments.of(new Vector3(-0.9, 0.1, 0.1), PatientOrientation.Biped.R),
          Arguments.of(new Vector3(0.9, 0.1, 0.1), PatientOrientation.Biped.L),
          Arguments.of(new Vector3(0.1, -0.9, 0.1), PatientOrientation.Biped.A),
          Arguments.of(new Vector3(0.1, 0.9, 0.1), PatientOrientation.Biped.P),
          Arguments.of(new Vector3(0.1, 0.1, -0.9), PatientOrientation.Biped.F),
          Arguments.of(new Vector3(0.1, 0.1, 0.9), PatientOrientation.Biped.H));
    }

    static Stream<Arguments> quadrupedOrientationTestData() {
      return Stream.of(
          Arguments.of(new Vector3(-0.9, 0.1, 0.1), PatientOrientation.Quadruped.RT),
          Arguments.of(new Vector3(0.9, 0.1, 0.1), PatientOrientation.Quadruped.LE),
          Arguments.of(new Vector3(0.1, -0.9, 0.1), PatientOrientation.Quadruped.V),
          Arguments.of(new Vector3(0.1, 0.9, 0.1), PatientOrientation.Quadruped.D),
          Arguments.of(new Vector3(0.1, 0.1, -0.9), PatientOrientation.Quadruped.CD),
          Arguments.of(new Vector3(0.1, 0.1, 0.9), PatientOrientation.Quadruped.CR));
    }
  }

  @Nested
  class AxisOrientation_Tests {

    @Test
    void getXAxisOrientation_with_biped_returns_biped_orientation() {
      var vector = new Vector3(-1.0, 0.0, 0.0);

      var result = ImageOrientation.getXAxisOrientation(vector, false);

      assertEquals(PatientOrientation.Biped.R, result);
    }

    @Test
    void getXAxisOrientation_with_quadruped_returns_quadruped_orientation() {
      var vector = new Vector3(-1.0, 0.0, 0.0);

      var result = ImageOrientation.getXAxisOrientation(vector, true);

      assertEquals(PatientOrientation.Quadruped.RT, result);
    }

    @Test
    void getYAxisOrientation_with_biped_returns_biped_orientation() {
      var vector = new Vector3(0.0, -1.0, 0.0);

      var result = ImageOrientation.getYAxisOrientation(vector, false);

      assertEquals(PatientOrientation.Biped.A, result);
    }

    @Test
    void getYAxisOrientation_with_quadruped_returns_quadruped_orientation() {
      var vector = new Vector3(0.0, -1.0, 0.0);

      var result = ImageOrientation.getYAxisOrientation(vector, true);

      assertEquals(PatientOrientation.Quadruped.V, result);
    }

    @Test
    void getZAxisOrientation_with_biped_returns_biped_orientation() {
      var vector = new Vector3(0.0, 0.0, -1.0);

      var result = ImageOrientation.getZAxisOrientation(vector, false);

      assertEquals(PatientOrientation.Biped.F, result);
    }

    @Test
    void getZAxisOrientation_with_quadruped_returns_quadruped_orientation() {
      var vector = new Vector3(0.0, 0.0, -1.0);

      var result = ImageOrientation.getZAxisOrientation(vector, true);

      assertEquals(PatientOrientation.Quadruped.CD, result);
    }
  }

  @Nested
  class Edge_Cases_And_Error_Handling_Tests {

    @Test
    void getPlan_with_very_small_vectors_handles_gracefully() {
      var smallVector = new Vector3(1e-10, 1e-10, 1e-10);

      var result = ImageOrientation.getPlan(smallVector, smallVector, 0.8);

      assertEquals(ImageOrientation.Plan.OBLIQUE, result);
    }

    @Test
    void getPlan_with_zero_vectors_handles_gracefully() {
      var zeroVector = Vector3.ZERO;

      var result = ImageOrientation.getPlan(zeroVector, zeroVector, 0.8);

      assertEquals(ImageOrientation.Plan.OBLIQUE, result);
    }

    @Test
    void getPatientOrientation_with_equal_components_returns_null() {
      var equalVector = new Vector3(0.577, 0.577, 0.577); // Normalized equal components

      var result = ImageOrientation.getPatientOrientation(equalVector, 0.8, false);

      assertNull(result);
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void full_workflow_from_vectors_to_plan() {
      // Simulate typical DICOM IOP array: [1,0,0,0,1,0] (axial slice)
      var rowVector = new Vector3(1.0, 0.0, 0.0);
      var colVector = new Vector3(0.0, 1.0, 0.0);

      // Test the complete workflow
      var plan1 = ImageOrientation.getPlan(rowVector, colVector);
      var plan2 = ImageOrientation.getPlan(rowVector, colVector, 0.8);

      var rowOrientation = ImageOrientation.getPatientOrientation(rowVector, 0.8, false);
      var colOrientation = ImageOrientation.getPatientOrientation(colVector, 0.8, false);
      var plan3 = ImageOrientation.getPlan(rowOrientation, colOrientation);

      // All methods should give same result for standard axial orientation
      assertEquals(ImageOrientation.Plan.TRANSVERSE, plan1);
      assertEquals(ImageOrientation.Plan.TRANSVERSE, plan2);
      assertEquals(ImageOrientation.Plan.TRANSVERSE, plan3);

      assertEquals(PatientOrientation.Biped.L, rowOrientation);
      assertEquals(PatientOrientation.Biped.P, colOrientation);
    }

    @Test
    void plan_determination_is_consistent_across_threshold_values() {
      var rowVector = new Vector3(0.9, 0.1, 0.1);
      var colVector = new Vector3(0.1, 0.9, 0.1);

      var plan1 = ImageOrientation.getPlan(rowVector, colVector, 0.5);
      var plan2 = ImageOrientation.getPlan(rowVector, colVector, 0.8);

      // Should be consistent as both vectors are clearly dominant in their axes
      assertEquals(plan1, plan2);
      assertEquals(ImageOrientation.Plan.TRANSVERSE, plan1);
    }
  }
}
