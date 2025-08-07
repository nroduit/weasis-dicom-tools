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
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

public class Dicomizer {
  private static final Logger LOGGER = LoggerFactory.getLogger(Dicomizer.class);

  private static final long FRAGMENT_LENGTH = 4294967294L;
  private static final int INIT_BUFFER_SIZE = 8192;

  public static final long MAX_FILE_SIZE = 0x7FFFFFFE;

  private Dicomizer() {}

  private static void encapsulateDocument(
      Attributes attrs, File inputFile, File dcmFile, String sopClassUID, String mimeType)
      throws IOException {
    long fileLength = inputFile.length();
    if (fileLength > MAX_FILE_SIZE) {
      throw new IOException(
          "Encapsulated file too large %s: %s"
              .formatted(inputFile, FileUtil.humanReadableByte(fileLength, false)));
    }
    attrs.setLong(Tag.EncapsulatedDocumentLength, VR.UL, fileLength);

    ensureString(attrs, Tag.SOPClassUID, VR.UI, sopClassUID);
    commonRequiredAttributes(attrs);
    ensureString(attrs, Tag.InstanceNumber, VR.IS, "1");
    ensureString(attrs, Tag.SeriesNumber, VR.IS, "999");
    ensureString(attrs, Tag.BurnedInAnnotation, VR.CS, "YES");

    BulkData bulk = new BulkData(inputFile.toURI().toString(), 0, fileLength, false);
    attrs.setValue(Tag.EncapsulatedDocument, VR.OB, bulk);
    attrs.setString(Tag.MIMETypeOfEncapsulatedDocument, VR.LO, mimeType);
    attrs.setString(Tag.DocumentTitle, VR.LO, inputFile.getName());
    if (!attrs.containsValue(Tag.DocumentTitle) && attrs.containsValue(Tag.ImageComments)) {
      attrs.setString(Tag.DocumentTitle, VR.LO, attrs.getString(Tag.ImageComments));
    }

    Attributes fmi = attrs.createFileMetaInformation(UID.ExplicitVRLittleEndian);
    try (DicomOutputStream dos = new DicomOutputStream(dcmFile)) {
      dos.writeDataset(fmi, attrs);
    }
  }

  private static void encapsulateM3d(Attributes attrs) {
    ensureString(attrs, Tag.Modality, VR.CS, "M3D");
    ensureString(attrs, Tag.Manufacturer, VR.LO, "UNKNOWN");
    ensureString(attrs, Tag.ManufacturerModelName, VR.LO, "UNKNOWN");
    ensureString(attrs, Tag.DeviceSerialNumber, VR.LO, "UNKNOWN");
    ensureString(attrs, Tag.SoftwareVersions, VR.LO, "UNKNOWN");

    Attributes dcm = new Attributes();
    dcm.setString(Tag.CodeValue, VR.SH, "mm");
    dcm.setString(Tag.CodingSchemeDesignator, VR.SH, "UCUM");
    dcm.setString(Tag.CodeMeaning, VR.LO, "mm");
    Sequence seq = attrs.ensureSequence(Tag.MeasurementUnitsCodeSequence, 1);
    seq.add(dcm);
  }

  public static void pdf(Attributes attrs, File pdfFile, File dcmFile) throws IOException {
    ensureString(attrs, Tag.Modality, VR.CS, "DOC");
    ensureString(attrs, Tag.ConversionType, VR.CS, "SD");
    encapsulateDocument(attrs, pdfFile, dcmFile, UID.EncapsulatedPDFStorage, "application/pdf");
  }

  public static void stl(Attributes attrs, File stlFile, File dcmFile) throws IOException {
    encapsulateM3d(attrs);
    ensureString(attrs, Tag.FrameOfReferenceUID, VR.UI, UIDUtils.createUID());
    encapsulateDocument(attrs, stlFile, dcmFile, UID.EncapsulatedSTLStorage, "model/stl");
  }

  public static void mtl(Attributes attrs, File stlFile, File dcmFile) throws IOException {
    encapsulateM3d(attrs);
    encapsulateDocument(attrs, stlFile, dcmFile, UID.EncapsulatedMTLStorage, "model/mtl");
  }

