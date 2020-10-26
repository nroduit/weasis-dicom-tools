/*******************************************************************************
 * Copyright (c) 2009-2019 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.param;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class ForwardDicomNode extends DicomNode {

    private final String forwardAETitle;
    private final Set<DicomNode> acceptedSourceNodes;
    private final Long id;


    public ForwardDicomNode(DicomNode dicomNode) {
        this(dicomNode.getAet(), dicomNode.getHostname());
    }

    public ForwardDicomNode(String fwdAeTitle) {
        this(fwdAeTitle, null);
    }

    public ForwardDicomNode(String fwdAeTitle, String fwdHostname) {
        this(fwdAeTitle, fwdHostname, null);
    }

    public ForwardDicomNode(String fwdAeTitle, String fwdHostname, Long id) {
        super(fwdAeTitle, fwdHostname, null);
        this.forwardAETitle = fwdAeTitle;
        this.id = id;
        this.acceptedSourceNodes = new HashSet<>();
    }

    public void addAcceptedSourceNode(String srcAeTitle) {
        addAcceptedSourceNode(srcAeTitle, null);
    }

    public void addAcceptedSourceNode(String srcAeTitle, String srcHostname) {
        acceptedSourceNodes.add(new DicomNode(null, srcAeTitle, srcHostname, null, srcHostname != null));
    }

    public void addAcceptedSourceNode(Long id, String srcAeTitle, String srcHostname) {
        acceptedSourceNodes.add(new DicomNode(id, srcAeTitle, srcHostname, null, srcHostname != null));
    }

    public Set<DicomNode> getAcceptedSourceNodes() {
        return acceptedSourceNodes;
    }

    public String getForwardAETitle() {
        return forwardAETitle;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String toString() {
        return forwardAETitle;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + forwardAETitle.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass())
            return false;
        ForwardDicomNode other = (ForwardDicomNode) obj;
        return forwardAETitle.equals(other.forwardAETitle);
    }

}
