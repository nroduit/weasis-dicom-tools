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
package org.weasis.dicom;

import java.net.MalformedURLException;
import java.util.List;
import java.util.OptionalInt;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.net.Status;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;

public class CFindNetTest {
    
    @BeforeAll
    public static void setLogger() throws MalformedURLException {
        BasicConfigurator.configure();
    }
    
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

         List<DicomObject> items = state.getDicomRSP();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                DicomObject item = items.get(i);
                System.out.println("===========================================");
                System.out.println("CFind Item " + (i + 1));
                System.out.println("===========================================");
                System.out.println(item.toString(100, 150));
            }
        }

        // See server log at http://dicomserver.co.uk/logs/
        System.out.println("DicomState result: ");
        System.out.println("DICOM Status:" + state.getStatus());
        // See org.dcm4ch6.net.Status
        System.out.println("\tDICOM Status: " + String.format("0x%04X", state.getStatus().orElseThrow() & 0xFFFF));
        System.out.println("\t" + state.getMessage());
        // see org.dcm4che3.net.Status
        Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(OptionalInt.of(Status.Success)));
        Assert.assertFalse("No DICOM RSP Object", state.getDicomRSP().isEmpty());
    }

}
