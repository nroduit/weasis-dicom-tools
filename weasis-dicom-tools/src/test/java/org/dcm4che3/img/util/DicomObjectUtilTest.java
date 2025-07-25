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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.data.CIELab;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DicomObjectUtilTest {

  @Nested
  @DisplayName("DICOM Sequence Operations")
  class SequenceTests {

    @Test
    @DisplayName("Should return empty list for null attributes")
    void shouldReturnEmptyListForNullAttributes() {
      assertTrue(
          DicomObjectUtil.getSequence(null, Tag.GroupOfPatientsIdentificationSequence).isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for non-existent sequence")
    void shouldReturnEmptyListForNonExistentSequence() {
      Attributes attrs = new Attributes();
      assertTrue(
          DicomObjectUtil.getSequence(attrs, Tag.GroupOfPatientsIdentificationSequence).isEmpty());
    }

    @Test
    @DisplayName("Should return sequence when present")
    void shouldReturnSequenceWhenPresent() {
      Attributes dataset = new Attributes();
      Sequence sequence = dataset.newSequence(Tag.GroupOfPatientsIdentificationSequence, 2);

      // Add first item
      Attributes item1 = new Attributes();
      item1.setString(Tag.PatientID, VR.LO, "PAT001");
      item1.setString(Tag.PatientName, VR.PN, "Doe^John");
      sequence.add(item1);

      // Add second item
      Attributes item2 = new Attributes();
      item2.setString(Tag.PatientID, VR.LO, "PAT002");
      item2.setString(Tag.PatientName, VR.PN, "Smith^Jane");
      sequence.add(item2);

      List<Attributes> result =
          DicomObjectUtil.getSequence(dataset, Tag.GroupOfPatientsIdentificationSequence);

      assertEquals(2, result.size());
      assertEquals("PAT001", result.get(0).getString(Tag.PatientID));
      assertEquals("PAT002", result.get(1).getString(Tag.PatientID));
    }

    @Test
    @DisplayName("Should handle nested sequences")
    void shouldHandleNestedSequences() {
      Attributes dataset = new Attributes();
      Sequence outerSequence = dataset.newSequence(Tag.GroupOfPatientsIdentificationSequence, 1);

      Attributes outerItem = new Attributes();
      outerItem.setString(Tag.PatientID, VR.LO, "12345");

      Sequence innerSequence = outerItem.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
      Attributes innerItem = new Attributes();
      innerItem.setString(Tag.UniversalEntityID, VR.UT, "TEST_UID");
      innerSequence.add(innerItem);

      outerSequence.add(outerItem);

      List<Attributes> outerResult =
          DicomObjectUtil.getSequence(dataset, Tag.GroupOfPatientsIdentificationSequence);
      assertEquals(1, outerResult.size());

      List<Attributes> innerResult =
          DicomObjectUtil.getSequence(outerResult.get(0), Tag.IssuerOfPatientIDQualifiersSequence);
      assertEquals(1, innerResult.size());
      assertEquals("TEST_UID", innerResult.get(0).getString(Tag.UniversalEntityID));
    }
  }

  @Nested
  @DisplayName("Referenced Image Sequence Validation")
  class ReferencedImageSequenceTests {

    private List<Attributes> createReferencedImageSequence() {
      List<Attributes> sequence = new ArrayList<>();
      String baseUID = "1.2.3.4.5.1.";

      // Image 1: No specific frames (all frames applicable)
      Attributes ref1 = new Attributes();
      ref1.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.EnhancedMRImageStorage);
      ref1.setString(Tag.ReferencedSOPInstanceUID, VR.UI, baseUID + "1");
      ref1.setInt(Tag.ReferencedFrameNumber, VR.IS, new int[0]);
      sequence.add(ref1);

      // Image 2: Only frame 1
      Attributes ref2 = new Attributes();
      ref2.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.EnhancedMRImageStorage);
      ref2.setString(Tag.ReferencedSOPInstanceUID, VR.UI, baseUID + "2");
      ref2.setInt(Tag.ReferencedFrameNumber, VR.IS, new int[] {1});
      sequence.add(ref2);

      // Image 3: Frames 1 and 3
      Attributes ref3 = new Attributes();
      ref3.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.EnhancedMRImageStorage);
      ref3.setString(Tag.ReferencedSOPInstanceUID, VR.UI, baseUID + "3");
      ref3.setInt(Tag.ReferencedFrameNumber, VR.IS, new int[] {1, 3});
      sequence.add(ref3);

      return sequence;
    }

    @Test
    @DisplayName("Should return true when sequence is not required and empty")
    void shouldReturnTrueWhenNotRequiredAndEmpty() {
      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              Collections.emptyList(), Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 1, false));

      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              null, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 1, false));
    }

    @Test
    @DisplayName("Should return false when sequence is required and empty")
    void shouldReturnFalseWhenRequiredAndEmpty() {
      assertFalse(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              Collections.emptyList(), Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 1, true));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should return false for invalid SOP Instance UID")
    void shouldReturnFalseForInvalidSopInstanceUID(String sopInstanceUID) {
      List<Attributes> sequence = createReferencedImageSequence();

      assertFalse(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, sopInstanceUID, 1, true));
    }

    @Test
    @DisplayName("Should handle frames when no specific frames are referenced")
    void shouldHandleAllFramesWhenNoneSpecified() {
      List<Attributes> sequence = createReferencedImageSequence();

      // Image 1 has no specific frames, so all frames should be applicable
      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 1, true));
      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 5, true));
    }

    @Test
    @DisplayName("Should validate specific frame numbers")
    void shouldValidateSpecificFrameNumbers() {
      List<Attributes> sequence = createReferencedImageSequence();

      // Image 2: Only frame 1 is referenced
      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.2", 1, true));
      assertFalse(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.2", 2, true));

      // Image 3: Frames 1 and 3 are referenced
      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.3", 1, true));
      assertFalse(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.3", 2, true));
      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.3", 3, true));
    }

    @Test
    @DisplayName("Should return false for non-existent SOP Instance UID")
    void shouldReturnFalseForNonExistentSopInstanceUID() {
      List<Attributes> sequence = createReferencedImageSequence();

      assertFalse(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.999", 1, true));
    }
  }

  @Nested
  @DisplayName("Memoization Tests")
  class MemoizationTests {

    @Test
    @DisplayName("Should return same value on multiple invocations")
    void shouldReturnSameValueOnMultipleInvocations() throws Exception {
      AtomicInteger counter = new AtomicInteger(0);
      SupplierEx<Integer, Exception> original = counter::incrementAndGet;
      SupplierEx<Integer, Exception> memoized = DicomObjectUtil.memoize(original);

      int firstResult = memoized.get();
      assertEquals(1, firstResult);

      // Subsequent calls should return the same value
      for (int i = 0; i < 10; i++) {
        assertEquals(firstResult, memoized.get());
      }

      // Counter should only be incremented once
      assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("Should be thread-safe")
    void shouldBeThreadSafe() throws Exception {
      AtomicInteger invocationCount = new AtomicInteger(0);
      AtomicInteger resultValue = new AtomicInteger(0);

      SupplierEx<Integer, Exception> original =
          () -> {
            invocationCount.incrementAndGet();
            // Simulate some computation time
            Thread.sleep(10);
            return resultValue.incrementAndGet();
          };

      SupplierEx<Integer, Exception> memoized = DicomObjectUtil.memoize(original);

      ExecutorService executor = Executors.newFixedThreadPool(10);
      List<CompletableFuture<Integer>> futures =
          IntStream.range(0, 100)
              .mapToObj(
                  i ->
                      CompletableFuture.supplyAsync(
                          () -> {
                            try {
                              return memoized.get();
                            } catch (Exception e) {
                              throw new RuntimeException(e);
                            }
                          },
                          executor))
              .toList();

      // All futures should complete with the same value
      Integer expectedValue = futures.get(0).get();
      for (CompletableFuture<Integer> future : futures) {
        assertEquals(expectedValue, future.get());
      }

      // Original supplier should only be called once
      assertEquals(1, invocationCount.get());

      executor.shutdown();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should propagate exceptions")
    void shouldPropagateExceptions() {
      SupplierEx<String, RuntimeException> failingSupplier =
          () -> {
            throw new RuntimeException("Test exception");
          };

      SupplierEx<String, RuntimeException> memoized = DicomObjectUtil.memoize(failingSupplier);

      assertThrows(RuntimeException.class, memoized::get);
      // Second call should also throw the same exception
      assertThrows(RuntimeException.class, memoized::get);
    }

    @Test
    @DisplayName("Should handle null values")
    void shouldHandleNullValues() throws Exception {
      SupplierEx<String, Exception> nullSupplier = () -> null;
      SupplierEx<String, Exception> memoized = DicomObjectUtil.memoize(nullSupplier);

      assertNull(memoized.get());
      assertNull(memoized.get()); // Should return cached null
    }
  }

  @Nested
  @DisplayName("Date and Time Parsing")
  class DateTimeTests {

    @ParameterizedTest
    @ValueSource(strings = {"20230615", "19991231", "20000229"})
    @DisplayName("Should parse valid DICOM dates")
    void shouldParseValidDicomDates(String dateString) {
      LocalDate result = DicomObjectUtil.getDicomDate(dateString);
      assertNotNull(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "20230232", "20001301", "2023"})
    @DisplayName("Should return null for invalid DICOM dates")
    void shouldReturnNullForInvalidDicomDates(String dateString) {
      assertNull(DicomObjectUtil.getDicomDate(dateString));
    }

    @ParameterizedTest
    @ValueSource(strings = {"120000.000", "235959.999", "000000", "120000"})
    @DisplayName("Should parse valid DICOM times")
    void shouldParseValidDicomTimes(String timeString) {
      LocalTime result = DicomObjectUtil.getDicomTime(timeString);
      assertNotNull(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "250000", "123060", "12;30;00"})
    @DisplayName("Should return null for invalid DICOM times")
    void shouldReturnNullForInvalidDicomTimes(String timeString) {
      assertNull(DicomObjectUtil.getDicomTime(timeString));
    }

    @Test
    @DisplayName("Should combine date and time correctly")
    void shouldCombineDateAndTimeCorrectly() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.StudyDate, VR.DA, "20230615");
      dcm.setString(Tag.StudyTime, VR.TM, "143000.000");

      LocalDateTime result = DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime);

      assertNotNull(result);
      assertEquals(LocalDate.of(2023, 6, 15), result.toLocalDate());
      assertEquals(LocalTime.of(14, 30, 0), result.toLocalTime());
    }

    @Test
    @DisplayName("Should return date at start of day when time is missing")
    void shouldReturnDateAtStartOfDayWhenTimeMissing() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.StudyDate, VR.DA, "20230615");

      LocalDateTime result = DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime);

      assertNotNull(result);
      assertEquals(LocalDate.of(2023, 6, 15), result.toLocalDate());
      assertEquals(LocalTime.MIDNIGHT, result.toLocalTime());
    }

    @Test
    @DisplayName("Should return null when date is missing")
    void shouldReturnNullWhenDateMissing() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.StudyTime, VR.TM, "143000.000");

      assertNull(DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime));
    }

    @Test
    @DisplayName("Should return null for null attributes")
    void shouldReturnNullForNullAttributes() {
      assertNull(DicomObjectUtil.dateTime(null, Tag.StudyDate, Tag.StudyTime));
    }
  }

  @Nested
  @DisplayName("Shutter Shape Tests")
  class ShutterShapeTests {

    @Test
    @DisplayName("Should return null for null or empty shutter shape")
    void shouldReturnNullForNullOrEmptyShutterShape() {
      assertNull(DicomObjectUtil.getShutterShape(new Attributes()));

      Attributes dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "UNKNOWN");
      assertNull(DicomObjectUtil.getShutterShape(dcm));
    }

    @Test
    @DisplayName("Should create rectangular shutter")
    void shouldCreateRectangularShutter() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "RECTANGULAR");
      dcm.setInt(Tag.ShutterLeftVerticalEdge, VR.IS, 10);
      dcm.setInt(Tag.ShutterUpperHorizontalEdge, VR.IS, 20);
      dcm.setInt(Tag.ShutterRightVerticalEdge, VR.IS, 100);
      dcm.setInt(Tag.ShutterLowerHorizontalEdge, VR.IS, 80);

      Area result = DicomObjectUtil.getShutterShape(dcm);

      assertNotNull(result);
      Rectangle2D expectedRect = new Rectangle2D.Double(10, 20, 90, 60);
      assertTrue(new Area(expectedRect).equals(result));
    }

    @Test
    @DisplayName("Should create circular shutter")
    void shouldCreateCircularShutter() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "CIRCULAR");
      dcm.setInt(Tag.CenterOfCircularShutter, VR.IS, 50, 60); // row, column
      dcm.setInt(Tag.RadiusOfCircularShutter, VR.IS, 25);

      Area result = DicomObjectUtil.getShutterShape(dcm);

      assertNotNull(result);
      // Expected ellipse: center at (60, 50) with radius 25
      Ellipse2D expectedEllipse = new Ellipse2D.Double(35, 25, 50, 50);
      assertTrue(new Area(expectedEllipse).equals(result));
    }

    @Test
    @DisplayName("Should create polygonal shutter")
    void shouldCreatePolygonalShutter() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "POLYGONAL");
      // Points in DICOM format: row1, col1, row2, col2, row3, col3
      dcm.setInt(Tag.VerticesOfThePolygonalShutter, VR.IS, 10, 20, 30, 20, 30, 40, 10, 40);

      Area result = DicomObjectUtil.getShutterShape(dcm);

      assertNotNull(result);

      Polygon expectedPolygon = new Polygon();
      expectedPolygon.addPoint(20, 10); // col1, row1
      expectedPolygon.addPoint(20, 30); // col2, row2
      expectedPolygon.addPoint(40, 30); // col3, row3
      expectedPolygon.addPoint(40, 10); // col4, row4

      assertTrue(new Area(expectedPolygon).equals(result));
    }

    @Test
    @DisplayName("Should combine multiple shutter shapes")
    void shouldCombineMultipleShutterShapes() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "RECTANGULAR", "CIRCULAR");

      // Rectangular shutter
      dcm.setInt(Tag.ShutterLeftVerticalEdge, VR.IS, 0);
      dcm.setInt(Tag.ShutterUpperHorizontalEdge, VR.IS, 0);
      dcm.setInt(Tag.ShutterRightVerticalEdge, VR.IS, 100);
      dcm.setInt(Tag.ShutterLowerHorizontalEdge, VR.IS, 100);

      // Circular shutter inside rectangle
      dcm.setInt(Tag.CenterOfCircularShutter, VR.IS, 50, 50);
      dcm.setInt(Tag.RadiusOfCircularShutter, VR.IS, 30);

      Area result = DicomObjectUtil.getShutterShape(dcm);

      assertNotNull(result);

      // Result should be intersection of rectangle and circle
      Rectangle2D rect = new Rectangle2D.Double(0, 0, 100, 100);
      Ellipse2D circle = new Ellipse2D.Double(20, 20, 60, 60);
      Area expected = new Area(rect);
      expected.intersect(new Area(circle));

      assertTrue(expected.equals(result));
    }

    @Test
    @DisplayName("Should handle invalid polygon data")
    void shouldHandleInvalidPolygonData() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "POLYGONAL");

      // Too few points
      dcm.setInt(Tag.VerticesOfThePolygonalShutter, VR.IS, 10, 20);
      assertNull(DicomObjectUtil.getShutterShape(dcm));

      // Degenerate polygon (line)
      dcm.setInt(Tag.VerticesOfThePolygonalShutter, VR.IS, 10, 20, 10, 30, 10, 40);
      assertNull(DicomObjectUtil.getShutterShape(dcm));
    }

    @Test
    @DisplayName("Should handle empty rectangular shutter")
    void shouldHandleEmptyRectangularShutter() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "RECTANGULAR");
      dcm.setInt(Tag.ShutterLeftVerticalEdge, VR.IS, 50);
      dcm.setInt(Tag.ShutterUpperHorizontalEdge, VR.IS, 50);
      dcm.setInt(Tag.ShutterRightVerticalEdge, VR.IS, 50);
      dcm.setInt(Tag.ShutterLowerHorizontalEdge, VR.IS, 50);

      assertNull(DicomObjectUtil.getShutterShape(dcm));
    }
  }

  @Nested
  @DisplayName("Color Conversion Tests")
  class ColorTests {

    @Test
    @DisplayName("Should return black for empty attributes")
    void shouldReturnBlackForEmptyAttributes() {
      assertEquals(Color.BLACK, DicomObjectUtil.getShutterColor(new Attributes()));
    }

    @ParameterizedTest
    @CsvSource({"0x0000, 0, 0, 0", "0x8080, 128, 128, 128", "0xFFFF, 255, 255, 255"})
    @DisplayName("Should convert P-Values to grayscale colors")
    void shouldConvertPValuesToGrayscaleColors(
        String pValueHex, int expectedR, int expectedG, int expectedB) {
      int pValue = Integer.decode(pValueHex);
      Color result = DicomObjectUtil.getRGBColor(pValue, null);

      assertEquals(new Color(expectedR, expectedG, expectedB, 255), result);
    }

    @Test
    @DisplayName("Should handle RGB color arrays")
    void shouldHandleRgbColorArrays() {
      Color orange = DicomObjectUtil.getRGBColor(0, new int[] {255, 165, 0});
      assertEquals(new Color(255, 165, 0, 255), orange);

      Color blue = DicomObjectUtil.getRGBColor(0, new int[] {0, 0, 255});
      assertEquals(new Color(0, 0, 255, 255), blue);
    }

    @Test
    @DisplayName("Should handle RGBA color arrays with alpha")
    void shouldHandleRgbaColorArraysWithAlpha() {
      Color semiTransparentRed = DicomObjectUtil.getRGBColor(0, new int[] {255, 0, 0, 128});
      assertEquals(new Color(255, 0, 0, 128), semiTransparentRed);
    }

    @Test
    @DisplayName("Should clamp RGB values to valid range")
    void shouldClampRgbValuesToValidRange() {
      Color clampedColor = DicomObjectUtil.getRGBColor(0, new int[] {300, -50, 256, 300});
      assertEquals(new Color(255, 0, 255, 255), clampedColor);
    }

    @Test
    @DisplayName("Should handle insufficient RGB array elements")
    void shouldHandleInsufficientRgbArrayElements() {
      // Array with only 2 elements should use P-Value
      Color result = DicomObjectUtil.getRGBColor(0x8080, new int[] {255, 128});
      assertEquals(new Color(128, 128, 128, 255), result);
    }

    @Test
    @DisplayName("Should extract shutter color from CIE Lab values")
    void shouldExtractShutterColorFromCieLabValues() {
      Color magenta = Color.MAGENTA;
      Attributes dcm = new Attributes();
      dcm.setInt(Tag.ShutterPresentationColorCIELabValue, VR.US, CIELab.rgbToDicomLab(magenta));

      Color result = DicomObjectUtil.getShutterColor(dcm);
      assertEquals(magenta, result);
    }

    @Test
    @DisplayName("Should prioritize CIE Lab over P-Value")
    void shouldPrioritizeCieLabOverPValue() {
      Color red = Color.RED;
      Attributes dcm = new Attributes();
      dcm.setInt(Tag.ShutterPresentationValue, VR.US, 0xFFFF); // Would be white
      dcm.setInt(Tag.ShutterPresentationColorCIELabValue, VR.US, CIELab.rgbToDicomLab(red));

      Color result = DicomObjectUtil.getShutterColor(dcm);
      assertEquals(red, result);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should handle complete DICOM dataset with all features")
    void shouldHandleCompleteDicomDataset() {
      // Create a comprehensive DICOM dataset
      Attributes dcm = new Attributes();

      // Add study information
      dcm.setString(Tag.StudyDate, VR.DA, "20230615");
      dcm.setString(Tag.StudyTime, VR.TM, "143000.000");
      dcm.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9");

      // Add sequence
      Sequence sequence = dcm.newSequence(Tag.ReferencedImageSequence, 1);
      Attributes refImage = new Attributes();
      refImage.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.10");
      refImage.setInt(Tag.ReferencedFrameNumber, VR.IS, new int[] {1, 2, 3});
      sequence.add(refImage);

      // Add shutter information
      dcm.setString(Tag.ShutterShape, VR.CS, "RECTANGULAR");
      dcm.setInt(Tag.ShutterLeftVerticalEdge, VR.IS, 10);
      dcm.setInt(Tag.ShutterUpperHorizontalEdge, VR.IS, 10);
      dcm.setInt(Tag.ShutterRightVerticalEdge, VR.IS, 90);
      dcm.setInt(Tag.ShutterLowerHorizontalEdge, VR.IS, 90);
      dcm.setInt(Tag.ShutterPresentationValue, VR.US, 0x8080);

      // Test all operations
      LocalDateTime dateTime = DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime);
      assertNotNull(dateTime);
      assertEquals(LocalDate.of(2023, 6, 15), dateTime.toLocalDate());

      List<Attributes> refSequence = DicomObjectUtil.getSequence(dcm, Tag.ReferencedImageSequence);
      assertEquals(1, refSequence.size());

      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              refSequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.6.7.8.10", 2, true));

      Area shutterShape = DicomObjectUtil.getShutterShape(dcm);
      assertNotNull(shutterShape);

      Color shutterColor = DicomObjectUtil.getShutterColor(dcm);
      assertEquals(new Color(128, 128, 128, 255), shutterColor);
    }

    @RepeatedTest(5)
    @DisplayName("Should maintain consistent behavior across multiple runs")
    void shouldMaintainConsistentBehaviorAcrossMultipleRuns() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.StudyDate, VR.DA, "20230101");
      dcm.setString(Tag.StudyTime, VR.TM, "120000");

      LocalDateTime result = DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime);
      assertNotNull(result);
      assertEquals(LocalDateTime.of(2023, 1, 1, 12, 0, 0), result);
    }
  }
}
