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

public abstract class ForwardDestination {

    protected final AttributeEditor attributesEditor;
    private final Long id;

    public ForwardDestination(Long id, AttributeEditor attributesEditor) {
        this.attributesEditor = attributesEditor;
        this.id = id;
    }

    public AttributeEditor getAttributesEditor() {
        return attributesEditor;
    }
    
    public Long getId() {
        return id;
    }

    public abstract ForwardDicomNode getForwardDicomNode();

    public abstract void stop();
}