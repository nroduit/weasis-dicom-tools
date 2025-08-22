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
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DicomAttributeUtils;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.img.util.LookupTableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.annotations.Generated;
import org.weasis.opencv.data.LookupTableCV;

/**
 * Immutable Modality LUT Module for DICOM pixel value transformations.
 *
 * <p>According to DICOM standard, either a Modality LUT Sequence containing a single Item or
 * Rescale Slope and Intercept values shall be present but not both. This implementation only
 * applies a warning in such cases.
 *
 * @see <a href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.11.html">
 *     C.11.1 Modality LUT Module</a>
 * @author Nicolas Roduit
 */
public final class ModalityLutModule {
  private static final Logger LOGGER = LoggerFactory.getLogger(ModalityLutModule.class);

  // Modalities with special pixel intensity handling
  private static final Set<String> SPECIAL_MODALITIES = Set.of("XA", "XRF");

  // Pixel intensity relationships that prevent LUT application
  private static final Set<String> RESTRICTED_PIXEL_INTENSITIES = Set.of("LOG", "DISP");

  // Default values for rescale parameters
  private static final String DEFAULT_RESCALE_TYPE = "US";
  private static final double DEFAULT_RESCALE_INTERCEPT = 0.0;
  private static final double DEFAULT_RESCALE_SLOPE = 1.0;

  private final boolean overlayBitMaskApplied;
  private final Double rescaleSlope;
  private final Double rescaleIntercept;
  private final String rescaleType;
  private final String lutType;
  private final String lutExplanation;
  private final LookupTableCV lut;

  /**
   * Creates a new ModalityLutModule from DICOM attributes.
   *
   * @param dcm DICOM attributes, must not be null
   * @throws NullPointerException if dcm is null
   */
  public ModalityLutModule(Attributes dcm) {
    Objects.requireNonNull(dcm, "DICOM attributes cannot be null");
    final var initialization = initializeFromDicom(dcm);

    this.overlayBitMaskApplied = false;
    this.rescaleSlope = initialization.rescaleSlope();
    this.rescaleIntercept = initialization.rescaleIntercept();
    this.rescaleType = initialization.rescaleType();
    this.lutType = initialization.lutData.lutType();
    this.lutExplanation = initialization.lutData.lutExplanation();
    this.lut = initialization.lutData.lut();
  }

  /** Private constructor for creating instances with overlay bit mask applied. */
  private ModalityLutModule(
      final boolean overlayBitMaskApplied,
      final Double rescaleSlope,
      final Double rescaleIntercept,
      final String rescaleType,
      final String lutType,
      final String lutExplanation,
      final LookupTableCV lut) {

    this.overlayBitMaskApplied = overlayBitMaskApplied;
    this.rescaleSlope = rescaleSlope;
    this.rescaleIntercept = rescaleIntercept;
    this.rescaleType = rescaleType;
    this.lutType = lutType;
    this.lutExplanation = lutExplanation;
    this.lut = lut;
  }

  private InitializationData initializeFromDicom(final Attributes dcm) {
    final String modality = DicomAttributeUtils.getModality(dcm);

    // Initialize rescale values
    final Double slope = DicomUtils.getDoubleFromDicomElement(dcm, Tag.RescaleSlope, null);
    final Double intercept = DicomUtils.getDoubleFromDicomElement(dcm, Tag.RescaleIntercept, null);
    final String type = dcm.getString(Tag.RescaleType);

    // Initialize LUT if present and applicable
    final var lutData = initializeLutIfApplicable(dcm, modality);

    // Log warnings for DICOM compliance issues
    logComplianceWarnings(intercept, lutData.lut());

    return new InitializationData(slope, intercept, type, lutData);
  }

