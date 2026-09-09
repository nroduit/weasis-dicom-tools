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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.weasis.dicom.exif.TiffBuilder.Ifd;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ExifDataTest {

  @TempDir Path tempDir;

  static Stream<ByteOrder> byteOrders() {
    return Stream.of(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN);
  }

  @Nested
  class Parsing {

    @ParameterizedTest
    @MethodSource("org.weasis.dicom.exif.ExifDataTest#byteOrders")
    void reads_image_exif_and_gps_directories(ByteOrder order) {
      byte[] tiff =
          new TiffBuilder(order)
              .ascii(Ifd.IMAGE, 0x010F, "Canon")
              .shorts(Ifd.EXIF, 0x8827, 400)
              .rationals(Ifd.EXIF, 0x829A, 1, 125)
              .srationals(Ifd.EXIF, 0x9204, -2, 3)
              .ascii(Ifd.GPS, 0x0001, "N")
              .rationals(Ifd.GPS, 0x0002, 46, 1, 12, 1, 3015, 100)
              .build();

      ExifData exif = ExifData.parse(tiff);

      assertAll(
          () -> assertEquals("Canon", exif.get(0x010F).orElseThrow().asString()),
          () -> assertArrayEquals(new long[] {400}, exif.get(0x8827).orElseThrow().asLongs()),
          () -> assertEquals(0.008, exif.get(0x829A).orElseThrow().asDoubles()[0], 1e-12),
          () -> assertEquals(-2 / 3.0, exif.get(0x9204).orElseThrow().asDoubles()[0], 1e-12),
          () -> assertEquals("N", exif.getGps(0x0001).orElseThrow().asString()),
          () ->
              assertArrayEquals(
                  new double[] {46, 12, 30.15},
                  exif.getGps(0x0002).orElseThrow().asDoubles(),
                  1e-12),
          () -> assertTrue(exif.getGps(0x010F).isEmpty(), "GPS tags have their own namespace"));
    }

    @Test
    void unknown_rational_is_nan() {
      byte[] tiff =
          new TiffBuilder(ByteOrder.LITTLE_ENDIAN)
              .rationals(Ifd.EXIF, 0xA432, 24, 1, 70, 1, 0, 0, 0xFFFFFFFFL, 0xFFFFFFFFL)
              .srationals(Ifd.EXIF, 0x9400, 5, 0xFFFFFFFFL)
              .build();

      ExifData exif = ExifData.parse(tiff);

      double[] lens = exif.get(0xA432).orElseThrow().asDoubles();
      assertAll(
          () -> assertEquals(24, lens[0]),
          () -> assertEquals(70, lens[1]),
          () -> assertTrue(Double.isNaN(lens[2])),
          () -> assertTrue(Double.isNaN(lens[3])),
          () -> assertTrue(Double.isNaN(exif.get(0x9400).orElseThrow().asDoubles()[0])));
    }

    @Test
    void integer_accessor_is_empty_for_rationals() {
      byte[] tiff =
          new TiffBuilder(ByteOrder.BIG_ENDIAN).rationals(Ifd.EXIF, 0x829D, 28, 10).build();

      assertEquals(0, ExifData.parse(tiff).get(0x829D).orElseThrow().asLongs().length);
    }

    @Test
    void invalid_blocks_give_empty_metadata() {
      byte[] wrongMagic = {'I', 'I', 43, 0, 8, 0, 0, 0};
      assertAll(
          () -> assertSame(ExifData.EMPTY, ExifData.parse(null)),
          () -> assertSame(ExifData.EMPTY, ExifData.parse(new byte[4])),
          () -> assertSame(ExifData.EMPTY, ExifData.parse("not a tiff block".getBytes())),
          () -> assertSame(ExifData.EMPTY, ExifData.parse(wrongMagic)));
    }

    @Test
    void entry_pointing_outside_the_block_is_skipped() {
      byte[] tiff =
          new TiffBuilder(ByteOrder.LITTLE_ENDIAN)
              .ascii(Ifd.IMAGE, 0x010F, "A long manufacturer name")
              .ascii(Ifd.IMAGE, 0x0110, "Model")
              .build();
      // First entry of IFD0 (at offset 10): move its value offset beyond the end
      ByteBuffer.wrap(tiff).order(ByteOrder.LITTLE_ENDIAN).putInt(10 + 8, 100_000);

      ExifData exif = ExifData.parse(tiff);

      assertAll(
          () -> assertTrue(exif.get(0x010F).isEmpty()),
          () -> assertEquals("Model", exif.get(0x0110).orElseThrow().asString()));
    }

    @Test
    void sub_directory_loop_does_not_hang() {
      byte[] tiff =
          new TiffBuilder(ByteOrder.LITTLE_ENDIAN)
              .ascii(Ifd.IMAGE, 0x010F, "Make")
              .longs(Ifd.IMAGE, ExifData.EXIF_IFD_POINTER, 8) // Points back to IFD0
              .build();

      assertEquals("Make", ExifData.parse(tiff).get(0x010F).orElseThrow().asString());
    }
  }

  @Nested
  class Jpeg_Reading {

    @Test
    void finds_the_exif_segment_after_other_app_segments() throws IOException {
      byte[] tiff =
          new TiffBuilder(ByteOrder.BIG_ENDIAN).ascii(Ifd.IMAGE, 0x0110, "Pixel 9").build();
      Path file = tempDir.resolve("photo.jpg");
      Files.write(file, TiffBuilder.jpeg(tiff));

      assertEquals("Pixel 9", ExifData.readJpeg(file).get(0x0110).orElseThrow().asString());
    }

    @Test
    void jpeg_without_exif_gives_empty_metadata() throws IOException {
      Path file = tempDir.resolve("no-exif.jpg");
      Files.write(file, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9});

      assertSame(ExifData.EMPTY, ExifData.readJpeg(file));
    }

    @Test
    void non_jpeg_or_truncated_file_gives_empty_metadata() throws IOException {
      Path png = tempDir.resolve("image.png");
      Files.write(png, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
      Path truncated = tempDir.resolve("truncated.jpg");
      Files.write(truncated, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x10});

      assertAll(
          () -> assertSame(ExifData.EMPTY, ExifData.readJpeg(png)),
          () -> assertSame(ExifData.EMPTY, ExifData.readJpeg(truncated)));
    }
  }
}
