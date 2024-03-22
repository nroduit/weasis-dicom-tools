/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.lut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.opencv.op.lut.LutShape;

class PresetWindowLevelTest {

  @Test
  @DisplayName("Verify PresetWindowLevel constructor")
  void testConstructor() {
    PresetWindowLevel presetWindowLevel = new PresetWindowLevel("Test", 4.0, 2.0, LutShape.LINEAR);
    presetWindowLevel.setKeyCode(0x30);

    assertEquals(0x30, presetWindowLevel.getKeyCode());
    assertTrue(presetWindowLevel.isAutoLevel());
    assertEquals("Test", presetWindowLevel.getName());
    assertEquals("Test", presetWindowLevel.toString());
    assertEquals(4.0, presetWindowLevel.getWindow());
    assertEquals(2.0, presetWindowLevel.getLevel());
    assertEquals(LutShape.LINEAR, presetWindowLevel.getLutShape());
    assertEquals(0.0, presetWindowLevel.getMinBox());
    assertEquals(4.0, presetWindowLevel.getMaxBox());

    presetWindowLevel.setKeyCode(0x31);
    assertFalse(presetWindowLevel.isAutoLevel());

    assertThrows(
        NullPointerException.class, () -> new PresetWindowLevel(null, 1.0, 2.0, LutShape.LINEAR));
    assertThrows(
        NullPointerException.class, () -> new PresetWindowLevel(null, 1.0, 2.0, LutShape.LINEAR));
    assertThrows(
        NullPointerException.class,
        () -> new PresetWindowLevel("Test", null, 2.0, LutShape.LINEAR));
    assertThrows(
        NullPointerException.class,
        () -> new PresetWindowLevel("Test", 1.0, null, LutShape.LINEAR));
    assertThrows(NullPointerException.class, () -> new PresetWindowLevel("Test", 1.0, 2.0, null));

    assertThrows(
        IllegalArgumentException.class,
        () -> PresetWindowLevel.getPresetCollection(null, "Test", null));
  }

  @Test
  @DisplayName("Verify equals")
  void testEquals() {
    PresetWindowLevel preset1 = new PresetWindowLevel("Test", 1.0, 2.0, LutShape.LINEAR);
    PresetWindowLevel preset2 = new PresetWindowLevel("Test", 1.0, 2.0, LutShape.LINEAR);
    assertEquals(preset1, preset2);
    assertEquals(preset1.hashCode(), preset2.hashCode());
    assertNotEquals(preset1, null);

    preset2 = new PresetWindowLevel("Test2", 1.0, 2.0, LutShape.LINEAR);
    assertNotEquals(preset1, preset2);
    assertNotEquals(preset1.hashCode(), preset2.hashCode());

    preset2 = new PresetWindowLevel("Test", 3.0, 2.0, LutShape.LINEAR);
    assertNotEquals(preset1, preset2);
    assertNotEquals(preset1.hashCode(), preset2.hashCode());
  }
}
