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

import java.awt.Color;
import java.util.Objects;

/**
 * Utility class for determining image orientation planes from DICOM direction cosines.
 *
 * <p>This class provides methods to analyze Image Orientation Patient (IOP) vectors and determine
 * the corresponding anatomical plane (axial, coronal, sagittal, or oblique).
 *
 * <p>Reference: <a
 * href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.2.html#sect_C.7.6.2.1.1">
 * DICOM Part 3: Image Position and Image Orientation (Patient)</a>
 *
 * @author Nicolas Roduit
 */
public final class ImageOrientation {

  /** Anatomical orientation planes based on DICOM Image Orientation Patient. */
  public enum Plan {
    /** Unknown or unspecified orientation */
    UNKNOWN("Unknown"),
    /** Transverse/Axial plane (horizontal cross-sections) */
    TRANSVERSE("Axial"),
    /** Sagittal plane (left-right divisions) */
    SAGITTAL("Sagittal"),
    /** Coronal plane (front-back divisions) */
    CORONAL("Coronal"),
    /** Oblique plane (not aligned with standard anatomical planes) */
    OBLIQUE("Oblique");

    private final String displayName;

    Plan(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Returns the human-readable name of the anatomical plane.
     *
     * @return the display name of the plane
     */
    @Override
    public String toString() {
      return displayName;
    }
  }

  /** Default threshold for determining if a vector is aligned with a major axis */
  public static final double DEFAULT_OBLIQUITY_THRESHOLD = 0.8;

  private ImageOrientation() {
    // Prevent instantiation
  }

  /**
   * Determines the anatomical plane from row and column direction cosines using default threshold.
   *
   * @param rowVector the row direction vector (first 3 values of ImageOrientationPatient)
   * @param columnVector the column direction vector (last 3 values of ImageOrientationPatient)
   * @return the anatomical plane, or {@link Plan#OBLIQUE} if not aligned with standard planes
   * @throws NullPointerException if either vector is null
   */
  public static Plan getPlan(Vector3 rowVector, Vector3 columnVector) {
    return getPlan(rowVector, columnVector, DEFAULT_OBLIQUITY_THRESHOLD);
  }

  /**
   * Determines the anatomical plane from row and column direction cosines.
   *
   * @param rowVector the row direction vector (first 3 values of ImageOrientationPatient)
   * @param columnVector the column direction vector (last 3 values of ImageOrientationPatient)
   * @param threshold minimum cosine value to consider a vector aligned with a major axis (0.0-1.0)
   * @return the anatomical plane, or {@link Plan#OBLIQUE} if not aligned with standard planes
   * @throws NullPointerException if either vector is null
   * @throws IllegalArgumentException if threshold is not between 0.0 and 1.0
   */
  public static Plan getPlan(Vector3 rowVector, Vector3 columnVector, double threshold) {
    Objects.requireNonNull(rowVector, "Row vector cannot be null");
    Objects.requireNonNull(columnVector, "Column vector cannot be null");
    validateThreshold(threshold);

    Orientation rowAxis = getPatientOrientation(rowVector, threshold, false);
    Orientation colAxis = getPatientOrientation(columnVector, threshold, false);
    return determinePlanFromOrientations(rowAxis, colAxis);
  }

  /**
   * Determines the anatomical plane from row and column orientations.
   *
   * @param rowOrientation the row axis orientation
   * @param columnOrientation the column axis orientation
   * @return the anatomical plane, or {@link Plan#OBLIQUE} if orientations are null or don't match
   *     standard planes
   */
  public static Plan getPlan(Orientation rowOrientation, Orientation columnOrientation) {
    return determinePlanFromOrientations(rowOrientation, columnOrientation);
  }

  private static void validateThreshold(double threshold) {
    if (threshold < 0.0 || threshold > 1.0) {
      throw new IllegalArgumentException(
          "Threshold must be between 0.0 and 1.0, got: " + threshold);
    }
  }

