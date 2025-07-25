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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("JPEG Header Tests")
class JPEGHeaderTest {

  @Nested
  @DisplayName("SOF Marker Tests")
  class SOFMarkerTests {

    @Test
    @DisplayName("SOF0 (Baseline JPEG) should return correct attributes")
    void toAttributesReturnsCorrectAttributesForSOF0() {
      byte[] data = createSOFData(JPEG.SOF0, 8, 320, 320, 3);
      JPEGHeader header = new JPEGHeader(data, JPEG.SOF0);

      assertEquals(1, header.numberOfMarkers());
      assertEquals(1, header.offset(0));
      assertEquals(-1, header.offsetAfterAPP());

      Attributes attrs = header.toAttributes(null);
      assertNotNull(attrs);
      assertEquals(UID.JPEGBaseline8Bit, header.getTransferSyntaxUID());
      assertEquals("YBR_FULL_422", attrs.getString(Tag.PhotometricInterpretation));
      assertEquals(320, attrs.getInt(Tag.Rows, 0));
      assertEquals(320, attrs.getInt(Tag.Columns, 0));
      assertEquals(8, attrs.getInt(Tag.BitsAllocated, 0));
      assertEquals(8, attrs.getInt(Tag.BitsStored, 0));
      assertEquals(7, attrs.getInt(Tag.HighBit, 0));
      assertEquals(0, attrs.getInt(Tag.PixelRepresentation, 0));
      assertEquals("01", attrs.getString(Tag.LossyImageCompression));
      assertEquals(3, attrs.getInt(Tag.SamplesPerPixel, 0));
      assertEquals(0, attrs.getInt(Tag.PlanarConfiguration, 0));
    }

    @Test
    @DisplayName("SOF3 (Lossless JPEG) should return correct attributes")
    void toAttributesReturnsCorrectAttributesForSOF3() {
      byte[] data = createSOFDataWithSOS(JPEG.SOF3, 8, 10, 10, 3, 0);
      JPEGHeader header = new JPEGHeader(data, JPEG.SOF3);

      Attributes attrs = header.toAttributes(null);
      assertNotNull(attrs);
      assertEquals(UID.JPEGLossless, header.getTransferSyntaxUID());
      assertEquals("RGB", attrs.getString(Tag.PhotometricInterpretation));
      assertEquals(10, attrs.getInt(Tag.Rows, 0));
      assertEquals(10, attrs.getInt(Tag.Columns, 0));
      assertEquals(8, attrs.getInt(Tag.BitsAllocated, 0));
      assertEquals(8, attrs.getInt(Tag.BitsStored, 0));
      assertEquals(7, attrs.getInt(Tag.HighBit, 0));
      assertEquals(0, attrs.getInt(Tag.PixelRepresentation, 0));
      assertNull(attrs.getString(Tag.LossyImageCompression));
    }

    @Test
    @DisplayName("SOF3 with SV1 should return JPEGLosslessSV1 transfer syntax")
    void toAttributesReturnsCorrectTransferSyntaxForSOF3SV1() {
      byte[] data = createSOFDataWithSOS(JPEG.SOF3, 8, 10, 10, 3, 1);
      JPEGHeader header = new JPEGHeader(data, JPEG.SOS);

      assertEquals(UID.JPEGLosslessSV1, header.getTransferSyntaxUID());
    }

    @Test
    @DisplayName("SOF55 (JPEG-LS Near Lossless) should return correct attributes")
    void toAttributesReturnsCorrectAttributesForSOF55() {
      byte[] data = createSOFDataWithSOS(JPEG.SOF55, 12, 512, 512, 1, 1);
      JPEGHeader header = new JPEGHeader(data, JPEG.SOF55);

      Attributes attrs = header.toAttributes(null);
      assertNotNull(attrs);
      assertEquals(UID.JPEGLSNearLossless, header.getTransferSyntaxUID());
      assertEquals("MONOCHROME2", attrs.getString(Tag.PhotometricInterpretation));
      assertEquals(512, attrs.getInt(Tag.Rows, 0));
      assertEquals(512, attrs.getInt(Tag.Columns, 0));
      assertEquals(16, attrs.getInt(Tag.BitsAllocated, 0));
      assertEquals(12, attrs.getInt(Tag.BitsStored, 0));
      assertEquals(11, attrs.getInt(Tag.HighBit, 0));
      assertEquals(0, attrs.getInt(Tag.PixelRepresentation, 0));
      assertEquals("01", attrs.getString(Tag.LossyImageCompression));
    }

