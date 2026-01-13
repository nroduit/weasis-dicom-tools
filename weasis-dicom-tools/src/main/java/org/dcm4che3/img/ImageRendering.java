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

import java.awt.image.DataBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.img.data.EmbeddedOverlay;
import org.dcm4che3.img.data.OverlayData;
import org.dcm4che3.img.lut.WindLevelParameters;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.opencv.core.CvType;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;

/**
 * Utility class for DICOM image rendering and transformation operations.
 *
 * <p>This class provides static methods for applying various DICOM-specific transformations
 * including modality LUT, VOI (Value of Interest) LUT, and overlay processing. It handles the
 * complex pixel value transformations required for proper DICOM image display.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Modality LUT application for raw pixel value transformation
 *   <li>VOI LUT processing for windowing and leveling
 *   <li>Embedded overlay extraction and processing
 *   <li>Support for various pixel data types and color spaces
 * </ul>
 *
 * @author Nicolas Roduit
 */
public final class ImageRendering {

  private ImageRendering() {}

  /**
   * Returns the raw rendered image with modality LUT applied and without embedded overlays.
   *
   * @param imageSource the source image (must not be null)
   * @param desc the image descriptor containing modality LUT and overlay information (must not be
   *     null)
   * @param params the DicomImageReadParam containing window/level parameters (must not be null)
   * @param frameIndex the index of the frame to process (0 for single-frame images)
   * @return the raw rendered image with modality LUT applied
   * @throws NullPointerException if any parameter is null
   */
  public static PlanarImage getRawRenderedImage(
      PlanarImage imageSource, ImageDescriptor desc, DicomImageReadParam params, int frameIndex) {
    var imageWithoutOverlay = getImageWithoutEmbeddedOverlay(imageSource, desc, frameIndex);
    var adapter = new DicomImageAdapter(imageWithoutOverlay, desc, frameIndex);
    return getModalityLutImage(imageSource, adapter, params);
  }

  /**
   * Returns the image with modality LUT applied based on the provided adapter and parameters.
   *
   * @param img the source image (must not be null)
   * @param adapter the DicomImageAdapter containing modality LUT information (must not be null)
   * @param params the DicomImageReadParam containing window/level parameters
   * @return the image with modality LUT applied, or the original image if no transformation is
   *     needed
   * @throws NullPointerException if any parameter is null
   */
  public static PlanarImage getModalityLutImage(
      PlanarImage img, DicomImageAdapter adapter, DicomImageReadParam params) {
    var windLevelParams = new WindLevelParameters(adapter, params);
    int dataType = Objects.requireNonNull(img).type();

    return isIntegerDataType(dataType)
        ? applyModalityLutToIntegerData(img, adapter, windLevelParams)
        : img;
  }

  /**
   * Returns the default rendered image with VOI LUT and overlays applied.
   *
   * @param imageSource the source image (must not be null)
   * @param desc the image descriptor containing VOI LUT and overlay information (must not be null)
   * @param params the DicomImageReadParam containing window/level parameters (must not be null)
   * @param frameIndex the index of the frame to process (0 for single-frame images)
   * @return the fully rendered image with VOI LUT and overlays applied
   * @throws NullPointerException if any parameter is null
   */
  public static PlanarImage getDefaultRenderedImage(
      PlanarImage imageSource, ImageDescriptor desc, DicomImageReadParam params, int frameIndex) {
    var imageWithoutOverlay = getImageWithoutEmbeddedOverlay(imageSource, desc, frameIndex);
    var voiProcessedImage = getVoiLutImage(imageWithoutOverlay, desc, params, frameIndex);
    return OverlayData.getOverlayImage(imageSource, voiProcessedImage, desc, params, frameIndex);
  }

