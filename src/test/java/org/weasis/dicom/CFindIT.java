/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
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
import org.dcm4che3.net.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;

@DisplayName("DICOM C-FIND")
class CFindIT {

  @Test
  @DisplayName("Test Process")
  void testProcess() {
    DicomParam[] params = {
      new DicomParam(Tag.PatientID, "ModifiedPatientID"),
      new DicomParam(Tag.StudyInstanceUID),
      new DicomParam(Tag.NumberOfStudyRelatedSeries)
    };
    DicomNode calling = new DicomNode("WEASIS-SCU");
    DicomNode called = new DicomNode("DICOMSERVER", "www.dicomserver.co.uk", 104);
    DicomState state = CFind.process(calling, called, params);
    // Should never happen
    Assertions.assertNotNull(state);
    List<Attributes> items = state.getDicomRSP();
    for (int i = 0; i < items.size(); i++) {
      Attributes item = items.get(i);
      System.out.println("===========================================");
      System.out.println("CFind Item " + (i + 1));
      System.out.println("===========================================");
      System.out.println(item.toString(100, 150));
    }
    System.out.println("DICOM Status:" + state.getStatus());
    System.out.println(state.getMessage());
    // see org.dcm4che3.net.Status
    // See server log at https://dicomserver.co.uk/logs/
    Assertions.assertEquals(Status.Success, state.getStatus(), state.getMessage());
    assertFalse(state.getDicomRSP().isEmpty(), "No DICOM RSP Object in response");
  }
}
