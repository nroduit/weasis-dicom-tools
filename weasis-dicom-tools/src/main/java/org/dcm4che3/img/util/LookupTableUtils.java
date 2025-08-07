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
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.ByteUtils;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.LutShape;

/**
 * Utility class for DICOM lookup table operations.
 *
 * <p>This class provides methods for creating and manipulating lookup tables (LUTs) commonly used
 * in DICOM image processing, including VOI LUTs and standard DICOM LUTs.
 */
public class LookupTableUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(LookupTableUtils.class);

  // DICOM standard constants
  private static final int MAX_BITS_STORED = 16;
  private static final double MIN_WINDOW_WIDTH = 1.0;
  private static final int SIGMOID_FACTOR = -20;
  private static final int LOG_EXP_FACTOR = 20;
  private static final double FACTOR_DIVISOR = 10.0;

  private LookupTableUtils() {
    // Prevent instantiation
  }

  /**
   * Creates a VOI (Value of Interest) LUT for windowing operations.
   *
   * <p>The output range depends on the bitsStored and signed parameters:
   *
   * <ul>
   *   <li>8 bits unsigned: 0-255
   *   <li>8 bits signed: -128 to 127
   *   <li>16 bits unsigned: 0-65535
   *   <li>16 bits signed: -32768 to 32767
   * </ul>
   *
   * @param lutShape the LUT shape defining the transformation function
   * @param window the window width (must be > 0)
   * @param level the window center
   * @param minValue the minimum input value
   * @param maxValue the maximum input value
   * @param bitsStored the number of bits stored (1-16)
   * @param isSigned true if the data is signed
   * @param inverse true if the LUT should be inverted
   * @return a LookupTableCV for windowing, or null if lutShape is null
   */
  public static LookupTableCV createVoiLut(
      LutShape lutShape,
      double window,
      double level,
      int minValue,
      int maxValue,
      int bitsStored,
      boolean isSigned,
      boolean inverse) {

    if (lutShape == null) {
      return null;
    }

    var lutConfig = new LutConfiguration(window, level, minValue, maxValue, bitsStored, isSigned);
    Object outLut = createOutputArray(lutConfig);

    populateLut(lutShape, lutConfig, outLut, inverse);

    return createLookupTable(outLut, lutConfig);
  }

  /**
   * Creates a DICOM LUT from DICOM attributes.
   *
   * @param dicomLutObject the DICOM LUT attributes containing descriptor and data
   * @return an Optional containing the LookupTableCV, or empty if creation fails
   * @see <a href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.11.html">
   *     C.11 Look Up Tables and Presentation State</a>
   */
  public static Optional<LookupTableCV> createLut(Attributes dicomLutObject) {
    if (dicomLutObject == null || dicomLutObject.isEmpty()) {
      return Optional.empty();
    }

    int[] descriptor =
        DicomUtils.getIntArrayFromDicomElement(dicomLutObject, Tag.LUTDescriptor, null);
    if (!isValidDescriptor(descriptor)) {
      return Optional.empty();
    }

    var lutInfo = new DicomLutInfo(descriptor);
    byte[] lutData = extractLutData(dicomLutObject, lutInfo);

    if (lutData == null) {
      return Optional.empty();
    }

    return createDicomLookupTable(lutData, lutInfo);
  }

  /** Extracts and validates LUT descriptor from DICOM attributes. */
  public static int[] lutDescriptor(Attributes ds, int descTag) {
    int[] desc = ds.getInts(descTag);
    if (desc == null) {
      throw new IllegalArgumentException("Missing LUT Descriptor!");
    }
    if (desc.length != 3) {
      throw new IllegalArgumentException("Illegal number of LUT Descriptor values: " + desc.length);
    }
    if (desc[0] < 0) {
      throw new IllegalArgumentException("Illegal LUT Descriptor: len=" + desc[0]);
    }
    int bits = desc[2];
    if (bits != 8 && bits != 16) {
      throw new IllegalArgumentException("Illegal LUT Descriptor: bits=" + bits);
    }
    return desc;
  }

  /** Extracts LUT data from DICOM attributes. */
  public static byte[] lutData(Attributes ds, int[] desc, int dataTag, int segmTag) {
    int len = desc[0] <= 0 ? desc[0] + 0x10000 : desc[0];
    int bits = desc[2];

    Optional<byte[]> odata = DicomAttributeUtils.getByteData(ds, dataTag);
    if (odata.isEmpty()) {
      return handleSegmentedLutData(ds, segmTag, len, bits);
    }

    return processLutData(odata.get(), len, bits, ds.bigEndian());
  }

  // Private helper classes and methods

  /** Configuration container for LUT creation parameters. */
  private static class LutConfiguration {
    final int bitsStored;
    final double window;
    final double level;
    final int minInValue;
    final int maxInValue;
    final int numEntries;
    final int minOutValue;
    final int maxOutValue;
    final boolean isSigned;

    LutConfiguration(
        double window, double level, int minValue, int maxValue, int bitsStored, boolean isSigned) {
      this.bitsStored = Math.min(Math.max(bitsStored, 1), MAX_BITS_STORED);
      this.window = Math.max(window, MIN_WINDOW_WIDTH);
      this.level = level;
      this.minInValue = Math.min(maxValue, minValue);
      this.maxInValue = Math.max(maxValue, minValue);
      this.numEntries = this.maxInValue - this.minInValue + 1;
      this.isSigned = isSigned;

      int bitsAllocated = (this.bitsStored <= 8) ? 8 : 16;
      int outRangeSize = (1 << bitsAllocated) - 1;
      this.maxOutValue = isSigned ? (1 << (bitsAllocated - 1)) - 1 : outRangeSize;
      this.minOutValue = isSigned ? -(this.maxOutValue + 1) : 0;
    }
  }

  /** Information container for DICOM LUT parameters. */
  private static class DicomLutInfo {
    final int numEntries;
    final int offset;
    final int numBits;

    DicomLutInfo(int[] descriptor) {
      this.numEntries = (descriptor[0] <= 0) ? descriptor[0] + 0x10000 : descriptor[0];
      this.offset = (short) descriptor[1]; // Cast to handle negative values
      this.numBits = descriptor[2];
    }
  }

  private static Object createOutputArray(LutConfiguration config) {
    return config.bitsStored <= 8 ? new byte[config.numEntries] : new short[config.numEntries];
  }

  private static void populateLut(
      LutShape lutShape, LutConfiguration config, Object outLut, boolean inverse) {
    if (lutShape.getFunctionType() != null) {
      switch (lutShape.getFunctionType()) {
        case LINEAR -> setLinearLut(config, outLut, inverse);
        case SIGMOID -> setSigmoidLut(config, outLut, inverse, false);
        case SIGMOID_NORM -> setSigmoidLut(config, outLut, inverse, true);
        case LOG -> setLogarithmicLut(config, outLut, inverse);
        case LOG_INV -> setExponentialLut(config, outLut, inverse);
      }
    } else {
      setSequenceLut(config, lutShape.getLookup(), outLut, inverse);
    }
  }

  private static LookupTableCV createLookupTable(Object outLut, LutConfiguration config) {
    return (outLut instanceof byte[] bytes)
        ? new LookupTableCV(bytes, config.minInValue)
        : new LookupTableCV((short[]) outLut, config.minInValue, !config.isSigned);
  }

  private static void setLinearLut(LutConfiguration config, Object outLut, boolean inverse) {
    double slope = (config.maxOutValue - config.minOutValue) / config.window;
    double intercept = config.maxOutValue - slope * (config.level + (config.window / 2.0));

    for (int i = 0; i < Array.getLength(outLut); i++) {
      int value = (int) ((i + config.minInValue) * slope + intercept);
      setLutValue(outLut, config.minOutValue, config.maxOutValue, inverse, i, value);
    }
  }

  private static void setSigmoidLut(
      LutConfiguration config, Object outLut, boolean inverse, boolean normalize) {
    double outRange = config.maxOutValue - (double) config.minOutValue;
    double nFactor = SIGMOID_FACTOR;

    NormalizationParams normParams =
        calculateNormalization(config, normalize, nFactor, outRange, LutType.SIGMOID);

    for (int i = 0; i < Array.getLength(outLut); i++) {
      double value =
          outRange
              / (1.0
                  + Math.exp(
                      (2.0 * nFactor / FACTOR_DIVISOR)
                          * (i + config.minInValue - config.level)
                          / config.window));

      setNormalizedLutValue(outLut, config, inverse, normalize, normParams, i, value);
    }
  }

  private static void setExponentialLut(LutConfiguration config, Object outLut, boolean inverse) {
    double outRange = config.maxOutValue - (double) config.minOutValue;
    double nFactor = LOG_EXP_FACTOR;

    NormalizationParams normParams =
        calculateNormalization(config, true, nFactor, outRange, LutType.EXPONENTIAL);

    for (int i = 0; i < Array.getLength(outLut); i++) {
      double value =
          outRange
              * Math.exp(
                  (nFactor / FACTOR_DIVISOR)
                      * (i + config.minInValue - config.level)
                      / config.window);

      setNormalizedLutValue(outLut, config, inverse, true, normParams, i, value);
    }
  }

  private static void setLogarithmicLut(LutConfiguration config, Object outLut, boolean inverse) {
    double outRange = config.maxOutValue - (double) config.minOutValue;
    double nFactor = LOG_EXP_FACTOR;

    NormalizationParams normParams =
        calculateNormalization(config, true, nFactor, outRange, LutType.LOGARITHMIC);

    for (int i = 0; i < Array.getLength(outLut); i++) {
      double value =
          outRange
              * Math.log(
                  (nFactor / FACTOR_DIVISOR)
                      * (1 + (i + config.minInValue - config.level) / config.window));

      setNormalizedLutValue(outLut, config, inverse, true, normParams, i, value);
    }
  }

  private static void setSequenceLut(
      LutConfiguration config, LookupTableCV lookupSequence, Object outLut, boolean inverse) {
    Object inLutDataArray = getLutDataArray(lookupSequence);
    if (inLutDataArray == null) {
      return;
    }

    var sequenceProcessor = new SequenceLutProcessor(config, inLutDataArray, outLut, inverse);
    sequenceProcessor.process();
  }

  /** Enumeration for different LUT transformation types. */
  private enum LutType {
    SIGMOID,
    EXPONENTIAL,
    LOGARITHMIC
  }

  /** Container for normalization parameters. */
  private static class NormalizationParams {
    final double minValue;
    final double rescaleRatio;

    NormalizationParams(double minValue, double rescaleRatio) {
      this.minValue = minValue;
      this.rescaleRatio = rescaleRatio;
    }
  }

  private static NormalizationParams calculateNormalization(
      LutConfiguration config, boolean normalize, double nFactor, double outRange, LutType type) {
    if (!normalize) {
      return new NormalizationParams(0, 1);
    }

    double lowLevel = config.level - config.window / 2.0;
    double highLevel = config.level + config.window / 2.0;

    double minValue =
        switch (type) {
          case SIGMOID ->
              config.minOutValue
                  + outRange
                      / (1.0
                          + Math.exp(
                              (2.0 * nFactor / FACTOR_DIVISOR)
                                  * (lowLevel - config.level)
                                  / config.window));
          case EXPONENTIAL ->
              config.minOutValue
                  + outRange
                      * Math.exp(
                          (nFactor / FACTOR_DIVISOR) * (lowLevel - config.level) / config.window);
          case LOGARITHMIC ->
              config.minOutValue
                  + outRange
                      * Math.log(
                          (nFactor / FACTOR_DIVISOR)
                              * (1 + (lowLevel - config.level) / config.window));
        };

    double maxValue =
        switch (type) {
          case SIGMOID ->
              config.minOutValue
                  + outRange
                      / (1.0
                          + Math.exp(
                              (2.0 * nFactor / FACTOR_DIVISOR)
                                  * (highLevel - config.level)
                                  / config.window));
          case EXPONENTIAL ->
              config.minOutValue
                  + outRange
                      * Math.exp(
                          (nFactor / FACTOR_DIVISOR) * (highLevel - config.level) / config.window);
          case LOGARITHMIC ->
              config.minOutValue
                  + outRange
                      * Math.log(
                          (nFactor / FACTOR_DIVISOR)
                              * (1 + (highLevel - config.level) / config.window));
        };

    double rescaleRatio = (config.maxOutValue - config.minOutValue) / Math.abs(maxValue - minValue);
    return new NormalizationParams(minValue, rescaleRatio);
  }

  private static void setNormalizedLutValue(
      Object outLut,
      LutConfiguration config,
      boolean inverse,
      boolean normalize,
      NormalizationParams normParams,
      int i,
      double value) {
    if (normalize) {
      value = (value - normParams.minValue) * normParams.rescaleRatio;
    }

    value = Math.round(value + config.minOutValue);
    value = Math.max(config.minOutValue, Math.min(config.maxOutValue, value));
    value = inverse ? (config.maxOutValue + config.minOutValue - value) : value;

    setArrayValue(outLut, i, (int) value);
  }

  private static void setLutValue(
      Object outLut, int minOutValue, int maxOutValue, boolean inverse, int i, int value) {
    value = Math.max(minOutValue, Math.min(maxOutValue, value));
    value = inverse ? (maxOutValue + minOutValue - value) : value;
    setArrayValue(outLut, i, value);
  }

  private static void setArrayValue(Object outLut, int index, int value) {
    if (outLut instanceof byte[]) {
      Array.set(outLut, index, (byte) value);
    } else if (outLut instanceof short[]) {
      Array.set(outLut, index, (short) value);
    }
  }

  private static Object getLutDataArray(LookupTableCV lookup) {
    if (lookup == null) {
      return null;
    }
    return switch (lookup.getDataType()) {
      case DataBuffer.TYPE_BYTE -> lookup.getByteData(0);
      case DataBuffer.TYPE_SHORT, DataBuffer.TYPE_USHORT -> lookup.getShortData(0);
      default -> null;
    };
  }

  /** Processor for sequence-based LUT transformations. */
  private static class SequenceLutProcessor {
    private final LutConfiguration config;
    private final Object inLutDataArray;
    private final Object outLut;
    private final boolean inverse;
    private final int lutDataValueMask;
    private final double lowLevel;
    private final double highLevel;
    private final int maxInLutIndex;

    SequenceLutProcessor(
        LutConfiguration config, Object inLutDataArray, Object outLut, boolean inverse) {
      this.config = config;
      this.inLutDataArray = inLutDataArray;
      this.outLut = outLut;
      this.inverse = inverse;
      this.lutDataValueMask = calculateValueMask(inLutDataArray);
      this.lowLevel = config.level - config.window / 2.0;
      this.highLevel = config.level + config.window / 2.0;
      this.maxInLutIndex = Array.getLength(inLutDataArray) - 1;
    }

    void process() {
      var lookupRange = calculateLookupRange();
      double outRescaleRatio =
          (config.maxOutValue - config.minOutValue) / (double) lookupRange.range;

      for (int i = 0; i < Array.getLength(outLut); i++) {
        double inValueRescaled = calculateRescaledInput(i);
        int value = interpolateValue(inValueRescaled);
        value = (int) Math.round(value * outRescaleRatio);

        setLutValue(outLut, config.minOutValue, config.maxOutValue, inverse, i, value);
      }
    }

    private static int calculateValueMask(Object array) {
      return array instanceof byte[]
          ? 0x000000FF
          : (array instanceof short[] ? 0x0000FFFF : 0xFFFFFFFF);
    }

    private LookupRange calculateLookupRange() {
      int minLookupValue = Integer.MAX_VALUE;
      int maxLookupValue = Integer.MIN_VALUE;
      for (int i = 0; i < Array.getLength(inLutDataArray); i++) {
        int val = lutDataValueMask & Array.getInt(inLutDataArray, i);
        minLookupValue = Math.min(minLookupValue, val);
        maxLookupValue = Math.max(maxLookupValue, val);
      }
      return new LookupRange(minLookupValue, Math.abs(maxLookupValue - minLookupValue));
    }

    private double calculateRescaledInput(int i) {
      int inputValue = i + config.minInValue;

      if (inputValue <= lowLevel) {
        return 0;
      } else if (inputValue > highLevel) {
        return maxInLutIndex;
      } else {
        return (inputValue - lowLevel) * (maxInLutIndex / config.window);
      }
    }

    private int interpolateValue(double inValueRescaled) {
      int inValueRoundDown = Math.max(0, (int) Math.floor(inValueRescaled));
      int inValueRoundUp = Math.min(maxInLutIndex, (int) Math.ceil(inValueRescaled));

      int valueDown = lutDataValueMask & Array.getInt(inLutDataArray, inValueRoundDown);
      int valueUp = lutDataValueMask & Array.getInt(inLutDataArray, inValueRoundUp);

      return (inValueRoundUp == inValueRoundDown)
          ? valueDown
          : (int)
              Math.round(
                  valueDown
                      + (inValueRescaled - inValueRoundDown)
                          * (valueUp - valueDown)
                          / (inValueRoundUp - inValueRoundDown));
    }

    private record LookupRange(int min, int range) {}
  }

  private static boolean isValidDescriptor(int[] descriptor) {

    if (descriptor == null) {
      LOGGER.debug("Missing LUT Descriptor");
      return false;
    }
    if (descriptor.length != 3) {
      LOGGER.debug("Illegal number of LUT Descriptor values: {}", descriptor.length);
      return false;
    }
    return true;
  }

  private static byte[] extractLutData(Attributes dicomLutObject, DicomLutInfo lutInfo) {
    try {
      byte[] bData = dicomLutObject.getBytes(Tag.LUTData);
      if (bData == null || bData.length == 0) {
        return null;
      }
      return processLutDataByBits(bData, lutInfo, dicomLutObject.bigEndian());
    } catch (IOException e) {
      LOGGER.error("Cannot get byte[] of {}", TagUtils.toString(Tag.LUTData), e);
      return null;
    }
  }

  private static byte[] processLutDataByBits(
      byte[] bData, DicomLutInfo lutInfo, boolean bigEndian) {
    if (lutInfo.numBits <= 8) {
      return processEightBitLutData(bData, lutInfo, bigEndian);
    } else if (lutInfo.numBits <= 16) {
      return processSixteenBitLutData(bData, lutInfo, bigEndian);
    } else {
      LOGGER.debug("Illegal number of bits for each entry in the LUT Data");
      return null;
    }
  }

  private static byte[] processEightBitLutData(
      byte[] bData, DicomLutInfo lutInfo, boolean bigEndian) {
    if (lutInfo.numEntries <= 256 && bData.length == (lutInfo.numEntries << 1)) {
      // Handle 8-bit entries encoded with 16-bit allocation
      byte[] bDataNew = new byte[lutInfo.numEntries];
      int byteShift = bigEndian ? 1 : 0;
      for (int i = 0; i < bDataNew.length; i++) {
        bDataNew[i] = bData[(i << 1) | byteShift];
      }

      return bDataNew;
    }
    return bData;
  }

  private static byte[] processSixteenBitLutData(
      byte[] bData, DicomLutInfo lutInfo, boolean bigEndian) {
    short[] sData = new short[lutInfo.numEntries];
    ByteUtils.bytesToShorts(bData, sData, 0, sData.length, bigEndian);

    if (lutInfo.numEntries <= 256) {
      // Convert 16-bit to 8-bit with scaling
      int maxIn = (1 << lutInfo.numBits) - 1;
      int maxOut = lutInfo.numEntries - 1;

      byte[] bDataNew = new byte[lutInfo.numEntries];
      for (int i = 0; i < lutInfo.numEntries; i++) {
        bDataNew[i] = (byte) ((sData[i] & 0xffff) * maxOut / maxIn);
      }
      return bDataNew;
    }

    // Convert short array to byte array for 16-bit output
    byte[] result = new byte[sData.length * 2];
    for (int i = 0; i < sData.length; i++) {
      result[i * 2] = (byte) (sData[i] & 0xFF);
      result[i * 2 + 1] = (byte) ((sData[i] >> 8) & 0xFF);
    }
    return result;
  }

  private static Optional<LookupTableCV> createDicomLookupTable(
      byte[] lutData, DicomLutInfo lutInfo) {
    if (lutInfo.numBits <= 8) {
      return Optional.of(new LookupTableCV(lutData, lutInfo.offset));
    } else {
      // Convert byte array back to short array for 16-bit LUTs
      short[] shortData = new short[lutData.length / 2];
      for (int i = 0; i < shortData.length; i++) {
        shortData[i] = (short) ((lutData[i * 2] & 0xFF) | ((lutData[i * 2 + 1] & 0xFF) << 8));
      }

      return Optional.of(new LookupTableCV(shortData, lutInfo.offset, true));
    }
  }

  private static byte[] handleSegmentedLutData(Attributes ds, int segmTag, int len, int bits) {
    int[] lut = ds.getInts(segmTag);
    if (lut == null) {
      throw new IllegalArgumentException("Missing LUT Data!");
    }
    if (bits == 8) {
      throw new IllegalArgumentException("Segmented LUT Data with LUT Descriptor: bits=8");
    }
    byte[] data = new byte[len];
    new InflateSegmentedLut(lut, 0, data, 0).inflate(-1, 0);
    return data;
  }

  private static byte[] processLutData(byte[] data, int len, int bits, boolean bigEndian) {
    if (bits == 16 || data.length != len) {
      if (data.length != len << 1) {
        throw new IllegalArgumentException(
            "Number of actual LUT entries: "
                + data.length
                + " mismatch specified value: "
                + len
                + " in LUT Descriptor");
      }

      int hilo = bigEndian ? 0 : 1;
      if (bits == 8) {
        hilo = 1 - hilo; // padded high bits -> use low bits
      }
      return halfLength(data, hilo);
    }
    return data;
  }

  private static byte[] halfLength(byte[] data, int hilo) {
    byte[] bs = new byte[data.length >> 1];
    for (int i = 0; i < bs.length; i++) {
      bs[i] = data[(i << 1) | hilo];
    }

    return bs;
  }

  /** Helper class for inflating segmented LUT data according to DICOM standard. */
  private static class InflateSegmentedLut {
    private final int[] segm;
    private final byte[] data;
    private int readPos;
    private int writePos;

    InflateSegmentedLut(int[] segm, int readPos, byte[] data, int writePos) {
      this.segm = segm;
      this.data = data;
      this.readPos = readPos;
      this.writePos = writePos;
    }

    int inflate(int segs, int y0) {
      while (segs < 0 ? (readPos < segm.length) : segs-- > 0) {
        int segPos = readPos;
        int op = read();
        int n = read();
        y0 =
            switch (op) {
              case 0 -> discreteSegment(n);
              case 1 -> {
                if (writePos == 0) {
                  throw new IllegalArgumentException("Linear segment cannot be the first segment");
                }
                yield linearSegment(n, y0, read());
              }
              case 2 -> {
                if (segs >= 0) {
                  throw new IllegalArgumentException("nested indirect segment at index " + segPos);
                }
                yield indirectSegment(n, y0);
              }
              default ->
                  throw new IllegalArgumentException(
                      "illegal op code " + op + " at index " + segPos);
            };
      }
      return y0;
    }

    private int read() {
      if (readPos >= segm.length) {
        throw new IllegalArgumentException("Running out of data inflating segmented LUT");
      }
      return segm[readPos++] & 0xffff;
    }

    private void write(int y) {
      if (writePos >= data.length) {
        throw new IllegalArgumentException(
            "Number of entries in inflated segmented LUT exceeds specified value: "
                + data.length
                + " in LUT Descriptor");
      }
      data[writePos++] = (byte) (y >> 8);
    }

    private int discreteSegment(int n) {
      while (n-- > 0) {
        write(read());
      }
      return segm[readPos - 1] & 0xffff;
    }

    private int linearSegment(int n, int y0, int y1) {
      int dy = y1 - y0;
      for (int j = 1; j <= n; j++) {
        write(y0 + dy * j / n);
      }
      return y1;
    }

    private int indirectSegment(int n, int y0) {
      int segmentValue = read() | (read() << 16);
      return new InflateSegmentedLut(segm, segmentValue, data, writePos).inflate(n, y0);
    }
  }
}
