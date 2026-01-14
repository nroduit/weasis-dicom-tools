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
import java.util.Objects;

/**
 * Immutable configuration class for TLS/SSL connection options.
 *
 * <p>Provides predefined cipher suites and protocol versions for common TLS configurations.
 * Supports both keystore and truststore configuration for mutual authentication.
 */
public record TlsOptions(
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

  // Cipher Suites Constants
  /** Default cipher suites supporting various encryption algorithms */
  public static final List<String> TLS =
      List.of(
          "SSL_RSA_WITH_NULL_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA");

  /** Null encryption cipher suite (for testing only) */
  public static final List<String> TLS_NULL = List.of("SSL_RSA_WITH_NULL_SHA");

  /** 3DES encryption cipher suite */
  public static final List<String> TLS_3DES = List.of("SSL_RSA_WITH_3DES_EDE_CBC_SHA");

  /** AES encryption cipher suites */
  public static final List<String> TLS_AES =
      List.of("TLS_RSA_WITH_AES_128_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA");

  // TLS Protocol Constants
  /** Default TLS protocols (legacy compatibility) */
  @Deprecated(since = "Java 17", forRemoval = false)
  public static final List<String> DEFAULT_PROTOCOLS = List.of("TLSv1", "SSLv3");

  /** TLS version 1.0 */
  public static final List<String> TLS_1_0 = List.of("TLSv1");

  /** TLS version 1.1 */
  public static final List<String> TLS_1_1 = List.of("TLSv1.1");

  /** TLS version 1.2 */
  public static final List<String> TLS_1_2 = List.of("TLSv1.2");

  /** TLS version 1.3 */
  public static final List<String> TLS_1_3 = List.of("TLSv1.3");

  /** SSL version 3.0 (deprecated) */
  @Deprecated(since = "Java 17", forRemoval = true)
  public static final List<String> SSL_3 = List.of("SSLv3");

  /** SSL2Hello with multiple protocol support */
  public static final List<String> SSL2_HELLO =
      List.of("SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2");

  /** Modern TLS protocols (recommended) */
  public static final List<String> MODERN_TLS = List.of("TLSv1.2", "TLSv1.3");

  /**
   * Creates TLS options with default cipher suites and protocols.
   *
   * @param tlsNeedClientAuth whether client authentication is required
   * @param keystoreURL keystore location URL
   * @param keystoreType keystore type (JKS, PKCS12, etc.)
   * @param keystorePass keystore password
   * @param keyPass private key password
   * @param truststoreURL truststore location URL
   * @param truststoreType truststore type
   * @param truststorePass truststore password
   */
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
        MODERN_TLS,
        tlsNeedClientAuth,
        keystoreURL,
        keystoreType,
        keystorePass,
        keyPass,
        truststoreURL,
        truststoreType,
        truststorePass);
  }

  /**
   * Creates TLS options with custom cipher suites and protocols.
   *
   * @param cipherSuites list of supported cipher suites (must not be null)
   * @param tlsProtocols list of supported TLS protocols
   * @param tlsNeedClientAuth whether client authentication is required
   * @param keystoreURL keystore location URL
   * @param keystoreType keystore type (JKS, PKCS12, etc.)
   * @param keystorePass keystore password
   * @param keyPass private key password
   * @param truststoreURL truststore location URL
   * @param truststoreType truststore type
   * @param truststorePass truststore password
   * @throws IllegalArgumentException if cipherSuites is null
   */
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
    this.cipherSuites = Objects.requireNonNull(cipherSuites, "cipherSuites cannot be null");
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

  @Override
  public List<String> cipherSuites() {
    return List.copyOf(cipherSuites);
  }

  @Override
  public List<String> tlsProtocols() {
    return tlsProtocols != null ? List.copyOf(tlsProtocols) : null;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof TlsOptions other
        && Objects.equals(cipherSuites, other.cipherSuites)
        && Objects.equals(tlsProtocols, other.tlsProtocols)
        && tlsNeedClientAuth == other.tlsNeedClientAuth
        && Objects.equals(keystoreURL, other.keystoreURL)
        && Objects.equals(keystoreType, other.keystoreType)
        && Objects.equals(keystorePass, other.keystorePass)
        && Objects.equals(keyPass, other.keyPass)
        && Objects.equals(truststoreURL, other.truststoreURL)
        && Objects.equals(truststoreType, other.truststoreType)
        && Objects.equals(truststorePass, other.truststorePass);
  }

  @Override
  public String toString() {
    return """
           TlsOptions{
             cipherSuites=%s,
             tlsProtocols=%s,
             tlsNeedClientAuth=%s,
             keystoreURL='%s',
             keystoreType='%s',
             truststoreURL='%s',
             truststoreType='%s'
           }"""
        .formatted(
            cipherSuites,
            tlsProtocols,
            tlsNeedClientAuth,
            keystoreURL,
            keystoreType,
            truststoreURL,
            truststoreType);
  }
}
