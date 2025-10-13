/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.geom;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.DefaultLocale;

@DisplayNameGeneration(ReplaceUnderscores.class)
class Vector3Test {

  // Test data using realistic DICOM coordinate values
  private static final Vector3 ORIGIN = new Vector3(0.0, 0.0, 0.0);
  private static final Vector3 UNIT_VECTOR = new Vector3(1.0, 0.0, 0.0);
  private static final Vector3 DICOM_ROW_VECTOR =
      new Vector3(1.0, 0.0, 0.0); // Typical DICOM row direction
  private static final Vector3 DICOM_COL_VECTOR =
      new Vector3(0.0, 1.0, 0.0); // Typical DICOM column direction
  private static final Vector3 ARBITRARY_VECTOR = new Vector3(3.0, 4.0, 5.0);
  private static final Vector3 NEGATIVE_VECTOR = new Vector3(-1.0, -2.0, -3.0);

  // DICOM Image Orientation Patient test data (common orientations)
  private static final double[] AXIAL_IOP = {1.0, 0.0, 0.0, 0.0, 1.0, 0.0};
  private static final double[] SAGITTAL_IOP = {0.0, 1.0, 0.0, 0.0, 0.0, -1.0};
  private static final double[] CORONAL_IOP = {1.0, 0.0, 0.0, 0.0, 0.0, -1.0};

  @Nested
  class Constructor_Tests {

    @Test
    void constructor_creates_vector_with_correct_coordinates() {
      var vector = new Vector3(1.5, 2.5, 3.5);

      assertEquals(1.5, vector.x());
      assertEquals(2.5, vector.y());
      assertEquals(3.5, vector.z());
    }

    @Test
    void constructor_handles_zero_values() {
      var vector = new Vector3(0.0, 0.0, 0.0);

      assertEquals(0.0, vector.x());
      assertEquals(0.0, vector.y());
      assertEquals(0.0, vector.z());
    }

    @Test
    void constructor_handles_negative_values() {
      var vector = new Vector3(-1.0, -2.0, -3.0);

      assertEquals(-1.0, vector.x());
      assertEquals(-2.0, vector.y());
      assertEquals(-3.0, vector.z());
    }
  }

  @Nested
  class Static_Factory_Methods {

    @Test
    void of_array_creates_vector_from_coordinates() {
      double[] coords = {1.0, 2.0, 3.0};

      var vector = Vector3.of(coords);

      assertEquals(1.0, vector.x());
      assertEquals(2.0, vector.y());
      assertEquals(3.0, vector.z());
    }

    @Test
    void of_array_with_offset_creates_vector_from_specified_position() {
      double[] coords = {0.0, 1.0, 2.0, 3.0, 4.0, 5.0};

      var vector = Vector3.of(coords, 3);

      assertEquals(3.0, vector.x());
      assertEquals(4.0, vector.y());
      assertEquals(5.0, vector.z());
    }

    @ParameterizedTest
    @MethodSource("provideDicomOrientationData")
    void of_array_handles_dicom_orientation_patient_arrays(
        double[] iop, Vector3 expectedRow, Vector3 expectedCol) {
      var rowVector = Vector3.of(iop, 0);
      var colVector = Vector3.of(iop, 3);

      assertEquals(expectedRow, rowVector);
      assertEquals(expectedCol, colVector);
    }

    @Test
    void of_array_throws_null_pointer_exception_for_null_array() {
      assertThrows(NullPointerException.class, () -> Vector3.of(null));
    }

    @Test
    void of_array_throws_illegal_argument_exception_for_insufficient_elements() {
      double[] shortArray = {1.0, 2.0};

      assertThrows(IllegalArgumentException.class, () -> Vector3.of(shortArray));
    }

    @Test
    void of_array_with_offset_throws_array_index_out_of_bounds_for_negative_offset() {
      double[] coords = {1.0, 2.0, 3.0};

      assertThrows(ArrayIndexOutOfBoundsException.class, () -> Vector3.of(coords, -1));
    }

