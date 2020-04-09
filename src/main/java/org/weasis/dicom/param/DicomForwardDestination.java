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

import java.io.IOException;

import org.weasis.dicom.util.StoreFromStreamSCU;

public class DicomForwardDestination extends ForwardDestination {

    private final StoreFromStreamSCU streamSCU;
    private final boolean useDestinationAetForKeyMap;

    private final ForwardDicomNode callingNode;
    private final DicomNode destinationNode;

    public DicomForwardDestination(ForwardDicomNode fwdNode, DicomNode destinationNode) throws IOException {
        this(null, fwdNode, destinationNode, null);
    }

    public DicomForwardDestination(AdvancedParams forwardParams, ForwardDicomNode fwdNode, DicomNode destinationNode)
        throws IOException {
        this(forwardParams, fwdNode, destinationNode, null);
    }

    public DicomForwardDestination(AdvancedParams forwardParams, ForwardDicomNode fwdNode, DicomNode destinationNode,
        AttributeEditor attributesEditor) throws IOException {
        this(forwardParams, fwdNode, destinationNode, false, null, attributesEditor);
    }

    /**
     * @param forwardParams
     *            optional advanced parameters (proxy, authentication, connection and TLS)
     * @param fwdNode
     *            the DICOM forwarding node. Cannot be null.
     * @param destinationNode
     *            the DICOM destination node. Cannot be null.
     * @param useDestinationAetForKeyMap
     * @param progress
     * @param attributesEditor
     * @throws IOException
     */
    public DicomForwardDestination(AdvancedParams forwardParams, ForwardDicomNode fwdNode, DicomNode destinationNode,
        boolean useDestinationAetForKeyMap, DicomProgress progress, AttributeEditor attributesEditor)
        throws IOException {
        this(null, forwardParams, fwdNode, destinationNode, useDestinationAetForKeyMap, progress, attributesEditor);
    }
    
    public DicomForwardDestination(Long id, AdvancedParams forwardParams, ForwardDicomNode fwdNode, DicomNode destinationNode,
        boolean useDestinationAetForKeyMap, DicomProgress progress, AttributeEditor attributesEditor)
        throws IOException {
        super(id, attributesEditor);
        this.callingNode = fwdNode;
        this.destinationNode = destinationNode;
        CstoreParams params = new CstoreParams(attributesEditor, false, null);
        this.streamSCU = new StoreFromStreamSCU(forwardParams, fwdNode, destinationNode, progress, params);
        this.useDestinationAetForKeyMap = useDestinationAetForKeyMap;
    }

    public StoreFromStreamSCU getStreamSCU() {
        return streamSCU;
    }

    public boolean isUseDestinationAetForKeyMap() {
        return useDestinationAetForKeyMap;
    }

    @Override
    public ForwardDicomNode getForwardDicomNode() {
        return callingNode;
    }

    public DicomNode getDestinationNode() {
        return destinationNode;
    }

    @Override
    public void stop() {
        streamSCU.triggerCloseExecutor();
        streamSCU.close();
    }

    @Override
    public String toString() {
        return destinationNode.toString();
    }
}
