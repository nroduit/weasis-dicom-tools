/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import java.net.URL;

public class ListenerParams extends AbstractListenerParams {

  private final String storagePattern;

  /** {@inheritDoc} */
  public ListenerParams(boolean bindCallingAet) {
    this(null, bindCallingAet, null, null);
  }

  /** {@inheritDoc} */
  public ListenerParams(AdvancedParams params, boolean bindCallingAet) {
    this(params, bindCallingAet, null, null);
  }

  /**
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param bindCallingAet when true it will set the AET of the listener DICOM node. Only requests
   *     with matching called AETitle will be accepted. If false all the called AETs will be
   *     accepted.
   * @param storagePattern file path of stored objects, '{ggggeeee}' will be replaced by the
   *     attribute value, e.g.: '{00100020}/{0020000D}/{0020000E}/{00 080018}.dcm' will store
   *     received objects using the SOP Instance UID (0008,0018) as file name and '.dcm' as file
   *     name extension into sub-directories structured according its Patient ID (0010,0020), Study
   *     Instance UID (0020,000D} and Series Instance UID (0020,000E). At default, received objects
   *     are stored to the storage directory with the SOP Instance UID (0008,0018) as file name
   *     without extension.
   * @param transferCapabilityFile an URL for getting a file containing the transfer capabilities
   *     (sopClasses, roles, transferSyntaxes)
   * @param acceptedCallingAETitles the list of the accepted calling AETitles. Null will accepted
   *     all the AETitles.
   */
  public ListenerParams(
      AdvancedParams params,
      boolean bindCallingAet,
      String storagePattern,
      URL transferCapabilityFile,
      String... acceptedCallingAETitles) {
    super(params, bindCallingAet, transferCapabilityFile, acceptedCallingAETitles);
    this.storagePattern = storagePattern;
  }

  public String getStoragePattern() {
    return storagePattern;
  }
}
