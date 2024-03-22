/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrDicomObjectTest {

  @Test
  @DisplayName("Verify constructor throws exception when SOPClassUID is not PR")
  void checkBuildPrDicomObject() {
    LocalDateTime dateTime = LocalDateTime.now();
    Instant instant = dateTime.atZone(ZoneOffset.systemDefault()).toInstant();
    Date date = Date.from(instant);
    Attributes dcm = new Attributes();
    dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
    assertThrows(IllegalStateException.class, () -> new PrDicomObject(dcm));

    dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
    dcm.setDate(Tag.PresentationCreationDate, VR.DA, date);
    dcm.setDate(Tag.PresentationCreationTime, VR.TM, date);
    PrDicomObject prDicomObject = new PrDicomObject(dcm);

    assertEquals(
        dateTime.truncatedTo(ChronoUnit.SECONDS),
        prDicomObject.getPresentationCreationDateTime().truncatedTo(ChronoUnit.SECONDS));
  }

  @Test
  @DisplayName("Verify getter and setter methods")
  void checkGetterAndSetter() {
    String seriesInstanceUID = "1.2.276.0.7230010.3.200.12.1";
    String sopInstanceUID = "1.2.276.0.7230010.3.200.12.1.1";
    int frame = 1;

    Attributes pr = new Attributes();
    pr.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
    pr.setDouble(Tag.RescaleSlope, VR.DS, 2.0);
    pr.setDouble(Tag.RescaleIntercept, VR.DS, 1.0);
    pr.setString(Tag.RescaleType, VR.LO, "CT");

    Attributes dcm = new Attributes();
    dcm.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sopInstanceUID);
    Sequence seq = pr.newSequence(Tag.ReferencedSeriesSequence, 1);
    seq.add(dcm);

    Sequence seriesSeq = pr.newSequence(Tag.ReferencedSeriesSequence, 1);
    Attributes rfs = new Attributes(1);
    rfs.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);

    Sequence imageSeq = rfs.newSequence(Tag.ReferencedImageSequence, 1);
    Attributes rfi = new Attributes(1);
    rfi.setString(Tag.ReferencedSOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
    rfi.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sopInstanceUID);
    rfi.setInt(Tag.ReferencedFrameNumber, VR.IS, frame);
    rfi.setInt(Tag.ReferencedSegmentNumber, VR.IS, 5);
    imageSeq.add(rfi);
    seriesSeq.add(rfs);

    PrDicomObject prDicomObject = new PrDicomObject(pr);

    assertNotNull(prDicomObject.getDicomObject());
    assertEquals(Color.BLACK, prDicomObject.getShutterColor());
    assertEquals("PR 0", prDicomObject.getPrContentLabel());

    assertTrue(prDicomObject.getPrLut().isEmpty());
    assertTrue(prDicomObject.getVoiLUT().isEmpty());
    assertTrue(prDicomObject.getOverlays().isEmpty());
    assertTrue(prDicomObject.getPrLutExplanation().isEmpty());
    assertTrue(prDicomObject.getShutterOverlays().isEmpty());
    assertFalse(prDicomObject.hasOverlay());
    assertTrue(prDicomObject.getGraphicAnnotationSequence().isEmpty());
    assertTrue(prDicomObject.getGraphicLayerSequence().isEmpty());
    assertNull(prDicomObject.getShutterShape());
    assertTrue(prDicomObject.getPrLutShapeMode().isEmpty());
    assertTrue(prDicomObject.getModalityLutModule().getRescaleSlope().isPresent());
    assertTrue(prDicomObject.getModalityLutModule().getRescaleIntercept().isPresent());

    assertTrue(prDicomObject.isImageFrameApplicable(seriesInstanceUID, sopInstanceUID, frame));
    assertFalse(prDicomObject.isImageFrameApplicable("seriesInstanceUID", sopInstanceUID, frame));
    assertFalse(prDicomObject.isImageFrameApplicable(null, null, frame));
    assertFalse(prDicomObject.isImageFrameApplicable(seriesInstanceUID, "sopInstanceUID", frame));
    assertFalse(prDicomObject.isImageFrameApplicable(seriesInstanceUID, null, frame));
    assertFalse(prDicomObject.isImageFrameApplicable(seriesInstanceUID, sopInstanceUID, 3));

    assertTrue(prDicomObject.isSegmentationSegmentApplicable(seriesInstanceUID, sopInstanceUID, 5));
    assertFalse(
        prDicomObject.isSegmentationSegmentApplicable("seriesInstanceUID", sopInstanceUID, 5));
    assertFalse(prDicomObject.isSegmentationSegmentApplicable(null, null, 5));
    assertFalse(
        prDicomObject.isSegmentationSegmentApplicable(seriesInstanceUID, "sopInstanceUID", 5));
    assertFalse(prDicomObject.isSegmentationSegmentApplicable(seriesInstanceUID, null, 5));
    assertFalse(
        prDicomObject.isSegmentationSegmentApplicable(seriesInstanceUID, sopInstanceUID, 3));
  }
}
