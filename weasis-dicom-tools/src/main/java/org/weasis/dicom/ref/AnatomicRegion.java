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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.macro.Code;
import org.weasis.dicom.ref.AnatomicBuilder.Category;

public class AnatomicRegion {

  private final Category category;
  private final AnatomicItem region;
  private final Set<AnatomicModifier> modifiers;

  public AnatomicRegion(AnatomicItem region) {
    this(null, region, null);
  }

  public AnatomicRegion(Category category, AnatomicItem region, Set<AnatomicModifier> modifiers) {
    this.category = category;
    this.region = Objects.requireNonNull(region);
    this.modifiers = modifiers == null ? new HashSet<>() : modifiers;
  }

  public static AnatomicRegion read(Attributes dcm) {
    if (dcm == null) {
      return null;
    }
    // Get only the first item
    Attributes regionAttributes = dcm.getNestedDataset(Tag.AnatomicRegionSequence);
    if (regionAttributes == null) {
      String bodyPart = dcm.getString(Tag.BodyPartExamined);
      if (StringUtil.hasText(bodyPart)) {
        BodyPart item = AnatomicBuilder.getBodyPartFromLegacyCode(bodyPart);
        if (item != null) {
          return new AnatomicRegion(item);
        }
      }
      return null;
    }

    String codeValue = new Code(regionAttributes).getExistingCodeValue();
    AnatomicItem item = AnatomicBuilder.getBodyPartFromCode(codeValue);
    if (item == null) {
      item = AnatomicBuilder.getSurfacePartFromCode(codeValue);
    }

    AnatomicRegion region = new AnatomicRegion(item);
    addModifiers(regionAttributes, region);
    return region;
  }

  public static void write(Attributes dcm, AnatomicRegion region) {
    if (dcm == null || region == null) {
      return;
    }
    Attributes regAttributes = new Attributes();
    AnatomicItem anatomicItem = region.getRegion();
    regAttributes.setString(Tag.CodeValue, VR.SH, anatomicItem.getCodeValue());
    regAttributes.setString(Tag.CodeMeaning, VR.LO, anatomicItem.getCodeMeaning());
    writeScheme(regAttributes, anatomicItem.getScheme());
    if (anatomicItem.getLegacyCode() != null) {
      dcm.setString(Tag.BodyPartExamined, VR.CS, anatomicItem.getLegacyCode());
    }

    Set<AnatomicModifier> modifiers = region.getModifiers();
    if (modifiers != null && !modifiers.isEmpty()) {
      Sequence seq =
          regAttributes.newSequence(Tag.AnatomicRegionModifierSequence, modifiers.size());
      for (AnatomicModifier modifier : modifiers) {
        Attributes mod = new Attributes();
        writeScheme(mod, modifier.getScheme());
        mod.setString(Tag.CodeValue, VR.SH, modifier.getCodeValue());
        mod.setString(Tag.CodeMeaning, VR.LO, modifier.getCodeMeaning());
        seq.add(mod);
      }
    }
    dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regAttributes);
  }

  private static void writeScheme(Attributes regionAttributes, CodingScheme scheme) {
    regionAttributes.setString(Tag.CodingSchemeDesignator, VR.SH, scheme.getDesignator());
    regionAttributes.setString(Tag.CodingSchemeName, VR.SH, scheme.getCodeName());
    regionAttributes.setString(Tag.CodingSchemeUID, VR.UI, scheme.getUid());
  }

  private static void addModifiers(Attributes regionAttributes, AnatomicRegion region) {
    Sequence seq = regionAttributes.getSequence(Tag.AnatomicRegionModifierSequence);
    if (seq != null) {
      for (Attributes attribute : seq) {
        AnatomicModifier modifier =
            AnatomicBuilder.getAnatomicModifierFromCode(new Code(attribute).getExistingCodeValue());
        if (modifier != null) {
          region.addModifier(modifier);
        }
      }
    }
  }

  public AnatomicItem getRegion() {
    return region;
  }

  public Category getCategory() {
    return category;
  }

  public Set<AnatomicModifier> getModifiers() {
    return modifiers;
  }

  public void addModifier(AnatomicModifier modifier) {
    modifiers.add(modifier);
  }

  public void removeModifier(AnatomicModifier modifier) {
    modifiers.remove(modifier);
  }

  @Override
  public String toString() {
    String modifiersList =
        modifiers.stream().map(AnatomicModifier::getCodeMeaning).collect(Collectors.joining(", "));
    if (StringUtil.hasText(modifiersList)) {
      modifiersList = " (" + modifiersList + ")";
    }
    return region.getCodeMeaning() + modifiersList;
  }
}
