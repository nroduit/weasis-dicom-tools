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

    public ForwardDestination(AttributeEditor attributesEditor) {
        this.attributesEditor = attributesEditor;
    }

    public AttributeEditor getAttributesEditor() {
        return attributesEditor;
    }

    public abstract ForwardDicomNode getForwardDicomNode();

    public abstract void stop();
}