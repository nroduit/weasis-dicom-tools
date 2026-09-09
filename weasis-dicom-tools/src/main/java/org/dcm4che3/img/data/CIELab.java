/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.data;

import java.awt.Color;
import java.util.Objects;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.op.lut.colormap.InterpolationSpace;

/**
 * Utility class for converting between CIE L*a*b* color space and RGB color space, specifically
 * handling DICOM encoded L*a*b* values using the D65 white point standard.
 *
 * <p>This class provides methods to convert between:
 *
 * <ul>
 *   <li>DICOM encoded L*a*b* values to RGB color space
 *   <li>RGB color space to DICOM encoded L*a*b* values
 * </ul>
 *
 * @author Nicolas Roduit
 * @see <a
 *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.10.7.html#sect_C.10.7.1.1">DICOM
 *     L*a*b* Encoding</a>
 */
public final class CIELab {

  // DICOM encoding constants
  private static final double DICOM_L_SCALE = 65535.0 / 100.0;
  private static final double DICOM_AB_SCALE = 65535.0 / 255.0;
  private static final double DICOM_AB_OFFSET = 128.0;
  private static final double RGB_MAX_VALUE = 255.0;

  // RGB color bounds
  private static final int RGB_MIN = 0;
  private static final int DICOM_MAX = 65535;

  private CIELab() {
    // Utility class - prevent instantiation
  }

  /**
   * Converts DICOM encoded L*a*b* values to RGB color space.
   *
   * @param lab integer array of 3 DICOM encoded L*a*b* values, must not be null and length 3
   * @return RGB values as int array [r, g, b] in range [0, 255], or empty array if input is invalid
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.10.7.html#sect_C.10.7.1.1">DICOM
   *     L*a*b* Encoding</a>
   */
  public static int[] dicomLab2rgb(int[] lab) {
    if (lab == null || lab.length != 3) {
      return new int[0];
    }
    // Convert DICOM encoding to normalized L*a*b* values
    double l = lab[0] * 100.0 / 65535.0;
    double a = lab[1] * 255.0 / 65535.0 - DICOM_AB_OFFSET;
    double b = lab[2] * 255.0 / 65535.0 - DICOM_AB_OFFSET;

    double[] rgb = InterpolationSpace.labToRgb(l, a, b);
    return new int[] {
      (int) Math.round(MathUtil.clamp(rgb[0], 0.0, 1.0) * RGB_MAX_VALUE),
      (int) Math.round(MathUtil.clamp(rgb[1], 0.0, 1.0) * RGB_MAX_VALUE),
      (int) Math.round(MathUtil.clamp(rgb[2], 0.0, 1.0) * RGB_MAX_VALUE)
    };
  }

  /**
   * Converts RGB color to DICOM encoded L*a*b* values.
   *
   * @param color RGB color, must not be null
   * @return DICOM encoded L*a*b* values as int array [l, a, b]
   * @throws NullPointerException if color is null
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.10.7.html#sect_C.10.7.1.1">DICOM
   *     L*a*b* Encoding</a>
   */
  public static int[] rgbToDicomLab(Color color) {
    Objects.requireNonNull(color, "Color cannot be null");
    return rgbToDicomLab(color.getRed(), color.getGreen(), color.getBlue());
  }

  /**
   * Converts RGB values to DICOM encoded L*a*b* values.
   *
   * @param r red component (0-255)
   * @param g green component (0-255)
   * @param b blue component (0-255)
   * @return DICOM encoded L*a*b* values as int array [l, a, b]
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.10.7.html#sect_C.10.7.1.1">DICOM
   *     L*a*b* Encoding</a>
   */
  public static int[] rgbToDicomLab(int r, int g, int b) {
    double[] lab =
        InterpolationSpace.rgbToLab(r / RGB_MAX_VALUE, g / RGB_MAX_VALUE, b / RGB_MAX_VALUE);

    return new int[] {
      MathUtil.clamp((int) Math.round(lab[0] * DICOM_L_SCALE), RGB_MIN, DICOM_MAX),
      MathUtil.clamp(
          (int) Math.round((lab[1] + DICOM_AB_OFFSET) * DICOM_AB_SCALE), RGB_MIN, DICOM_MAX),
      MathUtil.clamp(
          (int) Math.round((lab[2] + DICOM_AB_OFFSET) * DICOM_AB_SCALE), RGB_MIN, DICOM_MAX)
    };
  }
}
