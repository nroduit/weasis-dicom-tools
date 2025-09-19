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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.img.util.PixelDataUtils;
import org.dcm4che3.img.util.SupplierEx;
import org.dcm4che3.io.DicomOutputStream;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.PlanarImage;

/**
 * Handles DICOM image output data processing for both compressed and raw pixel data formats. This
 * class manages image encoding, transfer syntax adaptation, and DICOM attribute generation for
 * various compression formats including JPEG, JPEG-LS, and JPEG 2000.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Support for multiple image frames and formats
 *   <li>Automatic transfer syntax adaptation based on image characteristics
 *   <li>Compression ratio calculation and lossy image compression tagging
 *   <li>Raw pixel data encoding with proper byte ordering
 * </ul>
 *
 * @author Nicolas Roduit
 */
public class DicomOutputData {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomOutputData.class);

  private final List<SupplierEx<PlanarImage, IOException>> images;
  private final ImageDescriptor desc;
  private final String tsuid;

  /**
   * Creates a DICOM output data instance with multiple images.
   *
   * @param images the list of image suppliers (must not be null or empty)
   * @param desc the image descriptor containing DICOM metadata (must not be null)
   * @param tsuid the desired transfer syntax UID (must not be null)
   * @throws IOException if image processing fails
   * @throws IllegalStateException if no images are provided or syntax is unsupported
   */
  public DicomOutputData(
      List<SupplierEx<PlanarImage, IOException>> images, ImageDescriptor desc, String tsuid)
      throws IOException {
    validateInputs(images, desc, tsuid);
    this.images = new ArrayList<>(images);
    this.desc = desc;
    int type = CvType.depth(getFirstImage().get().type());
    this.tsuid = adaptSuitableSyntax(desc.getBitsStored(), type, tsuid);

    validateTransferSyntax();
  }

  /**
   * Creates a DICOM output data instance with a single image supplier.
   *
   * @param image the image supplier (must not be null)
   * @param desc the image descriptor (must not be null)
   * @param tsuid the transfer syntax UID (must not be null)
   * @throws IOException if image processing fails
   */
  public DicomOutputData(
      SupplierEx<PlanarImage, IOException> image, ImageDescriptor desc, String tsuid)
      throws IOException {
    this(Collections.singletonList(image), desc, tsuid);
  }

  /**
   * Creates a DICOM output data instance with a single planar image.
   *
   * @param image the planar image (must not be null)
   * @param desc the image descriptor (must not be null)
   * @param tsuid the transfer syntax UID (must not be null)
   * @throws IOException if image processing fails
   */
  public DicomOutputData(PlanarImage image, ImageDescriptor desc, String tsuid) throws IOException {
    this(Collections.singletonList(() -> image), desc, tsuid);
  }

  private void validateInputs(
      List<SupplierEx<PlanarImage, IOException>> images, ImageDescriptor desc, String tsuid) {
    if (Objects.requireNonNull(images, "Images list cannot be null").isEmpty()) {
      throw new IllegalStateException("No image found!");
    }
    Objects.requireNonNull(desc, "Image descriptor cannot be null");
    Objects.requireNonNull(tsuid, "Transfer syntax UID cannot be null");
  }

  private void validateTransferSyntax() {
    if (!isSupportedSyntax(this.tsuid)) {
      throw new IllegalStateException(this.tsuid + " is not supported as encoding syntax!");
    }
  }

  public SupplierEx<PlanarImage, IOException> getFirstImage() {
    return images.get(0);
  }

  public List<SupplierEx<PlanarImage, IOException>> getImages() {
    return images;
  }

  public String getTsuid() {
    return tsuid;
  }

  /** Writes compressed image data to a DICOM output stream using native encoding. */
  public void writeCompressedImageData(DicomOutputStream dos, Attributes dataSet, int[] params)
      throws IOException {
    Mat buf = null;
    MatOfInt dicomParams = null;
    try {
      dicomParams = new MatOfInt(params);
      for (int i = 0; i < images.size(); i++) {
        buf = encodeImageFrame(i, dicomParams);
        if (i == 0) {
          writeDatasetHeader(dos, dataSet, params, buf);
        }
        writeCompressedFrame(dos, buf);
      }
      dos.writeHeader(Tag.SequenceDelimitationItem, null, 0);
    } catch (Throwable t) {
      throw new IOException("Native encoding error", t);
    } finally {
      DicomImageReader.closeMat(dicomParams);
      DicomImageReader.closeMat(buf);
    }
  }

  private Mat encodeImageFrame(int frameIndex, MatOfInt dicomParams) throws IOException {
    PlanarImage image = images.get(frameIndex).get();
    boolean releaseSrc = image.isReleasedAfterProcessing();
    PlanarImage writeImg =
        DicomUtils.isJpeg2000(tsuid) || DicomUtils.isJpegXL(tsuid)
            ? image
            : PixelDataUtils.bgr2rgb(image);
    if (releaseSrc && !writeImg.equals(image)) {
      image.release();
    }
    Mat buf = Imgcodecs.dicomJpgWrite(writeImg.toMat(), dicomParams, "");
    if (buf.empty()) {
      writeImg.release();
      throw new IOException("Native encoding error: null image");
    }
    if (releaseSrc) {
      writeImg.release();
    }

    return buf;
  }

  private void writeDatasetHeader(
      DicomOutputStream dos, Attributes dataSet, int[] params, Mat buffer) throws IOException {
    var compressionRatio = calculateCompressionRatio(buffer);
    adaptCompressionRatio(dataSet, params, compressionRatio);
    dos.writeDataset(null, dataSet);
    dos.writeHeader(Tag.PixelData, VR.OB, -1);
    dos.writeHeader(Tag.Item, null, 0);
  }

  private double calculateCompressionRatio(Mat buffer) throws IOException {
    var firstImage = getFirstImage().get();
    int compressedLength = buffer.width() * buffer.height() * (int) buffer.elemSize();
    double uncompressed = firstImage.width() * firstImage.height() * (double) firstImage.elemSize();
    return uncompressed / compressedLength;
  }

  private void writeCompressedFrame(DicomOutputStream dos, Mat buffer) throws IOException {
    int frameSize = buffer.width() * buffer.height() * (int) buffer.elemSize();
    byte[] frameData = new byte[frameSize];
    buffer.get(0, 0, frameData);
    dos.writeHeader(Tag.Item, null, frameData.length);
    dos.write(frameData);
  }

  private void adaptCompressionRatio(Attributes dataSet, int[] params, double ratio) {
    var compressionInfo = extractCompressionInfo(params);

    if (compressionInfo.isLossy()) {
      setLossyCompressionTags(dataSet, compressionInfo.type(), ratio);
    }
  }

  private CompressionInfo extractCompressionInfo(int[] params) {
    return new CompressionInfo(
        params[Imgcodecs.DICOM_PARAM_COMPRESSION],
        params[Imgcodecs.DICOM_PARAM_JPEG_QUALITY],
        params[Imgcodecs.DICOM_PARAM_J2K_COMPRESSION_FACTOR],
        params[Imgcodecs.DICOM_PARAM_JPEGLS_LOSSY_ERROR]);
  }

  private void setLossyCompressionTags(Attributes dataSet, int compressType, double ratio) {
    dataSet.setString(Tag.LossyImageCompression, VR.CS, "01");

    var method = getCompressionMethod(compressType);
    var updatedRatios = updateCompressionRatios(dataSet, ratio);
    var updatedMethods = updateCompressionMethods(dataSet, method);

    dataSet.setDouble(Tag.LossyImageCompressionRatio, VR.DS, updatedRatios);
    dataSet.setString(Tag.LossyImageCompressionMethod, VR.CS, updatedMethods);
  }

  private String getCompressionMethod(int compressType) {
    return switch (compressType) {
      case Imgcodecs.DICOM_CP_J2K -> "ISO_15444_1";
      case Imgcodecs.DICOM_CP_JPLS -> "ISO_14495_1";
      default -> "ISO_10918_1";
    };
  }

  private double[] updateCompressionRatios(Attributes dataSet, double ratio) {
    var existing = dataSet.getDoubles(Tag.LossyImageCompressionRatio);
    return existing == null ? new double[] {ratio} : appendToArray(existing, ratio);
  }

  private double[] appendToArray(double[] array, double value) {
    var result = Arrays.copyOf(array, array.length + 1);
    result[result.length - 1] = value;
    return result;
  }

  private String[] updateCompressionMethods(Attributes dataSet, String method) {
    var existing =
        DicomUtils.getStringArrayFromDicomElement(
            dataSet, Tag.LossyImageCompressionMethod, new String[0]);
    var updated = Arrays.copyOf(existing, existing.length + 1);
    updated[updated.length - 1] = method;

    return sanitizeCompressionMethods(updated);
  }

  private String[] sanitizeCompressionMethods(String[] methods) {
    for (int i = 0; i < methods.length; i++) {
      if (!StringUtil.hasText(methods[i])) {
        methods[i] = "unknown";
      }
    }
    return methods;
  }

  /** Writes raw (uncompressed) image data to a DICOM output stream. */
  public void writeRawImageData(DicomOutputStream dos, Attributes data) {
    try {
      var firstImage = getFirstImage().get();
      adaptTagsToRawImage(data, firstImage, desc);
      dos.writeDataset(null, data);

      var pixelDataInfo = calculatePixelDataInfo(firstImage);
      dos.writeHeader(Tag.PixelData, VR.OB, pixelDataInfo.totalLength());

      writePixelDataByType(dos, pixelDataInfo);
    } catch (Exception e) {
      LOGGER.error("Error writing raw pixel data", e);
    }
  }

  private PixelDataInfo calculatePixelDataInfo(PlanarImage firstImage) {
    int type = CvType.depth(firstImage.type());
    int channels = CvType.channels(firstImage.type());

    int imageSize = firstImage.width() * firstImage.height();
    int totalLength = images.size() * imageSize * (int) firstImage.elemSize();

    return new PixelDataInfo(type, channels, imageSize, totalLength);
  }

  private void writePixelDataByType(DicomOutputStream dos, PixelDataInfo info) throws IOException {
    switch (info.type()) {
      case CvType.CV_8U, CvType.CV_8S -> new ByteDataWriter().write(dos, info);
      case CvType.CV_16U, CvType.CV_16S -> new ShortDataWriter().write(dos, info);
      case CvType.CV_32S -> new IntDataWriter().write(dos, info);
      case CvType.CV_32F -> new FloatDataWriter().write(dos, info);
      case CvType.CV_64F -> new DoubleDataWriter().write(dos, info);
      default -> throw new IllegalStateException("Cannot write unknown image type: " + info.type());
    }
  }

  /** Adapts DICOM attributes for raw image data format. */
  public static void adaptTagsToRawImage(Attributes data, PlanarImage img, ImageDescriptor desc) {
    var imageAttrs = extractImageAttributes(img, desc);
    updateRawImageAttributes(data, img, imageAttrs, desc);
  }

  private static ImageAttributes extractImageAttributes(PlanarImage img, ImageDescriptor desc) {
    int cvType = img.type();
    int channels = CvType.channels(cvType);
    boolean signed = CvType.depth(cvType) == CvType.CV_16S || desc.isSigned();

    return new ImageAttributes(channels, signed);
  }

  private static void updateRawImageAttributes(
      Attributes data, PlanarImage img, ImageAttributes attrs, ImageDescriptor desc) {
    data.setInt(Tag.Columns, VR.US, img.width());
    data.setInt(Tag.Rows, VR.US, img.height());
    data.setInt(Tag.SamplesPerPixel, VR.US, attrs.channels());
    data.setInt(Tag.BitsAllocated, VR.US, desc.getBitsAllocated());
    data.setInt(Tag.BitsStored, VR.US, desc.getBitsStored());
    data.setInt(Tag.HighBit, VR.US, desc.getBitsStored() - 1);
    data.setInt(Tag.PixelRepresentation, VR.US, attrs.signed() ? 1 : 0);

    setPhotometricInterpretation(data, img, desc);
  }

  private static void setPhotometricInterpretation(
      Attributes data, PlanarImage img, ImageDescriptor desc) {
    String pmi = desc.getPhotometricInterpretation().toString();
    if (img.channels() > 1) {
      pmi = PhotometricInterpretation.RGB.toString();
      data.setInt(Tag.PlanarConfiguration, VR.US, 0);
    }
    data.setString(Tag.PhotometricInterpretation, VR.CS, pmi);
  }

  /** Adapts DICOM attributes for compressed image data and prepares encoding parameters. */
  public int[] adaptTagsToCompressedImage(
      Attributes data, PlanarImage img, ImageDescriptor desc, DicomJpegWriteParam param) {
    var characteristics = analyzeImageCharacteristics(img, desc);
    var settings = determineCompressionSettings(param, characteristics, (int) img.elemSize1());
    var encodingParams = buildEncodingParameters(img, settings, characteristics, param);

    updateDicomAttributes(data, img, characteristics);

    return encodingParams;
  }

  private ImageCharacteristics analyzeImageCharacteristics(PlanarImage img, ImageDescriptor desc) {
    int cvType = img.type();
    int elemSize = (int) img.elemSize1();
    int channels = CvType.channels(cvType);
    int depth = CvType.depth(cvType);
    boolean signed = depth != CvType.CV_8U && (depth != CvType.CV_16U || desc.isSigned());
    int bitAllocated = elemSize * 8;
    int bitCompressed = Math.min(desc.getBitsCompressed(), bitAllocated);
    return new ImageCharacteristics(channels, signed, bitAllocated, bitCompressed, depth);
  }

  private CompressionSettings determineCompressionSettings(
      DicomJpegWriteParam param, ImageCharacteristics characteristics, int elemSize) {
    var transferSyntax = param.getType();
    int jpeglsNearLosslessError = param.getNearLosslessError();
    int bitDepthForEncoder = characteristics.bitCompressed();

    int compressionType = determineCompressionType(transferSyntax);

    if (!UID.JPEGXL.equals(param.getTransferSyntaxUid()) || elemSize == 2) {
      param.setCompressionQuality(100);
    }

    if (transferSyntax == TransferSyntaxType.JPEG_LS && characteristics.signed()) {
      LOGGER.warn("Force compression to JPEG-LS lossless as lossy is not adapted to signed data.");
      jpeglsNearLosslessError = 0;
      bitDepthForEncoder = 16;
    } else if (transferSyntax != TransferSyntaxType.JPEG_2000) {
      bitDepthForEncoder = adjustBitDepthForEncoder(characteristics, param, transferSyntax);
    }

    return new CompressionSettings(compressionType, bitDepthForEncoder, jpeglsNearLosslessError);
  }

  private int determineCompressionType(TransferSyntaxType transferSyntax) {
    return switch (transferSyntax) {
      case JPEG_2000 -> Imgcodecs.DICOM_CP_J2K;
      case JPEG_LS -> Imgcodecs.DICOM_CP_JPLS;
      case JPEG_XL -> Imgcodecs.DICOM_CP_JXL;
      default -> Imgcodecs.DICOM_CP_JPG;
    };
  }

  private int adjustBitDepthForEncoder(
      ImageCharacteristics characteristics,
      DicomJpegWriteParam param,
      TransferSyntaxType transferSyntax) {
    int bitDepth = characteristics.bitCompressed();

    if (transferSyntax != TransferSyntaxType.JPEG_2000
        && bitDepth == 8
        && characteristics.bitAllocated() == 16) {
      return 12;
    }

    return adjustJpegBitDepth(characteristics, param);
  }

  private int adjustJpegBitDepth(ImageCharacteristics characteristics, DicomJpegWriteParam param) {
    int bitCompressed = characteristics.bitCompressed();
    if (bitCompressed <= 8) {
      return 8;
    } else if (bitCompressed <= 12) {
      if (characteristics.signed() && param.getPrediction() > 1) {
        LOGGER.warn("Force JPEGLosslessNonHierarchical14 compression to 16-bit with signed data.");
        return 16;
      }
      return 12;
    } else {
      return 16;
    }
  }

  private int[] buildEncodingParameters(
      PlanarImage img,
      CompressionSettings settings,
      ImageCharacteristics characteristics,
      DicomJpegWriteParam param) {
    int dcmFlags =
        characteristics.signed ? Imgcodecs.DICOM_FLAG_SIGNED : Imgcodecs.DICOM_FLAG_UNSIGNED;
    int epi = characteristics.channels == 1 ? Imgcodecs.EPI_Monochrome2 : Imgcodecs.EPI_RGB;

    int[] params = new int[18];
    params[Imgcodecs.DICOM_PARAM_IMREAD] = Imgcodecs.IMREAD_UNCHANGED; // Image flags
    params[Imgcodecs.DICOM_PARAM_DCM_IMREAD] = dcmFlags; // DICOM flags
    params[Imgcodecs.DICOM_PARAM_WIDTH] = img.width(); // Image width
    params[Imgcodecs.DICOM_PARAM_HEIGHT] = img.height(); // Image height
    params[Imgcodecs.DICOM_PARAM_COMPRESSION] = settings.compressType; // Type of compression
    params[Imgcodecs.DICOM_PARAM_COMPONENTS] = characteristics.channels; // Number of components
    params[Imgcodecs.DICOM_PARAM_BITS_PER_SAMPLE] =
        settings.bitCompressedForEncoder; // Bits per sample
    params[Imgcodecs.DICOM_PARAM_INTERLEAVE_MODE] = Imgcodecs.ILV_SAMPLE; // Interleave mode
    params[Imgcodecs.DICOM_PARAM_COLOR_MODEL] = epi; // Photometric interpretation
    params[Imgcodecs.DICOM_PARAM_JPEG_MODE] = param.getJpegMode(); // JPEG Codec mode
    params[Imgcodecs.DICOM_PARAM_JPEGLS_LOSSY_ERROR] =
        settings.jpeglsNLE; // Lossy error for jpeg-ls
    params[Imgcodecs.DICOM_PARAM_J2K_COMPRESSION_FACTOR] =
        param.getCompressionRatioFactor(); // JPEG2000 factor of compression ratio
    params[Imgcodecs.DICOM_PARAM_JPEG_QUALITY] =
        param.getCompressionQuality(); // JPEG lossy quality
    params[Imgcodecs.DICOM_PARAM_JPEG_PREDICTION] =
        param.getPrediction(); // JPEG lossless prediction
    params[Imgcodecs.DICOM_PARAM_JPEG_PT_TRANSFORM] =
        param.getPointTransform(); // JPEG lossless transformation point
    params[Imgcodecs.DICOM_PARAM_JXL_EFFORT] = param.getJxlEffort(); // Effort (1-9)
    params[Imgcodecs.DICOM_PARAM_JXL_DECODING_SPEED] =
        param.getJxlDecodingSpeed(); // Decoding speed (0-4)
    return params;
  }

  private void updateDicomAttributes(
      Attributes data, PlanarImage img, ImageCharacteristics characteristics) {
    data.setInt(Tag.Columns, VR.US, img.width());
    data.setInt(Tag.Rows, VR.US, img.height());
    data.setInt(Tag.SamplesPerPixel, VR.US, characteristics.channels());
    data.setInt(Tag.BitsAllocated, VR.US, characteristics.bitAllocated());
    data.setInt(Tag.BitsStored, VR.US, characteristics.bitCompressed());
    data.setInt(Tag.HighBit, VR.US, characteristics.bitCompressed() - 1);
    data.setInt(Tag.PixelRepresentation, VR.US, characteristics.signed() ? 1 : 0);

    setPhotometricInterpretation(data, img, desc);
  }

  /** Adapts transfer syntax to be suitable for image characteristics. */
  public static String adaptSuitableSyntax(int bitStored, int type, String dstTsuid) {
    return switch (dstTsuid) {
      case UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian -> UID.ExplicitVRLittleEndian;
      case UID.JPEGBaseline8Bit -> adaptJpegBaseline(type, bitStored);
      case UID.JPEGExtended12Bit,
              UID.JPEGSpectralSelectionNonHierarchical68,
              UID.JPEGFullProgressionNonHierarchical1012 ->
          adaptJpegExtended(type, bitStored, dstTsuid);
      case UID.JPEGLossless,
              UID.JPEGLosslessSV1,
              UID.JPEGLSLossless,
              UID.JPEGLSNearLossless,
              UID.JPEG2000Lossless,
              UID.JPEG2000,
              UID.HTJ2KLossless,
              UID.HTJ2KLosslessRPCL,
              UID.HTJ2K ->
          type <= CvType.CV_16S ? dstTsuid : UID.ExplicitVRLittleEndian;
      case UID.JPEGXLLossless -> type <= CvType.CV_32F ? dstTsuid : UID.ExplicitVRLittleEndian;
      case UID.JPEGXLJPEGRecompression, UID.JPEGXL ->
          type <= CvType.CV_8S
              ? dstTsuid
              : type <= CvType.CV_32F ? UID.JPEGXLLossless : UID.ExplicitVRLittleEndian;
      default -> dstTsuid;
    };
  }

  private static String adaptJpegBaseline(int type, int bitStored) {
    if (type <= CvType.CV_8S) {
      return UID.JPEGBaseline8Bit;
    } else if (type <= CvType.CV_16S) {
      return UID.JPEGLosslessSV1;
    } else {
      return UID.ExplicitVRLittleEndian;
    }
  }

  private static String adaptJpegExtended(int type, int bitStored, String dstTsuid) {
    if (type <= CvType.CV_16U && bitStored <= 12) {
      return dstTsuid;
    } else if (type <= CvType.CV_16S) {
      return UID.JPEGLosslessSV1;
    } else {
      return UID.ExplicitVRLittleEndian;
    }
  }

  public static boolean isAdaptableSyntax(String uid) {
    return switch (uid) {
      case UID.JPEGBaseline8Bit,
              UID.JPEGExtended12Bit,
              UID.JPEGSpectralSelectionNonHierarchical68,
              UID.JPEGFullProgressionNonHierarchical1012 ->
          true;
      default -> false;
    };
  }

  public static boolean isNativeSyntax(String uid) {
    return switch (uid) {
      case UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian -> true;
      default -> false;
    };
  }

  public static boolean isSupportedSyntax(String uid) {
    return switch (uid) {
      case UID.ImplicitVRLittleEndian,
              UID.ExplicitVRLittleEndian,
              UID.JPEGBaseline8Bit,
              UID.JPEGExtended12Bit,
              UID.JPEGSpectralSelectionNonHierarchical68,
              UID.JPEGFullProgressionNonHierarchical1012,
              UID.JPEGLossless,
              UID.JPEGLosslessSV1,
              UID.JPEGLSLossless,
              UID.JPEGLSNearLossless,
              UID.JPEG2000Lossless,
              UID.JPEG2000,
              UID.JPEGXL,
              UID.JPEGXLLossless,
              UID.JPEGXLJPEGRecompression ->
          true;
      default -> false;
    };
  }

  // Helper records for better organization
  private record ImageCharacteristics(
      int channels, boolean signed, int bitAllocated, int bitCompressed, int depth) {}

  private record CompressionSettings(
      int compressType, int bitCompressedForEncoder, int jpeglsNLE) {}

  private record CompressionInfo(int type, int jpegQuality, int jpeg2000CompRatio, int jpeglsNLE) {
    boolean isLossy() {
      return (type == Imgcodecs.DICOM_CP_JPG && jpegQuality > 0)
          || (type == Imgcodecs.DICOM_CP_J2K && jpeg2000CompRatio > 0)
          || (type == Imgcodecs.DICOM_CP_JPLS && jpeglsNLE > 0);
    }
  }

  private record ImageAttributes(int channels, boolean signed) {}

  private record PixelDataInfo(int type, int channels, int imageSize, int totalLength) {}

  // Data writers using strategy pattern
  private abstract static class PixelDataWriter {
    abstract void write(DicomOutputStream dos, PixelDataInfo info) throws IOException;

    protected PlanarImage convertImage(SupplierEx<PlanarImage, IOException> imageSupplier)
        throws IOException {
      return PixelDataUtils.bgr2rgb(imageSupplier.get());
    }
  }

  private class ByteDataWriter extends PixelDataWriter {
    @Override
    void write(DicomOutputStream dos, PixelDataInfo info) throws IOException {
      byte[] data = new byte[info.imageSize() * info.channels()];
      for (var imageSupplier : images) {
        var img = convertImage(imageSupplier); // Do not release the image here
        img.get(0, 0, data);
        dos.write(data);
      }
    }
  }

  private class ShortDataWriter extends PixelDataWriter {
    @Override
    void write(DicomOutputStream dos, PixelDataInfo info) throws IOException {
      short[] data = new short[info.imageSize() * info.channels()];
      var buffer = ByteBuffer.allocate(data.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);

      for (var imageSupplier : images) {
        var img = convertImage(imageSupplier); // Do not release the image here
        img.get(0, 0, data);
        buffer.clear().asShortBuffer().put(data);
        dos.write(buffer.array());
      }
    }
  }

  private class IntDataWriter extends PixelDataWriter {
    @Override
    void write(DicomOutputStream dos, PixelDataInfo info) throws IOException {
      int[] data = new int[info.imageSize() * info.channels()];
      var buffer = ByteBuffer.allocate(data.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);

      for (var imageSupplier : images) {
        var img = convertImage(imageSupplier); // Do not release the image here
        img.get(0, 0, data);
        buffer.clear().asIntBuffer().put(data);
        dos.write(buffer.array());
      }
    }
  }

  private class FloatDataWriter extends PixelDataWriter {
    @Override
    void write(DicomOutputStream dos, PixelDataInfo info) throws IOException {
      float[] data = new float[info.imageSize() * info.channels()];
      var buffer = ByteBuffer.allocate(data.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);

      for (var imageSupplier : images) {
        var img = convertImage(imageSupplier); // Do not release the image here
        img.get(0, 0, data);
        buffer.clear().asFloatBuffer().put(data);
        dos.write(buffer.array());
      }
    }
  }

  private class DoubleDataWriter extends PixelDataWriter {
    @Override
    void write(DicomOutputStream dos, PixelDataInfo info) throws IOException {
      double[] data = new double[info.imageSize() * info.channels()];
      var buffer = ByteBuffer.allocate(data.length * Double.BYTES).order(ByteOrder.LITTLE_ENDIAN);

      for (var imageSupplier : images) {
        var img = convertImage(imageSupplier); // Do not release the image here
        img.get(0, 0, data);
        buffer.clear().asDoubleBuffer().put(data);
        dos.write(buffer.array());
      }
    }
  }
}
