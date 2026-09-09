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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.dicom.exif.TiffBuilder.Ifd;
import org.weasis.dicom.tool.Dicomizer;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomizerExifTest {

  @TempDir Path tempDir;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void jpeg_maps_exif_and_keeps_identifying_tags_only_with_the_app_segments(boolean noAPPn)
      throws IOException, URISyntaxException {
    Path jpeg = tempDir.resolve("photo.jpg");
    Files.write(jpeg, withExif(Files.readAllBytes(sourceJpeg())));
    Path dcm = tempDir.resolve("photo_" + noAPPn + ".dcm");

    Dicomizer.jpeg(new Attributes(), jpeg, dcm, noAPPn);

    Attributes dataset;
    try (DicomInputStream in = new DicomInputStream(dcm.toFile())) {
      dataset = in.readDataset();
    }
    byte[] file = Files.readAllBytes(dcm);
    boolean exifInPixelData = indexOf(file, "Exif\0\0".getBytes(StandardCharsets.US_ASCII)) >= 0;
    assertAll(
        () -> assertEquals("Pixel 9", dataset.getString(Tag.ManufacturerModelName)),
        () -> assertEquals("20260920101112", dataset.getString(Tag.AcquisitionDateTime)),
        () -> assertEquals(noAPPn ? null : "SN-42", dataset.getString(Tag.DeviceSerialNumber)),
        () -> assertEquals(noAPPn ? null : "S", dataset.getString(Tag.GPSLatitudeRef)),
        () -> assertEquals(!noAPPn, exifInPixelData));
  }

  private Path sourceJpeg() throws URISyntaxException {
    var resource =
        getClass().getResource("/org/dcm4che3/imageio/codec/jpeg/readable/jfif-16bit-dqt.jpg");
    assertNotNull(resource, "Test JPEG resource not found");
    return Path.of(resource.toURI());
  }

  /** Inserts an APP1 Exif segment right after the SOI marker. */
  private static byte[] withExif(byte[] jpeg) {
    byte[] tiff =
        new TiffBuilder(ByteOrder.BIG_ENDIAN)
            .ascii(Ifd.IMAGE, ExifToDicom.MODEL, "Pixel 9")
            .ascii(Ifd.EXIF, ExifToDicom.DATE_TIME_ORIGINAL, "2026:09:20 10:11:12")
            .ascii(Ifd.EXIF, ExifToDicom.BODY_SERIAL_NUMBER, "SN-42")
            .ascii(Ifd.GPS, 0x0001, "S")
            .build();
    int length = 2 + 6 + tiff.length;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2);
    out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
    out.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
    out.writeBytes(tiff);
    out.write(jpeg, 2, jpeg.length - 2);
    return out.toByteArray();
  }

  private static int indexOf(byte[] data, byte[] pattern) {
    outer:
    for (int i = 0; i <= data.length - pattern.length; i++) {
      for (int j = 0; j < pattern.length; j++) {
        if (data[i + j] != pattern[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }
}
