/*
 * Copyright (c) 2026-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.ref;

import java.util.Locale;

/** Localized names of the {@link RegionGroup}s, keyed by group name. */
public final class MesRegionGroup {

  private static final AbstractResourceBundle BUNDLE =
      new AbstractResourceBundle("org.weasis.dicom.ref.group") {};

  private MesRegionGroup() {
    // Utility class - prevent instantiation
  }

  /**
   * Gets the localized name of a region group using the default locale.
   *
   * @param key the region group name
   * @return the localized group name
   */
  public static String getString(String key) {
    return BUNDLE.getString(key);
  }

  /**
   * Gets the localized name of a region group using the specified locale.
   *
   * @param key the region group name
   * @param locale the desired locale, or null to use default locale
   * @return the localized group name
   */
  public static String getString(String key, Locale locale) {
    return BUNDLE.getString(key, locale);
  }
}
