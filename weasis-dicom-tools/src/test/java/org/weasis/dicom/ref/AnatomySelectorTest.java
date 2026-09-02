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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnatomySelectorTest {

  @Test
  void a_bare_word_is_a_group_first_then_a_body_part_term() {
    AnatomySelector chest = AnatomySelector.parse(" chest ").orElseThrow();
    AnatomySelector thorax = AnatomySelector.parse("thorax").orElseThrow();

    assertAll(
        () -> assertEquals(new AnatomySelector.Group(RegionGroup.CHEST), chest),
        () -> assertEquals("SCT:816094009", thorax.notation(), "term of Table L-1"),
        () -> assertTrue(chest.matches(new AnatomicRegion(BodyPart.LUNG))),
        () -> assertTrue(chest.matches(new AnatomicRegion(BodyPart.CHEST_ABDOMEN_AND_PELVIS))),
        () -> assertTrue(chest.matches(new AnatomicRegion(BodyPart.ENTIRE_BODY))),
        () -> assertFalse(chest.matches(new AnatomicRegion(BodyPart.HEAD))),
        () -> assertTrue(thorax.matches(new AnatomicRegion(BodyPart.CHEST))),
        () -> assertFalse(thorax.matches(new AnatomicRegion(BodyPart.LUNG))),
        () -> assertFalse(chest.matches(null)));
  }

  @Test
  void a_coded_value_selects_that_code_only() {
    AnatomySelector code = AnatomySelector.parse("SCT:10200004").orElseThrow();

    assertAll(
        () -> assertEquals("SCT:10200004", code.notation()),
        () -> assertTrue(code.matches(new AnatomicRegion(BodyPart.LIVER))),
        () -> assertFalse(code.matches(new AnatomicRegion(BodyPart.ABDOMEN))),
        () -> assertFalse(code.matches(new AnatomicRegion(new BodyPartTerm("LIVER")))));
  }

  @Test
  void a_retired_snomed_rt_id_resolves_to_its_snomed_ct_code() {
    assertEquals(
        "SCT:39607008", AnatomySelector.parse("srt:T-28000").orElseThrow().notation(), "lung");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "NOWHERE", "XYZ:123", "SCT:", ":123", "SRT:T-99999"})
  void an_unknown_token_resolves_to_nothing(String token) {
    assertEquals(Optional.empty(), AnatomySelector.parse(token));
  }
}
