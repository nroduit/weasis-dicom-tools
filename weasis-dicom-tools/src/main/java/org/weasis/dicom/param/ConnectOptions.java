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

import org.dcm4che3.net.Connection;

/**
 * Configuration options for DICOM connections, encapsulating socket, PDU, timeout, and operational
 * settings for DICOM network communication.
 *
 * <p>All timeout values are specified in milliseconds, where zero indicates no timeout. Buffer
 * sizes and PDU lengths are specified in bytes.
 */
public final class ConnectOptions {

  // Operation settings
  private int maxOpsInvoked = Connection.SYNCHRONOUS_MODE;
  private int maxOpsPerformed = Connection.SYNCHRONOUS_MODE;

  // PDU settings
  private int maxPdulenRcv = Connection.DEF_MAX_PDU_LENGTH;
  private int maxPdulenSnd = Connection.DEF_MAX_PDU_LENGTH;

  private boolean packPDV = true;
  // Connection settings
  private int backlog = Connection.DEF_BACKLOG;
  private boolean tcpNoDelay = true;
  private int sosndBuffer = Connection.DEF_BUFFERSIZE;
  private int sorcvBuffer = Connection.DEF_BUFFERSIZE;
  private int socloseDelay = Connection.DEF_SOCKETDELAY;

  // Timeout settings (all in milliseconds)
  private int connectTimeout = Connection.NO_TIMEOUT;
  private int requestTimeout = Connection.NO_TIMEOUT;
  private int acceptTimeout = Connection.NO_TIMEOUT;
  private int releaseTimeout = Connection.NO_TIMEOUT;
  private int responseTimeout = Connection.NO_TIMEOUT;
  private int retrieveTimeout = Connection.NO_TIMEOUT;
  private int idleTimeout = Connection.NO_TIMEOUT;

  /** Creates connection options with default values from dcm4che3 Connection constants. */
  public ConnectOptions() {
    // Default initialization is handled by field declarations
  }

  // Operation settings

  /**
   * Gets the maximum number of operations this AE may invoke asynchronously.
   *
   * @return max operations invoked (0 = unlimited, 1 = synchronous)
   */
  public int getMaxOpsInvoked() {
    return maxOpsInvoked;
  }

  public void setMaxOpsInvoked(int maxOpsInvoked) {
    this.maxOpsInvoked = maxOpsInvoked;
  }

  /**
   * Gets the maximum number of operations this AE may perform asynchronously.
   *
   * @return max operations performed (0 = unlimited, 1 = synchronous)
   */
  public int getMaxOpsPerformed() {
    return maxOpsPerformed;
  }

  public void setMaxOpsPerformed(int maxOpsPerformed) {
    this.maxOpsPerformed = maxOpsPerformed;
  }

  // PDU settings

  /**
   * Gets the maximum PDU length for receiving data.
   *
   * @return max receive PDU length in bytes
   */
  public int getMaxPdulenRcv() {
    return maxPdulenRcv;
  }

  public void setMaxPdulenRcv(int maxPdulenRcv) {
    this.maxPdulenRcv = maxPdulenRcv;
  }

  /**
   * Gets the maximum PDU length for sending data.
   *
   * @return max send PDU length in bytes
   */
  public int getMaxPdulenSnd() {
    return maxPdulenSnd;
  }

  public void setMaxPdulenSnd(int maxPdulenSnd) {
    this.maxPdulenSnd = maxPdulenSnd;
  }

  /**
   * Checks if PDV (Presentation Data Value) packing is enabled.
   *
   * @return true if PDV packing is enabled
   */
  public boolean isPackPDV() {
    return packPDV;
  }

  public void setPackPDV(boolean packPDV) {
    this.packPDV = packPDV;
  }

  // Connection settings

  public int getBacklog() {
    return backlog;
  }

  public void setBacklog(int backlog) {
    this.backlog = backlog;
  }

  public boolean isTcpNoDelay() {
    return tcpNoDelay;
  }

  public void setTcpNoDelay(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
  }

  /**
   * Gets the socket send buffer size.
   *
   * @return send buffer size in bytes
   */
  public int getSosndBuffer() {
    return sosndBuffer;
  }

  public void setSosndBuffer(int sosndBuffer) {
    this.sosndBuffer = sosndBuffer;
  }

  /**
   * Gets the socket receive buffer size.
   *
   * @return receive buffer size in bytes
   */
  public int getSorcvBuffer() {
    return sorcvBuffer;
  }

  public void setSorcvBuffer(int sorcvBuffer) {
    this.sorcvBuffer = sorcvBuffer;
  }

  public int getSocloseDelay() {
    return socloseDelay;
  }

  public void setSocloseDelay(int socloseDelay) {
    this.socloseDelay = socloseDelay;
  }

  // Timeout settings

  /**
   * Gets the connection timeout.
   *
   * @return timeout in milliseconds (0 = no timeout)
   */
  public int getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(int connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  /**
   * Gets the request timeout.
   *
   * @return timeout in milliseconds (0 = no timeout)
   */
  public int getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(int requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  /**
   * Gets the accept timeout.
   *
   * @return timeout in milliseconds (0 = no timeout)
   */
  public int getAcceptTimeout() {
    return acceptTimeout;
  }

  public void setAcceptTimeout(int acceptTimeout) {
    this.acceptTimeout = acceptTimeout;
  }

  /**
   * Gets the release timeout.
   *
   * @return timeout in milliseconds (0 = no timeout)
   */
  public int getReleaseTimeout() {
    return releaseTimeout;
  }

  public void setReleaseTimeout(int releaseTimeout) {
    this.releaseTimeout = releaseTimeout;
  }

  /**
   * Gets the response timeout.
   *
   * @return timeout in milliseconds (0 = no timeout)
   */
  public int getResponseTimeout() {
    return responseTimeout;
  }

  public void setResponseTimeout(int responseTimeout) {
    this.responseTimeout = responseTimeout;
  }

  /**
   * Gets the retrieve timeout.
   *
   * @return timeout in milliseconds (0 = no timeout)
   */
  public int getRetrieveTimeout() {
    return retrieveTimeout;
  }

  public void setRetrieveTimeout(int retrieveTimeout) {
    this.retrieveTimeout = retrieveTimeout;
  }

  /**
   * Gets the idle timeout.
   *
   * @return timeout in milliseconds (0 = no timeout)
   */
  public int getIdleTimeout() {
    return idleTimeout;
  }

  public void setIdleTimeout(int idleTimeout) {
    this.idleTimeout = idleTimeout;
  }
}
