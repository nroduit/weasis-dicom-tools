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

  // Constants for bit manipulation and validation
  private static final int MIN_BITS = 1;
  private static final int MAX_BITS = 16;
  private static final int BYTE_THRESHOLD = 8;
  private static final int BYTE_BITS = 8;
  private static final int SHORT_BITS = 16;

  private static final double DEFAULT_SLOPE = 1.0;
  private static final double DEFAULT_INTERCEPT = 0.0;

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

    var bitConfig = BitConfiguration.create(bitsStored, bitsOutput, isSigned, outputSigned);
    var inputRange = InputRange.create(minValue, maxValue, bitConfig);

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

    var paddingRange = PaddingRange.from(params);
    var paddingInfo = PaddingInfo.calculate(paddingRange, modalityLookup);

    if (paddingInfo.isValid()) {

      applyPaddingToLookupData(modalityLookup, paddingInfo, params.inversePaddingMLUT());
    }
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
    return (lookup != null && isValueInRange(lookup, pixelValue))
        ? lookup.lookup(0, (int) pixelValue)
        : pixelValue;
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

    var rescaleParams = RescaleParameters.from(dcm);
    return rescaleParams.hasRescaleParameters()
        ? pixelValue * rescaleParams.slope() + rescaleParams.intercept()
        : pixelValue;
  }

  // ======= Private helper methods =======

  private static Object createLutArray(
      InputRange inputRange,
      BitConfiguration bitConfig,
      double intercept,
      double slope,
      boolean inverse) {
    int numEntries = inputRange.size();

    return bitConfig.isByteOutput()
        ? createByteLut(numEntries, inputRange.min(), intercept, slope, bitConfig, inverse)
        : createShortLut(numEntries, inputRange.min(), intercept, slope, bitConfig, inverse);
  }

  private static byte[] createByteLut(
      int numEntries,
      int minInValue,
      double intercept,
      double slope,
      BitConfiguration config,
      boolean inverse) {
    var lut = new byte[numEntries];
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
    var lut = new short[numEntries];
    for (int i = 0; i < numEntries; i++) {
      int value = calculateRescaledValue(i + minInValue, intercept, slope, config, inverse);
      lut[i] = (short) value;
    }
    return lut;
  }

  // Calculates rescaled value with clamping and optional inversion
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

  private static void applyPaddingToLookupData(
      LookupTableCV lookup, PaddingInfo info, boolean inverse) {

    switch (lookup.getDataType()) {
      case DataBuffer.TYPE_BYTE -> {
        byte[] data = lookup.getByteData(0);
        byte fillVal = inverse ? (byte) 255 : (byte) 0;
        Arrays.fill(data, info.startIndex(), info.endIndex(), fillVal);
      }
      case DataBuffer.TYPE_SHORT, DataBuffer.TYPE_USHORT -> {
        short[] data = lookup.getShortData(0);
        short fillVal = inverse ? data[data.length - 1] : data[0];
        Arrays.fill(data, info.startIndex(), info.endIndex(), fillVal);
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported data type for padding: " + lookup.getDataType());
    }
  }

  private static boolean isValueInRange(LookupTableCV lookup, double pixelValue) {
    return pixelValue >= lookup.getOffset()
        && pixelValue <= lookup.getOffset() + lookup.getNumEntries() - 1;
  }

  // ======= Helper records =======

  private record BitConfiguration(
      int stored, int bitsOutLut, int minOutValue, int maxOutValue, boolean isSigned) {

    static BitConfiguration create(
        int bitsStored, int bitsOutput, boolean isSigned, boolean outputSigned) {
      int stored = MathUtil.clamp(bitsStored, MIN_BITS, MAX_BITS);
      int bitsOutLut = bitsOutput <= BYTE_THRESHOLD ? BYTE_BITS : SHORT_BITS;
      int outRangeSize = (1 << bitsOutLut) - 1;
      int maxOutValue = outputSigned ? (1 << (bitsOutLut - 1)) - 1 : outRangeSize;
      int minOutValue = outputSigned ? -(maxOutValue + 1) : 0;

      return new BitConfiguration(stored, bitsOutLut, minOutValue, maxOutValue, isSigned);
    }

    boolean isByteOutput() {
      return bitsOutLut == BYTE_BITS;
    }
  }

  private record InputRange(int min, int max) {

    static InputRange create(int minValue, int maxValue, BitConfiguration config) {
      int minInValue = config.isSigned() ? -(1 << (config.stored() - 1)) : 0;
      int maxInValue =
          config.isSigned() ? (1 << (config.stored() - 1)) - 1 : (1 << config.stored()) - 1;

      minInValue = Math.max(minInValue, Math.min(maxValue, minValue));
      maxInValue = Math.min(maxInValue, Math.max(maxValue, minValue));

      return new InputRange(minInValue, maxInValue);
    }

    int size() {
      return max - min + 1;
    }
  }

  private record PaddingRange(int min, int max) {

    static PaddingRange from(LutParameters params) {
      int paddingValue = params.paddingMinValue();
      Integer paddingLimit = params.paddingMaxValue();
      int paddingValueMin =
          paddingLimit == null ? paddingValue : Math.min(paddingValue, paddingLimit);
      int paddingValueMax =
          paddingLimit == null ? paddingValue : Math.max(paddingValue, paddingLimit);

      return new PaddingRange(paddingValueMin, paddingValueMax);
    }

    int size() {
      return max - min + 1;
    }
  }

  private record PaddingInfo(int startIndex, int numValues, boolean valid) {
    static PaddingInfo invalid() {
      return new PaddingInfo(0, 0, false);
    }

    static PaddingInfo calculate(PaddingRange range, LookupTableCV lookup) {
      int numPaddingValues = range.size();
      int startIndex = range.min() - lookup.getOffset();

      if (startIndex >= lookup.getNumEntries()) {
        return invalid();
      }

      if (startIndex < 0) {
        numPaddingValues += startIndex;
        if (numPaddingValues < 1) {
          return invalid();
        }
        startIndex = 0;
      }

      return new PaddingInfo(startIndex, numPaddingValues, true);
    }

    boolean isValid() {
      return valid;
    }

    int endIndex() {
      return startIndex + numValues;
    }
  }

  private record RescaleParameters(double slope, double intercept, boolean hasRescaleParameters) {

    static RescaleParameters from(Attributes dcm) {
      Double slope = DicomUtils.getDoubleFromDicomElement(dcm, Tag.RescaleSlope, null);
      Double intercept = DicomUtils.getDoubleFromDicomElement(dcm, Tag.RescaleIntercept, null);

      return new RescaleParameters(
          slope != null ? slope : DEFAULT_SLOPE,
          intercept != null ? intercept : DEFAULT_INTERCEPT,
          slope != null || intercept != null);
    }
  }
}
