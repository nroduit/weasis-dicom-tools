/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.ref;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.DefaultLocale;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.macro.Code;
import org.weasis.dicom.macro.ItemCode;
import org.weasis.dicom.ref.AnatomicBuilder.Category;

@DefaultLocale(language = "en", country = "US")
@DisplayNameGeneration(ReplaceUnderscores.class)
class AnatomicRegionTest {

  // Test data constants using real DICOM values
  private static final String CUSTOM_CODE_VALUE = "86381001";
  private static final String CUSTOM_CODE_MEANING = "Skin of trunk";
  private static final String CUSTOM_CONTEXT_UID = "1.2.840.3.5.98";
  private static final String CUSTOM_CONTEXT_IDENTIFIER = "C13";

  // Test data sets for parameterized tests
  private static final List<BodyPart> SAMPLE_BODY_PARTS =
      List.of(
          BodyPart.ABDOMEN, BodyPart.HEAD_AND_NECK, BodyPart.HEART, BodyPart.LUNG, BodyPart.BRAIN);

  private static final List<SurfacePart> SAMPLE_SURFACE_PARTS =
      List.of(SurfacePart.IRIS, SurfacePart.CORNEA, SurfacePart.SKIN, SurfacePart.HAIR);

  private static final List<AnatomicModifier> SAMPLE_MODIFIERS =
      List.of(
          AnatomicModifier.LEFT,
          AnatomicModifier.RIGHT,
          AnatomicModifier.BILATERAL,
          AnatomicModifier.SURFACE,
          AnatomicModifier.MARGINAL);

  @Nested
  class Constructor_Tests {

    @ParameterizedTest(name = "creates region with {0}")
    @EnumSource(
        value = BodyPart.class,
        names = {"ABDOMEN", "HEART", "BRAIN", "LUNG"})
    void creates_region_with_body_part(BodyPart bodyPart) {
      var region = new AnatomicRegion(bodyPart);

      assertEquals(bodyPart, region.getRegion());
      assertTrue(region.getModifiers().isEmpty());
      assertNull(region.getCategory());
    }

    @ParameterizedTest(name = "creates region with {0}")
    @EnumSource(
        value = SurfacePart.class,
        names = {"IRIS", "CORNEA", "SKIN", "HAIR"})
    void creates_region_with_surface_part(SurfacePart surfacePart) {
      var region = new AnatomicRegion(surfacePart);

      assertEquals(surfacePart, region.getRegion());
      assertTrue(region.getModifiers().isEmpty());
      assertNull(region.getCategory());
    }

    @Test
    void creates_region_with_category_and_modifiers() {
      var modifiers = Set.of(AnatomicModifier.BILATERAL, AnatomicModifier.SURFACE);

      var region = new AnatomicRegion(Category.COMMON, BodyPart.ABDOMEN, modifiers);

      assertEquals(BodyPart.ABDOMEN, region.getRegion());
      assertEquals(Category.COMMON, region.getCategory());
      assertEquals(modifiers, region.getModifiers());
    }

    @Test
    void handles_null_modifiers_gracefully() {
      var region = new AnatomicRegion(Category.COMMON, BodyPart.ABDOMEN, null);

      assertEquals(BodyPart.ABDOMEN, region.getRegion());
      assertEquals(Category.COMMON, region.getCategory());
      assertNotNull(region.getModifiers());
      assertTrue(region.getModifiers().isEmpty());
    }

    @Test
    void throws_exception_for_null_region() {
      assertThrows(NullPointerException.class, () -> new AnatomicRegion(null));
      assertThrows(
          NullPointerException.class, () -> new AnatomicRegion(Category.COMMON, null, null));
    }

