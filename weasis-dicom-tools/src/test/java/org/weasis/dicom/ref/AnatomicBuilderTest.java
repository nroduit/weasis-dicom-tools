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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AnatomicBuilderTest {
  static final Locale defaultLocale = Locale.getDefault();

  @BeforeAll
  static void setUp() {
    Locale.setDefault(Locale.FRENCH); // Force Locale for testing date format
  }

  @AfterAll
  static void tearDown() {
    Locale.setDefault(defaultLocale);
  }

  @Test
  void getAllBodyParts_returnsAllBodyParts() {
    assertEquals(BodyPart.values().length, AnatomicBuilder.getAllBodyParts().size());
  }

  @Test
  void getCommonBodyParts_returnsOnlyCommonBodyParts() {
    assertTrue(AnatomicBuilder.getCommonBodyParts().stream().allMatch(BodyPart::isCommon));
  }

  @Test
  void getBodyPartsFromPredicate_returnsFilteredBodyParts() {
    assertEquals(0, AnatomicBuilder.getBodyParts(bp -> false).size());
  }

  @Test
  void getBodyPartFromCode() {
    assertEquals(
        BodyPart.values()[0],
        AnatomicBuilder.getBodyPartFromCode(BodyPart.values()[0].getCodeValue()));

    BodyPart bodyPart = AnatomicBuilder.getBodyPartFromCode("128559007");
    assertEquals("Artère géniculaire", bodyPart.getCodeMeaning());
    assertEquals("Artère géniculaire", bodyPart.toString());
    assertEquals(CodingScheme.SCT, bodyPart.getScheme());
    assertTrue(bodyPart.isPaired());
    assertFalse(bodyPart.isCommon());
    assertFalse(bodyPart.isEndoscopic());

    String key = "nonExistentKey";
    assertEquals('!' + key + '!', MesBody.getString(key));
  }

  @Test
  void getBodyPartFromLegacyCode() {
    assertEquals(
        BodyPart.values()[0],
        AnatomicBuilder.getBodyPartFromLegacyCode(BodyPart.values()[0].getLegacyCode()));
    BodyPart bodyPart = AnatomicBuilder.getBodyPartFromLegacyCode("PANCREATICDUCT");
    assertNotNull(bodyPart);
    assertEquals("Canal pancréatique", bodyPart.toString());
    assertEquals(CodingScheme.SCT, bodyPart.getScheme());
    assertFalse(bodyPart.isPaired());
    assertTrue(bodyPart.isCommon());
    assertTrue(bodyPart.isEndoscopic());

    assertNull(AnatomicBuilder.getBodyPartFromLegacyCode(" "));
    assertNull(AnatomicBuilder.getBodyPartFromLegacyCode("invalid"));
  }

  @Test
  void getBodyPartFromPredicate() {
    assertEquals(BodyPart.values()[0], AnatomicBuilder.getBodyPartFromPredicate(bp -> true));

    assertNull(
        AnatomicBuilder.getBodyPartFromPredicate(
            bp -> Objects.equals(bp.getCodeMeaning(), "invalid")));
  }

  @Test
  void getBodyPartFromCodeMeaning() {
    assertEquals(
        BodyPart.values()[0],
        AnatomicBuilder.getBodyPartFromCodeMeaning(BodyPart.values()[0].getCodeMeaning()));

    assertNull(AnatomicBuilder.getBodyPartFromCodeMeaning("invalid"));
  }

  @Test
  void getAnatomicModifierFromCode() {
    assertEquals(
        AnatomicModifier.values()[0],
        AnatomicBuilder.getAnatomicModifierFromCode(AnatomicModifier.values()[0].getCodeValue()));

    AnatomicModifier modifier = AnatomicBuilder.getAnatomicModifierFromCode("49370004");
    assertNotNull(modifier);
    assertEquals("Latéral", modifier.toString());
    assertEquals(CodingScheme.SCT, modifier.getScheme());

    String key = "nonExistentKey";
    assertEquals('!' + key + '!', MesModifier.getString(key));
  }

  @Test
  void getSurfacePartFromCode() {

    assertEquals(
        SurfacePart.values()[0],
        AnatomicBuilder.getSurfacePartFromCode(SurfacePart.values()[0].getCodeValue()));

    SurfacePart surfacePart = AnatomicBuilder.getSurfacePartFromCode("130319");
    assertNotNull(surfacePart);
    assertEquals(
        "Peau de l'hélix postérieur supérieur de l'oreille",
        surfacePart.toString());
    assertEquals(CodingScheme.DCM, surfacePart.getScheme());
    assertEquals(133, surfacePart.getLeft());
    assertEquals(0, surfacePart.getMiddle());
    assertEquals(132, surfacePart.getRight());

    String key = "nonExistentKey";
    assertEquals('!' + key + '!', MesSurface.getString(key));
  }

  @Test
  void getCodingScheme() {
    assertEquals("SCT", CodingScheme.SCT.getDesignator());
    assertEquals("SCT", CodingScheme.SCT.toString());
    assertEquals(CodingScheme.SRT.getUid(), CodingScheme.SCT.getUid());
    assertEquals("ACR Index", CodingScheme.ACR.getCodeName());
  }
}
