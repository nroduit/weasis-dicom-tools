/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.PaletteColorUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.natives.NativeLibrary;

/** Supplemental Palette Color LUT of a grayscale image (PS3.3 C.8.16.2). */
class SupplementalPaletteTest {

  private static final int FIRST_MAPPED = 100;

  @BeforeAll
  static void load_native_lib() {
    NativeLibrary.loadLibraryFromLibraryName();
  }

  @Test
  void the_upper_range_takes_the_palette_colors_and_the_lower_range_stays_gray() {
    ImageDescriptor desc = new ImageDescriptor(attributes("COLOR", "MONOCHROME2"));
    ImageCV stored = stored(0, 60, 100, 101);

    PlanarImage rendered = render(stored, desc, null);
    PlanarImage palette =
        PaletteColorUtils.getRGBImageFromPaletteColorModel(
            stored, desc.getPaletteColorLookupTable());

    double[] gray = rendered.toMat().get(0, 1);
    assertAll(
        () -> assertTrue(desc.hasSupplementalPaletteColorLookupTable()),
        () -> assertEquals(3, rendered.channels()),
        () -> assertEquals(gray[0], gray[1]),
        () -> assertEquals(gray[1], gray[2]),
        () -> assertNotEquals(0.0, gray[0], "windowed gray level, not the palette"),
        () -> assertArrayEquals(palette.toMat().get(0, 2), rendered.toMat().get(0, 2)),
        () -> assertArrayEquals(palette.toMat().get(0, 3), rendered.toMat().get(0, 3)),
        () -> assertNotEquals(rendered.toMat().get(0, 2)[0], rendered.toMat().get(0, 2)[2]));
  }

  @Test
  void the_whole_range_can_be_displayed_in_grayscale() {
    ImageDescriptor desc = new ImageDescriptor(attributes("COLOR", "MONOCHROME2"));
    DicomImageReadParam params = new DicomImageReadParam();
    params.setApplySupplementalPalette(false);

    PlanarImage rendered = render(stored(0, 60, 100, 101), desc, params);

    assertEquals(1, rendered.channels());
  }

  @Test
  void a_monochrome_pixel_presentation_has_no_supplemental_palette() {
    assertFalse(
        new ImageDescriptor(attributes("MONOCHROME", "MONOCHROME2"))
            .hasSupplementalPaletteColorLookupTable());
    assertFalse(
        new ImageDescriptor(attributes(null, "PALETTE COLOR"))
            .hasSupplementalPaletteColorLookupTable(),
        "a palette color image is converted when read");
  }

  private static PlanarImage render(
      ImageCV stored, ImageDescriptor desc, DicomImageReadParam params) {
    DicomImageReadParam p = params == null ? new DicomImageReadParam() : params;
    p.setWindowCenter(60.0);
    p.setWindowWidth(120.0);
    DicomImageAdapter adapter = new DicomImageAdapter(stored, desc, 0);
    return ImageRendering.getVoiLutImage(stored, adapter, p);
  }

  private static ImageCV stored(int... values) {
    ImageCV image = new ImageCV(1, values.length, CvType.CV_16UC1);
    for (int i = 0; i < values.length; i++) {
      image.put(0, i, values[i]);
    }
    return image;
  }

  // 12-bit image with a 4-entry, 8-bit palette mapping stored values 100 to 103
  private static Attributes attributes(String pixelPresentation, String photometric) {
    Attributes ds = new Attributes();
    ds.setInt(Tag.Rows, VR.US, 1);
    ds.setInt(Tag.Columns, VR.US, 4);
    ds.setInt(Tag.SamplesPerPixel, VR.US, 1);
    ds.setString(Tag.PhotometricInterpretation, VR.CS, photometric);
    ds.setInt(Tag.BitsAllocated, VR.US, 16);
    ds.setInt(Tag.BitsStored, VR.US, 12);
    ds.setInt(Tag.HighBit, VR.US, 11);
    ds.setInt(Tag.PixelRepresentation, VR.US, 0);
    if (pixelPresentation != null) {
      ds.setString(Tag.PixelPresentation, VR.CS, pixelPresentation);
    }
    int[] descriptor = {4, FIRST_MAPPED, 8};
    ds.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, descriptor);
    ds.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, descriptor);
    ds.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, descriptor);
    ds.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, new byte[] {(byte) 255, 0, 0, 0});
    ds.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, new byte[] {0, (byte) 255, 0, 0});
    ds.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, new byte[] {0, 0, (byte) 255, 0});
    return ds;
  }
}
