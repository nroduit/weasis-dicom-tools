/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmbeddedOverlayTest {

  @Test
  @DisplayName("Verify getEmbeddedOverlay returns empty list when no overlays are present")
  void shouldReturnEmptyListWhenNoOverlaysArePresent() {
    Attributes dcm = new Attributes();
    List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("Verify getEmbeddedOverlay returns overlays when overlays are present")
  void shouldReturnOverlaysWhenOverlaysArePresent() {
    Attributes dcm = new Attributes();
    dcm.setInt(Tag.BitsAllocated, VR.US, 16);
    dcm.setInt(Tag.BitsStored, VR.US, 12);
    dcm.setInt(Tag.OverlayRows, VR.US, 3);
    dcm.setInt(Tag.OverlayColumns, VR.US, 3);

    int gg0000 = 0;
    dcm.setString(Tag.OverlayType | gg0000, VR.CS, "G");
    dcm.setInt(Tag.OverlayOrigin | gg0000, VR.SS, 1, 1);
    dcm.setInt(Tag.OverlayBitsAllocated | gg0000, VR.US, 16);
    dcm.setInt(Tag.OverlayBitPosition | gg0000, VR.US, 15);

    gg0000 = 1 << 17;
    dcm.setString(Tag.OverlayType | gg0000, VR.CS, "G");
    dcm.setInt(Tag.OverlayOrigin | gg0000, VR.SS, 1, 1);
    dcm.setInt(Tag.OverlayBitsAllocated | gg0000, VR.US, 16);
    dcm.setInt(Tag.OverlayBitPosition | gg0000, VR.US, 14);

    gg0000 = 2 << 17;
    dcm.setString(Tag.OverlayType | gg0000, VR.CS, "G");
    dcm.setInt(Tag.OverlayOrigin | gg0000, VR.SS, 1, 1);
    dcm.setInt(Tag.OverlayBitsAllocated | gg0000, VR.US, 1); // Standard overlay
    dcm.setInt(Tag.OverlayBitPosition | gg0000, VR.US, 0);

    List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);
    assertEquals(2, result.size());
    assertEquals(0, result.get(0).groupOffset());
    assertEquals(15, result.get(0).bitPosition());
    assertEquals(1 << 17, result.get(1).groupOffset());
    assertEquals(14, result.get(1).bitPosition());
  }

  @Test
  @DisplayName("Verify getEmbeddedOverlay ignores overlays with bit position less than bits stored")
  void shouldIgnoreOverlaysWithBitPositionLessThanBitsStored() {
    Attributes dcm = new Attributes();
    dcm.setInt(Tag.BitsAllocated, VR.US, 16);
    dcm.setInt(Tag.BitsStored, VR.US, 12);
    dcm.setInt(Tag.OverlayRows, VR.US, 3);
    dcm.setInt(Tag.OverlayColumns, VR.US, 3);

    int gg0000 = 0;
    dcm.setString(Tag.OverlayType | gg0000, VR.CS, "G");
    dcm.setInt(Tag.OverlayOrigin | gg0000, VR.SS, 1, 1);
    dcm.setInt(Tag.OverlayBitsAllocated | gg0000, VR.US, 16);
    dcm.setInt(Tag.OverlayBitPosition | gg0000, VR.US, 11);

    gg0000 = 1 << 17;
    dcm.setString(Tag.OverlayType | gg0000, VR.CS, "G");
    dcm.setInt(Tag.OverlayOrigin | gg0000, VR.SS, 1, 1);
    dcm.setInt(Tag.OverlayBitsAllocated | gg0000, VR.US, 16);
    dcm.setInt(Tag.OverlayBitPosition | gg0000, VR.US, 7);

    List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);
    assertTrue(result.isEmpty());
  }
}
