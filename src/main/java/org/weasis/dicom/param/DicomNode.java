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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import org.dcm4che3.net.Association;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.StringUtil;

public class DicomNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomNode.class);

    private final String aet;
    private final String hostname;
    private final Integer port;

    public DicomNode(String aet) {
        this(aet, null, null);
    }

    public DicomNode(String aet, Integer port) {
        this(aet, null, port);
    }

    public DicomNode(String aet, String hostname, Integer port) {
        if (!StringUtil.hasText(aet)) {
            throw new IllegalArgumentException("Missing AET");
        }
        if (aet.length() > 16) {
            throw new IllegalArgumentException("AET has more than 16 characters");
        }
        if (port != null && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException("Port out of bound");
        }
        this.aet = aet;
        this.hostname = hostname;
        this.port = port;
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

    private boolean equalsHostname(String anotherHostname) {
        if (Objects.equals(hostname, anotherHostname)) {
            return true;
        }
        return convertToIP(hostname).equals(convertToIP(anotherHostname));
    }

    private String convertToIP(String hostname) {
        try {
            return InetAddress.getByName(hostname).getHostAddress();
        } catch (UnknownHostException e) {
            LOGGER.error("Cannot resolve hostname", e);
        }
        return StringUtil.hasText(hostname) ? hostname : "127.0.0.1";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + aet.hashCode();
        result = prime * result + convertToIP(hostname).hashCode();
        result = prime * result + ((port == null) ? 0 : port.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DicomNode other = (DicomNode) obj;
        if (!aet.equals(other.aet))
            return false;
        if (!equalsHostname(other.hostname))
            return false;
        if (port == null) {
            if (other.port != null)
                return false;
        } else if (!port.equals(other.port))
            return false;
        return true;
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
