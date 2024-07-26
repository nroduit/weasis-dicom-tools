/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.ref;

public record OtherPart(
    String codeValue, String codeMeaning, CodingScheme codingScheme, boolean paired)
    implements AnatomicItem {

  @Override
  public String getLegacyCode() {
    return null;
  }

  @Override
  public boolean isPaired() {
    return paired;
  }

  @Override
  public String getCodeValue() {
    return codeValue;
  }

  @Override
  public String getCodeMeaning() {
    return codeMeaning;
  }

  @Override
  public CodingScheme getCodingScheme() {
    return codingScheme;
  }
}
