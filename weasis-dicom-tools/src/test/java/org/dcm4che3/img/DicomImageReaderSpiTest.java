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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DicomImageReaderSpiTest {

  private DicomImageReaderSpi readerSpi;

  @BeforeEach
  void setUp() {
    readerSpi = new DicomImageReaderSpi();
  }

  @Nested
  @DisplayName("Basic SPI Properties")
  class BasicSpiProperties {

    @Test
    @DisplayName("Should return correct description")
    void shouldReturnCorrectDescription() {
      Assertions.assertEquals("DICOM Image Reader (dcm4che)", readerSpi.getDescription(null));
      Assertions.assertEquals("DICOM Image Reader (dcm4che)", readerSpi.getDescription(Locale.US));
      Assertions.assertEquals(
          "DICOM Image Reader (dcm4che)", readerSpi.getDescription(Locale.FRANCE));
    }

    @Test
    @DisplayName("Should create DicomImageReader instance")
    void shouldCreateDicomImageReaderInstance() {
      ImageReader reader = readerSpi.createReaderInstance(null);
      Assertions.assertInstanceOf(DicomImageReader.class, reader);
      Assertions.assertSame(readerSpi, reader.getOriginatingProvider());
    }

    @Test
    @DisplayName("Should create DicomImageReader instance with extension parameter")
    void shouldCreateDicomImageReaderInstanceWithExtension() {
      Object extension = new Object();
      ImageReader reader = readerSpi.createReaderInstance(extension);
      Assertions.assertInstanceOf(DicomImageReader.class, reader);
      Assertions.assertSame(readerSpi, reader.getOriginatingProvider());
    }

    @Test
    @DisplayName("Should have correct format names")
    void shouldHaveCorrectFormatNames() {
      String[] formatNames = readerSpi.getFormatNames();
      Assertions.assertArrayEquals(new String[] {"dicom", "DICOM"}, formatNames);
    }

    @Test
    @DisplayName("Should have correct file suffixes")
    void shouldHaveCorrectFileSuffixes() {
      String[] suffixes = readerSpi.getFileSuffixes();
      Assertions.assertArrayEquals(new String[] {"dcm", "dic", "dicm", "dicom"}, suffixes);
    }

    @Test
    @DisplayName("Should have correct MIME types")
    void shouldHaveCorrectMimeTypes() {
      String[] mimeTypes = readerSpi.getMIMETypes();
      Assertions.assertArrayEquals(new String[] {"application/dicom"}, mimeTypes);
    }
  }

  @Nested
  @DisplayName("DICOM Format Detection")
  class DicomFormatDetection {

    @Test
    @DisplayName("Should detect DICOM by standard preamble and DICM prefix")
    void shouldDetectDicomByStandardPreamble() throws IOException {
      byte[] dicomData = createDicomWithPreamble();
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(dicomData));

      Assertions.assertTrue(readerSpi.canDecodeInput(iis));
      // Verify stream position is restored
      Assertions.assertEquals(0, iis.getStreamPosition());
    }

    @Test
    @DisplayName("Should detect DICOM by valid tag at beginning")
    void shouldDetectDicomByValidTag() throws IOException {
      byte[] dicomData = createDicomWithValidTag(0x00080010); // Patient Name tag
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(dicomData));

      Assertions.assertTrue(readerSpi.canDecodeInput(iis));
      Assertions.assertEquals(0, iis.getStreamPosition());
    }

    @ParameterizedTest
    @ValueSource(ints = {0x00080000, 0x00080001, 0x00080005, 0x00080010, 0x00080016})
    @DisplayName("Should detect DICOM by various valid tags")
    void shouldDetectDicomByVariousValidTags(int tag) throws IOException {
      byte[] dicomData = createDicomWithValidTag(tag);
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(dicomData));

      Assertions.assertTrue(readerSpi.canDecodeInput(iis));
    }

    @Test
    @DisplayName("Should not detect DICOM with invalid preamble")
    void shouldNotDetectDicomWithInvalidPreamble() throws IOException {
      byte[] invalidData = createDicomWithPreamble();
      invalidData[128] = 'A'; // Corrupt the DICM prefix
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(invalidData));

      Assertions.assertFalse(readerSpi.canDecodeInput(iis));
    }

    @Test
    @DisplayName("Should not detect DICOM with invalid tag")
    void shouldNotDetectDicomWithInvalidTag() throws IOException {
      byte[] invalidData = createDicomWithValidTag(0x00070000); // Invalid tag range
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(invalidData));

      Assertions.assertFalse(readerSpi.canDecodeInput(iis));
    }

    @Test
    @DisplayName("Should not detect DICOM with tag too high")
    void shouldNotDetectDicomWithTagTooHigh() throws IOException {
      byte[] invalidData = createDicomWithValidTag(0x00080017); // Just above valid range
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(invalidData));

      Assertions.assertFalse(readerSpi.canDecodeInput(iis));
    }

    @Test
    @DisplayName("Should handle incomplete preamble gracefully")
    void shouldHandleIncompletePreambleGracefully() throws IOException {
      byte[] shortData = new byte[100]; // Too short for preamble
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(shortData));

      Assertions.assertFalse(readerSpi.canDecodeInput(iis));
    }

    @Test
    @DisplayName("Should handle incomplete tag data gracefully")
    void shouldHandleIncompleteTagDataGracefully() throws IOException {
      byte[] shortData = new byte[2]; // Too short for complete tag
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(shortData));

      Assertions.assertFalse(readerSpi.canDecodeInput(iis));
    }

    @Test
    @DisplayName("Should handle empty input gracefully")
    void shouldHandleEmptyInputGracefully() throws IOException {
      byte[] emptyData = new byte[0];
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(emptyData));

      Assertions.assertFalse(readerSpi.canDecodeInput(iis));
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesAndErrorHandling {

    @Test
    @DisplayName("Should return false for non-ImageInputStream input")
    void shouldReturnFalseForNonImageInputStreamInput() throws IOException {
      Assertions.assertFalse(readerSpi.canDecodeInput("not an input stream"));
      Assertions.assertFalse(readerSpi.canDecodeInput(new ByteArrayInputStream(new byte[10])));
      Assertions.assertFalse(readerSpi.canDecodeInput(null));
    }

    @Test
    @DisplayName("Should handle DICOM with minimal valid preamble")
    void shouldHandleDicomWithMinimalValidPreamble() throws IOException {
      byte[] minimalDicom = new byte[132];
      // Set DICM at position 128
      minimalDicom[128] = 'D';
      minimalDicom[129] = 'I';
      minimalDicom[130] = 'C';
      minimalDicom[131] = 'M';

      ImageInputStream iis =
          new MemoryCacheImageInputStream(new ByteArrayInputStream(minimalDicom));
      Assertions.assertTrue(readerSpi.canDecodeInput(iis));
    }

    @Test
    @DisplayName("Should preserve stream position after failed detection")
    void shouldPreserveStreamPositionAfterFailedDetection() throws IOException {
      byte[] invalidData = new byte[200];
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(invalidData));

      // Move stream position
      iis.skipBytes(10);
      long initialPosition = iis.getStreamPosition();

      Assertions.assertFalse(readerSpi.canDecodeInput(iis));
      Assertions.assertEquals(initialPosition, iis.getStreamPosition());
    }

    @Test
    @DisplayName("Should preserve stream position after successful detection")
    void shouldPreserveStreamPositionAfterSuccessfulDetection() throws IOException {
      byte[] dicomData = createDicomWithPreamble();
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(dicomData));

      long initialPosition = iis.getStreamPosition();

      Assertions.assertTrue(readerSpi.canDecodeInput(iis));
      Assertions.assertEquals(initialPosition, iis.getStreamPosition());
    }
  }

  @Nested
  @DisplayName("Little Endian Tag Detection")
  class LittleEndianTagDetection {

    @Test
    @DisplayName("Should correctly read little endian tags")
    void shouldCorrectlyReadLittleEndianTags() throws IOException {
      // Create data with tag 0x00080010 (Patient Name) in little endian format
      ByteBuffer buffer = ByteBuffer.allocate(20);
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      buffer.putInt(0x00080010); // Valid DICOM tag
      buffer.put(new byte[16]); // Some additional data

      ImageInputStream iis =
          new MemoryCacheImageInputStream(new ByteArrayInputStream(buffer.array()));

      Assertions.assertTrue(readerSpi.canDecodeInput(iis));
    }

    @Test
    @DisplayName("Should handle mixed endian scenarios")
    void shouldHandleMixedEndianScenarios() throws IOException {
      // Create data that would be invalid if read as big endian
      ByteBuffer buffer = ByteBuffer.allocate(132);
      buffer.order(ByteOrder.LITTLE_ENDIAN);

      // First 4 bytes: invalid tag when read as little endian
      buffer.putInt(0x10000800); // This becomes 0x00080010 when read as little endian

      // Skip to preamble position
      buffer.position(128);
      buffer.put((byte) 'D');
      buffer.put((byte) 'I');
      buffer.put((byte) 'C');
      buffer.put((byte) 'M');

      ImageInputStream iis =
          new MemoryCacheImageInputStream(new ByteArrayInputStream(buffer.array()));

      // Should be detected by preamble, not by tag
      Assertions.assertTrue(readerSpi.canDecodeInput(iis));
    }
  }

  @Nested
  @DisplayName("Real DICOM-like Data")
  class RealDicomLikeData {

    @Test
    @DisplayName("Should detect DICOM with typical file structure")
    void shouldDetectDicomWithTypicalFileStructure() throws IOException {
      byte[] dicomData = createRealisticDicomHeader();
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(dicomData));

      Assertions.assertTrue(readerSpi.canDecodeInput(iis));
    }

    @Test
    @DisplayName("Should detect DICOM without preamble (implicit VR)")
    void shouldDetectDicomWithoutPreamble() throws IOException {
      byte[] dicomData = createDicomWithoutPreamble();
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(dicomData));

      Assertions.assertTrue(readerSpi.canDecodeInput(iis));
    }

    @Test
    @DisplayName("Should not detect random binary data")
    void shouldNotDetectRandomBinaryData() throws IOException {
      byte[] randomData = createRandomBinaryData();
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(randomData));

      Assertions.assertFalse(readerSpi.canDecodeInput(iis));
    }

    @Test
    @DisplayName("Should not detect other image formats")
    void shouldNotDetectOtherImageFormats() throws IOException {
      // JPEG header
      byte[] jpegData = createJpegLikeHeader();
      ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(jpegData));
      Assertions.assertFalse(readerSpi.canDecodeInput(iis));

      // PNG header
      byte[] pngData = createPngLikeHeader();
      iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(pngData));
      Assertions.assertFalse(readerSpi.canDecodeInput(iis));
    }
  }

  // Helper methods to create test data

  /** Creates a DICOM-like byte array with proper preamble and DICM prefix. */
  private byte[] createDicomWithPreamble() {
    byte[] data = new byte[200]; // Enough space for preamble + some data

    // Fill preamble with zeros (first 128 bytes)
    // Add DICM prefix at position 128
    data[128] = 'D';
    data[129] = 'I';
    data[130] = 'C';
    data[131] = 'M';

    // Add some dummy DICOM data after the prefix
    ByteBuffer buffer = ByteBuffer.wrap(data, 132, data.length - 132);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(0x00080010); // Patient Name tag
    buffer.put((byte) 'P'); // VR = PN
    buffer.put((byte) 'N');
    buffer.putShort((short) 16); // Length

    return data;
  }

  /** Creates a DICOM-like byte array starting with a valid tag. */
  private byte[] createDicomWithValidTag(int tag) {
    ByteBuffer buffer = ByteBuffer.allocate(100);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(tag);
    buffer.put(new byte[96]); // Fill with zeros

    return buffer.array();
  }

  /** Creates a realistic DICOM header structure. */
  private byte[] createRealisticDicomHeader() {
    ByteBuffer buffer = ByteBuffer.allocate(300);

    // 128-byte preamble (zeros)
    buffer.position(128);

    // DICM prefix
    buffer.put((byte) 'D');
    buffer.put((byte) 'I');
    buffer.put((byte) 'C');
    buffer.put((byte) 'M');

    // File Meta Information Group Length
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(0x00020000);
    buffer.put((byte) 'U'); // VR = UL
    buffer.put((byte) 'L');
    buffer.putShort((short) 4); // Length
    buffer.putInt(100); // Dummy value

    // Transfer Syntax UID
    buffer.putInt(0x00020010);
    buffer.put((byte) 'U'); // VR = UI
    buffer.put((byte) 'I');
    buffer.putShort((short) 26); // Length
    buffer.put("1.2.840.10008.1.2.1\0\0\0\0\0\0".getBytes()); // Explicit VR Little Endian

    return buffer.array();
  }

  /** Creates DICOM data without preamble (starts directly with tags). */
  private byte[] createDicomWithoutPreamble() {
    ByteBuffer buffer = ByteBuffer.allocate(100);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    // Start with Patient Name tag
    buffer.putInt(0x00080010);
    buffer.putInt(0x00000010); // VR and length (implicit VR format)
    buffer.put("TEST PATIENT    ".getBytes());

    // Add another tag
    buffer.putInt(0x00080020); // Study Date
    buffer.putInt(0x00000008); // Length
    buffer.put("20240101".getBytes());

    return buffer.array();
  }

  /** Creates random binary data that should not be detected as DICOM. */
  private byte[] createRandomBinaryData() {
    byte[] data = new byte[200];
    // Fill with pseudo-random data
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i * 7 + 13); // Simple pseudo-random pattern
    }
    return data;
  }

  /** Creates JPEG-like header that should not be detected as DICOM. */
  private byte[] createJpegLikeHeader() {
    byte[] data = new byte[200];
    data[0] = (byte) 0xFF;
    data[1] = (byte) 0xD8; // JPEG SOI marker
    data[2] = (byte) 0xFF;
    data[3] = (byte) 0xE0; // JFIF marker
    return data;
  }

  /** Creates PNG-like header that should not be detected as DICOM. */
  private byte[] createPngLikeHeader() {
    byte[] data = new byte[200];
    // PNG signature
    data[0] = (byte) 0x89;
    data[1] = 0x50; // 'P'
    data[2] = 0x4E; // 'N'
    data[3] = 0x47; // 'G'
    data[4] = 0x0D;
    data[5] = 0x0A;
    data[6] = 0x1A;
    data[7] = 0x0A;
    return data;
  }
}
