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

import java.util.HashSet;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultLocale;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.macro.Code;
import org.weasis.dicom.macro.ItemCode;
import org.weasis.dicom.ref.AnatomicBuilder.Category;

@DefaultLocale(language = "en", country = "US")
@DisplayNameGeneration(ReplaceUnderscores.class)
class AnatomicRegionTest {

  private static final String TEST_CODE_VALUE = "86381001";
  private static final String TEST_CODE_MEANING = "Skin of trunk";
  private static final String TEST_CONTEXT_UID = "1.2.840.3.5.98";
  private static final String TEST_CONTEXT_IDENTIFIER = "C13";

  @Nested
  class Constructor_Tests {

    @Test
    void creates_region_with_single_anatomic_item() {
      AnatomicItem item = SurfacePart.IRIS;

      AnatomicRegion region = new AnatomicRegion(item);

      assertEquals(item, region.getRegion());
      assertTrue(region.getModifiers().isEmpty());
      assertNull(region.getCategory());
    }

    @Test
    void creates_region_with_category_and_modifiers() {
      AnatomicItem item = BodyPart.ABDOMEN;
      Set<AnatomicModifier> modifiers = new HashSet<>();
      modifiers.add(AnatomicModifier.BILATERAL);

      AnatomicRegion region = new AnatomicRegion(Category.COMMON, item, modifiers);

      assertEquals(item, region.getRegion());
      assertEquals(Category.COMMON, region.getCategory());
      assertEquals(modifiers, region.getModifiers());
      assertTrue(region.getModifiers().contains(AnatomicModifier.BILATERAL));
    }

    @Test
    void handles_null_modifiers_gracefully() {
      AnatomicItem item = BodyPart.ABDOMEN;

      AnatomicRegion region = new AnatomicRegion(Category.COMMON, item, null);

      assertEquals(item, region.getRegion());
      assertEquals(Category.COMMON, region.getCategory());
      assertNotNull(region.getModifiers());
      assertTrue(region.getModifiers().isEmpty());
    }

    @Test
    void throws_exception_for_null_region() {
      assertThrows(NullPointerException.class, () -> new AnatomicRegion(null));
    }

    @Test
    void handles_null_category_gracefully() {
      AnatomicItem item = BodyPart.HEAD_AND_NECK;
      Set<AnatomicModifier> modifiers = Set.of(AnatomicModifier.LEFT);

      AnatomicRegion region = new AnatomicRegion(null, item, modifiers);

      assertEquals(item, region.getRegion());
      assertNull(region.getCategory());
      assertEquals(modifiers, region.getModifiers());
    }
  }

  @Nested
  class Modifier_management_Tests {

    private AnatomicRegion region;

    @BeforeEach
    void setUp() {
      region = new AnatomicRegion(BodyPart.ABDOMEN);
    }

    @Test
    void adds_modifier_correctly() {
      region.addModifier(AnatomicModifier.MARGINAL);

      assertTrue(region.getModifiers().contains(AnatomicModifier.MARGINAL));
      assertEquals(1, region.getModifiers().size());
    }

    @Test
    void removes_modifier_correctly() {
      region.addModifier(AnatomicModifier.MARGINAL);
      assertTrue(region.getModifiers().contains(AnatomicModifier.MARGINAL));

      region.removeModifier(AnatomicModifier.MARGINAL);

      assertFalse(region.getModifiers().contains(AnatomicModifier.MARGINAL));
      assertTrue(region.getModifiers().isEmpty());
    }

    @Test
    void handles_multiple_modifiers() {
      region.addModifier(AnatomicModifier.LEFT);
      region.addModifier(AnatomicModifier.SURFACE);
      region.addModifier(AnatomicModifier.MARGINAL);

      assertEquals(3, region.getModifiers().size());
      assertTrue(region.getModifiers().contains(AnatomicModifier.LEFT));
      assertTrue(region.getModifiers().contains(AnatomicModifier.SURFACE));
      assertTrue(region.getModifiers().contains(AnatomicModifier.MARGINAL));
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
    void handles_null_modifier_operations() {
      assertDoesNotThrow(() -> region.addModifier(null));
      assertDoesNotThrow(() -> region.removeModifier(null));
    }
  }

  @Nested
  class DICOM_read_Tests {

    @Test
    void returns_null_for_null_attributes() {
      assertNull(AnatomicRegion.read(null));
    }

    @Test
    void returns_null_for_empty_attributes() {
      assertNull(AnatomicRegion.read(new Attributes()));
    }

    @Test
    void reads_anatomic_region_from_legacy_body_part_examined() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.BodyPartExamined, VR.CS, BodyPart.ABDOMEN.getLegacyCode());

