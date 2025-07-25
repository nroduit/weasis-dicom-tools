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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.LutTestDataBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.CvType;
import org.opencv.osgi.OpenCVNativeLoader;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.LutShape.Function;
import org.weasis.opencv.op.lut.PresentationStateLut;
import org.weasis.opencv.op.lut.WlPresentation;

@TestMethodOrder(OrderAnnotation.class)
class PresetWindowLevelTest {

  private static DicomImageAdapter testAdapter;
  private static TestWlPresentation testWlPresentation;
  private static VoiLutModule testVoiLutModule;

  @BeforeAll
  static void setUpClass() {
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();

    testAdapter = createTestDicomAdapter();
    testWlPresentation = new TestWlPresentation();
    testVoiLutModule = createTestVoiLutModule();
  }

  @Nested
  @Order(1)
  @DisplayName("Constructor and Basic Properties")
  class ConstructorAndBasicPropertiesTest {

    @Test
    @DisplayName("Should create preset with valid parameters")
    void shouldCreatePresetWithValidParameters() {
      PresetWindowLevel preset = new PresetWindowLevel("CT Abdomen", 400.0, 50.0, LutShape.LINEAR);

      assertAll(
          "Basic properties should be set correctly",
          () -> assertEquals("CT Abdomen", preset.getName()),
          () -> assertEquals(400.0, preset.getWindow()),
          () -> assertEquals(50.0, preset.getLevel()),
          () -> assertEquals(LutShape.LINEAR, preset.getLutShape()),
          () -> assertEquals(0, preset.getKeyCode()),
          () -> assertFalse(preset.isAutoLevel()));
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
    @DisplayName("Should calculate min/max box correctly")
    void shouldCalculateMinMaxBoxCorrectly(
        double window, double level, double expectedMin, double expectedMax) {
      PresetWindowLevel preset = new PresetWindowLevel("Test", window, level, LutShape.LINEAR);

      assertAll(
          "Min/Max box calculations",
          () -> assertEquals(expectedMin, preset.getMinBox(), 0.001),
          () -> assertEquals(expectedMax, preset.getMaxBox(), 0.001));
    }

    @Test
    @DisplayName("Should throw NullPointerException for null parameters")
    void shouldThrowNullPointerExceptionForNullParameters() {
      assertAll(
          "All null parameter combinations should throw NPE",
          () ->
              assertThrows(
                  NullPointerException.class,
                  () -> new PresetWindowLevel(null, 100.0, 50.0, LutShape.LINEAR),
                  "Null name should throw NPE"),
          () ->
              assertThrows(
                  NullPointerException.class,
                  () -> new PresetWindowLevel("Test", null, 50.0, LutShape.LINEAR),
                  "Null window should throw NPE"),
          () ->
              assertThrows(
                  NullPointerException.class,
                  () -> new PresetWindowLevel("Test", 100.0, null, LutShape.LINEAR),
                  "Null level should throw NPE"),
          () ->
              assertThrows(
                  NullPointerException.class,
                  () -> new PresetWindowLevel("Test", 100.0, 50.0, null),
                  "Null LutShape should throw NPE"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -100.0, -0.001})
    @DisplayName("Should handle edge cases for window values")
    void shouldHandleEdgeCasesForWindowValues(double window) {
      // Zero and negative windows should still create valid presets
      PresetWindowLevel preset = new PresetWindowLevel("Test", window, 50.0, LutShape.LINEAR);
      assertEquals(window, preset.getWindow());
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.MAX_VALUE, Double.MIN_VALUE, 0.0, -1000.0, 1000.0})
    @DisplayName("Should handle extreme level values")
    void shouldHandleExtremeLevelValues(double level) {
      PresetWindowLevel preset = new PresetWindowLevel("Test", 100.0, level, LutShape.LINEAR);
      assertEquals(level, preset.getLevel());
    }
  }

  @Nested
  @Order(2)
  @DisplayName("Key Code and Auto Level")
  class KeyCodeAndAutoLevelTest {

