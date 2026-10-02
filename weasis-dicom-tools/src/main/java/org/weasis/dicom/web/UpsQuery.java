/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.TagUtils;

/**
 * Query parameters of the UPS-RS Search transaction (PS3.18 §11.9 and §8.3.4). Matching keys use
 * attribute keywords, with dot-separated paths for sequence items (e.g. {@code
 * ScheduledStationNameCodeSequence.CodeValue}).
 */
public final class UpsQuery {

  private final Map<String, String> matches = new LinkedHashMap<>();
  private final List<String> includeFields = new ArrayList<>();
  private Integer limit;
  private Integer offset;
  private boolean fuzzyMatching;

  /** Adds a matching key; an empty value requests the attribute without constraining it. */
  public UpsQuery match(String attributeId, String value) {
    Objects.requireNonNull(attributeId, "Attribute ID cannot be null");
    if (value == null || value.isEmpty()) {
      includeFields.add(attributeId);
    } else {
      matches.put(attributeId, value);
    }
    return this;
  }

  /** Adds every attribute of {@code keys} as matching keys, flattening sequence items. */
  public UpsQuery match(Attributes keys) {
    toMatchingKeys(keys).forEach(this::match);
    return this;
  }

  public UpsQuery includeField(String attributeId) {
    includeFields.add(Objects.requireNonNull(attributeId));
    return this;
  }

  public UpsQuery includeAllFields() {
    return includeField("all");
  }

  public UpsQuery limit(int limit) {
    this.limit = limit;
    return this;
  }

  public UpsQuery offset(int offset) {
    this.offset = offset;
    return this;
  }

  public UpsQuery fuzzyMatching(boolean fuzzyMatching) {
    this.fuzzyMatching = fuzzyMatching;
    return this;
  }

  /** Returns the encoded query string, without leading {@code ?}. */
  public String toQueryString() {
    List<String> params = new ArrayList<>();
    matches.forEach((k, v) -> params.add(encode(k) + "=" + encode(v)));
    includeFields.forEach(f -> params.add("includefield=" + encode(f)));
    if (fuzzyMatching) {
      params.add("fuzzymatching=true");
    }
    if (limit != null) {
      params.add("limit=" + limit);
    }
    if (offset != null) {
      params.add("offset=" + offset);
    }
    return String.join("&", params);
  }

  /** Encodes {@code keys} as the {@code filter} value of a Filtered Global Subscription. */
  static String toFilter(Attributes keys) {
    return toMatchingKeys(keys).entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(Collectors.joining(","));
  }

  static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  static Map<String, String> toMatchingKeys(Attributes keys) {
    Map<String, String> result = new LinkedHashMap<>();
    addMatchingKeys(keys, "", result);
    return result;
  }

  private static void addMatchingKeys(Attributes attrs, String prefix, Map<String, String> result) {
    for (int tag : attrs.tags()) {
      String id = prefix + attributeId(attrs, tag);
      if (attrs.getVR(tag) == VR.SQ) {
        Sequence seq = attrs.getSequence(tag);
        if (seq == null || seq.isEmpty()) {
          result.put(id, "");
        } else {
          addMatchingKeys(seq.get(0), id + ".", result);
        }
      } else {
        String[] values = attrs.getStrings(tag);
        result.put(id, values == null ? "" : String.join(",", values));
      }
    }
  }

  private static String attributeId(Attributes attrs, int tag) {
    String keyword = ElementDictionary.keywordOf(tag, attrs.getPrivateCreator(tag));
    return keyword == null || keyword.isEmpty() ? TagUtils.toHexString(tag) : keyword;
  }
}
