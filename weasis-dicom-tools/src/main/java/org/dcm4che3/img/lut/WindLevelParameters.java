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
import java.util.function.Function;
import java.util.function.Supplier;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.data.PrDicomObject;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.WlParams;

/**
 * Immutable implementation of window/level parameters for DICOM image display.
 *
 * <p>This class encapsulates all the parameters needed for window/level transformations in DICOM
 * image processing, including:
 *
 * <ul>
 *   <li>Window width and center (level) values
 *   <li>Level minimum and maximum bounds
 *   <li>Various display options and flags
 *   <li>LUT shape and presentation state information
 * </ul>
 *
 * <p>Window/level parameters control how pixel intensity values are mapped to display values,
 * affecting image brightness and contrast. The window defines the range of pixel values to display,
 * while the level defines the center of that range.
 *
 * <p>This class supports both default parameter extraction from a DICOM image adapter and custom
 * parameter specification through {@link DicomImageReadParam}.
 *
 * @author Nicolas Roduit
 * @see WlParams
 * @see DicomImageAdapter
 * @see DicomImageReadParam
 */
public class WindLevelParameters implements WlParams {

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
   * <p>This constructor uses default values for all boolean flags:
   *
   * <ul>
   *   <li>pixelPadding: true
   *   <li>inverseLut: false
   *   <li>fillOutsideLutRange: false
   *   <li>allowWinLevelOnColorImage: false
   * </ul>
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
   * <p>If params is null, default values are used as in the single-argument constructor. Otherwise,
   * parameter values are extracted from the DicomImageReadParam with fallback to adapter defaults
   * when specific parameters are not specified.
   *
   * <p>The level minimum and maximum are calculated as:
   *
   * <ul>
   *   <li>levelMin = min(specified_or_calculated_min, adapter_min_value)
   *   <li>levelMax = max(specified_or_calculated_max, adapter_max_value)
   * </ul>
   *
   * where calculated values default to level ± window/2.0.
   *
   * @param adapter the DICOM image adapter to extract default values from
   * @param params optional parameters to override defaults, may be null
   * @throws NullPointerException if adapter is null
   */
  public WindLevelParameters(DicomImageAdapter adapter, DicomImageReadParam params) {
    Objects.requireNonNull(adapter);
    this.dcmPR = extractPresentationState(params);
    this.fillOutsideLutRange =
        extractBooleanParam(params, DicomImageReadParam::getFillOutsideLutRange, false);
    this.allowWinLevelOnColorImage =
        extractBooleanParam(params, DicomImageReadParam::getApplyWindowLevelToColorImage, false);
    this.pixelPadding =
        extractBooleanParam(params, DicomImageReadParam::getApplyPixelPadding, true);
    this.inverseLut = extractBooleanParam(params, DicomImageReadParam::getInverseLut, false);
    DefaultWlPresentation def = new DefaultWlPresentation(dcmPR, pixelPadding);
    this.window =
        extractOptionalDoubleParam(
            params, DicomImageReadParam::getWindowWidth, () -> adapter.getDefaultWindow(def));
    this.level =
        extractOptionalDoubleParam(
            params, DicomImageReadParam::getWindowCenter, () -> adapter.getDefaultLevel(def));
    this.lutShape = extractLutShapeParam(params, () -> adapter.getDefaultShape(def));

    this.levelMin = calculateLevelMin(params, adapter, def);
    this.levelMax = calculateLevelMax(params, adapter, def);
  }

  /** Extracts the presentation state from parameters or returns null if not specified. */
  private PrDicomObject extractPresentationState(DicomImageReadParam params) {
    return params != null ? params.getPresentationState().orElse(null) : null;
  }

  /** Extracts a boolean parameter with fallback to default value. */
  private boolean extractBooleanParam(
      DicomImageReadParam params,
      Function<DicomImageReadParam, Optional<Boolean>> extractor,
      boolean defaultValue) {
    return params != null ? extractor.apply(params).orElse(defaultValue) : defaultValue;
  }

  /** Extracts an OptionalDouble parameter with fallback to supplier when empty or null. */
  private double extractOptionalDoubleParam(
      DicomImageReadParam params,
      Function<DicomImageReadParam, OptionalDouble> extractor,
      Supplier<Double> defaultSupplier) {
    if (params != null) {
      OptionalDouble optional = extractor.apply(params);
      return optional.isPresent() ? optional.getAsDouble() : defaultSupplier.get();
    }
    return defaultSupplier.get();
  }

