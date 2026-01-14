/*
 * Copyright (c) 2016-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.tool;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.XPEGParser;
import org.dcm4che3.imageio.codec.jpeg.JPEG;
import org.dcm4che3.imageio.codec.jpeg.JPEGHeader;
import org.dcm4che3.imageio.codec.jpeg.JPEGParser;
import org.dcm4che3.imageio.codec.mp4.MP4Parser;
import org.dcm4che3.imageio.codec.mpeg.MPEG2Parser;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.ByteUtils;
import org.dcm4che3.util.UIDUtils;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.op.ImageIOHandler;

/**
 * Utility class for encapsulating various file types into DICOM format. Supported file types
 * include:
 *
 * <ul>
 *   <li>PDF documents
 *   <li>3D models (STL, MTL, OBJ)
 *   <li>Clinical Document Architecture (CDA) XML files
 *   <li>JPEG images
 *   <li>MPEG2 and MPEG4 videos
 * </ul>
 *
 * The class provides methods to read input files, validate their sizes, set necessary DICOM
 * attributes, and write the encapsulated data to DICOM files.
 */
public final class Dicomizer {
  private static final Logger LOGGER = LoggerFactory.getLogger(Dicomizer.class);

  private static final long FRAGMENT_LENGTH = 4294967294L;
  private static final int INIT_BUFFER_SIZE = 8192;
  private static final int JPEG_HEADER_BUFFER_SIZE = 65536;
  private static final int DEFAULT_JPEG_QUALITY = 95;
  private static final String UNKNOWN = "UNKNOWN";

  public static final long MAX_FILE_SIZE = 0x7FFFFFFE;

  private Dicomizer() {}

  private static void encapsulateDocument(
      Attributes attrs, Path inputFile, Path dcmFile, String sopClassUID, String mimeType)
      throws IOException {
    if (!isValidInputFile(inputFile) || !isValidOutputFile(dcmFile)) {
      return;
    }
    validateFileSize(inputFile);

    long fileLength = Files.size(inputFile);
    attrs.setLong(Tag.EncapsulatedDocumentLength, VR.UL, fileLength);

    setupDocumentAttributes(attrs, sopClassUID, inputFile.getFileName().toString());
    setupBulkData(attrs, inputFile, fileLength, mimeType);
    writeDicomFile(attrs, dcmFile);
  }

  private static void validateFileSize(Path inputFile) throws IOException {
    long fileLength = Files.size(inputFile);
    if (fileLength > MAX_FILE_SIZE) {
      throw new IOException(
          "Encapsulated file too large %s: %s"
              .formatted(inputFile, FileUtil.humanReadableByte(fileLength, false)));
    }
  }

  private static void setupDocumentAttributes(
      Attributes attrs, String sopClassUID, String fileName) {
    ensureString(attrs, Tag.SOPClassUID, VR.UI, sopClassUID);
    commonRequiredAttributes(attrs);
    ensureString(attrs, Tag.InstanceNumber, VR.IS, "1");
    ensureString(attrs, Tag.SeriesNumber, VR.IS, "999");
    ensureString(attrs, Tag.BurnedInAnnotation, VR.CS, "YES");

    if (!attrs.containsValue(Tag.DocumentTitle) && attrs.containsValue(Tag.ImageComments)) {
      attrs.setString(Tag.DocumentTitle, VR.LO, attrs.getString(Tag.ImageComments));
    }
    ensureString(attrs, Tag.DocumentTitle, VR.CS, fileName);
  }

  private static void setupBulkData(
      Attributes attrs, Path inputFile, long fileLength, String mimeType) {
    BulkData bulk = new BulkData(inputFile.toUri().toString(), 0, fileLength, false);
    attrs.setValue(Tag.EncapsulatedDocument, VR.OB, bulk);
    attrs.setString(Tag.MIMETypeOfEncapsulatedDocument, VR.LO, mimeType);
  }

  private static void writeDicomFile(Attributes attrs, Path dcmFile) throws IOException {
    Attributes fmi = attrs.createFileMetaInformation(UID.ExplicitVRLittleEndian);
    try (DicomOutputStream dos = new DicomOutputStream(dcmFile.toFile())) {
      dos.writeDataset(fmi, attrs);
    }
  }

