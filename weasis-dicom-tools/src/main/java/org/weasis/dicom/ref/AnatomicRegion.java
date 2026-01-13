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
import java.util.Optional;
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

/**
 * Represents an anatomical region in DICOM, combining an anatomical item with optional modifiers
 * and category information. This class provides functionality to read from and write to DICOM
 * attributes, supporting both modern anatomical region sequences and legacy body part examined
 * tags.
 *
 * <p>An anatomical region consists of:
 *
 * <ul>
 *   <li>A primary anatomical item (body part, surface part, or other part)
 *   <li>Optional modifiers that specify position, orientation, or characteristics
 *   <li>Optional category information providing additional context
 * </ul>
 *
 * @see AnatomicItem
 * @see AnatomicModifier
 * @see CategoryBuilder
 */
public class AnatomicRegion {

  private final CategoryBuilder category;
  private final AnatomicItem region;
  private final Set<AnatomicModifier> modifiers;

  /**
   * Creates an anatomical region with the specified anatomical item.
   *
   * @param region the anatomical item, must not be null
   * @throws NullPointerException if region is null
   */
  public AnatomicRegion(AnatomicItem region) {
    this(null, region, null);
  }

  /**
   * Creates an anatomical region with the specified category, anatomical item, and modifiers.
   *
   * @param category the category builder, may be null
   * @param region the anatomical item, must not be null
   * @param modifiers the set of modifiers, may be null (will create empty set)
   * @throws NullPointerException if region is null
   */
  public AnatomicRegion(
      CategoryBuilder category, AnatomicItem region, Set<AnatomicModifier> modifiers) {
    this.category = category;
    this.region = Objects.requireNonNull(region);
    this.modifiers = modifiers == null ? new HashSet<>() : modifiers;
  }

  /**
   * Reads an anatomical region from DICOM attributes. Supports both anatomical region sequence and
   * legacy body part examined tag.
   *
   * @param dcm the DICOM attributes to read from
   * @return the parsed anatomical region, or null if no valid region data found
   */
  public static AnatomicRegion read(Attributes dcm) {
    if (dcm == null) {
      return null;
    }
    return readFromRegionSequence(dcm).or(() -> readFromLegacyBodyPart(dcm)).orElse(null);
  }

  /**
   * Writes an anatomical region to DICOM attributes.
   *
   * @param dcm the DICOM attributes to write to
   * @param region the anatomical region to write
   */
  public static void write(Attributes dcm, AnatomicRegion region) {
    if (dcm == null || region == null) {
      return;
    }

    Attributes regAttributes = new Attributes();
    AnatomicItem anatomicItem = region.getRegion();
    Code code = new Code(regAttributes);

    writeCode(code, anatomicItem);
    writeRegionContext(code, region.getCategory());
    writeLegacyBodyPart(dcm, anatomicItem);
    writeModifiers(regAttributes, region.getModifiers());

    dcm.newSequence(Tag.AnatomicRegionSequence, 1).add(regAttributes);
  }

  // Read from anatomical region sequence
  private static Optional<AnatomicRegion> readFromRegionSequence(Attributes dcm) {
    Attributes regionAttributes = dcm.getNestedDataset(Tag.AnatomicRegionSequence);
    if (regionAttributes == null) {
      return Optional.empty();
    }

    Code code = new Code(regionAttributes);
    String codeValue = code.getExistingCodeValue();
    if (!StringUtil.hasText(codeValue)) {
      return Optional.empty();
    }
    AnatomicItem item = findAnatomicItem(code, dcm, regionAttributes);

    CategoryBuilder category = findOrCreateCategory(code, item);
    AnatomicRegion region = new AnatomicRegion(category, item, null);
    addModifiers(regionAttributes, region);

    return Optional.of(region);
  }

  // Read from legacy body part examined tag
  private static Optional<AnatomicRegion> readFromLegacyBodyPart(Attributes dcm) {
    String bodyPart = dcm.getString(Tag.BodyPartExamined);
    if (!StringUtil.hasText(bodyPart)) {
      return Optional.empty();
    }

    BodyPart item = AnatomicBuilder.getBodyPartFromLegacyCode(bodyPart);
    return Optional.ofNullable(item).map(AnatomicRegion::new);
  }

  // Find anatomic item from known types or create OtherPart
  private static AnatomicItem findAnatomicItem(
      Code code, Attributes dcm, Attributes regionAttributes) {
    String codeValue = code.getExistingCodeValue();

    // Try body parts first
    AnatomicItem item = AnatomicBuilder.getBodyPartFromCode(codeValue);
    if (item != null) {
      return item;
    }

    // Try surface parts
    item = AnatomicBuilder.getSurfacePartFromCode(codeValue);
    if (item != null) {
      return item;
    }
    // Create other part with pairing information
    boolean paired = isPaired(dcm, regionAttributes);
    return new OtherPart(codeValue, code.getCodeMeaning(), code.getCodingScheme(), paired);
  }

