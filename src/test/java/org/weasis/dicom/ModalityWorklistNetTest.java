/*
 * Copyright (c) 2014-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.tool.ModalityWorklist;

@DisplayName("DICOM Modality Worklist")
class ModalityWorklistNetTest {

  @Test
  void testProcess() {
    // Filter by AETitle by setting a value
    final int[] sps = {Tag.ScheduledProcedureStepSequence};
    DicomParam stationAet = new DicomParam(sps, Tag.ScheduledStationAETitle, "ADVT");
    DicomParam[] RETURN_KEYS = {
      CFind.AccessionNumber,
      CFind.IssuerOfAccessionNumberSequence,
      CFind.ReferringPhysicianName,
      CFind.PatientName,
      CFind.PatientID,
      CFind.IssuerOfPatientID,
      CFind.PatientBirthDate,
      CFind.PatientSex,
      ModalityWorklist.PatientWeight,
      ModalityWorklist.MedicalAlerts,
      ModalityWorklist.Allergies,
      ModalityWorklist.PregnancyStatus,
      CFind.StudyInstanceUID,
      ModalityWorklist.RequestingPhysician,
      ModalityWorklist.RequestingService,
      ModalityWorklist.RequestedProcedureDescription,
      ModalityWorklist.RequestedProcedureCodeSequence,
      ModalityWorklist.AdmissionID,
      ModalityWorklist.IssuerOfAdmissionIDSequence,
      ModalityWorklist.SpecialNeeds,
      ModalityWorklist.CurrentPatientLocation,
      ModalityWorklist.PatientState,
      ModalityWorklist.RequestedProcedureID,
      ModalityWorklist.RequestedProcedurePriority,
      ModalityWorklist.PatientTransportArrangements,
      ModalityWorklist.PlacerOrderNumberImagingServiceRequest,
      ModalityWorklist.FillerOrderNumberImagingServiceRequest,
      ModalityWorklist.ConfidentialityConstraintOnPatientDataDescription,
      // Scheduled Procedure Step Sequence
      ModalityWorklist.Modality,
      ModalityWorklist.RequestedContrastAgent,
      stationAet,
      ModalityWorklist.ScheduledProcedureStepStartDate,
      ModalityWorklist.ScheduledProcedureStepStartTime,
      ModalityWorklist.ScheduledPerformingPhysicianName,
      ModalityWorklist.ScheduledProcedureStepDescription,
      ModalityWorklist.ScheduledProcedureStepID,
      ModalityWorklist.ScheduledStationName,
      ModalityWorklist.ScheduledProcedureStepLocation,
      ModalityWorklist.PreMedication,
      ModalityWorklist.ScheduledProcedureStepStatus,
      ModalityWorklist.ScheduledProtocolCodeSequence
    };

    DicomNode calling = new DicomNode("WEASIS-SCU");
    DicomNode called = new DicomNode("DICOMSERVER", "www.dicomserver.co.uk", 104);
    // DicomNode called = new DicomNode("DCM4CHEE", "localhost", 11112);

    DicomState state = ModalityWorklist.process(null, calling, called, 0, RETURN_KEYS);

    // Should never happen
    Assertions.assertNotNull(state);

    List<Attributes> items = state.getDicomRSP();
    for (int i = 0; i < items.size(); i++) {
      Attributes item = items.get(i);
      System.out.println("===========================================");
      System.out.println("Worklist Item " + (i + 1));
      System.out.println("===========================================");
      System.out.println(item.toString(100, 150));
    }

    System.out.println("DICOM Status:" + state.getStatus());
    System.out.println(state.getMessage());

    // See server log at https://dicomserver.co.uk/logs/
    assertFalse(state.getDicomRSP().isEmpty(), "No DICOM RSP Object in response");
  }
}