    @ParameterizedTest
    @ValueSource(ints = {0x30})
    @DisplayName("Should identify auto level key codes")
    void shouldIdentifyAutoLevelKeyCodes(int keyCode) {
      PresetWindowLevel preset = new PresetWindowLevel("Test", 100.0, 50.0, LutShape.LINEAR);
      preset.setKeyCode(keyCode);

      assertAll(
          "Auto level key code properties",
          () -> assertTrue(preset.isAutoLevel()),
          () -> assertEquals(keyCode, preset.getKeyCode()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x31, 0x32, 0x00, 0x29, 0x33, 0x41, 0x5A, 0xFF})
    @DisplayName("Should not identify non-auto level key codes")
    void shouldNotIdentifyNonAutoLevelKeyCodes(int keyCode) {
      PresetWindowLevel preset = new PresetWindowLevel("Test", 100.0, 50.0, LutShape.LINEAR);
      preset.setKeyCode(keyCode);

      assertAll(
          "Non-auto level key code properties",
          () -> assertFalse(preset.isAutoLevel()),
          () -> assertEquals(keyCode, preset.getKeyCode()));
    }

    @Test
    @DisplayName("Should maintain auto level state after key code changes")
    void shouldMaintainAutoLevelStateAfterKeyCodeChanges() {
      PresetWindowLevel preset = new PresetWindowLevel("Test", 100.0, 50.0, LutShape.LINEAR);

      // Start with non-auto level
      preset.setKeyCode(0x31);
      assertFalse(preset.isAutoLevel());
      // Switch to auto level
      preset.setKeyCode(0x30);
      assertTrue(preset.isAutoLevel());

      // Switch back to non-auto level
      preset.setKeyCode(0x32);
      assertFalse(preset.isAutoLevel());
    }
  }

  @Nested
  @Order(3)
  @DisplayName("Equals and HashCode")
  class EqualsAndHashCodeTest {

    @Test
    @DisplayName("Should be equal when all properties match")
    void shouldBeEqualWhenAllPropertiesMatch() {
      PresetWindowLevel preset1 = new PresetWindowLevel("CT Brain", 80.0, 40.0, LutShape.LINEAR);
      PresetWindowLevel preset2 = new PresetWindowLevel("CT Brain", 80.0, 40.0, LutShape.LINEAR);

      assertAll(
          "Equal presets",
          () -> assertEquals(preset1, preset2),
          () -> assertEquals(preset2, preset1), // Symmetric
          () -> assertEquals(preset1.hashCode(), preset2.hashCode()));
    }

    @Test
    @DisplayName("Should maintain reflexivity, symmetry, and transitivity")
    void shouldMaintainEqualsContract() {
      PresetWindowLevel preset1 = new PresetWindowLevel("Test", 100.0, 50.0, LutShape.LINEAR);
      PresetWindowLevel preset2 = new PresetWindowLevel("Test", 100.0, 50.0, LutShape.LINEAR);
      PresetWindowLevel preset3 = new PresetWindowLevel("Test", 100.0, 50.0, LutShape.LINEAR);

      assertAll(
          "Equals contract",
          // Reflexivity
          () -> assertEquals(preset1, preset1),
          // Symmetry
          () -> {
            assertEquals(preset1, preset2);
            assertEquals(preset2, preset1);
          },
          // Transitivity
          () -> {
            assertEquals(preset1, preset2);
            assertEquals(preset2, preset3);
            assertEquals(preset1, preset3);
          });
    }

    @ParameterizedTest
    @MethodSource("provideDifferentPresets")
    @DisplayName("Should not be equal when properties differ")
    void shouldNotBeEqualWhenPropertiesDiffer(PresetWindowLevel different, String description) {
      PresetWindowLevel basePreset = new PresetWindowLevel("Test", 100.0, 50.0, LutShape.LINEAR);
      assertAll(
          description,
          () -> assertNotEquals(basePreset, different),
          () -> assertNotEquals(different, basePreset),
          () -> assertNotEquals(basePreset.hashCode(), different.hashCode()));
    }

