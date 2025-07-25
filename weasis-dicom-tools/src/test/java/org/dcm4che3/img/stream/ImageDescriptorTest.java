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

import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.img.util.LutTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.Core.MinMaxLocResult;

class ImageDescriptorTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Should create ImageDescriptor with comprehensive valid attributes")
    void shouldCreateImageDescriptorWithValidAttributes() {
      Attributes attributes = createCompleteAttributes();

      ImageDescriptor descriptor = new ImageDescriptor(attributes);

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
      assertEquals(16, descriptor.getBitsCompressed()); // Default when not specified
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
    @DisplayName("Should create ImageDescriptor with custom bits compressed")
    void shouldCreateImageDescriptorWithBitsCompressed() {
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, 16);
      attributes.setInt(Tag.BitsStored, VR.US, 12);

      ImageDescriptor descriptor = new ImageDescriptor(attributes, 10);

      assertEquals(16, descriptor.getBitsAllocated());
      assertEquals(12, descriptor.getBitsStored());
      assertEquals(10, descriptor.getBitsCompressed());
    }

    @Test
    @DisplayName("Should handle null attributes gracefully")
    void shouldThrowExceptionForNullAttributes() {
      assertThrows(NullPointerException.class, () -> new ImageDescriptor(null));
    }

    @Test
    @DisplayName("Should create ImageDescriptor with minimal attributes")
    void shouldCreateImageDescriptorWithMinimalAttributes() {
      Attributes attributes = new Attributes();

      ImageDescriptor descriptor = new ImageDescriptor(attributes);

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
  }

  @Nested
  @DisplayName("Multiframe Tests")
  class MultiframeTests {

    @Test
    @DisplayName("Should handle multiframe images correctly")
    void shouldHandleMultiframeImages() {
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.Rows, VR.US, 100);
      attributes.setInt(Tag.Columns, VR.US, 100);
      attributes.setInt(Tag.SamplesPerPixel, VR.US, 1);
      attributes.setInt(Tag.BitsAllocated, VR.US, 8);
      attributes.setInt(Tag.NumberOfFrames, VR.IS, 10);

      ImageDescriptor descriptor = new ImageDescriptor(attributes);

      assertTrue(descriptor.isMultiframe());
      assertEquals(10, descriptor.getFrames());
      assertEquals(10000, descriptor.getFrameLength()); // 100*100*1*8/8
      assertEquals(100000, descriptor.getLength()); // frameLength * 10 frames
    }

    @Test
    @DisplayName("Should calculate frame length correctly for different pixel configurations")
    void shouldCalculateFrameLengthCorrectly() {
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.Rows, VR.US, 2);
      attributes.setInt(Tag.Columns, VR.US, 2);
      attributes.setInt(Tag.SamplesPerPixel, VR.US, 3);
      attributes.setInt(Tag.BitsAllocated, VR.US, 8);
      attributes.setInt(Tag.NumberOfFrames, VR.IS, 5);

      ImageDescriptor descriptor = new ImageDescriptor(attributes);

      assertTrue(descriptor.isMultiframe());
      assertEquals(12, descriptor.getFrameLength()); // 2*2*3*8/8 = 12 bytes
      assertEquals(60, descriptor.getLength()); // 12 * 5 frames = 60 bytes
    }
  }

  @Nested
  @DisplayName("Float Pixel Data Tests")
  class FloatPixelDataTests {

    @ParameterizedTest
    @DisplayName("Should identify float pixel data correctly")
    @CsvSource({
      "32, RF, true",
      "32, XA, true",
      "32, RTDOSE, false",
      "64, CT, true",
      "64, RTDOSE, true",
      "16, CT, false",
      "8, MR, false"
    })
    void shouldIdentifyFloatPixelData(int bitsAllocated, String modality, boolean expectedFloat) {
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
      attributes.setString(Tag.Modality, VR.CS, modality);

      ImageDescriptor descriptor = new ImageDescriptor(attributes);

      assertEquals(expectedFloat, descriptor.isFloatPixelData());
    }
  }

  @Nested
  @DisplayName("Photometric Interpretation Tests")
  class PhotometricInterpretationTests {

    @ParameterizedTest
    @DisplayName("Should handle different photometric interpretations")
    @ValueSource(strings = {"MONOCHROME1", "MONOCHROME2", "PALETTE_COLOR", "RGB", "YBR_FULL"})
    void shouldHandlePhotometricInterpretations(String piValue) {
      Attributes attributes = new Attributes();
      attributes.setString(Tag.PhotometricInterpretation, VR.CS, piValue);

      boolean expectedPalette = "PALETTE_COLOR".equals(piValue);
      if (expectedPalette) {
        // Ensure palette color LUT is created for PALETTE_COLOR
        attributes.addAll(LutTestDataBuilder.createCompletePaletteLutAttributes());
      }
      ImageDescriptor descriptor = new ImageDescriptor(attributes);

      assertEquals(
          PhotometricInterpretation.fromString(piValue), descriptor.getPhotometricInterpretation());

      assertEquals(
          expectedPalette,
          descriptor.getPhotometricInterpretation() == PhotometricInterpretation.PALETTE_COLOR);
    }

    @Test
    @DisplayName("Should identify palette color lookup table correctly")
    void shouldIdentifyPaletteColorLookupTable() {
      Attributes attributes1 = LutTestDataBuilder.createCompletePaletteLutAttributes();

      ImageDescriptor descriptor1 = new ImageDescriptor(attributes1);
      assertTrue(descriptor1.hasPaletteColorLookupTable());

      // Test with COLOR pixel presentation
      Attributes attributes2 = LutTestDataBuilder.createCompletePaletteLutAttributes();
      attributes2.setString(Tag.PixelPresentation, VR.CS, "COLOR");

      ImageDescriptor descriptor2 = new ImageDescriptor(attributes2);
      assertTrue(descriptor2.hasPaletteColorLookupTable());

      // Test without either
      Attributes attributes3 = new Attributes();
      attributes3.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");

      ImageDescriptor descriptor3 = new ImageDescriptor(attributes3);
      assertFalse(descriptor3.hasPaletteColorLookupTable());
    }
  }

  @Nested
  @DisplayName("Frame-specific Data Tests")
  class FrameSpecificDataTests {

    @Test
    @DisplayName("Should handle frame-specific min/max values")
    void shouldHandleFrameSpecificMinMaxValues() {
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.NumberOfFrames, VR.IS, 3);

      ImageDescriptor descriptor = new ImageDescriptor(attributes);

      // Initially all frames should have null min/max values
      assertNull(descriptor.getMinMaxPixelValue(0));
      assertNull(descriptor.getMinMaxPixelValue(1));
      assertNull(descriptor.getMinMaxPixelValue(2));

      // Set min/max for frame 1
      MinMaxLocResult minMax = new MinMaxLocResult();
      minMax.minVal = 100.0;
      minMax.maxVal = 200.0;
      descriptor.setMinMaxPixelValue(1, minMax);

      // Verify frame 1 has the set value, others remain null
      assertNull(descriptor.getMinMaxPixelValue(0));
      assertEquals(minMax, descriptor.getMinMaxPixelValue(1));
      assertNull(descriptor.getMinMaxPixelValue(2));
    }

    @Test
    @DisplayName("Should handle invalid frame indices gracefully")
    void shouldHandleInvalidFrameIndices() {
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.NumberOfFrames, VR.IS, 2);

      ImageDescriptor descriptor = new ImageDescriptor(attributes);

      // Test invalid indices
      assertNull(descriptor.getMinMaxPixelValue(-1));
      assertNull(descriptor.getMinMaxPixelValue(2));
      assertNull(descriptor.getMinMaxPixelValue(100));

      // Setting invalid frame indices should not throw exceptions
      MinMaxLocResult minMax = new MinMaxLocResult();
      assertDoesNotThrow(() -> descriptor.setMinMaxPixelValue(-1, minMax));
      assertDoesNotThrow(() -> descriptor.setMinMaxPixelValue(2, minMax));
    }
  }

  @Nested
  @DisplayName("Edge Cases and Validation")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle negative or zero dimensions gracefully")
    void shouldHandleInvalidDimensions() {
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.Rows, VR.US, 0);
      attributes.setInt(Tag.Columns, VR.US, 0);
      attributes.setInt(Tag.NumberOfFrames, VR.IS, -5);

      ImageDescriptor descriptor = new ImageDescriptor(attributes);

      assertEquals(0, descriptor.getRows()); // Negative converted to 0
      assertEquals(0, descriptor.getColumns());
      assertEquals(1, descriptor.getFrames()); // Negative/zero converted to 1
      assertEquals(0, descriptor.getFrameLength());
      assertEquals(0, descriptor.getLength());
    }

    @Test
    @DisplayName("Should handle pixel padding values correctly")
    void shouldHandlePixelPaddingValues() {
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.PixelPaddingValue, VR.SS, -1000);
      attributes.setInt(Tag.PixelPaddingRangeLimit, VR.SS, -500);

      ImageDescriptor descriptor = new ImageDescriptor(attributes);

      assertTrue(descriptor.getPixelPaddingValue().isPresent());
      assertTrue(descriptor.getPixelPaddingRangeLimit().isPresent());
      assertEquals(Integer.valueOf(-1000), descriptor.getPixelPaddingValue().get());
      assertEquals(Integer.valueOf(-500), descriptor.getPixelPaddingRangeLimit().get());
    }

    @Test
    @DisplayName("Should validate bits relationship")
    void shouldValidateBitsRelationship() {
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, 16);
      attributes.setInt(Tag.BitsStored, VR.US, 20); // Invalid: more than allocated
      attributes.setInt(Tag.HighBit, VR.US, 25); // Invalid: higher than stored

      ImageDescriptor descriptor = new ImageDescriptor(attributes);

      assertEquals(16, descriptor.getBitsAllocated());
      assertEquals(16, descriptor.getBitsStored()); // Should be clamped to allocated
      assertEquals(15, descriptor.getHighBit()); // Should be calculated correctly
    }
  }

  @Nested
  @DisplayName("Complex Scenarios")
  class ComplexScenariosTests {

    @Test
    @DisplayName("Should handle color images with multiple samples")
    void shouldHandleColorImages() {
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.Rows, VR.US, 256);
      attributes.setInt(Tag.Columns, VR.US, 256);
      attributes.setInt(Tag.SamplesPerPixel, VR.US, 3);
      attributes.setString(Tag.PhotometricInterpretation, VR.CS, "RGB");
      attributes.setInt(Tag.BitsAllocated, VR.US, 8);
      attributes.setInt(Tag.PlanarConfiguration, VR.US, 0);

      ImageDescriptor descriptor = new ImageDescriptor(attributes);

      assertEquals(3, descriptor.getSamples());
      assertEquals(PhotometricInterpretation.RGB, descriptor.getPhotometricInterpretation());
      assertFalse(descriptor.isBanded()); // PlanarConfiguration = 0
      assertEquals(196608, descriptor.getFrameLength()); // 256*256*3*8/8
    }

    @Test
    @DisplayName("Should handle 16-bit signed images")
    void shouldHandle16BitSignedImages() {
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.Rows, VR.US, 512);
      attributes.setInt(Tag.Columns, VR.US, 512);
      attributes.setInt(Tag.BitsAllocated, VR.US, 16);
      attributes.setInt(Tag.BitsStored, VR.US, 12);
      attributes.setInt(Tag.HighBit, VR.US, 11);
      attributes.setInt(Tag.PixelRepresentation, VR.US, 1);
      attributes.setString(Tag.Modality, VR.CS, "CT");

      ImageDescriptor descriptor = new ImageDescriptor(attributes);

      assertTrue(descriptor.isSigned());
      assertEquals(12, descriptor.getBitsStored());
      assertEquals(11, descriptor.getHighBit());
      assertFalse(descriptor.isFloatPixelData());
      assertEquals("CT", descriptor.getModality());
    }
  }

  @Nested
  @DisplayName("ImageReaderDescriptor Interface Tests")
  class ImageReaderDescriptorTests {

    @Test
    @DisplayName("Should return image descriptor when available")
    void shouldReturnImageDescriptorWhenAvailable() {
      ImageDescriptor expectedDescriptor = new ImageDescriptor(createCompleteAttributes());

      ImageReaderDescriptor readerDescriptor = new TestImageReaderDescriptor(expectedDescriptor);

      ImageDescriptor actualDescriptor = readerDescriptor.getImageDescriptor();
      assertEquals(expectedDescriptor, actualDescriptor);
    }

    @Test
    @DisplayName("Should return null when image descriptor is not available")
    void shouldReturnNullWhenImageDescriptorNotAvailable() {
      ImageReaderDescriptor readerDescriptor = new TestImageReaderDescriptor(null);

      ImageDescriptor descriptor = readerDescriptor.getImageDescriptor();
      assertNull(descriptor);
    }

    @Test
    @DisplayName("Should return Optional with descriptor when available")
    void shouldReturnOptionalWithDescriptorWhenAvailable() {
      ImageDescriptor expectedDescriptor = new ImageDescriptor(createCompleteAttributes());
      ImageReaderDescriptor readerDescriptor = new TestImageReaderDescriptor(expectedDescriptor);

      Optional<ImageDescriptor> optional = readerDescriptor.getImageDescriptorOptional();

      assertTrue(optional.isPresent());
      assertEquals(expectedDescriptor, optional.get());
    }

    @Test
    @DisplayName("Should return empty Optional when descriptor is not available")
    void shouldReturnEmptyOptionalWhenDescriptorNotAvailable() {
      ImageReaderDescriptor readerDescriptor = new TestImageReaderDescriptor(null);

      Optional<ImageDescriptor> optional = readerDescriptor.getImageDescriptorOptional();

      assertFalse(optional.isPresent());
      assertTrue(optional.isEmpty());
    }

    @Test
    @DisplayName("Should return true when image descriptor is available")
    void shouldReturnTrueWhenImageDescriptorAvailable() {
      ImageDescriptor descriptor = new ImageDescriptor(createCompleteAttributes());
      ImageReaderDescriptor readerDescriptor = new TestImageReaderDescriptor(descriptor);

      boolean hasDescriptor = readerDescriptor.hasImageDescriptor();

      assertTrue(hasDescriptor);
    }

    @Test
    @DisplayName("Should return false when image descriptor is not available")
    void shouldReturnFalseWhenImageDescriptorNotAvailable() {
      ImageReaderDescriptor readerDescriptor = new TestImageReaderDescriptor(null);

      boolean hasDescriptor = readerDescriptor.hasImageDescriptor();

      assertFalse(hasDescriptor);
    }

    @Test
    @DisplayName("Should handle consistent behavior across all methods")
    void shouldHandleConsistentBehaviorAcrossAllMethods() {
      // Test with available descriptor
      ImageDescriptor expectedDescriptor = new ImageDescriptor(createCompleteAttributes());
      ImageReaderDescriptor availableReaderDescriptor =
          new TestImageReaderDescriptor(expectedDescriptor);

      assertEquals(expectedDescriptor, availableReaderDescriptor.getImageDescriptor());
      assertTrue(availableReaderDescriptor.getImageDescriptorOptional().isPresent());
      assertEquals(
          expectedDescriptor, availableReaderDescriptor.getImageDescriptorOptional().get());
      assertTrue(availableReaderDescriptor.hasImageDescriptor());

      // Test with unavailable descriptor
      ImageReaderDescriptor unavailableReaderDescriptor = new TestImageReaderDescriptor(null);

      assertNull(unavailableReaderDescriptor.getImageDescriptor());
      assertFalse(unavailableReaderDescriptor.getImageDescriptorOptional().isPresent());
      assertFalse(unavailableReaderDescriptor.hasImageDescriptor());
    }

    @Test
    @DisplayName("Should handle default method implementations")
    void shouldHandleDefaultMethodImplementations() {
      // Create a minimal implementation that only overrides getImageDescriptor
      ImageReaderDescriptor minimalImplementation =
          new ImageReaderDescriptor() {
            private final ImageDescriptor descriptor =
                new ImageDescriptor(createCompleteAttributes());

            @Override
            public ImageDescriptor getImageDescriptor() {
              return descriptor;
            }
          };

      // Test that default methods work correctly
      assertTrue(minimalImplementation.hasImageDescriptor());
      assertTrue(minimalImplementation.getImageDescriptorOptional().isPresent());
      assertEquals(
          minimalImplementation.getImageDescriptor(),
          minimalImplementation.getImageDescriptorOptional().get());
    }

    @Test
    @DisplayName("Should handle null-safe operations")
    void shouldHandleNullSafeOperations() {
      ImageReaderDescriptor nullReaderDescriptor =
          new ImageReaderDescriptor() {
            @Override
            public ImageDescriptor getImageDescriptor() {
              return null;
            }
          };

      // Verify no exceptions are thrown and behavior is consistent
      assertDoesNotThrow(() -> nullReaderDescriptor.getImageDescriptor());
      assertDoesNotThrow(() -> nullReaderDescriptor.getImageDescriptorOptional());
      assertDoesNotThrow(() -> nullReaderDescriptor.hasImageDescriptor());

      assertNull(nullReaderDescriptor.getImageDescriptor());
      assertFalse(nullReaderDescriptor.getImageDescriptorOptional().isPresent());
      assertFalse(nullReaderDescriptor.hasImageDescriptor());
    }

    /** Test implementation of ImageReaderDescriptor for testing purposes. */
    private static class TestImageReaderDescriptor implements ImageReaderDescriptor {
      private final ImageDescriptor descriptor;

      public TestImageReaderDescriptor(ImageDescriptor descriptor) {
        this.descriptor = descriptor;
      }

      @Override
      public ImageDescriptor getImageDescriptor() {
        return descriptor;
      }
    }
  }

  // Helper method to create comprehensive test attributes
  private Attributes createCompleteAttributes() {
    Attributes attributes = LutTestDataBuilder.createCompletePaletteLutAttributes();

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
