/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.lut;

import static org.junit.jupiter.api.Assertions.*;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.util.LutTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ModalityLutModuleTest {

  @Test
  @DisplayName("Verify ModalityLutModule initialization with non-null attributes")
  void shouldInitializeWithNonNullAttributes() {
    Attributes attributes = new Attributes();
    ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);

    assertFalse(modalityLutModule.getRescaleSlope().isPresent());
    assertFalse(modalityLutModule.getRescaleIntercept().isPresent());
    assertFalse(modalityLutModule.getRescaleType().isPresent());
    assertFalse(modalityLutModule.getLutType().isPresent());
    assertFalse(modalityLutModule.getLutExplanation().isPresent());
    assertFalse(modalityLutModule.getLut().isPresent());
    assertFalse(modalityLutModule.isOverlayBitMaskApplied());
  }

  @Test
  @DisplayName("Verify ModalityLutModule initialization with null attributes")
  void shouldThrowExceptionWhenAttributesAreNull() {
    assertThrows(NullPointerException.class, () -> new ModalityLutModule(null));
  }

  @Test
  @DisplayName("Verify do not apply modality LUT for XA and XRF")
  void verifyLUTForXA() {
    Attributes attributes = new Attributes();
    attributes.setString(Tag.Modality, VR.LO, "XA");
    attributes.setDouble(Tag.RescaleSlope, VR.DS, 2.0);
    attributes.setDouble(Tag.RescaleIntercept, VR.DS, 1.0);
    ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);
    assertEquals(2.0, modalityLutModule.getRescaleSlope().getAsDouble());
    assertEquals(1.0, modalityLutModule.getRescaleIntercept().getAsDouble());

    attributes.setString(Tag.Modality, VR.LO, "XRF");
    attributes.setString(Tag.PixelIntensityRelationship, VR.CS, "DISP");
    Attributes dcm = LutTestDataBuilder.createLinearLut8Bit();
    Sequence seq = attributes.newSequence(Tag.ModalityLUTSequence, 1);
    seq.add(dcm);

    modalityLutModule = new ModalityLutModule(attributes);
    assertEquals(2.0, modalityLutModule.getRescaleSlope().getAsDouble());
    assertEquals(1.0, modalityLutModule.getRescaleIntercept().getAsDouble());
    assertTrue(modalityLutModule.getLutExplanation().isEmpty());

    attributes.setString(Tag.Modality, VR.LO, "PT");
    dcm.setString(Tag.ModalityLUTType, VR.LO, "MGML");
    modalityLutModule = new ModalityLutModule(attributes);
    assertEquals("Linear 8-bit mapping", modalityLutModule.getLutExplanation().get());
    assertEquals("MGML", modalityLutModule.getLutType().get());
  }

  @Nested
  @DisplayName("Overlay Bit Mask Adaptation Tests")
  class OverlayBitMaskTests {

    @Test
    @DisplayName("Should set correct rescale slope when rescale slope is empty")
    void shouldSetCorrectRescaleSlopeWhenRescaleSlopeIsEmpty() {
      ModalityLutModule modalityLutModule = new ModalityLutModule(new Attributes());
      modalityLutModule.adaptWithOverlayBitMask(2);

      assertEquals(0.0, modalityLutModule.getRescaleIntercept().getAsDouble());
      assertEquals(0.25, modalityLutModule.getRescaleSlope().getAsDouble());
      assertEquals("US", modalityLutModule.getRescaleType().get());
      assertTrue(modalityLutModule.isOverlayBitMaskApplied());
    }

    @Test
    @DisplayName("Should adjust existing rescale slope correctly")
    void shouldAdjustExistingRescaleSlope() {
      Attributes attributes = new Attributes();
      attributes.setString(Tag.Modality, VR.LO, "CT");
      attributes.setDouble(Tag.RescaleSlope, VR.DS, 2.0);
      attributes.setDouble(Tag.RescaleIntercept, VR.DS, 1.0);
      attributes.setString(Tag.RescaleType, VR.LO, "HU");

      ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);
      modalityLutModule.adaptWithOverlayBitMask(2);

      assertEquals(1.0, modalityLutModule.getRescaleIntercept().getAsDouble());
      assertEquals(0.5, modalityLutModule.getRescaleSlope().getAsDouble());
      assertEquals("HU", modalityLutModule.getRescaleType().get());
      assertTrue(modalityLutModule.isOverlayBitMaskApplied());
    }

    @Test
    @DisplayName("Should prevent multiple applications of overlay bit mask")
    void shouldPreventMultipleApplications() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.RescaleSlope, VR.DS, 4.0);
      attributes.setDouble(Tag.RescaleIntercept, VR.DS, 2.0);

      ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);

      // First application should work
      modalityLutModule.adaptWithOverlayBitMask(2);
      assertEquals(1.0, modalityLutModule.getRescaleSlope().getAsDouble());
      assertTrue(modalityLutModule.isOverlayBitMaskApplied());

      // Second application should be ignored
      modalityLutModule.adaptWithOverlayBitMask(3);
      assertEquals(
          1.0, modalityLutModule.getRescaleSlope().getAsDouble()); // Should remain unchanged
      assertTrue(modalityLutModule.isOverlayBitMaskApplied());
    }

    @Test
    @DisplayName("Should handle different bit shift values correctly")
    void shouldHandleDifferentBitShiftValues() {
      // Test with 1 bit shift
      Attributes attributes1 = new Attributes();
      attributes1.setDouble(Tag.RescaleSlope, VR.DS, 2.0);
      ModalityLutModule module1 = new ModalityLutModule(attributes1);
      module1.adaptWithOverlayBitMask(1);
      assertEquals(1.0, module1.getRescaleSlope().getAsDouble()); // 2.0 / 2^1 = 1.0

      // Test with 3 bit shift
      Attributes attributes2 = new Attributes();
      attributes2.setDouble(Tag.RescaleSlope, VR.DS, 8.0);
      ModalityLutModule module2 = new ModalityLutModule(attributes2);
      module2.adaptWithOverlayBitMask(3);
      assertEquals(1.0, module2.getRescaleSlope().getAsDouble()); // 8.0 / 2^3 = 1.0

      // Test with 4 bit shift
      Attributes attributes3 = new Attributes();
      attributes3.setDouble(Tag.RescaleSlope, VR.DS, 1.0);
      ModalityLutModule module3 = new ModalityLutModule(attributes3);
      module3.adaptWithOverlayBitMask(4);
      assertEquals(0.0625, module3.getRescaleSlope().getAsDouble()); // 1.0 / 2^4 = 0.0625
    }

    @Test
    @DisplayName("Should handle zero bit shift gracefully")
    void shouldHandleZeroBitShift() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.RescaleSlope, VR.DS, 3.0);

      ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);
      modalityLutModule.adaptWithOverlayBitMask(0);

      assertEquals(3.0, modalityLutModule.getRescaleSlope().getAsDouble()); // 3.0 / 2^0 = 3.0
      assertTrue(modalityLutModule.isOverlayBitMaskApplied());
    }

    @Test
    @DisplayName("Should preserve rescale intercept during adaptation")
    void shouldPreserveRescaleIntercept() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.RescaleSlope, VR.DS, 2.0);
      attributes.setDouble(Tag.RescaleIntercept, VR.DS, 100.0);
      attributes.setString(Tag.RescaleType, VR.LO, "HU");

      ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);
      modalityLutModule.adaptWithOverlayBitMask(1);

      assertEquals(100.0, modalityLutModule.getRescaleIntercept().getAsDouble());
      assertEquals(1.0, modalityLutModule.getRescaleSlope().getAsDouble());
      assertEquals("HU", modalityLutModule.getRescaleType().get());
    }

    @Test
    @DisplayName("Should set default values when no original rescale slope exists")
    void shouldSetDefaultValuesWhenNoOriginalRescaleSlope() {
      ModalityLutModule modalityLutModule = new ModalityLutModule(new Attributes());

      // Verify initial state
      assertFalse(modalityLutModule.getRescaleSlope().isPresent());
      assertFalse(modalityLutModule.getRescaleIntercept().isPresent());
      assertFalse(modalityLutModule.getRescaleType().isPresent());

      modalityLutModule.adaptWithOverlayBitMask(3);

      // Should use default slope (1.0) and set defaults for intercept and type
      assertEquals(0.125, modalityLutModule.getRescaleSlope().getAsDouble()); // 1.0 / 2^3
      assertEquals(0.0, modalityLutModule.getRescaleIntercept().getAsDouble());
      assertEquals("US", modalityLutModule.getRescaleType().get());
    }

    @Test
    @DisplayName("Should maintain LUT data during overlay bit mask adaptation")
    void shouldMaintainLutDataDuringAdaptation() {
      Attributes attributes = new Attributes();
      attributes.setString(Tag.Modality, VR.LO, "CT");
      attributes.setDouble(Tag.RescaleSlope, VR.DS, 2.0);

      // Add LUT sequence (though this creates a warning due to mutual exclusivity)
      Attributes dcmLut = LutTestDataBuilder.createCtHounsfieldLut();
      Sequence seq = attributes.newSequence(Tag.ModalityLUTSequence, 1);
      seq.add(dcmLut);

      ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);
      modalityLutModule.adaptWithOverlayBitMask(1);

      // LUT data should be preserved
      assertEquals("HU", modalityLutModule.getLutType().get());
      assertEquals("CT Hounsfield Units", modalityLutModule.getLutExplanation().get());
      assertTrue(modalityLutModule.getLut().isPresent());

      // Rescale slope should still be adjusted
      assertEquals(1.0, modalityLutModule.getRescaleSlope().getAsDouble());
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle very small slope values")
    void shouldHandleVerySmallSlopeValues() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.RescaleSlope, VR.DS, 0.001);

      ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);
      modalityLutModule.adaptWithOverlayBitMask(2);

      assertEquals(0.00025, modalityLutModule.getRescaleSlope().getAsDouble(), 1e-10);
      assertTrue(modalityLutModule.isOverlayBitMaskApplied());
    }

    @Test
    @DisplayName("Should handle large slope values")
    void shouldHandleLargeSlopeValues() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.RescaleSlope, VR.DS, 1000.0);

      ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);
      modalityLutModule.adaptWithOverlayBitMask(4);

      assertEquals(62.5, modalityLutModule.getRescaleSlope().getAsDouble()); // 1000.0 / 16
      assertTrue(modalityLutModule.isOverlayBitMaskApplied());
    }

    @Test
    @DisplayName("Should handle negative slope values")
    void shouldHandleNegativeSlopeValues() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.RescaleSlope, VR.DS, -2.0);

      ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);
      modalityLutModule.adaptWithOverlayBitMask(1);

      assertEquals(-1.0, modalityLutModule.getRescaleSlope().getAsDouble());
      assertTrue(modalityLutModule.isOverlayBitMaskApplied());
    }

    @Test
    @DisplayName("Should handle module with only intercept set initially")
    void shouldHandleModuleWithOnlyInterceptSet() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.RescaleIntercept, VR.DS, 50.0);
      // Note: This is technically invalid DICOM (intercept without slope)

      ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);
      modalityLutModule.adaptWithOverlayBitMask(2);

      // Should use default slope and preserve intercept
      assertEquals(0.25, modalityLutModule.getRescaleSlope().getAsDouble()); // 1.0 / 4
      assertEquals(50.0, modalityLutModule.getRescaleIntercept().getAsDouble());
      assertEquals("US", modalityLutModule.getRescaleType().get());
    }
  }

  @Nested
  @DisplayName("State Management Tests")
  class StateManagementTests {

    @Test
    @DisplayName("Should track overlay bit mask application state correctly")
    void shouldTrackOverlayBitMaskApplicationState() {
      ModalityLutModule modalityLutModule = new ModalityLutModule(new Attributes());

      // Initially should not be applied
      assertFalse(modalityLutModule.isOverlayBitMaskApplied());

      // After application should be marked as applied
      modalityLutModule.adaptWithOverlayBitMask(1);
      assertTrue(modalityLutModule.isOverlayBitMaskApplied());
    }

    @Test
    @DisplayName("Should maintain consistent state across multiple query attempts")
    void shouldMaintainConsistentStateAcrossMultipleQueries() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.RescaleSlope, VR.DS, 5.0);
      attributes.setDouble(Tag.RescaleIntercept, VR.DS, 4.0);

      ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);
      modalityLutModule.adaptWithOverlayBitMask(3);

      // Multiple calls to getters should return same values
      for (int i = 0; i < 5; i++) {
        assertEquals(0.625, modalityLutModule.getRescaleSlope().getAsDouble());
        assertTrue(modalityLutModule.isOverlayBitMaskApplied());
      }
    }
  }
}
