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

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferUShort;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.LookupTableUtils;
import org.dcm4che3.img.util.RescaleUtils;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.SoftHashMap;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageAnalyzer;
import org.weasis.opencv.op.lut.LutParameters;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.PresentationStateLut;
import org.weasis.opencv.op.lut.WlParams;
import org.weasis.opencv.op.lut.WlPresentation;

/**
 * Adapter for DICOM images that provides access to pixel data transformations, lookup tables, and
 * window/level presets. This class handles the complex pixel value transformations required for
 * proper DICOM image display, including modality LUT, VOI LUT, and presentation state handling.
 *
 * <p>The adapter supports:
 *
 * <ul>
 *   <li>Pixel value transformation using rescale slope/intercept or modality LUT
 *   <li>Window/level preset management and computation
 *   <li>Pixel padding handling for monochrome images
 *   <li>Min/max value computation with caching
 *   <li>Photometric interpretation handling
 * </ul>
 *
 * @author Nicolas Roduit
 */
public class DicomImageAdapter {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomImageAdapter.class);

  private static final SoftHashMap<LutParameters, LookupTableCV> LUT_CACHE = new SoftHashMap<>();

  private final ImageDescriptor desc;
  private final MinMaxLocResult minMax;
  private final int frameIndex;

  private int bitsStored;
  private List<PresetWindowLevel> windowingPresetCollection;

  /**
   * Creates a new DICOM image adapter for the specified image and frame.
   *
   * @param image the planar image to adapt (must not be null)
   * @param desc the image descriptor containing DICOM metadata (must not be null)
   * @param frameIndex the zero-based frame index
   */
  public DicomImageAdapter(PlanarImage image, ImageDescriptor desc, int frameIndex) {
    int depth = CvType.depth(Objects.requireNonNull(image).type());
    this.desc = Objects.requireNonNull(desc);
    this.bitsStored = depth > CvType.CV_16S ? (int) image.elemSize1() * 8 : desc.getBitsStored();
    this.frameIndex = frameIndex;
    this.minMax = computeMinMaxValues(image, frameIndex);

    // Pre-compute modality lookup for performance optimization
    getModalityLookup(null, false);
  }

  // Getters
  public int getBitsStored() {
    return bitsStored;
  }

  public void setBitsStored(int bitsStored) {
    this.bitsStored = bitsStored;
  }

  public MinMaxLocResult getMinMax() {
    return minMax;
  }

  public ImageDescriptor getImageDescriptor() {
    return desc;
  }

  public int getFrameIndex() {
    return frameIndex;
  }

  /**
   * Returns the minimum allocated value based on the modality LUT output characteristics.
   *
   * @param wl the window/level presentation parameters
   * @return the minimum allocated value
   */
  public int getMinAllocatedValue(WlPresentation wl) {
    boolean signed = isModalityLutOutSigned(wl);
    int bitsAllocated = desc.getBitsAllocated();
    int maxValue = signed ? (1 << (bitsAllocated - 1)) - 1 : ((1 << bitsAllocated) - 1);
    return signed ? -(maxValue + 1) : 0;
  }

  /**
   * Returns the maximum allocated value based on the modality LUT output characteristics.
   *
   * @param wl the window/level presentation parameters
   * @return the maximum allocated value
   */
  public int getMaxAllocatedValue(WlPresentation wl) {
    boolean signed = isModalityLutOutSigned(wl);
    int bitsAllocated = desc.getBitsAllocated();
    return signed ? (1 << (bitsAllocated - 1)) - 1 : ((1 << bitsAllocated) - 1);
  }

  /**
   * Determines if the modality LUT output can produce signed values. This occurs when rescale
   * operations result in negative values or when pixel representation is signed.
   *
   * @param wl the window/level presentation parameters
   * @return true if the output can be signed
   */
  public boolean isModalityLutOutSigned(WlPresentation wl) {
    return getMinValue(wl) < 0 || desc.isSigned();
  }

  /**
   * Returns the minimum value after modality pixel transformation and pixel padding operations.
   *
   * @param wl the window/level presentation parameters
   * @return the transformed minimum value
   */
  public double getMinValue(WlPresentation wl) {
    return computeTransformedValue(true, wl);
  }

  /**
   * Returns the maximum value after modality pixel transformation and pixel padding operations.
   *
   * @param wl the window/level presentation parameters
   * @return the transformed maximum value
   */
  public double getMaxValue(WlPresentation wl) {
    return computeTransformedValue(false, wl);
  }

  /**
   * Gets the rescale intercept value, prioritizing presentation state over image descriptor.
   *
   * @param dcm the presentation state DICOM object (may be null)
   * @return the rescale intercept value (default: 0.0)
   */
  public double getRescaleIntercept(PrDicomObject dcm) {
    if (dcm != null) {
      OptionalDouble prIntercept = dcm.getModalityLutModule().getRescaleIntercept();
      if (prIntercept.isPresent()) {
        return prIntercept.getAsDouble();
      }
    }
    return desc.getModalityLutForFrame(frameIndex).getRescaleIntercept().orElse(0.0);
  }

  /**
   * Gets the rescale slope value, prioritizing presentation state over image descriptor.
   *
   * @param dcm the presentation state DICOM object (may be null)
   * @return the rescale slope value (default: 1.0)
   */
  public double getRescaleSlope(PrDicomObject dcm) {
    if (dcm != null) {
      OptionalDouble prSlope = dcm.getModalityLutModule().getRescaleSlope();
      if (prSlope.isPresent()) {
        return prSlope.getAsDouble();
      }
    }
    return desc.getModalityLutForFrame(frameIndex).getRescaleSlope().orElse(1.0);
  }

  public double getFullDynamicWidth(WlPresentation wl) {
    return getMaxValue(wl) - getMinValue(wl);
  }

  public double getFullDynamicCenter(WlPresentation wl) {
    double minValue = getMinValue(wl);
    double maxValue = getMaxValue(wl);
    return minValue + (maxValue - minValue) / 2.0;
  }

  /**
   * Returns the default preset (typically the first in the collection).
   *
   * @param wlp the window/level presentation parameters
   * @return the default preset, or null if none available
   */
  public PresetWindowLevel getDefaultPreset(WlPresentation wlp) {
    List<PresetWindowLevel> presetList = getPresetList(wlp);
    return (presetList != null && !presetList.isEmpty()) ? presetList.get(0) : null;
  }

  /**
   * Returns the collection of window/level presets for this image.
   *
   * @param wl the window/level presentation parameters
   * @return the preset collection (may be null or empty)
   */
  public synchronized List<PresetWindowLevel> getPresetList(WlPresentation wl) {
    return getPresetList(wl, false);
  }

  /**
   * Returns the collection of window/level presets, optionally forcing a reload.
   *
   * @param wl the window/level presentation parameters
   * @param reload if true, forces recomputation of the preset collection
   * @return the preset collection (may be null or empty)
   */
  public synchronized List<PresetWindowLevel> getPresetList(WlPresentation wl, boolean reload) {
    if (minMax != null && (windowingPresetCollection == null || reload)) {
      windowingPresetCollection = PresetWindowLevel.getPresetCollection(this, "[DICOM]", wl);
    }
    return windowingPresetCollection;
  }

  public int getPresetCollectionSize() {
    return windowingPresetCollection != null ? windowingPresetCollection.size() : 0;
  }

  public LutShape getDefaultShape(WlPresentation wlp) {
    PresetWindowLevel defaultPreset = getDefaultPreset(wlp);
    return (defaultPreset != null) ? defaultPreset.getLutShape() : LutShape.LINEAR;
  }

  public double getDefaultWindow(WlPresentation wlp) {
    PresetWindowLevel defaultPreset = getDefaultPreset(wlp);
    if (defaultPreset != null) {
      return defaultPreset.getWindow();
    }
    return minMax != null ? minMax.maxVal - minMax.minVal : 0.0;
  }

  public double getDefaultLevel(WlPresentation wlp) {
    PresetWindowLevel defaultPreset = getDefaultPreset(wlp);
    if (defaultPreset != null) {
      return defaultPreset.getLevel();
    }
    if (minMax != null) {
      return minMax.minVal + (minMax.maxVal - minMax.minVal) / 2.0;
    }
    return 0.0;
  }

  /**
   * Transforms a pixel value to its real-world value using the modality LUT.
   *
   * @param pixelValue the raw pixel value
   * @param wlp the window/level presentation parameters
   * @return the transformed value, or the original value if no transformation applies
   */
  public Number pixelToRealValue(Number pixelValue, WlPresentation wlp) {
    if (pixelValue == null) {
      return null;
    }
    LookupTableCV lookup = getModalityLookup(wlp, false);
    if (lookup != null) {
      int val = pixelValue.intValue();
      if (isValueInLookupRange(val, lookup)) {
        return lookup.lookup(0, val);
      }
    }
    return pixelValue;
  }

  /**
   * Creates or retrieves the modality lookup table according to DICOM PS 3.3 C.11.1. Handles both
   * LUT sequence data and rescale slope/intercept transformations.
   *
   * @param wlp the window/level presentation parameters
   * @param inverseLUTAction whether to apply inverse LUT transformation
   * @return the modality lookup table, or null if no transformation is needed
   */
  public LookupTableCV getModalityLookup(WlPresentation wlp, boolean inverseLUTAction) {
    Optional<Integer> paddingValue = desc.getPixelPaddingValue();
    boolean pixelPadding = wlp == null || wlp.isPixelPadding();
    PrDicomObject pr = extractPresentationState(wlp);

    LookupTableCV modalityLut = getModalityLutFromPresentationState(pr);
    if (modalityLut != null && canApplyModalityLut(modalityLut, pixelPadding, paddingValue)) {
      return modalityLut;
    }

    return createModalityLookup(pixelPadding, modalityLut, inverseLUTAction, pr);
  }

  public boolean isPhotometricInterpretationInverse(PresentationStateLut pr) {
    Optional<String> prLUTShape = pr != null ? pr.getPrLutShapeMode() : Optional.empty();
    PhotometricInterpretation p = desc.getPhotometricInterpretation();
    return prLUTShape
        .map("INVERSE"::equals)
        .orElseGet(() -> p == PhotometricInterpretation.MONOCHROME1);
  }

  // LUT parameters method
  public LutParameters getLutParameters(
      boolean pixelPadding, LookupTableCV mLUTSeq, boolean inversePaddingMLUT, PrDicomObject pr) {
    Optional<Integer> paddingValue = desc.getPixelPaddingValue();
    boolean isSigned = desc.isSigned();
    double intercept = getRescaleIntercept(pr);
    double slope = getRescaleSlope(pr);

    if (isIdentityTransformation(slope, intercept, paddingValue)) {
      return null;
    }

    Optional<Integer> paddingLimit = desc.getPixelPaddingRangeLimit();
    int bitsOutputLut = calculateOutputBits(mLUTSeq, slope, intercept);
    boolean outputSigned = calculateOutputSigned(mLUTSeq, slope, intercept, isSigned);
    if(bitsOutputLut <= 0 || bitsOutputLut > 32) {
      return null;
    }

    return new LutParameters(
        intercept,
        slope,
        pixelPadding,
        paddingValue.orElse(null),
        paddingLimit.orElse(null),
        bitsStored,
        isSigned,
        outputSigned,
        bitsOutputLut,
        inversePaddingMLUT);
  }

  // VOI LUT method
  public LookupTableCV getVOILookup(WlParams wl) {
    if (wl == null || wl.getLutShape() == null) {
      return null;
    }

    var valueRange = calculateVoiValueRange(wl);
    return LookupTableUtils.createVoiLut(
        wl.getLutShape(),
        wl.getWindow(),
        wl.getLevel(),
        valueRange.minValue(),
        valueRange.maxValue(),
        8,
        false,
        isPhotometricInterpretationInverse(wl.getPresentationState()));
  }

  // Static utility methods
  public static MinMaxLocResult getMinMaxValues(
      PlanarImage image, ImageDescriptor desc, int frameIndex) {
    MinMaxLocResult val = desc.getMinMaxPixelValue(frameIndex);
    if (val != null) {
      return val;
    }

    if (desc.getPhotometricInterpretation().isMonochrome()) {
      val = computeMinMaxWithPadding(image, desc);
    }

    return val != null
        ? val
        : ImageAnalyzer.findRawMinMaxValues(
            image, !desc.getPhotometricInterpretation().isMonochrome());
  }

  // Private helper methods

  /** Computes and caches min/max pixel values */
  private MinMaxLocResult computeMinMaxValues(PlanarImage image, int frameIndex) {
    MinMaxLocResult result = desc.getMinMaxPixelValue(frameIndex);
    if (result == null) {
      result = findMinMaxValues(image, frameIndex);
      desc.setMinMaxPixelValue(frameIndex, result);
    }
    return result;
  }

  /** Computes transformed min or max value with rescale operations */
  private double computeTransformedValue(boolean isMin, WlPresentation wl) {
    Number min = pixelToRealValue(minMax.minVal, wl);
    Number max = pixelToRealValue(minMax.maxVal, wl);
    if (min == null || max == null) {
      return 0;
    }

    double minValue = min.doubleValue();
    double maxValue = max.doubleValue();
    return isMin ? Math.min(minValue, maxValue) : Math.max(minValue, maxValue);
  }

  /** Finds min/max values with padding handling */
  private MinMaxLocResult findMinMaxValues(PlanarImage image, int frameIndex) {
    MinMaxLocResult val = getMinMaxValues(image, desc, frameIndex);
    validateAndAdjustBitsStored(val);
    return val;
  }

  /** Adjusts bits stored if values exceed expected range */
  private void validateAndAdjustBitsStored(MinMaxLocResult val) {
    int bitsAllocated = desc.getBitsAllocated();
    if (bitsStored < bitsAllocated) {
      boolean isSigned = desc.isSigned();
      int minInValue = isSigned ? -(1 << (bitsStored - 1)) : 0;
      int maxInValue = isSigned ? (1 << (bitsStored - 1)) - 1 : (1 << bitsStored) - 1;
      if (val.minVal < minInValue || val.maxVal > maxInValue) {
        setBitsStored(bitsAllocated);
      }
    }
  }

  /** Computes min/max with padding value exclusion */
  private static MinMaxLocResult computeMinMaxWithPadding(PlanarImage image, ImageDescriptor desc) {
    return desc.getPixelPaddingValue()
        .map(
            paddingValue -> {
              Integer paddingLimit = desc.getPixelPaddingRangeLimit().orElse(null);
              int paddingMin =
                  (paddingLimit == null) ? paddingValue : Math.min(paddingValue, paddingLimit);
              int paddingMax =
                  (paddingLimit == null) ? paddingValue : Math.max(paddingValue, paddingLimit);
              return findMinMaxValues(image, paddingMin, paddingMax);
            })
        .orElse(null);
  }

  /** Finds min/max values excluding specified padding range */
  private static MinMaxLocResult findMinMaxValues(
      PlanarImage image, Integer paddingValueMin, Integer paddingValueMax) {
    if (CvType.depth(image.type()) <= CvType.CV_8S) {
      var val = new MinMaxLocResult();
      val.minVal = 0.0;
      val.maxVal = 255.0;
      return val;
    }

    MinMaxLocResult val =
        ImageAnalyzer.findMinMaxValues(image.toMat(), paddingValueMin, paddingValueMax);
    if (val != null && val.minVal == val.maxVal) {
      val.maxVal += 1.0; // Handle edge case for uniform images
    }
    return val;
  }

  /** Checks if pixel value is within lookup table range */
  private boolean isValueInLookupRange(int val, LookupTableCV lookup) {
    return val >= lookup.getOffset() && val < lookup.getOffset() + lookup.getNumEntries();
  }

  /** Extracts presentation state from window/level parameters */
  private PrDicomObject extractPresentationState(WlPresentation wlp) {
    return wlp != null && wlp.getPresentationState() instanceof PrDicomObject pr ? pr : null;
  }

  /** Gets modality LUT from presentation state or image descriptor */
  private LookupTableCV getModalityLutFromPresentationState(PrDicomObject pr) {
    LookupTableCV prModLut = pr != null ? pr.getModalityLutModule().getLut().orElse(null) : null;
    return prModLut != null
        ? prModLut
        : desc.getModalityLutForFrame(frameIndex).getLut().orElse(null);
  }

  /** Determines if modality LUT can be applied */
  private boolean canApplyModalityLut(
      LookupTableCV mLUTSeq, boolean pixelPadding, Optional<Integer> paddingValue) {
    if (!pixelPadding || paddingValue.isEmpty()) {
      return isMinMaxInLutRange(mLUTSeq);
    }
    LOGGER.warn("Cannot apply Modality LUT sequence and Pixel Padding");
    return false;
  }

  /** Checks if min/max values are within LUT range */
  private boolean isMinMaxInLutRange(LookupTableCV mLUTSeq) {
    if (minMax.minVal >= mLUTSeq.getOffset()
        && minMax.maxVal < mLUTSeq.getOffset() + mLUTSeq.getNumEntries()) {
      return true;
    }
    LOGGER.warn("Pixel values don't match Modality LUT sequence table. Modality LUT not applied.");
    return false;
  }

  /** Creates modality lookup table with caching */
  private LookupTableCV createModalityLookup(
      boolean pixelPadding, LookupTableCV mLUTSeq, boolean inverseLUTAction, PrDicomObject pr) {
    boolean inverseLut = isPhotometricInterpretationInverse(pr);
    if (pixelPadding) {
      inverseLut ^= inverseLUTAction;
    }
    LutParameters lutParams = getLutParameters(pixelPadding, mLUTSeq, inverseLut, pr);
    if (lutParams == null) {
      return null;
    }
    return getOrCreateCachedLookup(lutParams, mLUTSeq);
  }

  /** Gets or creates cached lookup table */
  private LookupTableCV getOrCreateCachedLookup(LutParameters lutParams, LookupTableCV mLUTSeq) {
    LookupTableCV modalityLookup = LUT_CACHE.get(lutParams);

    if (modalityLookup != null) {
      return modalityLookup;
    }

    modalityLookup = createLookupTable(mLUTSeq, lutParams);

    if (desc.getPhotometricInterpretation().isMonochrome()) {
      RescaleUtils.applyPixelPaddingToModalityLUT(modalityLookup, lutParams);
    }

    LUT_CACHE.put(lutParams, modalityLookup);
    return modalityLookup;
  }

  /** Creates lookup table from sequence or parameters */
  private LookupTableCV createLookupTable(LookupTableCV mLUTSeq, LutParameters lutParams) {
    return mLUTSeq != null
        ? createLookupFromSequence(mLUTSeq)
        : RescaleUtils.createRescaleRampLut(lutParams);
  }

  /** Creates lookup table from sequence data */
  private LookupTableCV createLookupFromSequence(LookupTableCV mLUTSeq) {
    if (mLUTSeq.getNumBands() != 1) {
      return mLUTSeq;
    }
    if (mLUTSeq.getDataType() == DataBuffer.TYPE_BYTE) {
      byte[] data = mLUTSeq.getByteData(0);
      return data != null ? new LookupTableCV(data, mLUTSeq.getOffset(0)) : mLUTSeq;
    }
    short[] data = mLUTSeq.getShortData(0);
    return data != null
        ? new LookupTableCV(
            data, mLUTSeq.getOffset(0), mLUTSeq.getData() instanceof DataBufferUShort)
        : mLUTSeq;
  }

  /** Checks if transformation is identity (optimization) */
  private boolean isIdentityTransformation(
      double slope, double intercept, Optional<Integer> paddingValue) {
    return bitsStored <= 16
        && MathUtil.isEqual(slope, 1.0)
        && MathUtil.isEqualToZero(intercept)
        && paddingValue.isEmpty();
  }

  /** Calculates output bits for LUT */
  private int calculateOutputBits(LookupTableCV mLUTSeq, double slope, double intercept) {
    if (mLUTSeq != null) {
      return mLUTSeq.getDataType() == DataBuffer.TYPE_BYTE ? 8 : 16;
    }
    double minValue = minMax.minVal * slope + intercept;
    double maxValue = minMax.maxVal * slope + intercept;
    int bitsOutputLut =
        Integer.SIZE - Integer.numberOfLeadingZeros((int) Math.round(maxValue - minValue));

    // Ensure minimum bits for signed 8-bit handling
    if (minValue < 0 && bitsOutputLut <= 8) {
      bitsOutputLut = 9;
    }
    return bitsOutputLut;
  }

  /** Calculates if output should be signed */
  private boolean calculateOutputSigned(
      LookupTableCV mLUTSeq, double slope, double intercept, boolean isSigned) {
    if (mLUTSeq != null) {
      return false;
    }
    double minValue = minMax.minVal * slope + intercept;
    return minValue < 0 || isSigned;
  }

  /** Calculates VOI value range with modern record pattern */
  private ValueRange calculateVoiValueRange(WlParams wl) {
    if (shouldExtendVoiRange(wl)) {
      return new ValueRange(getMinAllocatedValue(wl), getMaxAllocatedValue(wl));
    } else {
      return new ValueRange((int) wl.getLevelMin(), (int) wl.getLevelMax());
    }
  }

  /** Determines if VOI range should be extended */
  private boolean shouldExtendVoiRange(WlParams wl) {
    return wl.isFillOutsideLutRange()
        || (desc.getPixelPaddingValue().isPresent()
            && desc.getPhotometricInterpretation().isMonochrome());
  }

  private record ValueRange(int minValue, int maxValue) {}
}
