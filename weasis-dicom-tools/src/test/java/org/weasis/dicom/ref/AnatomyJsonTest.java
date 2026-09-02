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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.junit.DefaultLocale;

class AnatomyJsonTest {

  @Test
  @DefaultLocale(language = "en", country = "US")
  void a_coded_region_gives_code_term_groups_and_laterality() {
    Attributes dcm = new Attributes();
    dcm.setString(Tag.BodyPartExamined, VR.CS, "THORAX");
    dcm.setString(Tag.Laterality, VR.CS, "R");

    JsonObject json = AnatomyJson.read(dcm);

    assertAll(
        () -> assertEquals("SCT", json.getString("scheme")),
        () -> assertEquals("816094009", json.getString("code")),
        () -> assertEquals("Chest", json.getString("meaning")),
        () -> assertEquals("CHEST", json.getString("bodyPartExamined")),
        () -> assertEquals("[\"CHEST\"]", json.getJsonArray("groups").toString()),
        () -> assertTrue(json.getJsonArray("modifiers").isEmpty()),
        () -> assertEquals("R", json.getString("laterality")));
  }

  @Test
  void combined_regions_list_their_groups_in_order_and_a_term_has_no_code() {
    JsonObject cap =
        AnatomyJson.toJson(new AnatomicRegion(BodyPart.CHEST_ABDOMEN_AND_PELVIS), null);
    JsonObject term = AnatomyJson.toJson(new AnatomicRegion(new BodyPartTerm("TETE")), " ");

    assertAll(
        () ->
            assertEquals(
                "[\"CHEST\",\"ABDOMEN\",\"PELVIS\"]", cap.getJsonArray("groups").toString()),
        () -> assertEquals(JsonValue.NULL, cap.get("laterality")),
        () -> assertEquals(JsonValue.NULL, term.get("code")),
        () -> assertEquals("TETE", term.getString("bodyPartExamined")),
        () -> assertEquals(JsonValue.NULL, term.get("laterality")),
        () -> assertTrue(term.getJsonArray("groups").isEmpty()));
  }

  @Test
  void no_anatomy_gives_null() {
    assertNull(AnatomyJson.read(new Attributes()));
  }
}