    @Test
    void handles_null_category_gracefully() {
      var modifiers = Set.of(AnatomicModifier.LEFT);

      var region = new AnatomicRegion(null, BodyPart.HEAD_AND_NECK, modifiers);

      assertEquals(BodyPart.HEAD_AND_NECK, region.getRegion());
      assertNull(region.getCategory());
      assertEquals(modifiers, region.getModifiers());
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void creates_region_with_all_standard_categories(Category category) {
      var region = new AnatomicRegion(category, BodyPart.ABDOMEN, Set.of());

      assertEquals(BodyPart.ABDOMEN, region.getRegion());
      assertEquals(category, region.getCategory());
    }
  }

  @Nested
  class Modifier_Management_Tests {

    private AnatomicRegion region;

    @BeforeEach
    void setUp() {
      region = new AnatomicRegion(BodyPart.ABDOMEN);
    }

    @ParameterizedTest(name = "adds {0} modifier correctly")
    @EnumSource(
        value = AnatomicModifier.class,
        names = {"LEFT", "RIGHT", "BILATERAL", "SURFACE", "MARGINAL"})
    void adds_modifier_correctly(AnatomicModifier modifier) {
      region.addModifier(modifier);

      assertTrue(region.getModifiers().contains(modifier));
      assertEquals(1, region.getModifiers().size());
    }

    @ParameterizedTest(name = "removes {0} modifier correctly")
    @EnumSource(
        value = AnatomicModifier.class,
        names = {"LEFT", "RIGHT", "BILATERAL", "SURFACE", "MARGINAL"})
    void removes_modifier_correctly(AnatomicModifier modifier) {
      region.addModifier(modifier);
      assertTrue(region.getModifiers().contains(modifier));

      region.removeModifier(modifier);

      assertFalse(region.getModifiers().contains(modifier));
      assertTrue(region.getModifiers().isEmpty());
    }

    @Test
    void handles_multiple_modifiers() {
      var modifiers =
          List.of(AnatomicModifier.LEFT, AnatomicModifier.SURFACE, AnatomicModifier.MARGINAL);
      modifiers.forEach(region::addModifier);

      assertEquals(modifiers.size(), region.getModifiers().size());
      modifiers.forEach(modifier -> assertTrue(region.getModifiers().contains(modifier)));
    }

    @Test
    void does_not_add_duplicate_modifiers() {
      region.addModifier(AnatomicModifier.BILATERAL);
      region.addModifier(AnatomicModifier.BILATERAL);

      assertEquals(1, region.getModifiers().size());
      assertTrue(region.getModifiers().contains(AnatomicModifier.BILATERAL));
    }

    @Test
    void removing_non_existent_modifier_is_noop() {
      assertDoesNotThrow(() -> region.removeModifier(AnatomicModifier.RIGHT));
      assertTrue(region.getModifiers().isEmpty());
    }

    @Test
    void handles_null_modifier_operations_gracefully() {
      assertDoesNotThrow(() -> region.addModifier(null));
      assertDoesNotThrow(() -> region.removeModifier(null));
    }

    @TestFactory
    Stream<DynamicTest> modifier_operations_with_various_combinations() {
      return SAMPLE_MODIFIERS.stream()
          .map(
              modifier ->
                  DynamicTest.dynamicTest(
                      "Add and remove " + modifier.name(),
                      () -> {
                        var testRegion = new AnatomicRegion(BodyPart.LUNG);
                        testRegion.addModifier(modifier);
                        assertTrue(testRegion.getModifiers().contains(modifier));

                        testRegion.removeModifier(modifier);
                        assertFalse(testRegion.getModifiers().contains(modifier));
                      }));
    }
  }

  @Nested
  class DICOM_Read_Tests {

    @Test
    void returns_null_for_null_attributes() {
      assertNull(AnatomicRegion.read(null));
    }

    @Test
    void returns_null_for_empty_attributes() {
      assertNull(AnatomicRegion.read(new Attributes()));
    }

    @ParameterizedTest(name = "reads {0} from legacy body part examined")
    @MethodSource("bodyPartsWithLegacyCodes")
    void reads_anatomic_region_from_legacy_body_part_examined(BodyPart bodyPart) {
      var dcm = createAttributesWithLegacyCode(bodyPart.getLegacyCode());

      var region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertEquals(bodyPart, region.getRegion());
      assertTrue(region.getModifiers().isEmpty());
      assertNull(region.getCategory());
    }

