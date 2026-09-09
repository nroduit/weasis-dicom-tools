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
import java.util.stream.IntStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.DicomAttributeUtils;
import org.dcm4che3.img.util.DicomUtils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.weasis.core.util.LangUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;

/**
 * Represents overlay data extracted from DICOM attributes. This class encapsulates the information
 * about the overlay group, its dimensions, origin, and pixel data. It provides methods to extract
 * overlay data from DICOM attributes, apply overlays to images, and manage overlay pixel data.
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

  /** Maximum number of overlay groups supported (0x6000-0x601E) */
  static final int MAX_OVERLAY_GROUPS = 16;

  /** Bit shift value to calculate group offset (17 bits for overlay group addressing) */
  static final int GROUP_OFFSET_SHIFT = 17;

  /** Default overlay pixel value (white) */
  private static final byte OVERLAY_PIXEL_VALUE = (byte) 255;

  /**
   * Extracts overlay data from DICOM attributes with the specified activation mask.
   *
   * @param dcm the DICOM attributes containing overlay data
   * @param activationMask bitmask specifying which overlay groups to activate
   * @return list of OverlayData objects
   */
  public static List<OverlayData> getOverlayData(Attributes dcm, int activationMask) {
    return getOverlayData(dcm, activationMask, false);
  }

  /**
   * Extracts presentation state overlay data from DICOM attributes.
   *
   * @param dcm the DICOM attributes containing overlay data
   * @param activationMask bitmask specifying which overlay groups to activate
   * @return list of OverlayData objects for presentation state overlays
   */
  public static List<OverlayData> getPrOverlayData(Attributes dcm, int activationMask) {
    return getOverlayData(dcm, activationMask, true);
  }

  private static List<OverlayData> getOverlayData(Attributes dcm, int activationMask, boolean pr) {
    if (dcm == null) {
      return Collections.emptyList();
    }

    return IntStream.range(0, MAX_OVERLAY_GROUPS)
        .filter(groupIndex -> isGroupActivated(groupIndex, activationMask))
        .mapToObj(groupIndex -> groupIndex << GROUP_OFFSET_SHIFT)
        .filter(groupOffset -> isLayerActivated(dcm, groupOffset, pr))
        .map(groupOffset -> createOverlayData(dcm, groupOffset))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  private static boolean isGroupActivated(int groupIndex, int activationMask) {
    return (activationMask & (1 << groupIndex)) != 0;
  }

  private static boolean isLayerActivated(Attributes dcm, int groupOffset, boolean pr) {
    return !pr || dcm.getString(Tag.OverlayActivationLayer | groupOffset) != null;
  }

  private static Optional<OverlayData> createOverlayData(Attributes dcm, int groupOffset) {
    var overlayDataBytes = DicomAttributeUtils.getByteData(dcm, Tag.OverlayData | groupOffset);

    if (overlayDataBytes.isEmpty()) {
      return Optional.empty();
    }

    var attributes = extractOverlayAttributes(dcm, groupOffset);

    return Optional.of(
        new OverlayData(
            groupOffset,
            attributes.rows(),
            attributes.columns(),
            attributes.imageFrameOrigin(),
            attributes.framesInOverlay(),
            attributes.origin(),
            overlayDataBytes.get()));
  }

  private static OverlayAttributes extractOverlayAttributes(Attributes dcm, int groupOffset) {
    int rows = dcm.getInt(Tag.OverlayRows | groupOffset, 0);
    int columns = dcm.getInt(Tag.OverlayColumns | groupOffset, 0);
    int imageFrameOrigin = dcm.getInt(Tag.ImageFrameOrigin | groupOffset, 1);
    int framesInOverlay = dcm.getInt(Tag.NumberOfFramesInOverlay | groupOffset, 1);
    int[] origin =
        DicomUtils.getIntArrayFromDicomElement(
            dcm, (Tag.OverlayOrigin | groupOffset), new int[] {1, 1});

    return new OverlayAttributes(rows, columns, imageFrameOrigin, framesInOverlay, origin);
  }

  /**
   * Applies overlays to the current image and returns the result.
   *
   * @param imageSource the source image
   * @param currentImage the current processed image
   * @param desc the image descriptor
   * @param params the image read parameters
   * @param frameIndex the frame index
   * @return the image with overlays applied
   */
  public static PlanarImage getOverlayImage(
      final PlanarImage imageSource,
      PlanarImage currentImage,
      ImageDescriptor desc,
      DicomImageReadParam params,
      int frameIndex) {
    if (imageSource == null || currentImage == null || desc == null || params == null) {
      return currentImage;
    }
    if (!hasSameDimensions(imageSource, currentImage)) {
      return currentImage;
    }
    ImageCV mask = getOverlayMask(imageSource, desc, params, frameIndex);
    if (mask == null) {
      return currentImage;
    }
    try {
      return applyOverlayMask(currentImage, mask, params);
    } finally {
      mask.release();
    }
  }

  /**
   * Builds the mask of the overlays of a frame: embedded in the pixel data, stored in the image and
   * brought by the presentation state. It does not depend on the rendering of the image, so a
   * caller can keep it while only the window changes.
   *
   * @param imageSource the stored pixel values, read for the embedded overlays
   * @return an 8-bit image, 255 where an overlay is set, or null when the frame has no overlay
   */
  public static ImageCV getOverlayMask(
      PlanarImage imageSource, ImageDescriptor desc, DicomImageReadParam params, int frameIndex) {
    var context = buildOverlayContext(desc, params);
    if (!context.hasOverlays()) {
      return null;
    }
    var dimensions = new ImageDimensions(imageSource.width(), imageSource.height());
    byte[] overlayPixelData = createOverlayPixelData(imageSource, context, frameIndex, dimensions);
    return createOverlayImage(overlayPixelData, dimensions);
  }

  /**
   * Paints a mask from {@link #getOverlayMask} on a rendered image, in the overlay color of the
   * parameters.
   *
   * @return a new image, or {@code currentImage} when the mask does not have its size
   */
  public static PlanarImage applyOverlayMask(
      PlanarImage currentImage, ImageCV mask, DicomImageReadParam params) {
    if (!hasSameDimensions(mask, currentImage)) {
      return currentImage;
    }
    var overlayColor = params.getOverlayColor().orElse(Color.WHITE);
    int bits = params.getOutputBits().orElse(8);
    if (bits > 8 && currentImage.type() == CvType.CV_16UC1) {
      return overlayWithinOutputRange(currentImage, mask, overlayColor, Math.min(bits, 16));
    }
    return ImageTransformer.overlay(currentImage.toMat(), mask, overlayColor);
  }

  private static OverlayContext buildOverlayContext(
      ImageDescriptor desc, DicomImageReadParam params) {

    var allOverlays =
        new ArrayList<>(
            params.getPresentationState().map(PrDicomObject::getOverlays).orElse(List.of()));

    // Add regular overlays
    allOverlays.addAll(desc.getOverlayData());

    return new OverlayContext(desc.getEmbeddedOverlay(), allOverlays, params);
  }

  private static boolean hasSameDimensions(PlanarImage image1, PlanarImage image2) {
    return image1.width() == image2.width() && image1.height() == image2.height();
  }

  // The windowed output spans [0, 2^bits - 1], not the full 16-bit range the transformer assumes.
  private static ImageCV overlayWithinOutputRange(
      PlanarImage image, ImageCV mask, Color color, int bits) {
    int component = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
    var result = new ImageCV();
    image.toMat().copyTo(result);
    result.setTo(new Scalar(component * ((1 << bits) - 1) / 255.0), mask);
    return result;
  }

  private static byte[] createOverlayPixelData(
      PlanarImage imageSource, OverlayContext context, int frameIndex, ImageDimensions dimensions) {

    byte[] pixelData = new byte[dimensions.totalPixels()];

    context
        .embeddedOverlays()
        .forEach(
            overlay -> {
              int mask = 1 << overlay.bitPosition();
              applyEmbeddedOverlayMask(imageSource, mask, pixelData);
            });

    applyRegularOverlays(context.overlays(), pixelData, frameIndex, dimensions.width());

    return pixelData;
  }

  private static void applyEmbeddedOverlayMask(
      PlanarImage imageSource, int mask, byte[] pixelData) {
    Mat stored = new Mat();
    Mat selected = new Mat();
    try {
      // Embedded overlays belong to single-sample images: only the first channel carries the bit
      Core.extractChannel(imageSource.toMat(), stored, 0);
      if (stored.depth() >= CvType.CV_32F) {
        stored.convertTo(stored, CvType.CV_32S);
      }
      try (ImageCV bit =
          ImageTransformer.bitwiseAnd(stored, storedBitPattern(mask, stored.depth()))) {
        Core.compare(bit, new Scalar(0), selected, Core.CMP_NE);
      }

      byte[] set = new byte[pixelData.length];
      selected.get(0, 0, set);
      for (int i = 0; i < set.length; i++) {
        if (set[i] != 0) {
          pixelData[i] = OVERLAY_PIXEL_VALUE;
        }
      }
    } finally {
      stored.release();
      selected.release();
    }
  }

  // A scalar is saturated to the Mat depth: the sign bit of a signed depth needs its negative value
  private static int storedBitPattern(int mask, int depth) {
    return switch (depth) {
      case CvType.CV_8S -> (byte) mask;
      case CvType.CV_16S -> (short) mask;
      default -> mask;
    };
  }

  private static void applyRegularOverlays(
      List<OverlayData> overlays, byte[] pixelData, int frameIndex, int imageWidth) {

    LangUtil.emptyIfNull(overlays).stream()
        .filter(overlay -> isOverlayApplicableToFrame(overlay, frameIndex))
        .forEach(overlay -> applyOverlayToPixelData(overlay, pixelData, frameIndex, imageWidth));
  }

  private static boolean isOverlayApplicableToFrame(OverlayData overlay, int frameIndex) {
    int overlayFrameIndex = frameIndex - overlay.imageFrameOrigin() + 1;
    return overlayFrameIndex >= 0 && overlayFrameIndex < overlay.framesInOverlay();
  }

  private static void applyOverlayToPixelData(
      OverlayData overlay, byte[] pixelData, int frameIndex, int imageWidth) {

    var frameInfo = calculateOverlayFrameInfo(overlay, frameIndex);
    applyOverlayBits(overlay, frameInfo, pixelData, imageWidth);
  }

  private static OverlayFrameInfo calculateOverlayFrameInfo(OverlayData overlay, int frameIndex) {
    int overlayFrameIndex = frameIndex - overlay.imageFrameOrigin() + 1;
    int overlayOffset = overlay.rows() * overlay.columns() * overlayFrameIndex;
    int originX = overlay.origin()[1] - 1; // Convert from 1-based to 0-based
    int originY = overlay.origin()[0] - 1; // Convert from 1-based to 0-based

    return new OverlayFrameInfo(overlayOffset, originX, originY, overlay.rows(), overlay.columns());
  }

  private static void applyOverlayBits(
      OverlayData overlay, OverlayFrameInfo frameInfo, byte[] pixelData, int imageWidth) {

    byte[] overlayData = overlay.data();
    int endRow = Math.min(frameInfo.originY() + frameInfo.height(), pixelData.length / imageWidth);
    int endCol = Math.min(frameInfo.originX() + frameInfo.width(), imageWidth);

    for (int row = Math.max(0, frameInfo.originY()); row < endRow; row++) {
      for (int col = Math.max(0, frameInfo.originX()); col < endCol; col++) {
        if (isOverlayPixelSet(overlayData, frameInfo, row, col)) {
          pixelData[row * imageWidth + col] = OVERLAY_PIXEL_VALUE;
        }
      }
    }
  }

  private static boolean isOverlayPixelSet(
      byte[] overlayData, OverlayFrameInfo frameInfo, int row, int col) {

    int pixelIndex =
        frameInfo.overlayOffset()
            + (row - frameInfo.originY()) * frameInfo.width()
            + (col - frameInfo.originX());
    int byteIndex = pixelIndex / 8;
    int bitIndex = pixelIndex % 8;

    return byteIndex < overlayData.length
        && ((overlayData[byteIndex] & 0xff) & (1 << bitIndex)) != 0;
  }

  private static ImageCV createOverlayImage(byte[] pixelData, ImageDimensions dimensions) {
    var overlay = new ImageCV(dimensions.height(), dimensions.width(), CvType.CV_8UC1);
    overlay.put(0, 0, pixelData);
    return overlay;
  }

  /**
   * Creates an overlay image from a list of overlays for a specific frame.
   *
   * @param imageSource the source image
   * @param overlays list of overlay data
   * @param frameIndex the frame index
   * @return the overlay image
   */
  public static PlanarImage getOverlayImage(
      PlanarImage imageSource, List<OverlayData> overlays, int frameIndex) {
    if (imageSource == null || overlays == null || overlays.isEmpty()) {
      return imageSource;
    }
    var dimensions = new ImageDimensions(imageSource.width(), imageSource.height());
    byte[] pixelData = new byte[dimensions.totalPixels()];

    applyRegularOverlays(overlays, pixelData, frameIndex, dimensions.width());

    return createOverlayImage(pixelData, dimensions);
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof OverlayData that
            && groupOffset == that.groupOffset
            && rows == that.rows
            && columns == that.columns
            && imageFrameOrigin == that.imageFrameOrigin
            && framesInOverlay == that.framesInOverlay
            && Arrays.equals(origin, that.origin)
            && Arrays.equals(data, that.data));
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
    return "OverlayData{groupOffset=%d, rows=%d, columns=%d}".formatted(groupOffset, rows, columns);
  }

  // Helper records

  private record OverlayAttributes( // NOSONAR only internal use
      int rows, int columns, int imageFrameOrigin, int framesInOverlay, int[] origin) {}

  private record OverlayContext(
      List<EmbeddedOverlay> embeddedOverlays,
      List<OverlayData> overlays,
      DicomImageReadParam params) {

    boolean hasOverlays() {
      return !embeddedOverlays.isEmpty() || !overlays.isEmpty();
    }
  }

  private record ImageDimensions(int width, int height) {
    int totalPixels() {
      return width * height;
    }
  }

  private record OverlayFrameInfo(
      int overlayOffset, int originX, int originY, int height, int width) {}
}
