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
 * @author Nicolas Roduit
 */
public class DicomJpegWriteParam {
  private final TransferSyntaxType type;

  /** JPEG lossless point transform (0..15, default: 0) */
  private int prediction;

  private int pointTransform;
  private int nearLosslessError;
  private int compressionQuality;
  private boolean losslessCompression;
  private Rectangle sourceRegion;
  private int compressionRatioFactor;
  private final String transferSyntaxUid;

  DicomJpegWriteParam(TransferSyntaxType type, String transferSyntaxUid) {
    this.type = type;
    this.transferSyntaxUid = transferSyntaxUid;
    this.prediction = 1;
    this.pointTransform = 0;
    this.nearLosslessError = 0;
    this.compressionQuality = 85;
    this.losslessCompression = true;
    this.sourceRegion = null;
    this.compressionRatioFactor = 0;
  }

  public String getTransferSyntaxUid() {
    return transferSyntaxUid;
  }

  public int getPrediction() {
    return prediction;
  }

  public void setPrediction(int prediction) {
    this.prediction = prediction;
  }

  public int getPointTransform() {
    return pointTransform;
  }

  public void setPointTransform(int pointTransform) {
    this.pointTransform = pointTransform;
  }

  public int getNearLosslessError() {
    return nearLosslessError;
  }

  public void setNearLosslessError(int nearLosslessError) {
    if (nearLosslessError < 0)
      throw new IllegalArgumentException("nearLossless invalid value: " + nearLosslessError);
    this.nearLosslessError = nearLosslessError;
  }

  public int getCompressionQuality() {
    return compressionQuality;
  }

  /**
   * @param compressionQuality between 1 and 100 (100 is the best lossy quality).
   */
  public void setCompressionQuality(int compressionQuality) {
    this.compressionQuality = compressionQuality;
  }

  public int getCompressionRatioFactor() {
    return compressionRatioFactor;
  }

  /**
   * JPEG-2000 Lossy compression ratio factor.
   *
   * <p>Visually near-lossless typically achieves compression ratios of 10:1 to 20:1 (e.g.
   * compressionRatioFactor = 10)
   *
   * <p>Lossy compression with acceptable degradation can have ratios of 50:1 to 100:1 (e.g.
   * compressionRatioFactor = 50)
   *
   * @param compressionRatioFactor the compression ratio
   */
  public void setCompressionRatioFactor(int compressionRatioFactor) {
    this.compressionRatioFactor = compressionRatioFactor;
  }

  public TransferSyntaxType getType() {
    return type;
  }

  public boolean isCompressionLossless() {
    return losslessCompression;
  }

  public int getJpegMode() {
    switch (type) {
      case JPEG_BASELINE:
        return 0;
      case JPEG_EXTENDED:
        return 1;
      case JPEG_SPECTRAL:
        return 2;
      case JPEG_PROGRESSIVE:
        return 3;
      case JPEG_LOSSLESS:
        return 4;
      default:
        return 0;
    }
  }

  public void setSourceRegion(Rectangle sourceRegion) {
    this.sourceRegion = sourceRegion;
    if (sourceRegion == null) {
      return;
    }
    if (sourceRegion.x < 0
        || sourceRegion.y < 0
        || sourceRegion.width <= 0
        || sourceRegion.height <= 0) {
      throw new IllegalArgumentException("sourceRegion has illegal values!");
    }
  }

  public Rectangle getSourceRegion() {
    return sourceRegion;
  }

  public static DicomJpegWriteParam buildDicomImageWriteParam(String tsuid) {
    TransferSyntaxType type = TransferSyntaxType.forUID(tsuid);
    switch (type) {
      case NATIVE:
      case RLE:
      case JPIP:
      case MPEG:
        throw new IllegalStateException(tsuid + " is not supported for compression!");
    }
    DicomJpegWriteParam param = new DicomJpegWriteParam(type, tsuid);
    param.losslessCompression = !TransferSyntaxType.isLossyCompression(tsuid);
    param.setNearLosslessError(param.losslessCompression ? 0 : 2);
    param.setCompressionRatioFactor(param.losslessCompression ? 0 : 10);
    param.setCompressionQuality(param.losslessCompression ? 0 : param.getCompressionQuality());
    if (type == TransferSyntaxType.JPEG_LOSSLESS) {
      param.setPointTransform(0);
      if (UID.JPEGLossless.equals(tsuid)) {
        param.setPrediction(6);
      } else {
        param.setPrediction(1);
      }
    }

    return param;
  }
}
