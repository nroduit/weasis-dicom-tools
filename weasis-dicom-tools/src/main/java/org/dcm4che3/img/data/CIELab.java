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
 * handling DICOM encoded L*a*b* values.
 *
 * @author Nicolas Roduit
 */
public class CIELab {
  // D65 white point constants (CIE L*a*b* standard white point)
  private static final double D65_WHITE_POINT_X = 0.950456;
  private static final double D65_WHITE_POINT_Y = 1.0;
  private static final double D65_WHITE_POINT_Z = 1.088754;

  // L*a*b* transformation constants
  private static final double LAB_THRESHOLD = 8.85645167903563082e-3;
  private static final double LAB_INVERSE_THRESHOLD = 0.206896551724137931;
  private static final double LAB_ALPHA = 841.0 / 108.0;
  private static final double LAB_BETA = 4.0 / 29.0;
  private static final double LAB_INVERSE_ALPHA = 108.0 / 841.0;

  // Gamma correction constants
  private static final double GAMMA_THRESHOLD = 0.0031306684425005883;
  private static final double GAMMA_INVERSE_THRESHOLD = 0.0404482362771076;
  private static final double GAMMA_LINEAR_COEFFICIENT = 12.92;
  private static final double GAMMA_POWER_COEFFICIENT = 1.055;
  private static final double GAMMA_POWER_OFFSET = 0.055;
  private static final double GAMMA_EXPONENT = 0.416666666666666667;
  private static final double GAMMA_INVERSE_EXPONENT = 2.4;

  // RGB to XYZ transformation matrix coefficients
  private static final double RGB_TO_XYZ_R_TO_X = 0.4123955889674142161;
  private static final double RGB_TO_XYZ_G_TO_X = 0.3575834307637148171;
  private static final double RGB_TO_XYZ_B_TO_X = 0.1804926473817015735;
  private static final double RGB_TO_XYZ_R_TO_Y = 0.2125862307855955516;
  private static final double RGB_TO_XYZ_G_TO_Y = 0.7151703037034108499;
  private static final double RGB_TO_XYZ_B_TO_Y = 0.07220049864333622685;
  private static final double RGB_TO_XYZ_R_TO_Z = 0.01929721549174694484;
  private static final double RGB_TO_XYZ_G_TO_Z = 0.1191838645808485318;
  private static final double RGB_TO_XYZ_B_TO_Z = 0.9504971251315797660;

  // XYZ to RGB transformation matrix coefficients
  private static final double XYZ_TO_RGB_X_TO_R = 3.2406;
  private static final double XYZ_TO_RGB_Y_TO_R = -1.5372;
  private static final double XYZ_TO_RGB_Z_TO_R = -0.4986;
  private static final double XYZ_TO_RGB_X_TO_G = -0.9689;
  private static final double XYZ_TO_RGB_Y_TO_G = 1.8758;
  private static final double XYZ_TO_RGB_Z_TO_G = 0.0415;
  private static final double XYZ_TO_RGB_X_TO_B = 0.0557;
  private static final double XYZ_TO_RGB_Y_TO_B = -0.2040;
  private static final double XYZ_TO_RGB_Z_TO_B = 1.0570;

  // DICOM encoding constants
  private static final double DICOM_L_SCALE = 65535.0 / 100.0;
  private static final double DICOM_AB_SCALE = 65535.0 / 255.0;
  private static final double DICOM_AB_OFFSET = 128;
  private static final double RGB_MAX_VALUE = 255.0;

  private CIELab() {}

  /**
   * Converts CIE L*a*b* values to RGB color space.
   *
   * @param l L* component (lightness)
   * @param a a* component (green-red)
   * @param b b* component (blue-yellow)
   * @return RGB values as double array [r, g, b] in range [0, 1]
   */
  private static double[] dicomLab2rgb(double l, double a, double b) {
    double[] xyz = labToXyz(l, a, b);
    double[] rgb = xyzToRgb(xyz[0], xyz[1], xyz[2]);
    rgb = adjustRgbRange(rgb);
    return new double[] {
      MathUtil.clamp(rgb[0], 0, 1), MathUtil.clamp(rgb[1], 0, 1), MathUtil.clamp(rgb[2], 0, 1)
    };
  }

  /** Converts L*a*b* values to XYZ color space. */
  private static double[] labToXyz(double l, double a, double b) {
    double cl = (l + 16) / 116;
    double ca = cl + a / 500;
    double cb = cl - b / 200;
    double x = D65_WHITE_POINT_X * labfInv(ca);
    double y = D65_WHITE_POINT_Y * labfInv(cl);
    double z = D65_WHITE_POINT_Z * labfInv(cb);

    return new double[] {x, y, z};
  }

  /** Converts XYZ values to RGB color space. */
  private static double[] xyzToRgb(double x, double y, double z) {
    double r = XYZ_TO_RGB_X_TO_R * x + XYZ_TO_RGB_Y_TO_R * y + XYZ_TO_RGB_Z_TO_R * z;
    double g = XYZ_TO_RGB_X_TO_G * x + XYZ_TO_RGB_Y_TO_G * y + XYZ_TO_RGB_Z_TO_G * z;
    double b = XYZ_TO_RGB_X_TO_B * x + XYZ_TO_RGB_Y_TO_B * y + XYZ_TO_RGB_Z_TO_B * z;

    return new double[] {r, g, b};
  }

  /** Adjusts RGB values to ensure they are in valid range and applies gamma correction. */
  private static double[] adjustRgbRange(double[] rgb) {
    double r = rgb[0], g = rgb[1], b = rgb[2];

    // Find minimum value and adjust if negative
    double min = Math.min(Math.min(r, g), b);
    if (min < 0) {
      r -= min;
      g -= min;
      b -= min;
    }

    return new double[] {gammaCorrection(r), gammaCorrection(g), gammaCorrection(b)};
  }

