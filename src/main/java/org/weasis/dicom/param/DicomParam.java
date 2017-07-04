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

import org.dcm4che3.data.ElementDictionary;

public class DicomParam {

    private final int tag;
    private final String[] values;
    private final int[] parentSeqTags;

    public DicomParam(int tag, String... values) {
        this(null, tag, values);
    }

    public DicomParam(int[] parentSeqTags, int tag, String... values) {
        this.tag = tag;
        this.values = values;
        this.parentSeqTags = parentSeqTags;
    }

    public int getTag() {
        return tag;
    }

    public String[] getValues() {
        return values;
    }

    public int[] getParentSeqTags() {
        return parentSeqTags;
    }

    public String getTagName() {
        return ElementDictionary.keywordOf(tag, null);
    }

}
