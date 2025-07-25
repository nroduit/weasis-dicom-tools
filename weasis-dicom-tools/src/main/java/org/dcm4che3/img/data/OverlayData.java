/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.data;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.DicomImageUtils;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.DicomUtils;
import org.opencv.core.CvType;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;

/**
 * Represents DICOM overlay data for an image.
 *
 * @author Nicolas Roduit
 */
public record OverlayData(
    int groupOffset,
    int rows,
    int columns,
    int imageFrameOrigin,
    int framesInOverlay,
    int[] origin,
    byte[] data) {

  public static List<OverlayData> getOverlayData(Attributes dcm, int activationMask) {
    return getOverlayData(dcm, activationMask, false);
  }

  private static List<OverlayData> getOverlayData(Attributes dcm, int activationMask, boolean pr) {
    List<OverlayData> data = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      int gg0000 = i << 17;
      if ((activationMask & (1 << i)) != 0 && isLayerActivate(dcm, gg0000, pr)) {
        Optional<byte[]> overData = DicomImageUtils.getByteData(dcm, Tag.OverlayData | gg0000);
        if (overData.isPresent()) {
          int rows = dcm.getInt(Tag.OverlayRows | gg0000, 0);
          int columns = dcm.getInt(Tag.OverlayColumns | gg0000, 0);
          int imageFrameOrigin = dcm.getInt(Tag.ImageFrameOrigin | gg0000, 1);
          int framesInOverlay = dcm.getInt(Tag.NumberOfFramesInOverlay | gg0000, 1);
          int[] origin =
              DicomUtils.getIntArrayFromDicomElement(
                  dcm, (Tag.OverlayOrigin | gg0000), new int[] {1, 1});
          data.add(
              new OverlayData(
                  gg0000,
                  rows,
                  columns,
                  imageFrameOrigin,
                  framesInOverlay,
                  origin,
                  overData.get()));
        }
      }
    }
    return data.isEmpty() ? Collections.emptyList() : data;
  }

  private static boolean isLayerActivate(Attributes dcm, int gg0000, boolean pr) {
    if (pr) {
      String layerName = dcm.getString(Tag.OverlayActivationLayer | gg0000);
      return layerName != null;
    }
    return true;
  }

  public static List<OverlayData> getPrOverlayData(Attributes dcm, int activationMask) {
    return getOverlayData(dcm, activationMask, true);
  }

  public static PlanarImage getOverlayImage(
      final PlanarImage imageSource,
      PlanarImage currentImage,
      ImageDescriptor desc,
      DicomImageReadParam params,
      int frameIndex) {
    Optional<PrDicomObject> prDcm = params.getPresentationState();
    List<OverlayData> overlays = new ArrayList<>();
    prDcm.ifPresent(prDicomObject -> overlays.addAll(prDicomObject.getOverlays()));
    List<EmbeddedOverlay> embeddedOverlays = desc.getEmbeddedOverlay();
    overlays.addAll(desc.getOverlayData());

    if (!embeddedOverlays.isEmpty() || !overlays.isEmpty()) {
      int width = currentImage.width();
      int height = currentImage.height();
      if (width == imageSource.width() && height == imageSource.height()) {
        return getOverlayImage(
            imageSource,
            currentImage,
            params,
            frameIndex,
            height,
            width,
            embeddedOverlays,
            overlays);
      }
    }
    return currentImage;
  }

  private static ImageCV getOverlayImage(
      PlanarImage imageSource,
      PlanarImage currentImage,
      DicomImageReadParam params,
      int frameIndex,
      int height,
      int width,
      List<EmbeddedOverlay> embeddedOverlays,
      List<OverlayData> overlays) {
    ImageCV overlay = new ImageCV(height, width, CvType.CV_8UC1);
    byte[] pixelData = new byte[height * width];
    byte pixVal = (byte) 255;

    for (EmbeddedOverlay data : embeddedOverlays) {
      int mask = 1 << data.bitPosition();
      for (int j = 0; j < height; j++) {
        for (int i = 0; i < width; i++) {
          double[] pix = imageSource.get(j, i);
          if ((((int) pix[0]) & mask) != 0) {
            pixelData[j * width + i] = pixVal;
          }
        }
      }
    }

    applyOverlay(overlays, pixelData, frameIndex, width);
    overlay.put(0, 0, pixelData);
    return ImageTransformer.overlay(
        currentImage.toMat(), overlay, params.getOverlayColor().orElse(Color.WHITE));
  }

  private static void applyOverlay(
      List<OverlayData> overlays, byte[] pixelData, int frameIndex, int width) {
    byte pixVal = (byte) 255;
    for (OverlayData data : overlays) {
      int imageFrameOrigin = data.imageFrameOrigin();
      int framesInOverlay = data.framesInOverlay();
      int overlayFrameIndex = frameIndex - imageFrameOrigin + 1;
      if (overlayFrameIndex >= 0 && overlayFrameIndex < framesInOverlay) {
        int ovHeight = data.rows();
        int ovWidth = data.columns();
        int ovOff = ovHeight * ovWidth * overlayFrameIndex;
        byte[] pix = data.data();
        int x0 = data.origin()[1] - 1;
        int y0 = data.origin()[0] - 1;
        setOverlayPixelData(ovOff, ovWidth, ovHeight, x0, y0, pix, pixelData, pixVal, width);
      }
    }
  }

  private static void setOverlayPixelData(
      int ovOff,
      int ovWidth,
      int ovHeight,
      int x0,
      int y0,
      byte[] pix,
      byte[] pixelData,
      byte pixVal,
      int width) {
    for (int j = y0; j < ovHeight; j++) {
      for (int i = x0; i < ovWidth; i++) {
        int index = ovOff + (j - y0) * ovWidth + (i - x0);
        int b = pix[index / 8] & 0xff;
        if ((b & (1 << (index % 8))) != 0) {
          pixelData[j * width + i] = pixVal;
        }
      }
    }
  }

  public static PlanarImage getOverlayImage(
      PlanarImage imageSource, List<OverlayData> overlays, int frameIndex) {
    int width = imageSource.width();
    int height = imageSource.height();
    ImageCV overlay = new ImageCV(height, width, CvType.CV_8UC1);
    byte[] pixelData = new byte[height * width];
    applyOverlay(overlays, pixelData, frameIndex, width);
    overlay.put(0, 0, pixelData);
    return overlay;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OverlayData that = (OverlayData) o;
    return groupOffset == that.groupOffset
        && rows == that.rows
        && columns == that.columns
        && imageFrameOrigin == that.imageFrameOrigin
        && framesInOverlay == that.framesInOverlay
        && Arrays.equals(origin, that.origin)
        && Arrays.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(groupOffset, rows, columns, imageFrameOrigin, framesInOverlay);
    result = 31 * result + Arrays.hashCode(origin);
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

  @Override
  public String toString() {
    return "OverlayData{"
        + "groupOffset="
        + groupOffset
        + ", rows="
        + rows
        + ", columns="
        + columns
        + '}';
  }
}
