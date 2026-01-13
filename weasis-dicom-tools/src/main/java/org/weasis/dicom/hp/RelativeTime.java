/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp;

import java.time.LocalDateTime;
import java.util.Objects;

public class RelativeTime {

  private final int[] values;
  private final RelativeTimeUnits units;

  public RelativeTime() {
    this(0, 0, RelativeTimeUnits.SECONDS);
  }

  public RelativeTime(int start, int end, RelativeTimeUnits units) {
    this(new int[] {start, end}, units);
  }

  RelativeTime(int[] value, RelativeTimeUnits units) {
    this.values = Objects.requireNonNullElse(value, new int[2]);
    if (this.values.length != 2) {
      throw new IllegalArgumentException("values must have a length of 2");
    }
    this.units = units;
  }

  final int[] getValues() {
    return values;
  }

  public final int getStart() {
    return values[0];
  }

  public final int getEnd() {
    return values[1];
  }

  public LocalDateTime getStartDate() {
    return toDate(values[0]);
  }

  public LocalDateTime getEndDate() {
    return toDate(values[1]);
  }

  private LocalDateTime toDate(int value) {
    return LocalDateTime.now().minus(value, units.getChronoUnit());
  }

  public final boolean isCurrentTime() {
    return values[0] == 0 && values[1] == 0;
  }

  public final RelativeTimeUnits getUnits() {
    return units;
  }
}
