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
 * <p>This class analyzes Image Orientation Patient (IOP) vectors to determine the corresponding
 * anatomical plane (axial, coronal, sagittal, or oblique).
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
    UNKNOWN("Unknown"),
    TRANSVERSE("Axial"),
    SAGITTAL("Sagittal"),
    CORONAL("Coronal"),
    OBLIQUE("Oblique");

    private final String displayName;

    Plan(String displayName) {
      this.displayName = displayName;
    }

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
   * Determines the anatomical plane using default threshold.
   *
   * @param rowVector the row direction vector
   * @param columnVector the column direction vector
   * @return the anatomical plane
   * @throws NullPointerException if either vector is null
   */
  public static Plan getPlan(Vector3 rowVector, Vector3 columnVector) {
    return getPlan(rowVector, columnVector, DEFAULT_OBLIQUITY_THRESHOLD);
  }

  /**
   * Determines the anatomical plane from direction cosines.
   *
   * @param rowVector the row direction vector
   * @param columnVector the column direction vector
   * @param threshold minimum cosine value to consider aligned (0.0-1.0)
   * @return the anatomical plane
   * @throws NullPointerException if either vector is null
   * @throws IllegalArgumentException if threshold is not between 0.0 and 1.0
   */
  public static Plan getPlan(Vector3 rowVector, Vector3 columnVector, double threshold) {
    Objects.requireNonNull(rowVector, "Row vector cannot be null");
    Objects.requireNonNull(columnVector, "Column vector cannot be null");
    validateThreshold(threshold);

    var rowAxis = getPatientOrientation(rowVector, threshold, false);
    var colAxis = getPatientOrientation(columnVector, threshold, false);
    return determinePlanFromOrientations(rowAxis, colAxis);
  }

  /**
   * Determines the anatomical plane from orientations.
   *
   * @param rowOrientation the row axis orientation
   * @param columnOrientation the column axis orientation
   * @return the anatomical plane
   */
  public static Plan getPlan(Orientation rowOrientation, Orientation columnOrientation) {
    return determinePlanFromOrientations(rowOrientation, columnOrientation);
  }

  /**
   * Determines patient orientation from direction cosine vector.
   *
   * @param vector the direction cosine vector
   * @param minCosine minimum cosine value for axis alignment
   * @param quadruped true for quadruped, false for biped
   * @return the patient orientation, or null if no dominant axis
   */
  public static Orientation getPatientOrientation(
      Vector3 vector, double minCosine, boolean quadruped) {
    double absX = Math.abs(vector.x());
    double absY = Math.abs(vector.y());
    double absZ = Math.abs(vector.z());

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

  /**
   * Gets X-axis orientation.
   *
   * @param vector the direction cosine vector
   * @param quadruped true for quadruped, false for biped
   * @return the X-axis orientation
   */
  public static Orientation getXAxisOrientation(Vector3 vector, boolean quadruped) {
    return quadruped
        ? PatientOrientation.getQuadrupedXOrientation(vector)
        : PatientOrientation.getBipedXOrientation(vector);
  }

  /**
   * Gets Y-axis orientation.
   *
   * @param vector the direction cosine vector
   * @param quadruped true for quadruped, false for biped
   * @return the Y-axis orientation
   */
  public static Orientation getYAxisOrientation(Vector3 vector, boolean quadruped) {
    return quadruped
        ? PatientOrientation.getQuadrupedYOrientation(vector)
        : PatientOrientation.getBipedYOrientation(vector);
  }

  /**
   * Gets Z-axis orientation.
   *
   * @param vector the direction cosine vector
   * @param quadruped true for quadruped, false for biped
   * @return the Z-axis orientation
   */
  public static Orientation getZAxisOrientation(Vector3 vector, boolean quadruped) {
    return quadruped
        ? PatientOrientation.getQuadrupedZOrientation(vector)
        : PatientOrientation.getBipedZOrientation(vector);
  }

  // Private helper methods

  private static void validateThreshold(double threshold) {
    if (threshold < 0.0 || threshold > 1.0 || Double.isNaN(threshold)) {
      throw new IllegalArgumentException(
          "Threshold must be between 0.0 and 1.0, got: " + threshold);
    }
  }

  private static boolean isDominantAxis(
      double primary, double secondary1, double secondary2, double threshold) {
    return primary > threshold && primary > secondary1 && primary > secondary2;
  }

  private static Plan determinePlanFromOrientations(Orientation rowAxis, Orientation colAxis) {
    if (rowAxis == null || colAxis == null) {
      return Plan.OBLIQUE;
    }

    return switch (getColorPairType(rowAxis.getColor(), colAxis.getColor())) {
      case BLUE_RED -> Plan.TRANSVERSE;
      case BLUE_GREEN -> Plan.CORONAL;
      case RED_GREEN -> Plan.SAGITTAL;
      case UNKNOWN -> Plan.OBLIQUE;
    };
  }

  private static ColorPairType getColorPairType(Color rowColor, Color colColor) {
    if (isColorCombination(rowColor, colColor, PatientOrientation.BLUE, PatientOrientation.RED)) {
      return ColorPairType.BLUE_RED;
    }
    if (isColorCombination(rowColor, colColor, PatientOrientation.BLUE, PatientOrientation.GREEN)) {
      return ColorPairType.BLUE_GREEN;
    }
    if (isColorCombination(rowColor, colColor, PatientOrientation.RED, PatientOrientation.GREEN)) {
      return ColorPairType.RED_GREEN;
    }
    return ColorPairType.UNKNOWN;
  }

  private static boolean isColorCombination(
      Color rowColor, Color colColor, Color color1, Color color2) {
    return (rowColor.equals(color1) && colColor.equals(color2))
        || (rowColor.equals(color2) && colColor.equals(color1));
  }

  private enum ColorPairType {
    BLUE_RED,
    BLUE_GREEN,
    RED_GREEN,
    UNKNOWN
  }
}
