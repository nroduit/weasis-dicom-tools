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

import java.util.EnumSet;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.op.CMove;
import org.weasis.dicom.param.*;

@DisplayName("DICOM C-MOVE")
class CMoveIT {

  @Test
  void testProcess() {
    DicomProgress progress = new DicomProgress();
    progress.addProgressListener(
        progress1 -> {
          System.out.println(
              "Remaining operations:" + progress1.getNumberOfRemainingSuboperations());
          // if (progress.getNumberOfRemainingSuboperations() == 100) {
          // progress.cancel();
          // }
        });
    // The following parameters must be changed to get a successful test.
    // Move a study
    DicomParam[] params = {
      new DicomParam(
          Tag.StudyInstanceUID, "1.2.528.1.1001.100.2.3865.6101.93503564261.20070711142700372")
    };
    // // Move series
    // DicomParam[] params = { new DicomParam(Tag.QueryRetrieveLevel, "SERIES"),
    // new DicomParam(Tag.SeriesInstanceUID,
    // "1.2.528.1.1001.100.3.3865.6101.93503564261.20070711142700388") };
    // // Move image
    // DicomParam[] params = { new DicomParam(Tag.QueryRetrieveLevel, "IMAGE"),
    // new DicomParam(Tag.SOPInstanceUID,
    // "1.2.528.1.1001.100.4.3865.6101.93503564261.20070711142700497") };
    DicomNode calling = new DicomNode("WEASIS-SCU");
    DicomNode called = new DicomNode("DCM4CHEE", "localhost", 11112);
    AdvancedParams options = new AdvancedParams();
    options.setQueryOptions(
        EnumSet.of(QueryOption.RELATIONAL)); // Required for QueryRetrieveLevel other than study
    DicomState state = CMove.process(options, calling, called, "WEASIS-SCU", progress, params);

    // Should never happen
    Assertions.assertNotNull(state);
    System.out.println("DICOM Status:" + state.getStatus());
    System.out.println(state.getMessage());
    System.out.println(
        "NumberOfRemainingSuboperations:" + progress.getNumberOfRemainingSuboperations());
    System.out.println(
        "NumberOfCompletedSuboperations:" + progress.getNumberOfCompletedSuboperations());
    System.out.println("NumberOfFailedSuboperations:" + progress.getNumberOfFailedSuboperations());
    System.out.println(
        "NumberOfWarningSuboperations:" + progress.getNumberOfWarningSuboperations());

    // see org.dcm4che3.net.Status
    // See server log at https://dicomserver.co.uk/logs/
    Assertions.assertEquals(Status.Success, state.getStatus(), state.getMessage());
    // Assert.assertFalse("No DICOM RSP Object", state.getDicomRSP().isEmpty());
  }
}
