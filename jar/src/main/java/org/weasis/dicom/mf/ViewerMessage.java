/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

public class ViewerMessage {
  public static final String TAG_DOCUMENT_MSG = "Message";
  public static final String MSG_ATTRIBUTE_TITLE = "title";
  public static final String MSG_ATTRIBUTE_DESC = "description";
  public static final String MSG_ATTRIBUTE_LEVEL = "severity";

  public enum eLevel {
    INFO,
    WARN,
    ERROR
  }

  private final String message;
  private final String title;
  private final eLevel level;

  public ViewerMessage(String title, String message, eLevel level) {
    this.title = title;
    this.message = message;
    this.level = level;
  }

  public String getMessage() {
    return message;
  }

  public String getTitle() {
    return title;
  }

  public eLevel getLevel() {
    return level;
  }
}
