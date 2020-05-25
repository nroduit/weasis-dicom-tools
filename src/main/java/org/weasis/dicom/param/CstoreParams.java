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

import java.net.URL;
import java.util.List;

public class CstoreParams {
    private final List<AttributeEditor> editors;
    private final boolean extendNegociation;
    private final URL extendSopClassesURL;

    /**
     * @param editors
     *            a editor to modify DICOM attributes
     * @param extendNegociation
     *            extends SOP classes negotiation
     * @param extendSopClassesURL
     *            configuration file of the SOP classes negotiation extension
     */
    public CstoreParams(List<AttributeEditor> editors, boolean extendNegociation, URL extendSopClassesURL) {
        this.editors = editors;
        this.extendNegociation = extendNegociation;
        this.extendSopClassesURL = extendSopClassesURL;
    }

    public List<AttributeEditor> getDicomEditors() {
        return editors;
    }

    public boolean isExtendNegociation() {
        return extendNegociation;
    }

    public URL getExtendSopClassesURL() {
        return extendSopClassesURL;
    }
}
