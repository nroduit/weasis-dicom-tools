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

import org.weasis.core.util.StringUtil;

/**
 * A Body Part Examined (0018,0015) value that matches no region of PS3.16 Table L-1, kept as text
 * so it is still shown and written back. It has no code and belongs to no {@link RegionGroup}.
 *
 * @param term the value as read, trimmed
 */
public record BodyPartTerm(String term) implements AnatomicItem {

  public BodyPartTerm {
    if (!StringUtil.hasText(term)) {
      throw new IllegalArgumentException("A body part term cannot be empty");
    }
    term = term.trim();
  }

  /** Always null: the term is not a coded value. */
  @Override
  public String getCodeValue() {
    return null;
  }

  @Override
  public String getCodeMeaning() {
    return term;
  }

  /** Always null: the term is not a coded value. */
  @Override
  public CodingScheme getCodingScheme() {
    return null;
  }

  @Override
  public String getLegacyCode() {
    return term;
  }

  @Override
  public boolean isPaired() {
    return false;
  }
}
