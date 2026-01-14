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

  // D65 white point constants (CIE L*a*b* standard illuminant)
  private static final double D65_WHITE_POINT_X = 0.950456;
  private static final double D65_WHITE_POINT_Y = 1.0;
  private static final double D65_WHITE_POINT_Z = 1.088754;

  // L*a*b* transformation constants
  private static final double LAB_THRESHOLD = 8.85645167903563082e-3;
  private static final double LAB_INVERSE_THRESHOLD = 0.206896551724137931;
  private static final double LAB_ALPHA = 841.0 / 108.0;
  private static final double LAB_BETA = 4.0 / 29.0;
  private static final double LAB_INVERSE_ALPHA = 108.0 / 841.0;

  // sRGB gamma correction constants
  private static final double GAMMA_THRESHOLD = 0.0031306684425005883;
  private static final double GAMMA_INVERSE_THRESHOLD = 0.0404482362771076;
  private static final double GAMMA_LINEAR_COEFFICIENT = 12.92;
  private static final double GAMMA_POWER_COEFFICIENT = 1.055;
  private static final double GAMMA_POWER_OFFSET = 0.055;
  private static final double GAMMA_EXPONENT = 1.0 / 2.4;
  private static final double GAMMA_INVERSE_EXPONENT = 2.4;

  // sRGB to XYZ transformation matrix (D65 illuminant)
  private static final double[][] RGB_TO_XYZ_MATRIX = {
    {0.4123955889674142161, 0.3575834307637148171, 0.1804926473817015735},
    {0.2125862307855955516, 0.7151703037034108499, 0.07220049864333622685},
    {0.01929721549174694484, 0.1191838645808485318, 0.9504971251315797660}
  };

  // XYZ to sRGB transformation matrix (inverse of RGB_TO_XYZ_MATRIX)
  private static final double[][] XYZ_TO_RGB_MATRIX = {
    {3.2406, -1.5372, -0.4986},
    {-0.9689, 1.8758, 0.0415},
    {0.0557, -0.2040, 1.0570}
  };

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

    double[] rgb = labToRgb(l, a, b);
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
    double[] lab = rgbToLab(r / RGB_MAX_VALUE, g / RGB_MAX_VALUE, b / RGB_MAX_VALUE);

    return new int[] {
      MathUtil.clamp((int) Math.round(lab[0] * DICOM_L_SCALE), RGB_MIN, DICOM_MAX),
      MathUtil.clamp(
          (int) Math.round((lab[1] + DICOM_AB_OFFSET) * DICOM_AB_SCALE), RGB_MIN, DICOM_MAX),
      MathUtil.clamp(
          (int) Math.round((lab[2] + DICOM_AB_OFFSET) * DICOM_AB_SCALE), RGB_MIN, DICOM_MAX)
    };
  }

  private static double[] labToRgb(double l, double a, double b) {
    double[] xyz = labToXyz(l, a, b);
    return xyzToRgb(xyz);
  }

  private static double[] rgbToLab(double r, double g, double b) {
    double[] xyz = rgbToXyz(new double[] {r, g, b});
    return xyzToLab(xyz);
  }

  private static double[] labToXyz(double l, double a, double b) {
    double fy = (l + 16.0) / 116.0;
    double fx = fy + a / 500.0;
    double fz = fy - b / 200.0;

    return new double[] {
      D65_WHITE_POINT_X * labfInverse(fx),
      D65_WHITE_POINT_Y * labfInverse(fy),
      D65_WHITE_POINT_Z * labfInverse(fz)
    };
  }

  private static double[] xyzToLab(double[] xyz) {
    // Normalize by D65 white point
    double x = labf(xyz[0] / D65_WHITE_POINT_X);
    double y = labf(xyz[1] / D65_WHITE_POINT_Y);
    double z = labf(xyz[2] / D65_WHITE_POINT_Z);

    return new double[] {
      116.0 * y - 16.0, // L*
      500.0 * (x - y), // a*
      200.0 * (y - z) // b*
    };
  }

  private static double[] xyzToRgb(double[] xyz) {
    double[] rgb = matrixMultiply(XYZ_TO_RGB_MATRIX, xyz);

    // Handle out-of-gamut colors by shifting minimum to zero
    double min = Math.min(Math.min(rgb[0], rgb[1]), rgb[2]);
    if (min < 0) {
      rgb[0] -= min;
      rgb[1] -= min;
      rgb[2] -= min;
    }

    // Apply gamma correction
    return new double[] {gammaCorrection(rgb[0]), gammaCorrection(rgb[1]), gammaCorrection(rgb[2])};
  }

  private static double[] rgbToXyz(double[] rgb) {
    // Apply inverse gamma correction
    double[] linearRgb = {
      inverseGammaCorrection(rgb[0]), inverseGammaCorrection(rgb[1]), inverseGammaCorrection(rgb[2])
    };

    return matrixMultiply(RGB_TO_XYZ_MATRIX, linearRgb);
  }

  private static double[] matrixMultiply(double[][] matrix, double[] vector) {
    return new double[] {
      matrix[0][0] * vector[0] + matrix[0][1] * vector[1] + matrix[0][2] * vector[2],
      matrix[1][0] * vector[0] + matrix[1][1] * vector[1] + matrix[1][2] * vector[2],
      matrix[2][0] * vector[0] + matrix[2][1] * vector[1] + matrix[2][2] * vector[2]
    };
  }

  private static double labf(double t) {
    return t > LAB_THRESHOLD ? Math.pow(t, 1.0 / 3.0) : LAB_ALPHA * t + LAB_BETA;
  }

  private static double labfInverse(double t) {
    return t > LAB_INVERSE_THRESHOLD ? t * t * t : LAB_INVERSE_ALPHA * (t - LAB_BETA);
  }

  private static double gammaCorrection(double value) {
    return value <= GAMMA_THRESHOLD
        ? GAMMA_LINEAR_COEFFICIENT * value
        : GAMMA_POWER_COEFFICIENT * Math.pow(value, GAMMA_EXPONENT) - GAMMA_POWER_OFFSET;
  }

  private static double inverseGammaCorrection(double value) {
    return value <= GAMMA_INVERSE_THRESHOLD
        ? value / GAMMA_LINEAR_COEFFICIENT
        : Math.pow((value + GAMMA_POWER_OFFSET) / GAMMA_POWER_COEFFICIENT, GAMMA_INVERSE_EXPONENT);
  }
}
