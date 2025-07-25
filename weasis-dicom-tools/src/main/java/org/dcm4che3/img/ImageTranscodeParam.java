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

import java.util.Optional;
import java.util.OptionalInt;
import org.dcm4che3.img.Transcoder.Format;
import org.weasis.core.util.LangUtil;

/**
 * Configuration parameters for DICOM image transcoding operations.
 *
 * <p>This class encapsulates the settings required for transcoding DICOM images to various output
 * formats, including compression quality settings and pixel data processing options.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Configurable output format (JPEG, PNG, TIFF, etc.)
 *   <li>JPEG compression quality control (1-100)
 *   <li>Raw pixel data preservation for >8-bit images
 *   <li>Integration with DICOM image reading parameters
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * ImageTranscodeParam param = new ImageTranscodeParam(Format.JPEG);
 * param.setJpegCompressionQuality(85);
 * param.setPreserveRawImage(false); // Apply windowing/leveling
 * }</pre>
 *
 * @author Nicolas Roduit
 * @see DicomImageReadParam
 * @see Transcoder.Format
 */
public class ImageTranscodeParam {

  // Constants for validation and defaults
  private static final int MIN_JPEG_QUALITY = 1;
  private static final int MAX_JPEG_QUALITY = 100;
  private static final Format DEFAULT_FORMAT = Format.JPEG;
  private final DicomImageReadParam readParam;
  private final Format format;

  // Optional configuration parameters
  private Integer jpegCompressionQuality;
  private Boolean preserveRawImage;

  /**
   * Creates transcoding parameters with the specified output format. Uses default DICOM image
   * reading parameters.
   *
   * @param format the target output format, or null for JPEG default
   */
  public ImageTranscodeParam(Format format) {
    this(null, format);
  }

  /**
   * Creates transcoding parameters with custom reading parameters and output format.
   *
   * @param readParam the DICOM image reading parameters, or null for defaults
   * @param format the target output format, or null for JPEG default
   */
  public ImageTranscodeParam(DicomImageReadParam readParam, Format format) {
    this.readParam = initializeReadParam(readParam);
    this.format = format != null ? format : DEFAULT_FORMAT;
    this.preserveRawImage = null;
    this.jpegCompressionQuality = null;
  }

  private DicomImageReadParam initializeReadParam(DicomImageReadParam readParam) {
    return readParam != null ? readParam : new DicomImageReadParam();
  }

  /**
   * Gets the DICOM image reading parameters.
   *
   * @return the reading parameters used for DICOM image processing
   */
  public DicomImageReadParam getReadParam() {
    return readParam;
  }

  /**
   * Gets the configured JPEG compression quality.
   *
   * @return an OptionalInt containing the quality level (1-100), or empty if not set
   */
  public OptionalInt getJpegCompressionQuality() {
    return LangUtil.toOptional(jpegCompressionQuality);
  }

  /**
   * Sets the JPEG compression quality level. Higher values produce better quality but larger file
   * sizes. Only applicable when the output format is JPEG.
   *
   * @param jpegCompressionQuality quality level between 1 and 100 (100 is best quality)
   * @throws IllegalArgumentException if quality is outside the valid range [1-100]
   */
  public void setJpegCompressionQuality(int jpegCompressionQuality) {
    validateJpegQuality(jpegCompressionQuality);
    this.jpegCompressionQuality = jpegCompressionQuality;
  }

  private void validateJpegQuality(int quality) {
    if (quality < MIN_JPEG_QUALITY || quality > MAX_JPEG_QUALITY) {
      throw new IllegalArgumentException(
          "JPEG quality must be between "
              + MIN_JPEG_QUALITY
              + " and "
              + MAX_JPEG_QUALITY
              + ", got: "
              + quality);
    }
  }

  /**
   * Checks if raw image data preservation is configured.
   *
   * @return an Optional containing the preservation setting, or empty if not explicitly set
   */
  public Optional<Boolean> isPreserveRawImage() {
    return Optional.ofNullable(preserveRawImage);
  }

  /**
   * Configures whether to preserve raw pixel data for images with >8 bits per sample.
   *
   * <p>When enabled (true):
   *
   * <ul>
   *   <li>Raw pixel values are preserved without windowing/leveling
   *   <li>Output maintains original bit depth when format supports it
   *   <li>Useful for diagnostic applications requiring full precision
   * </ul>
   *
   * <p>When disabled (false, default):
   *
   * <ul>
   *   <li>Window/Level (W/L) transformations are applied
   *   <li>Output is typically 8-bit per sample for display purposes
   *   <li>Suitable for general viewing and web applications
   * </ul>
   *
   * @param preserveRawImage true to preserve raw data, false to apply W/L transformations, null to
   *     use system defaults
   */
  public void setPreserveRawImage(Boolean preserveRawImage) {
    this.preserveRawImage = preserveRawImage;
  }

  /**
   * Gets the configured output format.
   *
   * @return the target output format for transcoded images
   */
  public Format getFormat() {
    return format;
  }

  /**
   * Creates a copy of this transcoding parameter configuration.
   *
   * @return a new ImageTranscodeParam instance with the same settings
   */
  public ImageTranscodeParam copy() {
    ImageTranscodeParam copy = new ImageTranscodeParam(readParam, format);
    copy.jpegCompressionQuality = this.jpegCompressionQuality;
    copy.preserveRawImage = this.preserveRawImage;
    return copy;
  }

  @Override
  public String toString() {
    return "ImageTranscodeParam{"
        + "format="
        + format
        + ", jpegQuality="
        + (jpegCompressionQuality != null ? jpegCompressionQuality : "default")
        + ", preserveRaw="
        + (preserveRawImage != null ? preserveRawImage : "default")
        + '}';
  }
}
