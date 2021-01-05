/*
 * Copyright (c) 2016-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.tool;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.dcm4che6.codec.CompressedPixelParser;
import org.dcm4che6.codec.JPEG;
import org.dcm4che6.codec.JPEGParser;
import org.dcm4che6.codec.MP4Parser;
import org.dcm4che6.codec.MPEG2Parser;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.UID;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.util.DateTimeUtils;
import org.dcm4che6.util.UIDUtils;

public class Dicomizer {
  private static final int BUFFER_SIZE = 8192;

  private Dicomizer() {}

  public static void pdf(final DicomObject dcm, Path pdfFile, Path dcmFile) throws IOException {
    ensureString(dcm, Tag.SOPClassUID, VR.UI, UID.EncapsulatedPDFStorage);
    ensureString(dcm, Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // UTF-8
    ensureString(dcm, Tag.Modality, VR.CS, "DOC");
    ensureUID(dcm, Tag.StudyInstanceUID);
    ensureUID(dcm, Tag.SeriesInstanceUID);
    ensureUID(dcm, Tag.SOPInstanceUID);
    setCreationDate(dcm);
    //        dcm.setInt(Tag.InstanceNumber, VR.IS, 1);
    //        dcm.setString(Tag.BurnedInAnnotation, VR.CS, "YES");
    int fileSize = (int) Files.size(pdfFile);
    dcm.setInt(Tag.EncapsulatedDocumentLength, VR.UL, (int) Files.size(pdfFile));
    dcm.setString(Tag.MIMETypeOfEncapsulatedDocument, VR.LO, "application/pdf");
    dcm.setBulkData(
        Tag.EncapsulatedDocument,
        VR.OB,
        pdfFile.toUri().toASCIIString() + "#length=" + fileSize,
        null);

    if (!Files.exists(dcmFile.getParent())) {
      Files.createDirectories(dcmFile.getParent());
    }
    DicomObject fmi = dcm.createFileMetaInformation(UID.ExplicitVRLittleEndian);
    try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(dcmFile))) {
      dos.writeFileMetaInformation(fmi).withEncoding(fmi);
      dos.writeDataSet(dcm);
    }
  }

  public static void jpegOrMpeg(final DicomObject dcm, Path jpgFile, Path dcmFile, boolean noAPPn)
      throws IOException {
    if (!Files.exists(jpgFile)) {
      throw new IllegalArgumentException("Source file doesn't exist: " + jpgFile);
    }
    ContentType type = ContentType.probe(jpgFile);
    DicomObject fmi;
    try (SeekableByteChannel channel = Files.newByteChannel(jpgFile)) {
      CompressedPixelParser parser = type.factory.newCompressedPixelParser(channel);
      DicomObject dcmobj = parser.getImagePixelDescription(dcm);
      ensureString(dcmobj, Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // UTF-8
      ensureString(dcmobj, Tag.SOPClassUID, VR.UI, type.defaultSOPClassUID);
      ensureUID(dcmobj, Tag.StudyInstanceUID);
      ensureUID(dcmobj, Tag.SeriesInstanceUID);
      ensureUID(dcmobj, Tag.SOPInstanceUID);
      setCreationDate(dcmobj);

      fmi = dcmobj.createFileMetaInformation(parser.getTransferSyntaxUID());

      if (!Files.exists(dcmFile.getParent())) {
        Files.createDirectories(dcmFile.getParent());
      }
      try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(dcmFile))) {
        dos.writeFileMetaInformation(fmi).withEncoding(fmi);
        dos.writeDataSet(dcmobj);
        dos.writeHeader(Tag.PixelData, VR.OB, -1);
        dos.writeHeader(Tag.Item, VR.NONE, 0);
        if (noAPPn && parser.getPositionAfterAPPSegments().isPresent()) {
          copyPixelData(
              channel,
              parser.getPositionAfterAPPSegments().getAsLong(),
              dos,
              (byte) 0xFF,
              (byte) JPEG.SOI);
        } else {
          copyPixelData(channel, parser.getCodeStreamPosition(), dos);
        }
        dos.writeHeader(Tag.SequenceDelimitationItem, VR.NONE, 0);
      }
    }
  }

  private static void setCreationDate(DicomObject dcm) {
    LocalDateTime dt = LocalDateTime.now();
    dcm.setString(Tag.InstanceCreationDate, VR.DA, DateTimeUtils.formatDA(dt));
    dcm.setString(Tag.InstanceCreationTime, VR.TM, DateTimeUtils.formatTM(dt));
  }

  private static void ensureString(DicomObject dcm, int tag, VR vr, String value) {
    if (dcm.get(tag).isEmpty()) {
      dcm.setString(tag, vr, value);
    }
  }

  private static void ensureUID(DicomObject dcm, int tag) {
    if (dcm.get(tag).isEmpty()) {
      dcm.setString(tag, VR.UI, UIDUtils.randomUID());
    }
  }

  private static void copyPixelData(
      SeekableByteChannel channel, long position, DicomOutputStream dos, byte... prefix)
      throws IOException {
    long codeStreamSize = channel.size() - position + prefix.length;
    dos.writeHeader(Tag.Item, VR.NONE, (int) ((codeStreamSize + 1) & ~1));
    dos.write(prefix);
    channel.position(position);
    copy(channel, dos);
    if ((codeStreamSize & 1) != 0) dos.write(0);
  }

  private static void copy(ByteChannel in, OutputStream out) throws IOException {
    byte[] b = new byte[BUFFER_SIZE];
    ByteBuffer buf = ByteBuffer.wrap(b);
    int read;
    while ((read = in.read(buf)) > 0) {
      out.write(b, 0, read);
      buf.clear();
    }
  }

  enum ContentType {
    IMAGE_JPEG(JPEGParser::new, UID.VLPhotographicImageStorage),
    VIDEO_MPEG(MPEG2Parser::new, UID.VideoPhotographicImageStorage),
    VIDEO_MP4(MP4Parser::new, UID.VideoPhotographicImageStorage);

    final CompressedPixelParserFactory factory;
    final String defaultSOPClassUID;

    ContentType(CompressedPixelParserFactory factory, String defaultSOPClassUID) {
      this.factory = factory;
      this.defaultSOPClassUID = defaultSOPClassUID;
    }

    static ContentType probe(Path path) {
      try {
        String type = Files.probeContentType(path);
        if (type == null)
          throw new IOException(
              String.format("failed to determine content type of file: '%s'", path));
        switch (type.toLowerCase()) {
          case "image/jpeg":
          case "image/jp2":
            return ContentType.IMAGE_JPEG;
          case "video/mpeg":
            return ContentType.VIDEO_MPEG;
          case "video/mp4":
            return ContentType.VIDEO_MP4;
        }
        throw new UnsupportedOperationException(
            String.format("unsupported content type: '%s' of file: '%s'", type, path));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @FunctionalInterface
  private interface CompressedPixelParserFactory {
    CompressedPixelParser newCompressedPixelParser(SeekableByteChannel channel) throws IOException;
  }
}