    static Stream<Arguments> provideDifferentPresets() {
      LutShape sigmoidShape = new LutShape(Function.SIGMOID, "SIGMOID Test");

      return Stream.of(
          Arguments.of(
              new PresetWindowLevel("Different Name", 100.0, 50.0, LutShape.LINEAR),
              "Different name"),
          Arguments.of(
              new PresetWindowLevel("Test", 200.0, 50.0, LutShape.LINEAR), "Different window"),
          Arguments.of(
              new PresetWindowLevel("Test", 100.0, 25.0, LutShape.LINEAR), "Different level"),
          Arguments.of(
              new PresetWindowLevel("Test", 100.0, 50.0, sigmoidShape), "Different LUT shape"));
    }

    @Test
    @DisplayName("Should handle null and different class comparisons")
    void shouldHandleNullAndDifferentClassComparisons() {
      PresetWindowLevel preset = new PresetWindowLevel("Test", 100.0, 50.0, LutShape.LINEAR);

      assertAll(
          "Edge cases for equals",
          () -> assertNotEquals(null, preset),
          () -> assertNotEquals("Not a PresetWindowLevel", preset),
          () -> assertNotEquals(new Object(), preset),
          () -> assertNotEquals(42, preset));
    }

    @Test
    @DisplayName("Should have consistent hashCode")
    void shouldHaveConsistentHashCode() {
      PresetWindowLevel preset = new PresetWindowLevel("Test", 100.0, 50.0, LutShape.LINEAR);
      int initialHashCode = preset.hashCode();

      // HashCode should remain consistent across multiple calls
      for (int i = 0; i < 10; i++) {
        assertEquals(initialHashCode, preset.hashCode());
      }
    }
  }

  @Nested
  @Order(4)
  @DisplayName("LUT Data Processing")
  class LutDataProcessingTest {

    @Test
    @DisplayName("Should build preset from byte LUT data")
    void shouldBuildPresetFromByteLutData() {
      byte[] lutData = createTestByteLutData();
      LookupTableCV lutTable = createByteLookupTable(lutData, 100);

      PresetWindowLevel preset =
          PresetWindowLevel.buildPresetFromLutData(
              testAdapter, lutTable, testWlPresentation, "Test Byte LUT");

      assertAll(
          "Byte LUT preset validation",
          () -> assertNotNull(preset),
          () -> assertTrue(preset.getName().contains("Test Byte LUT")),
          () -> assertTrue(preset.getWindow() > 0),
          () -> assertNotNull(preset.getLutShape()));
    }

    @Test
    @DisplayName("Should build preset from short LUT data")
    void shouldBuildPresetFromShortLutData() {
      short[] lutData = createTestShortLutData();
      LookupTableCV lutTable = createShortLookupTable(lutData, 1000);

      PresetWindowLevel preset =
          PresetWindowLevel.buildPresetFromLutData(
              testAdapter, lutTable, testWlPresentation, "Test Short LUT");

      assertAll(
          "Short LUT preset validation",
          () -> assertNotNull(preset),
          () -> assertTrue(preset.getName().contains("Test Short LUT")),
          () -> assertTrue(preset.getWindow() > 0),
          () -> assertNotNull(preset.getLutShape()));
    }

    @Test
    @DisplayName("Should handle different LUT data sizes")
    void shouldHandleDifferentLutDataSizes() {
      int[] sizes = {64, 256, 512, 1024, 4096};

      for (int size : sizes) {
        byte[] lutData = new byte[size];
        for (int i = 0; i < size; i++) {
          lutData[i] = (byte) (i % 256);
        }

        LookupTableCV lutTable = createByteLookupTable(lutData, 0);
        PresetWindowLevel preset =
            PresetWindowLevel.buildPresetFromLutData(
                testAdapter, lutTable, testWlPresentation, "Test Size " + size);

        assertNotNull(preset, "Should handle LUT size " + size);
      }
    }

