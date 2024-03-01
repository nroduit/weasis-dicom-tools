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
 * @author Nicolas Roduit
 */
public class ImageTranscodeParam {
  private final DicomImageReadParam readParam;
  private final Format format;

  private Integer jpegCompressionQuality;
  private Boolean preserveRawImage;

  public ImageTranscodeParam(Format format) {
    this(null, format);
  }

  public ImageTranscodeParam(DicomImageReadParam readParam, Format format) {
    this.readParam = readParam == null ? new DicomImageReadParam() : readParam;
    this.format = format == null ? Format.JPEG : format;
    this.preserveRawImage = null;
    this.jpegCompressionQuality = null;
  }

  public DicomImageReadParam getReadParam() {
    return readParam;
  }

  public OptionalInt getJpegCompressionQuality() {
    return LangUtil.getOptionalInteger(jpegCompressionQuality);
  }

  /**
   * @param jpegCompressionQuality between 1 to 100 (100 is the best lossy quality).
   */
  public void setJpegCompressionQuality(int jpegCompressionQuality) {
    this.jpegCompressionQuality = jpegCompressionQuality;
  }

  public Optional<Boolean> isPreserveRawImage() {
    return Optional.ofNullable(preserveRawImage);
  }

  /**
   * It preserves the raw data when the pixel depth is more than 8 bit. The default value applies
   * the W/L and is FALSE, the output image will be always a 8-bit per sample image.
   *
   * @param preserveRawImage
   */
  public void setPreserveRawImage(Boolean preserveRawImage) {
    this.preserveRawImage = preserveRawImage;
  }

  public Format getFormat() {
    return format;
  }
}
