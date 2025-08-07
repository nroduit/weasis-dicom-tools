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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

public class RelativeTimeUnitsTest {

  @Test
  public void shouldReturnCorrectChronoUnit() {
    assertEquals(ChronoUnit.SECONDS, RelativeTimeUnits.SECONDS.getChronoUnit());
    assertEquals(ChronoUnit.MINUTES, RelativeTimeUnits.MINUTES.getChronoUnit());
    assertEquals(ChronoUnit.HOURS, RelativeTimeUnits.HOURS.getChronoUnit());
    assertEquals(ChronoUnit.DAYS, RelativeTimeUnits.DAYS.getChronoUnit());
    assertEquals(ChronoUnit.WEEKS, RelativeTimeUnits.WEEKS.getChronoUnit());
    assertEquals(ChronoUnit.MONTHS, RelativeTimeUnits.MONTHS.getChronoUnit());
    assertEquals(ChronoUnit.YEARS, RelativeTimeUnits.YEARS.getChronoUnit());
  }

  @Test
  public void shouldReturnCorrectCodeString() {
    assertEquals("SECONDS", RelativeTimeUnits.SECONDS.getCodeString());
    assertEquals("MINUTES", RelativeTimeUnits.MINUTES.getCodeString());
    assertEquals("HOURS", RelativeTimeUnits.HOURS.getCodeString());
    assertEquals("DAYS", RelativeTimeUnits.DAYS.getCodeString());
    assertEquals("WEEKS", RelativeTimeUnits.WEEKS.getCodeString());
    assertEquals("MONTHS", RelativeTimeUnits.MONTHS.getCodeString());
    assertEquals("YEARS", RelativeTimeUnits.YEARS.getCodeString());
  }
}
