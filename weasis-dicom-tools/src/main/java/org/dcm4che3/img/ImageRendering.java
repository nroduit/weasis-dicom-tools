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
import org.dcm4che3.img.lut.ModalityLutModule;
import org.dcm4che3.img.lut.WindLevelParameters;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.opencv.core.CvType;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;
import org.weasis.opencv.op.lut.PresentationStateLut;

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
public class ImageRendering {

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
      final PlanarImage imageSource,
      ImageDescriptor desc,
      DicomImageReadParam params,
      int frameIndex) {
    PlanarImage img = getImageWithoutEmbeddedOverlay(imageSource, desc, frameIndex);
    DicomImageAdapter adapter = new DicomImageAdapter(img, desc, frameIndex);
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
    WindLevelParameters p = new WindLevelParameters(adapter, params);
    int datatype = Objects.requireNonNull(img).type();

    if (isIntegerDataType(datatype)) {
      return applyModalityLutToIntegerData(img, adapter, p);
    }
    return img;
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
      final PlanarImage imageSource,
      ImageDescriptor desc,
      DicomImageReadParam params,
      int frameIndex) {
    PlanarImage img = getImageWithoutEmbeddedOverlay(imageSource, desc, frameIndex);
    img = getVoiLutImage(img, desc, params, frameIndex);
    return OverlayData.getOverlayImage(imageSource, img, desc, params, frameIndex);
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
      final PlanarImage imageSource,
      ImageDescriptor desc,
      DicomImageReadParam params,
      int frameIndex) {
    DicomImageAdapter adapter = new DicomImageAdapter(imageSource, desc, frameIndex);
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
    WindLevelParameters p = new WindLevelParameters(adapter, params);
    int datatype = Objects.requireNonNull(imageSource).type();

    if (isIntegerDataType(datatype)) {
      return processIntegerDataForVoi(imageSource, adapter, p);
    } else if (isFloatingPointDataType(datatype)) {
      return processFloatingPointDataForVoi(imageSource, p, datatype);
    }
    return null;
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

  private static boolean isIntegerDataType(int datatype) {
    return datatype >= CvType.CV_8U && datatype < CvType.CV_32S;
  }

  private static boolean isFloatingPointDataType(int datatype) {
    return datatype >= CvType.CV_32S;
  }

  private static ImageCV applyModalityLutToIntegerData(
      PlanarImage img, DicomImageAdapter adapter, WindLevelParameters p) {
    LookupTableCV modalityLookup = adapter.getModalityLookup(p, p.isInverseLut());
    return modalityLookup == null ? img.toImageCV() : modalityLookup.lookup(img.toMat());
  }

  private static ImageCV processIntegerDataForVoi(
      PlanarImage imageSource, DicomImageAdapter adapter, WindLevelParameters p) {
    ImageDescriptor desc = adapter.getImageDescriptor();
    LookupTableCV modalityLookup = adapter.getModalityLookup(p, p.isInverseLut());
    ImageCV imageModalityTransformed = applyModalityTransformation(imageSource, modalityLookup);

    if (shouldSkipVoiLutForColorImage(p, desc)) {
      return imageModalityTransformed;
    }

    return applyVoiAndPresentationLuts(imageModalityTransformed, adapter, p);
  }

  private static ImageCV applyModalityTransformation(
      PlanarImage imageSource, LookupTableCV modalityLookup) {
    return modalityLookup == null
        ? imageSource.toImageCV()
        : modalityLookup.lookup(imageSource.toMat());
  }

  private static boolean shouldSkipVoiLutForColorImage(
      WindLevelParameters p, ImageDescriptor desc) {
    /*
     * C.11.2.1.2 Window center and window width
     *
     * These Attributes shall be used only for Images with Photometric Interpretation (0028,0004) values of
     * MONOCHROME1 and MONOCHROME2. They have no meaning for other Images.
     */
    return (!p.isAllowWinLevelOnColorImage()
            || MathUtil.isEqual(p.getWindow(), 255.0) && MathUtil.isEqual(p.getLevel(), 127.5))
        && !desc.getPhotometricInterpretation().isMonochrome();
  }

  private static ImageCV applyVoiAndPresentationLuts(
      ImageCV imageModalityTransformed, DicomImageAdapter adapter, WindLevelParameters p) {
    PresentationStateLut prDcm = p.getPresentationState();
    Optional<LookupTableCV> prLut = prDcm == null ? Optional.empty() : prDcm.getPrLut();
    LookupTableCV voiLookup = null;
    if (prLut.isEmpty() || p.getLutShape().getLookup() != null) {
      voiLookup = adapter.getVOILookup(p);
    }
    if (prLut.isEmpty()) {
      return voiLookup.lookup(imageModalityTransformed);
    }

    ImageCV imageVoiTransformed =
        voiLookup == null ? imageModalityTransformed : voiLookup.lookup(imageModalityTransformed);
    return prLut.get().lookup(imageVoiTransformed);
  }

  private static ImageCV processFloatingPointDataForVoi(
      PlanarImage imageSource, WindLevelParameters p, int datatype) {
    double low = p.getLevel() - p.getWindow() / 2.0;
    double high = p.getLevel() + p.getWindow() / 2.0;
    double range = calculateRange(high, low, datatype);
    double slope = 255.0 / range;
    double yint = 255.0 - slope * high;

    return ImageTransformer.rescaleToByte(ImageCV.toMat(imageSource), slope, yint);
  }

  private static double calculateRange(double high, double low, int datatype) {
    double range = high - low;
    if (range < 1.0 && datatype == DataBuffer.TYPE_INT) {
      range = 1.0;
    }
    return range;
  }

  private static boolean shouldApplyOverlayMask(int bitsStored, int bitsAllocated) {
    return bitsStored < bitsAllocated && bitsAllocated >= 8 && bitsAllocated <= 16;
  }

  private static int calculateOverlayMask(ImageDescriptor desc, int frameIndex, int bitsStored) {
    int highBit = desc.getHighBit();
    int high = highBit + 1;
    int val = (1 << high) - 1;
    if (high > bitsStored) {
      val -= (1 << (high - bitsStored)) - 1;
      adaptModalityLutForOverlay(desc, frameIndex, high - bitsStored);
    }
    return val;
  }

  private static void adaptModalityLutForOverlay(
      ImageDescriptor desc, int frameIndex, int overlayBits) {
    ModalityLutModule modLut = desc.getModalityLutForFrame(frameIndex);
    if (modLut != null && !modLut.isOverlayBitMaskApplied()) {
      modLut.adaptWithOverlayBitMask(overlayBits);
    }
  }
}
