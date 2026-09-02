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
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.Comparator;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.util.StringUtil;

/**
 * The anatomy of an image as a JSON object, for command output and exports: {@code {"scheme":
 * "SCT", "code": "816094009", "meaning": "Chest", "bodyPartExamined": "CHEST", "groups": ["CHEST"],
 * "modifiers": [], "laterality": null}}. {@code scheme} and {@code code} are null for a {@link
 * BodyPartTerm}; groups are in {@link RegionGroup} order.
 */
public final class AnatomyJson {

  private AnatomyJson() {}

  /**
   * @param laterality Image Laterality (0020,0062) or Laterality (0020,0060), null when absent
   */
  public static JsonObject toJson(AnatomicRegion region, String laterality) {
    AnatomicItem item = region.getRegion();
    JsonObjectBuilder b = Json.createObjectBuilder();
    CodingScheme scheme = item.getCodingScheme();
    addOrNull(b, "scheme", scheme == null ? null : scheme.getDesignator()); // NON-NLS
    addOrNull(b, "code", item.getCodeValue()); // NON-NLS
    addOrNull(b, "meaning", item.getCodeMeaning()); // NON-NLS
    addOrNull(b, "bodyPartExamined", item.getLegacyCode()); // NON-NLS

    JsonArrayBuilder groups = Json.createArrayBuilder();
    region.getGroups().stream().sorted().forEach(g -> groups.add(g.name()));
    b.add("groups", groups); // NON-NLS

    JsonArrayBuilder modifiers = Json.createArrayBuilder();
    region.getModifiers().stream()
        .sorted(Comparator.comparing(AnatomicModifier::getCodeValue))
        .forEach(
            m ->
                modifiers.add(
                    Json.createObjectBuilder()
                        .add("scheme", m.getCodingScheme().getDesignator()) // NON-NLS
                        .add("code", m.getCodeValue()) // NON-NLS
                        .add("meaning", m.getCodeMeaning()))); // NON-NLS
    b.add("modifiers", modifiers); // NON-NLS
    addOrNull(
        b, "laterality", StringUtil.hasText(laterality) ? laterality.trim() : null); // NON-NLS
    return b.build();
  }

  /**
   * The anatomy of a DICOM object, with Image Laterality, else Laterality.
   *
   * @return null when the object carries no anatomy
   */
  public static JsonObject read(Attributes dcm) {
    AnatomicRegion region = AnatomicRegion.read(dcm);
    if (region == null) {
      return null;
    }
    return toJson(region, dcm.getString(Tag.ImageLaterality, dcm.getString(Tag.Laterality)));
  }

  private static void addOrNull(JsonObjectBuilder b, String name, String value) {
    if (value == null) {
      b.addNull(name);
    } else {
      b.add(name, value);
    }
  }
}
