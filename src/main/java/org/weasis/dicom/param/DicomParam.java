/*
 * Copyright (c) 2014-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import org.dcm4che3.data.ElementDictionary;

public class DicomParam {

  private final int tag;
  private final String[] values;
  private final int[] parentSeqTags;

  public DicomParam(int tag, String... values) {
    this(null, tag, values);
  }

  public DicomParam(int[] parentSeqTags, int tag, String... values) {
    this.tag = tag;
    this.values = values;
    this.parentSeqTags = parentSeqTags;
  }

  public int getTag() {
    return tag;
  }

  public String[] getValues() {
    return values;
  }

  public int[] getParentSeqTags() {
    return parentSeqTags;
  }

  public String getTagName() {
    return ElementDictionary.keywordOf(tag, null);
  }
}
