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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.data.CIELab;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DicomObjectUtilTest {

  @Test
  @DisplayName("Get Dicom Sequence")
  void testGetSequence() {
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
  @DisplayName("Check image frame in Referenced Image Sequence")
  void testIsImageFrameApplicableToReferencedImageSequence() {
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
            new ArrayList<>(), Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 1, false));
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
  @DisplayName("Memoize supplier returns same value on multiple invocations")
  void shouldReturnSameValueOnMultipleInvocations() throws Exception {
    SupplierEx<Integer, Exception> original = () -> new Random().nextInt();
    SupplierEx<Integer, Exception> memoized = DicomObjectUtil.memoize(original);

    int firstInvocationResult = memoized.get();
    for (int i = 0; i < 10; i++) {
      assertEquals(firstInvocationResult, memoized.get());
    }
  }

  @Test
  @DisplayName("Memoize supplier does not invoke original supplier after first invocation")
  void shouldNotInvokeOriginalSupplierAfterFirstInvocation() throws Exception {
    AtomicInteger invocationCount = new AtomicInteger(0);
    SupplierEx<Integer, Exception> original =
        () -> {
          invocationCount.incrementAndGet();
          return new Random().nextInt();
        };
    SupplierEx<Integer, Exception> memoized = DicomObjectUtil.memoize(original);

    memoized.get();
    memoized.get();
    assertEquals(1, invocationCount.get());
  }

  @Test
  @DisplayName("Check Shutter Shape")
  void testGetShutterShape() {
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
    polygon.addPoint(5, 0);
    polygon.addPoint(0, 0);
    polygon.addPoint(0, 70);
    polygon.addPoint(5, 70);

    // VerticesOfThePolygonalShutter order is y1, x1, y2, x2...
    dcm.setInt(Tag.VerticesOfThePolygonalShutter, VR.IS, 0, 5, 0, 0, 70, 0, 70, 5);
    area.intersect(new Area(polygon));

    assertTrue(area.equals(DicomObjectUtil.getShutterShape(dcm)));
  }

  @Test
  @DisplayName("Check Shutter Color")
  void testGetShutterColor() {
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
  @DisplayName("Check RGB Color")
  void testGetRGBColor() {
    // Test P-Values
    assertEquals(Color.WHITE, DicomObjectUtil.getRGBColor(0xFFFF, null));
    assertEquals(Color.BLACK, DicomObjectUtil.getRGBColor(0x0000, null));

    assertEquals(Color.ORANGE, DicomObjectUtil.getRGBColor(0, new int[] {255, 200, 0}));
    assertEquals(Color.WHITE, DicomObjectUtil.getRGBColor(1, new int[] {256, 256, 256, 256}));

    // Color transparency
    assertEquals(
        new Color(255, 200, 0, 128), DicomObjectUtil.getRGBColor(0, new int[] {255, 200, 0, 128}));
  }

  @Test
  @DisplayName("Get DateTime")
  void testGetDateTime() {
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
}