      AnatomicRegion region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertEquals(BodyPart.ABDOMEN, region.getRegion());
      assertTrue(region.getModifiers().isEmpty());
    }

    @Test
    void reads_body_part_with_modifiers_from_anatomic_region_sequence() {
      Attributes dcm = new Attributes();
      Attributes regionAttr = codeAttributesFor(BodyPart.HEAD_AND_NECK);
      Sequence modSeq = regionAttr.newSequence(Tag.AnatomicRegionModifierSequence, 2);
      modSeq.add(modifierAttributes(AnatomicModifier.LEFT));
      modSeq.add(modifierAttributes(AnatomicModifier.SURFACE));
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      AnatomicRegion region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertEquals(BodyPart.HEAD_AND_NECK, region.getRegion());
      assertEquals(2, region.getModifiers().size());
      assertTrue(region.getModifiers().contains(AnatomicModifier.LEFT));
      assertTrue(region.getModifiers().contains(AnatomicModifier.SURFACE));
    }

    @Test
    void reads_surface_part_from_anatomic_region_sequence() {
      Attributes dcm = new Attributes();
      Attributes regionAttr = codeAttributesFor(SurfacePart.IRIS);
      Sequence modSeq = regionAttr.newSequence(Tag.AnatomicRegionModifierSequence, 1);
      modSeq.add(modifierAttributes(AnatomicModifier.RIGHT));
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      AnatomicRegion region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertEquals(SurfacePart.IRIS, region.getRegion());
      assertEquals(1, region.getModifiers().size());
      assertTrue(region.getModifiers().contains(AnatomicModifier.RIGHT));
    }

    @Test
    void reads_custom_other_part_with_category_and_modifiers() {
      Attributes dcm = new Attributes();
      // laterality is "U" so pairing must not be inferred from laterality
      dcm.setString(Tag.FrameLaterality, VR.CS, "U");

      Attributes regionAttr = new Attributes();
      regionAttr.setString(Tag.CodeValue, VR.SH, TEST_CODE_VALUE);
      regionAttr.setString(Tag.CodingSchemeDesignator, VR.SH, CodingScheme.SCT.getDesignator());
      regionAttr.setString(Tag.CodeMeaning, VR.LO, TEST_CODE_MEANING);
      regionAttr.setString(Tag.ContextUID, VR.UI, TEST_CONTEXT_UID);
      regionAttr.setString(Tag.ContextIdentifier, VR.SH, TEST_CONTEXT_IDENTIFIER);
      Sequence modSeq = regionAttr.newSequence(Tag.AnatomicRegionModifierSequence, 1);
      modSeq.add(modifierAttributes(AnatomicModifier.SURFACE));
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      AnatomicRegion region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertEquals(TEST_CODE_VALUE, region.getRegion().getCodeValue());
      assertNull(region.getRegion().getLegacyCode());
      assertEquals(TEST_CODE_MEANING, region.getRegion().getCodeMeaning());
      assertFalse(region.getRegion().isPaired());
      assertEquals(CodingScheme.SCT, region.getRegion().getCodingScheme());
      assertTrue(region.getModifiers().contains(AnatomicModifier.SURFACE));
    }

    @Test
    void returns_null_when_code_value_is_missing() {
      Attributes dcm = new Attributes();
      Attributes regionAttr = new Attributes();
      regionAttr.setString(Tag.CodeMeaning, VR.LO, "Some meaning");
      regionAttr.setString(Tag.CodingSchemeDesignator, VR.SH, "SCT");
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      assertNull(AnatomicRegion.read(dcm));
    }

