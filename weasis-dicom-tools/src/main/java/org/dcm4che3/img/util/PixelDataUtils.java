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

import org.dcm4che3.data.VR;
import org.opencv.core.CvType;
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
    return signed ? calculateSignedRange(clampedBits) : calculateUnsignedRange(clampedBits);
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
    return requiresColorConversion(img) ? performColorConversion(img, Imgproc.COLOR_BGR2RGB) : img;
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
    return requiresColorConversion(img) ? performColorConversion(img, Imgproc.COLOR_RGB2BGR) : img;
  }

  /**
   * Maps OpenCV CvType depth to appropriate DICOM VR for pixel data storage.
   *
   * <p>This method determines the correct DICOM Value Representation (VR) based on the OpenCV
   * CvType depth of the pixel data. Unsupported types default to VR.UN (Unknown).
   *
   * @param cvType the OpenCV CvType depth (e.g., CvType.CV_8U, CvType.CV_16S)
   * @return the corresponding DICOM VR for pixel data storage
   */
  public static VR getAppropriateVR(int cvType) {
    int depth = CvType.depth(cvType);
    return switch (depth) {
      case CvType.CV_8U, CvType.CV_8S -> VR.OB; // Other Byte (8-bit)
      case CvType.CV_16U, CvType.CV_16S -> VR.OW; // Other Word (16-bit)
      case CvType.CV_32S -> VR.OL; // Other Long (32-bit)
      case CvType.CV_32F -> VR.OF; // Other Float (32-bit float)
      case CvType.CV_64F -> VR.OD; // Other Double (64-bit double)
      default -> VR.UN; // Unknown VR for unsupported types
    };
  }

  /**
   * Retrieves the number of bits allocated per pixel from the given DICOM VR.
   *
   * <p>This method maps DICOM Value Representations (VR) to their corresponding bit depths for
   * pixel data storage. Unsupported VRs return 0.
   *
   * @param vr the DICOM Value Representation
   * @return the number of bits allocated per pixel, or 0 for unsupported VRs
   */
  public static int getBitsAllocatedFromVR(VR vr) {
    return switch (vr) {
      case OB -> 8;
      case OW -> 16;
      case OL, OF -> 32;
      case OD -> 64;
      default -> 0;
    };
  }

  /**
   * Retrieves the number of bits allocated per pixel from the given OpenCV CvType depth.
   *
   * <p>This method maps OpenCV CvType depths to their corresponding bit depths for pixel data
   * storage. Unsupported types return 0.
   *
   * @param cvType the OpenCV CvType depth
   * @return the number of bits allocated per pixel, or 0 for unsupported types
   */
  public static int getBitsAllocatedFromCvType(int cvType) {
    int depth = CvType.depth(cvType);
    return switch (depth) {
      case CvType.CV_8U, CvType.CV_8S -> 8;
      case CvType.CV_16U, CvType.CV_16S -> 16;
      case CvType.CV_32S, CvType.CV_32F -> 32;
      case CvType.CV_64F -> 64;
      default -> 0;
    };
  }

  public static boolean isSignedCvType(int cvType) {
    int depth = CvType.depth(cvType);
    return depth == CvType.CV_8S || depth >= CvType.CV_16S;
  }

  //  ======= Private helper methods =======

  private static Pair<Double, Double> calculateSignedRange(int bitsStored) {
    double minValue = -(1L << (bitsStored - 1));
    double maxValue = (1L << (bitsStored - 1)) - 1.0;
    return new Pair<>(minValue, maxValue);
  }

  private static Pair<Double, Double> calculateUnsignedRange(int bitsStored) {
    return new Pair<>(0.0, (1L << bitsStored) - 1.0);
  }

  private static boolean requiresColorConversion(PlanarImage img) {
    return img != null && img.channels() > 1;
  }

  /** Performs color space conversion using the specified OpenCV color code */
  private static PlanarImage performColorConversion(PlanarImage img, int colorConversionCode) {
    var dstImg = new ImageCV();
    Imgproc.cvtColor(img.toMat(), dstImg, colorConversionCode);
    return dstImg;
  }
}
