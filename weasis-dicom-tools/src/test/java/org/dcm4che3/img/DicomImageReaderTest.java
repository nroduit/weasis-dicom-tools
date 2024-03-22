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

/**
 * @author Nicolas Roduit
 */
@DisplayName("DicomImageReader")
public class DicomImageReaderTest {

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
    Assertions.assertEquals(19, imagesIn.size(), "The number of image frames doesn't match");
    for (PlanarImage img : imagesIn) {
      Assertions.assertEquals(256, img.width(), "The image width doesn't match");
      Assertions.assertEquals(256, img.height(), "The image height doesn't match");
    }
  }

  @Test
  @DisplayName("Read YBR_FULL with RLE compression")
  void ybrFullRLE() throws Exception {
    List<PlanarImage> imagesIn = readDicomImage("ybrFull-RLE.dcm");
    Assertions.assertEquals(1, imagesIn.size(), "The number of image frames doesn't match");
    for (PlanarImage img : imagesIn) {
      Assertions.assertEquals(640, img.width(), "The image width doesn't match");
      Assertions.assertEquals(480, img.height(), "The image height doesn't match");
    }
  }
}
