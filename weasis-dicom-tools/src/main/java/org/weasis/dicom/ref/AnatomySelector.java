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

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.weasis.core.util.StringUtil;

/**
 * Anatomy named by a person or a script: a {@link RegionGroup} ({@code CHEST}), a coded value
 * ({@code SCT:816094009}, or a retired {@code SRT:T-28000} resolved to its SNOMED CT code) or a
 * Body Part Examined term ({@code THORAX}), resolved on input to a group or a coded region.
 *
 * <p>A bare word is a group first, then a Body Part Examined term: {@code CHEST} is the group,
 * which contains the region the term {@code CHEST} names.
 */
public sealed interface AnatomySelector {

  /** Whether the region is selected; false for a null region. */
  boolean matches(AnatomicRegion region);

  /** The canonical notation: the group name or {@code SCHEME:code}. */
  String notation();

  /** Selects the regions of a group. */
  record Group(RegionGroup group) implements AnatomySelector {
    public Group {
      Objects.requireNonNull(group);
    }

    @Override
    public boolean matches(AnatomicRegion region) {
      return region != null && region.isIn(group);
    }

    @Override
    public String notation() {
      return group.name();
    }
  }

  /** Selects one coded region. */
  record Code(CodingScheme scheme, String codeValue) implements AnatomySelector {
    public Code {
      Objects.requireNonNull(scheme);
      if (!StringUtil.hasText(codeValue)) {
        throw new IllegalArgumentException("A code value cannot be empty");
      }
      codeValue = codeValue.trim();
    }

    @Override
    public boolean matches(AnatomicRegion region) {
      if (region == null) {
        return false;
      }
      AnatomicItem item = region.getRegion();
      return scheme == item.getCodingScheme() && codeValue.equals(item.getCodeValue());
    }

    @Override
    public String notation() {
      return scheme.getDesignator() + ":" + codeValue;
    }
  }

  /**
   * Resolves one token of the notation.
   *
   * @return empty when the token is blank or names no group, coding scheme or known term
   */
  static Optional<AnatomySelector> parse(String token) {
    if (!StringUtil.hasText(token)) {
      return Optional.empty();
    }
    String text = token.trim();
    int colon = text.indexOf(':');
    if (colon > 0 && colon < text.length() - 1) {
      String designator = text.substring(0, colon).trim();
      String code = text.substring(colon + 1);
      if (AnatomicBuilder.RETIRED_SNOMED_RT.equalsIgnoreCase(designator)) {
        return Optional.ofNullable(AnatomicBuilder.getBodyPartFromRetiredSrtCode(code))
            .map(p -> new Code(p.getCodingScheme(), p.getCodeValue()));
      }
      return CodingScheme.fromDesignator(designator).map(s -> new Code(s, code));
    }
    Optional<RegionGroup> group = RegionGroup.fromName(text);
    if (group.isPresent()) {
      return Optional.of(new Group(group.get()));
    }
    BodyPart part = AnatomicBuilder.getBodyPartFromLegacyCode(text.toUpperCase(Locale.ROOT));
    return Optional.ofNullable(part).map(p -> new Code(p.getCodingScheme(), p.getCodeValue()));
  }
}
