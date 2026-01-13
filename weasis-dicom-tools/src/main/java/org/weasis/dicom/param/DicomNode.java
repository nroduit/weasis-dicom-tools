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

import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.net.Association;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 * Represents a DICOM network node with Application Entity Title (AET), hostname, and port.
 *
 * <p>A DICOM node is a network entity that can send or receive DICOM objects. Each node is
 * identified by its AET (up to 16 characters) and optionally by hostname and port for network
 * communication.
 *
 * @since 1.0
 */
public class DicomNode {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomNode.class);

  /** Maximum allowed length for DICOM Application Entity Title */
  private static final int MAX_AET_LENGTH = 16;

  /** Valid port range boundaries */
  private static final int MIN_PORT = 1;

  private static final int MAX_PORT = 65535;

  /** Default loopback IP address */
  private static final String DEFAULT_IP = "127.0.0.1";

  private final String aet;
  private final String hostname;
  private final Integer port;
  private final boolean validateHostname;
  private final Long id;

  /** Creates a DICOM node with only an AET. */
  public DicomNode(String aet) {
    this(aet, null, null);
  }

  /** Creates a DICOM node with AET and port. */
  public DicomNode(String aet, Integer port) {
    this(aet, null, port);
  }

  /** Creates a DICOM node with AET, hostname, and port. */
  public DicomNode(String aet, String hostname, Integer port) {
    this(null, aet, hostname, port, false);
  }

  /** Creates a DICOM node with ID, AET, hostname, and port. */
  public DicomNode(Long id, String aet, String hostname, Integer port) {
    this(id, aet, hostname, port, false);
  }

  /**
   * Creates a DICOM node with full configuration.
   *
   * @param id unique identifier for this node
   * @param aet Application Entity Title (required, max 16 characters)
   * @param hostname network hostname or IP address
   * @param port network port (1-65535)
   * @param validateHostname whether to validate hostname during comparisons
   * @throws IllegalArgumentException if AET is invalid or port is out of range
   */
  public DicomNode(Long id, String aet, String hostname, Integer port, boolean validateHostname) {
    validateAet(aet);
    validatePort(port);
    this.id = id;
    this.aet = aet.trim();
    this.hostname = hostname;
    this.port = port;
    this.validateHostname = validateHostname;
  }

  private static void validateAet(String aet) {
    if (!StringUtil.hasText(aet)) {
      throw new IllegalArgumentException("Missing AETitle");
    }
    if (aet.length() > MAX_AET_LENGTH) {
      throw new IllegalArgumentException("AETitle has more than 16 characters");
    }
  }

  private static void validatePort(Integer port) {
    if (port != null && (port < MIN_PORT || port > MAX_PORT)) {
      throw new IllegalArgumentException("Port is out of bound");
    }
  }

  public Long getId() {
    return id;
  }

  public String getAet() {
    return aet;
  }

  public String getHostname() {
    return hostname;
  }

  public Integer getPort() {
    return port;
  }

  public boolean isValidateHostname() {
    return validateHostname;
  }

  /** Compares hostnames by IP resolution if they differ as strings. */
  public boolean equalsHostname(String anotherHostname) {
    if (Objects.equals(hostname, anotherHostname)) {
      return true;
    }
    return convertToIP(hostname).equals(convertToIP(anotherHostname));
  }

  /** Converts hostname to IP address, returning the hostname or default IP on failure. */
  public static String convertToIP(String hostname) {
    if (!StringUtil.hasText(hostname)) {
      return DEFAULT_IP;
    }
    try {
      return InetAddress.getByName(hostname).getHostAddress();
    } catch (UnknownHostException e) {
      LOGGER.debug("Cannot resolve hostname '{}': {}", hostname, e.getMessage());
      return hostname;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DicomNode dicomNode)) return false;

    return Objects.equals(aet, dicomNode.aet)
        && Objects.equals(hostname, dicomNode.hostname)
        && Objects.equals(port, dicomNode.port);
  }

  @Override
  public int hashCode() {
    return Objects.hash(aet, hostname, port);
  }

  @Override
  public String toString() {
    return "DicomNode{aet='%s', hostname='%s', port=%d}".formatted(aet, hostname, port);
  }

  /** Creates a DICOM node representing the local endpoint of an association. */
  public static DicomNode buildLocalDicomNode(Association as) {
    Socket socket = as.getSocket();
    String ip = extractIpAddress(socket.getLocalAddress());
    return new DicomNode(as.getLocalAET(), ip, socket.getLocalPort());
  }

  /** Creates a DICOM node representing the remote endpoint of an association. */
  public static DicomNode buildRemoteDicomNode(Association as) {
    Socket socket = as.getSocket();
    String ip = extractIpAddress(socket.getInetAddress());
    return new DicomNode(as.getRemoteAET(), ip, socket.getPort());
  }

  private static String extractIpAddress(InetAddress address) {
    return Optional.ofNullable(address).map(InetAddress::getHostAddress).orElse(null);
  }
}
