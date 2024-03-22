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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.opencv.data.PlanarImage;

class DicomImageReaderTest {

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
  @DisplayName("Read lossy JPEG2000 multiframe with multi-fragments stream")
  void jpeg2000LossyMultiframe() throws Exception {
    List<PlanarImage> imagesIn = readDicomImage("jpeg2000-multiframe-multifragments.dcm");
    assertEquals(19, imagesIn.size(), "The number of image frames doesn't match");
    assertEquals(19, reader.getNumImages(true));
    assertEquals(256, reader.getWidth(0));
    assertEquals(256, reader.getHeight(0));
    Assertions.assertThrows(UnsupportedOperationException.class, () -> reader.getImageTypes(0));
    assertInstanceOf(DicomImageReadParam.class, reader.getDefaultReadParam());
    assertNull(reader.getImageMetadata(0));
    assertTrue(reader.canReadRaster());

    for (PlanarImage img : imagesIn) {
      assertEquals(256, img.width(), "The image width doesn't match");
      assertEquals(256, img.height(), "The image height doesn't match");
    }
  }

  @Test
  @DisplayName("Read YBR_FULL with RLE compression")
  void ybrFullRLE() throws Exception {
    List<PlanarImage> imagesIn = readDicomImage("ybrFull-RLE.dcm");
    assertEquals(1, imagesIn.size(), "The number of image frames doesn't match");
    for (PlanarImage img : imagesIn) {
      assertEquals(640, img.width(), "The image width doesn't match");
      assertEquals(480, img.height(), "The image height doesn't match");
    }

    DicomImageReadParam params = new DicomImageReadParam();
    params.setSourceRenderSize(new Dimension(320, 240));
    Raster raster = reader.readRaster(0, params);
    assertEquals(320, raster.getWidth());
    assertEquals(240, raster.getHeight());
    assertNotNull(reader.readRaster(0, null));

    BufferedImage bufferedImage = reader.read(0, null);
    assertEquals(640, bufferedImage.getWidth());
    assertEquals(480, bufferedImage.getHeight());

    assertEquals(1, reader.getPlanarImages().size());
    assertNotNull(reader.getPlanarImage());
    assertEquals(1, reader.getNumImages(true));
    assertEquals(640, reader.getWidth(0));
    assertEquals(480, reader.getHeight(0));
    Assertions.assertThrows(UnsupportedOperationException.class, () -> reader.getImageTypes(0));
    assertInstanceOf(DicomImageReadParam.class, reader.getDefaultReadParam());
    assertNull(reader.getImageMetadata(0));
  }
}
