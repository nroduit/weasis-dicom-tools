/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.SSLManagerFactory;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.UserIdentityRQ;

/**
 * Advanced parameters for DICOM operations including connection options, TLS configuration, and
 * query parameters.
 */
public class AdvancedParams {
  public static final String[] IVR_LE_FIRST = {
    UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndian
  };
  public static final String[] EVR_LE_FIRST = {
    UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndian, UID.ImplicitVRLittleEndian
  };
  public static final String[] EVR_BE_FIRST = {
    UID.ExplicitVRBigEndian, UID.ExplicitVRLittleEndian, UID.ImplicitVRLittleEndian
  };
  public static final String[] IVR_LE_ONLY = {UID.ImplicitVRLittleEndian};

  private Object informationModel;
  private final EnumSet<QueryOption> queryOptions = EnumSet.noneOf(QueryOption.class);
  private String[] tsuidOrder = IVR_LE_FIRST.clone();

  /** HTTP Proxy configuration in format: [user:password@]host:port */
  private String proxy;

  private UserIdentityRQ identity;

  private int priority = Priority.NORMAL;

  private ConnectOptions connectOptions;
  private TlsOptions tlsOptions;

  public Object getInformationModel() {
    return informationModel;
  }

  public void setInformationModel(Object informationModel) {
    this.informationModel = informationModel;
  }

  public EnumSet<QueryOption> getQueryOptions() {
    return queryOptions;
  }

  public void setQueryOptions(EnumSet<QueryOption> queryOptions) {
    this.queryOptions.clear();
    if (queryOptions != null) {
      this.queryOptions.addAll(queryOptions);
    }
  }

  public String[] getTsuidOrder() {
    return tsuidOrder.clone();
  }

  public void setTsuidOrder(String[] tsuidOrder) {
    this.tsuidOrder = Objects.requireNonNull(tsuidOrder, "tsuidOrder cannot be null").clone();
  }

  public String getProxy() {
    return proxy;
  }

  public void setProxy(String proxy) {
    this.proxy = proxy;
  }

  public int getPriority() {
    return priority;
  }

  /**
   * Sets the priority for DICOM operations.
   *
   * @param priority the priority level (default is Priority.NORMAL)
   * @see org.dcm4che3.net.Priority
   */
  public void setPriority(int priority) {
    this.priority = priority;
  }

  public UserIdentityRQ getIdentity() {
    return identity;
  }

  public void setIdentity(UserIdentityRQ identity) {
    this.identity = identity;
  }

  public ConnectOptions getConnectOptions() {
    return connectOptions;
  }

  public void setConnectOptions(ConnectOptions connectOptions) {
    this.connectOptions = connectOptions;
  }

  public TlsOptions getTlsOptions() {
    return tlsOptions;
  }

  public void setTlsOptions(TlsOptions tlsOptions) {
    this.tlsOptions = tlsOptions;
  }

  /**
   * Configures the association request and remote connection with the called node parameters.
   *
   * @param aAssociateRQ the association request to configure
   * @param remote the remote connection
   * @param calledNode the called DICOM node
   */
  public void configureConnect(AAssociateRQ aAssociateRQ, Connection remote, DicomNode calledNode) {
    aAssociateRQ.setCalledAET(calledNode.getAet());
    Optional.ofNullable(identity).ifPresent(aAssociateRQ::setUserIdentityRQ);
    remote.setHostname(calledNode.getHostname());
    remote.setPort(calledNode.getPort());
  }

  /**
   * Configures the connection with the calling node parameters.
   *
   * @param connection the connection to configure
   * @param callingNode the calling DICOM node
   */
  public void configureBind(Connection connection, DicomNode callingNode) {
    Optional.ofNullable(callingNode.getHostname()).ifPresent(connection::setHostname);
    Optional.ofNullable(callingNode.getPort()).ifPresent(connection::setPort);
  }