  /**
   * Applies VOI LUT transformation to the specified image.
   *
   * @param imageSource the source image (must not be null)
   * @param desc the image descriptor containing VOI LUT information (must not be null)
   * @param params the DicomImageReadParam containing window/level parameters (must not be null)
   * @param frameIndex the index of the frame to process (0 for single-frame images)
   * @return the image with VOI LUT applied
   * @throws NullPointerException if any parameter is null
   */
  public static PlanarImage getVoiLutImage(
      PlanarImage imageSource, ImageDescriptor desc, DicomImageReadParam params, int frameIndex) {
    var adapter = new DicomImageAdapter(imageSource, desc, frameIndex);
    return getVoiLutImage(imageSource, adapter, params);
  }

  /**
   * Applies VOI LUT transformation using the provided adapter.
   *
   * @param imageSource the source image (must not be null)
   * @param adapter the DicomImageAdapter containing VOI LUT information (must not be null)
   * @param params the DicomImageReadParam containing window/level parameters (must not be null)
   * @return the image with VOI LUT applied, or null if the data type is not supported
   * @throws NullPointerException if any parameter is null
   */
  public static PlanarImage getVoiLutImage(
      PlanarImage imageSource, DicomImageAdapter adapter, DicomImageReadParam params) {
    var windLevelParams = new WindLevelParameters(adapter, params);
    int dataType = Objects.requireNonNull(imageSource).type();

    return switch (getDataTypeCategory(dataType)) {
      case INTEGER -> processIntegerDataForVoi(imageSource, adapter, windLevelParams);
      case FLOATING_POINT -> processFloatingPointDataForVoi(imageSource, windLevelParams, dataType);
      case UNSUPPORTED -> null;
    };
  }

  /**
   * Removes embedded overlays from the image by masking overlay bits.
   *
   * <p>For overlays encoded in Overlay Data Element (60xx,3000), Overlay Bits Allocated (60xx,0100)
   * is always 1 and Overlay Bit Position (60xx,0102) is always 0.
   *
   * @param img the source image (must not be null)
   * @param desc the image descriptor containing overlay information (must not be null)
   * @param frameIndex the index of the frame to process (0 for single-frame images)
   * @return the image without embedded overlays
   * @throws NullPointerException if any parameter is null
   * @see <a href="http://dicom.nema.org/medical/dicom/current/output/chtml/part05/chapter_8.html">
   *     8.1.2 Overlay data encoding of related data elements</a>
   */
  public static PlanarImage getImageWithoutEmbeddedOverlay(
      PlanarImage img, ImageDescriptor desc, int frameIndex) {
    Objects.requireNonNull(img);
    List<EmbeddedOverlay> embeddedOverlays = Objects.requireNonNull(desc).getEmbeddedOverlay();

    if (embeddedOverlays.isEmpty()) {
      return img;
    }

    int bitsStored = desc.getBitsStored();
    int bitsAllocated = desc.getBitsAllocated();

    if (shouldApplyOverlayMask(bitsStored, bitsAllocated)) {
      int overlayMask = calculateOverlayMask(desc, frameIndex, bitsStored);
      return ImageTransformer.bitwiseAnd(img.toMat(), overlayMask);
    }

    return img;
  }

  // ======= Private helper methods =======

  /** Data type categories for processing logic */
  private enum DataTypeCategory {
    INTEGER,
    FLOATING_POINT,
    UNSUPPORTED
  }

  private static DataTypeCategory getDataTypeCategory(int dataType) {
    if (dataType >= CvType.CV_8U && dataType < CvType.CV_32S) {
      return DataTypeCategory.INTEGER;
    }
    if (dataType >= CvType.CV_32S) {
      return DataTypeCategory.FLOATING_POINT;
    }
    return DataTypeCategory.UNSUPPORTED;
  }

  private static boolean isIntegerDataType(int dataType) {
    return getDataTypeCategory(dataType) == DataTypeCategory.INTEGER;
  }

  private static ImageCV applyModalityLutToIntegerData(
      PlanarImage img, DicomImageAdapter adapter, WindLevelParameters params) {
    var modalityLookup = adapter.getModalityLookup(params, params.isInverseLut());
    return modalityLookup == null ? img.toImageCV() : modalityLookup.lookup(img.toMat());
  }

