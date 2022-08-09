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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.op.MaskArea;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.Editable;
import org.dcm4che3.img.util.SupplierEx;
import org.dcm4che3.io.DicomOutputStream;
import org.opencv.core.CvType;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

/**
 * @author Nicolas Roduit
 */
public class Transcoder {
  private static final Logger LOGGER = LoggerFactory.getLogger(Transcoder.class);

  public enum Format {
    JPEG(".jpg", false, false, false, false),
    PNG(".png", true, false, false, false),
    TIF(".tif", true, false, true, true),
    JP2(".jp2", true, false, false, false),
    PNM(".pnm", true, false, false, false),
    BMP(".bmp", false, false, false, false),
    HDR(".hdr", false, false, false, true);

    final String extension;
    final boolean support16U;
    final boolean support16S;
    final boolean support32F;
    final boolean support64F;

    Format(
        String ext,
        boolean support16U,
        boolean support16S,
        boolean support32F,
        boolean support64F) {
      this.extension = ext;
      this.support16U = support16U;
      this.support16S = support16S;
      this.support32F = support32F;
      this.support64F = support64F;
    }
  }

  public static final DicomImageReaderSpi dicomImageReaderSpi = new DicomImageReaderSpi();

  private static final DicomImageReadParam dicomImageReadParam = new DicomImageReadParam();

  static {
    dicomImageReadParam.setReleaseImageAfterProcessing(true);
  }

