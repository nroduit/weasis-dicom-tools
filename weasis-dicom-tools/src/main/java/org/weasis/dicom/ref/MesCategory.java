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

/**
 * Resource bundle accessor for anatomical category terminology. Provides localized human-readable
 * names for anatomical category context UIDs used in DICOM anatomical region classification.
 *
 * @see AnatomicBuilder.Category
 * @see AnatomicRegion
 */
public final class MesCategory {

  private static final AbstractResourceBundle BUNDLE =
      new AbstractResourceBundle("org.weasis.dicom.ref.category") {};

  private MesCategory() {
    // Utility class - prevent instantiation
  }

  /**
   * Gets the localized string for a category context UID using the default locale.
   *
   * @param key the category context UID
   * @return the localized category name
   */
  public static String getString(String key) {
    return BUNDLE.getString(key);
  }

  /**
   * Gets the localized string for a category context UID using the specified locale.
   *
   * @param contextUID the category context UID
   * @param locale the desired locale, or null to use default locale
   * @return the localized category name
   */
  public static String getString(String contextUID, Locale locale) {
    return BUNDLE.getString(contextUID, locale);
  }
}
