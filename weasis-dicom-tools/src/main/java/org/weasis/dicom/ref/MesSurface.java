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
 * Resource bundle accessor for anatomical surface part terminology. Provides localized
 * human-readable names for surface anatomical structure codes used in specialized medical imaging
 * and dermatological applications.
 *
 * @see SurfacePart
 * @see AnatomicRegion
 */
public final class MesSurface {

  private static final AbstractResourceBundle BUNDLE =
      new AbstractResourceBundle("org.weasis.dicom.ref.surface") {};

  private MesSurface() {
    // Utility class - prevent instantiation
  }

  /**
   * Gets the localized string for a surface part code using the default locale.
   *
   * @param key the surface part code value
   * @return the localized surface part name
   */
  public static String getString(String key) {
    return BUNDLE.getString(key);
  }

  /**
   * Gets the localized string for a surface part code using the specified locale.
   *
   * @param key the surface part code value
   * @param locale the desired locale, or null to use default locale
   * @return the localized surface part name
   */
  public static String getString(String key, Locale locale) {
    return BUNDLE.getString(key, locale);
  }
}
