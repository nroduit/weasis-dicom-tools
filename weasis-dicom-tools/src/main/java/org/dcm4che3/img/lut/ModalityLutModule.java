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
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.annotations.Generated;
import org.weasis.opencv.data.LookupTableCV;

/**
 * Modality LUT Module for DICOM pixel value transformations.
 *
 * <p>According to DICOM standard, either a Modality LUT Sequence containing a single Item or
 * Rescale Slope and Intercept values shall be present but not both. This implementation only
 * applies a warning in such cases.
 *
 * @see <a href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.11.html">
 *     C.11.1 Modality LUT Module</a>
 * @author Nicolas Roduit
 */
public class ModalityLutModule {
  private static final Logger LOGGER = LoggerFactory.getLogger(ModalityLutModule.class);

  // Constants for modalities that require special handling
  private static final String MODALITY_XA = "XA";
  private static final String MODALITY_XRF = "XRF";
  private static final String PIXEL_INTENSITY_LOG = "LOG";
  private static final String PIXEL_INTENSITY_DISP = "DISP";
  private static final String DEFAULT_RESCALE_TYPE = "US";
  private static final double DEFAULT_RESCALE_INTERCEPT = 0.0;
  private static final double DEFAULT_RESCALE_SLOPE = 1.0;

  private boolean overlayBitMaskApplied = false;
  private Double rescaleSlope;
  private Double rescaleIntercept;
  private String rescaleType;
  private String lutType;
  private String lutExplanation;
  private LookupTableCV lut;

  /**
   * Creates a new ModalityLutModule from DICOM attributes.
   *
   * @param dcm DICOM attributes, must not be null
   * @throws NullPointerException if dcm is null
   */
  public ModalityLutModule(Attributes dcm) {
    initializeFields();
    init(Objects.requireNonNull(dcm));
  }

  /** Initializes all fields to null/default values. */
  private void initializeFields() {
    this.rescaleSlope = null;
    this.rescaleIntercept = null;
    this.rescaleType = null;
    this.lutType = null;
    this.lutExplanation = null;
    this.lut = null;
  }

  /** Main initialization method that processes DICOM attributes. */
  private void init(Attributes dcm) {
    String modality = DicomImageUtils.getModality(dcm);
    initRescaleValues(dcm);
    initModalityLUTSequence(dcm, modality);
    logModalityLutConsistency();
  }

  /** Initializes rescale slope, intercept and type from DICOM attributes. */
  private void initRescaleValues(Attributes dcm) {
    this.rescaleSlope = DicomUtils.getDoubleFromDicomElement(dcm, Tag.RescaleSlope, null);
    this.rescaleIntercept = DicomUtils.getDoubleFromDicomElement(dcm, Tag.RescaleIntercept, null);
    this.rescaleType = dcm.getString(Tag.RescaleType);
  }

  /** Initializes the Modality LUT Sequence if present and valid. */
  private void initModalityLUTSequence(Attributes dcm, String modality) {
    Attributes dcmLut = dcm.getNestedDataset(Tag.ModalityLUTSequence);
    if (isValidModalityLUT(dcmLut)) {
      processModalityLUT(dcm, modality, dcmLut);
    }
  }

  /** Validates if the Modality LUT contains all required fields. */
  private boolean isValidModalityLUT(Attributes dcmLut) {
    return dcmLut != null
        && dcmLut.containsValue(Tag.ModalityLUTType)
        && dcmLut.containsValue(Tag.LUTDescriptor)
        && dcmLut.containsValue(Tag.LUTData);
  }

  /** Processes the Modality LUT if it can be applied based on modality constraints. */
  private void processModalityLUT(Attributes dcm, String modality, Attributes dcmLut) {
    if (canApplyModalityLUT(dcm, modality)) {
      applyModalityLUT(dcmLut);
    }
  }

  /**
   * Determines if Modality LUT can be applied based on modality and pixel intensity relationship.
   *
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/html/part04.html#figure_N.2-1">
   *     DICOM Part 4 Figure N.2-1</a>
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/html/part03.html#sect_C.8.7.1.1.2">
   *     DICOM Part 3 Section C.8.7.1.1.2</a>
   */
  private boolean canApplyModalityLUT(Attributes dcm, String modality) {
    if (MODALITY_XA.equals(modality) || MODALITY_XRF.equals(modality)) {
      String pixelIntensityRelationship = dcm.getString(Tag.PixelIntensityRelationship);
      return !isLogOrDispPixelIntensity(pixelIntensityRelationship);
    }
    return true;
  }

