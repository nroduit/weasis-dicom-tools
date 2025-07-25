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
import org.weasis.opencv.op.lut.PresentationStateLut;

/**
 * @author Nicolas Roduit
 */
public class ImageRendering {

  private ImageRendering() {}

  /**
   * Returns the raw rendered image with modality LUT applied and without embedded overlays.
   *
   * @param imageSource the source image
   * @param desc the image descriptor containing modality LUT and overlay information
   * @param params the DicomImageReadParam containing window/level parameters
   * @param frameIndex the index of the frame to process (0 for single-frame images)
   * @return the raw rendered image with modality LUT applied
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
   * Returns the raw rendered image with modality LUT applied.
   *
   * @param img the source image
   * @param adapter the DicomImageAdapter containing modality LUT information
   * @param params the DicomImageReadParam containing window/level parameters
   * @return the raw rendered image with modality LUT applied
   */
  public static PlanarImage getModalityLutImage(
      PlanarImage img, DicomImageAdapter adapter, DicomImageReadParam params) {
    WindLevelParameters p = new WindLevelParameters(adapter, params);
    int datatype = Objects.requireNonNull(img).type();

    if (datatype >= CvType.CV_8U && datatype < CvType.CV_32S) {
      LookupTableCV modalityLookup = adapter.getModalityLookup(p, p.isInverseLut());
      return modalityLookup == null ? img.toImageCV() : modalityLookup.lookup(img.toMat());
    }
    return img;
  }

  /**
   * Returns the default rendered image with VOI LUT and without embedded overlays.
   *
   * @param imageSource the source image
   * @param desc the image descriptor containing VOI LUT and overlay information
   * @param params the DicomImageReadParam containing window/level parameters
   * @param frameIndex the index of the frame to process (0 for single-frame images)
   * @return the default rendered image with VOI LUT and overlays applied
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
   * Returns an image with the VOI LUT applied.
   *
   * @param imageSource the source image
   * @param desc the image descriptor containing VOI LUT information
   * @param params the DicomImageReadParam containing window/level parameters
   * @param frameIndex the index of the frame to process (0 for single-frame images)
   * @return the image with VOI LUT applied
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
   * Returns an image with the VOI LUT applied.
   *
   * @param imageSource the source image
   * @param adapter the DicomImageAdapter containing VOI LUT information
   * @param params the DicomImageReadParam containing window/level parameters
   * @return the image with VOI LUT applied
   */
  public static PlanarImage getVoiLutImage(
      PlanarImage imageSource, DicomImageAdapter adapter, DicomImageReadParam params) {
    WindLevelParameters p = new WindLevelParameters(adapter, params);
    int datatype = Objects.requireNonNull(imageSource).type();

    if (datatype >= CvType.CV_8U && datatype < CvType.CV_32S) {
      return getImageForByteOrShortData(imageSource, adapter, p);
    } else if (datatype >= CvType.CV_32S) {
      return getImageWithFloatOrIntData(imageSource, p, datatype);
    }
    return null;
  }

  private static ImageCV getImageForByteOrShortData(
      PlanarImage imageSource, DicomImageAdapter adapter, WindLevelParameters p) {
    ImageDescriptor desc = adapter.getImageDescriptor();
    LookupTableCV modalityLookup = adapter.getModalityLookup(p, p.isInverseLut());
    ImageCV imageModalityTransformed =
        modalityLookup == null
            ? imageSource.toImageCV()
            : modalityLookup.lookup(imageSource.toMat());

    /*
     * C.11.2.1.2 Window center and window width
     *
     * These Attributes shall be used only for Images with Photometric Interpretation (0028,0004) values of
     * MONOCHROME1 and MONOCHROME2. They have no meaning for other Images.
     */
    if ((!p.isAllowWinLevelOnColorImage()
            || MathUtil.isEqual(p.getWindow(), 255.0) && MathUtil.isEqual(p.getLevel(), 127.5))
        && !desc.getPhotometricInterpretation().isMonochrome()) {
      /*
       * If photometric interpretation is not monochrome, do not apply VOI LUT. It is necessary for
       * PALETTE_COLOR.
       */
      return imageModalityTransformed;
    }

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

  private static ImageCV getImageWithFloatOrIntData(
      PlanarImage imageSource, WindLevelParameters p, int datatype) {
    double low = p.getLevel() - p.getWindow() / 2.0;
    double high = p.getLevel() + p.getWindow() / 2.0;
    double range = high - low;
    if (range < 1.0 && datatype == DataBuffer.TYPE_INT) {
      range = 1.0;
    }
    double slope = 255.0 / range;
    double yint = 255.0 - slope * high;

    return ImageTransformer.rescaleToByte(ImageCV.toMat(imageSource), slope, yint);
  }

  /**
   * Returns an image without embedded overlays. If the image has embedded overlays, it will clear
   * the bits outside the range defined by bitsStored and highBit.
   *
   * <p>For overlays encoded in Overlay Data Element (60xx,3000), Overlay Bits Allocated (60xx,0100)
   * is always 1 and Overlay Bit Position (60xx,0102) is always 0.
   *
   * @param img the source image
   * @param desc the image descriptor containing overlay information
   * @param frameIndex the index of the frame to process (0 for single-frame images)
   * @return the image without embedded overlays
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part05/chapter_8.html">8.1.2
   *     Overlay data encoding of related data elements</a>
   */
  public static PlanarImage getImageWithoutEmbeddedOverlay(
      PlanarImage img, ImageDescriptor desc, int frameIndex) {
    Objects.requireNonNull(img);
    List<EmbeddedOverlay> embeddedOverlays = Objects.requireNonNull(desc).getEmbeddedOverlay();
    if (!embeddedOverlays.isEmpty()) {
      int bitsStored = desc.getBitsStored();
      int bitsAllocated = desc.getBitsAllocated();
      if (bitsStored < desc.getBitsAllocated() && bitsAllocated >= 8 && bitsAllocated <= 16) {
        int highBit = desc.getHighBit();
        int high = highBit + 1;
        int val = (1 << high) - 1;
        if (high > bitsStored) {
          val -= (1 << (high - bitsStored)) - 1;
        }
        // Set to 0 all bits upper than highBit and if lower than high-bitsStored (=> all bits
        // outside bitStored)
        if (high > bitsStored) {
          desc.getModalityLutForFrame(frameIndex).adaptWithOverlayBitMask(high - bitsStored);
        }

        // Set to 0 all bits outside bitStored
        return ImageTransformer.bitwiseAnd(img.toMat(), val);
      }
    }
    return img;
  }
}