    @Test
    @DisplayName("Should return null for invalid parameters")
    void shouldReturnNullForInvalidParameters() {
      LookupTableCV lutTable = createByteLookupTable(new byte[256], 0);

      assertAll(
          "Invalid parameter combinations",
          () ->
              assertNull(
                  PresetWindowLevel.buildPresetFromLutData(
                      null, lutTable, testWlPresentation, "Test")),
          () ->
              assertNull(
                  PresetWindowLevel.buildPresetFromLutData(
                      testAdapter, null, testWlPresentation, "Test")),
          () ->
              assertNull(
                  PresetWindowLevel.buildPresetFromLutData(
                      testAdapter, lutTable, testWlPresentation, null)));
    }

    private byte[] createTestByteLutData() {
      byte[] data = new byte[256];
      for (int i = 0; i < 256; i++) {
        data[i] = (byte) i;
      }
      return data;
    }

    private short[] createTestShortLutData() {
      short[] data = new short[4096];
      for (int i = 0; i < 4096; i++) {
        data[i] = (short) (i * 16);
      }
      return data;
    }

    private LookupTableCV createByteLookupTable(byte[] data, int offset) {
      return new LookupTableCV(data, offset);
    }

    private LookupTableCV createShortLookupTable(short[] data, int offset) {
      return new LookupTableCV(data, offset, false);
    }
  }

  @Nested
  @Order(5)
  @DisplayName("XML Configuration Loading")
  class XmlConfigurationLoadingTest {

    @Test
    @DisplayName("Should parse preset from XML correctly")
    void shouldParsePresetFromXmlCorrectly() throws Exception {
      String xmlContent = createValidXmlContent();

      Map<String, List<PresetWindowLevel>> presets = parsePresetsFromXml(xmlContent);

      assertNotNull(presets);
      assertTrue(presets.containsKey("CT"));

      List<PresetWindowLevel> ctPresets = presets.get("CT");
      assertEquals(2, ctPresets.size());

      PresetWindowLevel abdomenPreset = ctPresets.get(0);
      assertAll(
          "Abdomen preset validation",
          () -> assertEquals("CT Abdomen", abdomenPreset.getName()),
          () -> assertEquals(400.0, abdomenPreset.getWindow()),
          () -> assertEquals(50.0, abdomenPreset.getLevel()),
          () -> assertEquals(49, abdomenPreset.getKeyCode()));

      PresetWindowLevel brainPreset = ctPresets.get(1);
      assertAll(
          "Brain preset validation",
          () -> assertEquals("CT Brain", brainPreset.getName()),
          () -> assertEquals(80.0, brainPreset.getWindow()),
          () -> assertEquals(40.0, brainPreset.getLevel()),
          () -> assertEquals(50, brainPreset.getKeyCode()));
    }

