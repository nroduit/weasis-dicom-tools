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

public abstract class AbstractListenerParams {

  protected final AdvancedParams params;
  protected final boolean bindCallingAet;
  protected final URL transferCapabilityFile;
  protected final String[] acceptedCallingAETitles;

  /**
   * @param bindCallingAet when true it will set the AET of the listener DICOM node. Only requests
   *     with matching called AETitle will be accepted. If false all the called AETs will be
   *     accepted.
   */
  public AbstractListenerParams(boolean bindCallingAet) {
    this(null, bindCallingAet, null);
  }

  /**
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param bindCallingAet when true it will set the AET of the listener DICOM node. Only requests
   *     with matching called AETitle will be accepted. If false all the called AETs will be
   *     accepted.
   */
  public AbstractListenerParams(AdvancedParams params, boolean bindCallingAet) {
    this(params, bindCallingAet, null);
  }

  /**
   * @param params optional advanced parameters (proxy, authentication, connection and TLS)
   * @param bindCallingAet when true it will set the AET of the listener DICOM node. Only requests
   *     with matching called AETitle will be accepted. If false all the called AETs will be
   *     accepted.
   * @param transferCapabilityFile an URL for getting a file containing the transfer capabilities
   *     (sopClasses, roles, transferSyntaxes)
   * @param acceptedCallingAETitles the list of the accepted calling AETitles. Null will accepted
   *     all the AETitles.
   */
  public AbstractListenerParams(
      AdvancedParams params,
      boolean bindCallingAet,
      URL transferCapabilityFile,
      String... acceptedCallingAETitles) {
    this.params = params == null ? new AdvancedParams() : params;
    this.bindCallingAet = bindCallingAet;
    this.transferCapabilityFile = transferCapabilityFile;
    this.acceptedCallingAETitles =
        acceptedCallingAETitles == null ? new String[0] : acceptedCallingAETitles;
    if (params == null && this.params.getConnectOptions() != null) {
      // Concurrent DICOM operations
      this.params.getConnectOptions().setMaxOpsInvoked(50);
      this.params.getConnectOptions().setMaxOpsPerformed(50);
    }
  }

  public boolean isBindCallingAet() {
    return bindCallingAet;
  }

  public URL getTransferCapabilityFile() {
    return transferCapabilityFile;
  }

  public String[] getAcceptedCallingAETitles() {
    return acceptedCallingAETitles;
  }

  public AdvancedParams getParams() {
    return params;
  }
}