  // Find existing category or create new one
  private static CategoryBuilder findOrCreateCategory(Code code, AnatomicItem item) {
    String contextUID = code.getContextUID();
    if (!StringUtil.hasText(contextUID)) {
      return null;
    }

    CategoryBuilder category = Category.fromContextUID(contextUID).orElse(null);
    if (category == null) {
      category =
          new OtherCategory(contextUID, code.getContextIdentifier(), code.getContextIdentifier());
    }
    return category;
  }

  // Write legacy body part examined tag if available
  private static void writeLegacyBodyPart(Attributes dcm, AnatomicItem anatomicItem) {
    String legacyCode = anatomicItem.getLegacyCode();
    if (StringUtil.hasText(legacyCode)) {
      dcm.setString(Tag.BodyPartExamined, VR.CS, legacyCode);
    }
  }

  // Write modifiers sequence if any exist
  private static void writeModifiers(Attributes regAttributes, Set<AnatomicModifier> modifiers) {
    if (modifiers == null || modifiers.isEmpty()) {
      return;
    }

    Sequence seq = regAttributes.newSequence(Tag.AnatomicRegionModifierSequence, modifiers.size());
    for (AnatomicModifier modifier : modifiers) {
      Attributes mod = new Attributes();
      Code mcode = new Code(mod);
      writeCode(mcode, modifier);
      seq.add(mod);
    }
  }

  private static void writeRegionContext(Code code, CategoryBuilder category) {
    if (category != null) {
      code.setContextUID(category.getContextUID());
      code.setContextIdentifier(category.getIdentifier());
    }
  }

  private static void writeCode(Code code, ItemCode anatomicCode) {
    code.setCodingScheme(anatomicCode.getCodingScheme());
    code.setCodeValue(anatomicCode.getCodeValue());
    code.setCodeMeaning(anatomicCode.getCodeMeaning());
  }

  private static void addModifiers(Attributes regionAttributes, AnatomicRegion region) {
    Sequence seq = regionAttributes.getSequence(Tag.AnatomicRegionModifierSequence);
    if (seq == null) {
      return;
    }
    for (Attributes attribute : seq) {
      AnatomicModifier modifier =
          AnatomicModifier.fromCode(new Code(attribute).getExistingCodeValue());
      if (modifier != null) {
        region.addModifier(modifier);
      }
    }
  }

  // Check if anatomical structure is paired based on laterality or modifiers
  private static boolean isPaired(Attributes dcm, Attributes regionAttributes) {
    // Check laterality tags (excluding "Unknown")
    String laterality = dcm.getString(Tag.FrameLaterality, dcm.getString(Tag.ImageLaterality));
    if (StringUtil.hasText(laterality) && !"U".equals(laterality)) {
      return true;
    }
    // Check for laterality modifiers
    Sequence seq = regionAttributes.getSequence(Tag.AnatomicRegionModifierSequence);
    if (seq != null) {
      for (Attributes attribute : seq) {
        AnatomicModifier modifier =
            AnatomicModifier.fromCode(new Code(attribute).getExistingCodeValue());
        if (modifier == AnatomicModifier.LEFT
            || modifier == AnatomicModifier.RIGHT
            || modifier == AnatomicModifier.BILATERAL) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns the anatomical item of this region.
   *
   * @return the anatomical item
   */
  public AnatomicItem getRegion() {
    return region;
  }

  /**
   * Returns the category of this region.
   *
   * @return the category, or null if not specified
   */
  public CategoryBuilder getCategory() {
    return category;
  }

  /**
   * Returns the set of modifiers for this region. The returned set is mutable and can be modified.
   *
   * @return the set of modifiers
   */
  public Set<AnatomicModifier> getModifiers() {
    return modifiers;
  }

  /**
   * Adds a modifier to this region.
   *
   * @param modifier the modifier to add
   */
  public void addModifier(AnatomicModifier modifier) {
    modifiers.add(modifier);
  }

  /**
   * Removes a modifier from this region.
   *
   * @param modifier the modifier to remove
   */
  public void removeModifier(AnatomicModifier modifier) {
    modifiers.remove(modifier);
  }

  @Override
  public String toString() {
    String modifiersList =
        modifiers.stream().map(AnatomicModifier::getCodeMeaning).collect(Collectors.joining(", "));

    return StringUtil.hasText(modifiersList)
        ? region.getCodeMeaning() + " (" + modifiersList + ")"
        : region.getCodeMeaning();
  }
}
