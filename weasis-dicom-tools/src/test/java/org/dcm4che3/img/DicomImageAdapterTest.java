/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.lut.WindLevelParameters;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.WlPresentation;

class DicomImageAdapterTest {
  static final Path IN_DIR =
      FileSystems.getDefault().getPath("target/test-classes/org/dcm4che3/img");
  static DicomImageReader reader;

  @BeforeAll
  static void setUp() {
    reader = new DicomImageReader(new DicomImageReaderSpi());
  }

  @AfterAll
  static void tearDown() {
    if (reader != null) reader.dispose();
  }

  private List<PlanarImage> readDicomImage(String filename) throws IOException {
    reader.setInput(
        new DicomFileInputStream(FileSystems.getDefault().getPath(IN_DIR.toString(), filename)));
    return reader.getPlanarImages(null);
  }

  @Test
  @DisplayName("Read YBR_FULL with RLE compression")
  void ybrFullRLE() throws Exception {
    DicomImageReadParam readParam = new DicomImageReadParam();
    DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    reader.setInput(
        new DicomFileInputStream(
            FileSystems.getDefault().getPath(IN_DIR.toString(), "ybrFull-RLE.dcm")));
    ImageDescriptor desc = reader.getImageDescriptor();
    PlanarImage imageSource = reader.getPlanarImage(0, readParam);
    PlanarImage img = ImageRendering.getImageWithoutEmbeddedOverlay(imageSource, desc);
    DicomImageAdapter adapter = new DicomImageAdapter(img, desc);
    assertEquals(0, adapter.getPresetCollectionSize());
    assertEquals(8, adapter.getBitsStored());
    assertEquals(255, adapter.getMinMax().maxVal);
    assertEquals(0, adapter.getMinMax().minVal);

    WlPresentation wlp = new WindLevelParameters(adapter);
    assertEquals(127.5, adapter.getDefaultLevel(wlp));
    assertEquals(255.0, adapter.getDefaultWindow(wlp));

    img = ImageRendering.getModalityLutImage(imageSource, adapter, readParam);
    assertEquals(640, img.width());

    Attributes attributes = new Attributes();
    attributes.setInt(Tag.BitsAllocated, VR.US, 8);
    attributes.setInt(Tag.BitsStored, VR.US, 6);
    attributes.setInt(Tag.PixelPaddingValue, VR.US, 20);
    ImageDescriptor descriptor = new ImageDescriptor(attributes);
    adapter = new DicomImageAdapter(img, descriptor);
    assertEquals(8, adapter.getBitsStored());
  }

  @Test
  @DisplayName("Read MR frame with JPEG-Lossless compression")
  void readMR() throws Exception {
    DicomImageReadParam readParam = new DicomImageReadParam();
    DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    reader.setInput(
        new DicomFileInputStream(
            FileSystems.getDefault().getPath(IN_DIR.toString(), "MR-JPEGLosslessSV1.dcm")));
    ImageDescriptor desc = reader.getImageDescriptor();
    PlanarImage imageSource = reader.getPlanarImage(0, readParam);
    PlanarImage img = ImageRendering.getImageWithoutEmbeddedOverlay(imageSource, desc);
    DicomImageAdapter adapter = new DicomImageAdapter(img, desc);
    assertEquals(0, adapter.getPresetCollectionSize());
    assertEquals(8, adapter.getBitsStored());
    assertEquals(48, adapter.getMinMax().maxVal);
    assertEquals(0, adapter.getMinMax().minVal);

    WlPresentation wlp = new WindLevelParameters(adapter);
    assertEquals(22.3726703241119, adapter.getDefaultLevel(wlp));
    assertEquals(44.5507956888837, adapter.getDefaultWindow(wlp));

    img = ImageRendering.getModalityLutImage(imageSource, adapter, readParam);
    assertEquals(256, img.width());
  }
}
