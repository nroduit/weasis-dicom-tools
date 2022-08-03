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
        attrs.setInt(Tag.BitsStored, VR.US, bitsStored = 12);
        attrs.setInt(Tag.HighBit, VR.US, 11);
        return true;
      }
    }
    return false;
  }

  public static TransferSyntaxType forUID(String uid) {
    switch (uid) {
      case UID.ImplicitVRLittleEndian:
      case UID.ExplicitVRLittleEndian:
      case UID.ExplicitVRBigEndian:
        return NATIVE;
      case UID.DeflatedExplicitVRLittleEndian:
        return DEFLATED;
      case UID.JPEGBaseline8Bit:
        return JPEG_BASELINE;
      case UID.JPEGExtended12Bit:
        return JPEG_EXTENDED;
      case UID.JPEGSpectralSelectionNonHierarchical68:
        return JPEG_SPECTRAL;
      case UID.JPEGFullProgressionNonHierarchical1012:
        return JPEG_PROGRESSIVE;
      case UID.JPEGLossless:
      case UID.JPEGLosslessSV1:
        return JPEG_LOSSLESS;
      case UID.JPEGLSLossless:
      case UID.JPEGLSNearLossless:
        return JPEG_LS;
      case UID.JPEG2000Lossless:
      case UID.JPEG2000:
      case UID.JPEG2000MCLossless:
      case UID.JPEG2000MC:
        return JPEG_2000;
      case UID.JPIPReferenced:
      case UID.JPIPReferencedDeflate:
        return JPIP;
      case UID.MPEG2MPML:
      case UID.MPEG2MPHL:
      case UID.MPEG4HP41:
      case UID.MPEG4HP41BD:
      case UID.MPEG4HP422D:
      case UID.MPEG4HP423D:
      case UID.MPEG4HP42STEREO:
      case UID.HEVCMP51:
      case UID.HEVCM10P51:
        return MPEG;
      case UID.RLELossless:
        return RLE;
      default:
        return UNKNOWN;
    }
  }

  public static boolean isLossyCompression(String uid) {
    switch (uid) {
      case UID.JPEGBaseline8Bit:
      case UID.JPEGExtended12Bit:
      case UID.JPEGSpectralSelectionNonHierarchical68:
      case UID.JPEGFullProgressionNonHierarchical1012:
      case UID.JPEGLSNearLossless:
      case UID.JPEG2000:
      case UID.JPEG2000MC:
      case UID.MPEG2MPML:
      case UID.MPEG2MPHL:
      case UID.MPEG4HP41:
      case UID.MPEG4HP41BD:
      case UID.MPEG4HP422D:
      case UID.MPEG4HP423D:
      case UID.MPEG4HP42STEREO:
      case UID.HEVCMP51:
      case UID.HEVCM10P51:
        return true;
      default:
        return false;
    }
  }

  public static boolean isYBRCompression(String uid) {
    switch (uid) {
      case UID.JPEGBaseline8Bit:
      case UID.JPEGExtended12Bit:
      case UID.JPEGSpectralSelectionNonHierarchical68:
      case UID.JPEGFullProgressionNonHierarchical1012:
      case UID.JPEG2000Lossless:
      case UID.JPEG2000:
        return true;
      default:
        return false;
    }
  }
}
