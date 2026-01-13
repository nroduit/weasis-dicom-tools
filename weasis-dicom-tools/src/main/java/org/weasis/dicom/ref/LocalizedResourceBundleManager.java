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

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for accessing medical terminology resource bundles in a thread-safe, efficient manner.
 * Provides caching mechanisms and standardized error handling for missing resources.
 *
 * <p>This class handles localized resource lookup with fallback to default locale and graceful
 * error handling when resources are missing.
 */
abstract class AbstractResourceBundle {

  private static final String MISSING_KEY_FORMAT = "!%s!";
  private static final Map<String, ResourceBundle> bundleCache = new ConcurrentHashMap<>();

  private final String bundleName;
  private final ResourceBundle defaultBundle;

  protected AbstractResourceBundle(String bundleName) {
    this.bundleName = bundleName;
    this.defaultBundle = ResourceBundle.getBundle(bundleName);
  }

  /**
   * Gets a localized string for the given key using the default locale.
   *
   * @param key the resource key
   * @return the localized string, or a formatted missing key indicator if not found
   */
  public String getString(String key) {
    try {
      return defaultBundle.getString(key);
    } catch (MissingResourceException e) {
      return MISSING_KEY_FORMAT.formatted(key);
    }
  }

  /**
   * Gets a localized string for the given key using the specified locale.
   *
   * @param key the resource key
   * @param locale the desired locale, or null to use default locale
   * @return the localized string, or a formatted missing key indicator if not found
   */
  public String getString(String key, Locale locale) {
    if (locale == null) {
      return getString(key);
    }

    try {
      ResourceBundle bundle = getBundle(locale);
      return bundle.getString(key);
    } catch (MissingResourceException e) {
      return MISSING_KEY_FORMAT.formatted(key);
    }
  }

  // Get bundle with caching for performance
  private ResourceBundle getBundle(Locale locale) {
    String cacheKey = bundleName + "_" + locale.toString();
    return bundleCache.computeIfAbsent(cacheKey, k -> ResourceBundle.getBundle(bundleName, locale));
  }
}
