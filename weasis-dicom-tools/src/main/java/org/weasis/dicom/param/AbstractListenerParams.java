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
import java.util.Objects;

/**
 * Abstract base class for DICOM listener configuration parameters.
 *
 * <p>This class provides common configuration options for DICOM Service Class Provider (SCP)
 * listeners, including AET binding, transfer capabilities, and authorized calling nodes.
 */
public abstract class AbstractListenerParams {

  private static final String[] EMPTY_AET_ARRAY = new String[0];
  private static final int DEFAULT_MAX_OPS = 50;
  protected final AdvancedParams params;
  protected final boolean bindCallingAet;
  protected final URL transferCapabilityFile;
  protected final String[] acceptedCallingAETitles;

  /**
   * Creates listener parameters with AET binding configuration.
   *
   * @param bindCallingAet when true, only requests with matching called AETitle are accepted; when
   *     false, all called AETs are accepted
   */
  protected AbstractListenerParams(boolean bindCallingAet) {
    this(null, bindCallingAet, null);
  }

  /**
   * Creates listener parameters with advanced configuration and AET binding.
   *
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param bindCallingAet when true, only requests with matching called AETitle are accepted; when
   *     false, all called AETs are accepted
   */
  protected AbstractListenerParams(AdvancedParams params, boolean bindCallingAet) {
    this(params, bindCallingAet, null);
  }

  /**
   * Creates listener parameters with full configuration options.
   *
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param bindCallingAet when true, only requests with matching called AETitle are accepted; when
   *     false, all called AETs are accepted
   * @param transferCapabilityFile URL for transfer capabilities configuration file
   * @param acceptedCallingAETitles list of accepted calling AE titles (null accepts all)
   */
  protected AbstractListenerParams(
      AdvancedParams params,
      boolean bindCallingAet,
      URL transferCapabilityFile,
      String... acceptedCallingAETitles) {
    this.params = Objects.requireNonNullElseGet(params, AdvancedParams::new);
    this.bindCallingAet = bindCallingAet;
    this.transferCapabilityFile = transferCapabilityFile;
    this.acceptedCallingAETitles =
        acceptedCallingAETitles != null ? acceptedCallingAETitles.clone() : EMPTY_AET_ARRAY;

    configureDefaultOperationLimits(params);
  }

  private void configureDefaultOperationLimits(AdvancedParams originalParams) {
    if (originalParams == null && params.getConnectOptions() != null) {
      var connectOptions = params.getConnectOptions();
      connectOptions.setMaxOpsInvoked(DEFAULT_MAX_OPS);
      connectOptions.setMaxOpsPerformed(DEFAULT_MAX_OPS);
    }
  }

  public boolean isBindCallingAet() {
    return bindCallingAet;
  }

  public URL getTransferCapabilityFile() {
    return transferCapabilityFile;
  }

  /** Gets a defensive copy of accepted calling AE titles. */
  public String[] getAcceptedCallingAETitles() {
    return acceptedCallingAETitles.clone();
  }

  public AdvancedParams getParams() {
    return params;
  }
}
