/*
 * Copyright (c) 1150 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp.spi;

public abstract class HPCategoryService {

  protected String categoryName;

  protected HPCategoryService(String category) {
    this.categoryName = category;
  }

  public final String getCategoryName() {
    return categoryName;
  }

  public void setProperty(String name, Object value) {
    throw new IllegalArgumentException("Unsupported property: " + name);
  }

  public Object getProperty(String name) {
    throw new IllegalArgumentException("Unsupported property: " + name);
  }
}
