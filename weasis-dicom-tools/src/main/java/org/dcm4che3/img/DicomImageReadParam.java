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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.OptionalDouble;
import javax.imageio.IIOParamController;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageTypeSpecifier;
import org.dcm4che3.img.data.PrDicomObject;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.annotations.Generated;
import org.weasis.opencv.op.lut.LutShape;

/**
 * Extended image read parameters specifically designed for DICOM image processing.
 *
 * <p>This class provides comprehensive control over DICOM image rendering including:
 *
 * <ul>
 *   <li>Window/Level adjustments for proper image display
 *   <li>VOI (Value of Interest) LUT transformations
 *   <li>Presentation state handling
 *   <li>Overlay rendering configuration
 *   <li>Pixel padding and data type handling
 * </ul>
 *
 * <p>Note: This implementation restricts certain {@link ImageReadParam} operations that are
 * incompatible with native DICOM decoding requirements.
 *
 * @author Nicolas Roduit
 * @see ImageReadParam
 * @see org.dcm4che3.img.data.PrDicomObject
 * @see org.weasis.opencv.op.lut.LutShape
 */
@Generated
public class DicomImageReadParam extends ImageReadParam {

  private static final String NOT_COMPATIBLE = "Not compatible with the native DICOM Decoder";

  // Window/Level parameters
  private Double windowCenter;
  private Double windowWidth;
  private Double levelMin;
  private Double levelMax;

  // LUT and rendering parameters
  private LutShape voiLutShape;
  private Boolean applyPixelPadding;
  private Boolean inverseLut;
  private Boolean fillOutsideLutRange;
  private Boolean applyWindowLevelToColorImage;
  private Boolean keepRgbForLossyJpeg;

  private Boolean releaseImageAfterProcessing;

  // Presentation state
  private PrDicomObject presentationState;

  // Index parameters
  private int windowIndex;
  private int voiLUTIndex;

  // Overlay parameters
  private int overlayActivationMask = 0xf;
  private int overlayGrayscaleValue = 0xffff;
  private Color overlayColor;

  /**
   * Creates a new DICOM image read parameter with default settings. Enables source render size
   * capability for proper DICOM scaling.
   */
  public DicomImageReadParam() {
    this.canSetSourceRenderSize = true;
  }

  /**
   * Creates a new DICOM image read parameter copying source region and render size from the
   * provided parameter.
   *
   * @param param the source ImageReadParam to copy from
   */
  public DicomImageReadParam(ImageReadParam param) {
    this();
    this.sourceRegion = param.getSourceRegion();
    this.sourceRenderSize = param.getSourceRenderSize();
  }

  // ======== Unsupported operations - these conflict with DICOM native decoding ========
  @Override
  public void setDestinationOffset(Point destinationOffset) {
    throw new UnsupportedOperationException(NOT_COMPATIBLE);
  }

  @Override
  public void setController(IIOParamController controller) {
    throw new UnsupportedOperationException(NOT_COMPATIBLE);
  }

  @Override
  public void setSourceBands(int[] sourceBands) {
    throw new UnsupportedOperationException(NOT_COMPATIBLE);
  }

  @Override
  public void setSourceSubsampling(
      int sourceXSubsampling,
      int sourceYSubsampling,
      int subsamplingXOffset,
      int subsamplingYOffset) {
    throw new UnsupportedOperationException(NOT_COMPATIBLE);
  }

  @Override
  public void setDestination(BufferedImage destination) {
    throw new UnsupportedOperationException(NOT_COMPATIBLE);
  }

  @Override
  public void setDestinationBands(int[] destinationBands) {
    throw new UnsupportedOperationException(NOT_COMPATIBLE);
  }

  @Override
  public void setDestinationType(ImageTypeSpecifier destinationType) {
    throw new UnsupportedOperationException(NOT_COMPATIBLE);
  }

  @Override
  public void setSourceProgressivePasses(int minPass, int numPasses) {
    throw new UnsupportedOperationException(NOT_COMPATIBLE);
  }

