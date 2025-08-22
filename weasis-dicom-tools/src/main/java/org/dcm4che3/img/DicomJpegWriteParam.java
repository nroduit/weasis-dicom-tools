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

import java.awt.Rectangle;
import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.codec.TransferSyntaxType;

/**
 * Configuration parameters for DICOM JPEG image writing operations.
 *
 * <p>This class encapsulates the compression parameters and settings required for writing DICOM
 * images in various JPEG formats including:
 *
 * <ul>
 *   <li>JPEG Baseline (lossy)
 *   <li>JPEG Extended (lossy)
 *   <li>JPEG Spectral Selection (lossy)
 *   <li>JPEG Progressive (lossy)
 *   <li>JPEG Lossless
 *   <li>JPEG-LS (near-lossless and lossless)
 *   <li>JPEG-2000 (lossy and lossless)
 * </ul>
 *
 * <p>The parameters are automatically configured based on the target transfer syntax, with
 * appropriate defaults for lossless vs lossy compression modes.
 *
 * @author Nicolas Roduit
 * @see TransferSyntaxType
 * @see org.dcm4che3.data.UID
 */
public class DicomJpegWriteParam {
  // Default values for compression parameters
  public static final int DEFAULT_COMPRESSION_QUALITY = 85;
  public static final int DEFAULT_LOSSY_COMPRESSION_RATIO = 10;
  public static final int DEFAULT_NEAR_LOSSLESS_ERROR = 0;

  private static final int DEFAULT_PREDICTION = 1;
  private static final int DEFAULT_POINT_TRANSFORM = 0;
  private static final int DEFAULT_COMPRESSION_RATIO = 0;
  private static final int LOSSLESS_PREDICTION_MODE = 6;
  private static final int DEFAULT_LOSSY_NEAR_LOSSLESS = 2;

  // Immutable transfer syntax configuration
  private final TransferSyntaxType type;
  private final String transferSyntaxUid;

  // Mutable compression parameters
  private int prediction = DEFAULT_PREDICTION;
  private int pointTransform = DEFAULT_POINT_TRANSFORM;
  private int nearLosslessError = DEFAULT_NEAR_LOSSLESS_ERROR;
  private int compressionQuality = DEFAULT_COMPRESSION_QUALITY;
  private int compressionRatioFactor = DEFAULT_COMPRESSION_RATIO;
  private boolean losslessCompression = true;
  private Rectangle sourceRegion;

  /**
   * Creates DICOM JPEG write parameters for the specified transfer syntax.
   *
   * @param type the transfer syntax type
   * @param transferSyntaxUid the transfer syntax UID
   */
  private DicomJpegWriteParam(TransferSyntaxType type, String transferSyntaxUid) {
    this.type = type;
    this.transferSyntaxUid = transferSyntaxUid;
  }

  /**
   * Gets the transfer syntax UID for this compression configuration.
   *
   * @return the transfer syntax UID
   */
  public String getTransferSyntaxUid() {
    return transferSyntaxUid;
  }

  /**
   * Gets the JPEG prediction mode for lossless compression.
   *
   * @return the prediction mode (1-7, where 1=no prediction, 6=optimal for most cases)
   */
  public int getPrediction() {
    return prediction;
  }

  /**
   * Sets the JPEG prediction mode for lossless compression.
   *
   * @param prediction the prediction mode (1-7)
   */
  public void setPrediction(int prediction) {
    this.prediction = prediction;
  }

  /**
   * Gets the point transform value for lossless JPEG compression.
   *
   * @return the point transform value (0-15, default: 0)
   */
  public int getPointTransform() {
    return pointTransform;
  }

  /**
   * Sets the point transform value for lossless JPEG compression.
   *
   * @param pointTransform the point transform value (0-15)
   */
  public void setPointTransform(int pointTransform) {
    this.pointTransform = pointTransform;
  }

  /**
   * Gets the near-lossless error tolerance for JPEG-LS compression.
   *
   * @return the error tolerance (0 for lossless, >0 for near-lossless)
   */
  public int getNearLosslessError() {
    return nearLosslessError;
  }

  /**
   * Sets the near-lossless error tolerance for JPEG-LS compression. A value of 0 means lossless
   * compression, while values > 0 enable near-lossless compression with the specified error
   * tolerance.
   *
   * @param nearLosslessError the error tolerance (must be >= 0)
   * @throws IllegalArgumentException if nearLosslessError is negative
   */
  public void setNearLosslessError(int nearLosslessError) {
    if (nearLosslessError < 0) {
      throw new IllegalArgumentException("nearLossless invalid value: " + nearLosslessError);
    }
    this.nearLosslessError = nearLosslessError;
  }

  /**
   * Gets the compression quality for lossy JPEG compression.
   *
   * @return the quality level (1-100, where 100 is best quality)
   */
  public int getCompressionQuality() {
    return compressionQuality;
  }

