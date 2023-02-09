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
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.SoftHashMap;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;
import org.weasis.opencv.op.lut.LutParameters;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.PresentationStateLut;
import org.weasis.opencv.op.lut.WlParams;
import org.weasis.opencv.op.lut.WlPresentation;

/**
 * @author Nicolas Roduit
 */
public class DicomImageAdapter {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomImageAdapter.class);

  private static final SoftHashMap<LutParameters, LookupTableCV> LUT_Cache = new SoftHashMap<>();

  private final ImageDescriptor desc;
  private final MinMaxLocResult minMax;

  private int bitsStored;
  private List<PresetWindowLevel> windowingPresetCollection = null;

  public DicomImageAdapter(PlanarImage image, ImageDescriptor desc) {
    this.desc = Objects.requireNonNull(desc);
    this.bitsStored = desc.getBitsStored();
    this.minMax = findMinMaxValues(Objects.requireNonNull(image));
    /*
     * Lazily compute image pixel transformation here since inner class Load is called from a separate and dedicated
     * worker Thread. Also, it will be computed only once
     *
     * Considering that the default pixel padding option is true and Inverse LUT action is false
     */
    getModalityLookup(null, false);
  }

  private MinMaxLocResult findMinMaxValues(PlanarImage image) {
    /*
     * This function can be called several times from the inner class Load. min and max will be computed only once.
     */

    MinMaxLocResult val = null;
    // Cannot trust SmallestImagePixelValue and LargestImagePixelValue values! So search min and max
    // values
    int bitsAllocated = desc.getBitsAllocated();

    boolean monochrome = desc.getPhotometricInterpretation().isMonochrome();
    if (monochrome) {
      Integer paddingValue = desc.getPixelPaddingValue();
      if (paddingValue != null) {
        Integer paddingLimit = desc.getPixelPaddingRangeLimit();
        Integer paddingValueMin =
            (paddingLimit == null) ? paddingValue : Math.min(paddingValue, paddingLimit);
        Integer paddingValueMax =
            (paddingLimit == null) ? paddingValue : Math.max(paddingValue, paddingLimit);
        val = findMinMaxValues(image, paddingValueMin, paddingValueMax);
      }
    }

    // This function can be called several times from the inner class Load.
    // Do not compute min and max it has already be done
    if (val == null) {
      val = ImageProcessor.findRawMinMaxValues(image, !monochrome);
    }

    if (bitsStored < bitsAllocated) {
      boolean isSigned = desc.isSigned();
      int minInValue = isSigned ? -(1 << (bitsStored - 1)) : 0;
      int maxInValue = isSigned ? (1 << (bitsStored - 1)) - 1 : (1 << bitsStored) - 1;
      if (val.minVal < minInValue || val.maxVal > maxInValue) {
        /*
         *
         *
         * When the image contains values outside the bits stored values, the bits stored is replaced by the
         * bits allocated for having a LUT which handles all the values.
         *
         * Overlays in pixel data should be masked before finding min and max.
         */
        this.bitsStored = bitsAllocated;
      }
    }
    return val;
  }

  /**
   * Computes Min/Max values from Image excluding range of values provided
   *
   * @param paddingValueMin
   * @param paddingValueMax
   */
  private MinMaxLocResult findMinMaxValues(
      PlanarImage image, Integer paddingValueMin, Integer paddingValueMax) {
    MinMaxLocResult val;
    if (image.type() <= CvType.CV_8S) {
      val = new MinMaxLocResult();
      val.minVal = 0.0;
      val.maxVal = 255.0;
    } else {
      val = ImageProcessor.findMinMaxValues(image.toMat(), paddingValueMin, paddingValueMax);
      // Handle special case when min and max are equal, ex. black image
      // + 1 to max enables to display the correct value
      if (val != null && val.minVal == val.maxVal) {
        val.maxVal += 1.0;
      }
    }
    return val;
  }

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

  public int getMinAllocatedValue(WlPresentation wl) {
    boolean signed = isModalityLutOutSigned(wl);
    int bitsAllocated = desc.getBitsAllocated();
    int maxValue = signed ? (1 << (bitsAllocated - 1)) - 1 : ((1 << bitsAllocated) - 1);
    return signed ? -(maxValue + 1) : 0;
  }

  public int getMaxAllocatedValue(WlPresentation wl) {
    boolean signed = isModalityLutOutSigned(wl);
    int bitsAllocated = desc.getBitsAllocated();
    return signed ? (1 << (bitsAllocated - 1)) - 1 : ((1 << bitsAllocated) - 1);
  }

  /**
   * In the case where Rescale Slope and Rescale Intercept are used for modality pixel
   * transformation, the output ranges may be signed even if Pixel Representation is unsigned.
   *
   * @param wl
   * @return
   */
  public boolean isModalityLutOutSigned(WlPresentation wl) {
    boolean signed = desc.isSigned();
    return getMinValue(wl) < 0 || signed;
  }

  /**
   * @return return the min value after modality pixel transformation and after pixel padding
   *     operation if padding exists.
   */
  public double getMinValue(WlPresentation wl) {
    return minMaxValue(true, wl);
  }

  /**
   * @return return the max value after modality pixel transformation and after pixel padding
   *     operation if padding exists.
   */
  public double getMaxValue(WlPresentation wl) {
    return minMaxValue(false, wl);
  }

  private double minMaxValue(boolean minVal, WlPresentation wl) {
    Number min = pixelToRealValue(minMax.minVal, wl);
    Number max = pixelToRealValue(minMax.maxVal, wl);
    if (min == null || max == null) {
      return 0;
    }
    // Computes min and max as slope can be negative
    if (minVal) {
      return Math.min(min.doubleValue(), max.doubleValue());
    }
    return Math.max(min.doubleValue(), max.doubleValue());
  }

  public double getRescaleIntercept(PrDicomObject dcm) {
    if (dcm != null) {
      OptionalDouble prIntercept = dcm.getModalityLutModule().getRescaleIntercept();
      if (prIntercept.isPresent()) {
        return prIntercept.getAsDouble();
      }
    }
    return desc.getModalityLUT().getRescaleIntercept().orElse(0.0);
  }

  public double getRescaleSlope(PrDicomObject dcm) {
    if (dcm != null) {
      OptionalDouble prSlope = dcm.getModalityLutModule().getRescaleSlope();
      if (prSlope.isPresent()) {
        return prSlope.getAsDouble();
      }
    }
    return desc.getModalityLUT().getRescaleSlope().orElse(1.0);
  }

  public double getFullDynamicWidth(WlPresentation wl) {
    return getMaxValue(wl) - getMinValue(wl);
  }

  public double getFullDynamicCenter(WlPresentation wl) {
    double minValue = getMinValue(wl);
    double maxValue = getMaxValue(wl);
    return minValue + (maxValue - minValue) / 2.f;
  }

  /**
   * @return default as first element of preset List <br>
   *     Note : null should never be returned since auto is at least one preset
   */
  public PresetWindowLevel getDefaultPreset(WlPresentation wlp) {
    List<PresetWindowLevel> presetList = getPresetList(wlp);
    return (presetList != null && !presetList.isEmpty()) ? presetList.get(0) : null;
  }

  public synchronized List<PresetWindowLevel> getPresetList(WlPresentation wl) {
    return getPresetList(wl, false);
  }

  public synchronized List<PresetWindowLevel> getPresetList(WlPresentation wl, boolean reload) {
    if (minMax != null && (windowingPresetCollection == null || reload)) {
      windowingPresetCollection = PresetWindowLevel.getPresetCollection(this, "[DICOM]", wl);
    }
    return windowingPresetCollection;
  }

  public int getPresetCollectionSize() {
    if (windowingPresetCollection == null) {
      return 0;
    }
    return windowingPresetCollection.size();
  }

  public LutShape getDefaultShape(WlPresentation wlp) {
    PresetWindowLevel defaultPreset = getDefaultPreset(wlp);
    return (defaultPreset != null) ? defaultPreset.getLutShape() : LutShape.LINEAR;
  }

  public double getDefaultWindow(WlPresentation wlp) {
    PresetWindowLevel defaultPreset = getDefaultPreset(wlp);
    return (defaultPreset != null)
        ? defaultPreset.getWindow()
        : minMax == null ? 0.0 : minMax.maxVal - minMax.minVal;
  }

  public double getDefaultLevel(WlPresentation wlp) {
    PresetWindowLevel defaultPreset = getDefaultPreset(wlp);
    if (defaultPreset != null) {
      return defaultPreset.getLevel();
    }
    if (minMax != null) {
      return minMax.minVal + (minMax.maxVal - minMax.minVal) / 2.0;
    }
    return 0.0f;
  }

  public Number pixelToRealValue(Number pixelValue, WlPresentation wlp) {
    if (pixelValue != null) {
      LookupTableCV lookup = getModalityLookup(wlp, false);
      if (lookup != null) {
        int val = pixelValue.intValue();
        if (val >= lookup.getOffset() && val < lookup.getOffset() + lookup.getNumEntries()) {
          return lookup.lookup(0, val);
        }
      }
    }
    return pixelValue;
  }

  /** DICOM PS 3.3 $C.11.1 Modality LUT Module */
  public LookupTableCV getModalityLookup(WlPresentation wlp, boolean inverseLUTAction) {
    Integer paddingValue = desc.getPixelPaddingValue();
    boolean pixelPadding = wlp == null || wlp.isPixelPadding();
    PrDicomObject pr =
        wlp != null && wlp.getPresentationState() instanceof PrDicomObject
            ? (PrDicomObject) wlp.getPresentationState()
            : null;
    LookupTableCV prModLut = (pr != null ? pr.getModalityLutModule().getLut().orElse(null) : null);
    final LookupTableCV mLUTSeq =
        prModLut == null ? desc.getModalityLUT().getLut().orElse(null) : prModLut;
    if (mLUTSeq != null) {
      if (!pixelPadding || paddingValue == null) {
        if (minMax.minVal >= mLUTSeq.getOffset()
            && minMax.maxVal < mLUTSeq.getOffset() + mLUTSeq.getNumEntries()) {
          return mLUTSeq;
        } else if (prModLut == null) {
          LOGGER.warn(
              "Pixel values doesn't match to Modality LUT sequence table. So the Modality LUT is not applied.");
        }
      } else {
        LOGGER.warn("Cannot apply Modality LUT sequence and Pixel Padding");
      }
    }

    boolean inverseLut = isPhotometricInterpretationInverse(pr);
    if (pixelPadding) {
      inverseLut ^= inverseLUTAction;
    }
    LutParameters lutparams = getLutParameters(pixelPadding, mLUTSeq, inverseLut, pr);
    // Not required to have a modality lookup table
    if (lutparams == null) {
      return null;
    }
    LookupTableCV modalityLookup = LUT_Cache.get(lutparams);

    if (modalityLookup != null) {
      return modalityLookup;
    }

    if (mLUTSeq != null) {
      if (mLUTSeq.getNumBands() == 1) {
        if (mLUTSeq.getDataType() == DataBuffer.TYPE_BYTE) {
          byte[] data = mLUTSeq.getByteData(0);
          if (data != null) {
            modalityLookup = new LookupTableCV(data, mLUTSeq.getOffset(0));
          }
        } else {
          short[] data = mLUTSeq.getShortData(0);
          if (data != null) {
            modalityLookup =
                new LookupTableCV(
                    data, mLUTSeq.getOffset(0), mLUTSeq.getData() instanceof DataBufferUShort);
          }
        }
      }
      if (modalityLookup == null) {
        modalityLookup = mLUTSeq;
      }
    } else {
      modalityLookup = DicomImageUtils.createRescaleRampLut(lutparams);
    }

    if (desc.getPhotometricInterpretation().isMonochrome()) {
      DicomImageUtils.applyPixelPaddingToModalityLUT(modalityLookup, lutparams);
    }
    LUT_Cache.put(lutparams, modalityLookup);
    return modalityLookup;
  }

  public boolean isPhotometricInterpretationInverse(PresentationStateLut pr) {
    Optional<String> prLUTShape = pr == null ? Optional.empty() : pr.getPrLutShapeMode();
    PhotometricInterpretation p = desc.getPhotometricInterpretation();
    if (prLUTShape.isPresent()) {
      return ("INVERSE".equals(prLUTShape.get()) && p == PhotometricInterpretation.MONOCHROME2)
          || ("IDENTITY".equals(prLUTShape.get()) && p == PhotometricInterpretation.MONOCHROME1);
    }
    return p == PhotometricInterpretation.MONOCHROME1;
  }

  public LutParameters getLutParameters(
      boolean pixelPadding, LookupTableCV mLUTSeq, boolean inversePaddingMLUT, PrDicomObject pr) {
    Integer paddingValue = desc.getPixelPaddingValue();

    boolean isSigned = desc.isSigned();
    double intercept = getRescaleIntercept(pr);
    double slope = getRescaleSlope(pr);

    // No need to have a modality lookup table
    if (bitsStored > 16
        || (MathUtil.isEqual(slope, 1.0)
            && MathUtil.isEqualToZero(intercept)
            && paddingValue == null)) {
      return null;
    }

    Integer paddingLimit = desc.getPixelPaddingRangeLimit();
    boolean outputSigned = false;
    int bitsOutputLut;
    if (mLUTSeq == null) {
      double minValue = minMax.minVal * slope + intercept;
      double maxValue = minMax.maxVal * slope + intercept;
      bitsOutputLut =
          Integer.SIZE - Integer.numberOfLeadingZeros((int) Math.round(maxValue - minValue));
      outputSigned = minValue < 0 || isSigned;
      if (outputSigned && bitsOutputLut <= 8) {
        // Allows to handle negative values with 8-bit image
        bitsOutputLut = 9;
      }
    } else {
      bitsOutputLut = mLUTSeq.getDataType() == DataBuffer.TYPE_BYTE ? 8 : 16;
    }
    return new LutParameters(
        intercept,
        slope,
        pixelPadding,
        paddingValue,
        paddingLimit,
        bitsStored,
        isSigned,
        outputSigned,
        bitsOutputLut,
        inversePaddingMLUT);
  }

  /**
   * @return 8 bits unsigned Lookup Table
   */
  public LookupTableCV getVOILookup(WlParams wl) {
    if (wl == null || wl.getLutShape() == null) {
      return null;
    }

    int minValue;
    int maxValue;
    /*
     * When pixel padding is activated, VOI LUT must extend to the min bit stored value when MONOCHROME2 and to the
     * max bit stored value when MONOCHROME1. See C.7.5.1.1.2
     */
    if (wl.isFillOutsideLutRange()
        || (desc.getPixelPaddingValue() != null
            && desc.getPhotometricInterpretation().isMonochrome())) {
      minValue = getMinAllocatedValue(wl);
      maxValue = getMaxAllocatedValue(wl);
    } else {
      minValue = (int) wl.getLevelMin();
      maxValue = (int) wl.getLevelMax();
    }

    return DicomImageUtils.createVoiLut(
        wl.getLutShape(),
        wl.getWindow(),
        wl.getLevel(),
        minValue,
        maxValue,
        8,
        false,
        isPhotometricInterpretationInverse(wl.getPresentationState()));
  }
}
