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
import java.io.OutputStream;
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
import org.dcm4che3.io.DicomOutputStream;
import org.opencv.core.CvType;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageIOHandler;

/**
 * Provides transcoding capabilities for DICOM images, supporting conversion to standard image
 * formats (JPEG, PNG, TIFF, etc.) and between different DICOM transfer syntaxes.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>DICOM to standard image format conversion with customizable rendering parameters
 *   <li>DICOM to DICOM transcoding with transfer syntax transformation
 *   <li>Multi-frame image support with automatic file indexing
 *   <li>Image masking and region-of-interest processing
 *   <li>Configurable compression settings and quality parameters
 * </ul>
 *
 * <p>The transcoder handles various pixel data types and photometric interpretations, automatically
 * adapting output format capabilities to preserve image quality where possible.
 *
 * @author Nicolas Roduit
 */
public final class Transcoder {
  private static final Logger LOGGER = LoggerFactory.getLogger(Transcoder.class);
  private static final int DEFAULT_JPEG_QUALITY = 80;

  /**
   * Supported output image formats with their capabilities and limitations. Each format specifies
   * support for different pixel data types and bit depths.
   */
  public enum Format {
    JPEG(".jpg", false, false, false, false),
    PNG(".png", true, false, false, false),
    TIF(".tif", true, false, true, true),
    JP2(".jp2", true, false, false, false),
    PNM(".pnm", true, false, false, false),
    BMP(".bmp", false, false, false, false),
    HDR(".hdr", false, false, false, true);

    private final String extension;
    private final boolean support16U;
    private final boolean support16S;
    private final boolean support32F;
    private final boolean support64F;

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

    public String getExtension() {
      return extension;
    }

    public boolean isSupport16U() {
      return support16U;
    }

    public boolean isSupport16S() {
      return support16S;
    }

    public boolean isSupport32F() {
      return support32F;
    }

    public boolean isSupport64F() {
      return support64F;
    }

    public boolean isSupported(int cvType) {
      return switch (cvType) {
        case CvType.CV_8U -> true;
        case CvType.CV_16U -> support16U;
        case CvType.CV_16S -> support16S;
        case CvType.CV_32F -> support32F;
        case CvType.CV_64F -> support64F;
        default -> false;
      };
    }
  }

  // FIXME: Move to a dedicated service or utility class
  public static final DicomImageReaderSpi dicomImageReaderSpi = new DicomImageReaderSpi();

  private static final DicomImageReadParam dicomImageReadParam = new DicomImageReadParam();

  static {
    dicomImageReadParam.setReleaseImageAfterProcessing(true);
  }

  private Transcoder() {
    // Utility class - prevent instantiation
  }