    @Test
    void reads_body_part_with_modifiers_from_anatomic_region_sequence() {
      var dcm = new Attributes();
      var regionAttr = createCodeAttributes(BodyPart.HEAD_AND_NECK);
      var modifiers = List.of(AnatomicModifier.LEFT, AnatomicModifier.SURFACE);
      addModifiersToRegion(regionAttr, modifiers);
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      var region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertEquals(BodyPart.HEAD_AND_NECK, region.getRegion());
      assertEquals(modifiers.size(), region.getModifiers().size());
      modifiers.forEach(modifier -> assertTrue(region.getModifiers().contains(modifier)));
    }

    @Test
    void reads_surface_part_from_anatomic_region_sequence() {
      var dcm = new Attributes();
      var regionAttr = createCodeAttributes(SurfacePart.IRIS);
      addModifiersToRegion(regionAttr, List.of(AnatomicModifier.RIGHT));
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      var region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertEquals(SurfacePart.IRIS, region.getRegion());
      assertEquals(1, region.getModifiers().size());
      assertTrue(region.getModifiers().contains(AnatomicModifier.RIGHT));
    }

    @Test
    void reads_custom_other_part_with_category_and_modifiers() {
      var dcm = createAttributesWithLaterality("U"); // Unknown laterality
      var regionAttr = createOtherPartAttributes();
      addModifiersToRegion(regionAttr, List.of(AnatomicModifier.SURFACE));
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      var region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertInstanceOf(OtherPart.class, region.getRegion());
      assertEquals(CUSTOM_CODE_VALUE, region.getRegion().getCodeValue());
      assertNull(region.getRegion().getLegacyCode());
      assertEquals(CUSTOM_CODE_MEANING, region.getRegion().getCodeMeaning());
      assertFalse(region.getRegion().isPaired());
      assertEquals(CodingScheme.SCT, region.getRegion().getCodingScheme());
      assertTrue(region.getModifiers().contains(AnatomicModifier.SURFACE));
    }

    @Test
    void returns_null_when_code_value_is_missing() {
      var dcm = new Attributes();
      var regionAttr = new Attributes();
      regionAttr.setString(Tag.CodeMeaning, VR.LO, "Some meaning");
      regionAttr.setString(Tag.CodingSchemeDesignator, VR.SH, CodingScheme.SCT.getDesignator());
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      assertNull(AnatomicRegion.read(dcm));
    }

    @ParameterizedTest(name = "infers pairing from laterality {0}")
    @ValueSource(strings = {"L", "R", "B"})
    void infers_paired_from_laterality_even_without_modifiers(String laterality) {
      var dcm = createAttributesWithLaterality(laterality);
      var regionAttr = createCodeAttributes(createOtherPart(false));
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      var region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertTrue(region.getRegion().isPaired());
    }

    @ParameterizedTest(name = "handles {0} modifier for pairing determination")
    @EnumSource(
        value = AnatomicModifier.class,
        names = {"LEFT", "RIGHT", "BILATERAL"})
    void handles_laterality_modifier_in_pairing_determination(AnatomicModifier lateralityModifier) {
      var dcm = new Attributes();
      var regionAttr = createCodeAttributes(createOtherPart(false));
      addModifiersToRegion(regionAttr, List.of(lateralityModifier));
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      var region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertTrue(region.getRegion().isPaired());
    }

    @Test
    void does_not_infer_pairing_from_unknown_laterality() {
      var dcm = createAttributesWithLaterality("U");
      var regionAttr = createCodeAttributes(createOtherPart(false));
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      var region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertFalse(region.getRegion().isPaired());
    }

    private static Stream<Arguments> bodyPartsWithLegacyCodes() {
      return Stream.of(BodyPart.values())
          .filter(bp -> StringUtil.hasText(bp.getLegacyCode()))
          .limit(5) // Limit for test performance
          .map(Arguments::of);
    }
  }

