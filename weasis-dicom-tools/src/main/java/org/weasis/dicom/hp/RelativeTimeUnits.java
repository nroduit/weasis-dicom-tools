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

import java.time.temporal.ChronoUnit;

public enum RelativeTimeUnits {
  SECONDS("SECONDS", ChronoUnit.SECONDS),
  MINUTES("MINUTES", ChronoUnit.MINUTES),
  HOURS("HOURS", ChronoUnit.HOURS),
  DAYS("DAYS", ChronoUnit.DAYS),
  WEEKS("WEEKS", ChronoUnit.WEEKS),
  MONTHS("MONTHS", ChronoUnit.MONTHS),
  YEARS("YEARS", ChronoUnit.YEARS);

  private final ChronoUnit chronoUnit;
  private final String codeString;

  RelativeTimeUnits(String codeString, ChronoUnit chronoUnit) {
    this.codeString = codeString;
    this.chronoUnit = chronoUnit;
  }

  public ChronoUnit getChronoUnit() {
    return chronoUnit;
  }

  public String getCodeString() {
    return codeString;
  }

  @Override
  public String toString() {
    return codeString;
  }
}
