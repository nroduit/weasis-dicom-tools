/*
 * Copyright (c) 2014-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import java.util.List;

public class TlsOptions {
  // cipherSuites
  public static final List<String> TLS =
      List.of(
          "SSL_RSA_WITH_NULL_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA");
  public static final List<String> TLS_NULL = List.of("SSL_RSA_WITH_NULL_SHA");
  public static final List<String> TLS_3DES = List.of("SSL_RSA_WITH_3DES_EDE_CBC_SHA");
  public static final List<String> TLS_AES =
      List.of("TLS_RSA_WITH_AES_128_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA");

  // tlsProtocols
  public static final List<String> defaultProtocols = List.of("TLSv1", "SSLv3");
  public static final List<String> tls1 = List.of("TLSv1");
  public static final List<String> tls11 = List.of("TLSv1.1");
  public static final List<String> tls12 = List.of("TLSv1.2");
  @Deprecated public static final List<String> ssl3 = List.of("SSLv3");
  public static final List<String> ssl2Hello =
      List.of("SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2");

  private final List<String> cipherSuites;
  private final List<String> tlsProtocols;

  private final boolean tlsNeedClientAuth;

  private final String keystoreURL;
  private final String keystoreType;
  private final String keystorePass;
  private final String keyPass;
  private final String truststoreURL;
  private final String truststoreType;
  private final String truststorePass;

  public TlsOptions(
      boolean tlsNeedClientAuth,
      String keystoreURL,
      String keystoreType,
      String keystorePass,
      String keyPass,
      String truststoreURL,
      String truststoreType,
      String truststorePass) {
    this(
        TLS,
        defaultProtocols,
        tlsNeedClientAuth,
        keystoreURL,
        keystoreType,
        keystorePass,
        keyPass,
        truststoreURL,
        truststoreType,
        truststorePass);
  }

  public TlsOptions(
      List<String> cipherSuites,
      List<String> tlsProtocols,
      boolean tlsNeedClientAuth,
      String keystoreURL,
      String keystoreType,
      String keystorePass,
      String keyPass,
      String truststoreURL,
      String truststoreType,
      String truststorePass) {
    if (cipherSuites == null) {
      throw new IllegalArgumentException("cipherSuites cannot be null");
    }
    this.cipherSuites = cipherSuites;
    this.tlsProtocols = tlsProtocols;
    this.tlsNeedClientAuth = tlsNeedClientAuth;
    this.keystoreURL = keystoreURL;
    this.keystoreType = keystoreType;
    this.keystorePass = keystorePass;
    this.keyPass = keyPass;
    this.truststoreURL = truststoreURL;
    this.truststoreType = truststoreType;
    this.truststorePass = truststorePass;
  }

  public boolean isTlsNeedClientAuth() {
    return tlsNeedClientAuth;
  }

  public List<String> getCipherSuites() {
    return cipherSuites;
  }

  public List<String> getTlsProtocols() {
    return tlsProtocols;
  }

  public String getKeystoreURL() {
    return keystoreURL;
  }

  public String getKeystoreType() {
    return keystoreType;
  }

  public String getKeystorePass() {
    return keystorePass;
  }

  public String getKeyPass() {
    return keyPass;
  }

  public String getTruststoreURL() {
    return truststoreURL;
  }

  public String getTruststoreType() {
    return truststoreType;
  }

  public String getTruststorePass() {
    return truststorePass;
  }
}
