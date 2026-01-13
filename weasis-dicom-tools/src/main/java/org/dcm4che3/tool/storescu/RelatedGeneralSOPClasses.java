/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.storescu;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.pdu.CommonExtendedNegotiation;
import org.dcm4che3.util.StringUtils;

/**
 * Manages related general SOP classes and their common extended negotiations for DICOM storage
 * operations.
 *
 * <p>This class provides a mapping between SOP class UIDs and their corresponding {@link
 * CommonExtendedNegotiation} objects, which are used during DICOM association establishment to
 * negotiate supported extended services.
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Nicolas Roduit
 */
public final class RelatedGeneralSOPClasses {

  private final Map<String, CommonExtendedNegotiation> commonExtNegs = new ConcurrentHashMap<>();

  /**
   * Initializes the SOP class mappings from the provided properties.
   *
   * @param props properties where keys are SOP class UIDs and values are comma-separated related
   *     UIDs
   */
  public void init(Properties props) {
    if (props == null) {
      return;
    }
    props
        .stringPropertyNames()
        .forEach(
            cuid -> {
              var relatedUIDs = StringUtils.split(props.getProperty(cuid), ',');
              var negotiation = new CommonExtendedNegotiation(cuid, UID.Storage, relatedUIDs);
              commonExtNegs.put(cuid, negotiation);
            });
  }

  /**
   * Gets the common extended negotiation for the specified SOP class UID.
   *
   * @param cuid the SOP class UID
   * @return the common extended negotiation, or a default one if not found
   */
  public CommonExtendedNegotiation getCommonExtendedNegotiation(String cuid) {
    return commonExtNegs.getOrDefault(cuid, new CommonExtendedNegotiation(cuid, UID.Storage));
  }

  /** Clears all stored SOP class mappings. */
  public void clear() {
    commonExtNegs.clear();
  }

  /**
   * Returns the number of configured SOP class mappings.
   *
   * @return the number of mappings
   */
  public int size() {
    return commonExtNegs.size();
  }

  /**
   * Checks if any SOP class mappings are configured.
   *
   * @return true if no mappings are configured, false otherwise
   */
  public boolean isEmpty() {
    return commonExtNegs.isEmpty();
  }
}
