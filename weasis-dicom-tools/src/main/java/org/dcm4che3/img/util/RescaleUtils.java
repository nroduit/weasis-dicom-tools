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

import java.awt.image.DataBuffer;
import java.util.Arrays;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.LutParameters;

/**
 * Utility class for DICOM rescale operations and pixel value transformations.
 *
 * <p>This class provides methods for creating rescale lookup tables, applying pixel padding, and
 * performing pixel-to-rescale value conversions according to DICOM standard specifications.
 *
 * @author Nicolas Roduit
 */
public final class RescaleUtils {

  private RescaleUtils() {
    // Prevent instantiation
  }

  /**
   * Creates a rescale ramp lookup table from LUT parameters.
   *
   * @param params the LUT parameters containing rescale slope, intercept, and bit information
   * @return a LookupTableCV with full range of possible input entries according to bitStored
   */
  public static LookupTableCV createRescaleRampLut(LutParameters params) {
    return createRescaleRampLut(
        params.intercept(),
        params.slope(),
        params.bitsStored(),
        params.signed(),
        params.outputSigned(),
        params.bitsOutput());
  }

  /**
   * Creates a rescale ramp lookup table with specified parameters.
   *
   * @param intercept the rescale intercept value
   * @param slope the rescale slope value
   * @param bitsStored the number of bits stored in the pixel data
   * @param isSigned true if the pixel data is signed
   * @param outputSigned true if the output should be signed
   * @param bitsOutput the number of bits for output
   * @return a LookupTableCV for the specified rescale transformation
   */
  public static LookupTableCV createRescaleRampLut(
      double intercept,
      double slope,
      int bitsStored,
      boolean isSigned,
      boolean outputSigned,
      int bitsOutput) {

    return createRescaleRampLut(
        intercept,
        slope,
        Integer.MIN_VALUE,
        Integer.MAX_VALUE,
        bitsStored,
        isSigned,
        false,
        outputSigned,
        bitsOutput);
  }

  /**
   * Creates a rescale ramp lookup table with full parameter control.
   *
   * @param intercept the rescale intercept value
   * @param slope the rescale slope value
   * @param minValue the minimum input value to consider
   * @param maxValue the maximum input value to consider
   * @param bitsStored the number of bits stored in the pixel data
   * @param isSigned true if the input pixel data is signed
   * @param inverse true if the LUT should be inverted
   * @param outputSigned true if the output should be signed
   * @param bitsOutput the number of bits for output
   * @return a LookupTableCV for the specified rescale transformation
   */
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

    var bitConfig = calculateBitConfiguration(bitsStored, bitsOutput, isSigned, outputSigned);
    var inputRange = calculateInputRange(minValue, maxValue, bitConfig);

    Object lutData = createLutArray(inputRange, bitConfig, intercept, slope, inverse);

