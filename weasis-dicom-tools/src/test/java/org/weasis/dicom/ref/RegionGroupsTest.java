/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.ref;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.junit.DefaultLocale;

class RegionGroupsTest {

  // Generic structures that belong to no area (the Playbook's "unspecified")
  private static final Set<BodyPart> WITHOUT_GROUP =
      Set.of(
          BodyPart.ARTERY,
          BodyPart.CARDIOVASCULAR_SYSTEM,
          BodyPart.VEIN,
          BodyPart.JOINT,
          BodyPart.INTRA_ARTICULAR,
          BodyPart.LUMEN_OF_BLOOD_VESSEL,
          BodyPart.ENDO_VASCULAR,
          BodyPart.PHANTOM,
          BodyPart.BAFFLE,
          BodyPart.BODY_CONDUIT,
          BodyPart.VASCULAR_GRAFT,
          BodyPart.VENOUS_NETWORK);

  private static final RegionGroups BUILT_IN = RegionGroups.builtIn();

  @Test
  void every_body_part_but_the_generic_structures_has_a_group() {
    List<BodyPart> missing =
        Arrays.stream(BodyPart.values())
            .filter(b -> !WITHOUT_GROUP.contains(b) && BUILT_IN.groupsOf(b).isEmpty())
            .toList();

    assertEquals(List.of(), missing);
    WITHOUT_GROUP.forEach(b -> assertTrue(BUILT_IN.groupsOf(b).isEmpty(), b.name()));
  }

  @Test
  void every_surface_part_but_skin_and_hair_has_a_group() {
    List<SurfacePart> missing =
        Arrays.stream(SurfacePart.values())
            .filter(p -> p != SurfacePart.SKIN && p != SurfacePart.HAIR)
            .filter(p -> BUILT_IN.groupsOf(p).isEmpty())
            .toList();

    assertAll(
        () -> assertEquals(List.of(), missing),
        () -> assertTrue(BUILT_IN.groupsOf(SurfacePart.SKIN).isEmpty()),
        () ->
            assertEquals(
                Set.of(RegionGroup.HEAD), BUILT_IN.groupsOf(SurfacePart.SKIN_OF_NASOLABIAL_FOLD)),
        () ->
            assertEquals(Set.of(RegionGroup.BREAST), BUILT_IN.groupsOf(SurfacePart.SKIN_OF_NIPPLE)),
        () ->
            assertEquals(
                Set.of(RegionGroup.UPPER_EXTREMITY),
                BUILT_IN.groupsOf(SurfacePart.SKIN_OF_ANTERIOR_SURFACE_OF_FOREARM)),
        () ->
            assertEquals(
                Set.of(RegionGroup.CHEST, RegionGroup.ABDOMEN, RegionGroup.PELVIS),
                BUILT_IN.groupsOf(SurfacePart.SKIN_OF_BACK_OF_TRUNK)));
  }

  @Test
  void combined_regions_belong_to_every_group_they_cover() {
    assertAll(
        () -> assertEquals(Set.of(RegionGroup.CHEST), BUILT_IN.groupsOf(BodyPart.LUNG)),
        () -> assertEquals(Set.of(RegionGroup.ABDOMEN), BUILT_IN.groupsOf(BodyPart.LIVER)),
        () ->
            assertEquals(
                Set.of(RegionGroup.NECK, RegionGroup.CHEST),
                BUILT_IN.groupsOf(BodyPart.NECK_AND_CHEST)),
        () ->
            assertEquals(
                Set.of(RegionGroup.PELVIS, RegionGroup.SPINE), BUILT_IN.groupsOf(BodyPart.SACRUM)),
        () ->
            assertEquals(Set.of(RegionGroup.WHOLE_BODY), BUILT_IN.groupsOf(BodyPart.ENTIRE_BODY)));
  }

  @Test
  void groups_follow_the_region_imaged_of_the_loinc_rsna_playbook() {
    assertAll(
        () -> assertEquals(Set.of(RegionGroup.BREAST), BUILT_IN.groupsOf(BodyPart.BREAST)),
        () ->
            assertEquals(
                Set.of(RegionGroup.ABDOMEN, RegionGroup.PELVIS, RegionGroup.SPINE),
                BUILT_IN.groupsOf(BodyPart.LUMBAR_SPINE),
                "spine segments lie in the trunk regions, SPINE is an extension"),
        () ->
            assertEquals(
                Set.of(RegionGroup.NECK, RegionGroup.SPINE),
                BUILT_IN.groupsOf(BodyPart.CERVICAL_SPINE)),
        () -> assertEquals(Set.of(RegionGroup.EXTREMITY), BUILT_IN.groupsOf(BodyPart.EXTREMITY)),
        () -> assertTrue(new AnatomicRegion(BodyPart.FEMUR).isIn(RegionGroup.EXTREMITY)),
        () -> assertFalse(new AnatomicRegion(BodyPart.EXTREMITY).isIn(RegionGroup.UPPER_EXTREMITY)),
        () -> assertFalse(new AnatomicRegion(BodyPart.HEAD).isIn(RegionGroup.WHOLE_BODY)),
        () -> assertFalse(RegionGroup.SPINE.isPlaybookRegion()));
  }

  @Test
  @DefaultLocale(language = "en", country = "US")
  void every_group_has_a_label_in_english_and_french() {
    for (RegionGroup group : RegionGroup.values()) {
      String en = group.getLabel();
      String fr = group.getLabel(Locale.FRENCH);
      assertTrue(en != null && !en.isBlank() && !en.equals(group.name()), group.name());
      assertTrue(fr != null && !fr.isBlank() && !fr.equals(group.name()), group.name());
    }
    assertEquals("Upper extremity", RegionGroup.UPPER_EXTREMITY.getLabel());
    assertEquals("T\u00EAte", RegionGroup.HEAD.getLabel(Locale.FRENCH));
  }

  @Test
  void the_code_of_a_group_is_the_body_part_of_the_same_area() {
    for (RegionGroup group : RegionGroup.values()) {
      BodyPart part = AnatomicBuilder.getBodyPartFromCode(group.getSnomedCode());
      assertTrue(part != null && BUILT_IN.groupsOf(part).contains(group), group.name());
    }
  }

  @Test
  void reads_codes_and_merges_a_site_document() throws IOException {
    RegionGroups site =
        read(
            """
            {"schema": 1, "groups": {
              "chest": ["SCT:76752008", "LIVER", "NOT_A_PART", "XYZ:1"],
              "ORGANS": ["LIVER"]
            }}
            """);
    RegionGroups merged = BUILT_IN.merge(site);
    OtherPart custom = new OtherPart("76752008", "Breast structure", CodingScheme.SCT);

    assertAll(
        () -> assertEquals(Set.of(RegionGroup.CHEST), site.groupsOf(custom)),
        () ->
            assertEquals(
                Set.of(RegionGroup.ABDOMEN, RegionGroup.CHEST), merged.groupsOf(BodyPart.LIVER)),
        () -> assertEquals(Set.of(RegionGroup.ABDOMEN), BUILT_IN.groupsOf(BodyPart.LIVER)),
        () -> assertTrue(merged.groupsOf(new BodyPartTerm("TETE")).isEmpty()));
  }

  @Test
  void refuses_a_newer_schema_or_no_json() {
    assertAll(
        () -> assertThrows(IOException.class, () -> read("{\"schema\": 2, \"groups\": {}}")),
        () -> assertThrows(IOException.class, () -> read("not json")));
  }

  private static RegionGroups read(String json) throws IOException {
    return RegionGroups.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }
}
