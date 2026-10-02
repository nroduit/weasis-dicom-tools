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
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of a successful UPS-RS transaction.
 *
 * @param statusCode the HTTP status code (2xx)
 * @param location the {@code Content-Location} or {@code Location} header, if any
 * @param warnings the {@code Warning} headers returned by the origin server (PS3.18 §8.6.1.2)
 */
public record UpsResponse(int statusCode, Optional<String> location, List<String> warnings) {

  public UpsResponse {
    Objects.requireNonNull(location, "Location cannot be null");
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }

  /** Returns the last path segment of {@link #location()}, i.e. the workitem UID on creation. */
  public Optional<String> workitemUid() {
    return location.map(
        l -> {
          String path = l.endsWith("/") ? l.substring(0, l.length() - 1) : l;
          return path.substring(path.lastIndexOf('/') + 1);
        });
  }
}
