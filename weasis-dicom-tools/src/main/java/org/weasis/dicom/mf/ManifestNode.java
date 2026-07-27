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

import java.io.IOException;
import java.io.Writer;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A node of the DICOM manifest tree that can serialize itself through a format-agnostic {@link
 * ManifestSerializer} (XML or JSON).
 */
public interface ManifestNode {
  Logger LOGGER = LoggerFactory.getLogger(ManifestNode.class);

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
   * Writes this node through a format-agnostic serializer.
   *
   * @param serializer the manifest serializer (XML or JSON)
   * @throws IOException if an I/O error occurs during writing
   */
  void write(ManifestSerializer serializer) throws IOException;

  /**
   * Convenience shortcut that serializes this node as XML to {@code writer}.
   *
   * @param writer the writer to output XML content
   * @throws IOException if an I/O error occurs during writing
   */
  default void toXml(Writer writer) throws IOException {
    write(new XmlManifestSerializer(writer));
  }

  /** Returns the standard DICOM keyword for {@code tagID}, or null if unknown. */
  static String keyword(int tagID) {
    String keyword = ElementDictionary.getStandardElementDictionary().keywordOf(tagID);
    if (keyword == null) {
      LOGGER.error("Cannot find keyword for DICOM tag ID {}", TagUtils.toString(tagID));
    }
    return keyword;
  }
}
