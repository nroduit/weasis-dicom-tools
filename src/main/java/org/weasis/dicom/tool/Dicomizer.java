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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.jpeg.JPEG;
import org.dcm4che3.imageio.codec.jpeg.JPEGHeader;
import org.dcm4che3.imageio.codec.mpeg.MPEGHeader;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dicomizer {
  private static final Logger LOGGER = LoggerFactory.getLogger(Dicomizer.class);

  private static final int INIT_BUFFER_SIZE = 8192;
  private static final int MAX_BUFFER_SIZE = 10485768; // 10MiB

  private Dicomizer() {}

  public static void pdf(final Attributes attrs, File pdfFile, File dcmFile) throws IOException {
    attrs.setString(Tag.SOPClassUID, VR.UI, UID.EncapsulatedPDFStorage);
    ensureString(attrs, Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // UTF-8
    ensureUID(attrs, Tag.StudyInstanceUID);
    ensureUID(attrs, Tag.SeriesInstanceUID);
    ensureUID(attrs, Tag.SOPInstanceUID);
    setCreationDate(attrs);

    BulkData bulk = new BulkData(pdfFile.toURI().toString(), 0, pdfFile.length(), false);
    attrs.setValue(Tag.EncapsulatedDocument, VR.OB, bulk);
    attrs.setString(Tag.MIMETypeOfEncapsulatedDocument, VR.LO, "application/pdf");
    Attributes fmi = attrs.createFileMetaInformation(UID.ExplicitVRLittleEndian);
    try (DicomOutputStream dos = new DicomOutputStream(dcmFile)) {
      dos.writeDataset(fmi, attrs);
    }
  }

  public static void jpeg(final Attributes attrs, File jpgFile, File dcmFile, boolean noAPPn)
      throws IOException {
    buildDicom(attrs, jpgFile, dcmFile, noAPPn, false);
  }

  public static void mpeg2(final Attributes attrs, File mpegFile, File dcmFile) throws IOException {
    buildDicom(attrs, mpegFile, dcmFile, false, true);
  }

  private static void buildDicom(
      final Attributes attrs, File jpgFile, File dcmFile, boolean noAPPn, boolean mpeg)
      throws IOException {
    Parameters p = new Parameters();
    p.fileLength = (int) jpgFile.length();

    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(jpgFile))) {
      if (!readPixelHeader(p, attrs, bis, mpeg)) {
        throw new IOException("Cannot read the header of " + jpgFile.getPath());
      }

      int itemLen = p.fileLength;
      try (DicomOutputStream dos = new DicomOutputStream(dcmFile)) {
        ensureString(attrs, Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // UTF-8
        ensureUID(attrs, Tag.StudyInstanceUID);
        ensureUID(attrs, Tag.SeriesInstanceUID);
        ensureUID(attrs, Tag.SOPInstanceUID);

        setCreationDate(attrs);

        dos.writeDataset(
            attrs.createFileMetaInformation(mpeg ? UID.MPEG2MPML : UID.JPEGBaseline8Bit), attrs);
        dos.writeHeader(Tag.PixelData, VR.OB, -1);
        dos.writeHeader(Tag.Item, null, 0);
        if (p.jpegHeader != null && noAPPn) {
          int offset = p.jpegHeader.offsetAfterAPP();
          itemLen -= offset - 3;
          dos.writeHeader(Tag.Item, null, (itemLen + 1) & ~1);
          dos.write((byte) -1);
          dos.write((byte) JPEG.SOI);
          dos.write((byte) -1);
          dos.write(p.buffer, offset, p.realBufferLength - offset);
        } else {
          dos.writeHeader(Tag.Item, null, (itemLen + 1) & ~1);
          dos.write(p.buffer, 0, p.realBufferLength);
        }
        StreamUtils.copy(bis, dos, p.buffer);
        if ((itemLen & 1) != 0) {
          dos.write(0);
        }
        dos.writeHeader(Tag.SequenceDelimitationItem, null, 0);
      }

    } catch (Exception e) {
      LOGGER.error("Building {}", mpeg ? "mpeg" : "jpg", e);
    }
  }

  private static boolean readPixelHeader(
      Parameters p, Attributes metadata, InputStream in, boolean mpeg) throws IOException {
    int grow = INIT_BUFFER_SIZE;
    while (p.realBufferLength == p.buffer.length && p.realBufferLength < MAX_BUFFER_SIZE) {
      grow += p.realBufferLength;
      p.buffer = Arrays.copyOf(p.buffer, grow);
      p.realBufferLength +=
          StreamUtils.readAvailable(
              in, p.buffer, p.realBufferLength, p.buffer.length - p.realBufferLength);
      boolean jpgHeader;
      if (mpeg) {
        MPEGHeader mpegHeader = new MPEGHeader(p.buffer);
        jpgHeader = mpegHeader.toAttributes(metadata, p.fileLength) != null;
      } else {
        p.jpegHeader = new JPEGHeader(p.buffer, JPEG.SOS);
        jpgHeader = p.jpegHeader.toAttributes(metadata) != null;
      }
      if (jpgHeader) {
        ensureString(
            metadata,
            Tag.SOPClassUID,
            VR.UI,
            mpeg ? UID.VideoPhotographicImageStorage : UID.VLPhotographicImageStorage);
        return true;
      }
    }
    return false;
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

  private static class Parameters {
    int realBufferLength = 0;
    byte[] buffer = {};
    int fileLength = 0;
    JPEGHeader jpegHeader;
  }
}
