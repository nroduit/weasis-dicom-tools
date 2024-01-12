/*
 * Copyright (c) 2014-2018 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.common;

import java.io.File;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class FilesetInfo {

  private String uid;
  private String id;
  private File descFile;
  private String descFileCharset;

  public final String getFilesetUID() {
    return uid;
  }

  public final void setFilesetUID(String uid) {
    this.uid = uid;
  }

  public final String getFilesetID() {
    return id;
  }

  public final void setFilesetID(String id) {
    this.id = id;
  }

  public final File getDescriptorFile() {
    return descFile;
  }

  public final void setDescriptorFile(File descFile) {
    this.descFile = descFile;
  }

  public final String getDescriptorFileCharset() {
    return descFileCharset;
  }

  public final void setDescriptorFileCharset(String descFileCharset) {
    this.descFileCharset = descFileCharset;
  }
}