  private static void encapsulateM3d(Attributes attrs) {
    setupM3dAttributes(attrs);
    setupMeasurementUnitsSequence(attrs);
  }

  private static void setupM3dAttributes(Attributes attrs) {
    ensureString(attrs, Tag.Modality, VR.CS, "M3D");
    ensureString(attrs, Tag.Manufacturer, VR.LO, UNKNOWN);
    ensureString(attrs, Tag.ManufacturerModelName, VR.LO, UNKNOWN);
    ensureString(attrs, Tag.DeviceSerialNumber, VR.LO, UNKNOWN);
    ensureString(attrs, Tag.SoftwareVersions, VR.LO, UNKNOWN);
  }

  private static void setupMeasurementUnitsSequence(Attributes attrs) {
    Attributes unitAttributes = new Attributes();
    unitAttributes.setString(Tag.CodeValue, VR.SH, "mm");
    unitAttributes.setString(Tag.CodingSchemeDesignator, VR.SH, "UCUM");
    unitAttributes.setString(Tag.CodeMeaning, VR.LO, "mm");

    Sequence sequence = attrs.ensureSequence(Tag.MeasurementUnitsCodeSequence, 1);
    sequence.add(unitAttributes);
  }

  /**
   * Encapsulates a PDF document into a DICOM file.
   *
   * @param attrs the DICOM attributes to populate
   * @param pdfFile the path to the input PDF file
   * @param dcmFile the path to the output DICOM file
   * @throws IOException if an I/O error occurs
   */
  public static void pdf(Attributes attrs, Path pdfFile, Path dcmFile) throws IOException {
    ensureString(attrs, Tag.Modality, VR.CS, "DOC");
    ensureString(attrs, Tag.ConversionType, VR.CS, "SD");
    encapsulateDocument(attrs, pdfFile, dcmFile, UID.EncapsulatedPDFStorage, "application/pdf");
  }

  /**
   * Encapsulates an STL 3D model into a DICOM file.
   *
   * @param attrs the DICOM attributes to populate
   * @param stlFile the path to the input STL file
   * @param dcmFile the path to the output DICOM file
   * @throws IOException if an I/O error occurs
   */
  public static void stl(Attributes attrs, Path stlFile, Path dcmFile) throws IOException {
    encapsulateM3d(attrs);
    ensureString(attrs, Tag.FrameOfReferenceUID, VR.UI, UIDUtils.createUID());
    encapsulateDocument(attrs, stlFile, dcmFile, UID.EncapsulatedSTLStorage, "model/stl");
  }

  /**
   * Encapsulates an MTL 3D model into a DICOM file.
   *
   * @param attrs the DICOM attributes to populate
   * @param mtlFile the path to the input MTL file
   * @param dcmFile the path to the output DICOM file
   * @throws IOException if an I/O error occurs
   */
  public static void mtl(Attributes attrs, Path mtlFile, Path dcmFile) throws IOException {
    encapsulateM3d(attrs);
    encapsulateDocument(attrs, mtlFile, dcmFile, UID.EncapsulatedMTLStorage, "model/mtl");
  }

  /**
   * Encapsulates an OBJ 3D model into a DICOM file.
   *
   * @param attrs the DICOM attributes to populate
   * @param objFile the path to the input OBJ file
   * @param dcmFile the path to the output DICOM file
   * @throws IOException if an I/O error occurs
   */
  public static void obj(Attributes attrs, Path objFile, Path dcmFile) throws IOException {
    encapsulateM3d(attrs);
    ensureString(attrs, Tag.FrameOfReferenceUID, VR.UI, UIDUtils.createUID());
    encapsulateDocument(attrs, objFile, dcmFile, UID.EncapsulatedOBJStorage, "model/obj");
  }

  /**
   * Encapsulates a Clinical Document Architecture (CDA) XML file into a DICOM file.
   *
   * @param attrs the DICOM attributes to populate
   * @param cdaFile the path to the input CDA XML file
   * @param dcmFile the path to the output DICOM file
   * @throws IOException if an I/O error occurs
   */
  public static void cda(Attributes attrs, Path cdaFile, Path dcmFile) throws IOException {
    ensureString(attrs, Tag.Modality, VR.CS, "DOC");
    ensureString(attrs, Tag.ConversionType, VR.CS, "WSD");
    encapsulateDocument(attrs, cdaFile, dcmFile, UID.EncapsulatedCDAStorage, "text/XML");
  }

