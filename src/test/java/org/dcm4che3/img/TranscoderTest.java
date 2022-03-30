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

import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.Transcoder.Format;
import org.dcm4che3.img.data.ImageContentHash;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.weasis.core.util.FileUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

@RunWith(Enclosed.class)
public class TranscoderTest {

  static Path IN_DIR = FileSystems.getDefault().getPath("target/test-classes/org/dcm4che3/img");
  static final Path OUT_DIR = FileSystems.getDefault().getPath("target/test-out/");
  private static DicomImageReader reader = new DicomImageReader(new DicomImageReaderSpi());

  static {
    FileUtil.delete(OUT_DIR);
    try {
      Files.createDirectories(OUT_DIR);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  static final Consumer<Double> zeroDiff =
      val ->
          Assert.assertTrue(
              "The hash result of the image input is not exactly the same as the output image",
              val == 0.0);
  static final Consumer<Double> hasDiff =
      val ->
          Assert.assertTrue(
              "The hash result of the image input is exactly the same as the output image",
              val != 0.0);

  public static class GeneralTest {
    @Test
    public void dcm2image_ApllyPresentationStateLUT() throws Exception {
      Path in = FileSystems.getDefault().getPath(IN_DIR.toString(), "imageForPrLUTs.dcm");
      Path inPr = FileSystems.getDefault().getPath(IN_DIR.toString(), "prLUTs.dcm");
      DicomImageReadParam readParam = new DicomImageReadParam();
      readParam.setPresentationState(PrDicomObject.getPresentationState(inPr.toString()));
      ImageTranscodeParam params = new ImageTranscodeParam(readParam, Format.PNG);
      List<Path> outFiles = Transcoder.dcm2image(in, OUT_DIR, params);
      Assert.assertFalse(outFiles.isEmpty());

      Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
      enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
      enumMap.put(ImageContentHash.PHASH, zeroDiff);
      enumMap.put(ImageContentHash.BLOCK_MEAN_ONE, zeroDiff);
      enumMap.put(ImageContentHash.COLOR_MOMENT, zeroDiff);
      compareImageContent(
          FileSystems.getDefault().getPath(IN_DIR.toString(), "expected_imgForPrLUT.png"),
          outFiles.get(0),
          enumMap);
    }

    @Test
    public void dcm2image_ApllyPresentationStateOverlay() throws Exception {
      Path in = FileSystems.getDefault().getPath(IN_DIR.toString(), "overlay.dcm");
      Path inPr = FileSystems.getDefault().getPath(IN_DIR.toString(), "prOverlay.dcm");
      DicomImageReadParam readParam = new DicomImageReadParam();
      readParam.setPresentationState(PrDicomObject.getPresentationState(inPr.toString()));
      readParam.setOverlayColor(Color.GREEN);
      ImageTranscodeParam params = new ImageTranscodeParam(readParam, Format.PNG);
      List<Path> outFiles = Transcoder.dcm2image(in, OUT_DIR, params);
      Assert.assertFalse(outFiles.isEmpty());

      Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
      enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
      enumMap.put(ImageContentHash.PHASH, zeroDiff);
      enumMap.put(ImageContentHash.BLOCK_MEAN_ONE, zeroDiff);
      enumMap.put(ImageContentHash.COLOR_MOMENT, zeroDiff);
      compareImageContent(
          FileSystems.getDefault().getPath(IN_DIR.toString(), "expected_overlay.png"),
          outFiles.get(0),
          enumMap);
    }

    @Test
    public void dcm2dcm_Resize() throws Exception {
      DicomTranscodeParam params = new DicomTranscodeParam(UID.JPEGLSNearLossless);
      params.getReadParam().setSourceRenderSize(new Dimension(128, 128));
      Path out = transcodeDicom("signed-raw-9bit.dcm", params, null);
      List<PlanarImage> imgs = readImages(out);

      Assert.assertEquals("The width of image doesn't match", 128, imgs.get(0).width());
      Assert.assertEquals("The height of image doesn't match", 128, imgs.get(0).height());
    }
  }

  @RunWith(Parameterized.class)
  public static class FormatTest {
    @Parameter(value = 0)
    public Format format;

    @Parameters(name = "{index}: testFormat - {0}")
    public static Object[] data() {
      return Arrays.stream(Format.values()).toArray();
    }

    @Test
    @Parameters()
    public void dcm2image_YBR422Raw() throws Exception {
      Path in = FileSystems.getDefault().getPath(IN_DIR.toString(), "ybr422-raw.dcm");
      ImageTranscodeParam params = new ImageTranscodeParam(format);
      List<Path> outFiles = Transcoder.dcm2image(in, OUT_DIR, params);

      Assert.assertFalse(outFiles.isEmpty());

      Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
      enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
      enumMap.put(ImageContentHash.PHASH, zeroDiff);
      compareImageContent(in, outFiles, enumMap);
    }
  }

  @RunWith(Parameterized.class)
  public static class LossyCompressionTest {
    @Parameter(value = 0)
    public String lossyUID;

    @Parameters(name = "{index}: testLossy - {0}")
    public static Object[] data() {
      return new Object[] {UID.JPEG2000, UID.JPEGBaseline8Bit, UID.JPEGLSNearLossless};
    }

    @Test
    @Parameters()
    public void dcm2dcm_YBR422Raw_Lossy() throws Exception {
      Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
      enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
      enumMap.put(ImageContentHash.PHASH, zeroDiff);
      // JPEG compression mainly reduce the color information
      enumMap.put(ImageContentHash.COLOR_MOMENT, hasDiff);

      DicomTranscodeParam params = new DicomTranscodeParam(lossyUID);
      if (lossyUID.equals(UID.JPEGLSNearLossless)) {
        params.getWriteJpegParam().setNearLosslessError(3);
      } else {
        params.getWriteJpegParam().setCompressionQuality(80);
      }
      transcodeDicom("ybr422-raw.dcm", params, enumMap);
    }
  }

  @RunWith(Parameterized.class)
  public static class LosslessCompressionTest {
    @Parameter(value = 0)
    public String losslessUID;

    @Parameters(name = "{index}: testLossless - {0}")
    public static Object[] data() {
      return new Object[] {UID.JPEG2000Lossless, UID.JPEGLosslessSV1, UID.JPEGLSLossless};
    }

    @Test
    @Parameters()
    public void dcm2dcm_YBR422Raw_Lossless() throws Exception {
      Map<ImageContentHash, Consumer<Double>> enumMap = new EnumMap<>(ImageContentHash.class);
      // The image content must be fully preserved with lossless compression
      enumMap.put(ImageContentHash.AVERAGE, zeroDiff);
      enumMap.put(ImageContentHash.PHASH, zeroDiff);
      enumMap.put(ImageContentHash.BLOCK_MEAN_ONE, zeroDiff);
      enumMap.put(ImageContentHash.COLOR_MOMENT, zeroDiff);

      DicomTranscodeParam params = new DicomTranscodeParam(losslessUID);
      transcodeDicom("ybr422-raw.dcm", params, enumMap);
    }
  }

  private static void compareImageContent(
      Path in, Path out, Map<ImageContentHash, Consumer<Double>> enumMap) throws Exception {
    compareImageContent(in, Arrays.asList(out), enumMap);
  }

  private static void compareImageContent(
      Path in, List<Path> outFiles, Map<ImageContentHash, Consumer<Double>> enumMap)
      throws Exception {
    List<PlanarImage> imagesIn = readImages(in);
    List<PlanarImage> imagesOut = readImages(outFiles);

    Assert.assertTrue(
        "The number of image frames of the input file is different of the output file",
        imagesIn.size() == imagesOut.size());

    for (int i = 0; i < imagesIn.size(); i++) {
      PlanarImage imgIn = imagesIn.get(i);
      PlanarImage imgOut = imagesOut.get(i);

      System.out.println("");
      System.out.println("=== Image content diff of image " + (i + 1));
      System.out.println("=== Input: " + in);
      System.out.println(
          "=== Output: " + (i >= outFiles.size() ? outFiles.get(i) : outFiles.get(0)));

      for (Entry<ImageContentHash, Consumer<Double>> map : enumMap.entrySet()) {
        // Hash content comparison
        // http://qtandopencv.blogspot.com/2016/06/introduction-to-image-hash-module-of.html
        double val = map.getKey().compare(imgIn.toMat(), imgOut.toMat());
        System.out.println("\t" + map.getKey().name() + ": " + val);
        map.getValue().accept(val);
      }
    }
  }

  private static List<PlanarImage> readImages(List<Path> files) throws IOException {
    if (files.size() == 1 && files.get(0).getFileName().toString().endsWith(".dcm")) {
      reader.setInput(new DicomFileInputStream(files.get(0)));
      return reader.getPlanarImages(null);
    } else {
      return files.stream()
          .map(p -> ImageProcessor.readImageWithCvException(p.toFile(), null))
          .collect(Collectors.toList());
    }
  }

  private static List<PlanarImage> readImages(Path path) throws IOException {
    if (path.getFileName().toString().endsWith(".dcm")) {
      reader.setInput(new DicomFileInputStream(path));
      return reader.getPlanarImages(null);
    } else {
      return Arrays.asList(ImageProcessor.readImageWithCvException(path.toFile(), null));
    }
  }

  private static Path transcodeDicom(
      String ifname, DicomTranscodeParam params, Map<ImageContentHash, Consumer<Double>> enumMap)
      throws Exception {
    Path in = FileSystems.getDefault().getPath(IN_DIR.toString(), ifname);
    Path out = FileSystems.getDefault().getPath(OUT_DIR.toString(), params.getOutputTsuid());
    Files.createDirectories(out);
    out = Transcoder.dcm2dcm(in, out, params);

    Assert.assertTrue("The ouput image is empty", out != null && Files.size(out) > 0);
    if (enumMap != null) {
      compareImageContent(in, out, enumMap);
    }
    return out;
  }
}
