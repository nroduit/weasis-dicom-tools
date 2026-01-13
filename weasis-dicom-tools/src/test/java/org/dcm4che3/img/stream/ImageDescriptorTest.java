/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.img.util.LutTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.Core.MinMaxLocResult;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ImageDescriptorTest {

  @Nested
  @DisplayName("Constructor Tests")
  class Constructor_Tests {

    @Test
    void should_create_image_descriptor_with_comprehensive_valid_attributes() {
      var attributes = createCompleteAttributes();

      var descriptor = new ImageDescriptor(attributes);

      // Basic image properties
      assertEquals(512, descriptor.getRows());
      assertEquals(512, descriptor.getColumns());
      assertEquals(1, descriptor.getFrames());
      assertEquals(1, descriptor.getSamples());
      assertFalse(descriptor.isMultiframe());

      // Pixel representation
      assertEquals(
          PhotometricInterpretation.MONOCHROME2, descriptor.getPhotometricInterpretation());
      assertEquals(1, descriptor.getPlanarConfiguration());
      assertEquals(16, descriptor.getBitsAllocated());
      assertEquals(16, descriptor.getBitsStored());
      assertEquals(16, descriptor.getBitsCompressed());
      assertEquals(15, descriptor.getHighBit());
      assertEquals(1, descriptor.getPixelRepresentation());
      assertTrue(descriptor.isSigned());
      assertTrue(descriptor.isBanded());

      // DICOM metadata
      assertEquals("1.2.840.10008.5.1.4.1.1.2", descriptor.getSopClassUID());
      assertEquals("1.2.3.4.5.6.7.8.9", descriptor.getSeriesInstanceUID());
      assertEquals("CT", descriptor.getModality());
      assertEquals("STATION1", descriptor.getStationName());
      assertNotNull(descriptor.getAnatomicRegion());
      assertEquals("HEAD", descriptor.getAnatomicRegion().getRegion().getLegacyCode());

      // Pixel padding and presentation
      assertEquals("COLOR", descriptor.getPixelPresentation());
      assertTrue(descriptor.hasPaletteColorLookupTable());
      assertEquals(Integer.valueOf(-2000), descriptor.getPixelPaddingValue().orElse(null));
      assertEquals(Integer.valueOf(-1000), descriptor.getPixelPaddingRangeLimit().orElse(null));
      assertEquals("IDENTITY", descriptor.getPresentationLUTShape());

      // LUT modules
      assertNotNull(descriptor.getModalityLUT());
      assertNotNull(descriptor.getVoiLUT());

      // Overlay data
      assertNotNull(descriptor.getEmbeddedOverlay());
      assertNotNull(descriptor.getOverlayData());
      assertFalse(descriptor.isMultiframeWithEmbeddedOverlays());
    }

    @Test
    void should_create_image_descriptor_with_custom_bits_compressed() {
      var attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, 16);
      attributes.setInt(Tag.BitsStored, VR.US, 12);

      var descriptor = new ImageDescriptor(attributes, 10);

      assertEquals(16, descriptor.getBitsAllocated());
      assertEquals(12, descriptor.getBitsStored());
      assertEquals(10, descriptor.getBitsCompressed());
    }

    @Test
    void should_throw_exception_for_null_attributes() {
      assertThrows(NullPointerException.class, () -> new ImageDescriptor(null));
    }

    @Test
    void should_create_image_descriptor_with_minimal_attributes() {
      var attributes = new Attributes();

      var descriptor = new ImageDescriptor(attributes);

      // Verify defaults
      assertEquals(0, descriptor.getRows());
      assertEquals(0, descriptor.getColumns());
      assertEquals(1, descriptor.getFrames());
      assertEquals(1, descriptor.getSamples());
      assertEquals(
          PhotometricInterpretation.MONOCHROME2, descriptor.getPhotometricInterpretation());
      assertEquals(0, descriptor.getPlanarConfiguration());
      assertEquals(8, descriptor.getBitsAllocated());
      assertEquals(8, descriptor.getBitsStored());
      assertEquals(7, descriptor.getHighBit());
      assertEquals(0, descriptor.getPixelRepresentation());
      assertFalse(descriptor.isSigned());
      assertFalse(descriptor.isBanded());
      assertNull(descriptor.getSopClassUID());
      assertNull(descriptor.getAnatomicRegion());
      assertNull(descriptor.getModality());
      assertFalse(descriptor.getPixelPaddingValue().isPresent());
      assertFalse(descriptor.getPixelPaddingRangeLimit().isPresent());
      assertNull(descriptor.getPresentationLUTShape());
      assertNull(descriptor.getPixelPresentation());
    }

    @ParameterizedTest
    @CsvSource({
      "8, 8, 7, 0, false, false",
      "16, 16, 15, 0, false, false",
      "16, 12, 11, 1, true, false",
      "32, 32, 31, 1, true, true"
    })
    void should_handle_various_bit_configurations(
        int bitsAllocated,
        int bitsStored,
        int expectedHighBit,
        int pixelRepresentation,
        boolean expectedSigned,
        boolean expectedBanded) {
      var attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
      attributes.setInt(Tag.BitsStored, VR.US, bitsStored);
      attributes.setInt(Tag.PixelRepresentation, VR.US, pixelRepresentation);
      attributes.setInt(Tag.PlanarConfiguration, VR.US, expectedBanded ? 1 : 0);

      var descriptor = new ImageDescriptor(attributes);

      assertEquals(bitsAllocated, descriptor.getBitsAllocated());
      assertEquals(bitsStored, descriptor.getBitsStored());
      assertEquals(expectedHighBit, descriptor.getHighBit());
      assertEquals(expectedSigned, descriptor.isSigned());
      assertEquals(expectedBanded, descriptor.isBanded());
    }
  }

  @Nested
  @DisplayName("Multiframe Tests")
  class Multiframe_Tests {

    @Test
    void should_handle_multiframe_images_correctly() {
      var attributes = createBasicImageAttributes(100, 100, 1, 8, 10);

      var descriptor = new ImageDescriptor(attributes);

      assertTrue(descriptor.isMultiframe());
      assertEquals(10, descriptor.getFrames());
      assertEquals(10000, descriptor.getFrameLength()); // 100*100*1*8/8
      assertEquals(100000, descriptor.getLength()); // frameLength * 10 frames
    }

    @ParameterizedTest
    @CsvSource({
      "2, 2, 3, 8, 5, 12, 60",
      "256, 256, 1, 16, 1, 131072, 131072",
      "512, 512, 3, 8, 4, 786432, 3145728"
    })
    void should_calculate_frame_length_correctly(
        int rows,
        int columns,
        int samples,
        int bitsAllocated,
        int frames,
        int expectedFrameLength,
        int expectedTotalLength) {
      var attributes = createBasicImageAttributes(rows, columns, samples, bitsAllocated, frames);

      var descriptor = new ImageDescriptor(attributes);

      assertEquals(expectedFrameLength, descriptor.getFrameLength());
      assertEquals(expectedTotalLength, descriptor.getLength());
      assertEquals(frames > 1, descriptor.isMultiframe());
    }
  }

  @Nested
  @DisplayName("Float Pixel Data Tests")
  class Float_Pixel_Data_Tests {

    @ParameterizedTest
    @CsvSource({
      "32, RF, true",
      "32, XA, true",
      "32, RTDOSE, false",
      "64, CT, true",
      "64, RTDOSE, true",
      "16, CT, false",
      "8, MR, false"
    })
    void should_identify_float_pixel_data_correctly(
        int bitsAllocated, String modality, boolean expectedFloat) {
      var attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
      attributes.setString(Tag.Modality, VR.CS, modality);

      var descriptor = new ImageDescriptor(attributes);

      assertEquals(expectedFloat, descriptor.isFloatPixelData());
    }

    static Stream<Arguments> floatPixelDataProvider() {
      return Stream.of(
          Arguments.of(32, "RF", true, "32-bit RF should be float"),
          Arguments.of(32, "XA", true, "32-bit XA should be float"),
          Arguments.of(32, "RTDOSE", false, "32-bit RTDOSE should not be float"),
          Arguments.of(64, "CT", true, "64-bit should always be float"),
          Arguments.of(64, "RTDOSE", true, "64-bit RTDOSE should be float"),
          Arguments.of(16, "CT", false, "16-bit should not be float"),
          Arguments.of(8, "MR", false, "8-bit should not be float"));
    }

    @ParameterizedTest
    @MethodSource("floatPixelDataProvider")
    void should_identify_float_pixel_data_with_descriptions(
        int bitsAllocated, String modality, boolean expectedFloat, String description) {
      var attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
      attributes.setString(Tag.Modality, VR.CS, modality);

      var descriptor = new ImageDescriptor(attributes);

      assertEquals(expectedFloat, descriptor.isFloatPixelData(), description);
    }
  }

  @Nested
  @DisplayName("Photometric Interpretation Tests")
  class Photometric_Interpretation_Tests {

    @ParameterizedTest
    @ValueSource(strings = {"MONOCHROME1", "MONOCHROME2", "PALETTE_COLOR", "RGB", "YBR_FULL"})
    void should_handle_different_photometric_interpretations(String piValue) {
      var attributes = new Attributes();
      attributes.setString(Tag.PhotometricInterpretation, VR.CS, piValue);

      boolean expectedPalette = "PALETTE_COLOR".equals(piValue);
      if (expectedPalette) {
        // Use existing LUT test data instead of mocking
        attributes.addAll(LutTestDataBuilder.createCompletePaletteLutAttributes());
      }

      var descriptor = new ImageDescriptor(attributes);

      assertEquals(
          PhotometricInterpretation.fromString(piValue), descriptor.getPhotometricInterpretation());
      assertEquals(
          expectedPalette,
          descriptor.getPhotometricInterpretation() == PhotometricInterpretation.PALETTE_COLOR);
    }

    @Test
    void should_identify_palette_color_lookup_table_for_palette_color() {
      var attributes = LutTestDataBuilder.createCompletePaletteLutAttributes();

      var descriptor = new ImageDescriptor(attributes);

      assertTrue(descriptor.hasPaletteColorLookupTable());
      assertNotNull(descriptor.getPaletteColorLookupTable());
    }

    @Test
    void should_identify_palette_color_lookup_table_for_monochrome_with_color_presentation() {
      var attributes = new Attributes();
      attributes.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
      attributes.setString(Tag.PixelPresentation, VR.CS, "COLOR");

      var descriptor = new ImageDescriptor(attributes);

      assertTrue(descriptor.hasPaletteColorLookupTable());
    }

    @Test
    void should_not_identify_palette_color_lookup_table_for_regular_monochrome() {
      var attributes = new Attributes();
      attributes.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");

      var descriptor = new ImageDescriptor(attributes);

      assertFalse(descriptor.hasPaletteColorLookupTable());
      assertNull(descriptor.getPaletteColorLookupTable());
    }
  }

  @Nested
  @DisplayName("Frame Specific Data Tests")
  class Frame_Specific_Data_Tests {

    @Test
    void should_handle_frame_specific_min_max_values() {
      var attributes = new Attributes();
      attributes.setInt(Tag.NumberOfFrames, VR.IS, 3);

      var descriptor = new ImageDescriptor(attributes);

      // Initially all frames should have null min/max values
      assertNull(descriptor.getMinMaxPixelValue(0));
      assertNull(descriptor.getMinMaxPixelValue(1));
      assertNull(descriptor.getMinMaxPixelValue(2));

      // Set min/max for frame 1
      var minMax = createMinMaxResult(100.0, 200.0);
      descriptor.setMinMaxPixelValue(1, minMax);

      // Verify frame 1 has the set value, others remain null
      assertNull(descriptor.getMinMaxPixelValue(0));
      assertEquals(minMax, descriptor.getMinMaxPixelValue(1));
      assertNull(descriptor.getMinMaxPixelValue(2));
    }

    @Test
    void should_handle_invalid_frame_indices_gracefully() {
      var attributes = new Attributes();
      attributes.setInt(Tag.NumberOfFrames, VR.IS, 2);

      var descriptor = new ImageDescriptor(attributes);

      // Test invalid indices return null
      assertNull(descriptor.getMinMaxPixelValue(-1));
      assertNull(descriptor.getMinMaxPixelValue(2));
      assertNull(descriptor.getMinMaxPixelValue(100));

      // Setting invalid frame indices should not throw exceptions
      var minMax = createMinMaxResult(50.0, 150.0);
      assertDoesNotThrow(() -> descriptor.setMinMaxPixelValue(-1, minMax));
      assertDoesNotThrow(() -> descriptor.setMinMaxPixelValue(2, minMax));
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, -1, 0, 5, 10})
    void should_validate_frame_indices_correctly(int frameIndex) {
      var attributes = new Attributes();
      attributes.setInt(Tag.NumberOfFrames, VR.IS, 5); // Valid indices: 0-4

      var descriptor = new ImageDescriptor(attributes);

      boolean isValidIndex = frameIndex >= 0 && frameIndex < 5;
      var minMax = createMinMaxResult(10.0, 90.0);

      if (isValidIndex) {
        // Valid indices should work
        assertDoesNotThrow(() -> descriptor.setMinMaxPixelValue(frameIndex, minMax));
        descriptor.setMinMaxPixelValue(frameIndex, minMax);
        assertEquals(minMax, descriptor.getMinMaxPixelValue(frameIndex));
      } else {
        // Invalid indices should be handled gracefully
        assertDoesNotThrow(() -> descriptor.setMinMaxPixelValue(frameIndex, minMax));
        assertNull(descriptor.getMinMaxPixelValue(frameIndex));
      }
    }
  }

  @Nested
  @DisplayName("Edge Cases and Validation")
  class Edge_Cases_Tests {

    @Test
    void should_handle_negative_or_zero_dimensions_gracefully() {
      var attributes = new Attributes();
      attributes.setInt(Tag.Rows, VR.US, 0);
      attributes.setInt(Tag.Columns, VR.US, 0);
      attributes.setInt(Tag.NumberOfFrames, VR.IS, -5);

      var descriptor = new ImageDescriptor(attributes);

      assertEquals(0, descriptor.getRows());
      assertEquals(0, descriptor.getColumns());
      assertEquals(1, descriptor.getFrames()); // Negative/zero converted to 1
      assertEquals(0, descriptor.getFrameLength());
      assertEquals(0, descriptor.getLength());
    }

    @Test
    void should_handle_pixel_padding_values_correctly() {
      var attributes = new Attributes();
      attributes.setInt(Tag.PixelPaddingValue, VR.SS, -1000);
      attributes.setInt(Tag.PixelPaddingRangeLimit, VR.SS, -500);

      var descriptor = new ImageDescriptor(attributes);

      assertTrue(descriptor.getPixelPaddingValue().isPresent());
      assertTrue(descriptor.getPixelPaddingRangeLimit().isPresent());
      assertEquals(-1000, descriptor.getPixelPaddingValue().get());
      assertEquals(-500, descriptor.getPixelPaddingRangeLimit().get());
    }

    @ParameterizedTest
    @CsvSource({
      "16, 20, 25, 16, 15", // BitsStored > BitsAllocated, HighBit > BitsStored
      "8, 12, 15, 8, 7", // BitsStored > BitsAllocated, HighBit > BitsStored
      "32, 24, 20, 24, 20", // Valid case within bounds
      "16, 16, 16, 16, 15" // HighBit = BitsStored (should be clamped)
    })
    void should_validate_bits_relationship(
        int bitsAllocated,
        int bitsStored,
        int highBit,
        int expectedBitsStored,
        int expectedHighBit) {
      var attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
      attributes.setInt(Tag.BitsStored, VR.US, bitsStored);
      attributes.setInt(Tag.HighBit, VR.US, highBit);

      var descriptor = new ImageDescriptor(attributes);

      assertEquals(bitsAllocated, descriptor.getBitsAllocated());
      assertEquals(expectedBitsStored, descriptor.getBitsStored());
      assertEquals(expectedHighBit, descriptor.getHighBit());
    }
  }

  @Nested
  @DisplayName("Complex Scenarios")
  class Complex_Scenarios_Tests {

    @Test
    void should_handle_color_images_with_multiple_samples() {
      var attributes = createColorImageAttributes();

      var descriptor = new ImageDescriptor(attributes);

      assertEquals(3, descriptor.getSamples());
      assertEquals(PhotometricInterpretation.RGB, descriptor.getPhotometricInterpretation());
      assertFalse(descriptor.isBanded()); // PlanarConfiguration = 0
      assertEquals(196608, descriptor.getFrameLength()); // 256*256*3*8/8
      assertFalse(descriptor.isFloatPixelData());
    }

    @Test
    void should_handle_16_bit_signed_images() {
      var attributes = create16BitSignedImageAttributes();

      var descriptor = new ImageDescriptor(attributes);

      assertTrue(descriptor.isSigned());
      assertEquals(12, descriptor.getBitsStored());
      assertEquals(11, descriptor.getHighBit());
      assertFalse(descriptor.isFloatPixelData());
      assertEquals("CT", descriptor.getModality());
      assertEquals(524288, descriptor.getFrameLength()); // 512*512*1*16/8
    }

    static Stream<Arguments> complexScenarioProvider() {
      return Stream.of(
          Arguments.of(
              "RGB Color Image",
              createColorImageAttributes(),
              PhotometricInterpretation.RGB,
              3,
              false,
              196608),
          Arguments.of(
              "16-bit Signed CT",
              create16BitSignedImageAttributes(),
              PhotometricInterpretation.MONOCHROME2,
              1,
              true,
              524288),
          Arguments.of(
              "Palette Color",
              createPaletteColorImageAttributes(),
              PhotometricInterpretation.PALETTE_COLOR,
              1,
              false,
              65536));
    }

    @ParameterizedTest
    @MethodSource("complexScenarioProvider")
    void should_handle_complex_image_scenarios(
        String scenarioName,
        Attributes attributes,
        PhotometricInterpretation expectedPI,
        int expectedSamples,
        boolean expectedSigned,
        int expectedFrameLength) {
      var descriptor = new ImageDescriptor(attributes);

      assertEquals(expectedPI, descriptor.getPhotometricInterpretation(), scenarioName);
      assertEquals(expectedSamples, descriptor.getSamples(), scenarioName);
      assertEquals(expectedSigned, descriptor.isSigned(), scenarioName);
      assertEquals(expectedFrameLength, descriptor.getFrameLength(), scenarioName);
    }
  }

  @Nested
  @DisplayName("Image Reader Descriptor Interface Tests")
  class Image_Reader_Descriptor_Tests {

    @Test
    void should_return_image_descriptor_when_available() {
      var expectedDescriptor = new ImageDescriptor(createCompleteAttributes());
      var readerDescriptor = new TestImageReaderDescriptor(expectedDescriptor);

      var actualDescriptor = readerDescriptor.getImageDescriptor();

      assertEquals(expectedDescriptor, actualDescriptor);
    }

    @Test
    void should_return_null_when_image_descriptor_not_available() {
      var readerDescriptor = new TestImageReaderDescriptor(null);

      var descriptor = readerDescriptor.getImageDescriptor();

      assertNull(descriptor);
    }

    @Test
    void should_return_optional_with_descriptor_when_available() {
      var expectedDescriptor = new ImageDescriptor(createCompleteAttributes());
      var readerDescriptor = new TestImageReaderDescriptor(expectedDescriptor);

      var optional = readerDescriptor.getImageDescriptorOptional();

      assertTrue(optional.isPresent());
      assertEquals(expectedDescriptor, optional.get());
    }

    @Test
    void should_return_empty_optional_when_descriptor_not_available() {
      var readerDescriptor = new TestImageReaderDescriptor(null);
      var optional = readerDescriptor.getImageDescriptorOptional();
      assertFalse(optional.isPresent());
    }

    @ParameterizedTest
    @CsvSource({
      "true, true, true", // Has descriptor
      "false, false, false" // No descriptor
    })
    void should_provide_consistent_behavior_across_methods(
        boolean hasDescriptor, boolean expectedHasDescriptor, boolean expectedOptionalPresent) {
      var descriptor = hasDescriptor ? new ImageDescriptor(createCompleteAttributes()) : null;
      var readerDescriptor = new TestImageReaderDescriptor(descriptor);

      assertEquals(expectedHasDescriptor, readerDescriptor.hasImageDescriptor());
      assertEquals(
          expectedOptionalPresent, readerDescriptor.getImageDescriptorOptional().isPresent());

      if (hasDescriptor) {
        assertEquals(descriptor, readerDescriptor.getImageDescriptor());
        assertEquals(descriptor, readerDescriptor.getImageDescriptorOptional().get());
      } else {
        assertNull(readerDescriptor.getImageDescriptor());
      }
    }

    @Test
    void should_handle_default_method_implementations() {
      var minimalImplementation =
          new ImageReaderDescriptor() {
            private final ImageDescriptor descriptor =
                new ImageDescriptor(createCompleteAttributes());

            @Override
            public ImageDescriptor getImageDescriptor() {
              return descriptor;
            }
          };

      assertTrue(minimalImplementation.hasImageDescriptor());
      assertTrue(minimalImplementation.getImageDescriptorOptional().isPresent());
      assertEquals(
          minimalImplementation.getImageDescriptor(),
          minimalImplementation.getImageDescriptorOptional().get());
    }

    @Test
    void should_handle_null_safe_operations() {
      var nullReaderDescriptor =
          new ImageReaderDescriptor() {
            @Override
            public ImageDescriptor getImageDescriptor() {
              return null;
            }
          };

      assertAll(
          "Null-safe operations",
          () -> assertDoesNotThrow(nullReaderDescriptor::getImageDescriptor),
          () -> assertDoesNotThrow(nullReaderDescriptor::getImageDescriptorOptional),
          () -> assertDoesNotThrow(nullReaderDescriptor::hasImageDescriptor),
          () -> assertNull(nullReaderDescriptor.getImageDescriptor()),
          () -> assertFalse(nullReaderDescriptor.getImageDescriptorOptional().isPresent()),
          () -> assertFalse(nullReaderDescriptor.hasImageDescriptor()));
    }

    /** Test implementation of ImageReaderDescriptor for testing purposes. */
    private static final class TestImageReaderDescriptor implements ImageReaderDescriptor {
      private final ImageDescriptor descriptor;

      TestImageReaderDescriptor(ImageDescriptor descriptor) {
        this.descriptor = descriptor;
      }

      @Override
      public ImageDescriptor getImageDescriptor() {
        return descriptor;
      }
    }
  }

  // Helper methods for creating test data structures

  private static Attributes createBasicImageAttributes(
      int rows, int columns, int samples, int bitsAllocated, int frames) {
    var attributes = new Attributes();
    attributes.setInt(Tag.Rows, VR.US, rows);
    attributes.setInt(Tag.Columns, VR.US, columns);
    attributes.setInt(Tag.SamplesPerPixel, VR.US, samples);
    attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
    attributes.setInt(Tag.NumberOfFrames, VR.IS, frames);
    return attributes;
  }

  private static Attributes createColorImageAttributes() {
    var attributes = new Attributes();
    attributes.setInt(Tag.Rows, VR.US, 256);
    attributes.setInt(Tag.Columns, VR.US, 256);
    attributes.setInt(Tag.SamplesPerPixel, VR.US, 3);
    attributes.setString(Tag.PhotometricInterpretation, VR.CS, "RGB");
    attributes.setInt(Tag.BitsAllocated, VR.US, 8);
    attributes.setInt(Tag.PlanarConfiguration, VR.US, 0);
    return attributes;
  }

  private static Attributes create16BitSignedImageAttributes() {
    var attributes = new Attributes();
    attributes.setInt(Tag.Rows, VR.US, 512);
    attributes.setInt(Tag.Columns, VR.US, 512);
    attributes.setInt(Tag.BitsAllocated, VR.US, 16);
    attributes.setInt(Tag.BitsStored, VR.US, 12);
    attributes.setInt(Tag.HighBit, VR.US, 11);
    attributes.setInt(Tag.PixelRepresentation, VR.US, 1);
    attributes.setString(Tag.Modality, VR.CS, "CT");
    return attributes;
  }

  private static Attributes createPaletteColorImageAttributes() {
    var attributes = LutTestDataBuilder.createCompletePaletteLutAttributes();
    attributes.setInt(Tag.Rows, VR.US, 256);
    attributes.setInt(Tag.Columns, VR.US, 256);
    attributes.setInt(Tag.SamplesPerPixel, VR.US, 1);
    attributes.setInt(Tag.BitsAllocated, VR.US, 8);
    return attributes;
  }

  private static MinMaxLocResult createMinMaxResult(double minVal, double maxVal) {
    var result = new MinMaxLocResult();
    result.minVal = minVal;
    result.maxVal = maxVal;
    return result;
  }

  private Attributes createCompleteAttributes() {
    var attributes = LutTestDataBuilder.createCompletePaletteLutAttributes();

    // Basic dimensions
    attributes.setInt(Tag.Rows, VR.US, 512);
    attributes.setInt(Tag.Columns, VR.US, 512);
    attributes.setInt(Tag.NumberOfFrames, VR.IS, 1);

    // Pixel representation
    attributes.setInt(Tag.SamplesPerPixel, VR.US, 1);
    attributes.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
    attributes.setInt(Tag.PlanarConfiguration, VR.US, 1);
    attributes.setInt(Tag.BitsAllocated, VR.US, 16);
    attributes.setInt(Tag.BitsStored, VR.US, 16);
    attributes.setInt(Tag.HighBit, VR.US, 15);
    attributes.setInt(Tag.PixelRepresentation, VR.US, 1);

    // DICOM metadata
    attributes.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
    attributes.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9");
    attributes.setString(Tag.Modality, VR.CS, "CT");
    attributes.setString(Tag.StationName, VR.SH, "STATION1");
    attributes.setString(Tag.BodyPartExamined, VR.CS, "HEAD");

    // Presentation and padding
    attributes.setString(Tag.PixelPresentation, VR.CS, "COLOR");
    attributes.setInt(Tag.PixelPaddingValue, VR.SS, -2000);
    attributes.setInt(Tag.PixelPaddingRangeLimit, VR.SS, -1000);
    attributes.setString(Tag.PresentationLUTShape, VR.CS, "IDENTITY");

    return attributes;
  }
}