    @Test
    void of_array_with_offset_throws_illegal_argument_exception_for_insufficient_elements() {
      double[] coords = {1.0, 2.0, 3.0, 4.0};

      assertThrows(IllegalArgumentException.class, () -> Vector3.of(coords, 2));
    }

    private static Stream<Arguments> provideDicomOrientationData() {
      return Stream.of(
          Arguments.of(AXIAL_IOP, new Vector3(1.0, 0.0, 0.0), new Vector3(0.0, 1.0, 0.0)),
          Arguments.of(SAGITTAL_IOP, new Vector3(0.0, 1.0, 0.0), new Vector3(0.0, 0.0, -1.0)),
          Arguments.of(CORONAL_IOP, new Vector3(1.0, 0.0, 0.0), new Vector3(0.0, 0.0, -1.0)));
    }
  }

  @Nested
  class Static_Constants {

    @Test
    void zero_vector_has_all_zero_components() {
      assertEquals(0.0, Vector3.ZERO.x());
      assertEquals(0.0, Vector3.ZERO.y());
      assertEquals(0.0, Vector3.ZERO.z());
    }

    @Test
    void unit_x_vector_points_along_x_axis() {
      assertEquals(1.0, Vector3.UNIT_X.x());
      assertEquals(0.0, Vector3.UNIT_X.y());
      assertEquals(0.0, Vector3.UNIT_X.z());
    }

    @Test
    void unit_y_vector_points_along_y_axis() {
      assertEquals(0.0, Vector3.UNIT_Y.x());
      assertEquals(1.0, Vector3.UNIT_Y.y());
      assertEquals(0.0, Vector3.UNIT_Y.z());
    }

    @Test
    void unit_z_vector_points_along_z_axis() {
      assertEquals(0.0, Vector3.UNIT_Z.x());
      assertEquals(0.0, Vector3.UNIT_Z.y());
      assertEquals(1.0, Vector3.UNIT_Z.z());
    }
  }

  @Nested
  class Magnitude_Operations {

    @Test
    void magnitude_calculates_euclidean_length() {
      var vector = new Vector3(3.0, 4.0, 0.0); // 3-4-5 triangle

      assertEquals(5.0, vector.magnitude(), 1e-10);
    }

    @Test
    void magnitude_squared_avoids_sqrt_calculation() {
      var vector = new Vector3(3.0, 4.0, 0.0);

      assertEquals(25.0, vector.magnitudeSquared(), 1e-10);
    }

    @Test
    void magnitude_of_zero_vector_is_zero() {
      assertEquals(0.0, Vector3.ZERO.magnitude());
    }

    @Test
    void magnitude_squared_of_zero_vector_is_zero() {
      assertEquals(0.0, Vector3.ZERO.magnitudeSquared());
    }

    @ParameterizedTest
    @CsvSource({
      "1.0, 0.0, 0.0, 1.0",
      "0.0, 1.0, 0.0, 1.0",
      "0.0, 0.0, 1.0, 1.0",
      "1.0, 1.0, 1.0, 1.732050808"
    })
    void magnitude_calculates_correctly_for_various_vectors(
        double x, double y, double z, double expectedMagnitude) {
      var vector = new Vector3(x, y, z);

      assertEquals(expectedMagnitude, vector.magnitude(), 1e-9);
    }
  }

  @Nested
  class Normalization {

    @Test
    void normalize_creates_unit_vector_in_same_direction() {
      var vector = new Vector3(3.0, 4.0, 0.0);
      var normalized = vector.normalize();

      assertEquals(1.0, normalized.magnitude(), 1e-10);
      assertEquals(0.6, normalized.x(), 1e-10);
      assertEquals(0.8, normalized.y(), 1e-10);
      assertEquals(0.0, normalized.z(), 1e-10);
    }

