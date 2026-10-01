/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.ref;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.macro.ItemCode;

/**
 * Membership of region codes in {@link RegionGroup}s, read from a JSON document {@code {"schema":
 * 1, "groups": {"CHEST": ["LUNG", "SCT:39607008", ...]}}}. An entry is the name of a {@link
 * BodyPart} or {@link SurfacePart} constant, or a coded value {@code SCHEME:code}; a region may
 * belong to several groups. The built-in table covers every {@link BodyPart}; a site document can
 * be merged on top of it.
 */
public final class RegionGroups {
  private static final Logger LOGGER = LoggerFactory.getLogger(RegionGroups.class);

  public static final int SCHEMA_VERSION = 1;
  static final String BUILTIN_RESOURCE = "regionGroups.json"; // NON-NLS

  private static final String SCHEMA = "schema"; // NON-NLS
  private static final String GROUPS = "groups"; // NON-NLS

  private static final AtomicReference<RegionGroups> defaultGroups = new AtomicReference<>();

  private final Map<String, Set<RegionGroup>> byCode;

  private RegionGroups(Map<String, Set<RegionGroup>> byCode) {
    this.byCode = Map.copyOf(byCode);
  }

  /** The table in use: the built-in one unless {@link #setDefault} installed another. */
  public static RegionGroups getDefault() {
    RegionGroups groups = defaultGroups.get();
    if (groups == null) {
      synchronized (RegionGroups.class) {
        groups = defaultGroups.get();
        if (groups == null) {
          groups = builtIn();
          defaultGroups.set(groups);
        }
      }
    }
    return groups;
  }

  /** Installs the table in use, e.g. the built-in one merged with a site document; null resets. */
  public static void setDefault(RegionGroups groups) {
    defaultGroups.set(groups);
  }

  /** The table bundled with the library. */
  public static RegionGroups builtIn() {
    try (InputStream in = RegionGroups.class.getResourceAsStream(BUILTIN_RESOURCE)) {
      if (in == null) {
        LOGGER.error("Missing resource {}", BUILTIN_RESOURCE);
        return new RegionGroups(Map.of());
      }
      return read(in);
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read the built-in region groups", e);
      return new RegionGroups(Map.of());
    }
  }

  /**
   * Reads a group document. Unknown groups and entries are skipped with a warning.
   *
   * @throws IOException when the document is not JSON or has a newer schema
   */
  public static RegionGroups read(InputStream in) throws IOException {
    JsonObject root;
    try (JsonReader reader = Json.createReader(in)) {
      root = reader.readObject();
    } catch (JsonException | IllegalStateException e) {
      throw new IOException("Invalid region group document", e);
    }
    int schema = root.getInt(SCHEMA, SCHEMA_VERSION);
    if (schema > SCHEMA_VERSION) {
      throw new IOException(
          "Region group schema %d is newer than the supported %d"
              .formatted(schema, SCHEMA_VERSION));
    }
    Map<String, Set<RegionGroup>> byCode = new HashMap<>();
    if (root.get(GROUPS) instanceof JsonObject groups) {
      groups.forEach((name, value) -> readGroup(name, value, byCode));
    }
    return new RegionGroups(byCode);
  }

  private static void readGroup(String name, JsonValue value, Map<String, Set<RegionGroup>> out) {
    Optional<RegionGroup> group = RegionGroup.fromName(name);
    if (group.isEmpty() || !(value instanceof JsonArray entries)) {
      LOGGER.warn("Unknown region group '{}' skipped", name);
      return;
    }
    for (JsonValue entry : entries) {
      String key = entry instanceof JsonString text ? keyOfEntry(text.getString()) : null;
      if (key == null) {
        LOGGER.warn("Region group {}: unknown entry {} skipped", name, entry);
      } else {
        out.computeIfAbsent(key, k -> EnumSet.noneOf(RegionGroup.class)).add(group.get());
      }
    }
  }

  // An enum constant name or SCHEME:code, turned into the key of its coded value
  private static String keyOfEntry(String entry) {
    String text = entry.trim();
    int colon = text.indexOf(':');
    if (colon > 0 && colon < text.length() - 1) {
      return CodingScheme.fromDesignator(text.substring(0, colon))
          .map(s -> key(s, text.substring(colon + 1)))
          .orElse(null);
    }
    try {
      return key(BodyPart.valueOf(text));
    } catch (IllegalArgumentException e) {
      try {
        return key(SurfacePart.valueOf(text));
      } catch (IllegalArgumentException e2) {
        return null;
      }
    }
  }

  /** A table with the memberships of both; the entries of {@code other} add groups. */
  public RegionGroups merge(RegionGroups other) {
    Map<String, Set<RegionGroup>> merged = new HashMap<>();
    byCode.forEach((k, v) -> merged.put(k, EnumSet.copyOf(v)));
    other.byCode.forEach(
        (k, v) -> merged.computeIfAbsent(k, x -> EnumSet.noneOf(RegionGroup.class)).addAll(v));
    return new RegionGroups(merged);
  }

  /** The groups of a coded region; empty for a code outside the table or without a code. */
  public Set<RegionGroup> groupsOf(ItemCode item) {
    if (item == null) {
      return Set.of();
    }
    String key = key(item);
    Set<RegionGroup> groups = key == null ? null : byCode.get(key);
    return groups == null ? Set.of() : Set.copyOf(groups);
  }

  private static String key(ItemCode item) {
    return key(item.getCodingScheme(), item.getCodeValue());
  }

  private static String key(CodingScheme scheme, String codeValue) {
    if (scheme == null || codeValue == null || codeValue.isBlank()) {
      return null;
    }
    return scheme.getDesignator() + ":" + codeValue.trim();
  }
}