  @Nested
  class DICOM_Write_Tests {

    @Test
    void handles_null_parameters_gracefully() {
      assertDoesNotThrow(() -> AnatomicRegion.write(null, null));
      assertDoesNotThrow(() -> AnatomicRegion.write(new Attributes(), null));
      assertDoesNotThrow(() -> AnatomicRegion.write(null, new AnatomicRegion(BodyPart.ABDOMEN)));
    }

    @Test
    void writes_body_part_with_legacy_code_and_modifiers() {
      var region = new AnatomicRegion(BodyPart.ABDOMEN);
      var modifiers = List.of(AnatomicModifier.LEFT, AnatomicModifier.SURFACE);
      modifiers.forEach(region::addModifier);

      var dcm = new Attributes();
      AnatomicRegion.write(dcm, region);

      assertEquals(BodyPart.ABDOMEN.getLegacyCode(), dcm.getString(Tag.BodyPartExamined));

      var written = dcm.getNestedDataset(Tag.AnatomicRegionSequence);
      assertNotNull(written);

      var code = new Code(written);
      assertEquals(BodyPart.ABDOMEN.getCodingScheme(), code.getCodingScheme());
      assertEquals(BodyPart.ABDOMEN.getCodeValue(), code.getExistingCodeValue());
      assertEquals(BodyPart.ABDOMEN.getCodeMeaning(), code.getCodeMeaning());

      var modSeq = written.getSequence(Tag.AnatomicRegionModifierSequence);
      assertNotNull(modSeq);
      assertEquals(modifiers.size(), modSeq.size());
    }

    @Test
    void writes_surface_part_without_legacy_code() {
      var region = new AnatomicRegion(SurfacePart.IRIS);

      var dcm = new Attributes();
      AnatomicRegion.write(dcm, region);

      assertFalse(StringUtil.hasText(dcm.getString(Tag.BodyPartExamined)));

      var written = dcm.getNestedDataset(Tag.AnatomicRegionSequence);
      assertNotNull(written);
      var code = new Code(written);
      assertEquals(SurfacePart.IRIS.getCodeValue(), code.getExistingCodeValue());
    }

    @Test
    void writes_custom_other_part_with_category_context() {
      var other = new OtherPart(CUSTOM_CODE_VALUE, CUSTOM_CODE_MEANING, CodingScheme.SCT, false);
      var region = new AnatomicRegion(Category.COMMON, other, Collections.emptySet());

      var dcm = new Attributes();
      AnatomicRegion.write(dcm, region);

      var written = dcm.getNestedDataset(Tag.AnatomicRegionSequence);
      var code = new Code(written);

      assertEquals(Category.COMMON.getContextUID(), code.getContextUID());
      assertEquals(Category.COMMON.getIdentifier(), code.getContextIdentifier());
    }

    @ParameterizedTest
    @EnumSource(BodyPart.class)
    void writes_region_without_modifiers(BodyPart bodyPart) {
      var region = new AnatomicRegion(bodyPart);

      var dcm = new Attributes();
      AnatomicRegion.write(dcm, region);

      var written = dcm.getNestedDataset(Tag.AnatomicRegionSequence);
      assertNotNull(written);
      assertNull(written.getSequence(Tag.AnatomicRegionModifierSequence));
    }

    @Test
    void writes_region_with_empty_modifier_set() {
      var region = new AnatomicRegion(null, BodyPart.HEAD_AND_NECK, new HashSet<>());

      var dcm = new Attributes();
      AnatomicRegion.write(dcm, region);

      var written = dcm.getNestedDataset(Tag.AnatomicRegionSequence);
      assertNotNull(written);
      assertNull(written.getSequence(Tag.AnatomicRegionModifierSequence));
    }
  }

  @Nested
  class Round_Trip_Serialization_Tests {