    @Test
    void normalize_of_zero_vector_returns_zero_vector() {
      var normalized = Vector3.ZERO.normalize();

      assertEquals(Vector3.ZERO, normalized);
    }

    @Test
    void normalize_of_unit_vector_returns_same_vector() {
      var normalized = Vector3.UNIT_X.normalize();

      assertEquals(Vector3.UNIT_X, normalized);
    }

    @Test
    void normalize_preserves_direction() {
      var original = new Vector3(2.0, 3.0, 6.0);
      var normalized = original.normalize();

      // Cross product should be zero (parallel vectors)
      var crossProduct = original.cross(normalized);
      assertEquals(0.0, crossProduct.magnitude(), 1e-10);
    }
  }

  @Nested
  class Dot_Product {

    @Test
    void dot_product_of_orthogonal_vectors_is_zero() {
      var result = Vector3.UNIT_X.dot(Vector3.UNIT_Y);

      assertEquals(0.0, result, 1e-10);
    }

    @Test
    void dot_product_of_parallel_unit_vectors_is_one() {
      var result = Vector3.UNIT_X.dot(Vector3.UNIT_X);

      assertEquals(1.0, result, 1e-10);
    }

    @Test
    void dot_product_of_anti_parallel_unit_vectors_is_negative_one() {
      var negativeUnitX = Vector3.UNIT_X.negate();
      var result = Vector3.UNIT_X.dot(negativeUnitX);

      assertEquals(-1.0, result, 1e-10);
    }

    @Test
    void dot_product_calculates_correctly() {
      var v1 = new Vector3(1.0, 2.0, 3.0);
      var v2 = new Vector3(4.0, 5.0, 6.0);
      var result = v1.dot(v2);

      assertEquals(32.0, result, 1e-10); // 1*4 + 2*5 + 3*6 = 32
    }

    @Test
    void dot_product_throws_null_pointer_exception_for_null_vector() {
      assertThrows(NullPointerException.class, () -> Vector3.UNIT_X.dot(null));
    }
  }

  @Nested
  class Cross_Product {

    @Test
    void cross_product_of_unit_x_and_unit_y_is_unit_z() {
      var result = Vector3.UNIT_X.cross(Vector3.UNIT_Y);

      assertEquals(Vector3.UNIT_Z, result);
    }

    @Test
    void cross_product_of_unit_y_and_unit_z_is_unit_x() {
      var result = Vector3.UNIT_Y.cross(Vector3.UNIT_Z);

      assertEquals(Vector3.UNIT_X, result);
    }

    @Test
    void cross_product_of_unit_z_and_unit_x_is_unit_y() {
      var result = Vector3.UNIT_Z.cross(Vector3.UNIT_X);

      assertEquals(Vector3.UNIT_Y, result);
    }

    @Test
    void cross_product_is_anti_commutative() {
      var v1 = new Vector3(1.0, 2.0, 3.0);
      var v2 = new Vector3(4.0, 5.0, 6.0);

      var cross1 = v1.cross(v2);
      var cross2 = v2.cross(v1);

      assertEquals(cross1.negate(), cross2);
    }

    @Test
    void cross_product_of_parallel_vectors_is_zero() {
      var v1 = new Vector3(1.0, 2.0, 3.0);
      var v2 = new Vector3(2.0, 4.0, 6.0); // v2 = 2 * v1

      var result = v1.cross(v2);

      assertEquals(0.0, result.magnitude(), 1e-10);
    }

    @Test
    void cross_product_throws_null_pointer_exception_for_null_vector() {
      assertThrows(NullPointerException.class, () -> Vector3.UNIT_X.cross(null));
    }
  }

  @Nested
  class Vector_Addition {

    @Test
    void add_combines_components_correctly() {
      var v1 = new Vector3(1.0, 2.0, 3.0);
      var v2 = new Vector3(4.0, 5.0, 6.0);

      var result = v1.add(v2);

      assertEquals(new Vector3(5.0, 7.0, 9.0), result);
    }

