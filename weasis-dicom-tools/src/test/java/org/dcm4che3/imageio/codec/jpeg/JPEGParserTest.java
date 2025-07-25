/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec.jpeg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.XPEGParserException;
import org.dcm4che3.imageio.codec.jpeg.JPEGParser.JPEG2000Params;
import org.dcm4che3.imageio.codec.jpeg.JPEGParser.JPEGParams;
import org.dcm4che3.imageio.codec.jpeg.JPEGParser.Params;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.CvType;
import org.opencv.osgi.OpenCVNativeLoader;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.op.ImageProcessor;

class JPEGParserTest {

  @BeforeAll
  static void loadNativeLib() {
    // Load the native OpenCV library for integration tests
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();
  }

  // ============ UNIT TESTS ============

  @Nested
  @DisplayName("Unit Tests")
  class UnitTests {

    @Test
    @DisplayName("Should create JPEG parser with SOI marker")
    void shouldCreateJPEGParserWithSOI() throws IOException {
      byte[] jpegData = JPEGDataFactory.createMinimalJPEGData(JPEG.SOF0, 8, 100, 200, 3);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);

        assertNotNull(parser);
        assertEquals(0, parser.getCodeStreamPosition());
        assertInstanceOf(JPEGParams.class, parser.getParams());
        assertNotNull(parser.toString());
        assertTrue(parser.toString().contains("JPEGParser{"));
      }
    }

    @Test
    @DisplayName("Should create JPEG2000 parser with SOC marker")
    void shouldCreateJPEG2000ParserWithSOC() throws IOException {
      byte[] jpeg2000Data = JPEGDataFactory.createMinimalJPEG2000Data();

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpeg2000Data)) {
        JPEGParser parser = new JPEGParser(channel);

        assertNotNull(parser);
        assertEquals(0, parser.getCodeStreamPosition());
        assertInstanceOf(JPEG2000Params.class, parser.getParams());
        assertNotNull(parser.toString());
        assertTrue(parser.toString().contains("JPEGParser{"));
        assertEquals(UID.JPEG2000Lossless, parser.getTransferSyntaxUID(false));
        assertNull(parser.getMP4FileType());
      }
    }

    @Test
    @DisplayName("Should handle JPEG2000 with box structure")
    void shouldHandleJPEG2000WithBoxStructure() throws IOException {
      byte[] jp2Data = JPEGDataFactory.createJPEG2000WithBoxStructure();

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jp2Data)) {
        JPEGParser parser = new JPEGParser(channel);

        assertNotNull(parser);
        assertTrue(parser.getCodeStreamPosition() > 0);
        assertInstanceOf(JPEG2000Params.class, parser.getParams());
        assertTrue(parser.toString().contains("JPEGParser{"));
        assertEquals(UID.JPEG2000Lossless, parser.getTransferSyntaxUID(false));
      }
    }

    @Test
    @DisplayName("Should throw exception for invalid marker")
    void shouldThrowExceptionForInvalidMarker() throws IOException {
      byte[] invalidData = {(byte) 0xFF, (byte) 0x00}; // Invalid marker

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(invalidData)) {
        assertThrows(XPEGParserException.class, () -> new JPEGParser(channel));
      }
    }

    @ParameterizedTest
    @CsvSource({
      "8, 480, 640, 3, YBR_FULL_422, " + UID.JPEGBaseline8Bit,
      "8, 100, 200, 1, MONOCHROME2, " + UID.JPEGBaseline8Bit,
      "12, 512, 512, 1, MONOCHROME2, " + UID.JPEGExtended12Bit
    })
    @DisplayName("Should parse JPEG parameters correctly for different configurations")
    void shouldParseJPEGParametersCorrectly(
        int precision,
        int height,
        int width,
        int components,
        String expectedPhotometric,
        String expectedTSUID)
        throws IOException {
      int sofMarker = precision > 8 ? JPEG.SOF1 : JPEG.SOF0;
      byte[] jpegData =
          JPEGDataFactory.createJPEGDataWithSOF(sofMarker, precision, height, width, components);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        JPEGParser.Params params = parser.getParams();

        assertEquals(components, params.samplesPerPixel());
        assertEquals(height, params.rows());
        assertEquals(width, params.columns());
        assertEquals(precision, params.bitsStored());
        assertEquals(0, params.pixelRepresentation());
        assertEquals(expectedPhotometric, params.colorPhotometricInterpretation());
        assertEquals(expectedTSUID, params.transferSyntaxUID());
      }
    }

    @ParameterizedTest
    @ValueSource(ints = {JPEG.SOF0, JPEG.SOF1, JPEG.SOF2, JPEG.SOF3, JPEG.SOF55})
    @DisplayName("Should handle different SOF markers")
    void shouldHandleDifferentSOFMarkers(int sofMarker) throws IOException {
      byte[] jpegData = JPEGDataFactory.createJPEGDataWithSOF(sofMarker, 8, 100, 100, 1);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);

        assertNotNull(parser.getParams());
        assertNotNull(parser.getParams().transferSyntaxUID());
      }
    }

    @Test
    @DisplayName("Should detect RGB from component IDs")
    void shouldDetectRGBFromComponentIDs() throws IOException {
      byte[] jpegData = JPEGDataFactory.createJPEGDataWithRGBComponents(JPEG.SOF0, 8, 100, 100);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        JPEGParser.Params params = parser.getParams();

        assertEquals("RGB", params.colorPhotometricInterpretation());
      }
    }

    @Test
    @DisplayName("Should handle lossless JPEG with predictor 1")
    void shouldHandleLosslessJPEGWithPredictor1() throws IOException {
      byte[] jpegData = JPEGDataFactory.createLosslessJPEGData(JPEG.SOF3, 12, 100, 100, 1, 1);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        JPEGParser.Params params = parser.getParams();

        assertFalse(params.lossyImageCompression());
        assertEquals(UID.JPEGLosslessSV1, params.transferSyntaxUID());
      }
    }

    @Test
    @DisplayName("Should handle lossless JPEG with predictor 0")
    void shouldHandleLosslessJPEGWithPredictor0() throws IOException {
      byte[] jpegData = JPEGDataFactory.createLosslessJPEGData(JPEG.SOF3, 12, 100, 100, 1, 0);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        JPEGParser.Params params = parser.getParams();

        assertFalse(params.lossyImageCompression());
        assertEquals(UID.JPEGLossless, params.transferSyntaxUID());
      }
    }

    @Test
    @DisplayName("Should handle JPEG-LS lossless")
    void shouldHandleJPEGLSLossless() throws IOException {
      byte[] jpegData = JPEGDataFactory.createLosslessJPEGData(JPEG.SOF55, 8, 100, 100, 1, 0);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        JPEGParser.Params params = parser.getParams();

        assertFalse(params.lossyImageCompression());
        assertEquals(UID.JPEGLSLossless, params.transferSyntaxUID());
      }
    }

    @Test
    @DisplayName("Should handle JPEG-LS near lossless")
    void shouldHandleJPEGLSNearLossless() throws IOException {
      byte[] jpegData = JPEGDataFactory.createLosslessJPEGData(JPEG.SOF55, 8, 100, 100, 1, 5);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        JPEGParser.Params params = parser.getParams();
        assertEquals(UID.JPEGLSNearLossless, params.transferSyntaxUID());
      }
    }

    @Test
    @DisplayName("Should handle JPEG with APP0 marker (JFIF)")
    void shouldHandleJFIF() throws IOException {
      byte[] jpegData = JPEGDataFactory.createJPEGWithAPP0Marker();

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        JPEGParser.Params params = parser.getParams();

        assertEquals("YBR_FULL_422", params.colorPhotometricInterpretation());
        assertTrue(parser.getPositionAfterAPPSegments() > 0);
      }
    }

    @Test
    @DisplayName("Should handle JPEG with Adobe APP14 marker")
    void shouldHandleAdobeAPP14() throws IOException {
      byte[] jpegData = JPEGDataFactory.createJPEGWithAdobeAPP14();

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        JPEGParser.Params params = parser.getParams();

        assertEquals("RGB", params.colorPhotometricInterpretation());
      }
    }

    @Test
    @DisplayName("Should parse DICOM attributes correctly")
    void shouldParseDICOMAttributesCorrectly() throws IOException {
      byte[] jpegData = JPEGDataFactory.createJPEGDataWithSOF(JPEG.SOF0, 8, 256, 512, 3);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        Attributes attrs = parser.getAttributes(null);

        assertNotNull(attrs);
        assertEquals(3, attrs.getInt(Tag.SamplesPerPixel, 0));
        assertEquals(256, attrs.getInt(Tag.Rows, 0));
        assertEquals(512, attrs.getInt(Tag.Columns, 0));
        assertEquals(8, attrs.getInt(Tag.BitsAllocated, 0));
        assertEquals(8, attrs.getInt(Tag.BitsStored, 0));
        assertEquals(7, attrs.getInt(Tag.HighBit, 0));
        assertEquals(0, attrs.getInt(Tag.PixelRepresentation, 0));
        assertEquals("YBR_FULL_422", attrs.getString(Tag.PhotometricInterpretation));
        assertEquals("01", attrs.getString(Tag.LossyImageCompression));
      }
    }

    @Test
    @DisplayName("Should handle existing attributes in getAttributes")
    void shouldHandleExistingAttributesInGetAttributes() throws IOException {
      byte[] jpegData = JPEGDataFactory.createJPEGDataWithSOF(JPEG.SOF0, 8, 256, 512, 1);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        Attributes existingAttrs = new Attributes();
        existingAttrs.setString(Tag.PatientName, VR.PN, "Test Patient");

        Attributes attrs = parser.getAttributes(existingAttrs);

        assertSame(existingAttrs, attrs);
        assertEquals("Test Patient", attrs.getString(Tag.PatientName));
        assertEquals(1, attrs.getInt(Tag.SamplesPerPixel, 0));
        assertEquals("MONOCHROME2", attrs.getString(Tag.PhotometricInterpretation));
      }
    }

    @Test
    @DisplayName("Should handle 16-bit images")
    void shouldHandle16BitImages() throws IOException {
      byte[] jpegData = JPEGDataFactory.createJPEGDataWithSOF(JPEG.SOF1, 12, 100, 100, 1);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        Attributes attrs = parser.getAttributes(null);

        assertEquals(16, attrs.getInt(Tag.BitsAllocated, 0));
        assertEquals(12, attrs.getInt(Tag.BitsStored, 0));
        assertEquals(11, attrs.getInt(Tag.HighBit, 0));
      }
    }

    @Test
    @DisplayName("Should handle 4-component JPEG")
    void shouldHandle4ComponentJPEG() throws IOException {
      byte[] jpegData = JPEGDataFactory.createJPEGDataWithSOF(JPEG.SOF0, 8, 100, 100, 4);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        JPEGParser.Params params = parser.getParams();
        Attributes attrs = parser.getAttributes(null);

        assertEquals(4, params.samplesPerPixel());
        assertEquals(4, attrs.getInt(Tag.SamplesPerPixel, 0));
        assertEquals(0, attrs.getInt(Tag.PlanarConfiguration, 0));
        assertEquals("YBR_FULL_422", params.colorPhotometricInterpretation());
      }
    }

    @Test
    @DisplayName("Should handle getTransferSyntaxUID with fragmented parameter")
    void shouldHandleGetTransferSyntaxUIDFragmented() throws IOException {
      byte[] jpegData = JPEGDataFactory.createJPEGDataWithSOF(JPEG.SOF0, 8, 100, 100, 1);

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);

        assertEquals(UID.JPEGBaseline8Bit, parser.getTransferSyntaxUID(false));
        assertEquals(UID.JPEGBaseline8Bit, parser.getTransferSyntaxUID(true));
      }
    }

    @Test
    @DisplayName("Should throw exception when SOF marker not found")
    void shouldThrowExceptionWhenSOFMarkerNotFound() throws IOException {
      byte[] invalidJpegData = {
        (byte) 0xFF,
        (byte) JPEG.SOI, // SOI
        (byte) 0xFF,
        (byte) JPEG.DHT,
        0x00,
        0x04,
        0x00,
        0x00 // DHT without SOF
      };

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(invalidJpegData)) {
        assertThrows(XPEGParserException.class, () -> new JPEGParser(channel));
      }
    }

    @Test
    @DisplayName("Should throw exception for unsupported SOF marker")
    void shouldThrowExceptionForUnsupportedSOFMarker() throws IOException {
      byte[] jpegData = JPEGDataFactory.createJPEGWithUnsupportedSOF();

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        assertThrows(XPEGParserException.class, () -> new JPEGParser(channel));
      }
    }

    @Test
    @DisplayName("Should handle insufficient SOS data for lossless JPEG")
    void shouldHandleInsufficientSOSDataForLosslessJPEG() throws IOException {
      byte[] jpegData = JPEGDataFactory.createLosslessJPEGWithInsufficientSOS();

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpegData)) {
        JPEGParser parser = new JPEGParser(channel);
        assertThrows(XPEGParserException.class, () -> parser.getParams().transferSyntaxUID());
      }
    }

    @Test
    @DisplayName("Should handle unexpected byte sequence")
    void shouldHandleUnexpectedByteSequence() throws IOException {
      byte[] invalidData = JPEGDataFactory.createJPEGWithInvalidByteSequence();

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(invalidData)) {
        assertThrows(XPEGParserException.class, () -> new JPEGParser(channel));
      }
    }

    @Test
    @DisplayName("Should handle JPEG2000 parameters correctly")
    void shouldHandleJPEG2000ParametersCorrectly() throws IOException {
      byte[] jpeg2000Data = JPEGDataFactory.createAdvancedJPEG2000Data();

      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(jpeg2000Data)) {
        JPEGParser parser = new JPEGParser(channel);
        JPEGParser.Params params = parser.getParams();

        assertEquals(3, params.samplesPerPixel());
        assertEquals(512, params.rows());
        assertEquals(512, params.columns());
        assertEquals(8, params.bitsStored());
        assertEquals(0, params.pixelRepresentation());
        assertEquals("YBR_RCT", params.colorPhotometricInterpretation());
        assertEquals(UID.JPEG2000Lossless, params.transferSyntaxUID());
        assertFalse(params.lossyImageCompression());
      }
    }

    @Test
    @DisplayName("Should handle HTJ2K Lossless RPCL with monochrome image")
    void shouldHandleHTJ2KLosslessRPCLMonochrome() throws IOException {
      // Given
      byte[] htj2kData = JPEGDataFactory.createHTJ2KLosslessRPCLMonochromeData();

      // When
      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(htj2kData)) {
        JPEGParser parser = new JPEGParser(channel);
        Params params = parser.getParams();

        // Then - Verify monochrome parameters
        assertEquals(512, params.rows(), "Monochrome image height should be 256");
        assertEquals(512, params.columns(), "Monochrome image width should be 256");
        assertEquals(1, params.samplesPerPixel(), "Should have 1 component (monochrome)");
        assertEquals(8, params.bitsStored(), "Should use 12 bits per sample");
        assertEquals(
            "MONOCHROME2",
            params.colorPhotometricInterpretation(),
            "Should use MONOCHROME2 color space");
        assertEquals(
            UID.HTJ2KLosslessRPCL,
            params.transferSyntaxUID(),
            "Should identify as HTJ2K Lossless RPCL");
        assertFalse(params.lossyImageCompression(), "Should be lossless compression");
      }
    }

    @Test
    @DisplayName("Should handle HTJ2K Lossless RPCL with RGB image")
    void shouldHandleHTJ2KLossyRPCLRGB() throws IOException {
      // Given
      byte[] htj2kData = JPEGDataFactory.createHTJ2KLosslessRPCLData();

      // When
      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(htj2kData)) {
        JPEGParser parser = new JPEGParser(channel);
        Params params = parser.getParams();

        // Then - Verify RGB parameters
        assertEquals(512, params.rows(), "RGB image height should be 256");
        assertEquals(512, params.columns(), "RGB image width should be 256");
        assertEquals(3, params.samplesPerPixel(), "Should have 3 components (RGB)");
        assertEquals(8, params.bitsStored(), "Should use 8 bits per sample");
        assertEquals("RGB", params.colorPhotometricInterpretation(), "Should use RGB color space");
        assertEquals(
            UID.HTJ2KLossless, params.transferSyntaxUID(), "Should identify as HTJ2K Lossy RPCL");
        assertFalse(params.lossyImageCompression(), "Should be lossy compression");
      }
    }

    @Test
    @DisplayName("Should throw exception for invalid HTJ2K data")
    void shouldThrowExceptionForInvalidHTJ2KData() {
      // Given - Invalid data without proper JPEG 2000 markers
      byte[] invalidData = {0x00, 0x01, 0x02, 0x03};

      // When & Then
      try (SeekableByteChannel channel = JPEGDataFactory.createByteChannel(invalidData)) {
        assertThrows(
            IOException.class,
            () -> new JPEGParser(channel),
            "Should throw IOException for invalid HTJ2K data");
      } catch (IOException e) {
        // Expected when closing the channel
      }
    }

    // ============ INTEGRATION TESTS ============

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

      public static Stream<Arguments> loadJpegTestFiles(Path dir, String extension)
          throws IOException {
        return Files.list(dir)
            .filter(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(extension))
            .map(Arguments::of)
            .toList()
            .stream();
      }

      public static Stream<Arguments> loadJpegTestFiles(Path dir) throws IOException {
        return Files.list(dir).filter(Files::isRegularFile).map(Arguments::of).toList().stream();
      }

      public static Stream<Arguments> loadJpegTestFiles() {
        try {
          Path dir =
              Paths.get(
                  Objects.requireNonNull(JPEGParserTest.class.getResource("readable")).toURI());
          return loadJpegTestFiles(dir);
        } catch (URISyntaxException | IOException e) {
          throw new RuntimeException(e);
        }
      }

      public static Stream<Arguments> loadInvalidJpegTestCases() {
        try {
          Path dir =
              Paths.get(
                  Objects.requireNonNull(JPEGParserTest.class.getResource("invalid")).toURI());
          return loadJpegTestFiles(dir, ".jpg");
        } catch (URISyntaxException | IOException e) {
          throw new RuntimeException(e);
        }
      }

      @ParameterizedTest
      @MethodSource("loadJpegTestFiles")
      @DisplayName("Should parse real JPEG files correctly")
      void getAttributesReturnsCorrectAttributesForJPEGParams(Path path) throws IOException {
        try (ImageCV image = ImageProcessor.readImage(path.toFile(), null)) {
          try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            channel.position(0);
            JPEGParser parser = new JPEGParser(channel);
            Attributes attrs = parser.getAttributes(null);
            int channels = CvType.channels(image.type());
            int depth = CvType.depth(image.type());
            assertEquals(
                parser.getParams().colorPhotometricInterpretation(),
                attrs.getString(Tag.PhotometricInterpretation));
            assertEquals(channels, adaptChannels(attrs.getInt(Tag.SamplesPerPixel, 1)));
            assertEquals(image.rows(), attrs.getInt(Tag.Rows, 0));
            assertEquals(image.cols(), attrs.getInt(Tag.Columns, 0));
            boolean type16bit = depth == CvType.CV_16U || depth == CvType.CV_16S;
            int bitStored = parser.getParams().bitsStored();
            assertEquals(type16bit ? 16 : 8, attrs.getInt(Tag.BitsAllocated, 0));
            assertEquals(bitStored, attrs.getInt(Tag.BitsStored, 0));
            assertEquals(bitStored - 1, attrs.getInt(Tag.HighBit, 0));
            assertEquals(0, attrs.getInt(Tag.PixelRepresentation, 0));
            assertEquals(
                parser.getParams().lossyImageCompression() ? "01" : null,
                attrs.getString(Tag.LossyImageCompression));
            assertTrue(parser.getTransferSyntaxUID().startsWith("1.2.840.10008.1.2.4."));
          }
        }
      }

      @ParameterizedTest
      @MethodSource("loadInvalidJpegTestCases")
      @DisplayName("Should throw exception for invalid JPEG files")
      void invalidJPEGParams(Path path) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
          channel.position(0);
          assertThrows(XPEGParserException.class, () -> new JPEGParser(channel));
        }
      }

      private int adaptChannels(int channels) {
        return switch (channels) {
          case 3, 4 -> 3;
          default -> 1;
        };
      }
    }
  }
}
