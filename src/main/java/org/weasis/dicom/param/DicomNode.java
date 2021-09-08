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
import java.net.UnknownHostException;
import java.util.Objects;
import org.dcm4che3.net.Association;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

public class DicomNode {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomNode.class);

  private final String aet;
  private final String hostname;
  private final Integer port;
  private final boolean validateHostname;
  private final Long id;

  public DicomNode(String aet) {
    this(aet, null, null);
  }

  public DicomNode(String aet, Integer port) {
    this(aet, null, port);
  }

  public DicomNode(String aet, String hostname, Integer port) {
    this(null, aet, hostname, port, false);
  }

  public DicomNode(Long id, String aet, String hostname, Integer port) {
    this(id, aet, hostname, port, false);
  }

  public DicomNode(Long id, String aet, String hostname, Integer port, boolean validateHostname) {
    if (!StringUtil.hasText(aet)) {
      throw new IllegalArgumentException("Missing AETitle");
    }
    if (aet.length() > 16) {
      throw new IllegalArgumentException("AETitle has more than 16 characters");
    }
    if (port != null && (port < 1 || port > 65535)) {
      throw new IllegalArgumentException("Port is out of bound");
    }
    this.id = id;
    this.aet = aet.trim();
    this.hostname = hostname;
    this.port = port;
    this.validateHostname = validateHostname;
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

  public boolean equalsHostname(String anotherHostname) {
    if (Objects.equals(hostname, anotherHostname)) {
      return true;
    }
    return convertToIP(hostname).equals(convertToIP(anotherHostname));
  }

  public static String convertToIP(String hostname) {
    try {
      return InetAddress.getByName(hostname).getHostAddress();
    } catch (UnknownHostException e) {
      LOGGER.error("Cannot resolve hostname", e);
    }
    return StringUtil.hasText(hostname) ? hostname : "127.0.0.1";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DicomNode dicomNode = (DicomNode) o;
    return aet.equals(dicomNode.aet)
        && Objects.equals(hostname, dicomNode.hostname)
        && Objects.equals(port, dicomNode.port);
  }

  @Override
  public int hashCode() {
    return Objects.hash(aet, hostname, port);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder("Host=");
    buf.append(hostname);
    buf.append(" AET=");
    buf.append(aet);
    buf.append(" Port=");
    buf.append(port);
    return buf.toString();
  }

  public static DicomNode buildLocalDicomNode(Association as) {
    String ip = null;
    InetAddress address = as.getSocket().getLocalAddress();
    if (address != null) {
      ip = address.getHostAddress();
    }
    return new DicomNode(as.getLocalAET(), ip, as.getSocket().getLocalPort());
  }

  public static DicomNode buildRemoteDicomNode(Association as) {
    String ip = null;
    InetAddress address = as.getSocket().getInetAddress();
    if (address != null) {
      ip = address.getHostAddress();
    }
    return new DicomNode(as.getRemoteAET(), ip, as.getSocket().getPort());
  }
}
