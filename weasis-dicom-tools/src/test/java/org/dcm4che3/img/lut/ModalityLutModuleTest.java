/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.lut;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.util.LutTestDataBuilder;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ModalityLutModuleTest {

  private static final double PRECISION = 1e-10;

  @Test
  void should_initialize_with_empty_attributes() {
    final var attributes = new Attributes();
    final var module = new ModalityLutModule(attributes);

    assertAll(
        "Empty initialization",
        () -> assertFalse(module.getRescaleSlope().isPresent()),
        () -> assertFalse(module.getRescaleIntercept().isPresent()),
        () -> assertFalse(module.getRescaleType().isPresent()),
        () -> assertFalse(module.getLutType().isPresent()),
        () -> assertFalse(module.getLutExplanation().isPresent()),
        () -> assertFalse(module.getLut().isPresent()),
        () -> assertFalse(module.isOverlayBitMaskApplied()));
  }

  @Test
  void should_throw_exception_when_attributes_are_null() {
    final var exception =
        assertThrows(NullPointerException.class, () -> new ModalityLutModule(null));

    assertEquals("DICOM attributes cannot be null", exception.getMessage());
  }

  @Test
  void should_initialize_rescale_values_correctly() {
    final var attributes = createAttributesWithRescale(2.5, -1024.0, "HU");
    final var module = new ModalityLutModule(attributes);

    assertAll(
        "Rescale values",
        () -> assertEquals(2.5, module.getRescaleSlope().getAsDouble()),
        () -> assertEquals(-1024.0, module.getRescaleIntercept().getAsDouble()),
        () -> assertEquals("HU", module.getRescaleType().get()));
  }

  @Nested
  class Special_Modality_Handling {

    @ParameterizedTest
    @ValueSource(strings = {"XA", "XRF"})
    void should_apply_rescale_values_for_special_modalities_without_restricted_pixel_intensity(
        final String modality) {
      final var attributes = createAttributesWithRescale(2.0, 1.0, "US");
      attributes.setString(Tag.Modality, VR.LO, modality);

      final var module = new ModalityLutModule(attributes);

      assertAll(
          "Special modality rescale values",
          () -> assertEquals(2.0, module.getRescaleSlope().getAsDouble()),
          () -> assertEquals(1.0, module.getRescaleIntercept().getAsDouble()));
    }

    @ParameterizedTest
    @MethodSource("specialModalitiesWithRestrictedPixelIntensity")
    void should_ignore_lut_for_special_modalities_with_restricted_pixel_intensity(
        final String modality, final String pixelIntensity) {
      final var attributes = createAttributesWithRescale(2.0, 1.0, "US");
      attributes.setString(Tag.Modality, VR.LO, modality);
      attributes.setString(Tag.PixelIntensityRelationship, VR.CS, pixelIntensity);

      // Add LUT sequence that should be ignored
      final var lutAttributes = LutTestDataBuilder.createLinearLut8Bit();
      final var sequence = attributes.newSequence(Tag.ModalityLUTSequence, 1);
      sequence.add(lutAttributes);

      final var module = new ModalityLutModule(attributes);

      assertAll(
          "LUT should be ignored",
          () -> assertEquals(2.0, module.getRescaleSlope().getAsDouble()),
          () -> assertEquals(1.0, module.getRescaleIntercept().getAsDouble()),
          () -> assertTrue(module.getLutExplanation().isEmpty()),
          () -> assertTrue(module.getLutType().isEmpty()));
    }

    @Test
    void should_apply_lut_for_non_special_modalities() {
      final var attributes = createAttributesWithRescale(2.0, 1.0, "US");
      attributes.setString(Tag.Modality, VR.LO, "PT");

      final var lutAttributes = LutTestDataBuilder.createLinearLut8Bit();
      lutAttributes.setString(Tag.ModalityLUTType, VR.LO, "MGML");
      final var sequence = attributes.newSequence(Tag.ModalityLUTSequence, 1);
      sequence.add(lutAttributes);

      final var module = new ModalityLutModule(attributes);

      assertAll(
          "LUT should be applied",
          () -> assertEquals("Linear 8-bit mapping", module.getLutExplanation().get()),
          () -> assertEquals("MGML", module.getLutType().get()),
          () -> assertTrue(module.getLut().isPresent()));
    }

    private static Stream<Arguments> specialModalitiesWithRestrictedPixelIntensity() {
      return Stream.of(
          Arguments.of("XA", "LOG"),
          Arguments.of("XA", "DISP"),
          Arguments.of("XRF", "LOG"),
          Arguments.of("XRF", "DISP"));
    }
  }

  @Nested
  class Overlay_Bit_Mask_Adaptation {

    @Test
    void should_set_defaults_when_no_rescale_slope_exists() {
      final var originalModule = new ModalityLutModule(new Attributes());
      final var adaptedModule = originalModule.withOverlayBitMask(2);

      assertAll(
          "Original module unchanged",
          () -> assertFalse(originalModule.getRescaleSlope().isPresent()),
          () -> assertFalse(originalModule.isOverlayBitMaskApplied()));

      assertAll(
          "Adapted module with default values",
          () -> assertEquals(0.25, adaptedModule.getRescaleSlope().getAsDouble()),
          () -> assertEquals(0.0, adaptedModule.getRescaleIntercept().getAsDouble()),
          () -> assertEquals("US", adaptedModule.getRescaleType().get()),
          () -> assertTrue(adaptedModule.isOverlayBitMaskApplied()));
    }

    @Test
    void should_adjust_existing_rescale_slope() {
      final var attributes = createAttributesWithRescale(2.0, 1.0, "HU");
      attributes.setString(Tag.Modality, VR.LO, "CT");

      final var originalModule = new ModalityLutModule(attributes);
      final var adaptedModule = originalModule.withOverlayBitMask(2);

      assertAll(
          "Original module unchanged",
          () -> assertEquals(2.0, originalModule.getRescaleSlope().getAsDouble()),
          () -> assertFalse(originalModule.isOverlayBitMaskApplied()));

      assertAll(
          "Adapted module with adjusted values",
          () -> assertEquals(0.5, adaptedModule.getRescaleSlope().getAsDouble()),
          () -> assertEquals(1.0, adaptedModule.getRescaleIntercept().getAsDouble()),
          () -> assertEquals("HU", adaptedModule.getRescaleType().get()),
          () -> assertTrue(adaptedModule.isOverlayBitMaskApplied()));
    }

    @Test
    void should_prevent_multiple_applications() {
      final var attributes = createAttributesWithRescale(4.0, 2.0, "HU");
      final var originalModule = new ModalityLutModule(attributes);

      // First application creates new instance
      final var firstAdaptedModule = originalModule.withOverlayBitMask(2);
      final var firstSlope = firstAdaptedModule.getRescaleSlope().getAsDouble();

      // Second application on already adapted module should return same instance
      final var secondAdaptedModule = firstAdaptedModule.withOverlayBitMask(3);

      assertAll(
          "Multiple application prevention",
          () -> assertEquals(1.0, firstSlope),
          () -> assertSame(firstAdaptedModule, secondAdaptedModule), // Same instance returned
          () -> assertEquals(firstSlope, secondAdaptedModule.getRescaleSlope().getAsDouble()),
          () -> assertTrue(secondAdaptedModule.isOverlayBitMaskApplied()));
    }

    @ParameterizedTest
    @MethodSource("bitShiftTestCases")
    void should_handle_different_bit_shift_values(
        final double originalSlope, final int bitShift, final double expectedSlope) {
      final var attributes = createAttributesWithRescale(originalSlope, 0.0, "US");
      final var originalModule = new ModalityLutModule(attributes);

      final var adaptedModule = originalModule.withOverlayBitMask(bitShift);

      assertEquals(expectedSlope, adaptedModule.getRescaleSlope().getAsDouble(), PRECISION);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.001, 1000.0, -2.0})
    void should_handle_extreme_slope_values(final double extremeSlope) {
      final var attributes = createAttributesWithRescale(extremeSlope, 0.0, "US");
      final var originalModule = new ModalityLutModule(attributes);

      final var adaptedModule = originalModule.withOverlayBitMask(1);

      final var expectedSlope = extremeSlope / 2.0;
      assertAll(
          "Extreme slope handling",
          () ->
              assertEquals(expectedSlope, adaptedModule.getRescaleSlope().getAsDouble(), PRECISION),
          () -> assertTrue(adaptedModule.isOverlayBitMaskApplied()));
    }

    @Test
    void should_preserve_intercept_during_adaptation() {
      final var attributes = createAttributesWithRescale(2.0, 100.0, "HU");
      final var originalModule = new ModalityLutModule(attributes);

      final var adaptedModule = originalModule.withOverlayBitMask(1);

      assertAll(
          "Intercept preservation",
          () -> assertEquals(100.0, adaptedModule.getRescaleIntercept().getAsDouble()),
          () -> assertEquals(1.0, adaptedModule.getRescaleSlope().getAsDouble()),
          () -> assertEquals("HU", adaptedModule.getRescaleType().get()));
    }

    @Test
    void should_set_default_values_when_only_intercept_exists() {
      final var attributes = new Attributes();
      attributes.setDouble(Tag.RescaleIntercept, VR.DS, 50.0);

      final var originalModule = new ModalityLutModule(attributes);
      final var adaptedModule = originalModule.withOverlayBitMask(2);

      assertAll(
          "Default values with existing intercept",
          () -> assertEquals(0.25, adaptedModule.getRescaleSlope().getAsDouble()),
          () -> assertEquals(50.0, adaptedModule.getRescaleIntercept().getAsDouble()),
          () -> assertEquals("US", adaptedModule.getRescaleType().get()));
    }

    @Test
    void should_maintain_lut_data_during_adaptation() {
      final var attributes = createAttributesWithRescale(2.0, 0.0, "HU");
      attributes.setString(Tag.Modality, VR.LO, "CT");

      final var lutAttributes = LutTestDataBuilder.createCtHounsfieldLut();
      final var sequence = attributes.newSequence(Tag.ModalityLUTSequence, 1);
      sequence.add(lutAttributes);

      final var originalModule = new ModalityLutModule(attributes);
      final var adaptedModule = originalModule.withOverlayBitMask(1);

      assertAll(
          "LUT preservation during adaptation",
          () -> assertEquals("HU", adaptedModule.getLutType().get()),
          () -> assertEquals("CT Hounsfield Units", adaptedModule.getLutExplanation().get()),
          () -> assertTrue(adaptedModule.getLut().isPresent()),
          () -> assertEquals(1.0, adaptedModule.getRescaleSlope().getAsDouble()));
    }

    @Test
    void should_create_independent_instances() {
      final var attributes = createAttributesWithRescale(4.0, 10.0, "HU");
      final var originalModule = new ModalityLutModule(attributes);

      final var adapted1 = originalModule.withOverlayBitMask(1);
      final var adapted2 = originalModule.withOverlayBitMask(2);

      assertAll(
          "Independent instances",
          () -> assertNotSame(originalModule, adapted1),
          () -> assertNotSame(originalModule, adapted2),
          () -> assertNotSame(adapted1, adapted2),
          () -> assertEquals(2.0, adapted1.getRescaleSlope().getAsDouble()),
          () -> assertEquals(1.0, adapted2.getRescaleSlope().getAsDouble()),
          () -> assertEquals(10.0, adapted1.getRescaleIntercept().getAsDouble()),
          () -> assertEquals(10.0, adapted2.getRescaleIntercept().getAsDouble()));
    }

    private static Stream<Arguments> bitShiftTestCases() {
      return Stream.of(
          Arguments.of(2.0, 1, 1.0), // 2.0 / 2^1 = 1.0
          Arguments.of(8.0, 3, 1.0), // 8.0 / 2^3 = 1.0
          Arguments.of(1.0, 4, 0.0625), // 1.0 / 2^4 = 0.0625
          Arguments.of(3.0, 0, 3.0) // 3.0 / 2^0 = 3.0
          );
    }
  }

  @Nested
  class Immutability_Tests {

    @Test
    void should_maintain_original_instance_unchanged_after_adaptation() {
      final var attributes = createAttributesWithRescale(5.0, 15.0, "CT");
      final var originalModule = new ModalityLutModule(attributes);

      final var adaptedModule = originalModule.withOverlayBitMask(2);

      // Original should remain unchanged
      assertAll(
          "Original module unchanged",
          () -> assertEquals(5.0, originalModule.getRescaleSlope().getAsDouble()),
          () -> assertEquals(15.0, originalModule.getRescaleIntercept().getAsDouble()),
          () -> assertEquals("CT", originalModule.getRescaleType().get()),
          () -> assertFalse(originalModule.isOverlayBitMaskApplied()));

      // New instance should have adapted values
      assertAll(
          "Adapted module changed",
          () -> assertEquals(1.25, adaptedModule.getRescaleSlope().getAsDouble()),
          () -> assertEquals(15.0, adaptedModule.getRescaleIntercept().getAsDouble()),
          () -> assertEquals("CT", adaptedModule.getRescaleType().get()),
          () -> assertTrue(adaptedModule.isOverlayBitMaskApplied()));
    }

    @Test
    void should_return_same_instance_when_overlay_already_applied() {
      final var attributes = createAttributesWithRescale(2.0, 5.0, "US");
      final var originalModule = new ModalityLutModule(attributes);

      final var firstAdaptation = originalModule.withOverlayBitMask(1);
      final var secondAdaptation = firstAdaptation.withOverlayBitMask(2);

      assertSame(firstAdaptation, secondAdaptation);
    }

    @Test
    void should_allow_chaining_operations_on_fresh_instances() {
      final var attributes = createAttributesWithRescale(8.0, 0.0, "US");

      final var result = new ModalityLutModule(attributes).withOverlayBitMask(3);

      assertAll(
          "Chained operations",
          () -> assertEquals(1.0, result.getRescaleSlope().getAsDouble()),
          () -> assertTrue(result.isOverlayBitMaskApplied()));
    }
  }

  @Nested
  class State_Management {

    @Test
    void should_track_overlay_bit_mask_application_state() {
      final var originalModule = new ModalityLutModule(new Attributes());
      final var adaptedModule = originalModule.withOverlayBitMask(1);

      assertAll(
          "State tracking",
          () -> assertFalse(originalModule.isOverlayBitMaskApplied()),
          () -> assertTrue(adaptedModule.isOverlayBitMaskApplied()));
    }

    @Test
    void should_maintain_consistent_state_across_multiple_queries() {
      final var attributes = createAttributesWithRescale(5.0, 4.0, "HU");
      final var originalModule = new ModalityLutModule(attributes);
      final var adaptedModule = originalModule.withOverlayBitMask(3);

      // Multiple calls should return consistent values
      final var expectedSlope = 5.0 / 8.0; // 5.0 / 2^3
      for (int i = 0; i < 5; i++) {
        assertAll(
            "Consistent state queries",
            () -> assertEquals(expectedSlope, adaptedModule.getRescaleSlope().getAsDouble()),
            () -> assertTrue(adaptedModule.isOverlayBitMaskApplied()));
      }
    }
  }

  @Nested
  class LUT_Validation {

    @Test
    void should_ignore_invalid_lut_sequence_missing_type() {
      final var attributes = new Attributes();
      final var lutAttributes = new Attributes();
      lutAttributes.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);
      lutAttributes.setBytes(Tag.LUTData, VR.OW, new byte[512]);
      // Missing ModalityLUTType

      final var sequence = attributes.newSequence(Tag.ModalityLUTSequence, 1);
      sequence.add(lutAttributes);

      final var module = new ModalityLutModule(attributes);

      assertAll(
          "Invalid LUT handling",
          () -> assertTrue(module.getLutType().isEmpty()),
          () -> assertTrue(module.getLut().isEmpty()));
    }

    @Test
    void should_ignore_invalid_lut_sequence_missing_descriptor() {
      final var attributes = new Attributes();
      final var lutAttributes = new Attributes();
      lutAttributes.setString(Tag.ModalityLUTType, VR.LO, "US");
      lutAttributes.setBytes(Tag.LUTData, VR.OW, new byte[512]);
      // Missing LUTDescriptor

      final var sequence = attributes.newSequence(Tag.ModalityLUTSequence, 1);
      sequence.add(lutAttributes);

      final var module = new ModalityLutModule(attributes);

      assertAll(
          "Invalid LUT handling",
          () -> assertTrue(module.getLutType().isEmpty()),
          () -> assertTrue(module.getLut().isEmpty()));
    }
  }

  // Helper method to create attributes with rescale values
  private static Attributes createAttributesWithRescale(
      final double slope, final double intercept, final String type) {
    final var attributes = new Attributes();
    attributes.setDouble(Tag.RescaleSlope, VR.DS, slope);
    attributes.setDouble(Tag.RescaleIntercept, VR.DS, intercept);
    attributes.setString(Tag.RescaleType, VR.LO, type);
    return attributes;
  }
}
