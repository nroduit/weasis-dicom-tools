/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.macro.Code;

class HPDefinitionAnatomyTest {

  @Test
  void modality_alone_selects_the_studies_of_that_modality() {
    HPDefinition ct = definition("CT", null, null, null);

    assertAll(
        () -> assertTrue(ct.appliesTo(image("CT", "LUNG", null))),
        () -> assertFalse(ct.appliesTo(image("MR", "LUNG", null))));
  }

  @Test
  void a_region_code_matches_exactly_and_a_group_area_covers_its_regions() {
    HPDefinition lung = definition(null, "SCT", "39607008", "");
    HPDefinition chest = definition(null, "SCT", "816094009", "");

    assertAll(
        () -> assertTrue(lung.appliesTo(image("CT", "LUNG", null))),
        () -> assertFalse(lung.appliesTo(image("CT", "CHEST", null)), "lung is not the chest"),
        () -> assertTrue(chest.appliesTo(image("CT", "LUNG", null)), "the lung is in the chest"),
        () -> assertTrue(chest.appliesTo(image("CT", "THORAX", null))),
        () -> assertTrue(chest.appliesTo(image("CT", "CHESTABDPELVIS", null))),
        () -> assertFalse(chest.appliesTo(image("CT", "LIVER", null))),
        () -> assertFalse(chest.appliesTo(image("CT", null, null)), "no anatomy"));
  }

  @Test
  void a_retired_snomed_rt_code_of_the_protocol_is_resolved() {
    HPDefinition lung = definition(null, "SRT", "T-28000", null);

    assertTrue(lung.appliesTo(image("CT", "LUNG", null)));
  }

  @Test
  void the_anatomic_region_sequence_of_the_image_wins_over_body_part_examined() {
    HPDefinition liver = definition(null, "SCT", "10200004", null);
    Attributes image = image("CT", "CHEST", null);
    Attributes item = new Attributes();
    item.setString(Tag.CodeValue, VR.SH, "10200004");
    item.setString(Tag.CodingSchemeDesignator, VR.SH, "SCT");
    item.setString(Tag.CodeMeaning, VR.LO, "Liver");
    image.newSequence(Tag.AnatomicRegionSequence, 1).add(item);

    assertTrue(liver.appliesTo(image));
  }

  @Test
  void laterality_of_the_protocol_selects_the_image_laterality() {
    HPDefinition right = definition(null, "SCT", "72696002", "R");
    HPDefinition unpaired = definition(null, "SCT", "818981001", "U");
    HPDefinition any = definition(null, "SCT", "72696002", "");

    assertAll(
        () -> assertTrue(right.appliesTo(image("MR", "KNEE", "R"))),
        () -> assertFalse(right.appliesTo(image("MR", "KNEE", "L"))),
        () -> assertFalse(right.appliesTo(image("MR", "KNEE", null))),
        () -> assertTrue(unpaired.appliesTo(image("CT", "ABDOMEN", null))),
        () -> assertTrue(any.appliesTo(image("MR", "KNEE", "L"))));
  }

  @Test
  void a_protocol_applies_when_one_of_its_definitions_does() {
    HangingProtocol protocol = new HangingProtocol();
    protocol.addHangingProtocolDefinition(definition("MR", "SCT", "69536005", null));
    protocol.addHangingProtocolDefinition(definition("CT", "SCT", "816094009", null));

    assertAll(
        () -> assertTrue(protocol.appliesTo(image("CT", "LUNG", null))),
        () -> assertTrue(protocol.appliesTo(image("MR", "BRAIN", null)), "brain in the head"),
        () -> assertFalse(protocol.appliesTo(image("MR", "KNEE", null))),
        () -> assertFalse(new HangingProtocol().appliesTo(image("CT", "LUNG", null))));
  }

  private static HPDefinition definition(
      String modality, String scheme, String code, String laterality) {
    HPDefinition definition = new HPDefinition();
    if (modality != null) {
      definition.setModality(modality);
    }
    if (code != null) {
      Code region = new Code();
      region.setCodingSchemeDesignator(scheme);
      region.setCodeValue(code);
      region.setCodeMeaning(code);
      definition.addAnatomicRegionCodes(region);
    }
    if (laterality != null) {
      definition.setLaterality(laterality);
    }
    return definition;
  }

  private static Attributes image(String modality, String bodyPart, String laterality) {
    Attributes image = new Attributes();
    image.setString(Tag.Modality, VR.CS, modality);
    if (bodyPart != null) {
      image.setString(Tag.BodyPartExamined, VR.CS, bodyPart);
    }
    if (laterality != null) {
      image.setString(Tag.ImageLaterality, VR.CS, laterality);
    }
    return image;
  }
}
