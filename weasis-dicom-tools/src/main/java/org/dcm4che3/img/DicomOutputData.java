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
    if (Objects.requireNonNull(images).isEmpty()) {
      throw new IllegalStateException("No image found!");
    }
    this.images = new ArrayList<>(images);
    this.desc = Objects.requireNonNull(desc);
    int type = CvType.depth(getFirstImage().get().type());
    this.tsuid =
        DicomOutputData.adaptSuitableSyntax(
            desc.getBitsStored(), type, Objects.requireNonNull(tsuid));
    if (!isSupportedSyntax(this.tsuid)) {
      throw new IllegalStateException(this.tsuid + " is not supported as encoding syntax!");
    }
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

  public SupplierEx<PlanarImage, IOException> getFirstImage() {
    return images.get(0);
  }

  public List<SupplierEx<PlanarImage, IOException>> getImages() {
    return images;
  }

  public String getTsuid() {
    return tsuid;
  }

  /**
   * Writes compressed image data to a DICOM output stream using native encoding. Handles JPEG,
   * JPEG-LS, and JPEG 2000 compression formats with proper encapsulation.
   *
   * @param dos the DICOM output stream
   * @param dataSet the DICOM attributes dataset
   * @param params the compression parameters array
   * @throws IOException if encoding or writing fails
   */
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
    PlanarImage writeImg = DicomUtils.isJpeg2000(tsuid) ? image : PixelDataUtils.bgr2rgb(image);
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

  private void writeDatasetHeader(DicomOutputStream dos, Attributes dataSet, int[] params, Mat buf)
      throws IOException {
    PlanarImage firstImage = getFirstImage().get();
    int compressedLength = buf.width() * buf.height() * (int) buf.elemSize();
    double uncompressed = firstImage.width() * firstImage.height() * (double) firstImage.elemSize();
    adaptCompressionRatio(dataSet, params, uncompressed / compressedLength);
    dos.writeDataset(null, dataSet);
    dos.writeHeader(Tag.PixelData, VR.OB, -1);
    dos.writeHeader(Tag.Item, null, 0);
  }

  private void writeCompressedFrame(DicomOutputStream dos, Mat buf) throws IOException {
    int compressedLength = buf.width() * buf.height() * (int) buf.elemSize();
    byte[] frameData = new byte[compressedLength];
    buf.get(0, 0, frameData);
    dos.writeHeader(Tag.Item, null, frameData.length);
    dos.write(frameData);
  }

  private void adaptCompressionRatio(Attributes dataSet, int[] params, double ratio) {
    int compressType = params[Imgcodecs.DICOM_PARAM_COMPRESSION];
    int jpeglsNLE = params[Imgcodecs.DICOM_PARAM_JPEGLS_LOSSY_ERROR];
    int jpeg2000CompRatio = params[Imgcodecs.DICOM_PARAM_J2K_COMPRESSION_FACTOR];
    int jpegQuality = params[Imgcodecs.DICOM_PARAM_JPEG_QUALITY];
    if (isLossyCompression(compressType, jpegQuality, jpeg2000CompRatio, jpeglsNLE)) {
      setLossyCompressionTags(dataSet, compressType, ratio);
    }
  }

  private boolean isLossyCompression(
      int compressType, int jpegQuality, int jpeg2000CompRatio, int jpeglsNLE) {
    return (compressType == Imgcodecs.DICOM_CP_JPG && jpegQuality > 0)
        || (compressType == Imgcodecs.DICOM_CP_J2K && jpeg2000CompRatio > 0)
        || (compressType == Imgcodecs.DICOM_CP_JPLS && jpeglsNLE > 0);
  }

  private void setLossyCompressionTags(Attributes dataSet, int compressType, double ratio) {
    dataSet.setString(Tag.LossyImageCompression, VR.CS, "01");
    String method = getCompressionMethod(compressType);
    double[] ratios = updateCompressionRatios(dataSet, ratio);
    String[] methods = updateCompressionMethods(dataSet, method);

    dataSet.setDouble(Tag.LossyImageCompressionRatio, VR.DS, ratios);
    dataSet.setString(Tag.LossyImageCompressionMethod, VR.CS, methods);
  }

  private String getCompressionMethod(int compressType) {
    return switch (compressType) {
      case Imgcodecs.DICOM_CP_J2K -> "ISO_15444_1";
      case Imgcodecs.DICOM_CP_JPLS -> "ISO_14495_1";
      default -> "ISO_10918_1";
    };
  }

  private double[] updateCompressionRatios(Attributes dataSet, double ratio) {
    double[] old = dataSet.getDoubles(Tag.LossyImageCompressionRatio);
    if (old == null) {
      return new double[] {ratio};
    }

    double[] updated = Arrays.copyOf(old, old.length + 1);
    updated[updated.length - 1] = ratio;
    return updated;
  }

  private String[] updateCompressionMethods(Attributes dataSet, String method) {
    String[] oldMethods =
        DicomUtils.getStringArrayFromDicomElement(
            dataSet, Tag.LossyImageCompressionMethod, new String[0]);
    String[] updated = Arrays.copyOf(oldMethods, oldMethods.length + 1);
    updated[updated.length - 1] = method;

    // Ensure all methods have valid values
    for (int i = 0; i < updated.length; i++) {
      if (!StringUtil.hasText(updated[i])) {
        updated[i] = "unknown";
      }
    }
    return updated;
  }

  /**
   * Writes raw (uncompressed) image data to a DICOM output stream. Handles various pixel data types
   * with proper byte ordering and RGB conversion.
   *
   * @param dos the DICOM output stream
   * @param data the DICOM attributes dataset
   */
  public void writRawImageData(DicomOutputStream dos, Attributes data) {
    try {
      PlanarImage firstImage = getFirstImage().get();
      adaptTagsToRawImage(data, firstImage, desc);
      dos.writeDataset(null, data);

      int type = CvType.depth(firstImage.type());
      int length = calculateTotalPixelDataLength(firstImage);
      dos.writeHeader(Tag.PixelData, VR.OB, length);

      writePixelDataByType(dos, type, firstImage);
    } catch (Exception e) {
      LOGGER.error("Writing raw pixel data", e);
    }
  }

  private int calculateTotalPixelDataLength(PlanarImage firstImage) {
    int imgSize = firstImage.width() * firstImage.height();
    return images.size() * imgSize * (int) firstImage.elemSize();
  }

  private void writePixelDataByType(DicomOutputStream dos, int type, PlanarImage firstImage)
      throws IOException {
    int imgSize = firstImage.width() * firstImage.height();
    int channels = CvType.channels(firstImage.type());

    switch (type) {
      case CvType.CV_8U, CvType.CV_8S -> writeByteData(dos, imgSize, channels);
      case CvType.CV_16U, CvType.CV_16S -> writeShortData(dos, imgSize, channels);
      case CvType.CV_32S -> writeIntData(dos, imgSize, channels);
      case CvType.CV_32F -> writeFloatData(dos, imgSize, channels);
      case CvType.CV_64F -> writeDoubleData(dos, imgSize, channels);
      default -> throw new IllegalStateException("Cannot write this unknown image type");
    }
  }

  private void writeByteData(DicomOutputStream dos, int imgSize, int channels) throws IOException {
    byte[] srcData = new byte[imgSize * channels];
    for (SupplierEx<PlanarImage, IOException> image : images) {
      PlanarImage img = PixelDataUtils.bgr2rgb(image.get());
      img.get(0, 0, srcData);
      dos.write(srcData);
    }
  }

  private void writeShortData(DicomOutputStream dos, int imgSize, int channels) throws IOException {
    short[] srcData = new short[imgSize * channels];
    ByteBuffer bb = ByteBuffer.allocate(srcData.length * Short.BYTES);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    for (SupplierEx<PlanarImage, IOException> image : images) {
      PlanarImage img = PixelDataUtils.bgr2rgb(image.get());
      img.get(0, 0, srcData);
      bb.clear();
      bb.asShortBuffer().put(srcData);
      dos.write(bb.array());
    }
  }

  private void writeIntData(DicomOutputStream dos, int imgSize, int channels) throws IOException {
    int[] srcData = new int[imgSize * channels];
    ByteBuffer bb = ByteBuffer.allocate(srcData.length * Integer.BYTES);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    for (SupplierEx<PlanarImage, IOException> image : images) {
      PlanarImage img = PixelDataUtils.bgr2rgb(image.get());
      img.get(0, 0, srcData);
      bb.clear();
      bb.asIntBuffer().put(srcData);
      dos.write(bb.array());
    }
  }

  private void writeFloatData(DicomOutputStream dos, int imgSize, int channels) throws IOException {
    float[] srcData = new float[imgSize * channels];
    ByteBuffer bb = ByteBuffer.allocate(srcData.length * Float.BYTES);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    for (SupplierEx<PlanarImage, IOException> image : images) {
      PlanarImage img = PixelDataUtils.bgr2rgb(image.get());
      img.get(0, 0, srcData);
      bb.clear();
      bb.asFloatBuffer().put(srcData);
      dos.write(bb.array());
    }
  }

  private void writeDoubleData(DicomOutputStream dos, int imgSize, int channels)
      throws IOException {
    double[] srcData = new double[imgSize * channels];
    ByteBuffer bb = ByteBuffer.allocate(srcData.length * Double.BYTES);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    for (SupplierEx<PlanarImage, IOException> image : images) {
      PlanarImage img = PixelDataUtils.bgr2rgb(image.get());
      img.get(0, 0, srcData);
      bb.clear();
      bb.asDoubleBuffer().put(srcData);
      dos.write(bb.array());
    }
  }

  /**
   * Adapts DICOM attributes for raw (uncompressed) image data format. Sets pixel characteristics
   * including dimensions, bit depth, and photometric interpretation.
   */
  public static void adaptTagsToRawImage(Attributes data, PlanarImage img, ImageDescriptor desc) {
    int cvType = img.type();
    int channels = CvType.channels(cvType);
    int signed = CvType.depth(cvType) == CvType.CV_16S || desc.isSigned() ? 1 : 0;
    data.setInt(Tag.Columns, VR.US, img.width());
    data.setInt(Tag.Rows, VR.US, img.height());
    data.setInt(Tag.SamplesPerPixel, VR.US, channels);
    data.setInt(Tag.BitsAllocated, VR.US, desc.getBitsAllocated());
    data.setInt(Tag.BitsStored, VR.US, desc.getBitsStored());
    data.setInt(Tag.HighBit, VR.US, desc.getBitsStored() - 1);
    data.setInt(Tag.PixelRepresentation, VR.US, signed);
    String pmi = desc.getPhotometricInterpretation().toString();
    if (img.channels() > 1) {
      pmi = PhotometricInterpretation.RGB.toString();
      data.setInt(Tag.PlanarConfiguration, VR.US, 0);
    }
    data.setString(Tag.PhotometricInterpretation, VR.CS, pmi);
  }

  /**
   * Adapts DICOM attributes for compressed image data and prepares encoding parameters. Calculates
   * optimal bit depth and compression settings based on image characteristics.
   *
   * @param data the DICOM attributes to update
   * @param img the source image
   * @param desc the image descriptor
   * @param param the JPEG write parameters
   * @return array of encoding parameters for native compression
   */
  public int[] adaptTagsToCompressedImage(
      Attributes data, PlanarImage img, ImageDescriptor desc, DicomJpegWriteParam param) {
    ImageCharacteristics characteristics = analyzeImageCharacteristics(img, desc);
    CompressionSettings settings = determineCompressionSettings(param, characteristics);
    int[] params = buildEncodingParameters(img, settings, characteristics, param);

    updateDicomAttributes(data, img, characteristics);

    return params;
  }

  private ImageCharacteristics analyzeImageCharacteristics(PlanarImage img, ImageDescriptor desc) {
    int cvType = img.type();
    int elemSize = (int) img.elemSize1();
    int channels = CvType.channels(cvType);
    int depth = CvType.depth(cvType);
    boolean signed = depth != CvType.CV_8U && (depth != CvType.CV_16U || desc.isSigned());
    int bitAllocated = elemSize * 8;
    int bitCompressed = desc.getBitsCompressed();
    if (bitCompressed > bitAllocated) {
      bitCompressed = bitAllocated;
    }
    return new ImageCharacteristics(channels, signed, bitAllocated, bitCompressed, depth);
  }

  private CompressionSettings determineCompressionSettings(
      DicomJpegWriteParam param, ImageCharacteristics characteristics) {
    TransferSyntaxType ts = param.getType();
    int jpeglsNLE = param.getNearLosslessError();
    int bitCompressedForEncoder = characteristics.bitCompressed;
    int compressType = Imgcodecs.DICOM_CP_JPG;
    if (ts == TransferSyntaxType.JPEG_2000) {
      compressType = Imgcodecs.DICOM_CP_J2K;
    } else if (ts == TransferSyntaxType.JPEG_LS) {
      compressType = Imgcodecs.DICOM_CP_JPLS;
      if (characteristics.signed) {
        LOGGER.warn(
            "Force compression to JPEG-LS lossless as lossy is not adapted to signed data.");
        jpeglsNLE = 0;
        bitCompressedForEncoder = 16;
      }
    } else {
      // JPEG encoder adjustments
      bitCompressedForEncoder = adjustJpegBitDepth(characteristics, param);
    }

    // Handle specific encoder limitations
    if (ts != TransferSyntaxType.JPEG_2000
        && characteristics.bitCompressed == 8
        && characteristics.bitAllocated == 16) {
      bitCompressedForEncoder = 12;
    }

    return new CompressionSettings(compressType, bitCompressedForEncoder, jpeglsNLE);
  }

  private int adjustJpegBitDepth(ImageCharacteristics characteristics, DicomJpegWriteParam param) {
    int bitCompressed = characteristics.bitCompressed;
    if (bitCompressed <= 8) {
      return 8;
    } else if (bitCompressed <= 12) {
      if (characteristics.signed && param.getPrediction() > 1) {
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

    int[] params = new int[16];
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
    return params;
  }

  private void updateDicomAttributes(
      Attributes data, PlanarImage img, ImageCharacteristics characteristics) {

    data.setInt(Tag.Columns, VR.US, img.width());
    data.setInt(Tag.Rows, VR.US, img.height());
    data.setInt(Tag.SamplesPerPixel, VR.US, characteristics.channels);
    data.setInt(Tag.BitsAllocated, VR.US, characteristics.bitAllocated);
    data.setInt(Tag.BitsStored, VR.US, characteristics.bitCompressed);
    data.setInt(Tag.HighBit, VR.US, characteristics.bitCompressed - 1);
    data.setInt(Tag.PixelRepresentation, VR.US, characteristics.signed ? 1 : 0);

    PhotometricInterpretation pmi = desc.getPhotometricInterpretation();
    if (img.channels() > 1) {
      data.setInt(Tag.PlanarConfiguration, VR.US, 0);
      pmi = PhotometricInterpretation.RGB.compress(tsuid);
    }
    data.setString(Tag.PhotometricInterpretation, VR.CS, pmi.toString());
  }

  /**
   * Adapts transfer syntax to be suitable for the given image characteristics. Ensures
   * compatibility between image bit depth, data type, and compression format.
   */
  public static String adaptSuitableSyntax(int bitStored, int type, String dstTsuid) {
    return switch (dstTsuid) {
      case UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian -> UID.ExplicitVRLittleEndian;
      case UID.JPEGBaseline8Bit ->
          type <= CvType.CV_8S
              ? dstTsuid
              : type <= CvType.CV_16S ? UID.JPEGLosslessSV1 : UID.ExplicitVRLittleEndian;
      case UID.JPEGExtended12Bit,
              UID.JPEGSpectralSelectionNonHierarchical68,
              UID.JPEGFullProgressionNonHierarchical1012 ->
          type <= CvType.CV_16U && bitStored <= 12
              ? dstTsuid
              : type <= CvType.CV_16S ? UID.JPEGLosslessSV1 : UID.ExplicitVRLittleEndian;
      case UID.JPEGLossless,
              UID.JPEGLosslessSV1,
              UID.JPEGLSLossless,
              UID.JPEGLSNearLossless,
              UID.JPEG2000Lossless,
              UID.JPEG2000 ->
          type <= CvType.CV_16S ? dstTsuid : UID.ExplicitVRLittleEndian;
      default -> dstTsuid;
    };
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
              UID.JPEG2000 ->
          true;
      default -> false;
    };
  }

  // Helper record classes for better code organization
  private record ImageCharacteristics(
      int channels, boolean signed, int bitAllocated, int bitCompressed, int depth) {}

  private record CompressionSettings(
      int compressType, int bitCompressedForEncoder, int jpeglsNLE) {}
}
