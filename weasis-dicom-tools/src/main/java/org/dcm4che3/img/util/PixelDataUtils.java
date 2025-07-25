/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import org.opencv.imgproc.Imgproc;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.Pair;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Utility class for pixel data calculations and transformations.
 *
 * <p>This class provides comprehensive methods for pixel value range calculations, color space
 * conversions, bit depth operations, and other pixel-related transformations commonly used in DICOM
 * image processing.
 *
 * <p>All methods are thread-safe and handle edge cases such as null inputs and invalid parameters.
 *
 * @author Nicolas Roduit
 */
public final class PixelDataUtils {

  /** Maximum supported bit depth for pixel data */
  private static final int MAX_BITS_STORED = 16;

  /** Minimum supported bit depth for pixel data */
  private static final int MIN_BITS_STORED = 1;

  private PixelDataUtils() {
    // Prevent instantiation
  }

  /**
   * Calculates the theoretical minimum and maximum pixel values for given bit depth and signedness.
   *
   * <p>This method computes the value range based on the number of bits stored and whether the data
   * is signed or unsigned. The bit depth is automatically clamped to the supported range of 1-16
   * bits.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>8-bit unsigned: [0, 255]
   *   <li>8-bit signed: [-128, 127]
   *   <li>16-bit unsigned: [0, 65535]
   *   <li>16-bit signed: [-32768, 32767]
   * </ul>
   *
   * @param bitsStored the number of bits used to store pixel values (clamped to 1-16)
   * @param signed true if the pixel data is signed, false for unsigned
   * @return a Pair containing the minimum and maximum values as doubles
   */
  public static Pair<Double, Double> getMinMax(int bitsStored, boolean signed) {
    int clampedBits = MathUtil.clamp(bitsStored, MIN_BITS_STORED, MAX_BITS_STORED);
    if (signed) {
      return calculateSignedRange(clampedBits);
    } else {
      return calculateUnsignedRange(clampedBits);
    }
  }

  /**
   * Converts a BGR image to RGB color space.
   *
   * <p>This method performs color space conversion from BGR (Blue-Green-Red) to RGB
   * (Red-Green-Blue) channel ordering using OpenCV's optimized conversion routines. Single-channel
   * (grayscale) images are returned unchanged for performance reasons.
   *
   * @param img the input image in BGR format
   * @return a new image in RGB format, or the original image if it's single-channel or null
   */
  public static PlanarImage bgr2rgb(PlanarImage img) {
    if (!requiresColorConversion(img)) {
      return img;
    }

    return performBgrToRgbConversion(img);
  }

  /**
   * Converts an RGB image to BGR color space.
   *
   * <p>This method performs color space conversion from RGB (Red-Green-Blue) to BGR
   * (Blue-Green-Red) channel ordering. Single-channel images are returned unchanged.
   *
   * @param img the input image in RGB format
   * @return a new image in BGR format, or the original image if it's single-channel or null
   */
  public static PlanarImage rgb2bgr(PlanarImage img) {
    if (!requiresColorConversion(img)) {
      return img;
    }
    return performRgbToBgrConversion(img);
  }

  //  ======= Private helper methods =======

  private static Pair<Double, Double> calculateSignedRange(int bitsStored) {
    double minValue = -(1L << (bitsStored - 1));
    double maxValue = (1L << (bitsStored - 1)) - 1;
    return new Pair<>(minValue, maxValue);
  }

  private static Pair<Double, Double> calculateUnsignedRange(int bitsStored) {
    double minValue = 0.0;
    double maxValue = (1L << bitsStored) - 1;
    return new Pair<>(minValue, maxValue);
  }

  private static boolean requiresColorConversion(PlanarImage img) {
    return img != null && img.channels() > 1;
  }

  private static PlanarImage performBgrToRgbConversion(PlanarImage img) {
    var dstImg = new ImageCV();
    Imgproc.cvtColor(img.toMat(), dstImg, Imgproc.COLOR_BGR2RGB);
    return dstImg;
  }

  private static PlanarImage performRgbToBgrConversion(PlanarImage img) {
    var dstImg = new ImageCV();
    Imgproc.cvtColor(img.toMat(), dstImg, Imgproc.COLOR_RGB2BGR);
    return dstImg;
  }
}