  public static void obj(Attributes attrs, File objFile, File dcmFile) throws IOException {
    encapsulateM3d(attrs);
    ensureString(attrs, Tag.FrameOfReferenceUID, VR.UI, UIDUtils.createUID());
    encapsulateDocument(attrs, objFile, dcmFile, UID.EncapsulatedOBJStorage, "model/obj");
  }

  public static void cda(Attributes attrs, File cdaFile, File dcmFile) throws IOException {
    ensureString(attrs, Tag.Modality, VR.CS, "DOC");
    ensureString(attrs, Tag.ConversionType, VR.CS, "WSD");
    encapsulateDocument(attrs, cdaFile, dcmFile, UID.EncapsulatedCDAStorage, "text/XML");
  }

  public static void jpeg(Attributes attrs, File jpgFile, File dcmFile, boolean noAPPn)
      throws IOException {
    try (SeekableByteChannel channel = FileChannel.open(jpgFile.toPath())) {
      JPEGParser parser = new JPEGParser(channel);
      buildDicomUsingParser(
          parser, attrs, jpgFile, dcmFile, UID.VLPhotographicImageStorage, noAPPn);
    }
  }

  public static void mpeg2(Attributes attrs, File mpegFile, File dcmFile) throws IOException {
    try (SeekableByteChannel channel = FileChannel.open(mpegFile.toPath())) {
      MPEG2Parser parser = new MPEG2Parser(channel);
      buildDicomUsingParser(
          parser, attrs, mpegFile, dcmFile, UID.VideoPhotographicImageStorage, false);
    }
  }

  public static void mpeg4(Attributes attrs, File mpegFile, File dcmFile) throws IOException {
    try (SeekableByteChannel channel = FileChannel.open(mpegFile.toPath())) {
      MP4Parser parser = new MP4Parser(channel);
      buildDicomUsingParser(
          parser, attrs, mpegFile, dcmFile, UID.VideoPhotographicImageStorage, false);
    }
  }

  private static void buildDicomUsingParser(
      XPEGParser parser,
      Attributes attrs,
      File inputFile,
      File dcmFile,
      String sopClassUID,
      boolean noAPPn)
      throws IOException {
    try (SeekableByteChannel channel = Files.newByteChannel(inputFile.toPath());
        DicomOutputStream dos = new DicomOutputStream(dcmFile)) {
      ensureString(attrs, Tag.SOPClassUID, VR.UI, sopClassUID);
      ensureString(attrs, Tag.ImageType, VR.CS, "ORIGINAL\\PRIMARY");
      commonRequiredAttributes(attrs);
      parser.getAttributes(attrs);

      byte[] prefix = ByteUtils.EMPTY_BYTES;
      if (noAPPn && parser.getPositionAfterAPPSegments() > 0) {
        channel.position(parser.getPositionAfterAPPSegments());
        prefix = new byte[] {(byte) 0xFF, (byte) JPEG.SOI};
      } else {
        channel.position(parser.getCodeStreamPosition());
      }
      long codeStreamSize = channel.size() - channel.position() + prefix.length;
      Attributes fmi =
          attrs.createFileMetaInformation(
              parser.getTransferSyntaxUID(codeStreamSize > FRAGMENT_LENGTH));
      dos.writeDataset(fmi, attrs);
      dos.writeHeader(Tag.PixelData, VR.OB, -1);
      dos.writeHeader(Tag.Item, null, 0);
      do {
        long len = Math.min(codeStreamSize, FRAGMENT_LENGTH);
        dos.writeHeader(Tag.Item, null, (int) ((len + 1) & ~1));
        dos.write(prefix);
        copy(channel, len - prefix.length, dos);
        if ((len & 1) != 0) dos.write(0);
        prefix = ByteUtils.EMPTY_BYTES;
        codeStreamSize -= len;
      } while (codeStreamSize > 0);
      dos.writeHeader(Tag.SequenceDelimitationItem, null, 0);
    } catch (IOException e) {
      FileUtil.delete(dcmFile.toPath());
      throw e;
    } catch (Exception e) {
      FileUtil.delete(dcmFile.toPath());
      throw new IOException(e);
    }
  }