    @Test
    @DisplayName("SOF55 with lossless mode should return JPEGLSLossless transfer syntax")
    void toAttributesReturnsCorrectTransferSyntaxForSOF55Lossless() {
      byte[] data = createSOFDataWithSOS(JPEG.SOF55, 12, 512, 512, 1, 0);
      JPEGHeader header = new JPEGHeader(data, JPEG.SOS);

      assertEquals(UID.JPEGLSLossless, header.getTransferSyntaxUID());
    }

    @ParameterizedTest(name = "SOF{0} should return transfer syntax {1}")
    @MethodSource("sofTransferSyntaxProvider")
    @DisplayName("Various SOF markers should return correct transfer syntax UIDs")
    void getTransferSyntaxUIDReturnsCorrectUID(int sofMarker, String expectedUID) {
      byte[] data = createSOFData(sofMarker, 8, 100, 100, 1);
      JPEGHeader header = new JPEGHeader(data, sofMarker);

      assertEquals(expectedUID, header.getTransferSyntaxUID());
    }

    static Stream<Arguments> sofTransferSyntaxProvider() {
      return Stream.of(
          Arguments.of(JPEG.SOF0, UID.JPEGBaseline8Bit),
          Arguments.of(JPEG.SOF1, UID.JPEGExtended12Bit),
          Arguments.of(JPEG.SOF2, UID.JPEGFullProgressionNonHierarchical1012));
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesAndErrorHandling {

    @Test
    @DisplayName("Should return null attributes when no SOF marker present")
    void toAttributesReturnsNullForNoSOF() {
      byte[] data = new byte[] {-1, (byte) JPEG.SOS, 0, 1};
      JPEGHeader header = new JPEGHeader(data, JPEG.SOS);

      assertNull(header.toAttributes(null));
      assertNull(header.getTransferSyntaxUID());
    }

    @Test
    @DisplayName("Should handle empty byte array gracefully")
    void shouldHandleEmptyByteArray() {
      byte[] data = new byte[0];
      JPEGHeader header = new JPEGHeader(data, JPEG.SOF0);

      assertEquals(0, header.numberOfMarkers());
      assertNull(header.toAttributes(null));
      assertNull(header.getTransferSyntaxUID());
    }

    @Test
    @DisplayName("Should handle invalid marker data gracefully")
    void shouldHandleInvalidMarkerData() {
      byte[] data = new byte[] {-1, (byte) 0xFF, 0, 1}; // Invalid marker
      JPEGHeader header = new JPEGHeader(data, 0xFF);

      assertNull(header.getTransferSyntaxUID());
    }

    @Test
    @DisplayName("Should preserve existing attributes when provided")
    void shouldPreserveExistingAttributes() {
      byte[] data = createSOFData(JPEG.SOF0, 8, 100, 100, 1);
      JPEGHeader header = new JPEGHeader(data, JPEG.SOF0);

      Attributes existingAttrs = new Attributes();
      existingAttrs.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4");

      Attributes result = header.toAttributes(existingAttrs);

      assertEquals(existingAttrs, result);
      assertEquals("1.2.3.4", result.getString(Tag.StudyInstanceUID));
      assertNotNull(result.getString(Tag.PhotometricInterpretation));
    }
  }

  @Nested
  @DisplayName("Marker Navigation Tests")
  class MarkerNavigationTests {

    @Test
    @DisplayName("Should return correct offset after APP markers")
    void offsetAfterAPPReturnsCorrectOffsetWhenAPPExists() {
      byte[] data =
          new byte[] {
            -1, (byte) JPEG.APP0, 0, 1,
            -1, (byte) JPEG.APP1, 0, 1,
            -1, (byte) JPEG.SOF0, 0, 1
          };
      JPEGHeader header = new JPEGHeader(data, JPEG.SOF0);

      assertEquals(9, header.offsetAfterAPP());
    }

    @Test
    @DisplayName("Should return -1 when no APP markers present")
    void offsetAfterAPPReturnsMinusOneWhenNoAPP() {
      byte[] data = createSOFData(JPEG.SOF0, 8, 100, 100, 1);
      JPEGHeader header = new JPEGHeader(data, JPEG.SOF0);

      assertEquals(-1, header.offsetAfterAPP());
    }

    @Test
    @DisplayName("Should find correct marker offset")
    void offsetOfReturnsCorrectOffset() {
      byte[] data =
          new byte[] {-1, (byte) JPEG.APP0, 0, 2, -1, (byte) JPEG.SOF0, 0, 8, 8, 0, 100, 0, 100, 1};
      JPEGHeader header = new JPEGHeader(data, JPEG.SOF0);

      assertEquals(1, header.offsetOf(JPEG.APP0));
      assertEquals(5, header.offsetOf(JPEG.SOF0));
      assertEquals(-1, header.offsetOf(JPEG.SOS));
    }
  }

  @Nested
  @DisplayName("Bit Depth Tests")
  class BitDepthTests {

    @ParameterizedTest(name = "Precision {0} should result in {1} bits allocated")
    @ValueSource(ints = {1, 8, 12, 16})
    @DisplayName("Various bit depths should be handled correctly")
    void shouldHandleVariousBitDepths(int precision) {
      byte[] data = createSOFData(JPEG.SOF0, precision, 100, 100, 1);
      JPEGHeader header = new JPEGHeader(data, JPEG.SOF0);

      Attributes attrs = header.toAttributes(null);

      assertEquals(precision > 8 ? 16 : 8, attrs.getInt(Tag.BitsAllocated, 0));
      assertEquals(precision, attrs.getInt(Tag.BitsStored, 0));
      assertEquals(precision - 1, attrs.getInt(Tag.HighBit, 0));
    }
  }

  @Nested
  @DisplayName("Photometric Interpretation Tests")
  class PhotometricInterpretationTests {

    @Test
    @DisplayName("Single component should result in MONOCHROME2")
    void singleComponentShouldResultInMonochrome() {
      byte[] data = createSOFData(JPEG.SOF0, 8, 100, 100, 1);
      JPEGHeader header = new JPEGHeader(data, JPEG.SOF0);

      Attributes attrs = header.toAttributes(null);

      assertEquals("MONOCHROME2", attrs.getString(Tag.PhotometricInterpretation));
      assertEquals(1, attrs.getInt(Tag.SamplesPerPixel, 0));
    }

    @Test
    @DisplayName("Three components with SOF0 should result in YBR_FULL_422")
    void threeComponentsWithSOF0ShouldResultInYBR() {
      byte[] data = createSOFData(JPEG.SOF0, 8, 100, 100, 3);
      JPEGHeader header = new JPEGHeader(data, JPEG.SOF0);

      Attributes attrs = header.toAttributes(null);

      assertEquals("YBR_FULL_422", attrs.getString(Tag.PhotometricInterpretation));
      assertEquals(3, attrs.getInt(Tag.SamplesPerPixel, 0));
      assertEquals(0, attrs.getInt(Tag.PlanarConfiguration, 0));
    }

    @Test
    @DisplayName("Three components with SOF3 should result in RGB")
    void threeComponentsWithSOF3ShouldResultInRGB() {
      byte[] data = createSOFDataWithSOS(JPEG.SOF3, 8, 100, 100, 3, 0);
      JPEGHeader header = new JPEGHeader(data, JPEG.SOF3);

      Attributes attrs = header.toAttributes(null);

      assertEquals("RGB", attrs.getString(Tag.PhotometricInterpretation));
    }
  }

  // Helper methods for creating test data
  private byte[] createSOFData(
      int sofMarker, int precision, int height, int width, int components) {
    return new byte[] {
      -1,
      (byte) sofMarker,
      0,
      0,
      (byte) precision,
      (byte) (height >> 8),
      (byte) (height & 0xFF),
      (byte) (width >> 8),
      (byte) (width & 0xFF),
      (byte) components,
      0,
      0,
      0,
      0
    };
  }

  private byte[] createSOFDataWithSOS(
      int sofMarker, int precision, int height, int width, int components, int ss) {
    // Create SOF data first
    byte[] sofData = createSOFData(sofMarker, precision, height, width, components);

    // Create SOS marker structure
    // The ss value must be at position 6 relative to the SOS marker byte
    byte[] sosData =
        new byte[] {
          -1,
          (byte) JPEG.SOS, // Marker
          0,
          6, // Length (6 bytes follow)
          (byte) components, // Number of components
          0, // Start of spectral selection
          63, // End of spectral selection
          (byte) ss // Successive approximation (at offsetSOS + 6)
        };

    // Combine SOF and SOS data
    byte[] combined = new byte[sofData.length + sosData.length];
    System.arraycopy(sofData, 0, combined, 0, sofData.length);
    System.arraycopy(sosData, 0, combined, sofData.length, sosData.length);

    return combined;
  }
}
