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

import java.util.List;

public abstract class ForwardDestination {

    protected final List<AttributeEditor> dicomEditors;
    private final Long id;

    public ForwardDestination(Long id, List<AttributeEditor> dicomEditors) {
        this.dicomEditors = dicomEditors;
        this.id = id;
    }

    public List<AttributeEditor> getDicomEditors() {
        return dicomEditors;
    }
    
    public Long getId() {
        return id;
    }

    public abstract ForwardDicomNode getForwardDicomNode();

    public abstract void stop();

    public abstract DicomState getState();
}