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

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link VoiLutModule}.
 *
 * @author Nicolas Roduit
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class VoiLutModuleTest {

  @Nested
  class Constructor_Tests {

    @Test
    void should_throw_null_pointer_exception_when_attributes_is_null() {
      assertThrows(NullPointerException.class, () -> new VoiLutModule(null));
    }

    @Test
    void should_create_instance_with_empty_attributes() {
      var attributes = new Attributes();
      var voiLutModule = new VoiLutModule(attributes);

      assertNotNull(voiLutModule);
      assertTrue(voiLutModule.getWindowCenter().isEmpty());
      assertTrue(voiLutModule.getWindowWidth().isEmpty());
      assertTrue(voiLutModule.getLutExplanation().isEmpty());
      assertTrue(voiLutModule.getLut().isEmpty());
      assertTrue(voiLutModule.getWindowCenterWidthExplanation().isEmpty());
      assertTrue(voiLutModule.getVoiLutFunction().isEmpty());
    }
  }

  @Nested
  class Window_Center_Width_Tests {

    @Test
    void should_initialize_window_values_when_both_present() {
      var attributes =
          createAttributesWithWindowValues(
              new double[] {100.0, 200.0},
              new double[] {50.0, 75.0},
              "LINEAR",
              new String[] {"Default", "Enhanced"});

      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(100.0, 200.0), voiLutModule.getWindowCenter());
      assertEquals(List.of(50.0, 75.0), voiLutModule.getWindowWidth());
      assertEquals(Optional.of("LINEAR"), voiLutModule.getVoiLutFunction());
      assertEquals(List.of("Default", "Enhanced"), voiLutModule.getWindowCenterWidthExplanation());
    }

    @Test
    void should_initialize_when_only_center_present() {
      var attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, 100.0);

      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(100.0, voiLutModule.getWindowCenter().get(0));
      assertTrue(voiLutModule.getWindowWidth().isEmpty());
      assertTrue(voiLutModule.getVoiLutFunction().isEmpty());
    }

    @Test
    void should_initialize_when_only_width_present() {
      var attributes = new Attributes();
      attributes.setDouble(Tag.WindowWidth, VR.DS, 50.0);

      var voiLutModule = new VoiLutModule(attributes);

      assertTrue(voiLutModule.getWindowCenter().isEmpty());
      assertEquals(50.0, voiLutModule.getWindowWidth().get(0));
      assertTrue(voiLutModule.getVoiLutFunction().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("singleWindowValuesProvider")
    void should_handle_single_window_values(double center, double width, String function) {
      var attributes =
          createAttributesWithWindowValues(
              new double[] {center}, new double[] {width}, function, null);

      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(center), voiLutModule.getWindowCenter());
      assertEquals(List.of(width), voiLutModule.getWindowWidth());
      assertEquals(Optional.ofNullable(function), voiLutModule.getVoiLutFunction());
      assertTrue(voiLutModule.getWindowCenterWidthExplanation().isEmpty());
    }

    private static Stream<Arguments> singleWindowValuesProvider() {
      return Stream.of(
          Arguments.of(150.0, 100.0, "SIGMOID"),
          Arguments.of(0.0, 0.0, null),
          Arguments.of(-100.0, -50.0, "LINEAR"),
          Arguments.of(123.456, 78.901, "LINEAR_EXACT"),
          Arguments.of(Double.MAX_VALUE, Double.MAX_VALUE, "LINEAR"));
    }

    @Test
    void should_handle_multiple_window_values() {
      var centers = new double[] {100.0, 200.0, 300.0};
      var widths = new double[] {50.0, 75.0, 100.0};
      var explanations = new String[] {"Soft Tissue", "Bone", "Lung"};

      var attributes = createAttributesWithWindowValues(centers, widths, null, explanations);
      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(100.0, 200.0, 300.0), voiLutModule.getWindowCenter());
      assertEquals(List.of(50.0, 75.0, 100.0), voiLutModule.getWindowWidth());
      assertEquals(
          List.of("Soft Tissue", "Bone", "Lung"), voiLutModule.getWindowCenterWidthExplanation());
    }

    @Test
    void should_handle_mismatched_array_lengths() {
      var attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, 100.0, 200.0);
      attributes.setDouble(Tag.WindowWidth, VR.DS, 50.0); // Only one width value

      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(100.0, 200.0), voiLutModule.getWindowCenter());
      assertEquals(List.of(50.0), voiLutModule.getWindowWidth());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LINEAR", "SIGMOID", "LINEAR_EXACT"})
    void should_handle_all_voi_lut_function_types(String function) {
      var attributes =
          createAttributesWithWindowValues(
              new double[] {100.0}, new double[] {50.0}, function, null);

      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(Optional.of(function), voiLutModule.getVoiLutFunction());
    }

    @Test
    void should_handle_special_floating_point_values() {
      var specialValues =
          new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
      var attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, specialValues);
      attributes.setDouble(Tag.WindowWidth, VR.DS, specialValues);

      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(3, voiLutModule.getWindowCenter().size());
      assertEquals(3, voiLutModule.getWindowWidth().size());
      assertTrue(Double.isNaN(voiLutModule.getWindowCenter().get(0)));
      assertTrue(Double.isInfinite(voiLutModule.getWindowCenter().get(1)));
      assertTrue(Double.isInfinite(voiLutModule.getWindowCenter().get(2)));
    }

    @Test
    void should_handle_empty_window_arrays() {
      var attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS); // Empty array
      attributes.setDouble(Tag.WindowWidth, VR.DS); // Empty array

      var voiLutModule = new VoiLutModule(attributes);

      assertTrue(voiLutModule.getWindowCenter().isEmpty());
      assertTrue(voiLutModule.getWindowWidth().isEmpty());
    }

    @Test
    void should_handle_large_number_of_window_values() {
      var centers = IntStream.range(0, 1000).mapToDouble(i -> i * 0.1).toArray();
      var widths = IntStream.range(0, 1000).mapToDouble(i -> i * 0.05).toArray();

      var attributes = new Attributes();
      attributes.setDouble(Tag.WindowCenter, VR.DS, centers);
      attributes.setDouble(Tag.WindowWidth, VR.DS, widths);

      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(1000, voiLutModule.getWindowCenter().size());
      assertEquals(1000, voiLutModule.getWindowWidth().size());
      assertEquals(0.0, voiLutModule.getWindowCenter().get(0), 0.001);
      assertEquals(49.95, voiLutModule.getWindowWidth().get(999), 0.001);
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Voi_Lut_Sequence_Tests {

    @Test
    void should_initialize_voi_lut_sequence_when_present_and_valid() {
      var attributes = new Attributes();
      var voiLutSequence = attributes.newSequence(Tag.VOILUTSequence, 1);
      voiLutSequence.add(createValidLutItem("Test VOI LUT", 256));

      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of("Test VOI LUT"), voiLutModule.getLutExplanation());
      assertEquals(1, voiLutModule.getLut().size());
      assertNotNull(voiLutModule.getLut().get(0));
    }

    @Test
    void should_handle_multiple_voi_lut_sequence_items() {
      var attributes = new Attributes();
      var voiLutSequence = attributes.newSequence(Tag.VOILUTSequence, 2);
      voiLutSequence.add(createValidLutItem("Soft Tissue LUT", 256));
      voiLutSequence.add(createValidLutItem("Bone LUT", 512));

      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of("Soft Tissue LUT", "Bone LUT"), voiLutModule.getLutExplanation());
      assertEquals(2, voiLutModule.getLut().size());
    }

    @Test
    void should_handle_empty_lut_explanation() {
      var attributes = new Attributes();
      var voiLutSequence = attributes.newSequence(Tag.VOILUTSequence, 1);
      voiLutSequence.add(createValidLutItem(null, 256)); // Null explanation

      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(""), voiLutModule.getLutExplanation());
    }

    @Test
    void should_handle_voi_lut_sequence_with_invalid_lut_data() {
      var attributes = new Attributes();
      var voiLutSequence = attributes.newSequence(Tag.VOILUTSequence, 1);

      var lutItem = new Attributes();
      lutItem.setString(Tag.LUTExplanation, VR.LO, "Invalid LUT");
      // Missing LUT descriptor and data - should result in null LUT
      voiLutSequence.add(lutItem);

      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of("Invalid LUT"), voiLutModule.getLutExplanation());
      assertTrue(voiLutModule.getLut().isEmpty());
    }

    @Test
    void should_not_initialize_voi_lut_sequence_when_empty() {
      var attributes = new Attributes();
      attributes.newSequence(Tag.VOILUTSequence, 0); // Empty sequence

      var voiLutModule = new VoiLutModule(attributes);

      assertTrue(voiLutModule.getLutExplanation().isEmpty());
      assertTrue(voiLutModule.getLut().isEmpty());
    }

    private Attributes createValidLutItem(String explanation, int entries) {
      var lutItem = new Attributes();
      if (explanation != null) {
        lutItem.setString(Tag.LUTExplanation, VR.LO, explanation);
      }
      lutItem.setInt(Tag.LUTDescriptor, VR.US, entries, 0, 8);
      lutItem.setBytes(Tag.LUTData, VR.OW, createLutData(entries));
      return lutItem;
    }
  }

  @Nested
  class Combined_Scenarios_Tests {

    @Test
    void should_handle_both_window_values_and_voi_lut_sequence() {
      var attributes =
          createAttributesWithWindowValues(
              new double[] {100.0},
              new double[] {50.0},
              "LINEAR",
              new String[] {"Window explanation"});

      var voiLutSequence = attributes.newSequence(Tag.VOILUTSequence, 1);
      var lutItem = new Attributes();
      lutItem.setString(Tag.LUTExplanation, VR.LO, "Combined LUT");
      lutItem.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);
      lutItem.setBytes(Tag.LUTData, VR.OW, createLutData(256));
      voiLutSequence.add(lutItem);

      var voiLutModule = new VoiLutModule(attributes);

      assertEquals(List.of(100.0), voiLutModule.getWindowCenter());
      assertEquals(List.of(50.0), voiLutModule.getWindowWidth());
      assertEquals(Optional.of("LINEAR"), voiLutModule.getVoiLutFunction());
      assertEquals(List.of("Window explanation"), voiLutModule.getWindowCenterWidthExplanation());
      assertEquals(List.of("Combined LUT"), voiLutModule.getLutExplanation());
      assertEquals(1, voiLutModule.getLut().size());
    }
  }

  @Nested
  class Data_Integrity_Tests {

    @Test
    void should_return_immutable_lists() {
      var attributes =
          createAttributesWithWindowValues(new double[] {100.0}, new double[] {50.0}, null, null);
      var voiLutModule = new VoiLutModule(attributes);

      assertAll(
          "All lists should be immutable",
          () ->
              assertThrows(
                  UnsupportedOperationException.class,
                  () -> voiLutModule.getWindowCenter().add(200.0)),
          () ->
              assertThrows(
                  UnsupportedOperationException.class,
                  () -> voiLutModule.getWindowWidth().add(75.0)),
          () ->
              assertThrows(
                  UnsupportedOperationException.class,
                  () -> voiLutModule.getLutExplanation().add("test")),
          () ->
              assertThrows(
                  UnsupportedOperationException.class, () -> voiLutModule.getLut().add(null)),
          () ->
              assertThrows(
                  UnsupportedOperationException.class,
                  () -> voiLutModule.getWindowCenterWidthExplanation().add("test")));
    }

    @Test
    void should_maintain_data_consistency_after_multiple_accesses() {
      var attributes =
          createAttributesWithWindowValues(
              new double[] {100.0, 200.0}, new double[] {50.0, 75.0}, null, null);
      var voiLutModule = new VoiLutModule(attributes);

      var firstAccess = voiLutModule.getWindowCenter();
      var secondAccess = voiLutModule.getWindowCenter();

      assertEquals(firstAccess, secondAccess);
      assertSame(firstAccess, secondAccess); // Should be the same instance
    }
  }

  // Utility methods for creating test data
  private static Attributes createAttributesWithWindowValues(
      double[] centers, double[] widths, String voiLutFunction, String[] explanations) {
    var attributes = new Attributes();

    if (centers != null) {
      attributes.setDouble(Tag.WindowCenter, VR.DS, centers);
    }
    if (widths != null) {
      attributes.setDouble(Tag.WindowWidth, VR.DS, widths);
    }
    if (voiLutFunction != null) {
      attributes.setString(Tag.VOILUTFunction, VR.CS, voiLutFunction);
    }
    if (explanations != null) {
      attributes.setString(Tag.WindowCenterWidthExplanation, VR.LO, explanations);
    }

    return attributes;
  }

  private static byte[] createLutData(int entries) {
    var data = new byte[entries * 2]; // 16-bit entries
    for (int i = 0; i < entries; i++) {
      // Create a simple linear mapping
      int value = (i * 65535) / (entries - 1);
      data[i * 2] = (byte) (value & 0xFF);
      data[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
    }
    return data;
  }
}
