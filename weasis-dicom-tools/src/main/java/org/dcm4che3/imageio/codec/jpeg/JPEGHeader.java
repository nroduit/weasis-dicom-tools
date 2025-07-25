/*
 * Copyright (c) 2009-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec.jpeg;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.ByteUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class JPEGHeader {

  private final byte[] data;
  private final int[] offsets;

  public JPEGHeader(byte[] data, int lastMarker) {
    int n = 0;
    for (int offset = 0; (offset = nextMarker(data, offset)) != -1; ) {
      n++;
      int marker = data[offset++] & 0xff;
      if (JPEG.isStandalone(marker)) continue;
      if (offset + 1 >= data.length) break;
      if (marker == lastMarker) break;
      offset += ByteUtils.bytesToUShortBE(data, offset);
    }
    this.data = data;
    this.offsets = new int[n];
    for (int i = 0, offset = 0; i < n; i++) {
      offsets[i] = (offset = nextMarker(data, offset));
      if (!JPEG.isStandalone(data[offset++] & 0xff))
        offset += ByteUtils.bytesToUShortBE(data, offset);
    }
  }

  private static int nextMarker(byte[] data, int from) {
    for (int i = from + 1; i < data.length; i++) {
      if (data[i - 1] == -1 && data[i] != -1 && data[i] != 0) {
        return i;
      }
    }
    return -1;
  }

  public int offsetOf(int marker) {
    for (int i = 0; i < offsets.length; i++) {
      if (marker(i) == marker) return offsets[i];
    }
    return -1;
  }

  public int offsetSOF() {
    for (int i = 0; i < offsets.length; i++) {
      if (JPEG.isSOF(marker(i))) return offsets[i];
    }
    return -1;
  }

  public int offsetAfterAPP() {
    for (int i = 1; i < offsets.length; i++) {
      if (!JPEG.isAPP(marker(i))) return offsets[i];
    }
    return -1;
  }

  public int offset(int index) {
    return offsets[index];
  }

  public int marker(int index) {
    return data[offsets[index]] & 0xff;
  }

  public int numberOfMarkers() {
    return offsets.length;
  }

  /**
   * Return corresponding Image Pixel Description Macro Attributes
   *
   * @param attrs target {@code Attributes} or {@code null}
   * @return Image Pixel Description Macro Attributes
   */
  public Attributes toAttributes(Attributes attrs) {
    int offsetSOF = offsetSOF();
    if (offsetSOF == -1) return null;

    if (attrs == null) attrs = new Attributes(10);

    int sof = data[offsetSOF] & 0xff;
    int p = data[offsetSOF + 3] & 0xff;
    int y = ((data[offsetSOF + 3 + 1] & 0xff) << 8) | (data[offsetSOF + 3 + 2] & 0xff);
    int x = ((data[offsetSOF + 3 + 3] & 0xff) << 8) | (data[offsetSOF + 3 + 4] & 0xff);
    int nf = data[offsetSOF + 3 + 5] & 0xff;
    attrs.setInt(Tag.SamplesPerPixel, VR.US, nf);
    if (nf == 3) {
      attrs.setString(
          Tag.PhotometricInterpretation,
          VR.CS,
          (sof == JPEG.SOF3 || sof == JPEG.SOF55) ? "RGB" : "YBR_FULL_422");
      attrs.setInt(Tag.PlanarConfiguration, VR.US, 0);
    } else {
      attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
    }
    attrs.setInt(Tag.Rows, VR.US, y);
    attrs.setInt(Tag.Columns, VR.US, x);
    attrs.setInt(Tag.BitsAllocated, VR.US, p > 8 ? 16 : 8);
    attrs.setInt(Tag.BitsStored, VR.US, p);
    attrs.setInt(Tag.HighBit, VR.US, p - 1);
    attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
    if (!(sof == JPEG.SOF3 || (sof == JPEG.SOF55 && ss() == 0)))
      attrs.setString(Tag.LossyImageCompression, VR.CS, "01");
    return attrs;
  }

  public String getTransferSyntaxUID() {
    int sofOffset = offsetSOF();
    if (sofOffset == -1) return null;

    return switch (data[sofOffset] & 0xff) {
      case JPEG.SOF0 -> UID.JPEGBaseline8Bit;
      case JPEG.SOF1 -> UID.JPEGExtended12Bit;
      case JPEG.SOF2 -> UID.JPEGFullProgressionNonHierarchical1012;
      case JPEG.SOF3 -> ss() == 1 ? UID.JPEGLosslessSV1 : UID.JPEGLossless;
      case JPEG.SOF55 -> ss() == 0 ? UID.JPEGLSLossless : UID.JPEGLSNearLossless;
      default -> null;
    };
  }

  private int ss() {
    int offsetSOS = offsetOf(JPEG.SOS);
    return offsetSOS != -1 ? data[offsetSOS + 6] & 0xff : -1;
  }
}