  /**
   * Encapsulates a JPEG image into a DICOM file.
   *
   * @param attrs the DICOM attributes to populate
   * @param jpgFile the path to the input JPEG file
   * @param dcmFile the path to the output DICOM file
   * @param noAPPn if true, strips APPn segments from the JPEG data
   * @throws IOException if an I/O error occurs
   */
  public static void jpeg(Attributes attrs, Path jpgFile, Path dcmFile, boolean noAPPn)
      throws IOException {
    if (!isValidInputFile(jpgFile) || !isValidOutputFile(dcmFile)) {
      return;
    }
    try (SeekableByteChannel channel = Files.newByteChannel(jpgFile)) {
      JPEGParser parser = new JPEGParser(channel);
      buildDicomUsingParser(
          parser, attrs, jpgFile, dcmFile, UID.VLPhotographicImageStorage, noAPPn);
    }
  }

  /**
   * Encapsulates an MPEG2 video into a DICOM file.
   *
   * @param attrs the DICOM attributes to populate
   * @param mpegFile the path to the input MPEG2 file
   * @param dcmFile the path to the output DICOM file
   * @throws IOException if an I/O error occurs
   */
  public static void mpeg2(Attributes attrs, Path mpegFile, Path dcmFile) throws IOException {
    if (!isValidInputFile(mpegFile) || !isValidOutputFile(dcmFile)) {
      return;
    }
    try (SeekableByteChannel channel = Files.newByteChannel(mpegFile)) {
      MPEG2Parser parser = new MPEG2Parser(channel);
      buildDicomUsingParser(
          parser, attrs, mpegFile, dcmFile, UID.VideoPhotographicImageStorage, false);
    }
  }

  /**
   * Encapsulates an MPEG4 video into a DICOM file. Only MPEG4 DICOM-compliant files are supported.
   *
   * @param attrs the DICOM attributes to populate
   * @param mpegFile the path to the input MPEG4 file
   * @param dcmFile the path to the output DICOM file
   * @throws IOException if an I/O error occurs
   */
  public static void mpeg4(Attributes attrs, Path mpegFile, Path dcmFile) throws IOException {
    if (!isValidInputFile(mpegFile) || !isValidOutputFile(dcmFile)) {
      return;
    }
    try (SeekableByteChannel channel = Files.newByteChannel(mpegFile)) {
      MP4Parser parser = new MP4Parser(channel);
      buildDicomUsingParser(
          parser, attrs, mpegFile, dcmFile, UID.VideoPhotographicImageStorage, false);
    }
  }

  private static void buildDicomUsingParser(
      XPEGParser parser,
      Attributes attrs,
      Path inputFile,
      Path dcmFile,
      String sopClassUID,
      boolean noAPPn)
      throws IOException {
    try {
      setupParserAttributes(attrs, sopClassUID, parser);
      writePixelData(parser, attrs, inputFile, dcmFile, noAPPn);
    } catch (IOException e) {
      Files.deleteIfExists(dcmFile);
      throw e;
    } catch (Exception e) {
      Files.deleteIfExists(dcmFile);
      throw new IOException(e);
    }
  }

  private static void setupParserAttributes(
      Attributes attrs, String sopClassUID, XPEGParser parser) {
    ensureString(attrs, Tag.SOPClassUID, VR.UI, sopClassUID);
    ensureString(attrs, Tag.ImageType, VR.CS, "ORIGINAL\\PRIMARY");
    commonRequiredAttributes(attrs);
    parser.getAttributes(attrs);
  }

  private static void writePixelData(
      XPEGParser parser, Attributes attrs, Path inputFile, Path dcmFile, boolean noAPPn)
      throws IOException {
    try (SeekableByteChannel channel = Files.newByteChannel(inputFile);
        DicomOutputStream dos = new DicomOutputStream(dcmFile.toFile())) {

      PixelDataContext context = preparePixelDataContext(parser, channel, noAPPn);

      Attributes fmi =
          attrs.createFileMetaInformation(
              parser.getTransferSyntaxUID(context.codeStreamSize > FRAGMENT_LENGTH));

      dos.writeDataset(fmi, attrs);
      writePixelDataFragments(dos, channel, context);
    }
  }

