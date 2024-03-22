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

import static org.junit.jupiter.api.Assertions.*;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModalityLutModuleTest {

  @Test
  @DisplayName("Verify ModalityLutModule initialization with non-null attributes")
  void shouldInitializeWithNonNullAttributes() {
    Attributes attributes = new Attributes();
    ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);

    assertFalse(modalityLutModule.getRescaleSlope().isPresent());
    assertFalse(modalityLutModule.getRescaleIntercept().isPresent());
    assertFalse(modalityLutModule.getRescaleType().isPresent());
    assertFalse(modalityLutModule.getLutType().isPresent());
    assertFalse(modalityLutModule.getLutExplanation().isPresent());
    assertFalse(modalityLutModule.getLut().isPresent());
  }

  @Test
  @DisplayName("Verify ModalityLutModule initialization with null attributes")
  void shouldThrowExceptionWhenAttributesAreNull() {
    assertThrows(NullPointerException.class, () -> new ModalityLutModule(null));
  }

  @Test
  @DisplayName("Verify do not apply modality LUT for XA and XRF")
  void verifyLUTForXA() {
    Attributes attributes = new Attributes();
    attributes.setString(Tag.Modality, VR.LO, "XA");
    attributes.setDouble(Tag.RescaleSlope, VR.DS, 2.0);
    attributes.setDouble(Tag.RescaleIntercept, VR.DS, 1.0);
    ModalityLutModule modalityLutModule = new ModalityLutModule(attributes);
    assertTrue(modalityLutModule.getRescaleIntercept().isEmpty());
    assertTrue(modalityLutModule.getRescaleSlope().isEmpty());

    attributes.setString(Tag.Modality, VR.LO, "XRF");
    attributes.setString(Tag.PixelIntensityRelationship, VR.CS, "DISP");
    Attributes dcm = new Attributes();
    dcm.setString(Tag.ModalityLUTType, VR.LO, "US");
    dcm.setInt(Tag.LUTDescriptor, VR.US, 8);
    dcm.setBytes(Tag.LUTData, VR.OB, new byte[] {1, 2, 3});
    dcm.setString(Tag.LUTExplanation, VR.LO, "Explanation");
    Sequence seq = attributes.newSequence(Tag.ModalityLUTSequence, 1);
    seq.add(dcm);

    modalityLutModule = new ModalityLutModule(attributes);
    assertTrue(modalityLutModule.getRescaleIntercept().isEmpty());
    assertTrue(modalityLutModule.getRescaleSlope().isEmpty());
    assertTrue(modalityLutModule.getLutExplanation().isEmpty());

    attributes.setString(Tag.Modality, VR.LO, "PT");
    dcm.setString(Tag.ModalityLUTType, VR.LO, "MGML");
    modalityLutModule = new ModalityLutModule(attributes);
    assertEquals("Explanation", modalityLutModule.getLutExplanation().get());
  }

  @Test
  @DisplayName(
      "Verify adaptWithOverlayBitMask sets correct rescaleSlope when rescaleSlope is empty")
  void shouldSetCorrectRescaleSlopeWhenRescaleSlopeIsEmpty() {

    ModalityLutModule modalityLutModule = new ModalityLutModule(new Attributes());
    modalityLutModule.adaptWithOverlayBitMask(2);
    assertEquals(0.0, modalityLutModule.getRescaleIntercept().getAsDouble());
    assertEquals(0.25, modalityLutModule.getRescaleSlope().getAsDouble());
    assertEquals("US", modalityLutModule.getRescaleType().get());

    Attributes attributes = new Attributes();
    attributes.setString(Tag.Modality, VR.LO, "CT");
    attributes.setDouble(Tag.RescaleSlope, VR.DS, 2.0);
    attributes.setDouble(Tag.RescaleIntercept, VR.DS, 1.0);
    attributes.setString(Tag.RescaleType, VR.LO, "HU");
    modalityLutModule = new ModalityLutModule(attributes);
    modalityLutModule.adaptWithOverlayBitMask(2);
    assertEquals(1.0, modalityLutModule.getRescaleIntercept().getAsDouble());
    assertEquals(0.25, modalityLutModule.getRescaleSlope().getAsDouble());
    assertEquals("HU", modalityLutModule.getRescaleType().get());
  }
}