  private static ImageCV processIntegerDataForVoi(
      PlanarImage imageSource, DicomImageAdapter adapter, WindLevelParameters params) {
    var modalityLookup = adapter.getModalityLookup(params, params.isInverseLut());
    var imageModalityTransformed = applyModalityTransformation(imageSource, modalityLookup);

    if (shouldSkipVoiLutForColorImage(params, adapter.getImageDescriptor())) {
      return imageModalityTransformed;
    }

    return applyVoiAndPresentationLuts(imageModalityTransformed, adapter, params);
  }

  private static ImageCV applyModalityTransformation(
      PlanarImage imageSource, LookupTableCV modalityLookup) {
    return modalityLookup == null
        ? imageSource.toImageCV()
        : modalityLookup.lookup(imageSource.toMat());
  }

  private static boolean shouldSkipVoiLutForColorImage(
      WindLevelParameters params, ImageDescriptor desc) {
    /*
     * C.11.2.1.2 Window center and window width
     *
     * These Attributes shall be used only for Images with Photometric Interpretation (0028,0004) values of
     * MONOCHROME1 and MONOCHROME2. They have no meaning for other Images.
     */
    var photometricInterpretation = desc.getPhotometricInterpretation();
    if (photometricInterpretation.isMonochrome()) {
      return false;
    }

    boolean isDefaultWindow =
        MathUtil.isEqual(params.getWindow(), 255.0) && MathUtil.isEqual(params.getLevel(), 127.5);
    return !params.isAllowWinLevelOnColorImage() || isDefaultWindow;
  }

  private static ImageCV applyVoiAndPresentationLuts(
      ImageCV imageModalityTransformed, DicomImageAdapter adapter, WindLevelParameters params) {
    var presentationState = params.getPresentationState();
    Optional<LookupTableCV> presentationLut =
        presentationState == null ? Optional.empty() : presentationState.getPrLut();
    LookupTableCV voiLookup = null;
    if (presentationLut.isEmpty() || params.getLutShape().getLookup() != null) {
      voiLookup = adapter.getVOILookup(params);
    }
    if (presentationLut.isEmpty()) {
      return voiLookup.lookup(imageModalityTransformed);
    }

    var imageVoiTransformed =
        voiLookup == null ? imageModalityTransformed : voiLookup.lookup(imageModalityTransformed);

    return presentationLut.get().lookup(imageVoiTransformed);
  }

  private static ImageCV processFloatingPointDataForVoi(
      PlanarImage imageSource, WindLevelParameters params, int dataType) {
    double window = params.getWindow();
    double level = params.getLevel();

    double low = level - window / 2.0;
    double high = level + window / 2.0;
    double range = calculateRange(high, low, dataType);
    double slope = 255.0 / range;
    double yIntercept = 255.0 - slope * high;

    return ImageTransformer.rescaleToByte(ImageCV.toMat(imageSource), slope, yIntercept);
  }

  private static double calculateRange(double high, double low, int dataType) {
    double range = high - low;
    // Ensure minimum range for integer data types
    return (range < 1.0 && dataType == DataBuffer.TYPE_INT) ? 1.0 : range;
  }

  private static boolean shouldApplyOverlayMask(int bitsStored, int bitsAllocated) {
    return bitsStored < bitsAllocated && bitsAllocated >= 8 && bitsAllocated <= 16;
  }

  private static int calculateOverlayMask(ImageDescriptor desc, int frameIndex, int bitsStored) {
    int highBit = desc.getHighBit();
    int high = highBit + 1;
    int mask = (1 << high) - 1;
    if (high > bitsStored) {
      mask -= (1 << (high - bitsStored)) - 1;
      adaptModalityLutForOverlay(desc, frameIndex, high - bitsStored);
    }
    return mask;
  }

  private static void adaptModalityLutForOverlay(
      ImageDescriptor desc, int frameIndex, int overlayBits) {
    var modalityLut = desc.getModalityLutForFrame(frameIndex);
    if (modalityLut != null && !modalityLut.isOverlayBitMaskApplied()) {
      var updatedLut = modalityLut.withOverlayBitMask(overlayBits);
      desc.setModalityLutForFrame(frameIndex, updatedLut);
    }
  }
}
