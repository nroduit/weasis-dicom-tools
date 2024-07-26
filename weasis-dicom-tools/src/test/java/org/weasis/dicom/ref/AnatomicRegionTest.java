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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultLocale;
import org.weasis.dicom.ref.AnatomicBuilder.Category;
import org.weasis.dicom.ref.AnatomicBuilder.OtherCategory;

@DefaultLocale(language = "en", country = "US")
class AnatomicRegionTest {

  @Test
  void getRegion_returnsCorrectRegion() {
    AnatomicItem item = SurfacePart.IRIS;
    AnatomicRegion region = new AnatomicRegion(item);
    assertEquals(item, region.getRegion());
    assertTrue(region.getModifiers().isEmpty());
  }

  @Test
  void getModifiers_returnsCorrectModifiers() {
    AnatomicItem item = BodyPart.ABDOMEN;
    AnatomicModifier modifier = AnatomicModifier.BILATERAL;
    Set<AnatomicModifier> modifiers = new HashSet<>();
    modifiers.add(modifier);
    AnatomicRegion region = new AnatomicRegion(Category.COMMON, item, modifiers);
    assertEquals(modifiers, region.getModifiers());
  }

  @Test
  void Modifier_addsRemovesModifierToSet() {
    AnatomicItem item = BodyPart.ABDOMEN;
    AnatomicModifier modifier = AnatomicModifier.MARGINAL;
    AnatomicRegion region = new AnatomicRegion(item);
    region.addModifier(modifier);
    assertTrue(region.getModifiers().contains(modifier));

    region.removeModifier(modifier);
    assertFalse(region.getModifiers().contains(modifier));
  }

  @Test
  void getAnatomicRegion_returnsBodyPartFromLegacyCode() {
    Attributes dcm = new Attributes();
    dcm.setString(Tag.BodyPartExamined, VR.CS, BodyPart.ABDOMEN.getLegacyCode());
    AnatomicRegion item = AnatomicRegion.read(dcm);
    assertEquals(BodyPart.ABDOMEN, item.getRegion());
  }

  @Test
  void readWriteAnatomicRegion_BodyPart() {
    Attributes dcm = new Attributes();
    assertNull(AnatomicRegion.read(null));
    assertNull(AnatomicRegion.read(dcm));
    Set<AnatomicModifier> modifiers = new HashSet<>();
    modifiers.add(AnatomicModifier.LEFT);
    modifiers.add(AnatomicModifier.SURFACE);
    AnatomicRegion.write(
        dcm, new AnatomicRegion(Category.ALL_REGIONS, BodyPart.HEAD_AND_NECK, modifiers));
    assertTrue(dcm.contains(Tag.AnatomicRegionSequence));

    AnatomicRegion anatomicRegion = AnatomicRegion.read(dcm);
    assertEquals(BodyPart.HEAD_AND_NECK, anatomicRegion.getRegion());
    assertEquals(2, anatomicRegion.getModifiers().size());
    assertTrue(anatomicRegion.getModifiers().contains(AnatomicModifier.LEFT));
    assertTrue(anatomicRegion.getModifiers().contains(AnatomicModifier.SURFACE));
    assertTrue(anatomicRegion.toString().contains(AnatomicModifier.SURFACE.getCodeMeaning()));
  }

  @Test
  void readWriteAnatomicRegion_SurfacePart() {
    Attributes dcm = new Attributes();
    Set<AnatomicModifier> modifiers = new HashSet<>();
    modifiers.add(AnatomicModifier.RIGHT);
    modifiers.add(AnatomicModifier.SURFACE);
    AnatomicRegion.write(dcm, new AnatomicRegion(Category.SURFACE, SurfacePart.IRIS, modifiers));
    assertTrue(dcm.contains(Tag.AnatomicRegionSequence));
    assertFalse(dcm.contains(Tag.BodyPartExamined));

    AnatomicRegion anatomicRegion = AnatomicRegion.read(dcm);
    assertEquals(Category.SURFACE, anatomicRegion.getCategory());
    assertEquals(SurfacePart.IRIS, anatomicRegion.getRegion());
    assertEquals(2, anatomicRegion.getModifiers().size());
    assertTrue(anatomicRegion.getModifiers().contains(AnatomicModifier.RIGHT));
    assertTrue(anatomicRegion.getModifiers().contains(AnatomicModifier.SURFACE));
    assertTrue(anatomicRegion.toString().contains(AnatomicModifier.SURFACE.getCodeMeaning()));
  }

  @Test
  void readWriteAnatomicRegion_OtherPart() {
    Attributes dcm = new Attributes();
    Set<AnatomicModifier> modifiers = new HashSet<>();
    modifiers.add(AnatomicModifier.SURFACE);
    OtherPart otherPart = new OtherPart("86381001", "Skin of trunk", CodingScheme.SCT, false);
    OtherCategory otherCategory = new OtherCategory("1.2.840.3.5.98", "C13", "Custom Test");
    AnatomicRegion.write(dcm, new AnatomicRegion(otherCategory, otherPart, modifiers));
    dcm.setString(Tag.FrameLaterality, VR.CS, "U");

    assertTrue(dcm.contains(Tag.AnatomicRegionSequence));
    assertFalse(AnatomicBuilder.categoryMap.containsKey(otherCategory));
    AnatomicRegion anatomicRegion = AnatomicRegion.read(dcm);
    assertTrue(AnatomicBuilder.categoryMap.containsKey(otherCategory));
    assertTrue(AnatomicBuilder.categoryMap.get(otherCategory).contains(otherPart));
    assertEquals("86381001", anatomicRegion.getRegion().getCodeValue());
    assertNull(anatomicRegion.getRegion().getLegacyCode());
    assertEquals("Skin of trunk", anatomicRegion.getRegion().getCodeMeaning());
    assertFalse(anatomicRegion.getRegion().isPaired());
    assertEquals(CodingScheme.SCT, anatomicRegion.getRegion().getCodingScheme());
    assertEquals(1, anatomicRegion.getModifiers().size());
    assertTrue(anatomicRegion.getModifiers().contains(AnatomicModifier.SURFACE));
    assertTrue(anatomicRegion.toString().contains(AnatomicModifier.SURFACE.getCodeMeaning()));
  }
}
