/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.lut;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.DicomImageUtils;
import org.dcm4che3.img.util.DicomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.LookupTableCV;

/**
 * @author Nicolas Roduit
 */
public class ModalityLutModule {
  private static final Logger LOGGER = LoggerFactory.getLogger(ModalityLutModule.class);

  private OptionalDouble rescaleSlope;
  private OptionalDouble rescaleIntercept;
  private Optional<String> rescaleType;
  private Optional<String> lutType;
  private Optional<String> lutExplanation;
  private Optional<LookupTableCV> lut;

  /**
   * Modality LUT Module
   *
   * <p>Note: Either a Modality LUT Sequence containing a single Item or Rescale Slope and Intercept
   * values shall be present but not both. This implementation only applies a warning in such a
   * case.
   *
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.11.html">C.11.1
   *     Modality LUT Module</a>
   */
  public ModalityLutModule(Attributes dcm) {
    this.rescaleSlope = OptionalDouble.empty();
    this.rescaleIntercept = OptionalDouble.empty();
    this.rescaleType = Optional.empty();
    this.lutType = Optional.empty();
    this.lutExplanation = Optional.empty();
    this.lut = Optional.empty();
    init(Objects.requireNonNull(dcm));
  }

  private void init(Attributes dcm) {
    String modality = DicomImageUtils.getModality(dcm);
    if (dcm.containsValue(Tag.RescaleIntercept) && dcm.containsValue(Tag.RescaleSlope)) {
      if ("MR".equals(modality)
          //          || "PT".equals(modality) // bug https://github.com/nroduit/Weasis/issues/399
          || "XA".equals(modality)
          || "XRF".equals(modality)) {
        // IHE BIR: Windowing and Rendering 4.16.4.2.2.5.4
        LOGGER.trace("Do not apply RescaleSlope and RescaleIntercept to {}", modality);
      } else {
        this.rescaleSlope =
            OptionalDouble.of(DicomUtils.getDoubleFromDicomElement(dcm, Tag.RescaleSlope, null));
        this.rescaleIntercept =
            OptionalDouble.of(
                DicomUtils.getDoubleFromDicomElement(dcm, Tag.RescaleIntercept, null));
        this.rescaleType = Optional.ofNullable(dcm.getString(Tag.RescaleType));
      }
    }

    initModalityLUTSequence(dcm, modality);

    if (rescaleIntercept.isPresent() && lut.isPresent()) {
      LOGGER.warn(
          "Either a Modality LUT Sequence or Rescale Slope and Intercept values shall be present but not both!");
    }

    logModalityLutConsistency();
  }

  private void initModalityLUTSequence(Attributes dcm, String modality) {
    Attributes dcmLut = dcm.getNestedDataset(Tag.ModalityLUTSequence);
    if (dcmLut != null) {
      if (dcmLut.containsValue(Tag.ModalityLUTType)
          && dcmLut.containsValue(Tag.LUTDescriptor)
          && dcmLut.containsValue(Tag.LUTData)) {
        boolean canApplyMLUT = true;

        // See http://dicom.nema.org/medical/dicom/current/output/html/part04.html#figure_N.2-1 and
        // http://dicom.nema.org/medical/dicom/current/output/html/part03.html#sect_C.8.7.1.1.2
        if ("XA".equals(modality) || "XRF".equals(modality)) {
          String pixRel = dcm.getString(Tag.PixelIntensityRelationship);
          if (("LOG".equalsIgnoreCase(pixRel) || "DISP".equalsIgnoreCase(pixRel))) {
            canApplyMLUT = false;
          }
        }
        if (canApplyMLUT) {
          this.lutType = Optional.ofNullable(dcmLut.getString(Tag.ModalityLUTType));
          this.lutExplanation = Optional.ofNullable(dcmLut.getString(Tag.LUTExplanation));
          this.lut = DicomImageUtils.createLut(dcmLut);
        }
      }
    }
  }

  private void logModalityLutConsistency() {
    if (LOGGER.isTraceEnabled()) {
      if (lut.isPresent()) {
        if (rescaleIntercept.isPresent()) {
          LOGGER.trace(
              "Modality LUT Sequence shall NOT be present if Rescale Intercept is present");
        }
        if (!lutType.isPresent()) {
          LOGGER.trace("Modality Type is required if Modality LUT Sequence is present.");
        }
      } else if (rescaleIntercept.isPresent() && !rescaleSlope.isPresent()) {
        LOGGER.trace("Modality Rescale Slope is required if Rescale Intercept is present.");
      }
    }
  }

  public OptionalDouble getRescaleSlope() {
    return rescaleSlope;
  }

  public OptionalDouble getRescaleIntercept() {
    return rescaleIntercept;
  }

  public Optional<String> getRescaleType() {
    return rescaleType;
  }

  public Optional<String> getLutType() {
    return lutType;
  }

  public Optional<String> getLutExplanation() {
    return lutExplanation;
  }

  public Optional<LookupTableCV> getLut() {
    return lut;
  }

  public void adaptWithOverlayBitMask(int shiftHighBit) {
    // Combine to the slope value
    double rs = 1.0;
    if (!rescaleSlope.isPresent()) {
      // Set valid modality LUT values
      if (!rescaleIntercept.isPresent()) {
        rescaleIntercept = OptionalDouble.of(0.0);
      }
      if (!rescaleType.isPresent()) {
        rescaleType = Optional.of("US");
      }
    }
    // Divide pixel value by (2 ^ rightBit) => remove right bits
    rs /= 1 << shiftHighBit;
    this.rescaleSlope = OptionalDouble.of(rs);
  }
}
