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

import java.util.ArrayList;
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
import org.weasis.dicom.macro.ItemCode;
import org.weasis.dicom.ref.AnatomicBuilder.Category;
import org.weasis.dicom.ref.AnatomicBuilder.CategoryBuilder;
import org.weasis.dicom.ref.AnatomicBuilder.OtherCategory;

public class AnatomicRegion {

  private final CategoryBuilder category;
  private final AnatomicItem region;
  private final Set<AnatomicModifier> modifiers;

  public AnatomicRegion(AnatomicItem region) {
    this(null, region, null);
  }

  public AnatomicRegion(
      CategoryBuilder category, AnatomicItem region, Set<AnatomicModifier> modifiers) {
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

    Code code = new Code(regionAttributes);
    String codeValue = code.getExistingCodeValue();
    if (!StringUtil.hasText(codeValue)) {
      return null;
    }
    AnatomicItem item = AnatomicBuilder.getBodyPartFromCode(codeValue);
    if (item == null) {
      item = AnatomicBuilder.getSurfacePartFromCode(codeValue);
    }
    if (item == null) {
      boolean paired = isPaired(dcm, regionAttributes);
      item = new OtherPart(codeValue, code.getCodeMeaning(), code.getCodingScheme(), paired);
    }

    CategoryBuilder category = null;
    String contextUID = code.getContextUID();
    if (StringUtil.hasText(contextUID)) {
      category = Category.getCategoryFromContextUID(contextUID);
      if (category == null) {
        category =
            new OtherCategory(contextUID, code.getContextIdentifier(), code.getContextIdentifier());
        AnatomicBuilder.categoryMap.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
      }
    }
    AnatomicRegion region = new AnatomicRegion(category, item, null);
    addModifiers(regionAttributes, region);
    return region;
  }

  public static void write(Attributes dcm, AnatomicRegion region) {
    if (dcm == null || region == null) {
      return;
    }
    Attributes regAttributes = new Attributes();
    AnatomicItem anatomicItem = region.getRegion();
    Code code = new Code(regAttributes);
    writeCode(code, anatomicItem);
    writeRegionContext(code, region.getCategory());

    if (StringUtil.hasText(anatomicItem.getLegacyCode())) {
      dcm.setString(Tag.BodyPartExamined, VR.CS, anatomicItem.getLegacyCode());
    }

    Set<AnatomicModifier> modifiers = region.getModifiers();
    if (modifiers != null && !modifiers.isEmpty()) {
      Sequence seq =
          regAttributes.newSequence(Tag.AnatomicRegionModifierSequence, modifiers.size());
      for (AnatomicModifier modifier : modifiers) {
        Attributes mod = new Attributes();
        Code mcode = new Code(mod);
        writeCode(mcode, modifier);
        seq.add(mod);
      }
    }
    dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regAttributes);
  }

  private static void writeRegionContext(Code code, CategoryBuilder category) {
    if (category != null) {
      code.setContextUID(category.getContextUID());
      code.setContextIdentifier(category.getTIdentifier());
    }
  }

  private static void writeCode(Code code, ItemCode anatomicCode) {
    code.setCodingScheme(anatomicCode.getCodingScheme());
    code.setCodeValue(anatomicCode.getCodeValue());
    code.setCodeMeaning(anatomicCode.getCodeMeaning());
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

  private static boolean isPaired(Attributes dcm, Attributes regionAttributes) {
    String laterality =
        dcm.getString(Tag.FrameLaterality, regionAttributes.getString(Tag.ImageLaterality));
    if (StringUtil.hasText(laterality) && !laterality.equals("U")) {
      return true;
    }
    Sequence seq = regionAttributes.getSequence(Tag.AnatomicRegionModifierSequence);
    if (seq != null) {
      for (Attributes attribute : seq) {
        AnatomicModifier modifier =
            AnatomicBuilder.getAnatomicModifierFromCode(new Code(attribute).getExistingCodeValue());
        if (modifier == AnatomicModifier.LEFT
            || modifier == AnatomicModifier.RIGHT
            || modifier == AnatomicModifier.BILATERAL) {
          return true;
        }
      }
    }
    return false;
  }

  public AnatomicItem getRegion() {
    return region;
  }

  public CategoryBuilder getCategory() {
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
