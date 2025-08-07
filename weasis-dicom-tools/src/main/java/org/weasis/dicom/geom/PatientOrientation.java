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

import java.awt.Color;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 * Patient orientation utilities for DICOM imaging.
 *
 * <p>This class provides anatomical orientation mappings for both biped (human) and quadruped
 * (animal) patients, following DICOM standards for Image Position and Image Orientation (Patient).
 *
 * <p>The orientations use standard anatomical terminology:
 *
 * <ul>
 *   <li><b>Biped orientations</b>: Right/Left, Anterior/Posterior, Head/Foot
 *   <li><b>Quadruped orientations</b>: Right/Left, Ventral/Dorsal, Cranial/Caudal
 * </ul>
 *
 * <p>Color coding follows anatomical conventions:
 *
 * <ul>
 *   <li><b>Blue</b> - Lateral axis (left/right orientations)
 *   <li><b>Red</b> - Sagittal axis (anterior/posterior or ventral/dorsal)
 *   <li><b>Green</b> - Longitudinal axis (head/foot or cranial/caudal)
 * </ul>
 *
 * @author Nicolas Roduit
 * @see <a
 *     href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.2.html#sect_C.7.6.2.1.1">
 *     DICOM Part 3: Image Position and Image Orientation (Patient)</a>
 */
public final class PatientOrientation {
  private static final Logger LOGGER = LoggerFactory.getLogger(PatientOrientation.class);

  /** Color for lateral orientations (left/right) */
  public static final Color blue = new Color(44783);

  /** Color for sagittal orientations (anterior/posterior, ventral/dorsal) */
  public static final Color red = new Color(15539236);

  /** Color for longitudinal orientations (head/foot, cranial/caudal) */
  public static final Color green = new Color(897355);

  private PatientOrientation() {
    // Utility class - prevent instantiation
  }

  /**
   * Human anatomical orientations following standard medical terminology.
   *
   * <p>These orientations represent the standard anatomical position for humans:
   *
   * <ul>
   *   <li><b>R/L</b> - Patient's right/left (lateral axis)
   *   <li><b>A/P</b> - Anterior/posterior (front/back, sagittal axis)
   *   <li><b>H/F</b> - Head/foot (superior/inferior, longitudinal axis)
   * </ul>
   */
  public enum Biped implements Orientation {
    /** Patient's right side */
    R("Right", blue),
    /** Patient's left side */
    L("Left", blue),
    /** Anterior (front) aspect */
    A("Anterior", red),
    /** Posterior (back) aspect */
    P("Posterior", red),
    /** Foot (inferior) direction */
    F("Foot", green),
    /** Head (superior) direction */
    H("Head", green);

    private final String fullName;
    private final Color color;

    Biped(String fullName, Color color) {
      this.fullName = fullName;
      this.color = color;
    }

    @Override
    public String getFullName() {
      return fullName;
    }

    @Override
    public String toString() {
      return fullName;
    }

    @Override
    public Color getColor() {
      return color;
    }

    /**
     * Parses a string value to a Biped orientation.
     *
     * @param value the string value to parse (e.g., "R", "L", "A", "P", "F", "H")
     * @return the corresponding Biped orientation, or null if not found
     */
    public static Biped fromString(String value) {
      if (!StringUtil.hasText(value)) {
        return null;
      }
      try {
        return Biped.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        LOGGER.debug("Unknown biped orientation: {}", value);
        return null;
      }
    }
  }

  /**
   * Animal anatomical orientations following veterinary terminology.
   *
   * <p>These orientations represent the standard anatomical position for quadrupeds:
   *
   * <ul>
   *   <li><b>RT/LE</b> - Right/left (lateral axis)
   *   <li><b>V/D</b> - Ventral/dorsal (belly/back, sagittal axis)
   *   <li><b>CR/CD</b> - Cranial/caudal (head/tail, longitudinal axis)
   * </ul>
   */
  public enum Quadruped implements Orientation {
    /** Right side */
    RT("Right", blue),
    /** Left side */
    LE("Left", blue),
    /** Ventral (belly) aspect */
    V("Ventral", red),
    /** Dorsal (back) aspect */
    D("Dorsal", red),
    /** Caudal (tail) direction */
    CD("Caudal", green),
    /** Cranial (head) direction */
    CR("Cranial", green);

    private final String fullName;
    private final Color color;

    Quadruped(String fullName, Color color) {
      this.fullName = fullName;
      this.color = color;
    }

    @Override
    public String getFullName() {
      return fullName;
    }

    @Override
    public String toString() {
      return fullName;
    }

    @Override
    public Color getColor() {
      return color;
    }

