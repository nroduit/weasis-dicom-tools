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
  public static final Color BLUE = new Color(44783);

  /** Color for sagittal orientations (anterior/posterior, ventral/dorsal) */
  public static final Color RED = new Color(15539236);

  /** Color for longitudinal orientations (head/foot, cranial/caudal) */
  public static final Color GREEN = new Color(897355);

  // Deprecated color constants for backward compatibility
  /**
   * @deprecated Use {@link #BLUE} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static final Color blue = BLUE;

  /**
   * @deprecated Use {@link #RED} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static final Color red = RED;

  /**
   * @deprecated Use {@link #GREEN} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static final Color green = GREEN;

  private PatientOrientation() {
    // Utility class
  }

  /** Human anatomical orientations following standard medical terminology. */
  public enum Biped implements Orientation {
    R("Right", BLUE),
    L("Left", BLUE),
    A("Anterior", RED),
    P("Posterior", RED),
    F("Foot", GREEN),
    H("Head", GREEN);

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
     * Parses a string to a Biped orientation.
     *
     * @param value the string to parse
     * @return the corresponding Biped orientation, or null if not found
     */
    public static Biped fromString(String value) {
      if (!StringUtil.hasText(value)) {
        return null;
      }
      try {
        return valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        LOGGER.debug("Unknown biped orientation: {}", value);
        return null;
      }
    }
  }

  /** Animal anatomical orientations following veterinary terminology. */
  public enum Quadruped implements Orientation {
    RT("Right", BLUE),
    LE("Left", BLUE),
    V("Ventral", RED),
    D("Dorsal", RED),
    CD("Caudal", GREEN),
    CR("Cranial", GREEN);

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
     * Parses a string to a Quadruped orientation.
     *
     * @param value the string to parse
     * @return the corresponding Quadruped orientation, or null if not found
     */
    public static Quadruped fromString(String value) {
      if (!StringUtil.hasText(value)) {
        return null;
      }
      try {
        return valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        LOGGER.debug("Unknown quadruped orientation: {}", value);
        return null;
      }
    }
  }

  // Axis orientation methods

  /**
   * Gets X-axis orientation for biped.
   *
   * @param vector the direction cosine vector
   * @return Right for negative X, Left for positive X
   * @throws NullPointerException if vector is null
   */
  public static Biped getBipedXOrientation(Vector3 vector) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    return vector.x() < 0 ? Biped.R : Biped.L;
  }

  /**
   * Gets Y-axis orientation for biped.
   *
   * @param vector the direction cosine vector
   * @return Anterior for negative Y, Posterior for positive Y
   * @throws NullPointerException if vector is null
   */
  public static Biped getBipedYOrientation(Vector3 vector) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    return vector.y() < 0 ? Biped.A : Biped.P;
  }

  /**
   * Gets Z-axis orientation for biped.
   *
   * @param vector the direction cosine vector
   * @return Foot for negative Z, Head for positive Z
   * @throws NullPointerException if vector is null
   */
  public static Biped getBipedZOrientation(Vector3 vector) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    return vector.z() < 0 ? Biped.F : Biped.H;
  }

  /**
   * Gets X-axis orientation for quadruped.
   *
   * @param vector the direction cosine vector
   * @return Right for negative X, Left for positive X
   * @throws NullPointerException if vector is null
   */
  public static Quadruped getQuadrupedXOrientation(Vector3 vector) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    return vector.x() < 0 ? Quadruped.RT : Quadruped.LE;
  }

  /**
   * Gets Y-axis orientation for quadruped.
   *
   * @param vector the direction cosine vector
   * @return Ventral for negative Y, Dorsal for positive Y
   * @throws NullPointerException if vector is null
   */
  public static Quadruped getQuadrupedYOrientation(Vector3 vector) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    return vector.y() < 0 ? Quadruped.V : Quadruped.D;
  }

  /**
   * Gets Z-axis orientation for quadruped.
   *
   * @param vector the direction cosine vector
   * @return Caudal for negative Z, Cranial for positive Z
   * @throws NullPointerException if vector is null
   */
  public static Quadruped getQuadrupedZOrientation(Vector3 vector) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    return vector.z() < 0 ? Quadruped.CD : Quadruped.CR;
  }

  // Generic axis orientation methods

  /**
   * Gets axis orientation for any patient type.
   *
   * @param vector the direction cosine vector
   * @param axis the axis (X, Y, or Z)
   * @param quadruped true for quadruped, false for biped
   * @return the appropriate orientation
   * @throws NullPointerException if vector or axis is null
   * @throws IllegalArgumentException if axis is not X, Y, or Z
   */
  public static Orientation getAxisOrientation(Vector3 vector, Axis axis, boolean quadruped) {
    Objects.requireNonNull(vector, "Vector cannot be null");
    Objects.requireNonNull(axis, "Axis cannot be null");

    return switch (axis) {
      case X -> quadruped ? getQuadrupedXOrientation(vector) : getBipedXOrientation(vector);
      case Y -> quadruped ? getQuadrupedYOrientation(vector) : getBipedYOrientation(vector);
      case Z -> quadruped ? getQuadrupedZOrientation(vector) : getBipedZOrientation(vector);
    };
  }

  // Opposite orientation methods

  /**
   * Returns the anatomically opposite biped orientation.
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

  /** Coordinate axes for anatomical orientation. */
  public enum Axis {
    X,
    Y,
    Z
  }
}
