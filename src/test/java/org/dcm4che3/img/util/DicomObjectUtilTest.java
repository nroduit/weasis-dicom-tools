/*
 * Copyright (c) 2009-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.data.CIELab;
import org.junit.Test;

public class DicomObjectUtilTest {

  @Test
  public void testGetSequence() {
    assertTrue(DicomObjectUtil.getSequence(new Attributes(), 1).isEmpty());
    assertTrue(DicomObjectUtil.getSequence(null, 1).isEmpty());

    Attributes dataset1 = new Attributes();
    Sequence dicomElemSeq1 = dataset1.newSequence(Tag.GroupOfPatientsIdentificationSequence, 1);
    Attributes datasetSeq1 = new Attributes();
    datasetSeq1.setString(Tag.PatientID, VR.LO, "12345");
    Sequence dicomElemSeq12 = datasetSeq1.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
    Attributes datasetSeq12 = new Attributes();
    datasetSeq12.setString(Tag.UniversalEntityID, VR.UT, "UT");
    dicomElemSeq12.add(datasetSeq12);
    dicomElemSeq1.add(datasetSeq1);

    assertEquals(
        dicomElemSeq1,
        DicomObjectUtil.getSequence(dataset1, Tag.GroupOfPatientsIdentificationSequence));
    assertEquals(
        dicomElemSeq12,
        DicomObjectUtil.getSequence(datasetSeq1, Tag.IssuerOfPatientIDQualifiersSequence));
  }

  @Test
  public void testIsImageFrameApplicableToReferencedImageSequence() {
    ArrayList<Attributes> attributesList = new ArrayList<>();

    List<Integer> images = List.of(1, 2, 3);
    List<int[]> frames = List.of(new int[] {}, new int[] {1}, new int[] {1, 3});

    Attributes rfs = new Attributes(2);
    String seriesUID = "1.2.3.4.5.1.";

    for (Integer imageNb : images) {
      Attributes rfi = new Attributes(2);
      rfi.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.EnhancedMRImageStorage);
      rfi.setString(Tag.ReferencedSOPInstanceUID, VR.UI, seriesUID + imageNb);
      rfi.setInt(Tag.ReferencedFrameNumber, VR.IS, frames.get(imageNb - 1));
      attributesList.add(rfi);
    }

    assertFalse(
        DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
            new ArrayList<>(), Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 1, true));
    assertFalse(
        DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
            new ArrayList<>(), Tag.ReferencedFrameNumber, "", 3, true));

    // Accept all when no reference
    assertTrue(
        DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
            new ArrayList<Attributes>(), Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 1, false));
    assertTrue(
        DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
            null, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 3, false));

    assertTrue(
        DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
            attributesList.subList(0, 1), Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 1, true));
    assertTrue(
        DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
            attributesList.subList(1, 2), Tag.ReferencedFrameNumber, "1.2.3.4.5.1.2", 1, true));
    assertFalse(
        DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
            attributesList.subList(1, 2), Tag.ReferencedFrameNumber, "1.2.3.4.5.1.2", 3, true));
    assertTrue(
        DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
            attributesList.subList(2, 3), Tag.ReferencedFrameNumber, "1.2.3.4.5.1.3", 3, true));
    assertTrue(
        DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
            attributesList.subList(1, 3), Tag.ReferencedFrameNumber, "1.2.3.4.5.1.3", 3, true));
  }

  @Test
  public void testGetDicomDate() {
    assertNull(DicomObjectUtil.getDicomDate("2020-12-25"));
    assertNull(DicomObjectUtil.getDicomDate(""));

    LocalDate day = LocalDate.of(2020, Month.DECEMBER, 25);
    assertEquals(day, DicomObjectUtil.getDicomDate("20201225")); // Dicom compliant
    assertEquals(day, DicomObjectUtil.getDicomDate(" 20201225"));
    assertEquals(day, DicomObjectUtil.getDicomDate("20201225 "));
    assertEquals(day, DicomObjectUtil.getDicomDate("2020.12.25")); // Dicom compliant (old)
  }

  @Test
  public void testGetDicomTime() {
    assertNull(DicomObjectUtil.getDicomTime("2020-12-25"));
    assertNull(DicomObjectUtil.getDicomTime(" "));
    assertNull(DicomObjectUtil.getDicomTime("235959000151"));

    LocalTime time = LocalTime.of(23, 59, 59, 151_000);
    assertEquals(time, DicomObjectUtil.getDicomTime("235959.000151")); // Dicom compliant
    assertEquals(
        LocalTime.of(23, 59, 59, 21_000_000),
        DicomObjectUtil.getDicomTime("235959.021")); // Dicom compliant
    assertEquals(time, DicomObjectUtil.getDicomTime(" 235959.000151"));
    assertEquals(time, DicomObjectUtil.getDicomTime("235959.000151 "));
    assertEquals(time, DicomObjectUtil.getDicomTime("23:59:59.000151")); // Dicom compliant (old)
  }

  @Test
  public void testDateTime() {
    assertNull(DicomObjectUtil.dateTime(new Attributes(), 1, 1));
    assertNull(DicomObjectUtil.dateTime(null, 1, 1));

    LocalDate day = LocalDate.of(2020, Month.DECEMBER, 25);
    // Conversion to Date only supports milli an not micro seconds as defined in DICOM
    LocalTime time = LocalTime.of(23, 59, 59, 21_000_000);
    DateTimeUtils.dateTime(day, time);
    LocalDateTime dateTime = DateTimeUtils.dateTime(day, time);
    // Get system offset to match with Date (using system timezone)
    ZoneOffset currentOffsetForMyZone = ZoneOffset.systemDefault().getRules().getOffset(dateTime);
    Instant instant = DateTimeUtils.dateTime(day, time).toInstant(currentOffsetForMyZone);
    Date date = Date.from(instant);
    Attributes dcm = new Attributes(2);
    dcm.setDate(Tag.StudyDate, VR.DA, date);
    dcm.setDate(Tag.StudyTime, VR.TM, date);
    assertEquals(
        instant,
        DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime)
            .toInstant(currentOffsetForMyZone));

    assertEquals(
        DateTimeUtils.dateTime(day, null).toInstant(currentOffsetForMyZone),
        DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.AcquisitionTime)
            .toInstant(currentOffsetForMyZone));
  }

  @Test
  public void testGetShutterShape() {
    assertNull(DicomObjectUtil.getShutterShape(new Attributes()));
    Attributes dcm = new Attributes(2);
    dcm.setString(Tag.ShutterShape, VR.CS, "SHAPE");
    assertNull(DicomObjectUtil.getShutterShape(dcm));

    Rectangle2D rect = new Rectangle2D.Double(5, 10, 250, 50);
    Area area = new Area(rect);
    dcm.setString(Tag.ShutterShape, VR.CS, "RECTANGULAR", "CIRCULAR", "POLYGONAL");
    dcm.setInt(Tag.ShutterLeftVerticalEdge, VR.IS, 5);
    dcm.setInt(Tag.ShutterUpperHorizontalEdge, VR.IS, 10);
    dcm.setInt(Tag.ShutterRightVerticalEdge, VR.IS, 255);
    dcm.setInt(Tag.ShutterLowerHorizontalEdge, VR.IS, 60);

    Ellipse2D ellipse = new Ellipse2D.Double(35, 35, 80, 80);
    dcm.setInt(Tag.CenterOfCircularShutter, VR.IS, 75, 75);
    dcm.setInt(Tag.RadiusOfCircularShutter, VR.IS, 40);
    area.intersect(new Area(ellipse));

    Polygon polygon = new Polygon();
    polygon.addPoint(5, 5);
    polygon.addPoint(150, 3);
    polygon.addPoint(3, 40);
    // VerticesOfThePolygonalShutter order is y1, x1, y2, x2...
    dcm.setInt(Tag.VerticesOfThePolygonalShutter, VR.IS, 5, 5, 3, 150, 40, 3);
    area.intersect(new Area(polygon));

    assertTrue(area.equals(DicomObjectUtil.getShutterShape(dcm)));
  }

  @Test
  public void testGetShutterColor() {
    assertEquals(Color.BLACK, DicomObjectUtil.getShutterColor(new Attributes()));

    Color c = Color.MAGENTA;
    Attributes dcm = new Attributes(2);
    dcm.setInt(Tag.ShutterPresentationValue, VR.US, null);
    assertEquals(Color.BLACK, DicomObjectUtil.getShutterColor(dcm));
    dcm.setInt(Tag.ShutterPresentationValue, VR.US, 0xFFFF);
    assertEquals(Color.WHITE, DicomObjectUtil.getShutterColor(dcm));

    dcm.setInt(Tag.ShutterPresentationColorCIELabValue, VR.US, CIELab.rgbToDicomLab(c));
    assertEquals(c, DicomObjectUtil.getShutterColor(dcm));
  }

  @Test
  public void testGetRGBColor() {
    // Test P-Values
    assertEquals(Color.WHITE, DicomObjectUtil.getRGBColor(0xFFFF, null));
    assertEquals(Color.BLACK, DicomObjectUtil.getRGBColor(0x0000, null));

    assertEquals(Color.ORANGE, DicomObjectUtil.getRGBColor(0, new int[] {255, 200, 0}));
    assertEquals(Color.WHITE, DicomObjectUtil.getRGBColor(1, new int[] {256, 256, 256, 0}));
  }
}
