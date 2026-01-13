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
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.data.PrDicomObject;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.WlParams;

/**
 * Immutable implementation of window/level parameters for DICOM image display.
 *
 * <p>This class encapsulates window/level transformation parameters for DICOM image processing:
 *
 * <ul>
 *   <li>Window width and center (level) values
 *   <li>Level minimum and maximum bounds
 *   <li>Display options and flags
 *   <li>LUT shape and presentation state information
 * </ul>
 *
 * <p>Window/level parameters control pixel intensity mapping to display values, affecting image
 * brightness and contrast. The window defines the range of pixel values to display, while the level
 * defines the center of that range.
 *
 * @author Nicolas Roduit
 * @see WlParams
 * @see DicomImageAdapter
 * @see DicomImageReadParam
 */
public final class WindLevelParameters implements WlParams {

  private final double window;
  private final double level;
  private final double levelMin;
  private final double levelMax;
  private final boolean pixelPadding;
  private final boolean inverseLut;
  private final boolean fillOutsideLutRange;
  private final boolean allowWinLevelOnColorImage;
  private final LutShape lutShape;
  private final PrDicomObject dcmPR;

  /**
   * Creates window/level parameters using default values from the DICOM image adapter.
   *
   * <p>Default boolean flags: pixelPadding=true, inverseLut=false, fillOutsideLutRange=false,
   * allowWinLevelOnColorImage=false
   *
   * @param adapter the DICOM image adapter to extract default values from
   * @throws NullPointerException if adapter is null
   */
  public WindLevelParameters(DicomImageAdapter adapter) {
    this(adapter, null);
  }

  /**
   * Creates window/level parameters using values from the adapter and optional parameters.
   *
   * <p>Parameter values are extracted from DicomImageReadParam with fallback to adapter defaults
   * when not specified. Level bounds are calculated as min/max of specified values and adapter
   * bounds.
   *
   * @param adapter the DICOM image adapter to extract default values from
   * @param params optional parameters to override defaults, may be null
   * @throws NullPointerException if adapter is null
   */
  public WindLevelParameters(DicomImageAdapter adapter, DicomImageReadParam params) {
    Objects.requireNonNull(adapter, "DicomImageAdapter cannot be null");

    // Extract basic parameters
    this.dcmPR = params != null ? params.getPresentationState().orElse(null) : null;
    this.pixelPadding =
        extractBooleanParam(params, DicomImageReadParam::getApplyPixelPadding, true);

    // Create presentation with extracted parameters
    var def = new DefaultWlPresentation(dcmPR, pixelPadding);

    // Extract remaining boolean parameters
    this.fillOutsideLutRange =
        extractBooleanParam(params, DicomImageReadParam::getFillOutsideLutRange, false);
    this.allowWinLevelOnColorImage =
        extractBooleanParam(params, DicomImageReadParam::getApplyWindowLevelToColorImage, false);
    this.inverseLut = extractBooleanParam(params, DicomImageReadParam::getInverseLut, false);

    // Extract window/level parameters
    this.window =
        validateAndExtractDoubleParam(
            params,
            DicomImageReadParam::getWindowWidth,
            () -> adapter.getDefaultWindow(def),
            1.0,
            false);
    this.level =
        validateAndExtractDoubleParam(
            params,
            DicomImageReadParam::getWindowCenter,
            () -> adapter.getDefaultLevel(def),
            0.0,
            true);
    this.lutShape =
        extractParam(
            params, DicomImageReadParam::getVoiLutShape, () -> adapter.getDefaultShape(def));

    // Calculate level bounds
    double halfWindow = window / 2.0;
    double calculatedMin = level - halfWindow;
    double calculatedMax = level + halfWindow;

    double paramMin = params != null ? params.getLevelMin().orElse(calculatedMin) : calculatedMin;
    double paramMax = params != null ? params.getLevelMax().orElse(calculatedMax) : calculatedMax;

    this.levelMin = validateDouble(Math.min(paramMin, adapter.getMinValue(def)), 0.0, true);
    this.levelMax = validateDouble(Math.max(paramMax, adapter.getMaxValue(def)), 255.0, true);
  }

  private double validateAndExtractDoubleParam(
      DicomImageReadParam params,
      Function<DicomImageReadParam, OptionalDouble> extractor,
      DoubleSupplier defaultSupplier,
      double defaultValue,
      boolean allowNegative) {
    double value;
    if (params != null) {
      var optional = extractor.apply(params);
      value = optional.isPresent() ? optional.getAsDouble() : defaultSupplier.getAsDouble();
    } else {
      value = defaultSupplier.getAsDouble();
    }

    return validateDouble(value, defaultValue, allowNegative);
  }

  private double validateDouble(double value, double fallback, boolean allowNegative) {
    if (!Double.isFinite(value)) {
      return fallback;
    }
    if (!allowNegative && value <= MathUtil.DOUBLE_EPSILON) {
      return fallback;
    }
    return value;
  }

  private boolean extractBooleanParam(
      DicomImageReadParam params,
      Function<DicomImageReadParam, Optional<Boolean>> extractor,
      boolean defaultValue) {
    return params != null ? extractor.apply(params).orElse(defaultValue) : defaultValue;
  }

  private <T> T extractParam(
      DicomImageReadParam params,
      Function<DicomImageReadParam, Optional<T>> extractor,
      Supplier<T> defaultSupplier) {
    return params != null
        ? extractor.apply(params).orElseGet(defaultSupplier)
        : defaultSupplier.get();
  }

  @Override
  public double getWindow() {
    return window;
  }

  @Override
  public double getLevel() {
    return level;
  }

  @Override
  public double getLevelMin() {
    return levelMin;
  }

  @Override
  public double getLevelMax() {
    return levelMax;
  }

  @Override
  public boolean isPixelPadding() {
    return pixelPadding;
  }

  @Override
  public boolean isInverseLut() {
    return inverseLut;
  }

  @Override
  public boolean isFillOutsideLutRange() {
    return fillOutsideLutRange;
  }

  @Override
  public boolean isAllowWinLevelOnColorImage() {
    return allowWinLevelOnColorImage;
  }

  @Override
  public LutShape getLutShape() {
    return lutShape;
  }

  @Override
  public PrDicomObject getPresentationState() {
    return dcmPR;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof WindLevelParameters other
        && Double.compare(window, other.window) == 0
        && Double.compare(level, other.level) == 0
        && Double.compare(levelMin, other.levelMin) == 0
        && Double.compare(levelMax, other.levelMax) == 0
        && pixelPadding == other.pixelPadding
        && inverseLut == other.inverseLut
        && fillOutsideLutRange == other.fillOutsideLutRange
        && allowWinLevelOnColorImage == other.allowWinLevelOnColorImage
        && Objects.equals(lutShape, other.lutShape)
        && Objects.equals(dcmPR, other.dcmPR);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        window,
        level,
        levelMin,
        levelMax,
        pixelPadding,
        inverseLut,
        fillOutsideLutRange,
        allowWinLevelOnColorImage,
        lutShape,
        dcmPR);
  }

  @Override
  public String toString() {
    return "WindLevelParameters{window=%.2f, level=%.2f, levelMin=%.2f, levelMax=%.2f, pixelPadding=%s, inverseLut=%s, fillOutsideLutRange=%s, allowWinLevelOnColorImage=%s, lutShape=%s, dcmPR=%s}"
        .formatted(
            window,
            level,
            levelMin,
            levelMax,
            pixelPadding,
            inverseLut,
            fillOutsideLutRange,
            allowWinLevelOnColorImage,
            lutShape,
            dcmPR);
  }
}