    @Test
    void add_zero_vector_returns_original_vector() {
      var original = new Vector3(1.0, 2.0, 3.0);

      var result = original.add(Vector3.ZERO);

      assertEquals(original, result);
    }

    @Test
    void add_is_commutative() {
      var v1 = ARBITRARY_VECTOR;
      var v2 = DICOM_ROW_VECTOR;

      assertEquals(v1.add(v2), v2.add(v1));
    }

    @Test
    void add_throws_null_pointer_exception_for_null_vector() {
      assertThrows(NullPointerException.class, () -> Vector3.UNIT_X.add(null));
    }
  }

  @Nested
  class Vector_Subtraction {

    @Test
    void subtract_calculates_difference_correctly() {
      var v1 = new Vector3(5.0, 7.0, 9.0);
      var v2 = new Vector3(1.0, 2.0, 3.0);

      var result = v1.subtract(v2);

      assertEquals(new Vector3(4.0, 5.0, 6.0), result);
    }

    @Test
    void subtract_zero_vector_returns_original_vector() {
      var original = new Vector3(1.0, 2.0, 3.0);

      var result = original.subtract(Vector3.ZERO);

      assertEquals(original, result);
    }

    @Test
    void subtract_vector_from_itself_returns_zero_vector() {
      var vector = ARBITRARY_VECTOR;

      var result = vector.subtract(vector);

      assertEquals(Vector3.ZERO, result);
    }

    @Test
    void subtract_throws_null_pointer_exception_for_null_vector() {
      assertThrows(NullPointerException.class, () -> Vector3.UNIT_X.subtract(null));
    }
  }

  @Nested
  class Scalar_Multiplication {

    @Test
    void multiply_scales_all_components() {
      var vector = new Vector3(1.0, 2.0, 3.0);

      var result = vector.multiply(3.0);

      assertEquals(new Vector3(3.0, 6.0, 9.0), result);
    }

    @Test
    void multiply_by_zero_returns_zero_vector() {
      var result = ARBITRARY_VECTOR.multiply(0.0);

      assertEquals(Vector3.ZERO, result);
    }

    @Test
    void multiply_by_one_returns_same_vector() {
      var result = ARBITRARY_VECTOR.multiply(1.0);

      assertEquals(ARBITRARY_VECTOR, result);
    }

