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

import java.util.List;
import org.dcm4che3.data.Attributes;

/**
 * Outcome of a successful Search transaction (PS3.18 §8.3.4).
 *
 * @param matches the matching data sets, empty on HTTP 204
 * @param warnings the {@code Warning} headers returned by the origin server (PS3.18 §8.6.1.2), e.g.
 *     when fuzzy matching was requested but literal matching was performed
 */
public record SearchResponse(List<Attributes> matches, List<String> warnings) {

  public SearchResponse {
    matches = matches == null ? List.of() : List.copyOf(matches);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }
}
