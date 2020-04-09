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
import org.weasis.dicom.tool.ModalityWorklist;

public class ModalityWorklistNetTest {
    
    @BeforeAll
    public static void setLogger() throws MalformedURLException {
        BasicConfigurator.configure();
    }

    @Test
    public void testProcess() {

        // Filter by AETitle by setting a value
        final int[] sps = { Tag.ScheduledProcedureStepSequence };
        DicomParam stationAet = new DicomParam(sps, Tag.ScheduledStationAETitle, "ADVT");

        DicomParam[] RETURN_KEYS = { CFind.AccessionNumber, CFind.IssuerOfAccessionNumberSequence,
            CFind.ReferringPhysicianName, CFind.PatientName, CFind.PatientID, CFind.IssuerOfPatientID,
            CFind.PatientBirthDate, CFind.PatientSex, ModalityWorklist.PatientWeight, ModalityWorklist.MedicalAlerts,
            ModalityWorklist.Allergies, ModalityWorklist.PregnancyStatus, CFind.StudyInstanceUID,
            ModalityWorklist.RequestingPhysician, ModalityWorklist.RequestingService,
            ModalityWorklist.RequestedProcedureDescription, ModalityWorklist.RequestedProcedureCodeSequence,
            ModalityWorklist.AdmissionID, ModalityWorklist.IssuerOfAdmissionIDSequence, ModalityWorklist.SpecialNeeds,
            ModalityWorklist.CurrentPatientLocation, ModalityWorklist.PatientState,
            ModalityWorklist.RequestedProcedureID, ModalityWorklist.RequestedProcedurePriority,
            ModalityWorklist.PatientTransportArrangements, ModalityWorklist.PlacerOrderNumberImagingServiceRequest,
            ModalityWorklist.FillerOrderNumberImagingServiceRequest,
            ModalityWorklist.ConfidentialityConstraintOnPatientDataDescription,
            // Scheduled Procedure Step Sequence
            ModalityWorklist.Modality, ModalityWorklist.RequestedContrastAgent, stationAet,
            ModalityWorklist.ScheduledProcedureStepStartDate, ModalityWorklist.ScheduledProcedureStepStartTime,
            ModalityWorklist.ScheduledPerformingPhysicianName, ModalityWorklist.ScheduledProcedureStepDescription,
            ModalityWorklist.ScheduledProcedureStepID, ModalityWorklist.ScheduledStationName,
            ModalityWorklist.ScheduledProcedureStepLocation, ModalityWorklist.PreMedication,
            ModalityWorklist.ScheduledProcedureStepStatus, ModalityWorklist.ScheduledProtocolCodeSequence };

        DicomNode calling = new DicomNode("WEASIS-SCU");
        DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);
        // DicomNode called = new DicomNode("DCM4CHEE", "localhost", 11112);

        DicomState state = ModalityWorklist.process(null, calling, called, 0, RETURN_KEYS);

        // Should never happen
        Assert.assertNotNull(state);

        List<DicomObject> items = state.getDicomRSP();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                DicomObject item = items.get(i);
                System.out.println("===========================================");
                System.out.println("Worklist Item " + (i + 1));
                System.out.println("===========================================");
                System.out.println(item.toString(100, 150));
            }
        }

        System.out.println("DICOM Status:" + state.getStatus());
        System.out.println(state.getMessage());

        // see org.dcm4che3.net.Status
        // See server log at http://dicomserver.co.uk/logs/
        Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));
    }

}
