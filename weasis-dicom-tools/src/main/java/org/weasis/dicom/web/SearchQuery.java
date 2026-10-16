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
import java.util.Arrays;
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
 * Search Query Parameters shared by the QIDO-RS, UPS-RS and MWL-RS Search transactions (PS3.18
 * §8.3.4). Attribute IDs are keywords or tags, with dot-separated paths for sequence items (e.g.
 * {@code ScheduledStationNameCodeSequence.CodeValue}).
 *
 * @param <T> the concrete query type, returned by the fluent setters
 */
public abstract class SearchQuery<T extends SearchQuery<T>> {

  private final Map<String, List<String>> matches = new LinkedHashMap<>();
  private final List<String> includeFields = new ArrayList<>();
  private Integer limit;
  private Integer offset;
  private boolean fuzzyMatching;

  protected abstract T self();

  /**
   * Adds a matching key; several values are a list to match (PS3.18 §8.3.4.1). No value requests
   * the attribute without constraining it.
   */
  public T match(String attributeId, String... values) {
    Objects.requireNonNull(attributeId, "Attribute ID cannot be null");
    List<String> list =
        values == null
            ? List.of()
            : Arrays.stream(values).filter(v -> v != null && !v.isEmpty()).toList();
    if (list.isEmpty()) {
      includeFields.add(attributeId);
    } else {
      matches.put(attributeId, list);
    }
    return self();
  }

  /** Adds a matching key identified by its tag, see {@link #match(String, String...)}. */
  public T match(int tag, String... values) {
    return match(TagUtils.toHexString(tag), values);
  }

  /** Adds every attribute of {@code keys} as matching keys, flattening sequence items. */
  public T match(Attributes keys) {
    toMatchingKeys(keys).forEach((id, values) -> match(id, values.toArray(String[]::new)));
    return self();
  }

  public T includeField(String attributeId) {
    includeFields.add(Objects.requireNonNull(attributeId));
    return self();
  }

  public T includeAllFields() {
    return includeField("all");
  }

  public T limit(int limit) {
    this.limit = limit;
    return self();
  }

  public T offset(int offset) {
    this.offset = offset;
    return self();
  }

  /**
   * Requests fuzzy matching of Person Name values (PS3.18 §8.3.4.2). An origin server without
   * support performs literal matching and returns a {@code 299} {@link HttpWarning}.
   */
  public T fuzzyMatching(boolean fuzzyMatching) {
    this.fuzzyMatching = fuzzyMatching;
    return self();
  }

  /** Returns the encoded query string, without leading {@code ?}. */
  public String toQueryString() {
    List<String> params = new ArrayList<>();
    matches.forEach(
        (k, v) ->
            params.add(
                encode(k)
                    + "="
                    + v.stream().map(SearchQuery::encode).collect(Collectors.joining(","))));
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

  static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  static Map<String, List<String>> toMatchingKeys(Attributes keys) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    addMatchingKeys(keys, "", result);
    return result;
  }

  private static void addMatchingKeys(
      Attributes attrs, String prefix, Map<String, List<String>> result) {
    for (int tag : attrs.tags()) {
      String id = prefix + attributeId(attrs, tag);
      if (attrs.getVR(tag) == VR.SQ) {
        Sequence seq = attrs.getSequence(tag);
        if (seq == null || seq.isEmpty()) {
          result.put(id, List.of());
        } else {
          addMatchingKeys(seq.get(0), id + ".", result);
        }
      } else {
        String[] values = attrs.getStrings(tag);
        result.put(id, values == null ? List.of() : Arrays.asList(values));
      }
    }
  }

  private static String attributeId(Attributes attrs, int tag) {
    String keyword = ElementDictionary.keywordOf(tag, attrs.getPrivateCreator(tag));
    return keyword == null || keyword.isEmpty() ? TagUtils.toHexString(tag) : keyword;
  }
}