    /**
     * Parses a string value to a Quadruped orientation.
     *
     * @param value the string value to parse (e.g., "RT", "LE", "V", "D", "CR", "CD")
     * @return the corresponding Quadruped orientation, or null if not found
     */
    public static Quadruped fromString(String value) {
      if (!StringUtil.hasText(value)) {
        return null;
      }
      try {
        return Quadruped.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        LOGGER.debug("Unknown quadruped orientation: {}", value);
        return null;
      }
    }
  }

  // Biped orientation determination methods

  /**
   * Determines the biped X-axis orientation from a direction vector.
   *
   * @param vector the direction cosine vector
   * @return the X-axis biped orientation (Right for negative X, Left for positive X)
   * @throws NullPointerException if vector is null
   */
  public static Biped getBipedXOrientation(Vector3 vector) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    return vector.x < 0 ? Biped.R : Biped.L;
  }

  /**
   * Determines the biped Y-axis orientation from a direction vector.
   *
   * @param vector the direction cosine vector
   * @return the Y-axis biped orientation (Anterior for negative Y, Posterior for positive Y)
   * @throws NullPointerException if vector is null
   */
  public static Biped getBipedYOrientation(Vector3 vector) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    return vector.y < 0 ? Biped.A : Biped.P;
  }

  /**
   * Determines the biped Z-axis orientation from a direction vector.
   *
   * @param vector the direction cosine vector
   * @return the Z-axis biped orientation (Foot for negative Z, Head for positive Z)
   * @throws NullPointerException if vector is null
   */
  public static Biped getBipedZOrientation(Vector3 vector) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    return vector.z < 0 ? Biped.F : Biped.H;
  }

  // Quadruped orientation determination methods

  /**
   * Determines the quadruped X-axis orientation from a direction vector.
   *
   * @param vector the direction cosine vector
   * @return the X-axis quadruped orientation (Right for negative X, Left for positive X)
   * @throws NullPointerException if vector is null
   */
  public static Quadruped getQuadrupedXOrientation(Vector3 vector) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    return vector.x < 0 ? Quadruped.RT : Quadruped.LE;
  }

  /**
   * Determines the quadruped Y-axis orientation from a direction vector.
   *
   * @param vector the direction cosine vector
   * @return the Y-axis quadruped orientation (Ventral for negative Y, Dorsal for positive Y)
   * @throws NullPointerException if vector is null
   */
  public static Quadruped getQuadrupedYOrientation(Vector3 vector) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    return vector.y < 0 ? Quadruped.V : Quadruped.D;
  }

  /**
   * Determines the quadruped Z-axis orientation from a direction vector.
   *
   * @param vector the direction cosine vector
   * @return the Z-axis quadruped orientation (Caudal for negative Z, Cranial for positive Z)
   * @throws NullPointerException if vector is null
   */
  public static Quadruped getQuadrupedZOrientation(Vector3 vector) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    return vector.z < 0 ? Quadruped.CD : Quadruped.CR;
  }

  // Opposite orientation methods

  /**
   * Returns the anatomically opposite biped orientation.
   *
   * <p>Opposite pairs:
   *
   * <ul>
   *   <li>Right ↔ Left
   *   <li>Anterior ↔ Posterior
   *   <li>Head ↔ Foot
   * </ul>
   *
   * @param orientation the biped orientation
   * @return the opposite biped orientation
   * @throws NullPointerException if orientation is null
   */
  public static Biped getOppositeOrientation(Biped orientation) {
    Objects.requireNonNull(orientation, "Orientation cannot be null");

    return switch (orientation) {
      case R -> Biped.L;
      case L -> Biped.R;
      case A -> Biped.P;
      case P -> Biped.A;
      case F -> Biped.H;
      case H -> Biped.F;
    };
  }

  /**
   * Returns the anatomically opposite quadruped orientation.
   *
   * <p>Opposite pairs:
   *
   * <ul>
   *   <li>Right ↔ Left
   *   <li>Ventral ↔ Dorsal
   *   <li>Cranial ↔ Caudal
   * </ul>
   *
   * @param orientation the quadruped orientation
   * @return the opposite quadruped orientation
   * @throws NullPointerException if orientation is null
   */
  public static Quadruped getOppositeOrientation(Quadruped orientation) {
    Objects.requireNonNull(orientation, "Orientation cannot be null");

    return switch (orientation) {
      case RT -> Quadruped.LE;
      case LE -> Quadruped.RT;
      case V -> Quadruped.D;
      case D -> Quadruped.V;
      case CD -> Quadruped.CR;
      case CR -> Quadruped.CD;
    };
  }
}
