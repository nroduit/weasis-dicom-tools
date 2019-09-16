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
package org.weasis.dicom.mf;

import java.util.List;
import java.util.Objects;

public class DefaultQueryResult extends AbstractQueryResult {
    
    protected final WadoParameters wadoParameters;

    public DefaultQueryResult(List<Patient> patients, WadoParameters wadoParameters) {
        super(patients);
        this.wadoParameters = Objects.requireNonNull(wadoParameters);
    }

    @Override
    public WadoParameters getWadoParameters() {
        return wadoParameters;
    }

}