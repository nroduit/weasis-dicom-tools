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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.weasis.core.util.StringUtil;

/**
 * Factory class for building and retrieving anatomical items and categories used in DICOM. This
 * class provides efficient lookup mechanisms for body parts, surface parts, and anatomical
 * modifiers, as well as categorization support for anatomical regions.
 *
 * <p>The builder supports:
 *
 * <ul>
 *   <li>Code-based lookups for all anatomical item types
 *   <li>Legacy code compatibility for body parts
 *   <li>Predicate-based filtering and searching
 *   <li>Category management with localized titles
 * </ul>
 *
 * @see AnatomicItem
 * @see AnatomicRegion
 * @see BodyPart
 * @see SurfacePart
 * @see AnatomicModifier
 */
public final class AnatomicBuilder {

  /**
   * Interface for anatomical category builders that provide context and identification information
   * for anatomical region classifications.
   */
  public interface CategoryBuilder {
    /**
     * Returns the context UID for this category.
     *
     * @return the context UID string
     */
    String getContextUID();

    /**
     * Returns the identifier for this category.
     *
     * @return the identifier string
     */
    String getIdentifier();

    /**
     * Returns the human-readable title for this category.
     *
     * @return the category title
     */
    String getTitle();
  }

  /**
   * Custom category implementation for user-defined anatomical categories. Supports creation of
   * categories not covered by the standard DICOM categories.
   */
  public static final class OtherCategory implements CategoryBuilder {

    private static final String VR_CS_PATTERN = "[A-Z0-9 _]*";
    private static final int MAX_IDENTIFIER_LENGTH = 16;

    private final String contextUID;
    private final String identifier;
    private final String title;

    /**
     * Creates a new custom category.
     *
     * @param contextUID the context UID, must not be null
     * @param identifier the identifier (max 16 chars, uppercase letters/digits/spaces/underscores),
     *     must not be null
     * @param title the human-readable title, must not be null
     * @throws IllegalArgumentException if identifier doesn't match DICOM VR.CS requirements
     */
    public OtherCategory(String contextUID, String identifier, String title) {
      this.contextUID = Objects.requireNonNull(contextUID, "contextUID must not be null");
      this.identifier = Objects.requireNonNull(identifier, "identifier must not be null");
      this.title = Objects.requireNonNull(title, "title must not be null");

      validateIdentifier(identifier);
    }

    private static void validateIdentifier(String identifier) {
      if (identifier.length() > MAX_IDENTIFIER_LENGTH || !identifier.matches(VR_CS_PATTERN)) {
        throw new IllegalArgumentException(
            "Identifier must be a valid VR.CS (max "
                + MAX_IDENTIFIER_LENGTH
                + " characters: uppercase letters, digits, spaces, and underscores)");
      }
    }

    @Override
    public String getContextUID() {
      return contextUID;
    }

    @Override
    public String getIdentifier() {
      return identifier;
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public String toString() {
      return getTitle();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof OtherCategory other && Objects.equals(contextUID, other.contextUID);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(contextUID);
    }
  }

  /**
   * Standard DICOM anatomical categories for classifying anatomical regions. Each category
   * corresponds to a specific DICOM Context ID (CID) and contains related anatomical items.
   */
  public enum Category implements CategoryBuilder {

    /** Surface anatomical structures (CID 4029) */
    SURFACE("1.2.840.10008.6.1.1268", "CID 4029"),
    /** All anatomical regions (CID 4) */
    ALL_REGIONS("1.2.840.10008.6.1.2", "CID 4"),
    /** Commonly used anatomical regions (CID 4031) */
    COMMON("1.2.840.10008.6.1.308", "CID 4031"),
    /** Endoscopic anatomical regions (CID 4040) */
    ENDOSCOPY("1.2.840.10008.6.1.311", "CID 4040");

    private static final Map<String, Category> UID_LOOKUP =
        Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(Category::getContextUID, Function.identity()));
    private final String contextUID;
    private final String identifier;

    Category(String contextUID, String identifier) {
      this.contextUID = Objects.requireNonNull(contextUID);
      this.identifier = Objects.requireNonNull(identifier);
      OtherCategory.validateIdentifier(identifier);
    }

    @Override
    public String getContextUID() {
      return contextUID;
    }

    @Override
    public String getIdentifier() {
      return identifier;
    }

    @Override
    public String getTitle() {
      return MesCategory.getString(contextUID);
    }

    /**
     * Returns the localized title for this category.
     *
     * @param locale the desired locale
     * @return the localized title
     */
    public String getTitle(Locale locale) {
      return MesCategory.getString(contextUID, locale);
    }

    @Override
    public String toString() {
      return getTitle();
    }

    /**
     * Finds a category by its context UID.
     *
     * @param uid the context UID to look up
     * @return an Optional containing the matching category, or empty if not found
     */
    public static Optional<Category> fromContextUID(String uid) {
      return Optional.ofNullable(UID_LOOKUP.get(uid));
    }

    /**
     * @deprecated Use {@link #fromContextUID(String)} instead for better null safety.
     */
    @Deprecated(since = "5.34.0.3", forRemoval = true)
    public static Category getCategoryFromContextUID(String uid) {
      return fromContextUID(uid).orElse(null);
    }
  }

  // Category-to-items mapping for efficient access
  public static final Map<CategoryBuilder, List<AnatomicItem>> categoryMap = createCategoryMap();

  // Efficient lookup maps initialized once
  private static final Map<String, BodyPart> CODE_TO_BODY_PART = createBodyPartCodeMap();
  private static final Map<String, BodyPart> LEGACY_CODE_TO_BODY_PART =
      createBodyPartLegacyCodeMap();
  private static final Map<String, AnatomicModifier> CODE_TO_ANATOMIC_MODIFIER =
      createModifierCodeMap();
  private static final Map<String, SurfacePart> CODE_TO_SURFACE_PART = createSurfacePartCodeMap();