  /**
   * Sets the compression quality for lossy JPEG compression. Higher values produce better quality
   * but larger file sizes.
   *
   * @param compressionQuality quality level between 1 and 100 (100 is best quality)
   */
  public void setCompressionQuality(int compressionQuality) {
    this.compressionQuality = compressionQuality;
  }

  /**
   * Gets the compression ratio factor for JPEG-2000.
   *
   * @return the compression ratio factor (0 for lossless, >1 for lossy)
   */
  public int getCompressionRatioFactor() {
    return compressionRatioFactor;
  }

  /**
   * Sets the JPEG-2000 lossy compression ratio factor.
   *
   * <p>Recommended values:
   *
   * <ul>
   *   <li>0: Lossless compression
   *   <li>10-20: Visually near-lossless compression
   *   <li>50-100: Lossy compression with acceptable degradation
   * </ul>
   *
   * @param compressionRatioFactor the compression ratio (0 for lossless, >1 for lossy)
   */
  public void setCompressionRatioFactor(int compressionRatioFactor) {
    this.compressionRatioFactor = compressionRatioFactor;
  }

  /**
   * Gets the transfer syntax type.
   *
   * @return the transfer syntax type
   */
  public TransferSyntaxType getType() {
    return type;
  }

  /**
   * Checks if the compression is configured for lossless mode.
   *
   * @return true if lossless compression is enabled
   */
  public boolean isCompressionLossless() {
    return losslessCompression;
  }

  /**
   * Gets the JPEG mode corresponding to the transfer syntax type.
   *
   * @return the JPEG mode (0-4) for different JPEG variants
   */
  public int getJpegMode() {
    return switch (type) {
      case JPEG_BASELINE -> 0;
      case JPEG_EXTENDED -> 1;
      case JPEG_SPECTRAL -> 2;
      case JPEG_PROGRESSIVE -> 3;
      case JPEG_LOSSLESS -> 4;
      default -> 0;
    };
  }

  /**
   * Gets the source region for partial image.
   *
   * @return the source region, or null for full image
   */
  public Rectangle getSourceRegion() {
    return sourceRegion;
  }

  /**
   * Sets the source region for partial image.
   *
   * @param sourceRegion the region to process, or null for full image
   * @throws IllegalArgumentException if the region has invalid dimensions
   */
  public void setSourceRegion(Rectangle sourceRegion) {
    if (sourceRegion != null) {
      validateSourceRegion(sourceRegion);
    }
    this.sourceRegion = sourceRegion;
  }

  private void validateSourceRegion(Rectangle region) {
    if (region.x < 0 || region.y < 0 || region.width <= 0 || region.height <= 0) {
      throw new IllegalArgumentException("sourceRegion has illegal values!");
    }
  }

  /**
   * Creates a DICOM JPEG write parameter instance configured for the specified transfer syntax.
   *
   * <p>This factory method automatically configures all compression parameters based on the
   * transfer syntax, setting appropriate defaults for lossless vs lossy modes.
   *
   * @param tsuid the target transfer syntax UID
   * @return a configured DicomJpegWriteParam instance
   * @throws IllegalStateException if the transfer syntax is not supported for compression
   */
  public static DicomJpegWriteParam buildDicomImageWriteParam(String tsuid) {
    var type = TransferSyntaxType.forUID(tsuid);
    validateSupportedSyntax(type, tsuid);

    var param = new DicomJpegWriteParam(type, tsuid);
    configureCompressionMode(param, tsuid);
    configureLosslessParameters(param, type, tsuid);

    return param;
  }

  private static void validateSupportedSyntax(TransferSyntaxType type, String tsuid) {
    if (isUnsupportedSyntax(type) || !DicomOutputData.isSupportedSyntax(tsuid)) {
      throw new IllegalStateException(tsuid + " is not supported for compression!");
    }
  }

  private static boolean isUnsupportedSyntax(TransferSyntaxType type) {
    return switch (type) {
      case NATIVE, RLE, JPIP, MPEG -> true;
      default -> false;
    };
  }

  private static void configureCompressionMode(DicomJpegWriteParam param, String tsuid) {
    boolean isLossless = !TransferSyntaxType.isLossyCompression(tsuid);
    param.losslessCompression = isLossless;

    if (isLossless) {
      param.setNearLosslessError(0);
      param.setCompressionRatioFactor(0);
      param.setCompressionQuality(0);
    } else {
      param.setNearLosslessError(DEFAULT_LOSSY_NEAR_LOSSLESS);
      param.setCompressionRatioFactor(DEFAULT_LOSSY_COMPRESSION_RATIO);
      // Keep the default compression quality for lossy modes
    }
  }

  private static void configureLosslessParameters(
      DicomJpegWriteParam param, TransferSyntaxType type, String tsuid) {
    if (type == TransferSyntaxType.JPEG_LOSSLESS) {
      param.setPointTransform(0);
      param.setPrediction(
          UID.JPEGLossless.equals(tsuid) ? LOSSLESS_PREDICTION_MODE : DEFAULT_PREDICTION);
    }
  }
}