  private LutData initializeLutIfApplicable(final Attributes dcm, final String modality) {
    final var lutAttributes = dcm.getNestedDataset(Tag.ModalityLUTSequence);

    if (isValidLutAttributes(lutAttributes) && canApplyModalityLUT(dcm, modality)) {
      final String type = lutAttributes.getString(Tag.ModalityLUTType);
      final String explanation = lutAttributes.getString(Tag.LUTExplanation);
      final LookupTableCV lookupTable = LookupTableUtils.createLut(lutAttributes).orElse(null);
      return new LutData(type, explanation, lookupTable);
    }
    return new LutData(null, null, null);
  }

  private boolean isValidLutAttributes(final Attributes lutAttributes) {
    return lutAttributes != null
        && lutAttributes.containsValue(Tag.ModalityLUTType)
        && lutAttributes.containsValue(Tag.LUTDescriptor)
        && lutAttributes.containsValue(Tag.LUTData);
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
  private boolean canApplyModalityLUT(final Attributes dcm, final String modality) {
    if (StringUtil.hasText(modality) && !SPECIAL_MODALITIES.contains(modality)) {
      return true;
    }
    final String pixelIntensityRelationship = dcm.getString(Tag.PixelIntensityRelationship);
    return pixelIntensityRelationship == null
        || !RESTRICTED_PIXEL_INTENSITIES.contains(pixelIntensityRelationship.toUpperCase());
  }

  private void logComplianceWarnings(final Double intercept, final LookupTableCV lutTable) {
    if (intercept != null && lutTable != null) {
      LOGGER.warn(
          "Either a Modality LUT Sequence or Rescale Slope and Intercept values shall be present but not both!");
    }
    logTraceInfo(intercept, lutTable);
  }

  @Generated
  private void logTraceInfo(final Double intercept, final LookupTableCV lutTable) {
    if (LOGGER.isTraceEnabled()) {
      if (lutTable != null) {
        if (intercept != null) {
          LOGGER.trace(
              "Modality LUT Sequence shall NOT be present if Rescale Intercept is present");
        }
        if (lutType == null) {
          LOGGER.trace("Modality Type is required if Modality LUT Sequence is present.");
        }
      } else if (intercept != null && rescaleSlope == null) {
        LOGGER.trace("Modality Rescale Slope is required if Rescale Intercept is present.");
      }
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
   * Creates a new ModalityLutModule with overlay bit mask adaptation applied. This method adjusts
   * the pixel values by dividing by 2^shiftHighBit to remove high bits. If overlay bit mask has
   * already been applied, returns the current instance unchanged.
   *
   * @param shiftHighBit The number of high bits to shift/remove
   * @return A new ModalityLutModule instance with overlay bit mask applied, or this instance if
   *     already applied
   */
  public ModalityLutModule withOverlayBitMask(final int shiftHighBit) {
    if (overlayBitMaskApplied) {
      return this;
    }

    final double baseSlope = getRescaleSlope().orElse(DEFAULT_RESCALE_SLOPE);
    final double adjustedSlope = baseSlope / (1 << shiftHighBit);

    final var defaults = calculateDefaults();

    return new ModalityLutModule(
        true,
        adjustedSlope,
        defaults.intercept(),
        defaults.type(),
        this.lutType,
        this.lutExplanation,
        this.lut);
  }

  private DefaultValues calculateDefaults() {
    final Double finalIntercept =
        rescaleSlope == null && rescaleIntercept == null
            ? DEFAULT_RESCALE_INTERCEPT
            : rescaleIntercept;

    final String finalType =
        rescaleSlope == null && rescaleType == null ? DEFAULT_RESCALE_TYPE : rescaleType;

    return new DefaultValues(finalIntercept, finalType);
  }

  /**
   * @return true if overlay bit mask adaptation has been applied
   */
  public boolean isOverlayBitMaskApplied() {
    return overlayBitMaskApplied;
  }

  /** Record for holding initialization data during construction. */
  private record InitializationData(
      Double rescaleSlope, Double rescaleIntercept, String rescaleType, LutData lutData) {}

  /** Record for holding LUT-specific data. */
  private record LutData(String lutType, String lutExplanation, LookupTableCV lut) {}

  /** Record for holding default values during overlay bit mask calculation. */
  private record DefaultValues(Double intercept, String type) {}
}
