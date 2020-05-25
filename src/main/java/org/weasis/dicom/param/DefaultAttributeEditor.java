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

import java.util.HashMap;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.util.UIDUtils;



public class DefaultAttributeEditor implements AttributeEditor {
    private HashMap<String, String> uidMap;
    private final boolean generateUIDs;
    private final DicomObject tagToOverride;

    public DefaultAttributeEditor(DicomObject tagToOverride) {
        this(false, tagToOverride);
    }

    /**
     * @param generateUIDs
     *            generate new UIDS for Study, Series and Instance
     * @param tagToOverride
     *            list of DICOM attributes to override
     * 
     */
    public DefaultAttributeEditor(boolean generateUIDs, DicomObject tagToOverride) {
        this.generateUIDs = generateUIDs;
        this.tagToOverride = tagToOverride;
        this.uidMap = generateUIDs ? new HashMap<>() : null;
    }

    @Override
    public void apply(DicomObject data, AttributeEditorContext context) {
        if (data != null) {
            if (generateUIDs) {
                // New Study UID
                String oldStudyUID = data.getString(Tag.StudyInstanceUID).orElseThrow();
                String studyUID = uidMap.computeIfAbsent(oldStudyUID, k -> UIDUtils.randomUID());
                data.setString(Tag.StudyInstanceUID, VR.UI, studyUID);

                // New Series UID
                String oldSeriesUID = data.getString(Tag.SeriesInstanceUID).orElseThrow();;
                String seriesUID = uidMap.computeIfAbsent(oldSeriesUID, k -> UIDUtils.randomUID());
                data.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);

                // New Sop UID
                String iuid = UIDUtils.randomUID();
                data.setString(Tag.SOPInstanceUID, VR.UI, iuid);
            }
            if (tagToOverride != null && !tagToOverride.isEmpty()) {
                tagToOverride.elementStream().forEach(data::add);
             //   data.update(Attributes.UpdatePolicy.OVERWRITE, tagToOverride, null);
            }
        }
    }

}