    @Test
    void infers_paired_from_laterality_even_without_modifiers() {
      Attributes dcm = new Attributes();
      Attributes regionAttr =
          codeAttributesFor(new OtherPart("X-123", "Custom", CodingScheme.SCT, false));
      // Set laterality (not Unknown) at dataset level
      dcm.setString(Tag.FrameLaterality, VR.CS, "R");
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      AnatomicRegion region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertTrue(region.getRegion().isPaired());
    }
  }

  @Nested
  class DICOM_write_Tests {

    @Test
    void handles_null_parameters_gracefully() {
      // No exception should be thrown
      assertDoesNotThrow(() -> AnatomicRegion.write(null, null));
      assertDoesNotThrow(() -> AnatomicRegion.write(new Attributes(), null));
    }

    @Test
    void writes_body_part_with_legacy_code_and_modifiers() {
      AnatomicRegion region = new AnatomicRegion(BodyPart.ABDOMEN);
      region.addModifier(AnatomicModifier.LEFT);
      region.addModifier(AnatomicModifier.SURFACE);

      Attributes dcm = new Attributes();
      AnatomicRegion.write(dcm, region);

      // BodyPartExamined should be set from legacy code
      assertEquals(BodyPart.ABDOMEN.getLegacyCode(), dcm.getString(Tag.BodyPartExamined));

      // Region sequence content
      Attributes written = dcm.getNestedDataset(Tag.AnatomicRegionSequence);
      assertNotNull(written);

      Code code = new Code(written);
      assertEquals(BodyPart.ABDOMEN.getCodingScheme(), code.getCodingScheme());
      assertEquals(BodyPart.ABDOMEN.getCodeValue(), code.getExistingCodeValue());
      assertEquals(BodyPart.ABDOMEN.getCodeMeaning(), code.getCodeMeaning());

      Sequence modSeq = written.getSequence(Tag.AnatomicRegionModifierSequence);
      assertNotNull(modSeq);
      assertEquals(2, modSeq.size());
    }

    @Test
    void writes_surface_part_without_legacy_code() {
      AnatomicRegion region = new AnatomicRegion(SurfacePart.IRIS);

      Attributes dcm = new Attributes();
      AnatomicRegion.write(dcm, region);

      // No legacy BodyPartExamined should be written
      assertFalse(StringUtil.hasText(dcm.getString(Tag.BodyPartExamined)));

      Attributes written = dcm.getNestedDataset(Tag.AnatomicRegionSequence);
      assertNotNull(written);
      Code code = new Code(written);
      assertEquals(SurfacePart.IRIS.getCodeValue(), code.getExistingCodeValue());
    }

    @Test
    void writes_custom_other_part_with_category_context() {
      OtherPart other = new OtherPart(TEST_CODE_VALUE, TEST_CODE_MEANING, CodingScheme.SCT, false);
      AnatomicRegion region = new AnatomicRegion(Category.COMMON, other, Set.of());

      Attributes dcm = new Attributes();
      AnatomicRegion.write(dcm, region);

      Attributes written = dcm.getNestedDataset(Tag.AnatomicRegionSequence);
      Code code = new Code(written);

      // Category context should be written when present
      assertEquals(Category.COMMON.getContextUID(), code.getContextUID());
      assertEquals(Category.COMMON.getIdentifier(), code.getContextIdentifier());
    }

    @Test
    void writes_region_without_modifiers() {
      AnatomicRegion region = new AnatomicRegion(BodyPart.HEAD_AND_NECK);

      Attributes dcm = new Attributes();
      AnatomicRegion.write(dcm, region);

      Attributes written = dcm.getNestedDataset(Tag.AnatomicRegionSequence);
      assertNull(written.getSequence(Tag.AnatomicRegionModifierSequence));
    }

    @Test
    void writes_region_with_empty_modifier_set() {
      AnatomicRegion region = new AnatomicRegion(null, BodyPart.HEAD_AND_NECK, new HashSet<>());

      Attributes dcm = new Attributes();
      AnatomicRegion.write(dcm, region);

      Attributes written = dcm.getNestedDataset(Tag.AnatomicRegionSequence);
      assertNull(written.getSequence(Tag.AnatomicRegionModifierSequence));
    }
  }

  @Nested
  class Round_trip_serialization_Tests {

    @Test
    void maintains_data_integrity_through_write_read_cycle_for_body_part() {
      AnatomicRegion original = new AnatomicRegion(BodyPart.ABDOMEN);
      original.addModifier(AnatomicModifier.LEFT);

      Attributes dcm = new Attributes();
      AnatomicRegion.write(dcm, original);

      AnatomicRegion parsed = AnatomicRegion.read(dcm);

      assertNotNull(parsed);
      assertEquals(original.getRegion(), parsed.getRegion());
      assertEquals(original.getModifiers(), parsed.getModifiers());
    }

    @Test
    void maintains_data_integrity_through_write_read_cycle_for_surface_part() {
      AnatomicRegion original = new AnatomicRegion(SurfacePart.IRIS);
      original.addModifier(AnatomicModifier.SURFACE);

      Attributes dcm = new Attributes();
      AnatomicRegion.write(dcm, original);

      AnatomicRegion parsed = AnatomicRegion.read(dcm);

      assertNotNull(parsed);
      assertEquals(original.getRegion(), parsed.getRegion());
      assertEquals(original.getModifiers(), parsed.getModifiers());
    }

    @Test
    void maintains_data_integrity_through_write_read_cycle_for_other_part() {
      OtherPart other = new OtherPart(TEST_CODE_VALUE, TEST_CODE_MEANING, CodingScheme.SCT, true);
      AnatomicRegion original =
          new AnatomicRegion(Category.COMMON, other, Set.of(AnatomicModifier.RIGHT));

      Attributes dcm = new Attributes();
      AnatomicRegion.write(dcm, original);

      AnatomicRegion parsed = AnatomicRegion.read(dcm);

      assertNotNull(parsed);
      assertEquals(original.getRegion().getCodeValue(), parsed.getRegion().getCodeValue());
      assertEquals(original.getRegion().getCodeMeaning(), parsed.getRegion().getCodeMeaning());
      assertEquals(original.getRegion().isPaired(), parsed.getRegion().isPaired());
      assertEquals(original.getModifiers(), parsed.getModifiers());
    }
  }

  @Nested
  class String_representation_Tests {

    @Test
    void formats_region_without_modifiers_correctly() {
      AnatomicRegion region = new AnatomicRegion(BodyPart.ABDOMEN);

      assertEquals(BodyPart.ABDOMEN.getCodeMeaning(), region.toString());
    }

    @Test
    void formats_region_with_single_modifier_correctly() {
      AnatomicRegion region = new AnatomicRegion(SurfacePart.IRIS);
      region.addModifier(AnatomicModifier.LEFT);

      assertEquals(
          SurfacePart.IRIS.getCodeMeaning() + " (" + AnatomicModifier.LEFT.getCodeMeaning() + ")",
          region.toString());
    }

    @Test
    void formats_region_with_multiple_modifiers_correctly() {
      AnatomicRegion region = new AnatomicRegion(BodyPart.HEAD_AND_NECK);
      region.addModifier(AnatomicModifier.LEFT);
      region.addModifier(AnatomicModifier.SURFACE);

      String result = region.toString();
      assertTrue(result.startsWith(BodyPart.HEAD_AND_NECK.getCodeMeaning()));
      assertTrue(result.contains(AnatomicModifier.LEFT.getCodeMeaning()));
      assertTrue(result.contains(AnatomicModifier.SURFACE.getCodeMeaning()));
    }

    @Test
    void handles_empty_modifier_set_in_string_representation() {
      AnatomicRegion region = new AnatomicRegion(null, BodyPart.HEAD_AND_NECK, new HashSet<>());

      assertEquals(BodyPart.HEAD_AND_NECK.getCodeMeaning(), region.toString());
    }
  }

  @Nested
  class Edge_cases_and_error_handling_Tests {

    @Test
    void handles_legacy_code_lookup_correctly() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.BodyPartExamined, VR.CS, BodyPart.HEAD_AND_NECK.getLegacyCode());

      AnatomicRegion region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertEquals(BodyPart.HEAD_AND_NECK, region.getRegion());
    }

    @Test
    void handles_unknown_legacy_code_gracefully() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.BodyPartExamined, VR.CS, "UNKNOWN-CODE");

      assertNull(AnatomicRegion.read(dcm));
    }

    @Test
    void handles_laterality_in_pairing_determination() {
      Attributes dcm = new Attributes();
      Attributes regionAttr =
          codeAttributesFor(new OtherPart("X-999", "Custom", CodingScheme.SCT, false));
      dcm.setString(Tag.ImageLaterality, VR.CS, "L"); // implies paired
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      AnatomicRegion region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertTrue(region.getRegion().isPaired());
    }

    @Test
    void handles_bilateral_modifier_in_pairing_determination() {
      Attributes dcm = new Attributes();
      Attributes regionAttr =
          codeAttributesFor(new OtherPart("X-888", "Custom", CodingScheme.SCT, false));
      Sequence modSeq = regionAttr.newSequence(Tag.AnatomicRegionModifierSequence, 1);
      modSeq.add(modifierAttributes(AnatomicModifier.BILATERAL));
      dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regionAttr);

      AnatomicRegion region = AnatomicRegion.read(dcm);

      assertNotNull(region);
      assertTrue(region.getRegion().isPaired());
    }
  }

  // ---------- Test data builders (prefer real data over mocks) ----------

  private static Attributes codeAttributesFor(ItemCode code) {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.CodeValue, VR.SH, code.getCodeValue());
    attrs.setString(Tag.CodingSchemeDesignator, VR.SH, code.getCodingScheme().getDesignator());
    attrs.setString(Tag.CodeMeaning, VR.LO, code.getCodeMeaning());
    return attrs;
  }

  private static Attributes modifierAttributes(AnatomicModifier modifier) {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.CodeValue, VR.SH, modifier.getCodeValue());
    attrs.setString(Tag.CodingSchemeDesignator, VR.SH, modifier.getCodingScheme().getDesignator());
    attrs.setString(Tag.CodeMeaning, VR.LO, modifier.getCodeMeaning());
    return attrs;
  }
}