  // ======== Window/Level parameters ========

  /**
   * Gets the window center value for DICOM windowing.
   *
   * @return an OptionalDouble containing the window center, or empty if not set
   */
  public OptionalDouble getWindowCenter() {
    return LangUtil.toOptional(windowCenter);
  }

  /**
   * Sets the window center value for DICOM windowing. The center defines the midpoint of the
   * display range.
   *
   * @param windowCenter the center of the window for DICOM values, or null to unset
   */
  public void setWindowCenter(Double windowCenter) {
    this.windowCenter = windowCenter;
  }

  /**
   * Gets the window width value for DICOM windowing.
   *
   * @return an OptionalDouble containing the window width, or empty if not set
   */
  public OptionalDouble getWindowWidth() {
    return LangUtil.toOptional(windowWidth);
  }

  /**
   * Sets the window width value for DICOM windowing. The width defines the range of DICOM values to
   * display around the center.
   *
   * @param windowWidth the width of the display range, or null to unset
   */
  public void setWindowWidth(Double windowWidth) {
    this.windowWidth = windowWidth;
  }

  /**
   * Gets the minimum level value for the image.
   *
   * @return an OptionalDouble containing the minimum level, or empty if not set
   */
  public OptionalDouble getLevelMin() {
    return LangUtil.toOptional(levelMin);
  }

  /**
   * Sets the minimum DICOM value present in the image.
   *
   * @param levelMin the minimum DICOM value, or null to unset
   */
  public void setLevelMin(Double levelMin) {
    this.levelMin = levelMin;
  }

  /**
   * Gets the maximum level value for the image.
   *
   * @return an OptionalDouble containing the maximum level, or empty if not set
   */
  public OptionalDouble getLevelMax() {
    return LangUtil.toOptional(levelMax);
  }

  /**
   * Sets the maximum DICOM value present in the image.
   *
   * @param levelMax the maximum DICOM value, or null to unset
   */
  public void setLevelMax(Double levelMax) {
    this.levelMax = levelMax;
  }

  //  ======== VOI LUT parameters ========

  /**
   * Gets the VOI LUT shape configuration.
   *
   * @return an Optional containing the LUT shape, or empty if not set
   */
  public Optional<LutShape> getVoiLutShape() {
    return Optional.ofNullable(voiLutShape);
  }

  /**
   * Sets the VOI (Value of Interest) LUT shape for image transformation.
   *
   * @param voiLutShape the LUT shape configuration, or null to unset
   */
  public void setVoiLutShape(LutShape voiLutShape) {
    this.voiLutShape = voiLutShape;
  }

  // ======== Boolean rendering options ========
  public Optional<Boolean> getApplyPixelPadding() {
    return Optional.ofNullable(applyPixelPadding);
  }

  /** Controls whether pixel padding values should be applied during rendering. */
  public void setApplyPixelPadding(Boolean applyPixelPadding) {
    this.applyPixelPadding = applyPixelPadding;
  }

  public Optional<Boolean> getInverseLut() {
    return Optional.ofNullable(inverseLut);
  }

  /**
   * Controls whether the LUT should be inverted (useful for different photometric interpretations).
   */
  public void setInverseLut(Boolean inverseLut) {
    this.inverseLut = inverseLut;
  }

  public Optional<Boolean> getReleaseImageAfterProcessing() {
    return Optional.ofNullable(releaseImageAfterProcessing);
  }

  /** Controls whether source images should be released from memory after processing. */
  public void setReleaseImageAfterProcessing(Boolean releaseImageAfterProcessing) {
    this.releaseImageAfterProcessing = releaseImageAfterProcessing;
  }

  public Optional<Boolean> getFillOutsideLutRange() {
    return Optional.ofNullable(fillOutsideLutRange);
  }

  /** Controls whether pixels outside the LUT range should be filled with boundary values. */
  public void setFillOutsideLutRange(Boolean fillOutsideLutRange) {
    this.fillOutsideLutRange = fillOutsideLutRange;
  }

