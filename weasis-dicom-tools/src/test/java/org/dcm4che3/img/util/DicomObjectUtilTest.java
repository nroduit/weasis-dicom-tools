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
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.data.CIELab;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomObjectUtilTest {

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class DICOM_Sequence_Operations {

    @Test
    void should_return_empty_list_for_null_attributes() {
      var result = DicomObjectUtil.getSequence(null, Tag.GroupOfPatientsIdentificationSequence);

      assertTrue(result.isEmpty());
    }

    @Test
    void should_return_empty_list_for_non_existent_sequence() {
      var attrs = new Attributes();
      var result = DicomObjectUtil.getSequence(attrs, Tag.GroupOfPatientsIdentificationSequence);

      assertTrue(result.isEmpty());
    }

    @Test
    void should_return_sequence_when_present() {
      var dataset = createPatientDataset();

      var result = DicomObjectUtil.getSequence(dataset, Tag.GroupOfPatientsIdentificationSequence);

      assertEquals(2, result.size());
      assertEquals("PAT001", result.get(0).getString(Tag.PatientID));
      assertEquals("PAT002", result.get(1).getString(Tag.PatientID));
    }

    @Test
    void should_handle_nested_sequences() {
      var dataset = createNestedSequenceDataset();

      var outerResult =
          DicomObjectUtil.getSequence(dataset, Tag.GroupOfPatientsIdentificationSequence);
      assertEquals(1, outerResult.size());

      var innerResult =
          DicomObjectUtil.getSequence(outerResult.get(0), Tag.IssuerOfPatientIDQualifiersSequence);
      assertEquals(1, innerResult.size());
      assertEquals("TEST_UID", innerResult.get(0).getString(Tag.UniversalEntityID));
    }

    @Test
    void should_return_immutable_empty_list() {
      var result = DicomObjectUtil.getSequence(null, Tag.GroupOfPatientsIdentificationSequence);

      assertThrows(UnsupportedOperationException.class, () -> result.set(0, new Attributes()));
    }

    private Attributes createPatientDataset() {
      var dataset = new Attributes();
      var sequence = dataset.newSequence(Tag.GroupOfPatientsIdentificationSequence, 2);

      var item1 = new Attributes();
      item1.setString(Tag.PatientID, VR.LO, "PAT001");
      item1.setString(Tag.PatientName, VR.PN, "Doe^John");
      sequence.add(item1);

      var item2 = new Attributes();
      item2.setString(Tag.PatientID, VR.LO, "PAT002");
      item2.setString(Tag.PatientName, VR.PN, "Smith^Jane");
      sequence.add(item2);

      return dataset;
    }

    private Attributes createNestedSequenceDataset() {
      var dataset = new Attributes();
      var outerSequence = dataset.newSequence(Tag.GroupOfPatientsIdentificationSequence, 1);

      var outerItem = new Attributes();
      outerItem.setString(Tag.PatientID, VR.LO, "12345");

      var innerSequence = outerItem.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
      var innerItem = new Attributes();
      innerItem.setString(Tag.UniversalEntityID, VR.UT, "TEST_UID");
      innerSequence.add(innerItem);

      outerSequence.add(outerItem);
      return dataset;
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Referenced_Image_Sequence_Validation {

    @Test
    void should_return_true_when_sequence_is_not_required_and_empty() {
      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              Collections.emptyList(), Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 1, false));

      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              null, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 1, false));
    }

    @Test
    void should_return_false_when_sequence_is_required_and_empty() {
      assertFalse(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              Collections.emptyList(), Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 1, true));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void should_return_false_for_invalid_sop_instance_uid(String sopInstanceUID) {
      var sequence = createReferencedImageSequence();

      assertFalse(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, sopInstanceUID, 1, true));
    }

    @Test
    void should_handle_frames_when_no_specific_frames_are_referenced() {
      var sequence = createReferencedImageSequence();

      // Image 1 has no specific frames, so all frames should be applicable
      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 1, true));
      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.1", 5, true));
    }

    @ParameterizedTest
    @MethodSource("provideSpecificFrameTestCases")
    void should_validate_specific_frame_numbers(
        String sopInstanceUID, int frame, boolean expected) {
      var sequence = createReferencedImageSequence();

      boolean result =
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, sopInstanceUID, frame, true);

      assertEquals(expected, result);
    }

    @Test
    void should_return_false_for_non_existent_sop_instance_uid() {
      var sequence = createReferencedImageSequence();

      assertFalse(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              sequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.1.999", 1, true));
    }

    private Stream<Arguments> provideSpecificFrameTestCases() {
      return Stream.of(
          // Image 2: Only frame 1 is referenced
          Arguments.of("1.2.3.4.5.1.2", 1, true),
          Arguments.of("1.2.3.4.5.1.2", 2, false),
          Arguments.of("1.2.3.4.5.1.2", 3, false),

          // Image 3: Frames 1 and 3 are referenced
          Arguments.of("1.2.3.4.5.1.3", 1, true),
          Arguments.of("1.2.3.4.5.1.3", 2, false),
          Arguments.of("1.2.3.4.5.1.3", 3, true),
          Arguments.of("1.2.3.4.5.1.3", 4, false));
    }

    private List<Attributes> createReferencedImageSequence() {
      var sequence = new ArrayList<Attributes>();
      var baseUID = "1.2.3.4.5.1.";

      // Image 1: No specific frames (all frames applicable)
      var ref1 = new Attributes();
      ref1.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.EnhancedMRImageStorage);
      ref1.setString(Tag.ReferencedSOPInstanceUID, VR.UI, baseUID + "1");
      sequence.add(ref1);

      // Image 2: Only frame 1
      var ref2 = new Attributes();
      ref2.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.EnhancedMRImageStorage);
      ref2.setString(Tag.ReferencedSOPInstanceUID, VR.UI, baseUID + "2");
      ref2.setInt(Tag.ReferencedFrameNumber, VR.IS, 1);
      sequence.add(ref2);

      // Image 3: Frames 1 and 3
      var ref3 = new Attributes();
      ref3.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.EnhancedMRImageStorage);
      ref3.setString(Tag.ReferencedSOPInstanceUID, VR.UI, baseUID + "3");
      ref3.setInt(Tag.ReferencedFrameNumber, VR.IS, 1, 3);
      sequence.add(ref3);

      return sequence;
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Date_and_Time_Parsing {

    @ParameterizedTest
    @ValueSource(strings = {"20230615", "19991231", "20000229", "20231201"})
    void should_parse_valid_dicom_dates(String dateString) {
      var result = DicomObjectUtil.getDicomDate(dateString);

      assertNotNull(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "20230232", "20001301", "2023", "20230635"})
    void should_return_null_for_invalid_dicom_dates(String dateString) {
      assertNull(DicomObjectUtil.getDicomDate(dateString));
    }

    @ParameterizedTest
    @MethodSource("provideValidTimeStrings")
    void should_parse_valid_dicom_times(String timeString, LocalTime expected) {
      var result = DicomObjectUtil.getDicomTime(timeString);

      assertEquals(expected, result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "250000", "123060", "12;30;00", "2500", "12:30:60"})
    void should_return_null_for_invalid_dicom_times(String timeString) {
      assertNull(DicomObjectUtil.getDicomTime(timeString));
    }

    @Test
    void should_combine_date_and_time_correctly() {
      var dcm = createStudyAttributes("20230615", "143000.000");

      var result = DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime);

      assertNotNull(result);
      assertEquals(LocalDate.of(2023, 6, 15), result.toLocalDate());
      assertEquals(LocalTime.of(14, 30, 0), result.toLocalTime());
    }

    @Test
    void should_return_date_at_start_of_day_when_time_is_missing() {
      var dcm = new Attributes();
      dcm.setString(Tag.StudyDate, VR.DA, "20230615");

      var result = DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime);

      assertNotNull(result);
      assertEquals(LocalDate.of(2023, 6, 15), result.toLocalDate());
      assertEquals(LocalTime.MIDNIGHT, result.toLocalTime());
    }

    @Test
    void should_return_null_when_date_is_missing() {
      var dcm = new Attributes();
      dcm.setString(Tag.StudyTime, VR.TM, "143000.000");

      assertNull(DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime));
    }

    @Test
    void should_return_null_for_null_attributes() {
      assertNull(DicomObjectUtil.dateTime(null, Tag.StudyDate, Tag.StudyTime));
    }

    @ParameterizedTest
    @MethodSource("provideDateTimeCombinations")
    void should_handle_various_date_time_combinations(
        String date, String time, LocalDateTime expected) {
      var dcm = createStudyAttributes(date, time);

      var result = DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime);

      assertEquals(expected, result);
    }

    private Stream<Arguments> provideValidTimeStrings() {
      return Stream.of(
          Arguments.of("120000.000", LocalTime.of(12, 0, 0)),
          Arguments.of("235959.999", LocalTime.of(23, 59, 59, 999_000_000)),
          Arguments.of("000000", LocalTime.MIDNIGHT),
          Arguments.of("120000", LocalTime.of(12, 0, 0)),
          Arguments.of("1230", LocalTime.of(12, 30, 0)));
    }

    private Stream<Arguments> provideDateTimeCombinations() {
      return Stream.of(
          Arguments.of("20230615", "143000", LocalDateTime.of(2023, 6, 15, 14, 30)),
          Arguments.of("20230615", null, LocalDateTime.of(2023, 6, 15, 0, 0)),
          Arguments.of(
              "20231201", "235959.999", LocalDateTime.of(2023, 12, 1, 23, 59, 59, 999_000_000)));
    }

    private Attributes createStudyAttributes(String date, String time) {
      var dcm = new Attributes();
      if (date != null) {
        dcm.setString(Tag.StudyDate, VR.DA, date);
      }
      if (time != null) {
        dcm.setString(Tag.StudyTime, VR.TM, time);
      }
      return dcm;
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Shutter_Shape_Tests {

    @Test
    void should_return_null_for_null_attributes() {
      assertNull(DicomObjectUtil.getShutterShape(null));
    }

    @Test
    void should_return_null_for_empty_shutter_shape() {
      assertNull(DicomObjectUtil.getShutterShape(new Attributes()));
    }

    @Test
    void should_return_null_for_unknown_shutter_shape() {
      var dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "UNKNOWN");

      assertNull(DicomObjectUtil.getShutterShape(dcm));
    }

    @Test
    void should_create_rectangular_shutter() {
      var dcm = createRectangularShutterAttributes(10, 20, 100, 80);

      var result = DicomObjectUtil.getShutterShape(dcm);

      assertNotNull(result);
      var expectedRect = new Rectangle2D.Double(10, 20, 90, 60);
      assertTrue(new Area(expectedRect).equals(result));
    }

    @Test
    void should_create_circular_shutter() {
      var dcm = createCircularShutterAttributes(50, 60, 25);

      var result = DicomObjectUtil.getShutterShape(dcm);

      assertNotNull(result);
      // Expected ellipse: center at (60, 50) with radius 25
      var expectedEllipse = new Ellipse2D.Double(35, 25, 50, 50);
      assertTrue(new Area(expectedEllipse).equals(result));
    }

    @Test
    void should_create_polygonal_shutter() {
      var dcm = createPolygonalShutterAttributes(10, 20, 30, 20, 30, 40, 10, 40);

      var result = DicomObjectUtil.getShutterShape(dcm);

      assertNotNull(result);

      var expectedPolygon = new Polygon();
      expectedPolygon.addPoint(20, 10); // col1, row1
      expectedPolygon.addPoint(20, 30); // col2, row2
      expectedPolygon.addPoint(40, 30); // col3, row3
      expectedPolygon.addPoint(40, 10); // col4, row4

      assertTrue(new Area(expectedPolygon).equals(result));
    }

    @Test
    void should_combine_multiple_shutter_shapes() {
      var dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "RECTANGULAR", "CIRCULAR");

      // Rectangular shutter
      dcm.setInt(Tag.ShutterLeftVerticalEdge, VR.IS, 0);
      dcm.setInt(Tag.ShutterUpperHorizontalEdge, VR.IS, 0);
      dcm.setInt(Tag.ShutterRightVerticalEdge, VR.IS, 100);
      dcm.setInt(Tag.ShutterLowerHorizontalEdge, VR.IS, 100);

      // Circular shutter inside rectangle
      dcm.setInt(Tag.CenterOfCircularShutter, VR.IS, 50, 50);
      dcm.setInt(Tag.RadiusOfCircularShutter, VR.IS, 30);

      var result = DicomObjectUtil.getShutterShape(dcm);

      assertNotNull(result);

      // Result should be intersection of rectangle and circle
      var rect = new Rectangle2D.Double(0, 0, 100, 100);
      var circle = new Ellipse2D.Double(20, 20, 60, 60);
      var expected = new Area(rect);
      expected.intersect(new Area(circle));

      assertTrue(expected.equals(result));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidPolygonData")
    void should_handle_invalid_polygon_data(int[] points) {
      var dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "POLYGONAL");
      dcm.setInt(Tag.VerticesOfThePolygonalShutter, VR.IS, points);

      assertNull(DicomObjectUtil.getShutterShape(dcm));
    }

    @Test
    void should_handle_empty_rectangular_shutter() {
      var dcm = createRectangularShutterAttributes(50, 50, 50, 50);

      assertNull(DicomObjectUtil.getShutterShape(dcm));
    }

    @Test
    void should_handle_invalid_circular_shutter_data() {
      var dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "CIRCULAR");

      // Missing center
      dcm.setInt(Tag.RadiusOfCircularShutter, VR.IS, 25);
      assertNull(DicomObjectUtil.getShutterShape(dcm));

      // Invalid center
      dcm.setInt(Tag.CenterOfCircularShutter, VR.IS, 50);
      assertNull(DicomObjectUtil.getShutterShape(dcm));

      // Valid center but invalid radius
      dcm.setInt(Tag.CenterOfCircularShutter, VR.IS, 50, 60);
      dcm.setInt(Tag.RadiusOfCircularShutter, VR.IS, 0);
      assertNull(DicomObjectUtil.getShutterShape(dcm));
    }

    private Stream<Arguments> provideInvalidPolygonData() {
      return Stream.of(
          Arguments.of((Object) new int[] {10, 20}), // Too few points
          Arguments.of((Object) new int[] {10, 20, 10, 30}), // Only 2 points
          Arguments.of((Object) new int[] {10, 20, 10, 30, 10, 40}) // Degenerate polygon (line)
          );
    }

    private Attributes createRectangularShutterAttributes(
        int left, int top, int right, int bottom) {
      var dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "RECTANGULAR");
      dcm.setInt(Tag.ShutterLeftVerticalEdge, VR.IS, left);
      dcm.setInt(Tag.ShutterUpperHorizontalEdge, VR.IS, top);
      dcm.setInt(Tag.ShutterRightVerticalEdge, VR.IS, right);
      dcm.setInt(Tag.ShutterLowerHorizontalEdge, VR.IS, bottom);
      return dcm;
    }

    private Attributes createCircularShutterAttributes(int row, int col, int radius) {
      var dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "CIRCULAR");
      dcm.setInt(Tag.CenterOfCircularShutter, VR.IS, row, col);
      dcm.setInt(Tag.RadiusOfCircularShutter, VR.IS, radius);
      return dcm;
    }

    private Attributes createPolygonalShutterAttributes(int... points) {
      var dcm = new Attributes();
      dcm.setString(Tag.ShutterShape, VR.CS, "POLYGONAL");
      dcm.setInt(Tag.VerticesOfThePolygonalShutter, VR.IS, points);
      return dcm;
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Color_Conversion_Tests {

    @Test
    void should_return_black_for_empty_attributes() {
      assertEquals(Color.BLACK, DicomObjectUtil.getShutterColor(new Attributes()));
    }

    @ParameterizedTest
    @CsvSource({
      "0x0000, 0, 0, 0",
      "0x8080, 128, 128, 128",
      "0xFFFF, 255, 255, 255",
      "0x4040, 64, 64, 64"
    })
    void should_convert_p_values_to_grayscale_colors(
        String pValueHex, int expectedR, int expectedG, int expectedB) {
      int pValue = Integer.decode(pValueHex);
      var result = DicomObjectUtil.getRGBColor(pValue, null);

      assertEquals(new Color(expectedR, expectedG, expectedB, 255), result);
    }

    @ParameterizedTest
    @MethodSource("provideRgbColorTestCases")
    void should_handle_rgb_color_arrays(int[] rgbArray, Color expected) {
      var result = DicomObjectUtil.getRGBColor(0, rgbArray);

      assertEquals(expected, result);
    }

    @Test
    void should_handle_rgba_color_arrays_with_alpha() {
      var semiTransparentRed = DicomObjectUtil.getRGBColor(0, new int[] {255, 0, 0, 128});
      assertEquals(new Color(255, 0, 0, 128), semiTransparentRed);

      var transparentBlue = DicomObjectUtil.getRGBColor(0, new int[] {0, 0, 255, 0});
      assertEquals(new Color(0, 0, 255, 0), transparentBlue);
    }

    @Test
    void should_clamp_rgb_values_to_valid_range() {
      var clampedColor = DicomObjectUtil.getRGBColor(0, new int[] {300, -50, 256, 300});
      assertEquals(new Color(255, 0, 255, 255), clampedColor);
    }

    @Test
    void should_handle_insufficient_rgb_array_elements() {
      // Array with only 2 elements should use P-Value
      var result = DicomObjectUtil.getRGBColor(0x8080, new int[] {255, 128});
      assertEquals(new Color(128, 128, 128, 255), result);

      // Empty array should use P-Value
      var result2 = DicomObjectUtil.getRGBColor(0x4040, new int[] {});
      assertEquals(new Color(64, 64, 64, 255), result2);
    }

    @Test
    void should_extract_shutter_color_from_cie_lab_values() {
      var magenta = Color.MAGENTA;
      var dcm = new Attributes();
      dcm.setInt(Tag.ShutterPresentationColorCIELabValue, VR.US, CIELab.rgbToDicomLab(magenta));

      var result = DicomObjectUtil.getShutterColor(dcm);
      assertEquals(magenta, result);
    }

    @Test
    void should_prioritize_cie_lab_over_p_value() {
      var red = Color.RED;
      var dcm = new Attributes();
      dcm.setInt(Tag.ShutterPresentationValue, VR.US, 0xFFFF); // Would be white
      dcm.setInt(Tag.ShutterPresentationColorCIELabValue, VR.US, CIELab.rgbToDicomLab(red));

      var result = DicomObjectUtil.getShutterColor(dcm);
      assertEquals(red, result);
    }

    private Stream<Arguments> provideRgbColorTestCases() {
      return Stream.of(
          Arguments.of(new int[] {255, 165, 0}, new Color(255, 165, 0, 255)),
          Arguments.of(new int[] {0, 0, 255}, new Color(0, 0, 255, 255)),
          Arguments.of(new int[] {255, 255, 255}, Color.WHITE),
          Arguments.of(new int[] {0, 0, 0}, Color.BLACK),
          Arguments.of(new int[] {128, 128, 128}, new Color(128, 128, 128, 255)));
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Integration_Tests {

    @Test
    void should_handle_complete_dicom_dataset_with_all_features() {
      var dcm = createComprehensiveDicomDataset();

      // Test all operations
      var dateTime = DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime);
      assertNotNull(dateTime);
      assertEquals(LocalDate.of(2023, 6, 15), dateTime.toLocalDate());

      var refSequence = DicomObjectUtil.getSequence(dcm, Tag.ReferencedImageSequence);
      assertEquals(1, refSequence.size());

      assertTrue(
          DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              refSequence, Tag.ReferencedFrameNumber, "1.2.3.4.5.6.7.8.10", 2, true));

      var shutterShape = DicomObjectUtil.getShutterShape(dcm);
      assertNotNull(shutterShape);

      var shutterColor = DicomObjectUtil.getShutterColor(dcm);
      assertEquals(new Color(128, 128, 128, 255), shutterColor);
    }

    @RepeatedTest(5)
    void should_maintain_consistent_behavior_across_multiple_runs() {
      var dcm = new Attributes();
      dcm.setString(Tag.StudyDate, VR.DA, "20230101");
      dcm.setString(Tag.StudyTime, VR.TM, "120000");

      var result = DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime);
      assertNotNull(result);
      assertEquals(LocalDateTime.of(2023, 1, 1, 12, 0, 0), result);
    }

    @Test
    void should_handle_edge_cases_gracefully() {
      // Test with minimal valid data
      var dcm = new Attributes();
      dcm.setString(Tag.StudyDate, VR.DA, "20230101");

      var dateTime = DicomObjectUtil.dateTime(dcm, Tag.StudyDate, Tag.StudyTime);
      assertEquals(LocalDateTime.of(2023, 1, 1, 0, 0), dateTime);

      // Test with empty sequences
      var emptySequence = DicomObjectUtil.getSequence(dcm, Tag.ReferencedImageSequence);
      assertTrue(emptySequence.isEmpty());

      // Test with minimal shutter data
      dcm.setString(Tag.ShutterShape, VR.CS, "RECTANGULAR");
      assertNull(DicomObjectUtil.getShutterShape(dcm)); // Missing required coordinates
    }

    private Attributes createComprehensiveDicomDataset() {
      var dcm = new Attributes();

      // Add study information
      dcm.setString(Tag.StudyDate, VR.DA, "20230615");
      dcm.setString(Tag.StudyTime, VR.TM, "143000.000");
      dcm.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9");

      // Add sequence
      var sequence = dcm.newSequence(Tag.ReferencedImageSequence, 1);
      var refImage = new Attributes();
      refImage.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.10");
      refImage.setInt(Tag.ReferencedFrameNumber, VR.IS, 1, 2, 3);
      sequence.add(refImage);

      // Add shutter information
      dcm.setString(Tag.ShutterShape, VR.CS, "RECTANGULAR");
      dcm.setInt(Tag.ShutterLeftVerticalEdge, VR.IS, 10);
      dcm.setInt(Tag.ShutterUpperHorizontalEdge, VR.IS, 10);
      dcm.setInt(Tag.ShutterRightVerticalEdge, VR.IS, 90);
      dcm.setInt(Tag.ShutterLowerHorizontalEdge, VR.IS, 90);
      dcm.setInt(Tag.ShutterPresentationValue, VR.US, 0x8080);

      return dcm;
    }
  }
}