  /**
   * Converts RGB values to DICOM L*a*b* format.
   *
   * @param r red component in range [0, 1]
   * @param g green component in range [0, 1]
   * @param b blue component in range [0, 1]
   * @return L*a*b* values as double array [l, a, b]
   */
  private static double[] rgb2DicomLab(double r, double g, double b) {
    double[] xyz = rgbToXyz(r, g, b);
    return xyzToLab(xyz[0], xyz[1], xyz[2]);
  }

  /** Converts RGB values to XYZ color space. */
  private static double[] rgbToXyz(double r, double g, double b) {
    r = invGammaCorrection(r);
    g = invGammaCorrection(g);
    b = invGammaCorrection(b);
    double x = RGB_TO_XYZ_R_TO_X * r + RGB_TO_XYZ_G_TO_X * g + RGB_TO_XYZ_B_TO_X * b;
    double y = RGB_TO_XYZ_R_TO_Y * r + RGB_TO_XYZ_G_TO_Y * g + RGB_TO_XYZ_B_TO_Y * b;
    double z = RGB_TO_XYZ_R_TO_Z * r + RGB_TO_XYZ_G_TO_Z * g + RGB_TO_XYZ_B_TO_Z * b;

    return new double[] {x, y, z};
  }

  /** Converts XYZ values to L*a*b* color space. */
  private static double[] xyzToLab(double x, double y, double z) {
    // Normalize by white point
    x /= D65_WHITE_POINT_X;
    y /= D65_WHITE_POINT_Y;
    z /= D65_WHITE_POINT_Z;

    x = labf(x);
    y = labf(y);
    z = labf(z);

    double l = 116 * y - 16;
    double a = 500 * (x - y);
    double b = 200 * (y - z);

    return new double[] {l, a, b};
  }

  /** L*a*b* transformation function. */
  private static double labf(double n) {
    return n >= LAB_THRESHOLD ? Math.pow(n, 1.0 / 3.0) : LAB_ALPHA * n + LAB_BETA;
  }

  /** Inverse L*a*b* transformation function. */
  private static double labfInv(double n) {
    return n >= LAB_INVERSE_THRESHOLD ? n * n * n : LAB_INVERSE_ALPHA * (n - LAB_BETA);
  }

  /** Applies gamma correction to RGB component. */
  private static double gammaCorrection(double n) {
    return n <= GAMMA_THRESHOLD
        ? GAMMA_LINEAR_COEFFICIENT * n
        : GAMMA_POWER_COEFFICIENT * Math.pow(n, GAMMA_EXPONENT) - GAMMA_POWER_OFFSET;
  }

  /** Applies inverse gamma correction to RGB component. */
  private static double invGammaCorrection(double n) {
    return n <= GAMMA_INVERSE_THRESHOLD
        ? n / GAMMA_LINEAR_COEFFICIENT
        : Math.pow((n + GAMMA_POWER_OFFSET) / GAMMA_POWER_COEFFICIENT, GAMMA_INVERSE_EXPONENT);
  }

  /**
   * This method converts integer <a
   * href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.10.7.html#sect_C.10.7.1.1">DICOM
   * encoded L*a*b* values</a> to RGB values.
   *
   * @param lab integer array of 3 DICOM encoded L*a*b* values
   * @return int array of 3 RGB components (0-255), or empty array if input is invalid
   */
  public static int[] dicomLab2rgb(int[] lab) {
    if (lab == null || lab.length != 3) {
      return new int[0];
    }
    // Convert DICOM lab to normalized lab
    double l = (lab[0] * 100.0) / 65535.0;
    double a = (lab[1] * 255.0) / 65535.0 - DICOM_AB_OFFSET;
    double b = (lab[2] * 255.0) / 65535.0 - DICOM_AB_OFFSET;
    double[] rgb = dicomLab2rgb(l, a, b);
    return new int[] {
      (int) Math.round(rgb[0] * RGB_MAX_VALUE),
      (int) Math.round(rgb[1] * RGB_MAX_VALUE),
      (int) Math.round(rgb[2] * RGB_MAX_VALUE)
    };
  }

  /**
   * Converts rgb values to DICOM encoded L*a*b* values with D65 light point (CIELab standard white
   * point)
   *
   * @param c a color
   * @return integer <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.10.7.html#sect_C.10.7.1.1">DICOM
   *     encoded L*a*b* values</a>
   */
  public static int[] rgbToDicomLab(Color c) {
    Objects.requireNonNull(c, "Color cannot be null");
    return rgbToDicomLab(c.getRed(), c.getGreen(), c.getBlue());
  }

  /**
   * Converts rgb values to DICOM encoded L*a*b* values with D65 light point (CIELab standard white
   * point)
   *
   * @param r red (0 to 255)
   * @param g green (0 to 255)
   * @param b blue (0 to 255)
   * @return integer <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.10.7.html#sect_C.10.7.1.1">DICOM
   *     encoded L*a*b* values</a>
   */
  public static int[] rgbToDicomLab(int r, int g, int b) {
    double[] lab = rgb2DicomLab(r / RGB_MAX_VALUE, g / RGB_MAX_VALUE, b / RGB_MAX_VALUE);

    return new int[] {
      MathUtil.clamp((int) Math.round(lab[0] * DICOM_L_SCALE), 0, 65535),
      MathUtil.clamp((int) Math.round((lab[1] + DICOM_AB_OFFSET) * DICOM_AB_SCALE), 0, 65535),
      MathUtil.clamp((int) Math.round((lab[2] + DICOM_AB_OFFSET) * DICOM_AB_SCALE), 0, 65535)
    };
  }
}