  /**
   * Converts a DICOM image to standard image format(s) with customizable rendering parameters.
   *
   * <p>For multi-frame DICOM images, each frame is saved as a separate file with automatic
   * indexing. The output path can be either a specific file or a directory where files will be
   * created using the source filename as a base.
   *
   * @param srcPath the source DICOM image path
   * @param dstPath the destination path (file or directory)
   * @param params the image conversion parameters including format, quality, and rendering options
   * @return list of created output file paths
   * @throws Exception if conversion fails due to I/O errors or unsupported formats
   */
  public static List<Path> dcm2image(Path srcPath, Path dstPath, ImageTranscodeParam params)
      throws Exception {
    var outFiles = new ArrayList<Path>();
    var format = params.getFormat();
    var reader = createAndConfigureReader(srcPath);
    try {
      var compressionParams = createCompressionParams(format, params);
      var frameCount = reader.getImageDescriptor().getFrames();
      var indexSize = calculateIndexSize(frameCount);

      for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
        var processedImage = processImageFrame(reader, params, format, frameIndex);
        var outputPath = createOutputPath(srcPath, dstPath, format, frameIndex + 1, indexSize);
        writeImageToFile(processedImage, outputPath, format, compressionParams);
        outFiles.add(outputPath);
      }
    } finally {
      reader.dispose();
    }
    return outFiles;
  }

  /**
   * Converts a DICOM image to another DICOM format with a different transfer syntax.
   *
   * <p>This method handles compression, decompression, and format adaptation while preserving DICOM
   * metadata and ensuring compatibility with the target transfer syntax.
   *
   * @param srcPath the source DICOM image path
   * @param dstPath the destination path (file or directory)
   * @param params the DICOM conversion parameters including target transfer syntax
   * @return the created output file path
   * @throws IOException if conversion fails due to I/O errors or format incompatibility
   */
  public static Path dcm2dcm(Path srcPath, Path dstPath, DicomTranscodeParam params)
      throws IOException {
    var outPath = adaptFileExtension(FileUtil.getOutputPath(srcPath, dstPath), ".dcm");

    try (var outputStream = Files.newOutputStream(outPath)) {
      dcm2dcm(srcPath, outputStream, params);
    } catch (Exception e) {
      FileUtil.delete(outPath);
      if (e instanceof IOException ioException) {
        throw ioException;
      }
      throw new IOException("Transcoding failed", e);
    }

    return outPath;
  }

  /**
   * Converts a DICOM image to another DICOM format, writing directly to an output stream.
   *
   * <p>This method provides more control over the output destination and is useful for streaming
   * scenarios or when the output needs to be written to non-file destinations.
   *
   * @param srcPath the source DICOM image path
   * @param outputStream the destination output stream
   * @param params the DICOM conversion parameters
   * @throws IOException if conversion fails due to I/O errors or format incompatibility
   */
  public static void dcm2dcm(Path srcPath, OutputStream outputStream, DicomTranscodeParam params)
      throws IOException {
    DicomImageReader reader = new DicomImageReader(dicomImageReaderSpi);
    try {
      reader.setInput(new DicomFileInputStream(srcPath));
      var context = createTranscodeContext(reader, params);

      try (var dos = new DicomOutputStream(outputStream, context.actualTsuid)) {
        writeTranscodedDicom(dos, context);
      } catch (Exception e) {
        throw new IOException("Transcoding failed", e);
      }
    } finally {
      reader.dispose();
    }
  }

  /**
   * Creates a masked image editor for applying region-of-interest operations.
   *
   * @param maskArea the mask area definition, or null for no masking
   * @return an image editor that applies the mask, or null if no mask is specified
   */
  public static Editable<PlanarImage> getMaskedImage(MaskArea maskArea) {
    if (maskArea == null) {
      return null;
    }

    return image -> {
      var maskedImage = MaskArea.drawShape(image.toMat(), maskArea);
      if (image.isReleasedAfterProcessing()) {
        image.release();
        maskedImage.setReleasedAfterProcessing(true);
      }
      return maskedImage;
    };
  }

  private static DicomImageReader createAndConfigureReader(Path srcPath) throws Exception {
    var reader = new DicomImageReader(dicomImageReaderSpi);
    reader.setInput(new DicomFileInputStream(srcPath));
    return reader;
  }

  private static MatOfInt createCompressionParams(Format format, ImageTranscodeParam params) {
    return format == Format.JPEG
        ? new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, getCompressionQuality(params))
        : null;
  }

  private static int calculateIndexSize(int frameCount) {
    return frameCount > 1 ? (int) Math.log10(frameCount) + 1 : 0;
  }

  private static PlanarImage processImageFrame(
      DicomImageReader reader, ImageTranscodeParam params, Format format, int frameIndex)
      throws IOException {
    var image = reader.getPlanarImage(frameIndex, params.getReadParam());
    var preserveRaw = shouldPreserveRawImage(params, format, image.type());

    return preserveRaw
        ? ImageRendering.getRawRenderedImage(
            image, reader.getImageDescriptor(), params.getReadParam(), frameIndex)
        : ImageRendering.getDefaultRenderedImage(
            image, reader.getImageDescriptor(), params.getReadParam(), frameIndex);
  }

  private static Path createOutputPath(
      Path srcPath, Path dstPath, Format format, int frameNumber, int indexSize) {
    var basePath = FileUtil.getOutputPath(srcPath, dstPath);
    var pathWithExtension = adaptFileExtension(basePath, format.extension);
    return FileUtil.addFileIndex(pathWithExtension, frameNumber, indexSize);
  }

  private static void writeImageToFile(
      PlanarImage image, Path outputPath, Format format, MatOfInt compressionParams) {
    boolean success =
        compressionParams == null
            ? ImageIOHandler.writeImage(image.toMat(), outputPath)
            : ImageIOHandler.writeImage(image.toMat(), outputPath, compressionParams);

    if (!success) {
      LOGGER.error("Failed to write {} image: {}", format, outputPath);
      FileUtil.delete(outputPath);
    }
  }

  private static DicomTranscodeContext createTranscodeContext(
      DicomImageReader reader, DicomTranscodeParam params) throws IOException {
    var metaData = reader.getStreamMetadata();
    var dataSet = new Attributes(metaData.getDicomObject());
    dataSet.remove(Tag.PixelData);

    var mask = createMask(dataSet, params);
    var readParams = getEffectiveReadParams(params);
    var images = reader.getLazyPlanarImages(readParams, mask);
    var targetTsuid = params.getOutputTsuid();
    var writeParams = params.getWriteJpegParam();
    var descriptor = metaData.getImageDescriptor();

    var outputData = new DicomOutputData(images, descriptor, targetTsuid);
    var adaptedWriteParams = adaptTransferSyntax(outputData, targetTsuid, writeParams);
    var actualTsuid = outputData.getTsuid();

    return new DicomTranscodeContext(
        dataSet, outputData, actualTsuid, adaptedWriteParams, descriptor);
  }

  private static DicomJpegWriteParam adaptTransferSyntax(
      DicomOutputData outputData, String targetTsuid, DicomJpegWriteParam writeParams) {
    var actualTsuid = outputData.getTsuid();
    if (!targetTsuid.equals(actualTsuid)) {
      if (!DicomOutputData.isNativeSyntax(actualTsuid)) {
        writeParams = DicomJpegWriteParam.buildDicomImageWriteParam(actualTsuid);
      }
      LOGGER.warn("Cannot transcode to {}, using {} instead", targetTsuid, actualTsuid);
    }
    return writeParams;
  }

  private static void writeTranscodedDicom(DicomOutputStream dos, DicomTranscodeContext context)
      throws IOException {
    dos.writeFileMetaInformation(context.dataSet.createFileMetaInformation(context.actualTsuid));

    if (DicomOutputData.isNativeSyntax(context.actualTsuid)) {
      context.outputData.writeRawImageData(dos, context.dataSet);
    } else {
      writeCompressedDicomData(dos, context);
    }
  }

  private static void writeCompressedDicomData(DicomOutputStream dos, DicomTranscodeContext context)
      throws IOException {
    var jpegParams =
        context.outputData.adaptTagsToCompressedImage(
            context.dataSet,
            context.outputData.getFirstImage().get(),
            context.descriptor,
            context.writeParams);
    context.outputData.writeCompressedImageData(dos, context.dataSet, jpegParams);
  }

  private static Editable<PlanarImage> createMask(Attributes dataSet, DicomTranscodeParam params) {
    var stationName = dataSet.getString(Tag.StationName, "*");
    return getMaskedImage(params.getMask(stationName));
  }

  private static DicomImageReadParam getEffectiveReadParams(DicomTranscodeParam params) {
    var readParams = params.getReadParam();
    if (readParams == null) {
      return dicomImageReadParam;
    }
    readParams.setReleaseImageAfterProcessing(true);
    return readParams;
  }

  private static int getCompressionQuality(ImageTranscodeParam params) {
    return params != null
        ? params.getJpegCompressionQuality().orElse(DEFAULT_JPEG_QUALITY)
        : DEFAULT_JPEG_QUALITY;
  }

  private static boolean shouldPreserveRawImage(
      ImageTranscodeParam params, Format format, int cvType) {
    if (params == null) {
      return false;
    }
    var preserveRaw = params.isPreserveRawImage().orElse(false);
    if (!preserveRaw) {
      return false;
    }

    // HDR format or 8-bit unsigned always preserves raw values
    if (format == Format.HDR || cvType == CvType.CV_8U) {
      return true;
    }
    return format.isSupported(cvType);
  }

  private static Path adaptFileExtension(Path path, String outputExt) {
    var filename = path.getFileName().toString();
    var currentExt = FileUtil.getExtension(filename);

    if (currentExt.equals(outputExt)) {
      return path;
    }

    if (currentExt.endsWith(".dcm")) {
      var baseName = filename.substring(0, filename.length() - ".dcm".length());
      return path.resolveSibling(baseName + outputExt);
    }
    return path.resolveSibling(filename + outputExt);
  }

  /** Context holder for DICOM transcoding operations. */
  private record DicomTranscodeContext(
      Attributes dataSet,
      DicomOutputData outputData,
      String actualTsuid,
      DicomJpegWriteParam writeParams,
      ImageDescriptor descriptor) {}
}