  /**
   * Configures the application entity and connection with the calling node parameters.
   *
   * @param applicationEntity the application entity to configure
   * @param connection the connection to configure
   * @param callingNode the calling DICOM node
   */
  public void configureBind(
      ApplicationEntity applicationEntity, Connection connection, DicomNode callingNode) {
    applicationEntity.setAETitle(callingNode.getAet());
    configureBind(connection, callingNode);
  }

  /**
   * Configures the connection with the specified connection options.
   *
   * @param conn the connection to configure
   * @throws IOException if configuration fails
   */
  public void configure(Connection conn) throws IOException {
    if (connectOptions == null) {
      return;
    }

    configureBasicOptions(conn);
    configureBufferOptions(conn);
    configurePduOptions(conn);
    configureOperationOptions(conn);
  }

  private void configureBasicOptions(Connection conn) {
    conn.setBacklog(connectOptions.getBacklog());
    conn.setConnectTimeout(connectOptions.getConnectTimeout());
    conn.setRequestTimeout(connectOptions.getRequestTimeout());
    conn.setAcceptTimeout(connectOptions.getAcceptTimeout());
    conn.setReleaseTimeout(connectOptions.getReleaseTimeout());
    conn.setResponseTimeout(connectOptions.getResponseTimeout());
    conn.setRetrieveTimeout(connectOptions.getRetrieveTimeout());
    conn.setIdleTimeout(connectOptions.getIdleTimeout());
    conn.setSocketCloseDelay(connectOptions.getSocloseDelay());
  }

  private void configureBufferOptions(Connection conn) {
    conn.setReceiveBufferSize(connectOptions.getSorcvBuffer());
    conn.setSendBufferSize(connectOptions.getSosndBuffer());
    conn.setPackPDV(connectOptions.isPackPDV());
    conn.setTcpNoDelay(connectOptions.isTcpNoDelay());
  }

  private void configurePduOptions(Connection conn) {
    conn.setReceivePDULength(connectOptions.getMaxPdulenRcv());
    conn.setSendPDULength(connectOptions.getMaxPdulenSnd());
  }

  private void configureOperationOptions(Connection conn) {
    conn.setMaxOpsInvoked(connectOptions.getMaxOpsInvoked());
    conn.setMaxOpsPerformed(connectOptions.getMaxOpsPerformed());
  }

  /**
   * Configures TLS settings for the connection.
   *
   * @param conn the local connection to configure
   * @param remote the remote connection to configure (optional)
   * @throws IOException if TLS configuration fails
   */
  public void configureTLS(Connection conn, Connection remote) throws IOException {
    if (tlsOptions == null) {
      return;
    }
    configureTlsProtocols(conn);
    configureDeviceSecurity(conn.getDevice());
    syncRemoteConnection(conn, remote);
  }

  private void configureTlsProtocols(Connection conn) {
    Optional.of(tlsOptions.getCipherSuites())
        .ifPresent(suites -> conn.setTlsCipherSuites(suites.toArray(String[]::new)));

    Optional.ofNullable(tlsOptions.getTlsProtocols())
        .ifPresent(protocols -> conn.setTlsProtocols(protocols.toArray(String[]::new)));
    conn.setTlsNeedClientAuth(tlsOptions.isTlsNeedClientAuth());
  }

  private void configureDeviceSecurity(Device device) throws IOException {
    try {
      device.setKeyManager(
          SSLManagerFactory.createKeyManager(
              tlsOptions.getKeystoreType(),
              tlsOptions.getKeystoreURL(),
              tlsOptions.getKeystorePass(),
              tlsOptions.getKeyPass()));
      device.setTrustManager(
          SSLManagerFactory.createTrustManager(
              tlsOptions.getTruststoreType(),
              tlsOptions.getTruststoreURL(),
              tlsOptions.getTruststorePass()));
    } catch (GeneralSecurityException e) {
      throw new IOException("Failed to configure TLS security", e);
    }
  }

  private void syncRemoteConnection(Connection conn, Connection remote) {
    Optional.ofNullable(remote)
        .ifPresent(
            r -> {
              r.setTlsProtocols(conn.getTlsProtocols());
              r.setTlsCipherSuites(conn.getTlsCipherSuites());
            });
  }
}