    @ParameterizedTest(name = "maintains integrity for {0}")
    @MethodSource("org.weasis.dicom.ref.AnatomicRegionTest#sampleBodyParts")
    void maintains_data_integrity_through_write_read_cycle_for_body_parts(BodyPart bodyPart) {
      var original = new AnatomicRegion(bodyPart);
      original.addModifier(AnatomicModifier.LEFT);

      var dcm = new Attributes();
      AnatomicRegion.write(dcm, original);
      var parsed = AnatomicRegion.read(dcm);

      assertNotNull(parsed);
      assertEquals(original.getRegion(), parsed.getRegion());
      assertEquals(original.getModifiers(), parsed.getModifiers());
    }

    @ParameterizedTest(name = "maintains integrity for {0}")
    @MethodSource("org.weasis.dicom.ref.AnatomicRegionTest#sampleSurfaceParts")
    void maintains_data_integrity_through_write_read_cycle_for_surface_parts(
        SurfacePart surfacePart) {
      var original = new AnatomicRegion(surfacePart);
      original.addModifier(AnatomicModifier.SURFACE);

      var dcm = new Attributes();
      AnatomicRegion.write(dcm, original);
      var parsed = AnatomicRegion.read(dcm);

      assertNotNull(parsed);
      assertEquals(original.getRegion().getCodeValue(), parsed.getRegion().getCodeValue());
      assertEquals(original.getModifiers(), parsed.getModifiers());
    }

    @Test
    void maintains_data_integrity_through_write_read_cycle_for_other_part() {
      var other = new OtherPart(CUSTOM_CODE_VALUE, CUSTOM_CODE_MEANING, CodingScheme.SCT, true);
      var original = new AnatomicRegion(Category.COMMON, other, Set.of(AnatomicModifier.RIGHT));

      var dcm = new Attributes();
      AnatomicRegion.write(dcm, original);
      var parsed = AnatomicRegion.read(dcm);

      assertNotNull(parsed);
      assertEquals(original.getRegion().getCodeValue(), parsed.getRegion().getCodeValue());
      assertEquals(original.getRegion().getCodeMeaning(), parsed.getRegion().getCodeMeaning());
      assertEquals(original.getRegion().isPaired(), parsed.getRegion().isPaired());
      assertEquals(original.getModifiers(), parsed.getModifiers());
    }
  }

  @Nested
  class String_Representation_Tests {

    @Test
    void formats_region_without_modifiers_correctly() {
      var region = new AnatomicRegion(BodyPart.ABDOMEN);

      assertEquals(BodyPart.ABDOMEN.getCodeMeaning(), region.toString());
    }

    @Test
    void formats_region_with_single_modifier_correctly() {
      var region = new AnatomicRegion(SurfacePart.IRIS);
      region.addModifier(AnatomicModifier.LEFT);

      var expected =
          SurfacePart.IRIS.getCodeMeaning() + " (" + AnatomicModifier.LEFT.getCodeMeaning() + ")";
      assertEquals(expected, region.toString());
    }

    @Test
    void formats_region_with_multiple_modifiers_correctly() {
      var region = new AnatomicRegion(BodyPart.HEAD_AND_NECK);
      region.addModifier(AnatomicModifier.LEFT);
      region.addModifier(AnatomicModifier.SURFACE);

      var result = region.toString();
      assertTrue(result.startsWith(BodyPart.HEAD_AND_NECK.getCodeMeaning()));
      assertTrue(result.contains(AnatomicModifier.LEFT.getCodeMeaning()));
      assertTrue(result.contains(AnatomicModifier.SURFACE.getCodeMeaning()));
      assertTrue(result.contains("("));
      assertTrue(result.contains(")"));
    }

    @Test
    void handles_empty_modifier_set_in_string_representation() {
      var region = new AnatomicRegion(null, BodyPart.HEAD_AND_NECK, new HashSet<>());

      assertEquals(BodyPart.HEAD_AND_NECK.getCodeMeaning(), region.toString());
    }
  }