  /**
   * Convert a DICOM image to a standard image with some rendering parameters
   *
   * @param srcPath the path of the source image
   * @param dstPath the path of the destination image or the path of a directory in which the source
   *     image filename will be used
   * @param params the standard image conversion parameters
   * @return
   * @throws Exception
   */
  public static List<Path> dcm2image(Path srcPath, Path dstPath, ImageTranscodeParam params)
      throws Exception {
    List<Path> outFiles = new ArrayList<>();
    Format format = params.getFormat();
    DicomImageReader reader = new DicomImageReader(dicomImageReaderSpi);
    try (DicomFileInputStream inputStream = new DicomFileInputStream(srcPath)) {
      MatOfInt map =
          format == Format.JPEG
              ? new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, getCompressionRatio(params))
              : null;
      reader.setInput(inputStream);
      int nbFrames = reader.getImageDescriptor().getFrames();
      int indexSize = (int) Math.log10(nbFrames);
      indexSize = nbFrames > 1 ? indexSize + 1 : 0;
      for (int i = 0; i < nbFrames; i++) {
        PlanarImage img = reader.getPlanarImage(i, params.getReadParam());
        boolean rawImg = isPreserveRawImage(params, format, img.type());
        if (rawImg) {
          img =
              ImageRendering.getRawRenderedImage(
                  img, reader.getImageDescriptor(), params.getReadParam());
        } else {
          img =
              ImageRendering.getDefaultRenderedImage(
                  img, reader.getImageDescriptor(), params.getReadParam(), i);
        }
        Path outPath =
            writeImage(
                img, FileUtil.getOutputPath(srcPath, dstPath), format, map, i + 1, indexSize);
        outFiles.add(outPath);
      }
    } finally {
      reader.dispose();
    }
    return outFiles;
  }

  /**
   * Convert a DICOM image to another DICOM image with a specific transfer syntax
   *
   * @param srcPath the path of the source image
   * @param dstPath the path of the destination image or the path of a directory in which the source
   *     image filename will be used
   * @param params the DICOM conversion parameters
   * @throws Exception
   */
  public static Path dcm2dcm(Path srcPath, Path dstPath, DicomTranscodeParam params)
      throws Exception {
    Path outPath;
    DicomImageReader reader = new DicomImageReader(dicomImageReaderSpi);
    reader.setInput(new DicomFileInputStream(srcPath));

    DicomMetaData dicomMetaData = reader.getStreamMetadata();
    Attributes dataSet = new Attributes(dicomMetaData.getDicomObject());
    dataSet.remove(Tag.PixelData);

    outPath = adaptFileExtension(FileUtil.getOutputPath(srcPath, dstPath), ".dcm", ".dcm");
    Editable<PlanarImage> mask = getMask(dataSet, params);
    DicomImageReadParam dicomParams = params.getReadParam();
    if (dicomParams == null) {
      dicomParams = dicomImageReadParam;
    } else {
      dicomParams.setReleaseImageAfterProcessing(true);
    }
    List<SupplierEx<PlanarImage, IOException>> images =
        reader.getLazyPlanarImages(dicomParams, mask);
    String dstTsuid = params.getOutputTsuid();
    DicomJpegWriteParam writeParams = params.getWriteJpegParam();
    ImageDescriptor desc = dicomMetaData.getImageDescriptor();

    DicomOutputData imgData = new DicomOutputData(images, desc, dstTsuid);
    if (!dstTsuid.equals(imgData.getTsuid())) {
      dstTsuid = imgData.getTsuid();
      if (!DicomOutputData.isNativeSyntax(dstTsuid)) {
        writeParams = DicomJpegWriteParam.buildDicomImageWriteParam(dstTsuid);
      }
      LOGGER.warn("Transcoding into {} is not possible, decompressing {}", dstTsuid, srcPath);
    }
    try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(outPath), dstTsuid)) {
      dos.writeFileMetaInformation(dataSet.createFileMetaInformation(dstTsuid));
      if (DicomOutputData.isNativeSyntax(dstTsuid)) {
        imgData.writRawImageData(dos, dataSet);
      } else {
        int[] jpegWriteParams =
            imgData.adaptTagsToCompressedImage(
                dataSet, imgData.getFirstImage().get(), desc, writeParams);
        imgData.writeCompressedImageData(dos, dataSet, jpegWriteParams);
      }
    } catch (Exception e) {
      FileUtil.delete(outPath);
      LOGGER.error("Transcoding image data", e);
    } finally {
      reader.dispose();
    }
    return outPath;
  }

  public static Editable<PlanarImage> getMaskedImage(MaskArea m) {
    if (m != null) {
      return img -> {
        ImageCV mask = MaskArea.drawShape(img.toMat(), m);
        if (img.isReleasedAfterProcessing()) {
          img.release();
          mask.setReleasedAfterProcessing(true);
        }
        return mask;
      };
    }
    return null;
  }

  private static Editable<PlanarImage> getMask(Attributes dataSet, DicomTranscodeParam params) {
    String stationName = dataSet.getString(Tag.StationName, "*");
    return getMaskedImage(params.getMask(stationName));
  }

  private static int getCompressionRatio(ImageTranscodeParam params) {
    if (params == null) {
      return 80;
    }
    return params.getJpegCompressionQuality().orElse(80);
  }

  private static boolean isPreserveRawImage(ImageTranscodeParam params, Format format, int cvType) {
    if (params == null) {
      return false;
    }
    boolean value = params.isPreserveRawImage().orElse(false);
    if (value) {
      if (format == Format.HDR || cvType == CvType.CV_8U) {
        return true; // Convert all values in double so do not apply W/L
      } else if (cvType == CvType.CV_16U) {
        return format.support16U;
      } else if (cvType == CvType.CV_16S) {
        return format.support16S;
      } else if (cvType == CvType.CV_32F) {
        return format.support32F;
      } else if (cvType == CvType.CV_64F) {
        return format.support64F;
      }
    }
    return value;
  }

  private static Path adaptFileExtension(Path path, String inExt, String outExt) {
    String fname = path.getFileName().toString();
    String suffix = FileUtil.getExtension(fname);
    if (suffix.equals(outExt)) {
      return path;
    }
    if (suffix.endsWith(inExt)) {
      return FileSystems.getDefault()
          .getPath(
              path.getParent().toString(),
              fname.substring(0, fname.length() - inExt.length()) + outExt);
    }
    return path.resolveSibling(fname + outExt);
  }

  private static Path writeImage(
      PlanarImage img, Path path, Format ext, MatOfInt map, int index, int indexSize) {
    Path outPath = adaptFileExtension(path, ".dcm", ext.extension);
    outPath = FileUtil.addFileIndex(outPath, index, indexSize);
    if (map == null) {
      if (!ImageProcessor.writeImage(img.toMat(), outPath.toFile())) {
        LOGGER.error("Cannot Transform to {} {}", ext, img);
        FileUtil.delete(outPath);
      }
    } else {
      if (!ImageProcessor.writeImage(img.toMat(), outPath.toFile(), map)) {
        LOGGER.error("Cannot Transform to {} {}", ext, img);
        FileUtil.delete(outPath);
      }
    }
    return outPath;
  }
}
