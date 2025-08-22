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

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a pixel embedded overlay in DICOM attributes defined by the group offset and bit
 * position. This overlay type has been retired in DICOM standard, but is still used in some legacy
 * DICOM files.
 *
 * @param groupOffset the overlay group offset calculated from group index
 * @param bitPosition the bit position where the overlay data is embedded
 * @author Nicolas Roduit
 */
public record EmbeddedOverlay(int groupOffset, int bitPosition) {
  private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedOverlay.class);
  private static final int DEFAULT_BITS_ALLOCATED = 8;
  private static final int DEFAULT_OVERLAY_BITS_ALLOCATED = 1;
  private static final int DEFAULT_BIT_POSITION = 0;

  /**
   * Extracts embedded overlay information from DICOM attributes.
   *
   * @param dcm the DICOM attributes containing the embedded overlays
   * @return an immutable list of EmbeddedOverlay objects, or empty list if none found
   */
  public static List<EmbeddedOverlay> getEmbeddedOverlay(Attributes dcm) {
    if (dcm == null) {
      return List.of();
    }

    var bitConfig = new DicomBitConfiguration(dcm);

    return IntStream.range(0, MAX_OVERLAY_GROUPS)
        .mapToObj(groupIndex -> createOverlayIfValid(dcm, groupIndex, bitConfig))
        .filter(Objects::nonNull)
        .toList();
  }

  private static EmbeddedOverlay createOverlayIfValid(
      Attributes dcm, int groupIndex, DicomBitConfiguration bitConfig) {

    int groupOffset = groupIndex << GROUP_OFFSET_SHIFT;

    if (!isEmbeddedOverlay(dcm, groupOffset)) {
      return null;
    }

    int bitPosition = dcm.getInt(Tag.OverlayBitPosition | groupOffset, DEFAULT_BIT_POSITION);

    if (bitPosition < bitConfig.bitsStored()) {
      LOGGER.info(
          "Ignoring embedded overlay #{} from bit #{} (< bits stored: {})",
          groupIndex + 1,
          bitPosition,
          bitConfig.bitsStored());
      return null;
    }
    return new EmbeddedOverlay(groupOffset, bitPosition);
  }

  private static boolean isEmbeddedOverlay(Attributes dcm, int groupOffset) {
    return dcm.getInt(Tag.OverlayBitsAllocated | groupOffset, DEFAULT_OVERLAY_BITS_ALLOCATED)
        != DEFAULT_OVERLAY_BITS_ALLOCATED;
  }

  /** Holds DICOM bit configuration extracted from attributes. */
  private record DicomBitConfiguration(int bitsAllocated, int bitsStored) {

    DicomBitConfiguration(Attributes dcm) {
      this(
          dcm.getInt(Tag.BitsAllocated, DEFAULT_BITS_ALLOCATED),
          dcm.getInt(Tag.BitsStored, dcm.getInt(Tag.BitsAllocated, DEFAULT_BITS_ALLOCATED)));
    }
  }
}
