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

import java.util.Objects;

/**
 * Immutable 3D vector representation for DICOM geometric operations.
 *
 * <p>This class represents a 3-dimensional vector commonly used in DICOM for direction cosines,
 * image orientation vectors, and spatial coordinates. All operations preserve immutability.
 *
 * <p>Usage examples:
 *
 * <pre>{@code
 * // Create from coordinates
 * Vector3 v1 = new Vector3(1.0, 0.0, 0.0);
 *
 * // Create from DICOM Image Orientation Patient array
 * double[] iop = {1.0, 0.0, 0.0, 0.0, 1.0, 0.0};
 * Vector3 rowVector = new Vector3(iop, 0);    // First 3 elements
 * Vector3 colVector = new Vector3(iop, 3);    // Last 3 elements
 * }</pre>
 *
 * @author Nicolas Roduit
 * @since 1.0
 */
public final class Vector3 {

  /** X component of the vector */
  public final double x;

  /** Y component of the vector */
  public final double y;

  /** Z component of the vector */
  public final double z;

  /** Zero vector (0, 0, 0) */
  public static final Vector3 ZERO = new Vector3(0.0, 0.0, 0.0);

  /** Unit vector along X axis (1, 0, 0) */
  public static final Vector3 UNIT_X = new Vector3(1.0, 0.0, 0.0);

  /** Unit vector along Y axis (0, 1, 0) */
  public static final Vector3 UNIT_Y = new Vector3(0.0, 1.0, 0.0);

  /** Unit vector along Z axis (0, 0, 1) */
  public static final Vector3 UNIT_Z = new Vector3(0.0, 0.0, 1.0);

  /**
   * Creates a 3D vector with the specified coordinates.
   *
   * @param x the X coordinate
   * @param y the Y coordinate
   * @param z the Z coordinate
   */
  public Vector3(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /**
   * Creates a 3D vector from a coordinate array.
   *
   * <p>This constructor is commonly used with DICOM Image Orientation Patient arrays which contain
   * 6 values representing two 3D direction vectors.
   *
   * @param coords array containing at least 3 coordinate values
   * @throws NullPointerException if coords is null
   * @throws IllegalArgumentException if coords has fewer than 3 elements
   */
  public Vector3(double[] coords) {
    this(coords, 0);
  }

  /**
   * Creates a 3D vector from a coordinate array starting at the specified offset.
   *
   * <p>Useful for extracting vectors from DICOM Image Orientation Patient arrays:
   *
   * <ul>
   *   <li>Row vector: {@code new Vector3(iop, 0)} (elements 0, 1, 2)
   *   <li>Column vector: {@code new Vector3(iop, 3)} (elements 3, 4, 5)
   * </ul>
   *
   * @param coords array containing coordinate values
   * @param offset starting index in the array (0-based)
   * @throws NullPointerException if coords is null
   * @throws IllegalArgumentException if coords has insufficient elements from offset
   * @throws ArrayIndexOutOfBoundsException if offset is invalid
   */
  public Vector3(double[] coords, int offset) {
    Objects.requireNonNull(coords, "Coordinate array cannot be null");
    validateArrayBounds(coords, offset);

    this.x = coords[offset];
    this.y = coords[offset + 1];
    this.z = coords[offset + 2];
  }

  /**
   * Returns the magnitude (length) of this vector.
   *
   * @return the Euclidean length of the vector
   */
  public double magnitude() {
    return Math.sqrt(x * x + y * y + z * z);
  }

  /**
   * Returns a normalized version of this vector (unit vector in same direction).
   *
   * @return a new Vector3 with magnitude 1.0, or zero vector if this vector has zero magnitude
   */
  public Vector3 normalize() {
    double mag = magnitude();
    return isZero(mag) ? ZERO : new Vector3(x / mag, y / mag, z / mag);
  }

  /**
   * Calculates the dot product with another vector.
   *
   * @param other the other vector
   * @return the dot product of this vector and the other vector
   * @throws NullPointerException if other is null
   */
  public double dot(Vector3 other) {
    Objects.requireNonNull(other, "Other vector cannot be null");
    return x * other.x + y * other.y + z * other.z;
  }

  /**
   * Calculates the cross product with another vector.
   *
   * @param other the other vector
   * @return a new Vector3 representing the cross product
   * @throws NullPointerException if other is null
   */
  public Vector3 cross(Vector3 other) {
    Objects.requireNonNull(other, "Other vector cannot be null");
    return new Vector3(
        y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
  }

  /**
   * Adds another vector to this vector.
   *
   * @param other the vector to add
   * @return a new Vector3 representing the sum
   * @throws NullPointerException if other is null
   */
  public Vector3 add(Vector3 other) {
    Objects.requireNonNull(other, "Other vector cannot be null");
    return new Vector3(x + other.x, y + other.y, z + other.z);
  }

  /**
   * Subtracts another vector from this vector.
   *
   * @param other the vector to subtract
   * @return a new Vector3 representing the difference
   * @throws NullPointerException if other is null
   */
  public Vector3 subtract(Vector3 other) {
    Objects.requireNonNull(other, "Other vector cannot be null");
    return new Vector3(x - other.x, y - other.y, z - other.z);
  }

  /**
   * Multiplies this vector by a scalar value.
   *
   * @param scalar the scalar multiplier
   * @return a new Vector3 scaled by the scalar value
   */
  public Vector3 multiply(double scalar) {
    return new Vector3(x * scalar, y * scalar, z * scalar);
  }

  /**
   * Returns the negation of this vector.
   *
   * @return a new Vector3 with all components negated
   */
  public Vector3 negate() {
    return new Vector3(-x, -y, -z);
  }

  /**
   * Converts this vector to a coordinate array.
   *
   * @return a new array containing [x, y, z]
   */
  public double[] toArray() {
    return new double[] {x, y, z};
  }

  /**
   * Checks if this vector is approximately equal to another vector within a tolerance.
   *
   * @param other the other vector to compare
   * @param tolerance the maximum allowed difference for each component
   * @return true if all components are within tolerance, false otherwise
   * @throws NullPointerException if other is null
   * @throws IllegalArgumentException if tolerance is negative
   */
  public boolean equals(Vector3 other, double tolerance) {
    Objects.requireNonNull(other, "Other vector cannot be null");
    if (tolerance < 0.0) {
      throw new IllegalArgumentException("Tolerance cannot be negative: " + tolerance);
    }

    return Math.abs(x - other.x) <= tolerance
        && Math.abs(y - other.y) <= tolerance
        && Math.abs(z - other.z) <= tolerance;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    Vector3 vector3 = (Vector3) obj;
    return Double.compare(vector3.x, x) == 0
        && Double.compare(vector3.y, y) == 0
        && Double.compare(vector3.z, z) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(x, y, z);
  }

  @Override
  public String toString() {
    return String.format("Vector3(%.6f, %.6f, %.6f)", x, y, z);
  }

  private static void validateArrayBounds(double[] coords, int offset) {
    if (offset < 0) {
      throw new ArrayIndexOutOfBoundsException("Offset cannot be negative: " + offset);
    }
    if (coords.length < offset + 3) {
      throw new IllegalArgumentException(
          String.format(
              "Array must contain at least %d elements, but has %d", offset + 3, coords.length));
    }
  }

  private static boolean isZero(double value) {
    return Math.abs(value) < 1e-10;
  }
}