    @Test
    void multiply_by_negative_one_returns_negated_vector() {
      var vector = new Vector3(1.0, -2.0, 3.0);

      var result = vector.multiply(-1.0);

      assertEquals(new Vector3(-1.0, 2.0, -3.0), result);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-2.5, -1.0, 0.0, 0.5, 1.0, 2.0, 10.0})
    void multiply_preserves_direction_for_positive_scalars(double scalar) {
      var original = ARBITRARY_VECTOR;
      var scaled = original.multiply(scalar);

      if (scalar > 0) {
        // Same direction
        assertTrue(original.dot(scaled) >= 0);
      } else if (scalar < 0) {
        // Opposite direction
        assertTrue(original.dot(scaled) <= 0);
      } else {
        // Zero vector
        assertEquals(Vector3.ZERO, scaled);
      }
    }
  }

  @Nested
  class Vector_Negation {

    @Test
    void negate_flips_all_components() {
      var vector = new Vector3(1.0, -2.0, 3.0);

      var result = vector.negate();

      assertEquals(new Vector3(-1.0, 2.0, -3.0), result);
    }

    @Test
    void negate_zero_vector_returns_zero_vector() {
      var result = Vector3.ZERO.negate();
      assertEquals(Vector3.ZERO, result);
    }

    @Test
    void constructor_normalizes_negative_zero() {
      var vector = new Vector3(-0.0, 0.0, -0.0);

      assertEquals(0.0, vector.x());
      assertEquals(0.0, vector.y());
      assertEquals(0.0, vector.z());

      // Verify it's actually positive zero, not negative zero
      assertEquals(0, Double.compare(1.0 / vector.x(), Double.POSITIVE_INFINITY));
    }

    @Test
    void vectors_with_positive_and_negative_zero_are_equal() {
      var v1 = new Vector3(0.0, 1.0, 2.0);
      var v2 = new Vector3(-0.0, 1.0, 2.0);

      assertEquals(v1, v2);
      assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void double_negation_returns_original_vector() {
      var original = ARBITRARY_VECTOR;

      var result = original.negate().negate();

      assertEquals(original, result);
    }
  }

  @Nested
  class Array_Conversion {

    @Test
    void to_array_returns_coordinate_array() {
      var vector = new Vector3(1.5, 2.5, 3.5);

      var array = vector.toArray();

      assertArrayEquals(new double[] {1.5, 2.5, 3.5}, array, 1e-10);
    }

    @Test
    void to_array_creates_independent_copy() {
      var vector = new Vector3(1.0, 2.0, 3.0);
      var array = vector.toArray();

      array[0] = 999.0; // Modify the array

      // Original vector should be unchanged
      assertEquals(1.0, vector.x());
    }

    @Test
    void to_array_handles_zero_vector() {
      var array = Vector3.ZERO.toArray();

      assertArrayEquals(new double[] {0.0, 0.0, 0.0}, array, 1e-10);
    }
  }

  @Nested
  class Tolerance_Based_Equality {

    @Test
    void equals_with_tolerance_accepts_vectors_within_tolerance() {
      var v1 = new Vector3(1.0, 2.0, 3.0);
      var v2 = new Vector3(1.0001, 1.9999, 3.0001);

      assertTrue(v1.equals(v2, 0.001));
    }

    @Test
    void equals_with_tolerance_rejects_vectors_outside_tolerance() {
      var v1 = new Vector3(1.0, 2.0, 3.0);
      var v2 = new Vector3(1.1, 2.0, 3.0);

      assertFalse(v1.equals(v2, 0.01));
    }

    @Test
    void equals_with_tolerance_handles_exact_boundary_values() {
      var v1 = new Vector3(1.0, 2.0, 3.0);
      var v2 = new Vector3(1.01, 2.0, 3.0);

      assertTrue(v1.equals(v2, 0.02)); // Exactly at boundary
      assertFalse(v1.equals(v2, 0.009)); // Just outside boundary
    }

    @Test
    void equals_with_zero_tolerance_requires_exact_match() {
      var v1 = new Vector3(1.0, 2.0, 3.0);
      var v2 = new Vector3(1.0, 2.0, 3.0);
      var v3 = new Vector3(1.000000001, 2.0, 3.0);

      assertTrue(v1.equals(v2, 0.0));
      assertFalse(v1.equals(v3, 0.0));
    }

    @Test
    void equals_with_tolerance_throws_illegal_argument_exception_for_negative_tolerance() {
      var v1 = Vector3.UNIT_X;
      var v2 = Vector3.UNIT_Y;

      assertThrows(IllegalArgumentException.class, () -> v1.equals(v2, -0.1));
    }

    @Test
    void equals_with_tolerance_throws_null_pointer_exception_for_null_vector() {
      assertThrows(NullPointerException.class, () -> Vector3.UNIT_X.equals(null, 0.1));
    }
  }

  @DefaultLocale(language = "en", country = "US")
  @Nested
  class Object_Methods {

    @Test
    void equals_returns_true_for_same_instance() {
      var vector = ARBITRARY_VECTOR;

      assertEquals(vector, vector);
    }

    @Test
    void equals_returns_true_for_vectors_with_same_coordinates() {
      var v1 = new Vector3(1.0, 2.0, 3.0);
      var v2 = new Vector3(1.0, 2.0, 3.0);

      assertEquals(v1, v2);
    }

    @Test
    void equals_returns_false_for_vectors_with_different_coordinates() {
      var v1 = new Vector3(1.0, 2.0, 3.0);
      var v2 = new Vector3(1.0, 2.0, 3.1);

      assertNotEquals(v1, v2);
    }

    @Test
    void equals_returns_false_for_null() {
      assertNotEquals(ARBITRARY_VECTOR, null);
    }

    @Test
    void equals_returns_false_for_different_types() {
      assertNotEquals(ARBITRARY_VECTOR, "not a vector");
    }

    @Test
    void hash_code_is_consistent() {
      var vector = new Vector3(1.0, 2.0, 3.0);

      assertEquals(vector.hashCode(), vector.hashCode());
    }

    @Test
    void hash_code_is_same_for_equal_vectors() {
      var v1 = new Vector3(1.0, 2.0, 3.0);
      var v2 = new Vector3(1.0, 2.0, 3.0);

      assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void to_string_contains_all_coordinates() {
      var vector = new Vector3(1.234567, 2.345678, 3.456789);
      var string = vector.toString();

      assertTrue(string.contains("1.234567"));
      assertTrue(string.contains("2.345678"));
      assertTrue(string.contains("3.456789"));
      assertTrue(string.startsWith("Vector3("));
    }

    @Test
    void to_string_formats_coordinates_with_precision() {
      var vector = new Vector3(1.0, 2.0, 3.0);
      var string = vector.toString();

      assertEquals("Vector3(1.000000, 2.000000, 3.000000)", string);
    }
  }

  @Nested
  class Record_Behavior {

    @Test
    void record_provides_component_accessors() {
      var vector = new Vector3(1.5, 2.5, 3.5);

      assertEquals(1.5, vector.x());
      assertEquals(2.5, vector.y());
      assertEquals(3.5, vector.z());
    }

    @Test
    void record_is_immutable() {
      var vector = new Vector3(1.0, 2.0, 3.0);

      // All operations return new instances
      assertNotSame(vector, vector.add(Vector3.UNIT_X));
      assertNotSame(vector, vector.multiply(2.0));
      assertNotSame(vector, vector.normalize());

      // Original vector is unchanged
      assertEquals(1.0, vector.x());
      assertEquals(2.0, vector.y());
      assertEquals(3.0, vector.z());
    }
  }

  @Nested
  class Dicom_Use_Cases {

    @Test
    void creates_orthogonal_coordinate_system_from_dicom_orientation() {
      // Typical DICOM axial image orientation
      var rowVector = Vector3.of(AXIAL_IOP, 0);
      var colVector = Vector3.of(AXIAL_IOP, 3);
      var normalVector = rowVector.cross(colVector);

      // Should form orthogonal basis
      assertEquals(0.0, rowVector.dot(colVector), 1e-10);
      assertEquals(0.0, rowVector.dot(normalVector), 1e-10);
      assertEquals(0.0, colVector.dot(normalVector), 1e-10);

      // Should be unit vectors
      assertEquals(1.0, rowVector.magnitude(), 1e-10);
      assertEquals(1.0, colVector.magnitude(), 1e-10);
      assertEquals(1.0, normalVector.magnitude(), 1e-10);
    }

    @Test
    void calculates_normal_vector_for_dicom_image_plane() {
      var rowVector = Vector3.of(SAGITTAL_IOP, 0);
      var colVector = Vector3.of(SAGITTAL_IOP, 3);

      var normal = rowVector.cross(colVector);

      // Normal should be perpendicular to both row and column vectors
      assertEquals(0.0, normal.dot(rowVector), 1e-10);
      assertEquals(0.0, normal.dot(colVector), 1e-10);
    }

    @Test
    void handles_patient_coordinate_transformations() {
      // Vector from origin to patient position
      var patientPosition = new Vector3(100.0, -50.0, 200.0); // Typical DICOM coordinates
      var translation = new Vector3(-10.0, 5.0, -15.0);

      var newPosition = patientPosition.add(translation);

      assertEquals(new Vector3(90.0, -45.0, 185.0), newPosition);
    }
  }
}
