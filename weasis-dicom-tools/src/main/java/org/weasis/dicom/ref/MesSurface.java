/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.ref;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class MesSurface {
  private static final String BUNDLE_NAME = "org.weasis.dicom.ref.surface"; // NON-NLS

  private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

  private MesSurface() {}

  public static String getString(String key) {
    try {
      return RESOURCE_BUNDLE.getString(key);
    } catch (MissingResourceException e) {
      return '!' + key + '!';
    }
  }

  public static String getString(String key, Locale locale) {
    try {
      if (locale == null) {
        locale = Locale.getDefault();
      }
      ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
      return bundle.getString(key);
    } catch (MissingResourceException e) {
      return '!' + key + '!';
    }
  }
}
