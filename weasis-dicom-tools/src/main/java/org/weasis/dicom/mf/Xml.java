/*
 * Copyright (c) 2017-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import static org.weasis.dicom.mf.ArcParameters.TAG_DELIMITER;

import java.io.IOException;
import java.io.Writer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.EscapeChars;
import org.weasis.core.util.StringUtil;

/**
 * Interface for XML serialization of DICOM-related objects. Provides utility methods for adding XML
 * attributes and defines hierarchical levels for DICOM data structures.
 */
public interface Xml {
  Logger LOGGER = LoggerFactory.getLogger(Xml.class);

  /** Hierarchical levels in DICOM data structure, from patient down to frame level. */
  enum Level {
    PATIENT("Patient"),
    STUDY("Study"),
    SERIES("Series"),
    INSTANCE("Instance"),
    FRAME("Frame");

    private final String tagName;

    Level(String tagName) {
      this.tagName = tagName;
    }

    public String getTagName() {
      return tagName;
    }

    @Override
    public String toString() {
      return tagName;
    }
  }

  /**
   * Converts this object to its XML representation.
   *
   * @param writer the writer to output XML content
   * @throws IOException if an I/O error occurs during writing
   */
  void toXml(Writer writer) throws IOException;

  /**
   * Adds an XML attribute using a DICOM tag ID and value.
   *
   * @param tagID the DICOM tag ID
   * @param value the attribute value
   * @param writer the writer to output the attribute
   * @throws IOException if an I/O error occurs during writing
   */
  static void addXmlAttribute(int tagID, String value, Writer writer) throws IOException {
    writeAttribute(getTagKeyword(tagID), value, writer);
  }

  /**
   * Adds an XML attribute with the specified tag name and string value.
   *
   * @param tagName the attribute name
   * @param value the attribute value
   * @param writer the writer to output the attribute
   * @throws IOException if an I/O error occurs during writing
   */
  static void addXmlAttribute(String tagName, String value, Writer writer) throws IOException {
    writeAttribute(tagName, value, writer);
  }

  /**
   * Adds an XML attribute with the specified tag name and boolean value.
   *
   * @param tagName the attribute name
   * @param value the attribute value
   * @param writer the writer to output the attribute
   * @throws IOException if an I/O error occurs during writing
   */
  static void addXmlAttribute(String tagName, Boolean value, Writer writer) throws IOException {
    if (value != null) {
      writeAttribute(tagName, value.toString(), writer);
    }
  }

  /**
   * Adds an XML attribute with the specified tag name and array of integer values. Values are
   * joined with commas.
   *
   * @param tagName the attribute name
   * @param values the array of integer values
   * @param writer the writer to output the attribute
   * @throws IOException if an I/O error occurs during writing
   */
  static void addXmlAttribute(String tagName, int[] values, Writer writer) throws IOException {
    if (!StringUtil.hasText(tagName) || values == null || values.length == 0) {
      return;
    }
    String joinedValue =
        IntStream.of(values).mapToObj(String::valueOf).collect(Collectors.joining(TAG_DELIMITER));

    writer.append(tagName).append("=\"").append(joinedValue).append("\" ");
  }

  // Gets the DICOM tag keyword, with error logging
  private static String getTagKeyword(int tagID) {
    String keyword = ElementDictionary.getStandardElementDictionary().keywordOf(tagID);
    if (keyword == null) {
      LOGGER.error("Cannot find keyword for DICOM tag ID {}", TagUtils.toString(tagID));
    }
    return keyword;
  }

  // Writes an XML attribute with XML escaping
  private static void writeAttribute(String name, String value, Writer writer) throws IOException {
    if (StringUtil.hasText(name) && StringUtil.hasText(value)) {
      writer.append(name).append("=\"").append(EscapeChars.forXML(value)).append("\" ");
    }
  }
}
