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
import java.util.Objects;

/**
 * Configuration parameters for DICOM C-STORE operations.
 *
 * <p>This immutable class encapsulates the parameters required for configuring DICOM C-STORE
 * operations, including DICOM attribute editors, SOP class negotiation settings, and configuration
 * resources.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Attribute editors for modifying DICOM attributes before transmission
 *   <li>SOP class negotiation extension support
 *   <li>External configuration via URL or file path
 * </ul>
 *
 * @since 1.0
 */
public final class CstoreParams {
  private static final String EMPTY_NEGOTIATION_MSG =
      "Extended negotiation is enabled but no configuration URL provided";
  private static final List<AttributeEditor> EMPTY_EDITORS = List.of();

  private final List<AttributeEditor> editors;
  private final boolean extendNegotiation;
  private final URL extendSopClassesURL;

  /**
   * Creates C-STORE parameters with all configuration options.
   *
   * @param editors list of attribute editors to apply during C-STORE operations, may be null or
   *     empty
   * @param extendNegotiation whether to extend SOP classes negotiation
   * @param extendSopClassesURL URL to configuration file for SOP classes negotiation extension, may
   *     be null
   * @throws IllegalArgumentException if extended negotiation is enabled but no configuration URL is
   *     provided
   */
  public CstoreParams(
      List<AttributeEditor> editors, boolean extendNegotiation, URL extendSopClassesURL) {
    validateConfiguration(extendNegotiation, extendSopClassesURL);

    this.editors = editors != null ? List.copyOf(editors) : EMPTY_EDITORS;
    this.extendNegotiation = extendNegotiation;
    this.extendSopClassesURL = extendSopClassesURL;
  }

  /**
   * Creates C-STORE parameters without extended negotiation.
   *
   * @param editors list of attribute editors, may be null or empty
   */
  public CstoreParams(List<AttributeEditor> editors) {
    this(editors, false, null);
  }

  /**
   * Returns an unmodifiable list of DICOM attribute editors.
   *
   * @return the list of attribute editors, never null
   */
  public List<AttributeEditor> getDicomEditors() {
    return editors;
  }

  /**
   * Checks if any DICOM attribute editors are configured.
   *
   * @return true if editors are present, false otherwise
   */
  public boolean hasDicomEditors() {
    return !editors.isEmpty();
  }

  /**
   * Returns whether extended SOP classes negotiation is enabled.
   *
   * @return true if extended negotiation is enabled
   */
  public boolean isExtendNegotiation() {
    return extendNegotiation;
  }

  /**
   * Returns the URL to the SOP classes negotiation extension configuration file.
   *
   * @return the configuration URL, may be null
   */
  public URL getExtendSopClassesURL() {
    return extendSopClassesURL;
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj
        || (obj instanceof CstoreParams other
            && Objects.equals(editors, other.editors)
            && extendNegotiation == other.extendNegotiation
            && Objects.equals(extendSopClassesURL, other.extendSopClassesURL));
  }

  @Override
  public int hashCode() {
    return Objects.hash(editors, extendNegotiation, extendSopClassesURL);
  }

  @Override
  public String toString() {
    return "CstoreParams{"
        + "editorsCount="
        + editors.size()
        + ", extendNegotiation="
        + extendNegotiation
        + ", extendSopClassesURL="
        + extendSopClassesURL
        + '}';
  }

  private static void validateConfiguration(boolean extendNegotiation, URL extendSopClassesURL) {
    if (extendNegotiation && extendSopClassesURL == null) {
      throw new IllegalArgumentException(EMPTY_NEGOTIATION_MSG);
    }
  }
}
