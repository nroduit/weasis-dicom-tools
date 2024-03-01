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
import java.util.List;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.EscapeChars;
import org.weasis.core.util.StringUtil;

public interface Xml {
  Logger LOGGER = LoggerFactory.getLogger(Xml.class);

  enum Level {
    PATIENT("Patient"), // $NON-NLS-1$
    STUDY("Study"), // $NON-NLS-1$
    SERIES("Series"), // $NON-NLS-1$
    INSTANCE("Instance"), // $NON-NLS-1$
    FRAME("Frame"); // $NON-NLS-1$

    private final String tag;

    Level(String tag) {
      this.tag = tag;
    }

    public String getTagName() {
      return tag;
    }

    @Override
    public String toString() {
      return tag;
    }
  }

  void toXml(Writer result) throws IOException;

  static void addXmlAttribute(int tagID, String value, Writer result) throws IOException {
    if (StringUtil.hasText(value)) {
      String key = ElementDictionary.getStandardElementDictionary().keywordOf(tagID);
      if (key == null) {
        LOGGER.error("Cannot find keyword of tagID {}", TagUtils.toString(tagID));
      } else {
        result.append(key);
        result.append("=\"");
        result.append(EscapeChars.forXML(value));
        result.append("\" ");
      }
    }
  }

  static void addXmlAttribute(String tag, String value, Writer result) throws IOException {
    if (StringUtil.hasText(tag) && StringUtil.hasText(value)) {
      result.append(tag);
      result.append("=\"");
      result.append(EscapeChars.forXML(value));
      result.append("\" ");
    }
  }

  static void addXmlAttribute(String tag, Boolean value, Writer result) throws IOException {
    if (tag != null && value != null) {
      result.append(tag);
      result.append("=\"");
      result.append(value.toString());
      result.append("\" ");
    }
  }

  static void addXmlAttribute(String tag, List<String> value, Writer result) throws IOException {
    if (tag != null && value != null) {
      result.append(tag);
      result.append("=\"");
      int size = value.size();
      for (int i = 0; i < size - 1; i++) {
        result.append(EscapeChars.forXML(value.get(i))).append(",");
      }
      if (size > 0) {
        result.append(EscapeChars.forXML(value.get(size - 1)));
      }
      result.append("\" ");
    }
  }
}