  private static PixelDataContext preparePixelDataContext(
      XPEGParser parser, SeekableByteChannel channel, boolean noAPPn) throws IOException {
    byte[] prefix = ByteUtils.EMPTY_BYTES;
    if (noAPPn && parser.getPositionAfterAPPSegments() > 0) {
      channel.position(parser.getPositionAfterAPPSegments());
      prefix = new byte[] {(byte) 0xFF, (byte) JPEG.SOI};
    } else {
      channel.position(parser.getCodeStreamPosition());
    }
    long codeStreamSize = channel.size() - channel.position() + prefix.length;
    return new PixelDataContext(prefix, codeStreamSize);
  }

  private static void writePixelDataFragments(
      DicomOutputStream dos, SeekableByteChannel channel, PixelDataContext context)
      throws IOException {
    dos.writeHeader(Tag.PixelData, VR.OB, -1);
    dos.writeHeader(Tag.Item, null, 0);
    byte[] prefix = context.prefix;
    long remainingSize = context.codeStreamSize;
    do {
      long fragmentSize = Math.min(remainingSize, FRAGMENT_LENGTH);
      dos.writeHeader(Tag.Item, null, (int) ((fragmentSize + 1) & ~1));
      dos.write(prefix);
      copy(channel, fragmentSize - prefix.length, dos);
      if ((fragmentSize & 1) != 0) dos.write(0);
      prefix = ByteUtils.EMPTY_BYTES;
      remainingSize -= fragmentSize;
    } while (remainingSize > 0);
    dos.writeHeader(Tag.SequenceDelimitationItem, null, 0);
  }

  private static void copy(ByteChannel in, long len, OutputStream out) throws IOException {
    byte[] buffer = new byte[INIT_BUFFER_SIZE];
    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    while (len > 0) {
      byteBuffer.clear();
      byteBuffer.limit((int) Math.min(len, buffer.length));
      int bytesRead = in.read(byteBuffer);
      out.write(buffer, 0, bytesRead);
      len -= bytesRead;
    }
  }

  private static void commonRequiredAttributes(Attributes attrs) {
    ensureString(attrs, Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192");
    ensureUID(attrs, Tag.StudyInstanceUID);
    ensureUID(attrs, Tag.SeriesInstanceUID);
    ensureUID(attrs, Tag.SOPInstanceUID);

    setCreationDate(attrs);
  }

  private static void setCreationDate(Attributes attrs) {
    Date now = new Date();
    attrs.setDate(Tag.InstanceCreationDate, VR.DA, now);
    attrs.setDate(Tag.InstanceCreationTime, VR.TM, now);
  }

  private static void ensureString(Attributes attrs, int tag, VR vr, String value) {
    if (!attrs.containsValue(tag)) {
      attrs.setString(tag, vr, value);
    }
  }

  private static void ensureUID(Attributes attrs, int tag) {
    if (!attrs.containsValue(tag)) {
      attrs.setString(tag, VR.UI, UIDUtils.createUID());
    }
  }

  /**
   * Reads an image, converts it to JPEG format, and writes it to a file.
   *
   * @param inputFile the input image file to read (allowed formats: TIFF, BMP, GIF, JPEG, PNG, RAS,
   *     HDR, and PNM)
   * @param outputFile the output JPEG file to write (if null, a default output file will be created
   *     with .jpg extension)
   * @param quality the JPEG quality (0-100). If null, uses default quality.
   * @return true if the image was successfully converted and written, false otherwise
   */
  public static boolean convertToJpegAndWrite(Path inputFile, Path outputFile, Integer quality) {
    try {
      if (!isValidInputFile(inputFile)) {
        return false;
      }
      if (!isValidOutputFile(outputFile)) {
        return false;
      }

      String inputExtension = FileUtil.getExtension(inputFile.toString()).toLowerCase();
      if (handleJpegFile(inputFile, outputFile, inputExtension)) {
        return true;
      }

      return convertAndWriteImage(inputFile, outputFile, quality);

    } catch (Exception e) {
      LOGGER.error("Error converting image to JPEG: {}", e.getMessage(), e);
      return false;
    }
  }

  private static boolean isValidInputFile(Path inputFile) {
    if (inputFile == null || !Files.isReadable(inputFile)) {
      LOGGER.error("Input file does not exist: {}", inputFile);
      return false;
    }
    return true;
  }

  private static boolean isValidOutputFile(Path outputFile) {
    if (outputFile == null || (Files.exists(outputFile) && !Files.isWritable(outputFile))) {
      LOGGER.error("Output file is not writable: {}", outputFile);
      return false;
    }
    return true;
  }

  private static boolean handleJpegFile(Path inputFile, Path outputFile, String inputExtension)
      throws IOException {
    if (!isJpegExtension(inputExtension)) {
      return false;
    }

    if (isValidJpegFile(inputFile)) {
      LOGGER.info("Input file is already a JPEG image: {}", inputFile);
      if (outputFile != null) {
        Files.copy(inputFile, outputFile);
      }
      return true;
    }
    return false;
  }

  private static boolean convertAndWriteImage(Path inputFile, Path outputFile, Integer quality) {
    ImageCV imageCV = ImageIOHandler.readImage(inputFile, null);
    if (imageCV == null || imageCV.empty()) {
      LOGGER.warn("Failed to read image from file: {}", inputFile);
      return false;
    }

    Path finalOutputFile = determineOutputFile(inputFile, outputFile);
    MatOfInt encodingParams = createJpegEncodingParams(quality);

    boolean success = ImageIOHandler.writeImage(imageCV, finalOutputFile, encodingParams);
    logConversionResult(success, finalOutputFile);

    return success;
  }

  private static Path determineOutputFile(Path inputFile, Path outputFile) {
    if (outputFile != null) {
      return ensureJpegExtension(outputFile);
    }
    String defaultOutputPath = inputFile.toString().replaceAll("\\.[^.]+$", ".jpg");
    return Path.of(defaultOutputPath);
  }

  private static Path ensureJpegExtension(Path outputFile) {
    String outputPath = outputFile.toString();
    String outputExtension = FileUtil.getExtension(outputPath).toLowerCase();
    if (!outputExtension.endsWith(".jpg")) {
      outputPath = outputPath + ".jpg";
      return Path.of(outputPath);
    }
    return outputFile;
  }

  private static MatOfInt createJpegEncodingParams(Integer quality) {
    int jpegQuality = quality != null ? MathUtil.clamp(quality, 0, 100) : DEFAULT_JPEG_QUALITY;
    return new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, jpegQuality);
  }

