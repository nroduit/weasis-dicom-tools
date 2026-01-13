/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.stream.Stream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomImageReaderSpiTest {

  private DicomImageReaderSpi readerSpi;

  @BeforeEach
  void setUp() {
    readerSpi = new DicomImageReaderSpi();
  }

  @Nested
  class Basic_SPI_Properties {

    @ParameterizedTest
    @MethodSource("org.dcm4che3.img.DicomImageReaderSpiTest#localeProvider")
    void should_return_correct_description_for_all_locales(Locale locale) {
      assertEquals("DICOM Image Reader (dcm4che)", readerSpi.getDescription(locale));
    }

    @Test
    void should_create_DicomImageReader_instance() {
      var reader = readerSpi.createReaderInstance(null);
      assertInstanceOf(DicomImageReader.class, reader);
      assertSame(readerSpi, reader.getOriginatingProvider());
    }

    @Test
    void should_create_DicomImageReader_instance_with_extension_parameter() {
      var extension = new Object();
      var reader = readerSpi.createReaderInstance(extension);
      assertInstanceOf(DicomImageReader.class, reader);
      assertSame(readerSpi, reader.getOriginatingProvider());
    }

    @Test
    void should_have_correct_format_names() {
      var formatNames = readerSpi.getFormatNames();
      assertArrayEquals(new String[] {"dicom", "DICOM"}, formatNames);
    }

    @Test
    void should_have_correct_file_suffixes() {
      var suffixes = readerSpi.getFileSuffixes();
      assertArrayEquals(new String[] {"dcm", "dic", "dicm", "dicom"}, suffixes);
    }

    @Test
    void should_have_correct_MIME_types() {
      var mimeTypes = readerSpi.getMIMETypes();
      assertArrayEquals(new String[] {"application/dicom"}, mimeTypes);
    }
  }

  @Nested
  class DICOM_Format_Detection {

    @Test
    void should_detect_DICOM_by_standard_preamble_and_DICM_prefix() throws IOException {
      var dicomData = DicomTestDataFactory.createDicomWithPreamble();
      var iis = createImageInputStream(dicomData);

      assertTrue(readerSpi.canDecodeInput(iis));
      assertEquals(0, iis.getStreamPosition(), "Stream position should be restored");
    }

    @Test
    void should_detect_DICOM_by_valid_tag_at_beginning() throws IOException {
      var dicomData = DicomTestDataFactory.createDicomWithValidTag(0x0008_0010);
      var iis = createImageInputStream(dicomData);

      assertTrue(readerSpi.canDecodeInput(iis));
      assertEquals(0, iis.getStreamPosition(), "Stream position should be restored");
    }

    @ParameterizedTest
    @ValueSource(ints = {0x0008_0000, 0x0008_0001, 0x0008_0005, 0x0008_0010, 0x0008_0016})
    void should_detect_DICOM_by_various_valid_tags(int tag) throws IOException {
      var dicomData = DicomTestDataFactory.createDicomWithValidTag(tag);
      var iis = createImageInputStream(dicomData);

      assertTrue(readerSpi.canDecodeInput(iis));
    }

    @Test
    void should_not_detect_DICOM_with_invalid_preamble() throws IOException {
      var invalidData = DicomTestDataFactory.createDicomWithPreamble();
      invalidData[128] = 'A'; // Corrupt the DICM prefix
      var iis = createImageInputStream(invalidData);

      assertFalse(readerSpi.canDecodeInput(iis));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x0007_0000, 0x0008_0017, 0x0009_0000}) // Invalid tag ranges
    void should_not_detect_DICOM_with_invalid_tags(int invalidTag) throws IOException {
      var invalidData = DicomTestDataFactory.createDicomWithValidTag(invalidTag);
      try (var iis = createImageInputStream(invalidData)) {
        assertThrows(EOFException.class, () -> readerSpi.canDecodeInput(iis));
      }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 50, 100, 127}) // Various incomplete lengths
    void should_handle_incomplete_data_gracefully(int dataLength) throws IOException {
      var shortData = new byte[dataLength];
      try (var iis = createImageInputStream(shortData)) {
        assertThrows(EOFException.class, () -> readerSpi.canDecodeInput(iis));
      }
    }
  }

  @Nested
  class Edge_Cases_And_Error_Handling {

    @ParameterizedTest
    @MethodSource("org.dcm4che3.img.DicomImageReaderSpiTest#invalidInputProvider")
    void should_return_false_for_invalid_input(Object invalidInput) throws IOException {
      assertFalse(readerSpi.canDecodeInput(invalidInput));
    }

    @Test
    void should_handle_DICOM_with_minimal_valid_preamble() throws IOException {
      var minimalDicom = DicomTestDataFactory.createMinimalValidDicom();
      var iis = createImageInputStream(minimalDicom);

      assertTrue(readerSpi.canDecodeInput(iis));
    }

    @Test
    void should_preserve_stream_position_after_failed_detection() throws IOException {
      var invalidData = new byte[200];
      var iis = createImageInputStream(invalidData);

      iis.skipBytes(10);
      var initialPosition = iis.getStreamPosition();

      assertFalse(readerSpi.canDecodeInput(iis));
      assertEquals(initialPosition, iis.getStreamPosition());
    }

    @Test
    void should_preserve_stream_position_after_successful_detection() throws IOException {
      var dicomData = DicomTestDataFactory.createDicomWithPreamble();
      var iis = createImageInputStream(dicomData);

      var initialPosition = iis.getStreamPosition();

      assertTrue(readerSpi.canDecodeInput(iis));
      assertEquals(initialPosition, iis.getStreamPosition());
    }
  }

  @Nested
  class Little_Endian_Tag_Detection {

    @Test
    void should_correctly_read_little_endian_tags() throws IOException {
      var buffer = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
      buffer.putInt(0x0008_0010); // Valid DICOM tag
      buffer.put(new byte[16]); // Additional data

      var iis = createImageInputStream(buffer.array());
      assertTrue(readerSpi.canDecodeInput(iis));
    }

    @Test
    void should_handle_mixed_endian_scenarios() throws IOException {
      var buffer = ByteBuffer.allocate(132).order(ByteOrder.LITTLE_ENDIAN);

      // Invalid tag when read as little endian, but valid preamble later
      buffer.putInt(0x1000_0800);

      // Add valid DICM preamble
      buffer.position(128);
      buffer.put("DICM".getBytes());

      var iis = createImageInputStream(buffer.array());
      assertTrue(readerSpi.canDecodeInput(iis), "Should be detected by preamble");
    }
  }

  @Nested
  class Real_DICOM_Like_Data {

    @Test
    void should_detect_DICOM_with_typical_file_structure() throws IOException {
      var dicomData = DicomTestDataFactory.createRealisticDicomHeader();
      var iis = createImageInputStream(dicomData);

      assertTrue(readerSpi.canDecodeInput(iis));
    }

    @Test
    void should_detect_DICOM_without_preamble() throws IOException {
      var dicomData = DicomTestDataFactory.createDicomWithoutPreamble();
      var iis = createImageInputStream(dicomData);

      assertTrue(readerSpi.canDecodeInput(iis));
    }

    @Test
    void should_not_detect_random_binary_data() throws IOException {
      var randomData = DicomTestDataFactory.createRandomBinaryData(200);
      var iis = createImageInputStream(randomData);

      assertFalse(readerSpi.canDecodeInput(iis));
    }

    @ParameterizedTest
    @MethodSource("org.dcm4che3.img.DicomImageReaderSpiTest#otherImageFormatProvider")
    void should_not_detect_other_image_formats(byte[] imageData, String formatName)
        throws IOException {
      var iis = createImageInputStream(imageData);
      assertFalse(
          readerSpi.canDecodeInput(iis), () -> "Should not detect " + formatName + " as DICOM");
    }
  }

  // Test data providers

  static Stream<Locale> localeProvider() {
    return Stream.of(null, Locale.US, Locale.FRANCE, Locale.GERMANY);
  }

  static Stream<Object> invalidInputProvider() {
    return Stream.of(
        "not an input stream", new ByteArrayInputStream(new byte[10]), null, new Object(), 123);
  }

  static Stream<Arguments> otherImageFormatProvider() {
    return Stream.of(
        Arguments.of(DicomTestDataFactory.createJpegLikeHeader(), "JPEG"),
        Arguments.of(DicomTestDataFactory.createPngLikeHeader(), "PNG"),
        Arguments.of(DicomTestDataFactory.createBmpLikeHeader(), "BMP"),
        Arguments.of(DicomTestDataFactory.createTiffLikeHeader(), "TIFF"));
  }

  // Utility methods

  private ImageInputStream createImageInputStream(byte[] data) {
    return new MemoryCacheImageInputStream(new ByteArrayInputStream(data));
  }

  // Factory class for creating test data - keeps test data creation separate and reusable
  static class DicomTestDataFactory {

    static byte[] createDicomWithPreamble() {
      var data = new byte[200];

      // DICM prefix at position 128
      System.arraycopy("DICM".getBytes(), 0, data, 128, 4);

      // Add realistic DICOM data after prefix
      var buffer = ByteBuffer.wrap(data, 132, data.length - 132);
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      buffer.putInt(0x0008_0010); // Patient Name tag
      buffer.put((byte) 'P').put((byte) 'N'); // VR = PN
      buffer.putShort((short) 16); // Length

      return data;
    }

    static byte[] createDicomWithValidTag(int tag) {
      var buffer = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN);
      buffer.putInt(tag);
      buffer.put(new byte[96]); // Fill remaining with zeros
      return buffer.array();
    }

    static byte[] createMinimalValidDicom() {
      var data = new byte[132];
      System.arraycopy("DICM".getBytes(), 0, data, 128, 4);
      return data;
    }

    static byte[] createRealisticDicomHeader() {
      var buffer = ByteBuffer.allocate(300);

      // 128-byte preamble (zeros) - position to 128
      buffer.position(128);
      buffer.put("DICM".getBytes());

      buffer.order(ByteOrder.LITTLE_ENDIAN);

      // File Meta Information Group Length
      buffer
          .putInt(0x0002_0000)
          .put((byte) 'U')
          .put((byte) 'L') // VR = UL
          .putShort((short) 4) // Length
          .putInt(100); // Dummy value

      // Transfer Syntax UID
      buffer
          .putInt(0x0002_0010)
          .put((byte) 'U')
          .put((byte) 'I') // VR = UI
          .putShort((short) 26); // Length

      var transferSyntax = "1.2.840.10008.1.2.1\0\0\0\0\0\0";
      buffer.put(transferSyntax.getBytes());

      return buffer.array();
    }

    static byte[] createDicomWithoutPreamble() {
      var buffer = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN);

      // Patient Name tag
      buffer
          .putInt(0x0008_0010)
          .putInt(0x0000_0010) // VR and length (implicit VR format)
          .put("TEST PATIENT    ".getBytes());

      // Study Date tag
      buffer
          .putInt(0x0008_0020) // Study Date
          .putInt(0x0000_0008) // Length
          .put("20240101".getBytes());

      return buffer.array();
    }

    static byte[] createRandomBinaryData(int size) {
      var data = new byte[size];
      // Simple deterministic "random" pattern for reproducible tests
      for (int i = 0; i < data.length; i++) {
        data[i] = (byte) (i * 7 + 13);
      }
      return data;
    }

    static byte[] createJpegLikeHeader() {
      var data = new byte[200];
      data[0] = (byte) 0xFF;
      data[1] = (byte) 0xD8; // JPEG SOI marker
      data[2] = (byte) 0xFF;
      data[3] = (byte) 0xE0; // JFIF marker
      return data;
    }

    static byte[] createPngLikeHeader() {
      var data = new byte[200];
      // PNG signature
      var pngSignature = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
      System.arraycopy(pngSignature, 0, data, 0, pngSignature.length);
      return data;
    }

    static byte[] createBmpLikeHeader() {
      var data = new byte[200];
      data[0] = 'B';
      data[1] = 'M';
      return data;
    }

    static byte[] createTiffLikeHeader() {
      var data = new byte[200];
      // TIFF little-endian signature
      data[0] = 0x49;
      data[1] = 0x49;
      data[2] = 0x2A;
      data[3] = 0x00;
      return data;
    }
  }
}
