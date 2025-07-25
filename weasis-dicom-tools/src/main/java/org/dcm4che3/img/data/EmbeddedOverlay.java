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

import static org.dcm4che3.img.data.OverlayData.GROUP_OFFSET_SHIFT;
import static org.dcm4che3.img.data.OverlayData.MAX_OVERLAY_GROUPS;

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
   * Extracts embedded overlay information from DICOM attributes.
   *
   * @param dcm the DICOM attributes containing the embedded overlays
   * @return a list of EmbeddedOverlay objects, or empty list if none found
   */
  public static List<EmbeddedOverlay> getEmbeddedOverlay(Attributes dcm) {
    if (dcm == null) {
      return Collections.emptyList();
    }

    List<EmbeddedOverlay> overlays = new ArrayList<>();
    DicomBitConfiguration bitConfig = extractBitConfiguration(dcm);

    for (int groupIndex = 0; groupIndex < MAX_OVERLAY_GROUPS; groupIndex++) {
      processOverlayGroup(dcm, groupIndex, bitConfig, overlays);
    }

    return overlays.isEmpty() ? Collections.emptyList() : overlays;
  }

  /** Extracts bit allocation and storage configuration from DICOM attributes. */
  private static DicomBitConfiguration extractBitConfiguration(Attributes dcm) {
    int bitsAllocated = dcm.getInt(Tag.BitsAllocated, 8);
    int bitsStored = dcm.getInt(Tag.BitsStored, bitsAllocated);
    return new DicomBitConfiguration(bitsAllocated, bitsStored);
  }

  /** Processes a single overlay group and adds valid embedded overlays to the list. */
  private static void processOverlayGroup(
      Attributes dcm,
      int groupIndex,
      DicomBitConfiguration bitConfig,
      List<EmbeddedOverlay> overlays) {

    int groupOffset = groupIndex << GROUP_OFFSET_SHIFT;

    if (isEmbeddedOverlay(dcm, groupOffset)) {
      int bitPosition = dcm.getInt(Tag.OverlayBitPosition | groupOffset, 0);

      if (isValidBitPosition(bitPosition, bitConfig.bitsStored(), groupIndex)) {
        overlays.add(new EmbeddedOverlay(groupOffset, bitPosition));
      }
    }
  }

  /** An overlay is embedded if OverlayBitsAllocated is not 1 (standard overlays have value 1). */
  private static boolean isEmbeddedOverlay(Attributes dcm, int groupOffset) {
    int overlayBitsAllocated = dcm.getInt(Tag.OverlayBitsAllocated | groupOffset, 1);
    return overlayBitsAllocated != 1;
  }

  /** Bit position must be >= bitsStored to avoid conflicts with actual image data. */
  private static boolean isValidBitPosition(int bitPosition, int bitsStored, int groupIndex) {
    if (bitPosition < bitsStored) {
      LOGGER.info(
          "Ignore embedded overlay #{} from bit #{} < bits stored: {}",
          groupIndex + 1,
          bitPosition,
          bitsStored);
      return false;
    }
    return true;
  }

  /** Internal record to hold DICOM bit configuration. */
  private record DicomBitConfiguration(int bitsAllocated, int bitsStored) {}
}
