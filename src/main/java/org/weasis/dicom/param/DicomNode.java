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

import org.weasis.core.api.util.StringUtil;

public class DicomNode {

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
}
