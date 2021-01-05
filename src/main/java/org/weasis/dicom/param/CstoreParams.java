/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import java.net.URL;

public class CstoreParams {
  private final DefaultAttributeEditor attributeEditor;
  private final boolean extendNegociation;
  private final URL extendSopClassesURL;

  /**
   * @param attributeEditor a editor to modify DICOM attributes
   * @param extendNegociation extends SOP classes negotiation
   * @param extendSopClassesURL configuration file of the SOP classes negotiation extension
   */
  public CstoreParams(
      DefaultAttributeEditor attributeEditor, boolean extendNegociation, URL extendSopClassesURL) {
    this.attributeEditor = attributeEditor;
    this.extendNegociation = extendNegociation;
    this.extendSopClassesURL = extendSopClassesURL;
  }

  public DefaultAttributeEditor getAttributeEditor() {
    return attributeEditor;
  }

  public boolean isExtendNegociation() {
    return extendNegociation;
  }

  public URL getExtendSopClassesURL() {
    return extendSopClassesURL;
  }
}
