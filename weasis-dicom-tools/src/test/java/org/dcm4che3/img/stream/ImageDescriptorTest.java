/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.PhotometricInterpretation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageDescriptorTest {

  @Test
  @DisplayName("Create ImageDescriptor with valid attributes")
  void shouldCreateImageDescriptorWithValidAttributes() {
    Attributes attributes = new Attributes();
    attributes.setInt(Tag.Rows, VR.US, 512);
    attributes.setInt(Tag.Columns, VR.US, 512);
    attributes.setInt(Tag.SamplesPerPixel, VR.US, 1);
    attributes.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
    attributes.setInt(Tag.PlanarConfiguration, VR.US, 1);
    attributes.setInt(Tag.BitsAllocated, VR.US, 16);
    attributes.setInt(Tag.BitsStored, VR.US, 16);
    attributes.setInt(Tag.HighBit, VR.US, 15);
    attributes.setInt(Tag.PixelRepresentation, VR.US, 1);
    attributes.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
    attributes.setString(Tag.BodyPartExamined, VR.CS, "HEAD");
    attributes.setInt(Tag.NumberOfFrames, VR.IS, 1);
    attributes.setString(Tag.PixelPresentation, VR.CS, "COLOR");
    attributes.setString(Tag.StationName, VR.SH, "STATION1");

    ImageDescriptor descriptor = new ImageDescriptor(attributes);

    assertEquals(512, descriptor.getRows());
    assertEquals(512, descriptor.getColumns());
    assertEquals(1, descriptor.getSamples());
    assertEquals(PhotometricInterpretation.MONOCHROME2, descriptor.getPhotometricInterpretation());
    assertEquals(1, descriptor.getPlanarConfiguration());
    assertEquals(16, descriptor.getBitsAllocated());
    assertEquals(16, descriptor.getBitsStored());
    assertEquals(15, descriptor.getHighBit());
    assertEquals(1, descriptor.getPixelRepresentation());
    assertEquals("1.2.840.10008.5.1.4.1.1.2", descriptor.getSopClassUID());
    assertEquals("HEAD", descriptor.getBodyPartExamined());
    assertEquals(1, descriptor.getFrames());
    assertEquals(1, descriptor.getPixelRepresentation());
    assertEquals("COLOR", descriptor.getPixelPresentation());
    assertTrue(descriptor.hasPaletteColorLookupTable());
    assertEquals("STATION1", descriptor.getStationName());
    assertFalse(descriptor.isMultiframe());
    assertFalse(descriptor.isMultiframeWithEmbeddedOverlays());
  }

  @Test
  @DisplayName("Verify getLength returns correct length")
  void shouldReturnCorrectLength() {
    Attributes attributes = new Attributes();
    attributes.setInt(Tag.Rows, VR.US, 2);
    attributes.setInt(Tag.Columns, VR.US, 2);
    attributes.setInt(Tag.SamplesPerPixel, VR.US, 3);
    attributes.setInt(Tag.BitsAllocated, VR.US, 8);
    attributes.setInt(Tag.NumberOfFrames, VR.IS, 5);
    ImageDescriptor descriptor = new ImageDescriptor(attributes);
    assertTrue(descriptor.isMultiframe());
    assertEquals(12, descriptor.getFrameLength());
    assertEquals(60, descriptor.getLength());
  }

  @Test
  @DisplayName("Create ImageDescriptor with missing attributes")
  void shouldCreateImageDescriptorWithMissingAttributes() {
    Attributes attributes = new Attributes();

    ImageDescriptor descriptor = new ImageDescriptor(attributes);

    assertEquals(0, descriptor.getRows());
    assertEquals(0, descriptor.getColumns());
    assertEquals(0, descriptor.getSamples());
    assertEquals(PhotometricInterpretation.MONOCHROME2, descriptor.getPhotometricInterpretation());
    assertEquals(0, descriptor.getPlanarConfiguration());
    assertEquals(8, descriptor.getBitsAllocated());
    assertEquals(8, descriptor.getBitsStored());
    assertEquals(7, descriptor.getHighBit());
    assertEquals(0, descriptor.getPixelRepresentation());
    assertNull(descriptor.getSopClassUID());
    assertNull(descriptor.getBodyPartExamined());
    assertEquals(1, descriptor.getFrames());
    assertEquals(0, descriptor.getPixelRepresentation());
    assertNull(descriptor.getModality());
    assertNull(descriptor.getPixelPaddingValue());
    assertNull(descriptor.getPixelPaddingRangeLimit());
    assertNull(descriptor.getPresentationLUTShape());
    assertNull(descriptor.getPixelPresentation());
  }

  @Test
  @DisplayName("Check if pixel data is float or double")
  void testIsFloatPixelData() {
    Attributes attributes = new Attributes();
    attributes.setInt(Tag.BitsAllocated, VR.US, 32);
    attributes.setString(Tag.Modality, VR.CS, "RTDOSE");

    ImageDescriptor descriptor = new ImageDescriptor(attributes);
    assertFalse(descriptor.isFloatPixelData());

    attributes.setString(Tag.Modality, VR.CS, "RF");
    descriptor = new ImageDescriptor(attributes);
    assertTrue(descriptor.isFloatPixelData());

    attributes.setInt(Tag.BitsAllocated, VR.US, 64);
    descriptor = new ImageDescriptor(attributes);
    assertTrue(descriptor.isFloatPixelData());

    attributes.setInt(Tag.BitsAllocated, VR.US, 16);
    descriptor = new ImageDescriptor(attributes);
    assertFalse(descriptor.isFloatPixelData());
  }
}
