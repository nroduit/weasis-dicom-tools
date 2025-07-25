/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

/** Creates valid DICOM LUT data for testing purposes */
public class LutTestDataBuilder {

  public static Attributes createLinearLut8Bit() {
    Attributes lutAttributes = new Attributes();

    // Descriptor: 256 entries, start at 0, 8 bits per entry
    lutAttributes.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);

    // Linear identity mapping
    byte[] lutData = new byte[256];
    for (int i = 0; i < 256; i++) {
      lutData[i] = (byte) i;
    }
    lutAttributes.setBytes(Tag.LUTData, VR.OB, lutData);
    lutAttributes.setString(Tag.ModalityLUTType, VR.LO, "US");
    lutAttributes.setString(Tag.LUTExplanation, VR.LO, "Linear 8-bit mapping");

    return lutAttributes;
  }

  public static Attributes createCtHounsfieldLut() {
    Attributes lutAttributes = new Attributes();

    // Descriptor: 4096 entries, start at -1024, 16 bits per entry
    lutAttributes.setInt(Tag.LUTDescriptor, VR.US, 4096, -1024, 16);

    // HU mapping for CT
    byte[] lutData = new byte[4096 * 2];
    for (int i = 0; i < 4096; i++) {
      int hounsfield = i - 1024; // Range: -1024 to 3071 HU

      // Store as little-endian 16-bit signed values
      lutData[i * 2] = (byte) (hounsfield & 0xFF);
      lutData[i * 2 + 1] = (byte) ((hounsfield >> 8) & 0xFF);
    }
    lutAttributes.setBytes(Tag.LUTData, VR.OW, lutData);
    lutAttributes.setString(Tag.ModalityLUTType, VR.LO, "HU");
    lutAttributes.setString(Tag.LUTExplanation, VR.LO, "CT Hounsfield Units");

    return lutAttributes;
  }

  public static Attributes createContrastLut12Bit() {
    Attributes lutAttributes = new Attributes();

    // Descriptor: 1024 entries, start at 0, 12 bits per entry
    lutAttributes.setInt(Tag.LUTDescriptor, VR.US, 1024, 0, 12);

    // Contrast enhancement mapping
    byte[] lutData = new byte[1024 * 2];
    for (int i = 0; i < 1024; i++) {
      // Gamma correction for contrast enhancement
      double normalized = i / 1023.0;
      double enhanced = Math.pow(normalized, 1.5);
      int outputValue = Math.min(4095, (int) (enhanced * 4095));

      // Store as little-endian 16-bit values
      lutData[i * 2] = (byte) (outputValue & 0xFF);
      lutData[i * 2 + 1] = (byte) ((outputValue >> 8) & 0xFF);
    }
    lutAttributes.setBytes(Tag.LUTData, VR.OW, lutData);
    lutAttributes.setString(Tag.ModalityLUTType, VR.LO, "US");
    lutAttributes.setString(Tag.LUTExplanation, VR.LO, "Contrast Enhanced 12-bit");

    return lutAttributes;
  }

  public static Attributes createCompletePaletteLutAttributes() {
    Attributes attributes = new Attributes();

    // Create a simple palette color LUT
    attributes.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);
    attributes.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);
    attributes.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);

    byte[] lutData = new byte[256];
    for (int i = 0; i < 256; i++) {
      lutData[i] = (byte) i;
    }
    attributes.setBytes(Tag.RedPaletteColorLookupTableData, VR.OB, lutData);
    attributes.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OB, lutData);
    attributes.setBytes(Tag.BluePaletteColorLookupTableData, VR.OB, lutData);

    attributes.setString(Tag.PhotometricInterpretation, VR.CS, "PALETTE_COLOR");
    return attributes;
  }
}
