/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.img.util.DicomAttributeUtils;
import org.dcm4che3.img.util.LookupTableUtils;
import org.dcm4che3.img.util.PaletteColorUtils;
import org.dcm4che3.img.util.PixelDataUtils;
import org.dcm4che3.img.util.RescaleUtils;
import org.weasis.core.util.Pair;
import org.weasis.core.util.annotations.Generated;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.LutParameters;
import org.weasis.opencv.op.lut.LutShape;

/**
 * Deprecated utility class for DICOM image operations.
 *
 * <p><strong>This class is deprecated and will be removed in a future version.</strong> All
 * functionality has been moved to specialized utility classes:
 *
 * <ul>
 *   <li>{@link DicomAttributeUtils} - for DICOM attribute operations
 *   <li>{@link PaletteColorUtils} - for palette color operations
 *   <li>{@link LookupTableUtils} - for lookup table operations
 *   <li>{@link PixelDataUtils} - for pixel data calculations
 *   <li>{@link RescaleUtils} - for rescale operations
 * </ul>
 *
 * <p>Please migrate your code to use the appropriate specialized utility classes instead of this
 * deprecated class.
 *
 * @author Benoit Jacquemoud, Nicolas Roduit
 * @deprecated Use specialized utility classes instead.
 */
@Deprecated(since = "5.34.0.3", forRemoval = true)
@Generated
public class DicomImageUtils {

  private DicomImageUtils() {
    // Prevent instantiation
  }

  // ======== Delegation methods for backward compatibility ========

  /**
   * @deprecated Use {@link DicomAttributeUtils#getModality(Attributes)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static String getModality(Attributes dcm) {
    return DicomAttributeUtils.getModality(dcm);
  }

  /**
   * @deprecated Use {@link RescaleUtils#createRescaleRampLut(LutParameters)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static LookupTableCV createRescaleRampLut(LutParameters params) {
    return RescaleUtils.createRescaleRampLut(params);
  }

  /**
   * @deprecated Use {@link RescaleUtils#createRescaleRampLut(double, double, int, boolean, boolean,
   *     int)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static LookupTableCV createRescaleRampLut(
      double intercept,
      double slope,
      int bitsStored,
      boolean isSigned,
      boolean outputSigned,
      int bitsOutput) {
    return RescaleUtils.createRescaleRampLut(
        intercept, slope, bitsStored, isSigned, outputSigned, bitsOutput);
  }

  /**
   * @deprecated Use {@link RescaleUtils#createRescaleRampLut(double, double, int, int, int,
   *     boolean, boolean, boolean, int)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static LookupTableCV createRescaleRampLut(
      double intercept,
      double slope,
      int minValue,
      int maxValue,
      int bitsStored,
      boolean isSigned,
      boolean inverse,
      boolean outputSigned,
      int bitsOutput) {
    return RescaleUtils.createRescaleRampLut(
        intercept,
        slope,
        minValue,
        maxValue,
        bitsStored,
        isSigned,
        inverse,
        outputSigned,
        bitsOutput);
  }

  /**
   * @deprecated Use {@link RescaleUtils#applyPixelPaddingToModalityLUT(LookupTableCV,
   *     LutParameters)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static void applyPixelPaddingToModalityLUT(
      LookupTableCV modalityLookup, LutParameters lutparams) {
    RescaleUtils.applyPixelPaddingToModalityLUT(modalityLookup, lutparams);
  }

  /**
   * @deprecated Use {@link RescaleUtils#pixel2rescale(LookupTableCV, double)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static double pixel2rescale(LookupTableCV lookup, double pixelValue) {
    return RescaleUtils.pixel2rescale(lookup, pixelValue);
  }

  /**
   * @deprecated Use {@link RescaleUtils#pixel2rescale(Attributes, double)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static double pixel2rescale(Attributes dcm, double pixelValue) {
    return RescaleUtils.pixel2rescale(dcm, pixelValue);
  }

  /**
   * @deprecated Use {@link DicomAttributeUtils#getByteData(Attributes, int)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static Optional<byte[]> getByteData(Attributes dicom, int tag) {
    return DicomAttributeUtils.getByteData(dicom, tag);
  }

  /**
   * @deprecated Use {@link DicomAttributeUtils#getByteData(Attributes, String, int)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static Optional<byte[]> getByteData(Attributes dicom, String privateCreator, int tag) {
    return DicomAttributeUtils.getByteData(dicom, privateCreator, tag);
  }

  /**
   * @deprecated Use {@link PaletteColorUtils#getPaletteColorLookupTable(Attributes)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static LookupTableCV getPaletteColorLookupTable(Attributes ds) {
    return PaletteColorUtils.getPaletteColorLookupTable(ds);
  }

  /**
   * @deprecated Use {@link PaletteColorUtils#getRGBImageFromPaletteColorModel(PlanarImage,
   *     LookupTableCV)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static PlanarImage getRGBImageFromPaletteColorModel(
      PlanarImage source, LookupTableCV lookup) {
    return PaletteColorUtils.getRGBImageFromPaletteColorModel(source, lookup);
  }

  /**
   * @deprecated Use {@link PaletteColorUtils#getRGBImageFromPaletteColorModel(PlanarImage,
   *     Attributes)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static PlanarImage getRGBImageFromPaletteColorModel(PlanarImage source, Attributes ds) {
    return PaletteColorUtils.getRGBImageFromPaletteColorModel(source, ds);
  }

  /**
   * @deprecated Use {@link LookupTableUtils#createLut(Attributes)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static Optional<LookupTableCV> createLut(Attributes dicomLutObject) {
    return LookupTableUtils.createLut(dicomLutObject);
  }

  /**
   * @deprecated Use {@link LookupTableUtils#createVoiLut(LutShape, double, double, int, int, int,
   *     boolean, boolean)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static LookupTableCV createVoiLut(
      LutShape lutShape,
      double level,
      double width,
      int bitsStored,
      int bitsOutput,
      int bitsAllocated,
      boolean isSigned,
      boolean outputSigned) {
    return LookupTableUtils.createVoiLut(
        lutShape, level, width, bitsStored, bitsOutput, bitsAllocated, isSigned, outputSigned);
  }

  /**
   * @deprecated Use {@link LookupTableUtils#lutDescriptor(Attributes, int)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static int[] lutDescriptor(Attributes ds, int tag) {
    return LookupTableUtils.lutDescriptor(ds, tag);
  }

  /**
   * @deprecated Use {@link LookupTableUtils#lutData(Attributes, int[], int, int)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static byte[] lutData(Attributes ds, int[] desc, int tag, int segmentedTag) {
    return LookupTableUtils.lutData(ds, desc, tag, segmentedTag);
  }

  /**
   * @deprecated Use {@link PixelDataUtils#getMinMax(int, boolean)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static Pair<Double, Double> getMinMax(int bitsStored, boolean signed) {
    return PixelDataUtils.getMinMax(bitsStored, signed);
  }

  /**
   * @deprecated Use {@link PixelDataUtils#bgr2rgb(PlanarImage)} instead
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static PlanarImage bgr2rgb(PlanarImage img) {
    return PixelDataUtils.bgr2rgb(img);
  }
}