  private static void logConversionResult(boolean success, Path outputFile) {
    if (success) {
      LOGGER.info("Successfully converted and wrote JPEG image to: {}", outputFile);
    } else {
      LOGGER.warn("Failed to write JPEG image to: {}", outputFile);
    }
  }

  private static boolean isJpegExtension(String extension) {
    return extension.endsWith(".jpg") || extension.endsWith(".jpeg");
  }

  private static boolean isValidJpegFile(Path inputFile) {
    try (SeekableByteChannel channel = Files.newByteChannel(inputFile)) {
      int headerBufferSize = Math.min(JPEG_HEADER_BUFFER_SIZE, (int) Files.size(inputFile));
      ByteBuffer buffer = ByteBuffer.allocate(headerBufferSize);
      channel.read(buffer);
      byte[] headerData = new byte[buffer.position()];
      buffer.flip();
      buffer.get(headerData);

      JPEGHeader header = new JPEGHeader(headerData, JPEG.SOF0);
      return Objects.equals(header.getTransferSyntaxUID(), UID.JPEGBaseline8Bit);
    } catch (Exception e) {
      LOGGER.debug("Could not parse JPEG header, proceeding with conversion: {}", e.getMessage());
      return false;
    }
  }

  private record PixelDataContext( // NOSONAR only internal use
      byte[] prefix, long codeStreamSize) {} // NOSONAR only internal use

  // Legacy method overloads for backward compatibility

  /**
   * @deprecated Use {@link #pdf(Attributes, Path, Path)} instead.
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static void pdf(Attributes attrs, File pdfFile, File dcmFile) throws IOException {
    pdf(attrs, pdfFile.toPath(), dcmFile.toPath());
  }

  /**
   * @deprecated Use {@link #jpeg(Attributes, Path, Path, boolean)} instead.
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static void jpeg(Attributes attrs, File jpgFile, File dcmFile, boolean noAPPn)
      throws IOException {
    jpeg(attrs, jpgFile.toPath(), dcmFile.toPath(), noAPPn);
  }
}
