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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.weasis.core.util.StringUtil;

public class AnatomicBuilder {
  public interface CategoryBuilder {
    String getContextUID();

    String getTIdentifier();
  }

  public static class OtherCategory implements CategoryBuilder {

    private final String ContextUID;
    private final String identifier;

    public OtherCategory(String ContextUID, String identifier) {
      this.ContextUID = Objects.requireNonNull(ContextUID);
      this.identifier = Objects.requireNonNull(identifier);
    }

    public String getContextUID() {
      return ContextUID;
    }

    public String getTIdentifier() {
      return identifier;
    }

    public int hashCode() {
      return Objects.hashCode(getContextUID());
    }

    @Override
    public String toString() {
      return getTIdentifier();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      OtherCategory that = (OtherCategory) o;
      return Objects.equals(ContextUID, that.ContextUID);
    }
  }

  public enum Category implements AnatomicBuilder.CategoryBuilder {
    SURFACE("1.2.840.10008.6.1.1268"),
    ALL_REGIONS("1.2.840.10008.6.1.2"),
    COMMON("1.2.840.10008.6.1.308"),
    ENDOSCOPY("1.2.840.10008.6.1.311");

    private final String ContextUID;

    Category(String ContextUID) {
      this.ContextUID = ContextUID;
    }

    @Override
    public String getContextUID() {
      return ContextUID;
    }

    @Override
    public String getTIdentifier() {
      return MesCategory.getString(name());
    }

    @Override
    public String toString() {
      return getTIdentifier();
    }

    public static Category getCategoryFromContextUID(String uid) {
      return Arrays.stream(Category.values())
          .filter(c -> Objects.equals(c.ContextUID, uid))
          .findFirst()
          .orElse(null);
    }
  }

  public static final Map<CategoryBuilder, List<AnatomicItem>> categoryMap = new HashMap<>();

  static {
    categoryMap.put(Category.SURFACE, List.of(SurfacePart.values()));
    categoryMap.put(Category.ALL_REGIONS, List.of(BodyPart.values()));
    categoryMap.put(
        Category.COMMON,
        Arrays.stream(BodyPart.values())
            .filter(BodyPart::isCommon)
            .map(b -> (AnatomicItem) b)
            .toList());
    categoryMap.put(
        Category.ENDOSCOPY,
        Arrays.stream(BodyPart.values())
            .filter(BodyPart::isEndoscopic)
            .map(b -> (AnatomicItem) b)
            .toList());
  }

  private static final Map<String, BodyPart> CODE_TO_BODY_PART =
      Arrays.stream(BodyPart.values())
          .collect(Collectors.toMap(BodyPart::getCodeValue, Function.identity()));
  private static final Map<String, BodyPart> LEGACY_CODE_TO_BODY_PART =
      Arrays.stream(BodyPart.values())
          .filter(bp -> StringUtil.hasText(bp.getLegacyCode()))
          .collect(Collectors.toMap(BodyPart::getLegacyCode, Function.identity()));

  private static final Map<String, AnatomicModifier> CODE_TO_ANATOMIC_MODIFIER =
      Arrays.stream(AnatomicModifier.values())
          .collect(Collectors.toMap(AnatomicModifier::getCodeValue, Function.identity()));

  private static final Map<String, SurfacePart> CODE_TO_SURFACE_PART =
      Arrays.stream(SurfacePart.values())
          .collect(Collectors.toMap(SurfacePart::getCodeValue, Function.identity()));

  private AnatomicBuilder() {}

  public static List<BodyPart> getAllBodyParts() {
    return Arrays.asList(BodyPart.values());
  }

  public static List<BodyPart> getCommonBodyParts() {
    return getBodyParts(BodyPart::isCommon);
  }

  /**
   * Retrieve BodyPart from a filter
   *
   * @param filter Predicate to filter the body parts
   * @return BodyPart
   */
  public static List<BodyPart> getBodyParts(Predicate<BodyPart> filter) {
    return Arrays.stream(BodyPart.values()).filter(filter).toList();
  }

  /**
   * Retrieve BodyPart from code value
   *
   * @param code Code Value of the body part
   * @return BodyPart
   */
  public static BodyPart getBodyPartFromCode(String code) {
    return CODE_TO_BODY_PART.get(code);
  }

  /**
   * Retrieve BodyPart from tge Body Part Examined code
   *
   * @param legacyCode the old Body Part Examined code
   * @return BodyPart
   */
  public static BodyPart getBodyPartFromLegacyCode(String legacyCode) {
    if (StringUtil.hasText(legacyCode)) {
      return LEGACY_CODE_TO_BODY_PART.get(legacyCode.trim());
    }
    return null;
  }

  /**
   * Retrieve BodyPart from a filter
   *
   * @param filter Predicate to filter the body parts
   * @return BodyPart
   */
  public static BodyPart getBodyPartFromPredicate(Predicate<BodyPart> filter) {
    return Arrays.stream(BodyPart.values()).filter(filter).findFirst().orElse(null);
  }

  /**
   * Retrieve BodyPart from tge code meaning
   *
   * @param codeMeaning Code Meaning of the body part
   * @return BodyPart
   */
  public static BodyPart getBodyPartFromCodeMeaning(String codeMeaning) {
    return getBodyPartFromPredicate(bp -> Objects.equals(codeMeaning.trim(), bp.getCodeMeaning()));
  }

  /**
   * Retrieve AnatomicModifier from code value
   *
   * @param code Code Value of the anatomic modifier
   * @return AnatomicModifier
   */
  public static AnatomicModifier getAnatomicModifierFromCode(String code) {
    return CODE_TO_ANATOMIC_MODIFIER.get(code);
  }

  /**
   * Retrieve SurfacePart from code value
   *
   * @param code Code Value of the surface part
   * @return SurfacePart
   */
  public static SurfacePart getSurfacePartFromCode(String code) {
    return CODE_TO_SURFACE_PART.get(code);
  }
}
