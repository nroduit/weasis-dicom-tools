/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.lut;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VoiLutModule}.
 *
 * @author Nicolas Roduit
 */
@DisplayName("VoiLutModule Tests")
class VoiLutModuleTest {

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Should throw NullPointerException when attributes is null")
    void shouldThrowNullPointerExceptionWhenAttributesIsNull() {
      assertThrows(NullPointerException.class, () -> new VoiLutModule(null));
    }

    @Test
    @DisplayName("Should create instance with empty attributes")
    void shouldCreateInstanceWithEmptyAttributes() {
      Attributes attributes = new Attributes();
      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertNotNull(voiLutModule);
      assertTrue(voiLutModule.getWindowCenter().isEmpty());
      assertTrue(voiLutModule.getWindowWidth().isEmpty());
      assertTrue(voiLutModule.getLutExplanation().isEmpty());
      assertTrue(voiLutModule.getLut().isEmpty());
      assertTrue(voiLutModule.getWindowCenterWidthExplanation().isEmpty());
      assertFalse(voiLutModule.getVoiLutFunction().isPresent());
    }
  }

  @Nested
  @DisplayName("Window Center and Width Tests")
  class WindowCenterWidthTests {

    @Test
    @DisplayName("Should initialize window center and width when both are present")
    void shouldInitializeWindowCenterAndWidthWhenBothPresent() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, 100.0, 200.0);
      attributes.setDouble(Tag.WindowWidth, VR.DS, 50.0, 75.0);
      attributes.setString(Tag.VOILUTFunction, VR.CS, "LINEAR");
      attributes.setString(Tag.WindowCenterWidthExplanation, VR.LO, "Default", "Enhanced");

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(100.0, 200.0), voiLutModule.getWindowCenter());
      assertEquals(List.of(50.0, 75.0), voiLutModule.getWindowWidth());
      assertEquals(Optional.of("LINEAR"), voiLutModule.getVoiLutFunction());
      assertEquals(List.of("Default", "Enhanced"), voiLutModule.getWindowCenterWidthExplanation());
    }

    @Test
    @DisplayName("Should not initialize window values when only center is present")
    void shouldNotInitializeWhenOnlyCenterPresent() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, 100.0);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertTrue(voiLutModule.getWindowCenter().isEmpty());
      assertTrue(voiLutModule.getWindowWidth().isEmpty());
      assertFalse(voiLutModule.getVoiLutFunction().isPresent());
    }

    @Test
    @DisplayName("Should not initialize window values when only width is present")
    void shouldNotInitializeWhenOnlyWidthPresent() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowWidth, VR.DS, 50.0);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertTrue(voiLutModule.getWindowCenter().isEmpty());
      assertTrue(voiLutModule.getWindowWidth().isEmpty());
      assertFalse(voiLutModule.getVoiLutFunction().isPresent());
    }

    @Test
    @DisplayName("Should handle single window center and width values")
    void shouldHandleSingleWindowCenterAndWidthValues() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, 150.0);
      attributes.setDouble(Tag.WindowWidth, VR.DS, 100.0);
      attributes.setString(Tag.VOILUTFunction, VR.CS, "SIGMOID");

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(150.0), voiLutModule.getWindowCenter());
      assertEquals(List.of(100.0), voiLutModule.getWindowWidth());
      assertEquals(Optional.of("SIGMOID"), voiLutModule.getVoiLutFunction());
      assertTrue(voiLutModule.getWindowCenterWidthExplanation().isEmpty());
    }

    @Test
    @DisplayName("Should handle multiple window center and width values")
    void shouldHandleMultipleWindowCenterAndWidthValues() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, 100.0, 200.0, 300.0);
      attributes.setDouble(Tag.WindowWidth, VR.DS, 50.0, 75.0, 100.0);
      attributes.setString(Tag.WindowCenterWidthExplanation, VR.LO, "Soft Tissue", "Bone", "Lung");

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(100.0, 200.0, 300.0), voiLutModule.getWindowCenter());
      assertEquals(List.of(50.0, 75.0, 100.0), voiLutModule.getWindowWidth());
      assertEquals(
          List.of("Soft Tissue", "Bone", "Lung"), voiLutModule.getWindowCenterWidthExplanation());
    }

    @Test
    @DisplayName("Should handle zero window values")
    void shouldHandleZeroWindowValues() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, 0.0);
      attributes.setDouble(Tag.WindowWidth, VR.DS, 0.0);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(0.0), voiLutModule.getWindowCenter());
      assertEquals(List.of(0.0), voiLutModule.getWindowWidth());
    }

    @Test
    @DisplayName("Should handle negative window values")
    void shouldHandleNegativeWindowValues() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, -100.0);
      attributes.setDouble(Tag.WindowWidth, VR.DS, -50.0);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(-100.0), voiLutModule.getWindowCenter());
      assertEquals(List.of(-50.0), voiLutModule.getWindowWidth());
    }

    @Test
    @DisplayName("Should handle very large window values")
    void shouldHandleVeryLargeWindowValues() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, Double.MAX_VALUE);
      attributes.setDouble(Tag.WindowWidth, VR.DS, Double.MAX_VALUE);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(Double.MAX_VALUE), voiLutModule.getWindowCenter());
      assertEquals(List.of(Double.MAX_VALUE), voiLutModule.getWindowWidth());
    }

    @Test
    @DisplayName("Should handle fractional window values")
    void shouldHandleFractionalWindowValues() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, 123.456);
      attributes.setDouble(Tag.WindowWidth, VR.DS, 78.901);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(123.456), voiLutModule.getWindowCenter());
      assertEquals(List.of(78.901), voiLutModule.getWindowWidth());
    }
  }

  @Nested
  @DisplayName("VOI LUT Sequence Tests")
  class VoiLutSequenceTests {

    @Test
    @DisplayName("Should initialize VOI LUT sequence when present and valid")
    void shouldInitializeVoiLutSequenceWhenPresentAndValid() {
      Attributes attributes = new Attributes();
      Sequence voiLutSequence = attributes.newSequence(Tag.VOILUTSequence, 1);

      Attributes lutItem = new Attributes();
      lutItem.setString(Tag.LUTExplanation, VR.LO, "Test VOI LUT");
      lutItem.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);
      lutItem.setBytes(Tag.LUTData, VR.OW, createSimpleLutData(256));
      voiLutSequence.add(lutItem);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of("Test VOI LUT"), voiLutModule.getLutExplanation());
      assertEquals(1, voiLutModule.getLut().size());
      assertNotNull(voiLutModule.getLut().get(0));
    }

    @Test
    @DisplayName("Should handle multiple VOI LUT sequence items")
    void shouldHandleMultipleVoiLutSequenceItems() {
      Attributes attributes = new Attributes();
      Sequence voiLutSequence = attributes.newSequence(Tag.VOILUTSequence, 2);

      // First LUT item
      Attributes lutItem1 = new Attributes();
      lutItem1.setString(Tag.LUTExplanation, VR.LO, "Soft Tissue LUT");
      lutItem1.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);
      lutItem1.setBytes(Tag.LUTData, VR.OW, createSimpleLutData(256));
      voiLutSequence.add(lutItem1);

      // Second LUT item
      Attributes lutItem2 = new Attributes();
      lutItem2.setString(Tag.LUTExplanation, VR.LO, "Bone LUT");
      lutItem2.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);
      lutItem2.setBytes(Tag.LUTData, VR.OW, createSimpleLutData(256));
      voiLutSequence.add(lutItem2);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of("Soft Tissue LUT", "Bone LUT"), voiLutModule.getLutExplanation());
      assertEquals(2, voiLutModule.getLut().size());
    }

    @Test
    @DisplayName("Should handle empty LUT explanation")
    void shouldHandleEmptyLutExplanation() {
      Attributes attributes = new Attributes();
      Sequence voiLutSequence = attributes.newSequence(Tag.VOILUTSequence, 1);

      Attributes lutItem = new Attributes();
      // No LUT explanation set, should default to empty string
      lutItem.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);
      lutItem.setBytes(Tag.LUTData, VR.OW, createSimpleLutData(256));
      voiLutSequence.add(lutItem);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(""), voiLutModule.getLutExplanation());
    }

    @Test
    @DisplayName("Should handle VOI LUT sequence with invalid LUT data")
    void shouldHandleVoiLutSequenceWithInvalidLutData() {
      Attributes attributes = new Attributes();
      Sequence voiLutSequence = attributes.newSequence(Tag.VOILUTSequence, 1);

      Attributes lutItem = new Attributes();
      lutItem.setString(Tag.LUTExplanation, VR.LO, "Invalid LUT");
      // Missing LUT descriptor and data - should result in null LUT
      voiLutSequence.add(lutItem);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of("Invalid LUT"), voiLutModule.getLutExplanation());
      assertEquals(Collections.emptyList(), voiLutModule.getLut());
    }

    @Test
    @DisplayName("Should not initialize VOI LUT sequence when empty")
    void shouldNotInitializeVoiLutSequenceWhenEmpty() {
      Attributes attributes = new Attributes();
      attributes.newSequence(Tag.VOILUTSequence, 0); // Empty sequence

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertTrue(voiLutModule.getLutExplanation().isEmpty());
      assertTrue(voiLutModule.getLut().isEmpty());
    }
  }

  @Nested
  @DisplayName("Combined Scenarios Tests")
  class CombinedScenariosTests {

    @Test
    @DisplayName("Should handle both window values and VOI LUT sequence")
    void shouldHandleBothWindowValuesAndVoiLutSequence() {
      Attributes attributes = new Attributes();

      // Set window center/width
      attributes.setDouble(Tag.WindowCenter, VR.DS, 100.0);
      attributes.setDouble(Tag.WindowWidth, VR.DS, 50.0);
      attributes.setString(Tag.VOILUTFunction, VR.CS, "LINEAR");
      attributes.setString(Tag.WindowCenterWidthExplanation, VR.LO, "Window explanation");

      // Set VOI LUT sequence
      Sequence voiLutSequence = attributes.newSequence(Tag.VOILUTSequence, 1);
      Attributes lutItem = new Attributes();
      lutItem.setString(Tag.LUTExplanation, VR.LO, "Combined LUT");
      lutItem.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);
      lutItem.setBytes(Tag.LUTData, VR.OW, createSimpleLutData(256));
      voiLutSequence.add(lutItem);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      // Both should be initialized
      assertEquals(List.of(100.0), voiLutModule.getWindowCenter());
      assertEquals(List.of(50.0), voiLutModule.getWindowWidth());
      assertEquals(Optional.of("LINEAR"), voiLutModule.getVoiLutFunction());
      assertEquals(List.of("Window explanation"), voiLutModule.getWindowCenterWidthExplanation());
      assertEquals(List.of("Combined LUT"), voiLutModule.getLutExplanation());
      assertEquals(1, voiLutModule.getLut().size());
    }

    @Test
    @DisplayName("Should handle window values with mismatched array lengths")
    void shouldHandleWindowValuesWithMismatchedArrayLengths() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, 100.0, 200.0);
      attributes.setDouble(Tag.WindowWidth, VR.DS, 50.0); // Only one width value

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(100.0, 200.0), voiLutModule.getWindowCenter());
      assertEquals(List.of(50.0), voiLutModule.getWindowWidth());
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle empty window arrays")
    void shouldHandleEmptyWindowArrays() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS); // Empty array
      attributes.setDouble(Tag.WindowWidth, VR.DS); // Empty array

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertTrue(voiLutModule.getWindowCenter().isEmpty());
      assertTrue(voiLutModule.getWindowWidth().isEmpty());
    }

    @Test
    @DisplayName("Should handle large number of window values")
    void shouldHandleLargeNumberOfWindowValues() {
      Attributes attributes = new Attributes();

      double[] centers = new double[1000];
      double[] widths = new double[1000];
      for (int i = 0; i < 1000; i++) {
        centers[i] = i * 0.1;
        widths[i] = i * 0.05;
      }

      attributes.setDouble(Tag.WindowCenter, VR.DS, centers);
      attributes.setDouble(Tag.WindowWidth, VR.DS, widths);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(1000, voiLutModule.getWindowCenter().size());
      assertEquals(1000, voiLutModule.getWindowWidth().size());
      assertEquals(0.0, voiLutModule.getWindowCenter().get(0), 0.001);
      assertEquals(49.95, voiLutModule.getWindowWidth().get(999), 0.001);
    }

    @Test
    @DisplayName("Should handle all VOI LUT function types")
    void shouldHandleAllVoiLutFunctionTypes() {
      String[] functions = {"LINEAR", "SIGMOID", "LINEAR_EXACT"};

      for (String function : functions) {
        Attributes attributes = new Attributes();
        attributes.setDouble(Tag.WindowCenter, VR.DS, 100.0);
        attributes.setDouble(Tag.WindowWidth, VR.DS, 50.0);
        attributes.setString(Tag.VOILUTFunction, VR.CS, function);

        VoiLutModule voiLutModule = new VoiLutModule(attributes);

        assertEquals(Optional.of(function), voiLutModule.getVoiLutFunction());
      }
    }

    @Test
    @DisplayName("Should handle special floating point values")
    void shouldHandleSpecialFloatingPointValues() {
      Attributes attributes = new Attributes();
      attributes.setDouble(
          Tag.WindowCenter, VR.DS, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
      attributes.setDouble(
          Tag.WindowWidth, VR.DS, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      assertEquals(3, voiLutModule.getWindowCenter().size());
      assertEquals(3, voiLutModule.getWindowWidth().size());
      assertTrue(Double.isNaN(voiLutModule.getWindowCenter().get(0)));
      assertTrue(Double.isInfinite(voiLutModule.getWindowCenter().get(1)));
      assertTrue(Double.isInfinite(voiLutModule.getWindowCenter().get(2)));
    }
  }

  @Nested
  @DisplayName("Data Integrity Tests")
  class DataIntegrityTests {

    @Test
    @DisplayName("Should return immutable lists")
    void shouldReturnImmutableLists() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, 100.0);
      attributes.setDouble(Tag.WindowWidth, VR.DS, 50.0);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      // Lists should not be modifiable
      assertThrows(
          UnsupportedOperationException.class, () -> voiLutModule.getWindowCenter().add(200.0));
      assertThrows(
          UnsupportedOperationException.class, () -> voiLutModule.getWindowWidth().add(75.0));
      assertThrows(
          UnsupportedOperationException.class, () -> voiLutModule.getLutExplanation().add("test"));
      assertThrows(UnsupportedOperationException.class, () -> voiLutModule.getLut().add(null));
      assertThrows(
          UnsupportedOperationException.class,
          () -> voiLutModule.getWindowCenterWidthExplanation().add("test"));
    }

    @Test
    @DisplayName("Should maintain data consistency after multiple accesses")
    void shouldMaintainDataConsistencyAfterMultipleAccesses() {
      Attributes attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, 100.0, 200.0);
      attributes.setDouble(Tag.WindowWidth, VR.DS, 50.0, 75.0);

      VoiLutModule voiLutModule = new VoiLutModule(attributes);

      // Multiple accesses should return the same data
      List<Double> firstAccess = voiLutModule.getWindowCenter();
      List<Double> secondAccess = voiLutModule.getWindowCenter();

      assertEquals(firstAccess, secondAccess);
      assertSame(firstAccess, secondAccess); // Should be the same instance
    }
  }

  /**
   * Creates simple LUT data for testing purposes.
   *
   * @param entries Number of LUT entries
   * @return Byte array representing LUT data
   */
  private byte[] createSimpleLutData(int entries) {
    byte[] data = new byte[entries * 2]; // 16-bit entries
    for (int i = 0; i < entries; i++) {
      // Create a simple linear mapping
      int value = (i * 65535) / (entries - 1);
      data[i * 2] = (byte) (value & 0xFF);
      data[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
    }
    return data;
  }
}
