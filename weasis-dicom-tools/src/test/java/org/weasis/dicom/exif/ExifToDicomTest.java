/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.exif;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.exif.TiffBuilder.Ifd;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ExifToDicomTest {

  private static ExifData camera() {
    return ExifData.parse(
        new TiffBuilder(ByteOrder.LITTLE_ENDIAN)
            .ascii(Ifd.IMAGE, ExifToDicom.MAKE, "Canon")
            .ascii(Ifd.IMAGE, ExifToDicom.MODEL, "EOS R5")
            .ascii(Ifd.IMAGE, ExifToDicom.SOFTWARE, "Firmware 1.8.1")
            .ascii(Ifd.IMAGE, ExifToDicom.IMAGE_DESCRIPTION, "Left forearm")
            .rationals(Ifd.EXIF, 0x829A, 1, 125)
            .rationals(Ifd.EXIF, 0x829D, 28, 10)
            .shorts(Ifd.EXIF, 0x8822, 3)
            .shorts(Ifd.EXIF, 0x8827, 400)
            .undefined(
                Ifd.EXIF, ExifToDicom.EXIF_VERSION, "0232".getBytes(StandardCharsets.US_ASCII))
            .srationals(Ifd.EXIF, 0x9204, -1, 3)
            .rationals(Ifd.EXIF, 0x920A, 50, 1)
            .shorts(Ifd.EXIF, ExifToDicom.FLASH, 0x59)
            .undefined(Ifd.EXIF, 0xA300, new byte[] {3})
            .shorts(Ifd.EXIF, 0xA405, 50)
            .rationals(Ifd.EXIF, 0xA432, 24, 1, 105, 1, 4, 1, 4, 1)
            .ascii(Ifd.EXIF, 0xA433, "Canon")
            .ascii(Ifd.EXIF, 0xA434, "RF24-105mm F4 L IS USM")
            .ascii(Ifd.EXIF, ExifToDicom.DATE_TIME_ORIGINAL, "2026:09:20 10:11:12")
            .ascii(Ifd.EXIF, ExifToDicom.SUB_SEC_TIME_ORIGINAL, "1234567")
            .ascii(Ifd.EXIF, ExifToDicom.OFFSET_TIME_ORIGINAL, "+02:00")
            .ascii(Ifd.EXIF, ExifToDicom.DATE_TIME_DIGITIZED, "2026:09:20 10:11:13")
            .ascii(Ifd.EXIF, ExifToDicom.CAMERA_OWNER_NAME, "Jane Doe")
            .ascii(Ifd.EXIF, ExifToDicom.BODY_SERIAL_NUMBER, "012345678")
            .ascii(Ifd.EXIF, ExifToDicom.LENS_SERIAL_NUMBER, "9876")
            .bytes(Ifd.GPS, 0x0000, 2, 3, 0, 0)
            .ascii(Ifd.GPS, 0x0001, "n")
            .rationals(Ifd.GPS, 0x0002, 46, 1, 12, 1, 3015, 100)
            .ascii(Ifd.GPS, 0x0003, "E")
            .rationals(Ifd.GPS, 0x0004, 6, 1, 8, 1, 0, 1)
            .bytes(Ifd.GPS, 0x0005, 0)
            .rationals(Ifd.GPS, 0x0006, 3755, 10)
            .rationals(Ifd.GPS, ExifToDicom.GPS_TIME_STAMP, 8, 1, 11, 1, 125, 10)
            .ascii(Ifd.GPS, ExifToDicom.GPS_DATE_STAMP, "2026:09:20")
            .build());
  }

  @Nested
  class Technical_Attributes {

    @Test
    void camera_and_exposure_are_mapped_with_the_dictionary_vr() {
      Attributes attrs = new Attributes();

      ExifToDicom.fill(camera(), attrs, false);

      assertAll(
          () -> assertEquals("Canon", attrs.getString(Tag.Manufacturer)),
          () -> assertEquals("EOS R5", attrs.getString(Tag.ManufacturerModelName)),
          () -> assertEquals("Firmware 1.8.1", attrs.getString(Tag.SoftwareVersions)),
          () -> assertEquals("Left forearm", attrs.getString(Tag.ImageComments)),
          () -> assertEquals(0.008, attrs.getDouble(Tag.ExposureTimeInSeconds, 0), 1e-12),
          () -> assertEquals(2.8, attrs.getDouble(Tag.FNumber, 0), 1e-12),
          () -> assertEquals(VR.US, attrs.getVR(Tag.ExposureProgram)),
          () -> assertEquals(3, attrs.getInt(Tag.ExposureProgram, 0)),
          () -> assertEquals(VR.IS, attrs.getVR(Tag.PhotographicSensitivity)),
          () -> assertEquals(400, attrs.getInt(Tag.PhotographicSensitivity, 0)),
          () -> assertEquals("0232", attrs.getString(Tag.EXIFVersion)),
          () -> assertEquals(-1 / 3.0, attrs.getDouble(Tag.ExposureBiasValue, 0), 1e-6),
          () -> assertEquals(50, attrs.getDouble(Tag.FocalLength, 0), 1e-12),
          () -> assertEquals(3, attrs.getInt(Tag.FileSource, 0)),
          () -> assertEquals(50, attrs.getInt(Tag.FocalLengthIn35mmFilm, 0)),
          () ->
              assertArrayEquals(
                  new double[] {24, 105, 4, 4}, attrs.getDoubles(Tag.LensSpecification), 1e-12),
          () -> assertEquals("Canon", attrs.getString(Tag.LensMake)),
          () -> assertEquals("RF24-105mm F4 L IS USM", attrs.getString(Tag.LensModel)));
    }

    @Test
    void flash_bit_field_is_split() {
      Attributes attrs = new Attributes();

      ExifToDicom.fill(camera(), attrs, false);

      // 0x59: fired, no return detection, auto mode, flash present, red-eye reduction
      assertAll(
          () -> assertEquals(1, attrs.getInt(Tag.FlashFiringStatus, -1)),
          () -> assertEquals(0, attrs.getInt(Tag.FlashReturnStatus, -1)),
          () -> assertEquals(3, attrs.getInt(Tag.FlashMode, -1)),
          () -> assertEquals(0, attrs.getInt(Tag.FlashFunctionPresent, -1)),
          () -> assertEquals(1, attrs.getInt(Tag.FlashRedEyeMode, -1)));
    }

    @Test
    void dates_go_to_acquisition_and_content_with_the_offset_in_the_dt_value() {
      Attributes attrs = new Attributes();

      ExifToDicom.fill(camera(), attrs, false);

      assertAll(
          () ->
              assertEquals("20260920101112.123456+0200", attrs.getString(Tag.AcquisitionDateTime)),
          () -> assertEquals("20260920", attrs.getString(Tag.ContentDate)),
          () -> assertEquals("101113", attrs.getString(Tag.ContentTime)),
          () -> assertFalse(attrs.contains(Tag.TimezoneOffsetFromUTC)));
    }

    @Test
    void existing_attributes_are_kept() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.Manufacturer, VR.LO, "Edited by user");
      attrs.setString(Tag.ImageComments, VR.LT, "photo.jpg");
      attrs.setString(Tag.ContentDate, VR.DA, "20260101");

      ExifToDicom.fill(camera(), attrs, false);

      assertAll(
          () -> assertEquals("Edited by user", attrs.getString(Tag.Manufacturer)),
          () -> assertEquals("photo.jpg", attrs.getString(Tag.ImageComments)),
          () -> assertEquals("20260101", attrs.getString(Tag.ContentDate)),
          () -> assertFalse(attrs.contains(Tag.ContentTime)));
    }

    @Test
    void unknown_values_are_not_written() {
      ExifData exif =
          ExifData.parse(
              new TiffBuilder(ByteOrder.BIG_ENDIAN)
                  .rationals(Ifd.EXIF, 0xA432, 24, 1, 70, 1, 0, 0, 0, 0)
                  .srationals(Ifd.EXIF, 0x9400, 21, 0xFFFFFFFFL)
                  .ascii(Ifd.EXIF, ExifToDicom.DATE_TIME_ORIGINAL, "0000:00:00 00:00:00")
                  .ascii(Ifd.EXIF, ExifToDicom.DATE_TIME_DIGITIZED, "    :  :     :  :  ")
                  .ascii(Ifd.IMAGE, ExifToDicom.MODEL, "   ")
                  .build());
      Attributes attrs = new Attributes();

      ExifToDicom.fill(exif, attrs, true);

      assertTrue(attrs.isEmpty(), () -> "Unexpected attributes: " + attrs);
    }

    @Test
    void decimal_attribute_from_a_non_rational_entry_is_ignored() {
      // Seen in files written by tools that store a fraction as two SHORT values
      ExifData exif =
          ExifData.parse(
              new TiffBuilder(ByteOrder.LITTLE_ENDIAN)
                  .shorts(Ifd.EXIF, 0x829A, 1, 125)
                  .shorts(Ifd.EXIF, 0x829D, 28, 10)
                  .build());
      Attributes attrs = new Attributes();

      ExifToDicom.fill(exif, attrs, false);

      assertAll(
          () -> assertFalse(attrs.contains(Tag.ExposureTimeInSeconds)),
          () -> assertFalse(attrs.contains(Tag.FNumber)));
    }

    @Test
    void single_valued_text_is_sanitized_for_its_vr() {
      ExifData exif =
          ExifData.parse(
              new TiffBuilder(ByteOrder.LITTLE_ENDIAN)
                  .ascii(Ifd.IMAGE, ExifToDicom.SOFTWARE, "a\\b" + "x".repeat(80))
                  .ascii(Ifd.GPS, 0x0009, "a")
                  .build());
      Attributes attrs = new Attributes();

      ExifToDicom.fill(exif, attrs, true);

      String software = attrs.getString(Tag.SoftwareVersions);
      assertAll(
          () -> assertEquals(64, software.length()),
          () -> assertTrue(software.startsWith("a/b")),
          () -> assertEquals(1, attrs.getStrings(Tag.SoftwareVersions).length),
          () -> assertEquals("A", attrs.getString(Tag.GPSStatus)));
    }
  }

  @Nested
  class User_Comment {

    private Attributes fillWithComment(byte[] code, byte[] text) {
      byte[] value = new byte[code.length + text.length];
      System.arraycopy(code, 0, value, 0, code.length);
      System.arraycopy(text, 0, value, code.length, text.length);
      ExifData exif =
          ExifData.parse(
              new TiffBuilder(ByteOrder.LITTLE_ENDIAN)
                  .undefined(Ifd.EXIF, ExifToDicom.USER_COMMENT, value)
                  .build());
      Attributes attrs = new Attributes();
      ExifToDicom.fill(exif, attrs, false);
      return attrs;
    }

    @Test
    void ascii_comment_is_used_when_there_is_no_description() {
      Attributes attrs =
          fillWithComment(
              "ASCII\0\0\0".getBytes(StandardCharsets.US_ASCII),
              "Wound, day 3".getBytes(StandardCharsets.US_ASCII));

      assertEquals("Wound, day 3", attrs.getString(Tag.ImageComments));
    }

    @Test
    void unicode_comment_is_decoded() {
      Attributes attrs =
          fillWithComment(
              "UNICODE\0".getBytes(StandardCharsets.US_ASCII),
              "Plaie jour 3 é".getBytes(StandardCharsets.UTF_16LE));

      assertEquals("Plaie jour 3 é", attrs.getString(Tag.ImageComments));
    }

    @Test
    void blank_comment_is_ignored() {
      Attributes attrs = fillWithComment(new byte[8], "        ".getBytes(StandardCharsets.UTF_8));

      assertFalse(attrs.contains(Tag.ImageComments));
    }
  }

  @Nested
  class Identifying_Attributes {

    @Test
    void are_not_mapped_by_default_choice() {
      Attributes attrs = new Attributes();

      ExifToDicom.fill(camera(), attrs, false);

      assertAll(
          () -> assertFalse(attrs.contains(Tag.CameraOwnerName)),
          () -> assertFalse(attrs.contains(Tag.DeviceSerialNumber)),
          () -> assertFalse(attrs.contains(Tag.LensSerialNumber)),
          () -> assertFalse(attrs.contains(Tag.GPSLatitude)),
          () -> assertFalse(attrs.contains(Tag.GPSVersionID)),
          () -> assertFalse(attrs.contains(Tag.GPSDateStamp)),
          () -> assertFalse(attrs.contains(Tag.GPSTimeStamp)));
    }

    @Test
    void are_mapped_when_requested() {
      Attributes attrs = new Attributes();

      ExifToDicom.fill(camera(), attrs, true);

      assertAll(
          () -> assertEquals("Jane Doe", attrs.getString(Tag.CameraOwnerName)),
          () -> assertEquals("012345678", attrs.getString(Tag.DeviceSerialNumber)),
          () -> assertEquals("9876", attrs.getString(Tag.LensSerialNumber)),
          () -> assertArrayEquals(new byte[] {2, 3, 0, 0}, attrs.getBytes(Tag.GPSVersionID)),
          () -> assertEquals("N", attrs.getString(Tag.GPSLatitudeRef)),
          () ->
              assertArrayEquals(
                  new double[] {46, 12, 30.15}, attrs.getDoubles(Tag.GPSLatitude), 1e-12),
          () -> assertEquals("E", attrs.getString(Tag.GPSLongitudeRef)),
          () -> assertEquals(0, attrs.getInt(Tag.GPSAltitudeRef, -1)),
          () -> assertEquals(375.5, attrs.getDouble(Tag.GPSAltitude, 0), 1e-12),
          () -> assertEquals("20260920", attrs.getString(Tag.GPSDateStamp)),
          () -> assertEquals("20260920081112.500000+0000", attrs.getString(Tag.GPSTimeStamp)));
    }
  }

  @Test
  void empty_metadata_changes_nothing() {
    Attributes attrs = new Attributes();

    ExifToDicom.fill(ExifData.EMPTY, attrs, true);
    ExifToDicom.fill(null, attrs, true);

    assertTrue(attrs.isEmpty());
  }
}
