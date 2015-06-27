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
import org.weasis.dicom.tool.ModalityWorklist;

public class ModalityWorklistNetTest {

    @Test
    public void testProcess() {

        DicomParam stationAet = new DicomParam(Tag.ScheduledStationAETitle, "ADVT");

        DicomParam[] SPS_RETURN_KEYS = { CFind.Modality, ModalityWorklist.RequestedContrastAgent, stationAet,
            ModalityWorklist.ScheduledProcedureStepStartDate, ModalityWorklist.ScheduledProcedureStepStartTime,
            ModalityWorklist.ScheduledPerformingPhysicianName, ModalityWorklist.ScheduledProcedureStepDescription,
            ModalityWorklist.ScheduledProcedureStepID, ModalityWorklist.ScheduledStationName,
            ModalityWorklist.ScheduledProcedureStepLocation, ModalityWorklist.PreMedication,
            ModalityWorklist.ScheduledProcedureStepStatus };

        DicomParam[] RETURN_KEYS = { CFind.AccessionNumber, CFind.ReferringPhysicianName, CFind.PatientName,
            CFind.PatientID, CFind.PatientBirthDate, CFind.PatientSex, ModalityWorklist.PatientWeight,
            ModalityWorklist.MedicalAlerts, ModalityWorklist.Allergies, ModalityWorklist.PregnancyStatus,
            CFind.StudyInstanceUID, ModalityWorklist.RequestingPhysician, ModalityWorklist.RequestingService,
            ModalityWorklist.RequestedProcedureDescription, ModalityWorklist.AdmissionID, ModalityWorklist.SpecialNeeds,
            ModalityWorklist.CurrentPatientLocation, ModalityWorklist.PatientState,
            ModalityWorklist.RequestedProcedureID, ModalityWorklist.RequestedProcedurePriority,
            ModalityWorklist.PatientTransportArrangements, ModalityWorklist.PlacerOrderNumberImagingServiceRequest,
            ModalityWorklist.FillerOrderNumberImagingServiceRequest,
            ModalityWorklist.ConfidentialityConstraintOnPatientDataDescription };

        DicomNode calling = new DicomNode("WEASIS-SCU");
        DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);

        DicomState state = ModalityWorklist.process(null, calling, called, 0, SPS_RETURN_KEYS, RETURN_KEYS);

        // Should never happen
        Assert.assertNotNull(state);

        List<Attributes> items = state.getDicomRSP();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                Attributes item = items.get(i);
                System.out.println("===========================================");
                System.out.println("Worklist Item " + (i + 1));
                System.out.println("===========================================");
                System.out.println(item.toString(100, 150));
            }
        }

        // see org.dcm4che3.net.Status
        // See server log at http://dicomserver.co.uk/logs/
        Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));
    }

}
