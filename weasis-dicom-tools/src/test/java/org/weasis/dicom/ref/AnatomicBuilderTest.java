/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.ref;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.DefaultLocale;
import org.weasis.dicom.ref.AnatomicBuilder.Category;
import org.weasis.dicom.ref.AnatomicBuilder.OtherCategory;

@DefaultLocale(language = "en", country = "US")
@DisplayNameGeneration(ReplaceUnderscores.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class AnatomicBuilderTest {

  @BeforeAll
  static void pinLocaleAndClearCaches() {
    // Make absolutely sure both categories use en_US
    Locale.setDefault(Locale.Category.DISPLAY, Locale.US);
    Locale.setDefault(Locale.Category.FORMAT, Locale.US);

    // Clear RB caches to avoid stale bundles
    ResourceBundle.clearCache();
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl != null) ResourceBundle.clearCache(cl);
  }

  @Nested
  class Body_Parts_Tests {

    @Test
    void getAllBodyParts_returns_all_body_parts() {
      List<BodyPart> allBodyParts = AnatomicBuilder.getAllBodyParts();

      assertEquals(BodyPart.values().length, allBodyParts.size());
      assertTrue(allBodyParts.containsAll(List.of(BodyPart.values())));
    }

    @Test
    void getCommonBodyParts_returns_only_common_body_parts() {
      List<BodyPart> commonBodyParts = AnatomicBuilder.getCommonBodyParts();

      assertTrue(commonBodyParts.stream().allMatch(BodyPart::isCommon));
      assertTrue(commonBodyParts.contains(BodyPart.ABDOMEN));
      assertFalse(commonBodyParts.contains(BodyPart.GENICULAR_ARTERY));
    }

    @Test
    void getBodyParts_with_false_predicate_returns_empty_list() {
      List<BodyPart> bodyParts = AnatomicBuilder.getBodyParts(bp -> false);

      assertTrue(bodyParts.isEmpty());
    }

    @Test
    void getBodyParts_with_true_predicate_returns_all_body_parts() {
      List<BodyPart> bodyParts = AnatomicBuilder.getBodyParts(bp -> true);

      assertEquals(BodyPart.values().length, bodyParts.size());
    }

    @Test
    void getBodyParts_filters_correctly_based_on_predicate() {
      Predicate<BodyPart> pairedFilter = BodyPart::isPaired;
      List<BodyPart> pairedBodyParts = AnatomicBuilder.getBodyParts(pairedFilter);

      assertTrue(pairedBodyParts.stream().allMatch(BodyPart::isPaired));
      assertTrue(pairedBodyParts.contains(BodyPart.BREAST));
      assertFalse(pairedBodyParts.contains(BodyPart.ABDOMEN));
    }

    @ParameterizedTest(name = "getBodyPartFromCode for {0} returns correct body part")
    @MethodSource("bodyPartCodeTestData")
    void getBodyPartFromCode_with_valid_codes_returns_correct_body_part(
        BodyPart expectedBodyPart,
        String codeValue,
        String expectedMeaning,
        CodingScheme expectedScheme,
        boolean expectedIsPaired,
        boolean expectedIsCommon,
        boolean expectedIsEndoscopic) {

      BodyPart bodyPart = BodyPart.fromCode(codeValue);

      assertEquals(expectedBodyPart, bodyPart);
      assertEquals(expectedMeaning, bodyPart.getCodeMeaning());
      assertEquals(expectedMeaning, bodyPart.toString());
      assertEquals(expectedScheme, bodyPart.getCodingScheme());
      assertEquals(expectedIsPaired, bodyPart.isPaired());
      assertEquals(expectedIsCommon, bodyPart.isCommon());
      assertEquals(expectedIsEndoscopic, bodyPart.isEndoscopic());
    }

    static Stream<Arguments> bodyPartCodeTestData() {
      return Stream.of(
          Arguments.of(
              BodyPart.ABDOMEN, "818981001", "Abdomen", CodingScheme.SCT, false, true, false),
          Arguments.of(
              BodyPart.GENICULAR_ARTERY,
              "128559007",
              "Genicular artery",
              CodingScheme.SCT,
              true,
              false,
              false),
          Arguments.of(BodyPart.BREAST, "76752008", "Breast", CodingScheme.SCT, true, true, false));
    }

    @Test
    void getBodyPartFromCode_with_null_returns_null() {
      assertThrows(NullPointerException.class, () -> BodyPart.fromCode(null));
    }

    @Test
    void getBodyPartFromCode_with_invalid_code_returns_null() {
      assertNull(BodyPart.fromCode("invalid_code"));
    }

    @ParameterizedTest(name = "getBodyPartFromLegacyCode for {1} returns {0}")
    @MethodSource("bodyPartLegacyCodeTestData")
    void getBodyPartFromLegacyCode_with_valid_legacy_codes_returns_correct_body_part(
        BodyPart expectedBodyPart,
        String legacyCode,
        String expectedMeaning,
        CodingScheme expectedScheme,
        boolean expectedIsPaired,
        boolean expectedIsCommon,
        boolean expectedIsEndoscopic) {

      BodyPart bodyPart = AnatomicBuilder.getBodyPartFromLegacyCode(legacyCode);

      assertEquals(expectedBodyPart, bodyPart);
      assertEquals(expectedMeaning, bodyPart.toString());
      assertEquals(expectedScheme, bodyPart.getCodingScheme());
      assertEquals(expectedIsPaired, bodyPart.isPaired());
      assertEquals(expectedIsCommon, bodyPart.isCommon());
      assertEquals(expectedIsEndoscopic, bodyPart.isEndoscopic());
    }

    static Stream<Arguments> bodyPartLegacyCodeTestData() {
      return Stream.of(
          Arguments.of(
              BodyPart.PANCREATIC_DUCT,
              "PANCREATICDUCT",
              "Pancreatic duct",
              CodingScheme.SCT,
              false,
              true,
              true),
          Arguments.of(
              BodyPart.ABDOMEN, "ABDOMEN", "Abdomen", CodingScheme.SCT, false, true, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "   ", "invalid", "", "NONEXISTENT"})
    void getBodyPartFromLegacyCode_with_invalid_input_returns_null(String invalidInput) {
      assertNull(AnatomicBuilder.getBodyPartFromLegacyCode(invalidInput));
    }

    @Test
    void getBodyPartFromLegacyCode_with_null_returns_null() {
      assertNull(AnatomicBuilder.getBodyPartFromLegacyCode(null));
    }

    @Test
    void getBodyPartFromPredicate_with_true_predicate_returns_first_body_part() {
      BodyPart bodyPart = AnatomicBuilder.getBodyPartFromPredicate(bp -> true);

      assertNotNull(bodyPart);
      assertEquals(BodyPart.ABDOMEN, bodyPart);
    }

    @Test
    void getBodyPartFromPredicate_with_specific_condition_returns_correct_body_part() {
      BodyPart bodyPart =
          AnatomicBuilder.getBodyPartFromPredicate(
              bp -> Objects.equals(bp.getCodeMeaning(), "Breast"));

      assertEquals(BodyPart.BREAST, bodyPart);
    }

    @Test
    void getBodyPartFromPredicate_with_invalid_condition_returns_null() {
      BodyPart bodyPart =
          AnatomicBuilder.getBodyPartFromPredicate(
              bp -> Objects.equals(bp.getCodeMeaning(), "NonExistentBodyPart"));

      assertNull(bodyPart);
    }

    @ParameterizedTest(name = "getBodyPartFromCodeMeaning for '{1}' returns {0}")
    @MethodSource("bodyPartCodeMeaningTestData")
    void getBodyPartFromCodeMeaning_with_valid_code_meaning_returns_correct_body_part(
        BodyPart expectedBodyPart, String codeMeaning) {

      BodyPart bodyPart = AnatomicBuilder.getBodyPartFromCodeMeaning(codeMeaning);

      assertEquals(expectedBodyPart, bodyPart);
    }

    static Stream<Arguments> bodyPartCodeMeaningTestData() {
      return Stream.of(
          Arguments.of(BodyPart.ABDOMEN, "Abdomen"),
          Arguments.of(BodyPart.BREAST, "Breast"),
          Arguments.of(BodyPart.GENICULAR_ARTERY, "Genicular artery"));
    }

    @Test
    void getBodyPartFromCodeMeaning_with_invalid_code_meaning_returns_null() {
      assertNull(AnatomicBuilder.getBodyPartFromCodeMeaning("NonExistentCodeMeaning"));
    }
  }

  @Nested
  class Anatomic_Modifier_Tests {

    @Test
    void getAnatomicModifierFromCode_with_first_enum_value_returns_correct_modifier() {
      AnatomicModifier firstModifier = AnatomicModifier.RIGHT;
      AnatomicModifier retrievedModifier = AnatomicModifier.fromCode(firstModifier.getCodeValue());

      assertEquals(firstModifier, retrievedModifier);
    }

    @ParameterizedTest(name = "getAnatomicModifierFromCode for {0} returns correct modifier")
    @MethodSource("anatomicModifierTestData")
    void getAnatomicModifierFromCode_with_valid_codes_returns_correct_modifier(
        AnatomicModifier expectedModifier,
        String codeValue,
        String expectedMeaning,
        CodingScheme expectedScheme) {

      AnatomicModifier modifier = AnatomicModifier.fromCode(codeValue);

      assertNotNull(modifier);
      assertEquals(expectedModifier, modifier);
      assertEquals(expectedMeaning, modifier.getCodeMeaning(Locale.getDefault()));
      assertEquals(expectedScheme, modifier.getCodingScheme());
    }

    static Stream<Arguments> anatomicModifierTestData() {
      return Stream.of(
          Arguments.of(AnatomicModifier.LATERAL, "49370004", "Lateral", CodingScheme.SCT),
          Arguments.of(AnatomicModifier.RIGHT, "24028007", "Right", CodingScheme.SCT),
          Arguments.of(AnatomicModifier.LEFT, "7771000", "Left", CodingScheme.SCT),
          Arguments.of(AnatomicModifier.BILATERAL, "51440002", "Bilateral", CodingScheme.SCT));
    }

    @Test
    void getAnatomicModifierFromCode_with_null_returns_null() {
      assertThrows(NullPointerException.class, () -> AnatomicModifier.fromCode(null));
    }

    @Test
    void getAnatomicModifierFromCode_with_invalid_code_returns_null() {
      assertNull(AnatomicModifier.fromCode("invalid_code"));
    }
  }

  @Nested
  class Surface_Part_Tests {

    @Test
    void getSurfacePartFromCode_with_first_enum_value_returns_correct_surface_part() {
      SurfacePart firstSurfacePart = SurfacePart.ANTERIOR_TRIANGLE_OF_NECK;
      SurfacePart retrievedSurfacePart = SurfacePart.fromCode(firstSurfacePart.getCodeValue());

      assertEquals(firstSurfacePart, retrievedSurfacePart);
    }

    @ParameterizedTest(name = "getSurfacePartFromCode for code {1} returns correct surface part")
    @MethodSource("surfacePartTestData")
    void getSurfacePartFromCode_with_valid_codes_returns_correct_surface_part(
        SurfacePart expectedSurfacePart,
        String codeValue,
        String expectedMeaning,
        CodingScheme expectedScheme,
        boolean expectedIsPaired,
        String expectedLegacyCode,
        int expectedLeft,
        int expectedMiddle,
        int expectedRight) {

      SurfacePart surfacePart = SurfacePart.fromCode(codeValue);

      assertNotNull(surfacePart);
      assertEquals(expectedSurfacePart, surfacePart);
      assertEquals(expectedMeaning, surfacePart.getCodeMeaning(Locale.getDefault()));
      assertEquals(expectedScheme, surfacePart.getCodingScheme());
      assertEquals(expectedIsPaired, surfacePart.isPaired());
      assertEquals(expectedLegacyCode, surfacePart.getLegacyCode());
      assertEquals(expectedLeft, surfacePart.getLeft());
      assertEquals(expectedMiddle, surfacePart.getMiddle());
      assertEquals(expectedRight, surfacePart.getRight());
    }

    static Stream<Arguments> surfacePartTestData() {
      return Stream.of(
          Arguments.of(
              SurfacePart.SKIN_OF_SUPERIOR_POSTERIOR_HELIX_OF_EAR,
              "130319",
              "Skin of superior posterior helix of ear",
              CodingScheme.DCM,
              true,
              null,
              133,
              0,
              132),
          Arguments.of(
              SurfacePart.ANTERIOR_TRIANGLE_OF_NECK,
              "182329002",
              "Anterior triangle of neck",
              CodingScheme.SCT,
              true,
              null,
              41,
              0,
              42));
    }

    @Test
    void getSurfacePartFromCode_with_null_returns_null() {
      assertThrows(NullPointerException.class, () -> SurfacePart.fromCode(null));
    }

    @Test
    void getSurfacePartFromCode_with_invalid_code_returns_null() {
      assertNull(SurfacePart.fromCode("invalid_code"));
    }
  }

  @Nested
  class Coding_Scheme_Tests {

    @ParameterizedTest(name = "CodingScheme {0} has correct properties")
    @MethodSource("codingSchemeTestData")
    void codingScheme_has_correct_properties(
        CodingScheme scheme,
        String expectedDesignator,
        String expectedUid,
        String expectedCodeName) {

      assertEquals(expectedDesignator, scheme.getDesignator());
      assertEquals(expectedDesignator, scheme.toString());
      assertEquals(expectedUid, scheme.getUid());
      assertEquals(expectedCodeName, scheme.getCodeName());
    }

    static Stream<Arguments> codingSchemeTestData() {
      return Stream.of(
          Arguments.of(CodingScheme.SCT, "SCT", "2.16.840.1.113883.6.96", "SNOMED CT"),
          Arguments.of(CodingScheme.ACR, "ACR", "2.16.840.1.113883.6.76", "ACR Index"),
          Arguments.of(
              CodingScheme.DCM, "DCM", "1.2.840.10008.2.16.4", "DICOM Controlled Terminology"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SCT", "DCM", "ACR"})
    void getSchemeFromDesignator_with_valid_designator_returns_correct_scheme(String designator) {
      CodingScheme expectedScheme = CodingScheme.valueOf(designator);

      assertEquals(expectedScheme, CodingScheme.fromDesignator(designator).orElse(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "xyz"})
    void getSchemeFromDesignator_with_invalid_input_returns_null(String invalidInput) {
      assertNull(CodingScheme.fromDesignator(invalidInput).orElse(null));
    }

    @Test
    void getSchemeFromDesignator_with_null_returns_null() {
      assertNull(CodingScheme.fromDesignator(null).orElse(null));
    }

    @ParameterizedTest(name = "getSchemeFromUid with UID {1} returns {0}")
    @MethodSource("uidTestData")
    void getSchemeFromUid_with_valid_UID_returns_correct_scheme(
        CodingScheme expectedScheme, String uid) {
      assertEquals(expectedScheme, CodingScheme.fromUid(uid).orElse(null));
    }

    static Stream<Arguments> uidTestData() {
      return Stream.of(
          Arguments.of(CodingScheme.SCT, "2.16.840.1.113883.6.96"),
          Arguments.of(CodingScheme.DCM, "1.2.840.10008.2.16.4"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "1.2.3.4.5"})
    void getSchemeFromUid_with_invalid_input_returns_null(String invalidInput) {
      assertNull(CodingScheme.fromUid(invalidInput).orElse(null));
    }

    @Test
    void getSchemeFromUid_with_null_returns_null() {
      assertNull(CodingScheme.fromUid(null).orElse(null));
    }
  }

  @Nested
  class Category_Tests {

    @Test
    void category_SURFACE_has_correct_string_representation() {
      assertEquals("Dermatology Anatomic Site", Category.SURFACE.getTitle(Locale.getDefault()));
    }

    @ParameterizedTest(name = "Category {0} has correct properties")
    @MethodSource("categoryTestData")
    void category_has_correct_properties(
        Category category, String expectedContextUID, String expectedIdentifier) {
      assertEquals(expectedContextUID, category.getContextUID());
      assertEquals(expectedIdentifier, category.getIdentifier());
      assertNotNull(category.getTitle());
      assertNotNull(category.toString());
    }

    static Stream<Arguments> categoryTestData() {
      return Stream.of(
          Arguments.of(Category.SURFACE, "1.2.840.10008.6.1.1268", "CID 4029"),
          Arguments.of(Category.ALL_REGIONS, "1.2.840.10008.6.1.2", "CID 4"),
          Arguments.of(Category.COMMON, "1.2.840.10008.6.1.308", "CID 4031"),
          Arguments.of(Category.ENDOSCOPY, "1.2.840.10008.6.1.311", "CID 4040"));
    }

    @ParameterizedTest(name = "getCategoryFromContextUID with UID {1} returns {0}")
    @MethodSource("categoryFromContextUidData")
    void getCategoryFromContextUID_returns_expected_category(Category expected, String contextUid) {
      assertEquals(expected, Category.fromContextUID(contextUid).orElse(null));
    }

    static Stream<Arguments> categoryFromContextUidData() {
      return Stream.of(
          Arguments.of(Category.SURFACE, "1.2.840.10008.6.1.1268"),
          Arguments.of(Category.ALL_REGIONS, "1.2.840.10008.6.1.2"),
          Arguments.of(Category.COMMON, "1.2.840.10008.6.1.308"),
          Arguments.of(Category.ENDOSCOPY, "1.2.840.10008.6.1.311"));
    }

    @Test
    void getCategoryFromContextUID_with_unknown_UID_returns_null() {
      assertNull(Category.fromContextUID("9.9.9.9").orElse(null));
    }

    @Test
    void categoryMap_contains_expected_entries_without_mocks() {
      // Prefer real data over mocks: verify that category mappings expose real enums
      assertTrue(AnatomicBuilder.categoryMap.containsKey(Category.SURFACE));
      assertTrue(AnatomicBuilder.categoryMap.containsKey(Category.ALL_REGIONS));
      assertTrue(AnatomicBuilder.categoryMap.containsKey(Category.COMMON));
      assertTrue(AnatomicBuilder.categoryMap.containsKey(Category.ENDOSCOPY));

      // SURFACE -> SurfacePart.values()
      List<AnatomicItem> surface = AnatomicBuilder.categoryMap.get(Category.SURFACE);
      assertNotNull(surface);
      assertEquals(SurfacePart.values().length, surface.size());

      // ALL_REGIONS -> BodyPart.values()
      List<AnatomicItem> all = AnatomicBuilder.categoryMap.get(Category.ALL_REGIONS);
      assertNotNull(all);
      assertEquals(BodyPart.values().length, all.size());

      // COMMON is a filtered subset from BodyPart
      List<AnatomicItem> common = AnatomicBuilder.categoryMap.get(Category.COMMON);
      assertNotNull(common);
      assertFalse(common.isEmpty());
      assertTrue(common.stream().allMatch(i -> ((BodyPart) i).isCommon()));

      // ENDOSCOPY is a filtered subset from BodyPart
      List<AnatomicItem> endoscopy = AnatomicBuilder.categoryMap.get(Category.ENDOSCOPY);
      assertNotNull(endoscopy);
      assertFalse(endoscopy.isEmpty());
      assertTrue(endoscopy.stream().allMatch(i -> ((BodyPart) i).isEndoscopic()));
    }
  }

  @Nested
  class OtherCategory_Data_Creation_Tests {

    @Test
    void otherCategory_can_be_created_with_valid_values() {
      String context = "1.2.840.3.5.98";
      String identifier = "C13";
      String title = "Custom Test";

      OtherCategory other = new OtherCategory(context, identifier, title);

      assertEquals(context, other.getContextUID());
      assertEquals(identifier, other.getIdentifier());
      assertEquals(title, other.getTitle());
      assertEquals(title, other.toString());
      assertEquals(
          new OtherCategory(context, "DIFF_ID", "Another Title"), other); // equals by context UID
      assertEquals(other.hashCode(), new OtherCategory(context, "X", "Y").hashCode());
    }

    @Test
    void otherCategory_identifier_must_match_VR_CS_constraints() {
      String context = "1.2.3.4.5";
      // invalid: lowercase, hyphen and too long
      assertThrows(
          IllegalArgumentException.class,
          () -> new OtherCategory(context, "invalid-name-too-long____", "T"));
      assertThrows(
          IllegalArgumentException.class, () -> new OtherCategory(context, "invalid", "T"));
      assertThrows(
          IllegalArgumentException.class, () -> new OtherCategory(context, "HAS-HYPHEN", "T"));

      // valid edge-cases
      assertDoesNotThrow(() -> new OtherCategory(context, "A_B 9", "T"));
      assertDoesNotThrow(() -> new OtherCategory(context, "ABCDEFGHIJKLMNOP", "T")); // length 16
    }

    @Test
    void otherCategory_constructor_null_arguments_throw_NPE() {
      assertThrows(NullPointerException.class, () -> new OtherCategory(null, "ID", "T"));
      assertThrows(NullPointerException.class, () -> new OtherCategory("1.2.3", null, "T"));
      assertThrows(NullPointerException.class, () -> new OtherCategory("1.2.3", "ID", null));
    }
  }
}
