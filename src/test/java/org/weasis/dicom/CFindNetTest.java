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
package org.weasis.dicom;

import java.util.List;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Test;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;

public class CFindNetTest {

    @Test
    public void testProcess() {
        BasicConfigurator.configure();
        
        DicomParam[] params = { new DicomParam(Tag.PatientID, "PAT001"), new DicomParam(Tag.StudyInstanceUID),
            new DicomParam(Tag.NumberOfStudyRelatedSeries) };
        DicomNode calling = new DicomNode("WEASIS-SCU");
        DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);
        DicomState state = CFind.process(calling, called, params);
        // Should never happen
        Assert.assertNotNull(state);

        List<Attributes> items = state.getDicomRSP();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                Attributes item = items.get(i);
                System.out.println("===========================================");
                System.out.println("CFind Item " + (i + 1));
                System.out.println("===========================================");
                System.out.println(item.toString(100, 150));
            }
        }

        System.out.println("DICOM Status:" + state.getStatus());
        System.out.println(state.getMessage());
        // see org.dcm4che3.net.Status
        // See server log at http://dicomserver.co.uk/logs/
        Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));
        Assert.assertFalse("No DICOM RSP Object", state.getDicomRSP().isEmpty());
    }

}
