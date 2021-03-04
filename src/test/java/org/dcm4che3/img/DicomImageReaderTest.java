/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.log4j.BasicConfigurator;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.weasis.opencv.data.PlanarImage;

/** @author Nicolas Roduit */
public class DicomImageReaderTest {
  static Path IN_DIR;
  static DicomImageReader reader;

  @BeforeClass
  public static void setUp() throws URISyntaxException {
    IN_DIR = Paths.get(DicomImageReaderTest.class.getResource("").toURI());
    BasicConfigurator.configure();
    reader = new DicomImageReader(new DicomImageReaderSpi());
  }

  @AfterClass
  public static void tearDown() {
    if (reader != null) reader.dispose();
  }

  private List<PlanarImage> readDicomImage(String filename) throws IOException {
    reader.setInput(
        new DicomFileInputStream(FileSystems.getDefault().getPath(IN_DIR.toString(), filename)));
    return reader.getPlanarImages(null);
  }

  @Test
  public void jpeg2000_lossy_multiframe_multifragments() throws Exception {
    List<PlanarImage> imagesIn = readDicomImage("jpeg2000-multiframe-multifragments.dcm");
    MatcherAssert.assertThat(
        "The number of image frames doesn't match", imagesIn.size(), IsEqual.equalTo(19));
    for (PlanarImage img : imagesIn) {
      MatcherAssert.assertThat("The image size doesn't match", img.width(), IsEqual.equalTo(256));
    }
  }

  @Test
  public void ybrFull_RLE() throws Exception {
    List<PlanarImage> imagesIn = readDicomImage("ybrFull-RLE.dcm");
    MatcherAssert.assertThat(
        "The number of image frames doesn't match", imagesIn.size(), IsEqual.equalTo(1));
    for (PlanarImage img : imagesIn) {
      MatcherAssert.assertThat("The image width doesn't match", img.width(), IsEqual.equalTo(640));
      MatcherAssert.assertThat(
          "The image height doesn't match", img.height(), IsEqual.equalTo(480));
    }
  }
}
