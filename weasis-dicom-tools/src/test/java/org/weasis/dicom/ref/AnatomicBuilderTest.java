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
import org.weasis.dicom.ref.AnatomicBuilder.Category;

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
        BodyPart.values()[0], BodyPart.getBodyPartFromCode(BodyPart.values()[0].getCodeValue()));

    BodyPart bodyPart = BodyPart.getBodyPartFromCode("128559007");
    assertEquals("Artère géniculaire", bodyPart.getCodeMeaning());
    assertEquals("Artère géniculaire", bodyPart.toString());
    assertEquals(CodingScheme.SCT, bodyPart.getCodingScheme());
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
    assertEquals(CodingScheme.SCT, bodyPart.getCodingScheme());
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
        AnatomicModifier.getAnatomicModifierFromCode(AnatomicModifier.values()[0].getCodeValue()));

    AnatomicModifier modifier = AnatomicModifier.getAnatomicModifierFromCode("49370004");
    assertNotNull(modifier);
    assertEquals("Latéral", modifier.toString());
    assertEquals(CodingScheme.SCT, modifier.getCodingScheme());

    String key = "nonExistentKey";
    assertEquals('!' + key + '!', MesModifier.getString(key));
  }

  @Test
  void getSurfacePartFromCode() {
    assertEquals(
        SurfacePart.values()[0],
        SurfacePart.getSurfacePartFromCode(SurfacePart.values()[0].getCodeValue()));

    SurfacePart surfacePart = SurfacePart.getSurfacePartFromCode("130319");
    assertNotNull(surfacePart);
    assertEquals("Peau de l'hélix postérieur supérieur de l'oreille", surfacePart.toString());
    assertEquals(CodingScheme.DCM, surfacePart.getCodingScheme());
    assertTrue(surfacePart.isPaired());
    assertNull(surfacePart.getLegacyCode());
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

    assertNull(CodingScheme.getSchemeFromDesignator(null));
    assertNull(CodingScheme.getSchemeFromDesignator(""));
    assertNull(CodingScheme.getSchemeFromDesignator("invalid"));
    assertEquals(CodingScheme.SCT, CodingScheme.getSchemeFromDesignator("SCT"));

    assertNull(CodingScheme.getSchemeFromUid(null));
    assertNull(CodingScheme.getSchemeFromUid(""));
    assertNull(CodingScheme.getSchemeFromUid("invalid"));
    assertEquals(CodingScheme.SCT, CodingScheme.getSchemeFromUid("2.16.840.1.113883.6.96"));
  }

  @Test
  void category() {
    assertEquals("Dermatologie (surface)", Category.SURFACE.toString());

    String key = "nonExistentKey";
    assertEquals('!' + key + '!', MesCategory.getString(key));

    Category category = Category.getCategoryFromContextUID("1.2.840.10008.6.1.2");
    assertNotNull(category);
  }
}