  private boolean isLogOrDispPixelIntensity(String pixelIntensityRelationship) {
    return PIXEL_INTENSITY_LOG.equalsIgnoreCase(pixelIntensityRelationship)
        || PIXEL_INTENSITY_DISP.equalsIgnoreCase(pixelIntensityRelationship);
  }

  private void applyModalityLUT(Attributes dcmLut) {
    this.lutType = dcmLut.getString(Tag.ModalityLUTType);
    this.lutExplanation = dcmLut.getString(Tag.LUTExplanation);
    this.lut = DicomImageUtils.createLut(dcmLut).orElse(null);
  }

  /** Logs consistency warnings and debug information about the Modality LUT configuration. */
  private void logModalityLutConsistency() {
    logMutualExclusivityWarning();
    logDebugInformation();
  }

  /** Logs a warning if both rescale values and LUT are present (mutual exclusivity violation). */
  private void logMutualExclusivityWarning() {
    if (rescaleIntercept != null && lut != null) {
      LOGGER.warn(
          "Either a Modality LUT Sequence or Rescale Slope and Intercept values shall be present but not both!");
    }
  }

  @Generated
  private void logDebugInformation() {
    if (!LOGGER.isTraceEnabled()) {
      return;
    }
    if (lut != null) {
      logLutDebugInfo();
    } else if (rescaleIntercept != null && rescaleSlope == null) {
      LOGGER.trace("Modality Rescale Slope is required if Rescale Intercept is present.");
    }
  }

  /** Logs debug information specific to LUT configuration. */
  @Generated
  private void logLutDebugInfo() {
    if (rescaleIntercept != null) {
      LOGGER.trace("Modality LUT Sequence shall NOT be present if Rescale Intercept is present");
    }
    if (lutType == null) {
      LOGGER.trace("Modality Type is required if Modality LUT Sequence is present.");
    }
  }

  /**
   * @return The rescale slope value, or empty if not present
   */
  public OptionalDouble getRescaleSlope() {
    return LangUtil.toOptional(rescaleSlope);
  }

  /**
   * @return The rescale intercept value, or empty if not present
   */
  public OptionalDouble getRescaleIntercept() {
    return LangUtil.toOptional(rescaleIntercept);
  }

  /**
   * @return The rescale type, or empty if not present
   */
  public Optional<String> getRescaleType() {
    return Optional.ofNullable(rescaleType);
  }

  /**
   * @return The LUT type, or empty if not present
   */
  public Optional<String> getLutType() {
    return Optional.ofNullable(lutType);
  }

  /**
   * @return The LUT explanation, or empty if not present
   */
  public Optional<String> getLutExplanation() {
    return Optional.ofNullable(lutExplanation);
  }

  /**
   * @return The lookup table, or empty if not present
   */
  public Optional<LookupTableCV> getLut() {
    return Optional.ofNullable(lut);
  }

  /**
   * Adapts the rescale slope to account for overlay bit mask shifting. This method adjusts the
   * pixel values by dividing by 2^shiftHighBit to remove high bits. Can only be applied once to
   * prevent multiple adjustments.
   *
   * @param shiftHighBit The number of high bits to shift/remove
   */
  public void adaptWithOverlayBitMask(int shiftHighBit) {
    if (overlayBitMaskApplied) {
      return;
    }

    double adjustedSlope = calculateAdjustedSlope(shiftHighBit);
    ensureValidModalityLutValues();
    this.rescaleSlope = adjustedSlope;
    this.overlayBitMaskApplied = true; // Mark as applied
  }

  /**
   * Returns whether overlay bit mask adaptation has been applied.
   *
   * @return true if adaptation has been applied, false otherwise
   */
  public boolean isOverlayBitMaskApplied() {
    return overlayBitMaskApplied;
  }

  /** Calculates the adjusted slope value based on the current slope and bit shift. */
  private double calculateAdjustedSlope(int shiftHighBit) {
    double baseSlope = getRescaleSlope().orElse(DEFAULT_RESCALE_SLOPE);
    return baseSlope / (1 << shiftHighBit);
  }

  /**
   * Ensures that rescale intercept and type have valid default values when rescale slope is not
   * set.
   */
  private void ensureValidModalityLutValues() {
    if (rescaleSlope == null) {
      if (rescaleIntercept == null) {
        rescaleIntercept = DEFAULT_RESCALE_INTERCEPT;
      }
      if (rescaleType == null) {
        rescaleType = DEFAULT_RESCALE_TYPE;
      }
    }
  }
}
