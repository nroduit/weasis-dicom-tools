/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a pixel embedded overlay in DICOM attributes which is defined by the group offset and
 * the bit position. This type of overlay has been retired in DICOM standard, but it is still used
 * in some old DICOM files.
 *
 * @author Nicolas Roduit
 */
public record EmbeddedOverlay(int groupOffset, int bitPosition) {
  private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedOverlay.class);

  /**
   * Returns a list of EmbeddedOverlay objects extracted from the given DICOM attributes.
   *
   * @param dcm the DICOM attributes containing the embedded overlays
   * @return a list of EmbeddedOverlay objects
   */
  public static List<EmbeddedOverlay> getEmbeddedOverlay(Attributes dcm) {
    List<EmbeddedOverlay> data = new ArrayList<>();
    int bitsAllocated = dcm.getInt(Tag.BitsAllocated, 8);
    int bitsStored = dcm.getInt(Tag.BitsStored, bitsAllocated);
    for (int i = 0; i < 16; i++) {
      int gg0000 = i << 17;
      if (dcm.getInt(Tag.OverlayBitsAllocated | gg0000, 1) != 1) {
        int bitPosition = dcm.getInt(Tag.OverlayBitPosition | gg0000, 0);
        if (bitPosition < bitsStored) {
          LOGGER.info(
              "Ignore embedded overlay #{} from bit #{} < bits stored: {}",
              (gg0000 >>> 17) + 1,
              bitPosition,
              bitsStored);
        } else {
          data.add(new EmbeddedOverlay(gg0000, bitPosition));
        }
      }
    }
    return data.isEmpty() ? Collections.emptyList() : data;
  }
}