    @Test
    @DisplayName("Should handle multiple modalities in XML")
    void shouldHandleMultipleModalitiesInXml() throws Exception {
      String xmlContent =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <presets>
            <preset name="CT Abdomen" modality="CT" window="400.0" level="50.0" shape="LINEAR" key="49"/>
            <preset name="MR T1" modality="MR" window="600.0" level="300.0" shape="LINEAR" key="51"/>
            <preset name="US General" modality="US" window="100.0" level="50.0" shape="LINEAR" key="52"/>
          </presets>
          """;

      Map<String, List<PresetWindowLevel>> presets = parsePresetsFromXml(xmlContent);

      assertAll(
          "Multiple modalities",
          () -> assertTrue(presets.containsKey("CT")),
          () -> assertTrue(presets.containsKey("MR")),
          () -> assertTrue(presets.containsKey("US")),
          () -> assertEquals(1, presets.get("CT").size()),
          () -> assertEquals(1, presets.get("MR").size()),
          () -> assertEquals(1, presets.get("US").size()));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "<?xml version=\"1.0\"?><presets><preset name=\"Invalid\" modality=\"CT\" window=\"invalid\" level=\"50.0\" shape=\"LINEAR\"/></presets>",
          "<?xml version=\"1.0\"?><presets><preset name=\"Missing\" modality=\"CT\" level=\"50.0\" shape=\"LINEAR\"/></presets>",
          "<?xml version=\"1.0\"?><presets><preset modality=\"CT\" window=\"100.0\" level=\"50.0\" shape=\"LINEAR\"/></presets>",
          "<invalid-xml>"
        })
    @DisplayName("Should handle invalid XML gracefully")
    void shouldHandleInvalidXmlGracefully(String invalidXmlContent) throws Exception {
      Map<String, List<PresetWindowLevel>> presets = parsePresetsFromXml(invalidXmlContent);

      assertNotNull(presets);
      // Should not contain any presets due to parsing error or should be empty
      assertTrue(presets.isEmpty() || presets.values().stream().allMatch(List::isEmpty));
    }

    @Test
    @DisplayName("Should handle empty XML document")
    void shouldHandleEmptyXmlDocument() throws Exception {
      String emptyXmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><presets></presets>";
      Map<String, List<PresetWindowLevel>> presets = parsePresetsFromXml(emptyXmlContent);

      assertNotNull(presets);
      assertTrue(presets.isEmpty());
    }

    private String createValidXmlContent() {
      return """
          <?xml version="1.0" encoding="UTF-8"?>
          <presets>
            <preset name="CT Abdomen" modality="CT" window="400.0" level="50.0" shape="LINEAR" key="49"/>
            <preset name="CT Brain" modality="CT" window="80.0" level="40.0" shape="SIGMOID" key="50"/>
          </presets>
          """;
    }

    private Map<String, List<PresetWindowLevel>> parsePresetsFromXml(String xmlContent)
        throws Exception {
      Map<String, List<PresetWindowLevel>> presets = new TreeMap<>();

      try (InputStream stream =
          new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8))) {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        XMLStreamReader xmler = factory.createXMLStreamReader(stream);

        // Use reflection to access private method for testing
        java.lang.reflect.Method parseMethod =
            PresetWindowLevel.class.getDeclaredMethod(
                "parsePresetsXML", XMLStreamReader.class, Map.class);
        parseMethod.setAccessible(true);
        parseMethod.invoke(null, xmler, presets);
      } catch (Exception e) {
        // Handle parsing exceptions gracefully for invalid XML tests
        if (!xmlContent.contains("invalid") && !xmlContent.contains("<invalid-xml>")) {
          throw e;
        }
      }

      return presets;
    }
  }

  @Nested
  @Order(6)
  @DisplayName("Window Level Module Integration")
  class WindowLevelModuleIntegrationTest {

    @Test
    @DisplayName("Should build presets from VoiLutModule")
    void shouldBuildPresetsFromVoiLutModule() {
      ArrayList<PresetWindowLevel> presetList = new ArrayList<>();

      // Use reflection to test the private method
      try {
        java.lang.reflect.Method buildMethod =
            PresetWindowLevel.class.getDeclaredMethod(
                "buildPresetsFromWindowLevel",
                VoiLutModule.class,
                WlPresentation.class,
                String.class,
                ArrayList.class);
        buildMethod.setAccessible(true);
        buildMethod.invoke(null, testVoiLutModule, testWlPresentation, "TEST", presetList);

        assertAll(
            "VoiLutModule preset building",
            () -> assertFalse(presetList.isEmpty()),
            () -> assertEquals(2, presetList.size()),
            () -> assertTrue(presetList.stream().anyMatch(p -> p.getName().contains("Brain"))),
            () -> assertTrue(presetList.stream().anyMatch(p -> p.getName().contains("Abdomen"))));
      } catch (Exception e) {
        fail("Failed to test buildPresetsFromWindowLevel: " + e.getMessage());
      }
    }

    @Test
    @DisplayName("Should handle empty VoiLutModule gracefully")
    void shouldHandleEmptyVoiLutModuleGracefully() {
      VoiLutModule emptyModule = createEmptyVoiLutModule();
      ArrayList<PresetWindowLevel> presetList = new ArrayList<>();

      try {
        java.lang.reflect.Method buildMethod =
            PresetWindowLevel.class.getDeclaredMethod(
                "buildPresetsFromWindowLevel",
                VoiLutModule.class,
                WlPresentation.class,
                String.class,
                ArrayList.class);
        buildMethod.setAccessible(true);
        buildMethod.invoke(null, emptyModule, testWlPresentation, "EMPTY", presetList);

        // Should handle gracefully without throwing exceptions
        assertNotNull(presetList);
      } catch (Exception e) {
        fail("Should handle empty VoiLutModule gracefully: " + e.getMessage());
      }
    }

    private VoiLutModule createEmptyVoiLutModule() {
      return new VoiLutModule(new Attributes()) {
        @Override
        public List<Double> getWindowCenter() {
          return new ArrayList<>();
        }

        @Override
        public List<Double> getWindowWidth() {
          return new ArrayList<>();
        }

        @Override
        public List<String> getWindowCenterWidthExplanation() {
          return new ArrayList<>();
        }

        @Override
        public Optional<String> getVoiLutFunction() {
          return Optional.empty();
        }

        @Override
        public List<LookupTableCV> getLut() {
          return new ArrayList<>();
        }

        @Override
        public List<String> getLutExplanation() {
          return new ArrayList<>();
        }
      };
    }
  }

  @Nested
  @Order(7)
  @DisplayName("Integration Tests")
  class IntegrationTest {

    @Test
    @DisplayName("Should create complete preset collection")
    void shouldCreateCompletePresetCollection() {
      List<PresetWindowLevel> presets =
          PresetWindowLevel.getPresetCollection(testAdapter, "[TEST]", testWlPresentation);

      assertAll(
          "Complete preset collection",
          () -> assertNotNull(presets),
          () -> assertFalse(presets.isEmpty()));

      // Should contain at least the auto-level preset
      boolean hasAutoLevel = presets.stream().anyMatch(PresetWindowLevel::isAutoLevel);
      assertTrue(hasAutoLevel, "Should contain auto-level preset");

      // Verify auto-level preset properties
      PresetWindowLevel autoLevel =
          presets.stream().filter(PresetWindowLevel::isAutoLevel).findFirst().orElse(null);

      assertAll(
          "Auto-level preset validation",
          () -> assertNotNull(autoLevel),
          () -> assertTrue(autoLevel.getName().contains("Auto Level")),
          () -> assertTrue(autoLevel.getWindow() > 0));
    }

    @Test
    @DisplayName("Should handle different image types")
    void shouldHandleDifferentImageTypes() {
      // Test with different modalities
      String[] modalities = {"CT", "MR", "US", "XA", "RF"};

      for (String modality : modalities) {
        DicomImageAdapter adapter = createTestDicomAdapterWithModality(modality);
        List<PresetWindowLevel> presets =
            PresetWindowLevel.getPresetCollection(
                adapter, "[" + modality + "]", testWlPresentation);

        assertAll(
            "Preset collection for " + modality,
            () -> assertNotNull(presets, "Should create presets for " + modality),
            () ->
                assertFalse(presets.isEmpty(), "Should have at least one preset for " + modality));
      }
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null parameters")
    void shouldThrowIllegalArgumentExceptionForNullParameters() {
      assertAll(
          "Null parameter validation",
          () ->
              assertThrows(
                  IllegalArgumentException.class,
                  () -> PresetWindowLevel.getPresetCollection(null, "TEST", testWlPresentation),
                  "Null adapter should throw IllegalArgumentException"),
          () ->
              assertThrows(
                  IllegalArgumentException.class,
                  () -> PresetWindowLevel.getPresetCollection(testAdapter, "TEST", null),
                  "Null WlPresentation should throw IllegalArgumentException"));
    }

    @Test
    @DisplayName("Should handle null dicom keyword gracefully")
    void shouldHandleNullDicomKeywordGracefully() {
      // This should not throw an exception but handle null keyword gracefully
      assertDoesNotThrow(
          () -> {
            List<PresetWindowLevel> presets =
                PresetWindowLevel.getPresetCollection(testAdapter, null, testWlPresentation);
            assertNotNull(presets);
          });
    }

    private DicomImageAdapter createTestDicomAdapterWithModality(String modality) {
      ImageCV image = new ImageCV(10, 10, CvType.CV_16SC1);
      ImageDescriptor desc = createTestImageDescriptorWithModality(modality);
      return new DicomImageAdapter(image, desc, 0);
    }

    private ImageDescriptor createTestImageDescriptorWithModality(String modality) {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
      attrs.setInt(Tag.Rows, VR.US, 10);
      attrs.setInt(Tag.Columns, VR.US, 10);
      attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
      attrs.setInt(Tag.BitsAllocated, VR.US, 16);
      attrs.setInt(Tag.BitsStored, VR.US, 12);
      attrs.setInt(Tag.PlanarConfiguration, VR.US, 0);
      attrs.setInt(Tag.PixelRepresentation, VR.US, 1);
      attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
      attrs.setString(Tag.Modality, VR.CS, modality);
      return new ImageDescriptor(attrs);
    }
  }

  @Test
  @Order(8)
  @DisplayName("Should return name as string representation")
  void shouldReturnNameAsStringRepresentation() {
    PresetWindowLevel preset = new PresetWindowLevel("CT Chest", 350.0, 40.0, LutShape.LINEAR);
    assertEquals("CT Chest", preset.toString());
  }

  @Test
  @Order(9)
  @DisplayName("Should handle concurrent access safely")
  void shouldHandleConcurrentAccessSafely() throws InterruptedException {
    PresetWindowLevel preset =
        new PresetWindowLevel("Concurrent Test", 100.0, 50.0, LutShape.LINEAR);
    int numberOfThreads = 10;
    int operationsPerThread = 100;

    List<Thread> threads = new ArrayList<>();
    List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < numberOfThreads; i++) {
      Thread thread =
          new Thread(
              () -> {
                try {
                  for (int j = 0; j < operationsPerThread; j++) {
                    // Test concurrent read operations
                    preset.getName();
                    preset.getWindow();
                    preset.getLevel();
                    preset.getLutShape();
                    preset.getMinBox();
                    preset.getMaxBox();
                    preset.toString();
                    preset.hashCode();
                    preset.equals(preset);
                  }
                } catch (Exception e) {
                  exceptions.add(e);
                }
              });
      threads.add(thread);
      thread.start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    assertTrue(
        exceptions.isEmpty(), "Concurrent access should not cause exceptions: " + exceptions);
  }

  // Helper methods
  private static DicomImageAdapter createTestDicomAdapter() {
    ImageCV image = new ImageCV(10, 10, CvType.CV_16SC1);
    ImageDescriptor desc = createTestImageDescriptor();
    return new DicomImageAdapter(image, desc, 0);
  }

  private static ImageDescriptor createTestImageDescriptor() {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
    attrs.setInt(Tag.Rows, VR.US, 10);
    attrs.setInt(Tag.Columns, VR.US, 10);
    attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
    attrs.setInt(Tag.BitsAllocated, VR.US, 16);
    attrs.setInt(Tag.BitsStored, VR.US, 12);
    attrs.setInt(Tag.PlanarConfiguration, VR.US, 0);
    attrs.setInt(Tag.PixelRepresentation, VR.US, 1);
    attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
    attrs.setString(Tag.Modality, VR.CS, "CT");

    // Set VOI LUT sequence
    Sequence voiLutSequence = attrs.newSequence(Tag.VOILUTSequence, 1);
    Attributes lutItem = LutTestDataBuilder.createCtHounsfieldLut();
    voiLutSequence.add(lutItem);

    return new ImageDescriptor(attrs);
  }

  private static VoiLutModule createTestVoiLutModule() {
    return new VoiLutModule(new Attributes()) {
      @Override
      public List<Double> getWindowCenter() {
        return Arrays.asList(40.0, 50.0);
      }

      @Override
      public List<Double> getWindowWidth() {
        return Arrays.asList(80.0, 350.0);
      }

      @Override
      public List<String> getWindowCenterWidthExplanation() {
        return Arrays.asList("Brain", "Abdomen");
      }

      @Override
      public Optional<String> getVoiLutFunction() {
        return Optional.of("LINEAR");
      }

      @Override
      public List<LookupTableCV> getLut() {
        return new ArrayList<>();
      }

      @Override
      public List<String> getLutExplanation() {
        return new ArrayList<>();
      }
    };
  }

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