  private AnatomicBuilder() {
    // Utility class - prevent instantiation
  }

  // Create immutable lookup maps using modern Java 17 patterns
  private static Map<CategoryBuilder, List<AnatomicItem>> createCategoryMap() {
    return Map.of(
        Category.SURFACE, List.of(SurfacePart.values()),
        Category.ALL_REGIONS, List.of(BodyPart.values()),
        Category.COMMON, filterBodyParts(BodyPart::isCommon),
        Category.ENDOSCOPY, filterBodyParts(BodyPart::isEndoscopic));
  }

  private static List<AnatomicItem> filterBodyParts(Predicate<BodyPart> filter) {
    return Stream.of(BodyPart.values()).filter(filter).map(AnatomicItem.class::cast).toList();
  }

  private static Map<String, BodyPart> createBodyPartCodeMap() {
    return Stream.of(BodyPart.values())
        .collect(Collectors.toUnmodifiableMap(BodyPart::getCodeValue, Function.identity()));
  }

  private static Map<String, BodyPart> createBodyPartLegacyCodeMap() {
    return Stream.of(BodyPart.values())
        .filter(bp -> StringUtil.hasText(bp.getLegacyCode()))
        .collect(Collectors.toUnmodifiableMap(BodyPart::getLegacyCode, Function.identity()));
  }

  private static Map<String, AnatomicModifier> createModifierCodeMap() {
    return Stream.of(AnatomicModifier.values())
        .collect(Collectors.toUnmodifiableMap(AnatomicModifier::getCodeValue, Function.identity()));
  }

  private static Map<String, SurfacePart> createSurfacePartCodeMap() {
    return Stream.of(SurfacePart.values())
        .collect(Collectors.toUnmodifiableMap(SurfacePart::getCodeValue, Function.identity()));
  }

  /**
   * Returns all available body parts.
   *
   * @return unmodifiable list of all body parts
   */
  public static List<BodyPart> getAllBodyParts() {
    return List.of(BodyPart.values());
  }

  /**
   * Returns all commonly used body parts.
   *
   * @return unmodifiable list of common body parts
   */
  public static List<BodyPart> getCommonBodyParts() {
    return getBodyParts(BodyPart::isCommon);
  }

  /**
   * Returns all endoscopic body parts.
   *
   * @return unmodifiable list of endoscopic body parts
   */
  public static List<BodyPart> getEndoscopicBodyParts() {
    return getBodyParts(BodyPart::isEndoscopic);
  }

  /**
   * Filters body parts using the provided predicate.
   *
   * @param filter predicate to filter body parts
   * @return unmodifiable list of matching body parts
   */
  public static List<BodyPart> getBodyParts(Predicate<BodyPart> filter) {
    return Stream.of(BodyPart.values()).filter(filter).toList();
  }

  /**
   * Finds a body part by its SNOMED CT code value.
   *
   * @param code the code value to look up
   * @return the matching body part, or {@code null} if not found
   */
  public static BodyPart getBodyPartFromCode(String code) {
    return CODE_TO_BODY_PART.get(code);
  }

  /**
   * Finds a body part by its legacy DICOM Body Part Examined code.
   *
   * @param legacyCode the legacy code to look up
   * @return the matching body part, or {@code null} if not found
   */
  public static BodyPart getBodyPartFromLegacyCode(String legacyCode) {
    return StringUtil.hasText(legacyCode) ? LEGACY_CODE_TO_BODY_PART.get(legacyCode.trim()) : null;
  }

  /**
   * Finds the first body part matching the given predicate.
   *
   * @param filter predicate to match against body parts
   * @return the first matching body part, or {@code null} if none found
   */
  public static BodyPart getBodyPartFromPredicate(Predicate<BodyPart> filter) {
    return Stream.of(BodyPart.values()).filter(filter).findFirst().orElse(null);
  }

  /**
   * Finds a body part by its human-readable code meaning.
   *
   * @param codeMeaning the code meaning to look up
   * @return the matching body part, or {@code null} if not found
   */
  public static BodyPart getBodyPartFromCodeMeaning(String codeMeaning) {
    if (!StringUtil.hasText(codeMeaning)) {
      return null;
    }
    String trimmedMeaning = codeMeaning.trim();
    return getBodyPartFromPredicate(bp -> Objects.equals(trimmedMeaning, bp.getCodeMeaning()));
  }

  /**
   * Finds an anatomical modifier by its code value.
   *
   * @param code the code value to look up
   * @return the matching modifier, or {@code null} if not found
   */
  public static AnatomicModifier getAnatomicModifierFromCode(String code) {
    return CODE_TO_ANATOMIC_MODIFIER.get(code);
  }

  /**
   * Finds a surface part by its code value.
   *
   * @param code the code value to look up
   * @return the matching surface part, or {@code null} if not found
   */
  public static SurfacePart getSurfacePartFromCode(String code) {
    return CODE_TO_SURFACE_PART.get(code);
  }

  /**
   * Returns all available surface parts.
   *
   * @return unmodifiable list of all surface parts
   */
  public static List<SurfacePart> getAllSurfaceParts() {
    return List.of(SurfacePart.values());
  }

  /**
   * Returns all available anatomical modifiers.
   *
   * @return unmodifiable list of all modifiers
   */
  public static List<AnatomicModifier> getAllAnatomicModifiers() {
    return List.of(AnatomicModifier.values());
  }
}
