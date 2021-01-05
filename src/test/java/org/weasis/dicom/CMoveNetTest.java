/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom;

import java.net.MalformedURLException;
import org.apache.log4j.BasicConfigurator;
import org.dcm4che6.data.Tag;
import org.dcm4che6.net.Status;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.op.CMove;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

public class CMoveNetTest {

  @BeforeAll
  public static void setLogger() throws MalformedURLException {
    BasicConfigurator.configure();
  }

  @Test
  public void testProcess() {
    DicomProgress progress = new DicomProgress();
    progress.addProgressListener(
        progress1 -> {
          System.out.println(
              "Remaining operations:" + progress1.getNumberOfRemainingSuboperations());
          // if (progress.getNumberOfRemainingSuboperations() == 100) {
          // progress.cancel();
          // }
        });

    /** The following parameters must be changed to get a successful test. */

    // Move study
    DicomParam[] params = {
      new DicomParam(Tag.StudyInstanceUID, "1.3.46.670589.16.5.100.20091127134147.64690")
    };
    // // Move series
    // DicomParam[] params = { new DicomParam(Tag.QueryRetrieveLevel, "SERIES"),
    // new DicomParam(Tag.SeriesInstanceUID, "2.25.62689877621998739235261278936628920157") };
    // // Move image
    // DicomParam[] params = { new DicomParam(Tag.QueryRetrieveLevel, "IMAGE"),
    // new DicomParam(Tag.SOPInstanceUID,
    // "1.2.840.113543.6.6.3.4.637463244096141531813342472862196132286") };
    DicomNode calling = new DicomNode("WEASIS-SCU");
    DicomNode called = new DicomNode("DCM4CHEE", "localhost", 11112);
    AdvancedParams options = new AdvancedParams();
    //     options.getQueryOptions().add(QueryOption.RELATIONAL); // Required for QueryRetrieveLevel
    // other than study
    DicomState state = CMove.process(options, calling, called, "WEASIS-SCU", progress, params);

    // Should never happen
    Assert.assertNotNull(state);

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
    // See server log at http://dicomserver.co.uk/logs/
    Assert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));
    // Assert.assertFalse("No DICOM RSP Object", state.getDicomRSP().isEmpty());
  }
}
