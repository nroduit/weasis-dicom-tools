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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Polygon;
import java.awt.Shape;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageAnalyzer;

class ImageRenderingTest {

  @Test
  @DisplayName("Check statistics on a Modality Lut image (polygonal area)")
  void getModalityLutImage_Statistics() throws Exception {
    Path in =
        Paths.get(
            Objects.requireNonNull(DicomImageReaderTest.class.getResource("mono2-CT-16bit.dcm"))
                .toURI());
    DicomImageReadParam readParam = new DicomImageReadParam();
    Polygon polygon = new Polygon();
    polygon.addPoint(150, 200);
    polygon.addPoint(200, 190);
    polygon.addPoint(200, 250);
    polygon.addPoint(140, 240);
    double[][] val = statistics(in.toString(), readParam, polygon);
    assertNotNull(val);
    assertEquals(val[0][0], -202.0, 0.0);
    assertEquals(961.0, val[1][0], 0.0);
    assertTrue(MathUtil.isEqual(val[2][0], 13.184417441029307));
    assertTrue(MathUtil.isEqual(val[3][0], 146.3726881826613));
  }

  public static double[][] statistics(String srcPath, DicomImageReadParam params, Shape shape)
      throws Exception {
    if (!StringUtil.hasText(srcPath)) {
      throw new IllegalStateException("Path cannot be empty");
    }
    DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    reader.setInput(new DicomFileInputStream(srcPath));
    ImageDescriptor desc = reader.getImageDescriptor();
    PlanarImage img = reader.getPlanarImage(0, params);
    img = ImageRendering.getRawRenderedImage(img, desc, params, 0);
    double[][] val =
        ImageAnalyzer.meanStdDev(
            img.toMat(), shape, desc.getPixelPaddingValue(), desc.getPixelPaddingRangeLimit());
    if (val != null) {
      DecimalFormat df = new DecimalFormat("#.00");
      String b =
          "Image path: "
              + srcPath
              + "\nPixel statistics of real values:"
              + "\n\tMin: "
              + DoubleStream.of(val[0]).mapToObj(df::format).collect(Collectors.joining(" "))
              + "\n\tMax: "
              + DoubleStream.of(val[1]).mapToObj(df::format).collect(Collectors.joining(" "))
              + "\n\tMean: "
              + DoubleStream.of(val[2]).mapToObj(df::format).collect(Collectors.joining(" "))
              + "\n\tStd: "
              + DoubleStream.of(val[3]).mapToObj(df::format).collect(Collectors.joining(" "));
      System.out.print(b);
    }
    reader.dispose();
    return val;
  }
}