  private static void copy(ByteChannel in, long len, OutputStream out) throws IOException {
    byte[] buf = new byte[INIT_BUFFER_SIZE];
    ByteBuffer bb = ByteBuffer.wrap(buf);
    int read;
    while (len > 0) {
      bb.position(0);
      bb.limit((int) Math.min(len, buf.length));
      read = in.read(bb);
      out.write(buf, 0, read);
      len -= read;
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
      if (inputFile == null || !Files.isReadable(inputFile)) {
        LOGGER.error("Input file does not exist: {}", inputFile);
        return false;
      }

      // If the input file is a JPEG file, we can skip conversion as we can directly dicomize it
      String inputExtension = FileUtil.getExtension(inputFile.toString()).toLowerCase();
      if (isJpeg(inputFile, outputFile, inputExtension)) return true;

      // Read the image using the existing readImage method
      List<String> tags = new ArrayList<>();
      ImageCV imageCV = ImageIOHandler.readImage(inputFile, tags);
      if (imageCV == null || imageCV.empty()) {
        LOGGER.warn("Failed to read image from file: {}", inputFile);
        return false;
      }

      // If outputFile is null, create a default output file
      if (outputFile == null) {
        String defaultOutputPath = inputFile.toString().replaceAll("\\.[^.]+$", ".jpg");
        outputFile = Path.of(defaultOutputPath);
      }

      // Ensure the output file has .jpg or .jpeg extension
      String outputPath = outputFile.toString();
      String outputExtension = FileUtil.getExtension(outputPath).toLowerCase();
      if (!outputExtension.endsWith(".jpg")) {
        outputPath = outputPath + ".jpg";
        outputFile = Path.of(outputPath);
      }

      // Create JPEG encoding parameters
      MatOfInt params = new MatOfInt();
      if (quality != null) {
        int jpegQuality = MathUtil.clamp(quality, 0, 100);
        params.fromArray(Imgcodecs.IMWRITE_JPEG_QUALITY, jpegQuality);
      } else {
        params.fromArray(Imgcodecs.IMWRITE_JPEG_QUALITY, 95);
      }

      // Write the image as JPEG
      boolean success = ImageIOHandler.writeImage(imageCV, outputFile, params);

      if (success) {
        LOGGER.info("Successfully converted and wrote JPEG image to: {}", outputFile);
      } else {
        LOGGER.warn("Failed to write JPEG image to: {}", outputFile);
      }
      return success;
    } catch (Exception e) {
      LOGGER.error("Error converting image to JPEG: {}", e.getMessage(), e);
      return false;
    }
  }

  private static boolean isJpeg(Path inputFile, Path outputFile, String inputExtension) {
    if (inputExtension.endsWith(".jpg") || inputExtension.endsWith(".jpeg")) {
      try (SeekableByteChannel channel = Files.newByteChannel(inputFile)) {
        // JPEG headers are typically within the first few KB of the file
        // SOF (Start of Frame) segments are usually found within the first 64KB
        int headerBufferSize = Math.min(65536, (int) Files.size(inputFile));
        ByteBuffer buffer = ByteBuffer.allocate(headerBufferSize);
        channel.read(buffer);
        byte[] headerData = new byte[buffer.position()];
        buffer.flip();
        buffer.get(headerData);

        JPEGHeader header = new JPEGHeader(headerData, JPEG.SOF0);
        if (Objects.equals(header.getTransferSyntaxUID(), UID.JPEGBaseline8Bit)) {
          LOGGER.info("Input file is already a JPEG image: {}", inputFile);
          // Optionally, copy the file directly if no conversion is needed
          if (outputFile != null) {
            Files.copy(inputFile, outputFile);
          }
          return true;
        }
      } catch (Exception e) {
        LOGGER.debug(
            "Could not parse JPEG header from beginning of file, proceeding with full conversion: {}",
            e.getMessage());
        // Fall through to normal conversion process
      }
    }
    return false;
  }
}
