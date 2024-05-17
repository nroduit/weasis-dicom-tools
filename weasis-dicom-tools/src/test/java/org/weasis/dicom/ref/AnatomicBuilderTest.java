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

import java.util.Locale;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
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
    Assertions.assertEquals(BodyPart.values().length, AnatomicBuilder.getAllBodyParts().size());
  }

  @Test
  void getCommonBodyParts_returnsOnlyCommonBodyParts() {
    Assertions.assertTrue(
        AnatomicBuilder.getCommonBodyParts().stream().allMatch(BodyPart::isCommon));
  }

  @Test
  void getBodyPartsFromPredicate_returnsFilteredBodyParts() {
    Assertions.assertEquals(0, AnatomicBuilder.getBodyParts(bp -> false).size());
  }

  @Test
  void getBodyPartFromCode_returnsCorrectBodyPart() {
    Assertions.assertEquals(
        BodyPart.values()[0],
        AnatomicBuilder.getBodyPartFromCode(BodyPart.values()[0].getCodeValue()));

    BodyPart bodyPart = AnatomicBuilder.getBodyPartFromCode(128559007);
    Assertions.assertEquals("Artère géniculaire", bodyPart.getCodeMeaning());
  }

  @Test
  void getBodyPartFromLegacyCode_returnsCorrectBodyPart() {
    Assertions.assertEquals(
        BodyPart.values()[0],
        AnatomicBuilder.getBodyPartFromLegacyCode(BodyPart.values()[0].getLegacyCode()));
  }

  @Test
  void getBodyPartFromLegacyCode_returnsNullForInvalidCode() {
    Assertions.assertNull(AnatomicBuilder.getBodyPartFromLegacyCode("invalid"));
  }

  @Test
  void getBodyPartFromPredicate_returnsBodyPart() {
    Assertions.assertEquals(
        BodyPart.values()[0], AnatomicBuilder.getBodyPartFromPredicate(bp -> true));

    Assertions.assertNull(
        AnatomicBuilder.getBodyPartFromPredicate(
            bp -> Objects.equals(bp.getCodeMeaning(), "invalid")));
  }

  @Test
  void getBodyPartFromCodeMeaning_returnsBodyPart() {
    Assertions.assertEquals(
        BodyPart.values()[0],
        AnatomicBuilder.getBodyPartFromCodeMeaning(BodyPart.values()[0].getCodeMeaning()));

    Assertions.assertNull(AnatomicBuilder.getBodyPartFromCodeMeaning("invalid"));
  }

  @Test
  void getAnatomicModifierFromCode_returnsAnatomicModifier() {
    Assertions.assertEquals(
        AnatomicModifier.values()[0],
        AnatomicBuilder.getAnatomicModifierFromCode(AnatomicModifier.values()[0].getCodeValue()));

    Assertions.assertEquals(
        SurfacePart.values()[0],
        AnatomicBuilder.getSurfacePartFromCode(SurfacePart.values()[0].getCodeValue()));
  }
}
