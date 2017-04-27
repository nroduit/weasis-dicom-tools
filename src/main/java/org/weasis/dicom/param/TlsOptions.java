/*******************************************************************************
 * Copyright (c) 2014 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.param;

public class TlsOptions {
    // cipherSuites
    public static final String[] TLS =
        { "SSL_RSA_WITH_NULL_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA" };
    public static final String[] TLS_NULL = { "SSL_RSA_WITH_NULL_SHA" };
    public static final String[] TLS_3DES = { "SSL_RSA_WITH_3DES_EDE_CBC_SHA" };
    public static final String[] TLS_AES = { "TLS_RSA_WITH_AES_128_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA" };

    // tlsProtocols
    public static final String[] defaultProtocols = { "TLSv1", "SSLv3" };
    public static final String[] tls1 = { "TLSv1" };
    public static final String[] tls11 = { "TLSv1.1" };
    public static final String[] tls12 = { "TLSv1.2" };
    public static final String[] ssl3 = { "SSLv3" }; // deprecated
    public static final String[] ssl2Hello = { "SSLv2Hello", "SSLv3", "TLSv1","TLSv1.1", "TLSv1.2" };

    private final String[] cipherSuites;
    private final String[] tlsProtocols;

    private final boolean tlsNeedClientAuth;

    private final String keystoreURL;
    private final String keystoreType;
    private final String keystorePass;
    private final String keyPass;
    private final String truststoreURL;
    private final String truststoreType;
    private final String truststorePass;

    public TlsOptions(boolean tlsNeedClientAuth, String keystoreURL, String keystoreType, String keystorePass,
        String keyPass, String truststoreURL, String truststoreType, String truststorePass) {
        this(TLS, defaultProtocols, tlsNeedClientAuth, keystoreURL, keystoreType, keystorePass, keyPass, truststoreURL,
            truststoreType, truststorePass);
    }

    public TlsOptions(String[] cipherSuites, String[] tlsProtocols, boolean tlsNeedClientAuth, String keystoreURL,
        String keystoreType, String keystorePass, String keyPass, String truststoreURL, String truststoreType,
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

    public String[] getCipherSuites() {
        return cipherSuites;
    }

    public String[] getTlsProtocols() {
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
