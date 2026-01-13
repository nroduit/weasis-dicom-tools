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

/**
 * Configuration parameters for DICOM listeners with storage pattern support.
 *
 * <p>Extends {@link AbstractListenerParams} to provide file storage pattern configuration for
 * received DICOM objects. The storage pattern allows customization of how received objects are
 * organized in the file system using DICOM attributes.
 */
public class ListenerParams extends AbstractListenerParams {

  private final String storagePattern;

  /**
   * Creates listener parameters with AET binding configuration only.
   *
   * @param bindCallingAet when true, only requests with matching called AETitle are accepted
   */
  public ListenerParams(boolean bindCallingAet) {
    this(null, bindCallingAet, null, null);
  }

  /**
   * Creates listener parameters with advanced parameters and AET binding.
   *
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param bindCallingAet when true, only requests with matching called AETitle are accepted
   */
  public ListenerParams(AdvancedParams params, boolean bindCallingAet) {
    this(params, bindCallingAet, null, null);
  }

  /**
   * Creates listener parameters with full configuration options including storage pattern.
   *
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param bindCallingAet when true, only requests with matching called AETitle are accepted
   * @param storagePattern file path pattern for stored objects using DICOM tag placeholders (e.g.,
   *     '{00100020}/{0020000D}/{0020000E}/{00080018}.dcm' creates subdirectories by Patient ID,
   *     Study UID, and Series UID with SOP Instance UID as filename). If null, uses SOP Instance
   *     UID without extension.
   * @param transferCapabilityFile URL for transfer capabilities configuration file
   * @param acceptedCallingAETitles accepted calling AE titles (null accepts all)
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

  /**
   * Gets the storage pattern for organizing received DICOM objects.
   *
   * @return the storage pattern or null if using default SOP Instance UID naming
   */
  public String getStoragePattern() {
    return storagePattern;
  }
}
