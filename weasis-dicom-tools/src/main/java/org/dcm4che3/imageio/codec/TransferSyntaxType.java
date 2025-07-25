/*
 * Copyright (c) 2009-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public enum TransferSyntaxType {
  NATIVE(false, false, true, 16, 0),
  JPEG_BASELINE(true, true, false, 8, 0),
  JPEG_EXTENDED(true, true, false, 12, 0),
  JPEG_SPECTRAL(true, true, false, 12, 0),
  JPEG_PROGRESSIVE(true, true, false, 12, 0),
  JPEG_LOSSLESS(true, true, true, 16, 0),
  JPEG_LS(true, true, true, 16, 0),
  JPEG_2000(true, true, true, 16, 0),
  RLE(true, false, true, 16, 1),
  JPIP(false, false, true, 16, 0),
  MPEG(true, false, false, 8, 0),
  DEFLATED(false, false, true, 16, 0),
  UNKNOWN(false, false, true, 16, 0);

  private final boolean pixeldataEncapsulated;
  private final boolean frameSpanMultipleFragments;
  private final boolean encodeSigned;
  private final int maxBitsStored;
  private final int planarConfiguration;

  TransferSyntaxType(
      boolean pixeldataEncapsulated,
      boolean frameSpanMultipleFragments,
      boolean encodeSigned,
      int maxBitsStored,
      int planarConfiguration) {
    this.pixeldataEncapsulated = pixeldataEncapsulated;
    this.frameSpanMultipleFragments = frameSpanMultipleFragments;
    this.encodeSigned = encodeSigned;
    this.maxBitsStored = maxBitsStored;
    this.planarConfiguration = planarConfiguration;
  }

  public boolean isPixeldataEncapsulated() {
    return pixeldataEncapsulated;
  }

  public boolean canEncodeSigned() {
    return encodeSigned;
  }

  public boolean mayFrameSpanMultipleFragments() {
    return frameSpanMultipleFragments;
  }

  public int getPlanarConfiguration() {
    return planarConfiguration;
  }

  public int getMaxBitsStored() {
    return maxBitsStored;
  }

  public boolean adjustBitsStoredTo12(Attributes attrs) {
    if (maxBitsStored == 12) {
      int bitsStored = attrs.getInt(Tag.BitsStored, 8);
      if (bitsStored > 8 && bitsStored < 12) {
        attrs.setInt(Tag.BitsStored, VR.US, 12);
        attrs.setInt(Tag.HighBit, VR.US, 11);
        return true;
      }
    }
    return false;
  }

  public static TransferSyntaxType forUID(String uid) {
    return switch (uid) {
      case UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndian ->
          NATIVE;
      case UID.DeflatedExplicitVRLittleEndian -> DEFLATED;
      case UID.JPEGBaseline8Bit -> JPEG_BASELINE;
      case UID.JPEGExtended12Bit -> JPEG_EXTENDED;
      case UID.JPEGSpectralSelectionNonHierarchical68 -> JPEG_SPECTRAL;
      case UID.JPEGFullProgressionNonHierarchical1012 -> JPEG_PROGRESSIVE;
      case UID.JPEGLossless, UID.JPEGLosslessSV1 -> JPEG_LOSSLESS;
      case UID.JPEGLSLossless, UID.JPEGLSNearLossless -> JPEG_LS;
      case UID.JPEG2000Lossless,
              UID.JPEG2000,
              UID.JPEG2000MCLossless,
              UID.JPEG2000MC,
              UID.HTJ2KLossless,
              UID.HTJ2KLosslessRPCL,
              UID.HTJ2K ->
          JPEG_2000;
      case UID.JPIPReferenced,
              UID.JPIPReferencedDeflate,
              UID.JPIPHTJ2KReferenced,
              UID.JPIPHTJ2KReferencedDeflate ->
          JPIP;
      case UID.MPEG2MPML,
              UID.MPEG2MPMLF,
              UID.MPEG2MPHL,
              UID.MPEG2MPHLF,
              UID.MPEG4HP41,
              UID.MPEG4HP41F,
              UID.MPEG4HP41BD,
              UID.MPEG4HP41BDF,
              UID.MPEG4HP422D,
              UID.MPEG4HP422DF,
              UID.MPEG4HP423D,
              UID.MPEG4HP423DF,
              UID.MPEG4HP42STEREO,
              UID.MPEG4HP42STEREOF,
              UID.HEVCMP51,
              UID.HEVCM10P51 ->
          MPEG;
      case UID.RLELossless -> RLE;
      default -> UNKNOWN;
    };
  }

  public static boolean isLossyCompression(String uid) {
    return switch (uid) {
      case UID.JPEGBaseline8Bit,
              UID.JPEGExtended12Bit,
              UID.JPEGSpectralSelectionNonHierarchical68,
              UID.JPEGFullProgressionNonHierarchical1012,
              UID.JPEGLSNearLossless,
              UID.JPEG2000,
              UID.JPEG2000MC,
              UID.HTJ2K,
              UID.MPEG2MPML,
              UID.MPEG2MPMLF,
              UID.MPEG2MPHL,
              UID.MPEG2MPHLF,
              UID.MPEG4HP41,
              UID.MPEG4HP41F,
              UID.MPEG4HP41BD,
              UID.MPEG4HP41BDF,
              UID.MPEG4HP422D,
              UID.MPEG4HP422DF,
              UID.MPEG4HP423D,
              UID.MPEG4HP423DF,
              UID.MPEG4HP42STEREO,
              UID.MPEG4HP42STEREOF,
              UID.HEVCMP51,
              UID.HEVCM10P51 ->
          true;
      default -> false;
    };
  }

  public static boolean isYBRCompression(String uid) {
    return switch (uid) {
      case UID.JPEGBaseline8Bit,
              UID.JPEGExtended12Bit,
              UID.JPEGSpectralSelectionNonHierarchical68,
              UID.JPEGFullProgressionNonHierarchical1012,
              UID.JPEG2000Lossless,
              UID.JPEG2000,
              UID.HTJ2KLossless,
              UID.HTJ2KLosslessRPCL,
              UID.HTJ2K ->
          true;
      default -> false;
    };
  }
}