  /**
   * Determines the patient orientation based on a direction cosine vector.
   *
   * @param vector the direction cosine vector (ImageOrientationPatient)
   * @param minCosine minimum cosine value to consider a vector aligned with a major axis
   * @param quadruped true if the patient is a quadruped, false for biped
   * @return the patient orientation, or null if no dominant axis found
   */
  public static Orientation getPatientOrientation(
      Vector3 vector, double minCosine, boolean quadruped) {
    double absX = Math.abs(vector.x);
    double absY = Math.abs(vector.y);
    double absZ = Math.abs(vector.z);

    if (isDominantAxis(absX, absY, absZ, minCosine)) {
      return getXAxisOrientation(vector, quadruped);
    }
    if (isDominantAxis(absY, absX, absZ, minCosine)) {
      return getYAxisOrientation(vector, quadruped);
    }
    if (isDominantAxis(absZ, absX, absY, minCosine)) {
      return getZAxisOrientation(vector, quadruped);
    }
    return null;
  }

  private static boolean isDominantAxis(
      double primary, double secondary1, double secondary2, double threshold) {
    return primary > threshold && primary > secondary1 && primary > secondary2;
  }

  /**
   * Determines the X-axis orientation based on a direction cosine vector.
   *
   * @param vector the direction cosine vector (ImageOrientationPatient)
   * @param quadruped true if the patient is a quadruped, false for biped
   * @return the X-axis orientation (Biped or Quadruped)
   */
  public static Orientation getXAxisOrientation(Vector3 vector, boolean quadruped) {
    return quadruped
        ? PatientOrientation.getQuadrupedXOrientation(vector)
        : PatientOrientation.getBipedXOrientation(vector);
  }

  /**
   * Determines the Y-axis orientation based on a direction cosine vector.
   *
   * @param vector the direction cosine vector (ImageOrientationPatient)
   * @param quadruped true if the patient is a quadruped, false for biped
   * @return the Y-axis orientation (Biped or Quadruped)
   */
  public static Orientation getYAxisOrientation(Vector3 vector, boolean quadruped) {
    return quadruped
        ? PatientOrientation.getQuadrupedYOrientation(vector)
        : PatientOrientation.getBipedYOrientation(vector);
  }

  /**
   * Determines the Z-axis orientation based on a direction cosine vector.
   *
   * @param vector the direction cosine vector (ImageOrientationPatient)
   * @param quadruped true if the patient is a quadruped, false for biped
   * @return the Z-axis orientation (Biped or Quadruped)
   */
  public static Orientation getZAxisOrientation(Vector3 vector, boolean quadruped) {
    return quadruped
        ? PatientOrientation.getQuadrupedZOrientation(vector)
        : PatientOrientation.getBipedZOrientation(vector);
  }

  private static Plan determinePlanFromOrientations(Orientation rowAxis, Orientation colAxis) {
    if (rowAxis == null || colAxis == null) {
      return Plan.OBLIQUE;
    }

    Color rowColor = rowAxis.getColor();
    Color colColor = colAxis.getColor();

    // Check for transverse plane (blue-red or red-blue combination)
    if (isColorCombination(rowColor, colColor, PatientOrientation.blue, PatientOrientation.red)) {
      return Plan.TRANSVERSE;
    }
    // Check for coronal plane (blue-green or green-blue combination)
    if (isColorCombination(rowColor, colColor, PatientOrientation.blue, PatientOrientation.green)) {
      return Plan.CORONAL;
    }
    // Check for sagittal plane (red-green or green-red combination)
    if (isColorCombination(rowColor, colColor, PatientOrientation.red, PatientOrientation.green)) {
      return Plan.SAGITTAL;
    }
    return Plan.OBLIQUE;
  }

  private static boolean isColorCombination(
      Color rowColor, Color colColor, Color color1, Color color2) {
    return (rowColor.equals(color1) && colColor.equals(color2))
        || (rowColor.equals(color2) && colColor.equals(color1));
  }
}
