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
import java.util.Arrays;
import java.util.EnumSet;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.SSLManagerFactory;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.UserIdentityRQ;

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
  private EnumSet<QueryOption> queryOptions = EnumSet.noneOf(QueryOption.class);
  private String[] tsuidOrder = Arrays.copyOf(IVR_LE_FIRST, IVR_LE_FIRST.length);

  /*
   * Configure proxy <[user:password@]host:port> specify host and port of the HTTP Proxy to tunnel the DICOM
   * connection.
   */
  private String proxy;

  private UserIdentityRQ identity;

  private int priority = Priority.NORMAL;

  private ConnectOptions connectOptions;
  private TlsOptions tlsOptions;

  public AdvancedParams() {
    super();
  }

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
    this.queryOptions = queryOptions;
  }

  public String[] getTsuidOrder() {
    return tsuidOrder;
  }

  public void setTsuidOrder(String[] tsuidOrder) {
    this.tsuidOrder = tsuidOrder;
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
   * @param priority the default value is Priority.NORMAL
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

  public void configureConnect(AAssociateRQ aAssociateRQ, Connection remote, DicomNode calledNode) {
    aAssociateRQ.setCalledAET(calledNode.getAet());
    if (identity != null) {
      aAssociateRQ.setUserIdentityRQ(identity);
    }
    remote.setHostname(calledNode.getHostname());
    remote.setPort(calledNode.getPort());
  }

  /**
   * Bind the connection with the callingNode
   *
   * @param connection
   * @param callingNode
   */
  public void configureBind(Connection connection, DicomNode callingNode) {
    if (callingNode.getHostname() != null) {
      connection.setHostname(callingNode.getHostname());
    }
    if (callingNode.getPort() != null) {
      connection.setPort(callingNode.getPort());
    }
  }

  /**
   * Bind the connection and applicationEntity with the callingNode
   *
   * @param applicationEntity
   * @param connection
   * @param callingNode
   */
  public void configureBind(
      ApplicationEntity applicationEntity, Connection connection, DicomNode callingNode) {
    applicationEntity.setAETitle(callingNode.getAet());
    if (callingNode.getHostname() != null) {
      connection.setHostname(callingNode.getHostname());
    }
    if (callingNode.getPort() != null) {
      connection.setPort(callingNode.getPort());
    }
  }

  public void configure(Connection conn) throws IOException {
    if (connectOptions != null) {
      conn.setBacklog(connectOptions.getBacklog());
      conn.setConnectTimeout(connectOptions.getConnectTimeout());
      conn.setRequestTimeout(connectOptions.getRequestTimeout());
      conn.setAcceptTimeout(connectOptions.getAcceptTimeout());
      conn.setReleaseTimeout(connectOptions.getReleaseTimeout());
      conn.setResponseTimeout(connectOptions.getResponseTimeout());
      conn.setRetrieveTimeout(connectOptions.getRetrieveTimeout());
      conn.setIdleTimeout(connectOptions.getIdleTimeout());
      conn.setSocketCloseDelay(connectOptions.getSocloseDelay());
      conn.setReceiveBufferSize(connectOptions.getSorcvBuffer());
      conn.setSendBufferSize(connectOptions.getSosndBuffer());
      conn.setReceivePDULength(connectOptions.getMaxPdulenRcv());
      conn.setSendPDULength(connectOptions.getMaxPdulenSnd());
      conn.setMaxOpsInvoked(connectOptions.getMaxOpsInvoked());
      conn.setMaxOpsPerformed(connectOptions.getMaxOpsPerformed());
      conn.setPackPDV(connectOptions.isPackPDV());
      conn.setTcpNoDelay(connectOptions.isTcpNoDelay());
    }
  }

  public void configureTLS(Connection conn, Connection remote) throws IOException {
    if (tlsOptions != null) {
      if (tlsOptions.getCipherSuites() != null) {
        conn.setTlsCipherSuites(tlsOptions.getCipherSuites().toArray(new String[0]));
      }
      if (tlsOptions.getTlsProtocols() != null) {
        conn.setTlsProtocols(tlsOptions.getTlsProtocols().toArray(new String[0]));
      }
      conn.setTlsNeedClientAuth(tlsOptions.isTlsNeedClientAuth());

      Device device = conn.getDevice();
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
        if (remote != null) {
          remote.setTlsProtocols(conn.getTlsProtocols());
          remote.setTlsCipherSuites(conn.getTlsCipherSuites());
        }
      } catch (GeneralSecurityException e) {
        throw new IOException(e);
      }
    }
  }
}