    return createLookupTable(lutData, inputRange.min(), outputSigned);
  }

  /**
   * Applies pixel padding to a modality lookup table.
   *
   * <p>This method modifies the lookup table to handle pixel padding values according to DICOM
   * standard specifications.
   *
   * @param modalityLookup the modality lookup table to modify
   * @param params the LUT parameters containing padding information
   * @see <a
   *     href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.5.html#sect_C.7.5.1.1.2">
   *     Pixel Padding Value and Pixel Padding Range Limit</a>
   */
  public static void applyPixelPaddingToModalityLUT(
      LookupTableCV modalityLookup, LutParameters params) {
    if (!isPaddingApplicable(modalityLookup, params)) {
      return;
    }

    var paddingRange = calculatePaddingRange(params);
    var paddingInfo = calculatePaddingInfo(paddingRange, modalityLookup);

    if (!paddingInfo.isValid()) {
      return;
    }

    applyPaddingToLookupData(modalityLookup, paddingInfo, params.inversePaddingMLUT());
  }

  /**
   * Converts a pixel value to rescaled value using a lookup table.
   *
   * @param lookup the lookup table to use for conversion
   * @param pixelValue the pixel value to convert
   * @return the rescaled value, or the original pixel value if lookup is null or value is out of
   *     range
   */
  public static double pixel2rescale(LookupTableCV lookup, double pixelValue) {
    if (lookup != null && isValueInRange(lookup, pixelValue)) {
      return lookup.lookup(0, (int) pixelValue);
    }
    return pixelValue;
  }

  /**
   * Converts a pixel value to rescaled value using DICOM rescale slope and intercept.
   *
   * <p>Applies the DICOM rescale transformation: value = pixelValue * slope + intercept
   *
   * @param dcm the DICOM attributes containing rescale slope and intercept
   * @param pixelValue the pixel value to convert
   * @return the rescaled value using the formula: pixelValue * slope + intercept
   */
  public static double pixel2rescale(Attributes dcm, double pixelValue) {
    if (dcm == null) {
      return pixelValue;
    }

    var rescaleParams = extractRescaleParameters(dcm);
    return rescaleParams.hasRescaleParameters()
        ? pixelValue * rescaleParams.slope() + rescaleParams.intercept()
        : pixelValue;
  }

  // ======= Private helper methods =======

  private static BitConfiguration calculateBitConfiguration(
      int bitsStored, int bitsOutput, boolean isSigned, boolean outputSigned) {
    int stored = MathUtil.clamp(bitsStored, 1, 16);
    int bitsOutLut = bitsOutput <= 8 ? 8 : 16;
    int outRangeSize = (1 << bitsOutLut) - 1;
    int maxOutValue = outputSigned ? (1 << (bitsOutLut - 1)) - 1 : outRangeSize;
    int minOutValue = outputSigned ? -(maxOutValue + 1) : 0;

    return new BitConfiguration(stored, bitsOutLut, minOutValue, maxOutValue, isSigned);
  }

  private static InputRange calculateInputRange(
      int minValue, int maxValue, BitConfiguration config) {
    int minInValue = config.isSigned() ? -(1 << (config.stored() - 1)) : 0;
    int maxInValue =
        config.isSigned() ? (1 << (config.stored() - 1)) - 1 : (1 << config.stored()) - 1;

    minInValue = Math.max(minInValue, Math.min(maxValue, minValue));
    maxInValue = Math.min(maxInValue, Math.max(maxValue, minValue));

    return new InputRange(minInValue, maxInValue);
  }

  private static Object createLutArray(
      InputRange inputRange,
      BitConfiguration bitConfig,
      double intercept,
      double slope,
      boolean inverse) {
    int numEntries = inputRange.max() - inputRange.min() + 1;
    boolean isByteOutput = bitConfig.bitsOutLut() == 8;

    if (isByteOutput) {
      return createByteLut(numEntries, inputRange.min(), intercept, slope, bitConfig, inverse);
    } else {
      return createShortLut(numEntries, inputRange.min(), intercept, slope, bitConfig, inverse);
    }
  }

  private static byte[] createByteLut(
      int numEntries,
      int minInValue,
      double intercept,
      double slope,
      BitConfiguration config,
      boolean inverse) {
    byte[] lut = new byte[numEntries];
    for (int i = 0; i < numEntries; i++) {
      int value = calculateRescaledValue(i + minInValue, intercept, slope, config, inverse);
      lut[i] = (byte) value;
    }
    return lut;
  }

  private static short[] createShortLut(
      int numEntries,
      int minInValue,
      double intercept,
      double slope,
      BitConfiguration config,
      boolean inverse) {
    short[] lut = new short[numEntries];
    for (int i = 0; i < numEntries; i++) {
      int value = calculateRescaledValue(i + minInValue, intercept, slope, config, inverse);
      lut[i] = (short) value;
    }
    return lut;
  }

  private static int calculateRescaledValue(
      int inputValue, double intercept, double slope, BitConfiguration config, boolean inverse) {
    int value = (int) Math.round(inputValue * slope + intercept);
    value = MathUtil.clamp(value, config.minOutValue(), config.maxOutValue());
    return inverse ? (config.maxOutValue() + config.minOutValue() - value) : value;
  }

  private static LookupTableCV createLookupTable(
      Object lutData, int minInValue, boolean outputSigned) {
    return lutData instanceof byte[] bytes
        ? new LookupTableCV(bytes, minInValue)
        : new LookupTableCV((short[]) lutData, minInValue, !outputSigned);
  }

  private static boolean isPaddingApplicable(LookupTableCV modalityLookup, LutParameters params) {
    return modalityLookup != null
        && params != null
        && params.applyPadding()
        && params.paddingMinValue() != null
        && modalityLookup.getDataType() <= DataBuffer.TYPE_SHORT;
  }

  private static PaddingRange calculatePaddingRange(LutParameters params) {
    int paddingValue = params.paddingMinValue();
    Integer paddingLimit = params.paddingMaxValue();
    int paddingValueMin =
        paddingLimit == null ? paddingValue : Math.min(paddingValue, paddingLimit);
    int paddingValueMax =
        paddingLimit == null ? paddingValue : Math.max(paddingValue, paddingLimit);

    return new PaddingRange(paddingValueMin, paddingValueMax);
  }

  private static PaddingInfo calculatePaddingInfo(PaddingRange range, LookupTableCV lookup) {
    int numPaddingValues = range.max() - range.min() + 1;
    int startIndex = range.min() - lookup.getOffset();

    if (startIndex >= lookup.getNumEntries()) {
      return PaddingInfo.invalid();
    }

    if (startIndex < 0) {
      numPaddingValues += startIndex;
      if (numPaddingValues < 1) {
        return PaddingInfo.invalid();
      }
      startIndex = 0;
    }

    return new PaddingInfo(startIndex, numPaddingValues, true);
  }

  private static void applyPaddingToLookupData(
      LookupTableCV lookup, PaddingInfo info, boolean inverse) {
    boolean isByteData = lookup.getDataType() == DataBuffer.TYPE_BYTE;

    if (isByteData) {
      byte[] data = lookup.getByteData(0);
      byte fillVal = inverse ? (byte) 255 : (byte) 0;
      Arrays.fill(data, info.startIndex(), info.startIndex() + info.numValues(), fillVal);
    } else {
      short[] data = lookup.getShortData(0);
      short fillVal = inverse ? data[data.length - 1] : data[0];
      Arrays.fill(data, info.startIndex(), info.startIndex() + info.numValues(), fillVal);
    }
  }

  private static boolean isValueInRange(LookupTableCV lookup, double pixelValue) {
    return pixelValue >= lookup.getOffset()
        && pixelValue <= lookup.getOffset() + lookup.getNumEntries() - 1;
  }

  private static RescaleParameters extractRescaleParameters(Attributes dcm) {
    Double slope = DicomUtils.getDoubleFromDicomElement(dcm, Tag.RescaleSlope, null);
    Double intercept = DicomUtils.getDoubleFromDicomElement(dcm, Tag.RescaleIntercept, null);

    return new RescaleParameters(
        slope != null ? slope : 1.0,
        intercept != null ? intercept : 0.0,
        slope != null || intercept != null);
  }

  // Helper records for better organization

  private record BitConfiguration(
      int stored, int bitsOutLut, int minOutValue, int maxOutValue, boolean isSigned) {}

  private record InputRange(int min, int max) {}

  private record PaddingRange(int min, int max) {}

  private record PaddingInfo(int startIndex, int numValues, boolean valid) {
    static PaddingInfo invalid() {
      return new PaddingInfo(0, 0, false);
    }

    boolean isValid() {
      return valid;
    }
  }

  private record RescaleParameters(double slope, double intercept, boolean hasRescaleParameters) {}
}
