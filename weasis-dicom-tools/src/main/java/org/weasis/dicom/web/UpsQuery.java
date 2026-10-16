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

import java.util.stream.Collectors;
import org.dcm4che3.data.Attributes;

/** Query parameters of the UPS-RS Search transaction (PS3.18 §11.9 and §8.3.4). */
public final class UpsQuery extends SearchQuery<UpsQuery> {

  @Override
  protected UpsQuery self() {
    return this;
  }

  /** Encodes {@code keys} as the {@code filter} value of a Filtered Global Subscription. */
  static String toFilter(Attributes keys) {
    return toMatchingKeys(keys).entrySet().stream()
        .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
        .collect(Collectors.joining(","));
  }
}
