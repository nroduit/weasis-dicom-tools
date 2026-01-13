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
public final class DicomAttributeUtils {
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
    return findInHierarchy(dcm, Tag.Modality);
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
      return Optional.ofNullable(dicom.getBytes(tag)).filter(data -> data.length > 0);
    } catch (IOException e) {
      LOGGER.error("Error extracting byte data from {}", TagUtils.toString(tag), e);
      return Optional.empty();
    }
  }

  /** Finds a string value in the DICOM hierarchy starting from the given attributes. */
  private static String findInHierarchy(Attributes attributes, int tag) {
    for (var current = attributes; current != null; current = current.getParent()) {
      var value = current.getString(tag);
      if (value != null && !value.isEmpty()) {
        return value;
      }
    }
    return null;
  }
}
