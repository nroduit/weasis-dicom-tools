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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
 *   <li>Registration of custom categories and of private extensions of standard context groups
 *       through {@link #registerCategory}
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
   * Private extension of a standard {@link Category}: it keeps the context UID and the identifier
   * of the extended context group and adds private codes identified by an extension creator UID.
   *
   * <p>Items flagged with {@link AnatomicItem#isContextGroupExtension()} are written with the
   * Context Group Extension attributes, as required for a private extension of a standard context
   * group.
   */
  public static final class ExtendedCategory implements CategoryBuilder {

    private final Category baseCategory;
    private final String extensionCreatorUID;
    private final String title;

    /**
     * @param baseCategory the standard context group being extended, must not be null
     * @param extensionCreatorUID the UID of the organization that created the private codes, must
     *     not be null
     * @param title the human-readable title, must not be null
     */
    public ExtendedCategory(Category baseCategory, String extensionCreatorUID, String title) {
      this.baseCategory = Objects.requireNonNull(baseCategory, "baseCategory must not be null");
      this.extensionCreatorUID =
          Objects.requireNonNull(extensionCreatorUID, "extensionCreatorUID must not be null");
      this.title = Objects.requireNonNull(title, "title must not be null");
    }

    @Override
    public String getContextUID() {
      return baseCategory.getContextUID();
    }

    @Override
    public String getIdentifier() {
      return baseCategory.getIdentifier();
    }

    @Override
    public String getTitle() {
      return title;
    }

    /**
     * Returns the standard context group extended by this category.
     *
     * @return the extended category
     */
    public Category getBaseCategory() {
      return baseCategory;
    }

    /**
     * Returns the value written in Context Group Extension Creator UID.
     *
     * @return the extension creator UID
     */
    public String getExtensionCreatorUID() {
      return extensionCreatorUID;
    }

    @Override
    public String toString() {
      return title;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ExtendedCategory other
          && baseCategory == other.baseCategory
          && extensionCreatorUID.equals(other.extensionCreatorUID);
    }

    @Override
    public int hashCode() {
      return Objects.hash(baseCategory, extensionCreatorUID);
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
  }

  // Category-to-items mapping for efficient access
  private static final Map<CategoryBuilder, List<AnatomicItem>> STANDARD_CATEGORIES =
      createCategoryMap();
  private static final AtomicReference<Map<CategoryBuilder, List<AnatomicItem>>>
      EXTENSION_CATEGORIES = new AtomicReference<>(Map.of());

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

  // Create immutable lookup maps
  private static Map<CategoryBuilder, List<AnatomicItem>> createCategoryMap() {
    Map<CategoryBuilder, List<AnatomicItem>> map = new LinkedHashMap<>();
    map.put(Category.SURFACE, List.of(SurfacePart.values()));
    map.put(Category.ALL_REGIONS, List.of(BodyPart.values()));
    map.put(Category.COMMON, filterBodyParts(BodyPart::isCommon));
    map.put(Category.ENDOSCOPY, filterBodyParts(BodyPart::isEndoscopic));
    return Collections.unmodifiableMap(map);
  }

  /**
   * Returns the standard categories plus the registered ones, in that order.
   *
   * @return unmodifiable snapshot of the category-to-items mapping
   */
  public static Map<CategoryBuilder, List<AnatomicItem>> getCategoryMap() {
    Map<CategoryBuilder, List<AnatomicItem>> extensions = EXTENSION_CATEGORIES.get();
    if (extensions.isEmpty()) {
      return STANDARD_CATEGORIES;
    }
    Map<CategoryBuilder, List<AnatomicItem>> merged = new LinkedHashMap<>(STANDARD_CATEGORIES);
    merged.putAll(extensions);
    return Collections.unmodifiableMap(merged);
  }

  /**
   * Returns the items of a category, standard or registered.
   *
   * @param category the category to look up, may be {@code null}
   * @return unmodifiable list of items, empty if the category is unknown
   */
  public static List<AnatomicItem> getCategoryItems(CategoryBuilder category) {
    if (category == null) {
      return List.of();
    }
    List<AnatomicItem> items = STANDARD_CATEGORIES.get(category);
    if (items == null) {
      items = EXTENSION_CATEGORIES.get().get(category);
    }
    return items == null ? List.of() : items;
  }

  /**
   * Finds a category by its context UID among the standard and the registered ones.
   *
   * @param contextUID the context UID to look up
   * @return an Optional containing the matching category, or empty if not found
   */
  public static Optional<CategoryBuilder> getCategoryFromContextUID(String contextUID) {
    CategoryBuilder standard = Category.fromContextUID(contextUID).orElse(null);
    if (standard != null) {
      return Optional.of(standard);
    }
    return EXTENSION_CATEGORIES.get().keySet().stream()
        .filter(c -> c.getContextUID().equals(contextUID))
        .findFirst();
  }

  /**
   * Finds a registered {@link ExtendedCategory} by the UID written in Context Group Extension
   * Creator UID.
   *
   * @param extensionCreatorUID the extension creator UID to look up
   * @return an Optional containing the matching extension, or empty if none is registered
   */
  public static Optional<ExtendedCategory> getExtendedCategory(String extensionCreatorUID) {
    if (!StringUtil.hasText(extensionCreatorUID)) {
      return Optional.empty();
    }
    return EXTENSION_CATEGORIES.get().keySet().stream()
        .filter(ExtendedCategory.class::isInstance)
        .map(ExtendedCategory.class::cast)
        .filter(c -> extensionCreatorUID.equals(c.getExtensionCreatorUID()))
        .findFirst();
  }

  /**
   * Registers a custom category and its items, replacing any category previously registered with
   * the same context UID.
   *
   * @param category the custom category, must not be null
   * @param items the items of this category, must not be null or contain null
   * @throws IllegalArgumentException if the context UID is already used by a standard {@link
   *     Category}, unless the category is an {@link ExtendedCategory} of that context group
   */
  public static void registerCategory(CategoryBuilder category, List<AnatomicItem> items) {
    Objects.requireNonNull(category, "category must not be null");
    Objects.requireNonNull(items, "items must not be null");
    List<AnatomicItem> copy = List.copyOf(items);
    if (!(category instanceof ExtendedCategory)
        && Category.fromContextUID(category.getContextUID()).isPresent()) {
      throw new IllegalArgumentException(
          "Standard categories cannot be overridden: " + category.getContextUID());
    }

    EXTENSION_CATEGORIES.updateAndGet(
        current -> {
          Map<CategoryBuilder, List<AnatomicItem>> updated = new LinkedHashMap<>(current);
          updated.remove(category); // keep the new insertion order when replacing an existing entry
          updated.put(category, copy);
          return Collections.unmodifiableMap(updated);
        });
  }

  /**
   * Removes a previously registered custom category.
   *
   * @param category the category to remove
   * @return true if the category was registered
   */
  public static boolean unregisterCategory(CategoryBuilder category) {
    if (category == null) {
      return false;
    }
    Map<CategoryBuilder, List<AnatomicItem>> previous =
        EXTENSION_CATEGORIES.getAndUpdate(
            current -> {
              if (!current.containsKey(category)) {
                return current;
              }
              Map<CategoryBuilder, List<AnatomicItem>> updated = new LinkedHashMap<>(current);
              updated.remove(category);
              return Collections.unmodifiableMap(updated);
            });
    return previous.containsKey(category);
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