  public Optional<Boolean> getApplyWindowLevelToColorImage() {
    return Optional.ofNullable(applyWindowLevelToColorImage);
  }

  /** Controls whether window/level adjustments should be applied to color images. */
  public void setApplyWindowLevelToColorImage(Boolean applyWindowLevelToColorImage) {
    this.applyWindowLevelToColorImage = applyWindowLevelToColorImage;
  }

  public Optional<Boolean> getKeepRgbForLossyJpeg() {
    return Optional.ofNullable(keepRgbForLossyJpeg);
  }

  /** Controls whether RGB color space should be preserved for lossy JPEG images. */
  public void setKeepRgbForLossyJpeg(Boolean keepRgbForLossyJpeg) {
    this.keepRgbForLossyJpeg = keepRgbForLossyJpeg;
  }

  // ======== Index parameters ========

  /**
   * Gets the VOI LUT index for multi-LUT scenarios.
   *
   * @return the current VOI LUT index (always >= 0)
   */
  public int getVoiLUTIndex() {
    return voiLUTIndex;
  }

  /**
   * Sets the VOI LUT index for selecting from multiple available LUTs.
   *
   * @param voiLUTIndex the LUT index (will be clamped to >= 0)
   */
  public void setVoiLUTIndex(int voiLUTIndex) {
    this.voiLUTIndex = Math.max(voiLUTIndex, 0);
  }

  /**
   * Gets the window index for multi-window scenarios.
   *
   * @return the current window index (always >= 0)
   */
  public int getWindowIndex() {
    return windowIndex;
  }

  /**
   * Sets the window index for selecting from multiple available windows.
   *
   * @param windowIndex the window index (will be clamped to >= 0)
   */
  public void setWindowIndex(int windowIndex) {
    this.windowIndex = Math.max(windowIndex, 0);
  }

  // ======== Presentation state ========

  /**
   * Gets the DICOM presentation state object.
   *
   * @return an Optional containing the presentation state, or empty if not set
   */
  public Optional<PrDicomObject> getPresentationState() {
    return Optional.ofNullable(presentationState);
  }

  /**
   * Sets the DICOM presentation state for advanced image rendering.
   *
   * @param presentationState the presentation state object, or null to unset
   */
  public void setPresentationState(PrDicomObject presentationState) {
    this.presentationState = presentationState;
  }

  // ======== Overlay parameters ========

  /**
   * Gets the overlay activation mask controlling which overlays are displayed.
   *
   * @return the current activation mask (default: 0xf for first 4 overlays)
   */
  public int getOverlayActivationMask() {
    return overlayActivationMask;
  }

  /**
   * Sets the overlay activation mask to control which overlays are displayed. Each bit represents
   * an overlay layer (bit 0 = overlay 1, etc.).
   *
   * @param overlayActivationMask the activation mask
   */
  public void setOverlayActivationMask(int overlayActivationMask) {
    this.overlayActivationMask = overlayActivationMask;
  }

  /**
   * Gets the grayscale value used for overlay rendering.
   *
   * @return the current grayscale value (default: 0xffff for maximum brightness)
   */
  public int getOverlayGrayscaleValue() {
    return overlayGrayscaleValue;
  }

  /**
   * Sets the grayscale value for overlay rendering when overlays are displayed in grayscale.
   *
   * @param overlayGrayscaleValue the grayscale value (typically 0x0000-0xffff)
   */
  public void setOverlayGrayscaleValue(int overlayGrayscaleValue) {
    this.overlayGrayscaleValue = overlayGrayscaleValue;
  }

  /**
   * Gets the color used for overlay rendering.
   *
   * @return an Optional containing the overlay color, or empty if grayscale rendering is used
   */
  public Optional<Color> getOverlayColor() {
    return Optional.ofNullable(overlayColor);
  }

  /**
   * Sets the color for overlay rendering. If null, overlays will be rendered in grayscale.
   *
   * @param overlayColor the overlay color, or null for grayscale rendering
   */
  public void setOverlayColor(Color overlayColor) {
    this.overlayColor = overlayColor;
  }
}
