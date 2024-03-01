/*
 * Copyright (c) 2017-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import java.net.URL;
import java.util.List;

public class CstoreParams {
  private final List<AttributeEditor> editors;
  private final boolean extendNegociation;
  private final URL extendSopClassesURL;

  /**
   * @param editors a editor to modify DICOM attributes
   * @param extendNegociation extends SOP classes negotiation
   * @param extendSopClassesURL configuration file of the SOP classes negotiation extension
   */
  public CstoreParams(
      List<AttributeEditor> editors, boolean extendNegociation, URL extendSopClassesURL) {
    this.editors = editors;
    this.extendNegociation = extendNegociation;
    this.extendSopClassesURL = extendSopClassesURL;
  }

  public List<AttributeEditor> getDicomEditors() {
    return editors;
  }

  public boolean hasDicomEditors() {
    return editors != null && !editors.isEmpty();
  }

  public boolean isExtendNegociation() {
    return extendNegociation;
  }

  public URL getExtendSopClassesURL() {
    return extendSopClassesURL;
  }
}
