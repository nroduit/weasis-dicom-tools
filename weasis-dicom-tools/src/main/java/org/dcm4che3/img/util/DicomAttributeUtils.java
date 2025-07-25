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

import java.io.IOException;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for DICOM attribute operations and data extraction.
 *
 * <p>This class provides methods for extracting data from DICOM attributes, handling modality
 * information, and managing attribute hierarchies.
 *
 * @author Nicolas Roduit
 */
public class DicomAttributeUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomAttributeUtils.class);

  private DicomAttributeUtils() {
    // Prevent instantiation
  }

  /**
   * Extracts the modality from DICOM attributes, searching parent hierarchy if needed.
   *
   * <p>This method first looks for the Modality tag in the provided attributes. If not found, it
   * traverses up the parent hierarchy until a modality is found or no more parents exist.
   *
   * @param dcm the DICOM attributes to search
   * @return the modality string, or null if not found in the hierarchy
   */
  public static String getModality(Attributes dcm) {
    if (dcm == null) {
      return null;
    }

    String modality = dcm.getString(Tag.Modality);
    if (modality != null) {
      return modality;
    }

    // Search in parent hierarchy
    Attributes parent = dcm.getParent();
    while (parent != null) {
      modality = parent.getString(Tag.Modality);
      if (modality != null) {
        return modality;
      }
      parent = parent.getParent();
    }

    return null;
  }

  /**
   * Extracts byte data from a DICOM element.
   *
   * @param dicom the DICOM attributes
   * @param tag the DICOM tag to extract
   * @return an Optional containing the byte data, or empty if not present
   */
  public static Optional<byte[]> getByteData(Attributes dicom, int tag) {
    return getByteData(dicom, null, tag);
  }

  /**
   * Extracts byte data from a DICOM element with private creator support.
   *
   * @param dicom the DICOM attributes
   * @param privateCreator the private creator ID, or null for standard elements
   * @param tag the DICOM tag to extract
   * @return an Optional containing the byte data, or empty if not present
   */
  public static Optional<byte[]> getByteData(Attributes dicom, String privateCreator, int tag) {
    if (dicom == null || !dicom.containsValue(privateCreator, tag)) {
      return Optional.empty();
    }

    try {
      byte[] data = dicom.getBytes(tag);
      return Optional.ofNullable(data);
    } catch (IOException e) {
      LOGGER.error("Error extracting byte data from {}", TagUtils.toString(tag), e);
      return Optional.empty();
    }
  }
}