  /** Extracts a LUT shape parameter with fallback to supplier. */
  private LutShape extractLutShapeParam(
      DicomImageReadParam params, Supplier<LutShape> defaultSupplier) {
    return params != null
        ? params.getVoiLutShape().orElseGet(defaultSupplier)
        : defaultSupplier.get();
  }

  /** Calculates the minimum level value using the minimum of parameter and adapter values. */
  private double calculateLevelMin(
      DicomImageReadParam params, DicomImageAdapter adapter, DefaultWlPresentation def) {
    double defaultMin = level - window / 2.0;
    double paramMin = params != null ? params.getLevelMin().orElse(defaultMin) : defaultMin;
    return Math.min(paramMin, adapter.getMinValue(def));
  }

  /** Calculates the maximum level value using the maximum of parameter and adapter values. */
  private double calculateLevelMax(
      DicomImageReadParam params, DicomImageAdapter adapter, DefaultWlPresentation def) {
    double defaultMax = level + window / 2.0;
    double paramMax = params != null ? params.getLevelMax().orElse(defaultMax) : defaultMax;
    return Math.max(paramMax, adapter.getMaxValue(def));
  }

  /**
   * Returns the window width value.
   *
   * <p>The window width defines the range of pixel values that will be displayed. Values outside
   * this range will be clamped to the minimum or maximum display values.
   *
   * @return the window width
   */
  @Override
  public double getWindow() {
    return window;
  }

  /**
   * Returns the window center (level) value.
   *
   * <p>The level defines the center of the window range. Combined with the window width, it
   * determines which pixel values map to the middle of the display range.
   *
   * @return the window center (level)
   */
  @Override
  public double getLevel() {
    return level;
  }

  /**
   * Returns the minimum level value for clamping.
   *
   * @return the minimum level value
   */
  @Override
  public double getLevelMin() {
    return levelMin;
  }

  /**
   * Returns the maximum level value for clamping.
   *
   * @return the maximum level value
   */
  @Override
  public double getLevelMax() {
    return levelMax;
  }

  /**
   * Returns whether pixel padding should be applied during processing.
   *
   * <p>When true, pixels with the padding value (if specified in DICOM tags) will be handled
   * specially during window/level processing.
   *
   * @return true if pixel padding should be applied
   */
  @Override
  public boolean isPixelPadding() {
    return pixelPadding;
  }

  /**
   * Returns whether the lookup table should be inverted.
   *
   * <p>When true, the output values of the window/level transformation are inverted, effectively
   * creating a negative image effect.
   *
   * @return true if the LUT should be inverted
   */
  @Override
  public boolean isInverseLut() {
    return inverseLut;
  }

  /**
   * Returns whether to fill values outside the LUT range.
   *
   * <p>When true, pixel values outside the defined LUT range will be filled with appropriate values
   * rather than being clamped.
   *
   * @return true if values outside LUT range should be filled
   */
  @Override
  public boolean isFillOutsideLutRange() {
    return fillOutsideLutRange;
  }

  /**
   * Returns whether window/level operations are allowed on color images.
   *
   * <p>Typically, window/level operations are applied only to grayscale images. When this flag is
   * true, the operations can also be applied to color images.
   *
   * @return true if window/level operations are allowed on color images
   */
  @Override
  public boolean isAllowWinLevelOnColorImage() {
    return allowWinLevelOnColorImage;
  }

  /**
   * Returns the shape of the lookup table transformation.
   *
   * <p>The LUT shape determines how pixel values are mapped within the window range. Common shapes
   * include linear, sigmoid, and other curve types.
   *
   * @return the LUT shape
   */
  @Override
  public LutShape getLutShape() {
    return lutShape;
  }

  /**
   * Returns the DICOM presentation state object.
   *
   * <p>The presentation state contains additional display parameters that may override or
   * supplement the basic window/level settings.
   *
   * @return the presentation state object, or null if not specified
   */
  @Override
  public PrDicomObject getPresentationState() {
    return dcmPR;
  }
}
