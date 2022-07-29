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
import java.util.Collections;
import java.util.List;
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
import org.weasis.opencv.op.ImageProcessor;

/**
 * @author Nicolas Roduit
 */
public class OverlayData {
  private final int groupOffset;
  private final int rows;
  private final int columns;
  private final int imageFrameOrigin;
  private final int framesInOverlay;
  private final int[] origin;
  private final byte[] data;

  public OverlayData(
      int groupOffset,
      int rows,
      int columns,
      int imageFrameOrigin,
      int framesInOverlay,
      int[] origin,
      byte[] data) {
    this.groupOffset = groupOffset;
    this.rows = rows;
    this.columns = columns;
    this.imageFrameOrigin = imageFrameOrigin;
    this.framesInOverlay = framesInOverlay;
    this.origin = origin;
    this.data = data;
  }

  public int getRows() {
    return rows;
  }

  public int getColumns() {
    return columns;
  }

  public int getGroupOffset() {
    return groupOffset;
  }

  public int getImageFrameOrigin() {
    return imageFrameOrigin;
  }

  public int getFramesInOverlay() {
    return framesInOverlay;
  }

  public int[] getOrigin() {
    return origin;
  }

  public byte[] getData() {
    return data;
  }

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
              DicomUtils.getIntAyrrayFromDicomElement(
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
      int tagOverlayActivationLayer = Tag.OverlayActivationLayer | gg0000;
      String layerName = dcm.getString(tagOverlayActivationLayer);
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
        ImageCV overlay = new ImageCV(height, width, CvType.CV_8UC1); // NOSONAR
        byte[] pixelData = new byte[height * width];
        byte pixVal = (byte) 255;

        for (EmbeddedOverlay data : embeddedOverlays) {
          int mask = 1 << data.getBitPosition();
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
        return ImageProcessor.overlay(
            currentImage.toMat(), overlay, params.getOverlayColor().orElse(Color.WHITE));
      }
    }
    return currentImage;
  }

  private static void applyOverlay(
      List<OverlayData> overlays, byte[] pixelData, int frameIndex, int width) {
    byte pixVal = (byte) 255;
    for (OverlayData data : overlays) {
      int imageFrameOrigin = data.getImageFrameOrigin();
      int framesInOverlay = data.getFramesInOverlay();
      int overlayFrameIndex = frameIndex - imageFrameOrigin + 1;
      if (overlayFrameIndex >= 0 && overlayFrameIndex < framesInOverlay) {
        int ovHeight = data.getRows();
        int ovWidth = data.getColumns();
        int ovOff = ovHeight * ovWidth * overlayFrameIndex;
        byte[] pix = data.getData();
        int x0 = data.getOrigin()[1] - 1;
        int y0 = data.getOrigin()[0] - 1;
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
    }
  }

  public static PlanarImage getOverlayImage(
      PlanarImage imageSource, List<OverlayData> overlays, int frameIndex) {
    int width = imageSource.width();
    int height = imageSource.height();
    ImageCV overlay = new ImageCV(height, width, CvType.CV_8UC1); // NOSONAR
    byte[] pixelData = new byte[height * width];
    applyOverlay(overlays, pixelData, frameIndex, width);
    overlay.put(0, 0, pixelData);
    return overlay;
  }
}