  @Nested
  class Edge_Cases_and_Error_Handling_Tests {

    @Test
    void handles_legacy_code_lookup_correctly() {
      var dcm = createAttributesWithLegacyCode(BodyPart.HEAD_AND_NECK.getLegacyCode());

      var region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertEquals(BodyPart.HEAD_AND_NECK, region.getRegion());
    }

    @Test
    void handles_unknown_legacy_code_gracefully() {
      var dcm = createAttributesWithLegacyCode("UNKNOWN-CODE");

      assertNull(AnatomicRegion.read(dcm));
    }

    @ParameterizedTest(name = "handles {0} laterality")
    @ValueSource(strings = {"L", "R", "B", "U", "", "INVALID"})
    void handles_various_laterality_values(String laterality) {
      var dcm = createAttributesWithLaterality(laterality);
      var regionAttr = createCodeAttributes(createOtherPart(false));
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      var region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      boolean shouldBePaired = StringUtil.hasText(laterality) && !"U".equals(laterality);
      assertEquals(shouldBePaired, region.getRegion().isPaired());
    }
  }

  // ---------- Test data creation methods (prefer real data over mocks) ----------

  private static Stream<BodyPart> sampleBodyParts() {
    return SAMPLE_BODY_PARTS.stream();
  }

  private static Stream<SurfacePart> sampleSurfaceParts() {
    return SAMPLE_SURFACE_PARTS.stream();
  }

  private static Attributes createAttributesWithLegacyCode(String legacyCode) {
    var dcm = new Attributes();
    dcm.setString(Tag.BodyPartExamined, VR.CS, legacyCode);
    return dcm;
  }

  private static Attributes createAttributesWithLaterality(String laterality) {
    var dcm = new Attributes();
    dcm.setString(Tag.FrameLaterality, VR.CS, laterality);
    return dcm;
  }

  private static Attributes createCodeAttributes(ItemCode code) {
    var attrs = new Attributes();
    attrs.setString(Tag.CodeValue, VR.SH, code.getCodeValue());
    attrs.setString(Tag.CodingSchemeDesignator, VR.SH, code.getCodingScheme().getDesignator());
    attrs.setString(Tag.CodeMeaning, VR.LO, code.getCodeMeaning());
    return attrs;
  }

  private static Attributes createOtherPartAttributes() {
    var attrs = new Attributes();
    attrs.setString(Tag.CodeValue, VR.SH, CUSTOM_CODE_VALUE);
    attrs.setString(Tag.CodingSchemeDesignator, VR.SH, CodingScheme.SCT.getDesignator());
    attrs.setString(Tag.CodeMeaning, VR.LO, CUSTOM_CODE_MEANING);
    attrs.setString(Tag.ContextUID, VR.UI, CUSTOM_CONTEXT_UID);
    attrs.setString(Tag.ContextIdentifier, VR.SH, CUSTOM_CONTEXT_IDENTIFIER);
    return attrs;
  }

  private static OtherPart createOtherPart(boolean paired) {
    return new OtherPart("X-" + System.nanoTime(), "Custom Test Part", CodingScheme.SCT, paired);
  }

  private static void addModifiersToRegion(
      Attributes regionAttr, List<AnatomicModifier> modifiers) {
    if (modifiers.isEmpty()) {
      return;
    }

    var modSeq = regionAttr.newSequence(Tag.AnatomicRegionModifierSequence, modifiers.size());
    modifiers.forEach(modifier -> modSeq.add(createModifierAttributes(modifier)));
  }

  private static Attributes createModifierAttributes(AnatomicModifier modifier) {
    var attrs = new Attributes();
    attrs.setString(Tag.CodeValue, VR.SH, modifier.getCodeValue());
    attrs.setString(Tag.CodingSchemeDesignator, VR.SH, modifier.getCodingScheme().getDesignator());
    attrs.setString(Tag.CodeMeaning, VR.LO, modifier.getCodeMeaning());
    return attrs;
  }
}
