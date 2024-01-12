/*
 * Copyright (c) 2009-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec.mpeg;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2017
 */
public class MPEGHeader {

  private static final String[] ASPECT_RATIO_1_1 = {"1", "1"};
  private static final String[] ASPECT_RATIO_4_3 = {"4", "3"};
  private static final String[] ASPECT_RATIO_16_9 = {"16", "9"};
  private static final String[] ASPECT_RATIO_221_100 = {"221", "100"};
  private static final String[][] ASPECT_RATIOS = {
    ASPECT_RATIO_1_1, ASPECT_RATIO_4_3, ASPECT_RATIO_16_9, ASPECT_RATIO_221_100
  };
  private static int[] FPS = {
    24, 1001,
    24, 1000,
    25, 1000,
    30, 1001,
    30, 1000,
    50, 1000,
    60, 1001,
    60, 1000
  };

  private final byte[] data;
  private final int seqHeaderOffset;

  public MPEGHeader(byte[] data) {
    this.data = data;
    int remaining = data.length;
    int i = 0;
    do {
      while (remaining-- > 0 && data[i++] != 0)
        ;
      if (remaining-- > 0 && data[i++] != 0) continue;
    } while (remaining > 8 && (data[i] != 1 || data[i + 1] != (byte) 0xb3));
    seqHeaderOffset = remaining > 8 ? i + 1 : -1;
  }

  /**
   * Return corresponding Image Pixel Description Macro Attributes
   *
   * @param attrs target {@code Attributes} or {@code null}
   * @param length MPEG stream length
   * @return Image Pixel Description Macro Attributes
   */
  public Attributes toAttributes(Attributes attrs, long length) {
    if (seqHeaderOffset == -1) return null;

    if (attrs == null) attrs = new Attributes(15);

    int off = seqHeaderOffset;
    int x = ((data[off + 1] & 0xFF) << 4) | ((data[off + 2] & 0xF0) >> 4);
    int y = ((data[off + 2] & 0x0F) << 8) | (data[off + 3] & 0xFF);
    int aspectRatio = (data[off + 4] >> 4) & 0x0F;
    int frameRate = data[off + 4] & 0x0F;
    int bitRate =
        ((data[off + 5] & 0xFF) << 10)
            | ((data[off + 6] & 0xFF) << 2)
            | ((data[off + 7] & 0xC0) >> 6);
    int numFrames = 9999;
    if (frameRate > 0 && frameRate < 9) {
      int frameRate2 = (frameRate - 1) << 1;
      attrs.setInt(Tag.CineRate, VR.IS, FPS[frameRate2]);
      attrs.setFloat(Tag.FrameTime, VR.DS, ((float) FPS[frameRate2 + 1]) / FPS[frameRate2]);
      if (bitRate > 0)
        numFrames = (int) (20 * length * FPS[frameRate2] / FPS[frameRate2 + 1] / bitRate);
    }
    attrs.setInt(Tag.SamplesPerPixel, VR.US, 3);
    attrs.setString(Tag.PhotometricInterpretation, VR.CS, "YBR_PARTIAL_420");
    attrs.setInt(Tag.PlanarConfiguration, VR.US, 0);
    attrs.setInt(Tag.FrameIncrementPointer, VR.AT, Tag.FrameTime);
    attrs.setInt(Tag.NumberOfFrames, VR.IS, numFrames);
    attrs.setInt(Tag.Rows, VR.US, y);
    attrs.setInt(Tag.Columns, VR.US, x);
    if (aspectRatio > 0 && aspectRatio < 5)
      attrs.setString(Tag.PixelAspectRatio, VR.IS, ASPECT_RATIOS[aspectRatio - 1]);
    attrs.setInt(Tag.BitsAllocated, VR.US, 8);
    attrs.setInt(Tag.BitsStored, VR.US, 8);
    attrs.setInt(Tag.HighBit, VR.US, 7);
    attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
    attrs.setString(Tag.LossyImageCompression, VR.CS, "01");
    return attrs;
  }
}
