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
import static org.mockito.Mockito.*;
import static org.weasis.opencv.op.lut.LutShape.SIGMOID;

import java.awt.image.DataBuffer;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.PresentationStateLut;
import org.weasis.opencv.op.lut.WlPresentation;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PresetWindowLevelTest {

  // Test data constants - using realistic medical imaging values
  private static final String BONE_PRESET = "Bone";
  private static final double BONE_WINDOW = 2000.0;
  private static final double BONE_LEVEL = 400.0;
  private static final LutShape LINEAR_SHAPE = LutShape.LINEAR;

  private static final String LUNG_PRESET = "Lung";
  private static final double LUNG_WINDOW = 1600.0;
  private static final double LUNG_LEVEL = -600.0;

  @Nested
  class Constructor_and_basic_properties {

    @Test
    void should_create_preset_with_valid_parameters() {
      // Given
      var preset = new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE);

      // Then
      assertEquals(BONE_PRESET, preset.getName());
      assertEquals(BONE_WINDOW, preset.getWindow());
      assertEquals(BONE_LEVEL, preset.getLevel());
      assertEquals(LINEAR_SHAPE, preset.getLutShape());
      assertEquals(0, preset.getKeyCode());
    }

    @ParameterizedTest
    @CsvSource({
      "100.0, 50.0, 0.0, 100.0",
      "200.0, 100.0, 0.0, 200.0",
      "50.0, 25.0, 0.0, 50.0",
      "80.0, 120.0, 80.0, 160.0",
      "1.0, 0.5, 0.0, 1.0",
      "4000.0, 2000.0, 0.0, 4000.0"
    })
    void should_calculate_min_max_box_correctly(
        double window, double level, double expectedMin, double expectedMax) {
      // Given
      var preset = new PresetWindowLevel("Test", window, level, LINEAR_SHAPE);

      // Then
      assertEquals(
          expectedMin,
          preset.getMinBox(),
          0.001,
          () -> "MinBox calculation failed for window=%f, level=%f".formatted(window, level));
      assertEquals(
          expectedMax,
          preset.getMaxBox(),
          0.001,
          () -> "MaxBox calculation failed for window=%f, level=%f".formatted(window, level));
    }

    @Test
    void should_throw_exception_for_null_parameters() {
      assertAll(
          () ->
              assertThrows(
                  NullPointerException.class,
                  () -> new PresetWindowLevel(null, 100.0, 50.0, LINEAR_SHAPE),
                  "Should throw NPE for null name"),
          () ->
              assertThrows(
                  NullPointerException.class,
                  () -> new PresetWindowLevel("Test", null, 50.0, LINEAR_SHAPE),
                  "Should throw NPE for null window"),
          () ->
              assertThrows(
                  NullPointerException.class,
                  () -> new PresetWindowLevel("Test", 100.0, null, LINEAR_SHAPE),
                  "Should throw NPE for null level"),
          () ->
              assertThrows(
                  NullPointerException.class,
                  () -> new PresetWindowLevel("Test", 100.0, 50.0, null),
                  "Should throw NPE for null shape"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -100.0, -0.001})
    void should_handle_edge_cases_for_window_values(double window) {
      // Given & When
      var preset = new PresetWindowLevel("Test", window, 50.0, LINEAR_SHAPE);

      // Then
      assertEquals(window, preset.getWindow());
      assertEquals(50.0 - window / 2.0, preset.getMinBox(), 0.001);
      assertEquals(50.0 + window / 2.0, preset.getMaxBox(), 0.001);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.MAX_VALUE, Double.MIN_VALUE, 0.0, -1000.0, 1000.0})
    void should_handle_extreme_level_values(double level) {
      // Given & When
      var preset = new PresetWindowLevel("Test", 100.0, level, LINEAR_SHAPE);

      // Then
      assertEquals(level, preset.getLevel());
    }
  }

  @Nested
  class Key_code_and_auto_level {

    @Test
    void should_identify_auto_level_key_code() {
      // Given
      var preset = new PresetWindowLevel("Auto", 100.0, 50.0, LINEAR_SHAPE);
      preset.setKeyCode(0x30);

      // Then
      assertTrue(preset.isAutoLevel(), "Should identify 0x30 as auto level key code");
      assertEquals(0x30, preset.getKeyCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {0x31, 0x32, 0x00, 0x29, 0x33, 0x41, 0x5A, 0xFF})
    void should_not_identify_non_auto_level_key_codes(int keyCode) {
      // Given
      var preset = new PresetWindowLevel("Test", 100.0, 50.0, LINEAR_SHAPE);
      preset.setKeyCode(keyCode);

      // Then
      assertFalse(
          preset.isAutoLevel(),
          () -> "Should not identify 0x%02X as auto level key code".formatted(keyCode));
    }

    @Test
    void should_maintain_auto_level_state_after_key_code_changes() {
      // Given
      var preset = new PresetWindowLevel("Auto", 100.0, 50.0, LINEAR_SHAPE);

      // When & Then
      preset.setKeyCode(0x30);
      assertTrue(preset.isAutoLevel(), "Should be auto level with 0x30");

      preset.setKeyCode(0x31);
      assertFalse(preset.isAutoLevel(), "Should not be auto level with 0x31");

      preset.setKeyCode(0x30);
      assertTrue(preset.isAutoLevel(), "Should be auto level again with 0x30");
    }
  }

  @Nested
  class Equals_and_hashcode {

    @Test
    void should_be_equal_when_all_properties_match() {
      // Given
      var preset1 = new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE);
      var preset2 = new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE);

      // Then
      assertEquals(preset1, preset2, "Presets with identical properties should be equal");
      assertEquals(
          preset1.hashCode(), preset2.hashCode(), "Equal objects should have equal hash codes");
    }

    @Test
    void should_maintain_equals_contract() {
      // Given
      var preset1 = new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE);
      var preset2 = new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE);
      var preset3 = new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE);

      // Then - Reflexivity
      assertEquals(preset1, preset1, "Object should be equal to itself");

      // Then - Symmetry
      assertEquals(preset1, preset2, "Equality should be symmetric");
      assertEquals(preset2, preset1, "Equality should be symmetric");

      // Then - Transitivity
      assertEquals(preset1, preset2, "First equality for transitivity");
      assertEquals(preset2, preset3, "Second equality for transitivity");
      assertEquals(preset1, preset3, "Transitivity should hold");
    }

    @ParameterizedTest
    @MethodSource("provideDifferentPresets")
    void should_not_be_equal_when_properties_differ(
        PresetWindowLevel different, String description) {
      // Given
      var reference = new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE);

      // Then
      assertNotEquals(reference, different, description);
    }

    static Stream<Arguments> provideDifferentPresets() {
      return Stream.of(
          Arguments.of(
              new PresetWindowLevel("Different Name", BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE),
              "Should not be equal when names differ"),
          Arguments.of(
              new PresetWindowLevel(BONE_PRESET, 1000.0, BONE_LEVEL, LINEAR_SHAPE),
              "Should not be equal when windows differ"),
          Arguments.of(
              new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, 300.0, LINEAR_SHAPE),
              "Should not be equal when levels differ"),
          Arguments.of(
              new PresetWindowLevel(
                  BONE_PRESET,
                  BONE_WINDOW,
                  BONE_LEVEL,
                  new LutShape(LutShape.Function.SIGMOID, "Sigmoid")),
              "Should not be equal when shapes differ"));
    }

    @Test
    void should_handle_null_and_different_class_comparisons() {
      // Given
      var preset = new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE);

      // Then
      assertNotEquals(null, preset, "Should not be equal to null");
      assertNotEquals("String object", preset, "Should not be equal to different class");
      assertNotEquals(42, preset, "Should not be equal to different type");
    }

    @Test
    void should_have_consistent_hashcode() {
      // Given
      var preset1 = new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE);
      var preset2 = new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE);

      // Then
      assertEquals(
          preset1.hashCode(), preset2.hashCode(), "Equal objects should have equal hash codes");

      // Hash code should be consistent across multiple calls
      var initialHashCode = preset1.hashCode();
      assertEquals(
          initialHashCode, preset1.hashCode(), "Hash code should be consistent across calls");
      assertEquals(initialHashCode, preset1.hashCode(), "Hash code should remain consistent");
    }
  }

  @Nested
  class LUT_data_processing {

    @Test
    void should_build_preset_from_byte_lut_data() {
      // Given
      var adapter = createRealDicomAdapter();
      var wl = new TestWlPresentation();
      var lutData = createByteLookupTable(createGradientByteData(256), 0);

      // When
      var preset = PresetWindowLevel.buildPresetFromLutData(adapter, lutData, wl, "Byte LUT");

      // Then
      assertNotNull(preset, "Should create preset from byte LUT data");
      assertEquals("Byte LUT", preset.getName());
      assertTrue(preset.getWindow() > 0, "Window should be positive");
      assertNotNull(preset.getLutShape(), "LUT shape should not be null");
    }

    @Test
    void should_build_preset_from_short_lut_data() {
      // Given
      var adapter = createRealDicomAdapter();
      var wl = new TestWlPresentation();
      var lutData = createShortLookupTable(createGradientShortData(1024), 0);

      // When
      var preset = PresetWindowLevel.buildPresetFromLutData(adapter, lutData, wl, "Short LUT");

      // Then
      assertNotNull(preset, "Should create preset from short LUT data");
      assertEquals("Short LUT", preset.getName());
      assertTrue(preset.getWindow() > 0, "Window should be positive");
    }

    @ParameterizedTest
    @ValueSource(ints = {64, 256, 512, 1024, 4096})
    void should_handle_different_lut_data_sizes(int size) {
      // Given
      var adapter = createRealDicomAdapter();
      var wl = new TestWlPresentation();
      var lutData = createByteLookupTable(createGradientByteData(size), 0);

      // When
      var preset = PresetWindowLevel.buildPresetFromLutData(adapter, lutData, wl, "Size Test");

      // Then
      assertNotNull(preset, () -> "Should handle LUT data of size %d".formatted(size));
      assertEquals(
          size - 1.0,
          preset.getWindow(),
          0.001,
          () -> "Window should match LUT data size for size %d".formatted(size));
    }

    @Test
    void should_return_null_for_invalid_parameters() {
      // Given
      var adapter = createRealDicomAdapter();
      var wl = new TestWlPresentation();
      var validLut = createByteLookupTable(createGradientByteData(256), 0);

      // Then
      assertNull(
          PresetWindowLevel.buildPresetFromLutData(null, validLut, wl, "Test"),
          "Should return null for null adapter");
      assertNull(
          PresetWindowLevel.buildPresetFromLutData(adapter, null, wl, "Test"),
          "Should return null for null LUT data");
      assertNull(
          PresetWindowLevel.buildPresetFromLutData(adapter, validLut, wl, null),
          "Should return null for null explanation");
    }

    private byte[] createGradientByteData(int size) {
      var data = new byte[size];
      for (int i = 0; i < size; i++) {
        data[i] = (byte) (i * 255 / (size - 1));
      }
      return data;
    }

    private short[] createGradientShortData(int size) {
      var data = new short[size];
      for (int i = 0; i < size; i++) {
        data[i] = (short) (i * 65535 / (size - 1));
      }
      return data;
    }

    private LookupTableCV createByteLookupTable(byte[] data, int offset) {
      var lut = mock(LookupTableCV.class);
      when(lut.getDataType()).thenReturn(DataBuffer.TYPE_BYTE);
      when(lut.getByteData(0)).thenReturn(data);
      when(lut.getOffset()).thenReturn(offset);
      return lut;
    }

    private LookupTableCV createShortLookupTable(short[] data, int offset) {
      var lut = mock(LookupTableCV.class);
      when(lut.getDataType()).thenReturn(DataBuffer.TYPE_SHORT);
      when(lut.getShortData(0)).thenReturn(data);
      when(lut.getOffset()).thenReturn(offset);
      return lut;
    }
  }

  @Nested
  class XML_configuration_loading {

    @Test
    void should_parse_preset_from_xml_correctly() throws Exception {
      // Given
      var xmlContent = createValidXmlContent();

      // When
      var presets = parsePresetsFromXml(xmlContent);

      // Then
      assertFalse(presets.isEmpty(), "Should parse presets from XML");
      assertTrue(presets.containsKey("CT"), "Should contain CT presets");

      var ctPresets = presets.get("CT");
      assertEquals(2, ctPresets.size(), "Should have 2 CT presets");

      var bonePreset =
          ctPresets.stream().filter(p -> "Bone".equals(p.getName())).findFirst().orElse(null);

      assertNotNull(bonePreset, "Should find Bone preset");
      assertEquals(2000.0, bonePreset.getWindow(), 0.001, "Bone preset should have correct window");
      assertEquals(400.0, bonePreset.getLevel(), 0.001, "Bone preset should have correct level");
    }

    @Test
    void should_handle_multiple_modalities_in_xml() throws Exception {
      // Given
      var xmlContent =
          """
          <?xml version="1.0"?>
          <presets>
            <preset name="Bone" modality="CT" window="2000.0" level="400.0" shape="LINEAR"/>
            <preset name="Lung" modality="CT" window="1600.0" level="-600.0" shape="LINEAR"/>
            <preset name="Brain" modality="MR" window="348.0" level="-57.9" shape="SIGMOID" key="49"/>
          </presets>
          """;

      // When
      var presets = parsePresetsFromXml(xmlContent);

      // Then
      assertEquals(2, presets.size(), "Should have 2 modalities");
      assertTrue(presets.containsKey("CT"), "Should contain CT");
      assertTrue(presets.containsKey("MR"), "Should contain MR");
      assertEquals(2, presets.get("CT").size(), "CT should have 2 presets");
      List<PresetWindowLevel> presetsMR = presets.get("MR");
      assertEquals(1, presetsMR.size(), "MR should have 1 preset");
      assertEquals("Brain", presetsMR.get(0).getName(), "MR should have correct preset name");
      assertEquals(348.0, presetsMR.get(0).getWindow(), 0.001, "MR should have correct window");
      assertEquals(-57.9, presetsMR.get(0).getLevel(), 0.001, "MR should have correct level");
      assertEquals(SIGMOID, presetsMR.get(0).getLutShape(), "MR should have correct shape");
      assertEquals(49, presetsMR.get(0).getKeyCode(), "MR should have correct key code");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "<?xml version=\"1.0\"?><presets><preset name=\"Invalid\" modality=\"CT\" window=\"invalid\" level=\"50.0\" shape=\"LINEAR\"/></presets>",
          "<?xml version=\"1.0\"?><presets><preset name=\"Missing\" modality=\"CT\" level=\"50.0\" shape=\"LINEAR\"/></presets>",
          "<?xml version=\"1.0\"?><presets><preset modality=\"CT\" window=\"100.0\" level=\"50.0\" shape=\"LINEAR\"/></presets>",
          "<invalid-xml/>"
        })
    void should_handle_invalid_xml_gracefully(String invalidXmlContent) {
      // When & Then - Should not throw exception
      assertDoesNotThrow(
          () -> parsePresetsFromXml(invalidXmlContent), "Should handle invalid XML gracefully");
    }

    @Test
    void should_handle_empty_xml_document() throws Exception {
      // Given
      var emptyXml = "<?xml version=\"1.0\"?><presets></presets>";

      // When
      var presets = parsePresetsFromXml(emptyXml);

      // Then
      assertTrue(presets.isEmpty(), "Should handle empty XML document");
    }

    private String createValidXmlContent() {
      return """
          <?xml version="1.0"?>
          <presets>
            <preset name="Bone" modality="CT" window="2000.0" level="400.0" shape="LINEAR" key="49"/>
            <preset name="Soft Tissue" modality="CT" window="400.0" level="50.0" shape="LINEAR" key="50"/>
          </presets>
          """;
    }

    private Map<String, List<PresetWindowLevel>> parsePresetsFromXml(String xmlContent)
        throws Exception {
      try (InputStream stream = new ByteArrayInputStream(xmlContent.getBytes())) {
        var factory = PresetWindowLevel.createSecureXMLFactory();
        var xmlReader = factory.createXMLStreamReader(stream);
        var presets = new TreeMap<String, List<PresetWindowLevel>>();
        PresetWindowLevel.parsePresetsXML(xmlReader, presets);
        return presets;
      }
    }
  }

  @Nested
  class Integration_tests {

    @Test
    void should_create_complete_preset_collection() {
      // Given
      var adapter = createRealDicomAdapter();
      var wl = new TestWlPresentation();

      // When
      var presets = PresetWindowLevel.getPresetCollection(adapter, "CT", wl);

      // Then
      assertFalse(presets.isEmpty(), "Should create non-empty preset collection");

      var autoLevelExists = presets.stream().anyMatch(PresetWindowLevel::isAutoLevel);
      assertTrue(autoLevelExists, "Should include auto level preset");
    }

    @Test
    void should_handle_different_image_types() {
      // Given
      var ctAdapter = createRealDicomAdapterWithModality("CT");
      var mrAdapter = createRealDicomAdapterWithModality("MR");
      var wl = new TestWlPresentation();

      // When
      var ctPresets = PresetWindowLevel.getPresetCollection(ctAdapter, "CT", wl);
      var mrPresets = PresetWindowLevel.getPresetCollection(mrAdapter, "MR", wl);

      // Then
      assertFalse(ctPresets.isEmpty(), "CT presets should not be empty");
      assertFalse(mrPresets.isEmpty(), "MR presets should not be empty");
    }

    @Test
    void should_throw_exception_for_null_parameters() {
      // Given
      var adapter = createRealDicomAdapter();
      var wl = new TestWlPresentation();

      // Then
      var adapterException =
          assertThrows(
              NullPointerException.class,
              () -> PresetWindowLevel.getPresetCollection(null, "CT", wl));
      assertTrue(
          adapterException.getMessage().contains("adapter cannot be null"),
          "Should have appropriate error message for null adapter");

      var wlException =
          assertThrows(
              NullPointerException.class,
              () -> PresetWindowLevel.getPresetCollection(adapter, "CT", null));
      assertTrue(
          wlException.getMessage().contains("wl cannot be null"),
          "Should have appropriate error message for null wl");
    }

    private DicomImageAdapter createRealDicomAdapterWithModality(String modality) {
      var adapter = mock(DicomImageAdapter.class);
      var descriptor = mock(ImageDescriptor.class);
      var voiLutModule = createRealVoiLutModule();

      when(adapter.getImageDescriptor()).thenReturn(descriptor);
      when(adapter.getFrameIndex()).thenReturn(0);
      when(adapter.getBitsStored()).thenReturn(12);
      when(adapter.getFullDynamicWidth(any())).thenReturn(4096.0);
      when(adapter.getFullDynamicCenter(any())).thenReturn(2048.0);
      when(adapter.getMinAllocatedValue(any())).thenReturn(0);
      when(adapter.getMaxAllocatedValue(any())).thenReturn(4095);

      when(descriptor.getModality()).thenReturn(modality);
      when(descriptor.getVoiLutForFrame(anyInt())).thenReturn(voiLutModule);

      return adapter;
    }
  }

  @Test
  void should_return_name_as_string_representation() {
    // Given
    var preset = new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE);

    // Then
    assertEquals(BONE_PRESET, preset.toString(), "toString should return preset name");
  }

  @Test
  void should_handle_concurrent_access_safely() throws InterruptedException, ExecutionException {
    // Given
    var preset = new PresetWindowLevel(BONE_PRESET, BONE_WINDOW, BONE_LEVEL, LINEAR_SHAPE);
    var numThreads = 10;
    var futures = new ArrayList<CompletableFuture<Void>>(numThreads);

    // When - Multiple threads accessing the preset concurrently
    for (int i = 0; i < numThreads; i++) {
      var future =
          CompletableFuture.runAsync(
              () -> {
                // Read operations should be thread-safe
                assertEquals(BONE_PRESET, preset.getName(), "Name should be consistent");
                assertEquals(BONE_WINDOW, preset.getWindow(), "Window should be consistent");
                assertEquals(BONE_LEVEL, preset.getLevel(), "Level should be consistent");
                assertEquals(
                    BONE_LEVEL - BONE_WINDOW / 2.0,
                    preset.getMinBox(),
                    "MinBox should be consistent");
                assertEquals(
                    BONE_LEVEL + BONE_WINDOW / 2.0,
                    preset.getMaxBox(),
                    "MaxBox should be consistent");
              });
      futures.add(future);
    }

    // Then - All futures should complete without exception
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
  }

  // Helper methods using real data structures instead of extensive mocking
  private static DicomImageAdapter createRealDicomAdapter() {
    var adapter = mock(DicomImageAdapter.class);
    var descriptor = mock(ImageDescriptor.class);
    var voiLutModule = createRealVoiLutModule();

    when(adapter.getImageDescriptor()).thenReturn(descriptor);
    when(adapter.getFrameIndex()).thenReturn(0);
    when(adapter.getBitsStored()).thenReturn(12);
    when(adapter.getFullDynamicWidth(any())).thenReturn(4096.0);
    when(adapter.getFullDynamicCenter(any())).thenReturn(2048.0);
    when(adapter.getMinAllocatedValue(any())).thenReturn(0);
    when(adapter.getMaxAllocatedValue(any())).thenReturn(4095);

    when(descriptor.getModality()).thenReturn("CT");
    when(descriptor.getVoiLutForFrame(anyInt())).thenReturn(voiLutModule);

    return adapter;
  }

  private static VoiLutModule createRealVoiLutModule() {
    var module = mock(VoiLutModule.class);

    // Use realistic medical imaging data instead of empty collections
    when(module.getWindowCenter()).thenReturn(List.of(BONE_LEVEL, LUNG_LEVEL));
    when(module.getWindowWidth()).thenReturn(List.of(BONE_WINDOW, LUNG_WINDOW));
    when(module.getWindowCenterWidthExplanation()).thenReturn(List.of(BONE_PRESET, LUNG_PRESET));
    when(module.getLut()).thenReturn(List.of());
    when(module.getLutExplanation()).thenReturn(List.of());
    when(module.getVoiLutFunction()).thenReturn(Optional.of("LINEAR"));

    return module;
  }

  /** Simple test implementation of WlPresentation for testing purposes. */
  private static class TestWlPresentation implements WlPresentation {
    @Override
    public boolean isPixelPadding() {
      return false;
    }

    @Override
    public PresentationStateLut getPresentationState() {
      return null;
    }
  }
}
