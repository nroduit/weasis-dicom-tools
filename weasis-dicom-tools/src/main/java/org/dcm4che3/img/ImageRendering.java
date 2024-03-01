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
import org.weasis.opencv.op.ImageProcessor;
import org.weasis.opencv.op.lut.PresentationStateLut;

/**
 * @author Nicolas Roduit
 */
public class ImageRendering {

  private ImageRendering() {}

  public static PlanarImage getRawRenderedImage(
      final PlanarImage imageSource, ImageDescriptor desc, DicomImageReadParam params) {
    PlanarImage img = getImageWithoutEmbeddedOverlay(imageSource, desc);
    DicomImageAdapter adapter = new DicomImageAdapter(img, desc);
    return getModalityLutImage(imageSource, adapter, params);
  }

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

  public static PlanarImage getDefaultRenderedImage(
      final PlanarImage imageSource,
      ImageDescriptor desc,
      DicomImageReadParam params,
      int frameIndex) {
    PlanarImage img = getImageWithoutEmbeddedOverlay(imageSource, desc);
    img = getVoiLutImage(img, desc, params);
    return OverlayData.getOverlayImage(imageSource, img, desc, params, frameIndex);
  }

  public static PlanarImage getVoiLutImage(
      final PlanarImage imageSource, ImageDescriptor desc, DicomImageReadParam params) {
    DicomImageAdapter adapter = new DicomImageAdapter(imageSource, desc);
    return getVoiLutImage(imageSource, adapter, params);
  }

  public static PlanarImage getVoiLutImage(
      PlanarImage imageSource, DicomImageAdapter adapter, DicomImageReadParam params) {
    ImageDescriptor desc = adapter.getImageDescriptor();

    WindLevelParameters p = new WindLevelParameters(adapter, params);
    int datatype = Objects.requireNonNull(imageSource).type();

    if (datatype >= CvType.CV_8U && datatype < CvType.CV_32S) {
      LookupTableCV modalityLookup = adapter.getModalityLookup(p, p.isInverseLut());
      ImageCV imageModalityTransformed =
          modalityLookup == null
              ? imageSource.toImageCV()
              : modalityLookup.lookup(imageSource.toMat());

      /*
       * C.11.2.1.2 Window center and window width
       *
       * Theses Attributes shall be used only for Images with Photometric Interpretation (0028,0004) values of
       * MONOCHROME1 and MONOCHROME2. They have no meaning for other Images.
       */
      if ((!p.isAllowWinLevelOnColorImage()
              || MathUtil.isEqual(p.getWindow(), 255.0) && MathUtil.isEqual(p.getLevel(), 127.5))
          && !desc.getPhotometricInterpretation().isMonochrome()) {
        /*
         * If photometric interpretation is not monochrome do not apply VOILUT. It is necessary for
         * PALETTE_COLOR.
         */
        return imageModalityTransformed;
      }

      PresentationStateLut prDcm = p.getPresentationState();
      Optional<LookupTableCV> prLut = prDcm == null ? Optional.empty() : prDcm.getPrLut();
      LookupTableCV voiLookup = null;
      if (!prLut.isPresent() || p.getLutShape().getLookup() != null) {
        voiLookup = adapter.getVOILookup(p);
      }
      if (!prLut.isPresent()) {
        return voiLookup.lookup(imageModalityTransformed);
      }

      ImageCV imageVoiTransformed =
          voiLookup == null ? imageModalityTransformed : voiLookup.lookup(imageModalityTransformed);
      return prLut.get().lookup(imageVoiTransformed);

    } else if (datatype >= CvType.CV_32S) {
      double low = p.getLevel() - p.getWindow() / 2.0;
      double high = p.getLevel() + p.getWindow() / 2.0;
      double range = high - low;
      if (range < 1.0 && datatype == DataBuffer.TYPE_INT) {
        range = 1.0;
      }
      double slope = 255.0 / range;
      double yint = 255.0 - slope * high;

      return ImageProcessor.rescaleToByte(ImageCV.toMat(imageSource), slope, yint);
    }
    return null;
  }

  /**
   * For overlays encoded in Overlay Data Element (60xx,3000), Overlay Bits Allocated (60xx,0100) is
   * always 1 and Overlay Bit Position (60xx,0102) is always 0.
   *
   * @param img
   * @return the bit mask for removing the pixel overlay
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part05/chapter_8.html">8.1.2
   *     Overlay data encoding of related data elements</a>
   */
  public static PlanarImage getImageWithoutEmbeddedOverlay(PlanarImage img, ImageDescriptor desc) {
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
          desc.getModalityLUT().adaptWithOverlayBitMask(high - bitsStored);
        }

        // Set to 0 all bits outside bitStored
        return ImageProcessor.bitwiseAnd(img.toMat(), val);
      }
    }
    return img;
  }
}
