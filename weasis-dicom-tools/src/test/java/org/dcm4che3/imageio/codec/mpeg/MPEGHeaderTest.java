/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec.mpeg;

import static org.junit.jupiter.api.Assertions.*;

import org.dcm4che3.data.Attributes;
import org.junit.jupiter.api.Test;

class MPEGHeaderTest {

  @Test
  void testToAttributesWithValidData() {
    // Simulated MPEG header data with a valid sequence header
    byte[] validData = {
      0x00, 0x00, 0x01, (byte) 0xB3, // Sequence header start code
      0x20, 0x01, (byte) 0xE0, 0x10, // Width=512, Height=480, frame rate
      0x3F, (byte) 0xFF, (byte) 0xC0, 0x00 // Bitrate and dummy data
    };

    MPEGHeader mpegHeader = new MPEGHeader(validData);
    assertTrue(mpegHeader.isValid());

    Attributes attributes = new Attributes();
    mpegHeader.toAttributes(attributes, 10000L);

    assertEquals(480, attributes.getInt(org.dcm4che3.data.Tag.Rows, 0));
    assertEquals(512, attributes.getInt(org.dcm4che3.data.Tag.Columns, 0));
    assertArrayEquals(
        new String[] {"16", "9"}, attributes.getStrings(org.dcm4che3.data.Tag.PixelAspectRatio));
    assertEquals(9_999, attributes.getInt(org.dcm4che3.data.Tag.NumberOfFrames, 0));
  }

  @Test
  void testToAttributesWithInvalidData() {
    // Simulated invalid MPEG header data
    byte[] invalidData = {0x00, 0x00, 0x01, 0x00}; // No sequence header start code
    MPEGHeader mpegHeader = new MPEGHeader(invalidData);
    assertFalse(mpegHeader.isValid());

    Attributes attributes = new Attributes();
    assertNull(mpegHeader.toAttributes(attributes, 10000L));
  }

  @Test
  void testToAttributesWithNullAttributes() {
    // Simulated MPEG header data with a valid sequence header
    byte[] validData = {
      0x00, 0x00, 0x01, (byte) 0xB3, // Sequence header start code
      0x20, 0x01, (byte) 0xE0, 0x10, // Width=512, Height=480, frame rate
      0x3F, (byte) 0xFF, (byte) 0xC0, 0x00 // Bitrate and dummy data
    };
    MPEGHeader mpegHeader = new MPEGHeader(validData);
    assertTrue(mpegHeader.isValid());

    // Passing null Attributes, expecting a new Attributes object to be created
    Attributes attributes = mpegHeader.toAttributes(null, 10000L);
    assertNotNull(attributes);

    assertEquals(480, attributes.getInt(org.dcm4che3.data.Tag.Rows, 0));
    assertEquals(512, attributes.getInt(org.dcm4che3.data.Tag.Columns, 0));
    assertArrayEquals(
        new String[] {"16", "9"}, attributes.getStrings(org.dcm4che3.data.Tag.PixelAspectRatio));
  }
}
