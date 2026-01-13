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

import java.awt.Color;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.data.PrDicomObject.PresentationGroup;
import org.dcm4che3.img.data.PrDicomObject.PresentationStateType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PrDicomObjectTest {

  // Test data constants
  private static final String VALID_GRAYSCALE_UID = "1.2.840.10008.5.1.4.1.1.11.1";
  private static final String INVALID_SOP_CLASS_UID =
      "1.2.840.10008.5.1.4.1.1.2"; // CT Image Storage
  private static final String TEST_SERIES_UID = "1.2.276.0.7230010.3.200.12.1";
  private static final String TEST_SOP_UID = "1.2.276.0.7230010.3.200.12.1.1";
  private static final String TEST_CONTENT_LABEL = "TEST_PR";

  @Nested
  class Constructor_and_validation_tests {

    @Test
    void should_throw_exception_when_sop_class_uid_is_not_presentation_state() {
      var dcm = createBasicAttributes(INVALID_SOP_CLASS_UID);

      var exception = assertThrows(IllegalStateException.class, () -> new PrDicomObject(dcm));

      assertTrue(
          exception.getMessage().contains("does not match any supported DICOM Presentation State"));
    }

    @Test
    void should_create_object_with_valid_grayscale_presentation_state() {
      var dateTime = OffsetDateTime.now();
      var dcm = createAttributesWithDateTime(VALID_GRAYSCALE_UID, dateTime);

      var prObject = new PrDicomObject(dcm);

      assertNotNull(prObject);
      assertEquals(PresentationStateType.GRAYSCALE_SOFTCOPY, prObject.getPresentationStateType());
      assertEquals(PresentationGroup.BASIC_GRAYSCALE, prObject.getPresentationGroup());
      assertEquals(
          dateTime.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS), // Convert to LocalDateTime
          prObject.getPresentationCreationDateTime().truncatedTo(ChronoUnit.SECONDS));
    }

    @ParameterizedTest
    @EnumSource(PresentationStateType.class)
    void should_create_object_for_all_presentation_state_types(PresentationStateType type) {
      var dcm = createBasicAttributes(type.getUid());

      var prObject = new PrDicomObject(dcm);

      assertAll(
          "Should create valid presentation object",
          () -> assertNotNull(prObject),
          () -> assertEquals(type, prObject.getPresentationStateType()),
          () -> assertEquals(type.getGroup(), prObject.getPresentationGroup()),
          () -> assertEquals(type.getDescription(), prObject.getSopClassDescription()));
    }

    @Test
    void should_throw_exception_for_null_attributes() {
      assertThrows(NullPointerException.class, () -> new PrDicomObject(null));
    }
  }

  @Nested
  class Presentation_state_type_tests {

    @Test
    void should_find_presentation_state_type_by_uid() {
      var type = PresentationStateType.fromUid(VALID_GRAYSCALE_UID);

      assertTrue(type.isPresent());
      assertEquals(PresentationStateType.GRAYSCALE_SOFTCOPY, type.get());
      assertEquals(VALID_GRAYSCALE_UID, type.get().getUid());
    }

    @Test
    void should_return_empty_for_unknown_uid() {
      var type = PresentationStateType.fromUid("unknown.uid");
      assertFalse(type.isPresent());
    }

    @Test
    void should_group_presentation_states_by_functional_group() {
      var basicGrayscale = PresentationStateType.getByGroup(PresentationGroup.BASIC_GRAYSCALE);
      var colorBased = PresentationStateType.getByGroup(PresentationGroup.COLOR_BASED);
      var volumetric = PresentationStateType.getByGroup(PresentationGroup.VOLUMETRIC);

      assertAll(
          "Should correctly group presentation state types",
          () -> assertEquals(1, basicGrayscale.size()),
          () -> assertTrue(basicGrayscale.contains(PresentationStateType.GRAYSCALE_SOFTCOPY)),
          () -> assertEquals(3, colorBased.size()),
          () -> assertTrue(colorBased.contains(PresentationStateType.COLOR_SOFTCOPY)),
          () -> assertTrue(colorBased.contains(PresentationStateType.PSEUDO_COLOR_SOFTCOPY)),
          () -> assertTrue(colorBased.contains(PresentationStateType.BLENDING_SOFTCOPY)),
          () -> assertEquals(5, volumetric.size()),
          () ->
              assertTrue(
                  volumetric.contains(PresentationStateType.GRAYSCALE_PLANAR_MPR_VOLUMETRIC)),
          () -> assertTrue(volumetric.contains(PresentationStateType.VOLUME_RENDERING_VOLUMETRIC)));
    }

    @Test
    void should_validate_all_sop_class_uids_are_in_range_11_1_to_11_12() {
      var expectedPrefix = "1.2.840.10008.5.1.4.1.1.11.";

      for (var type : PresentationStateType.values()) {
        var uid = type.getUid();
        assertTrue(uid.startsWith(expectedPrefix), "UID should start with expected prefix: " + uid);

        var suffix = uid.substring(expectedPrefix.length());
        var number = Integer.parseInt(suffix);
        assertTrue(
            number >= 1 && number <= 12,
            "UID %s should be in range 11.1-11.12, got number: %d".formatted(uid, number));
      }
    }
  }

  @Nested
  class Capability_tests {

    @ParameterizedTest
    @MethodSource("volumetricRenderingUids")
    void should_support_volumetric_rendering_for_volumetric_states(String uid) {
      var dcm = createBasicAttributes(uid);
      var prObject = new PrDicomObject(dcm);

      assertAll(
          "Should support volumetric rendering",
          () -> assertTrue(prObject.supportsVolumetricRendering()),
          () -> assertEquals(PresentationGroup.VOLUMETRIC, prObject.getPresentationGroup()));
    }

    @Test
    void should_support_advanced_blending_for_advanced_blending_state() {
      var dcm = createBasicAttributes("1.2.840.10008.5.1.4.1.1.11.8");
      var prObject = new PrDicomObject(dcm);

      assertAll(
          "Should support advanced blending",
          () -> assertTrue(prObject.supportsAdvancedBlending()),
          () -> assertEquals(PresentationGroup.ADVANCED_BLENDING, prObject.getPresentationGroup()));
    }

    @Test
    void should_not_support_special_capabilities_for_basic_grayscale() {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
      var prObject = new PrDicomObject(dcm);

      assertAll(
          "Basic grayscale should not support advanced features",
          () -> assertFalse(prObject.supportsVolumetricRendering()),
          () -> assertFalse(prObject.supportsAdvancedBlending()));
    }

    static Stream<Arguments> volumetricRenderingUids() {
      return Stream.of(
          Arguments.of("1.2.840.10008.5.1.4.1.1.11.6"), // GRAYSCALE_PLANAR_MPR_VOLUMETRIC
          Arguments.of("1.2.840.10008.5.1.4.1.1.11.7"), // COMPOSITING_PLANAR_MPR_VOLUMETRIC
          Arguments.of("1.2.840.10008.5.1.4.1.1.11.9"), // VOLUME_RENDERING_VOLUMETRIC
          Arguments.of("1.2.840.10008.5.1.4.1.1.11.10"), // SEGMENTED_VOLUME_RENDERING_VOLUMETRIC
          Arguments.of("1.2.840.10008.5.1.4.1.1.11.11") // MULTIPLE_VOLUME_RENDERING_VOLUMETRIC
          );
    }
  }

  @Nested
  class Lut_and_presentation_tests {

    @Test
    void should_handle_presentation_lut_sequence() {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
      addPresentationLutSequence(dcm, "Test LUT");

      var prObject = new PrDicomObject(dcm);

      assertAll(
          "Should handle presentation LUT sequence correctly",
          () -> assertTrue(prObject.getPrLutExplanation().isPresent()),
          () -> assertEquals("Test LUT", prObject.getPrLutExplanation().get()),
          () -> assertTrue(prObject.getPrLutShapeMode().isPresent()),
          () -> assertEquals("IDENTITY", prObject.getPrLutShapeMode().get()));
    }

    @Test
    void should_handle_presentation_lut_shape() {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
      dcm.setString(Tag.PresentationLUTShape, VR.CS, "INVERSE");

      var prObject = new PrDicomObject(dcm);

      assertAll(
          "Should handle presentation LUT shape correctly",
          () -> assertTrue(prObject.getPrLutShapeMode().isPresent()),
          () -> assertEquals("INVERSE", prObject.getPrLutShapeMode().get()),
          () -> assertFalse(prObject.getPrLut().isPresent()));
    }

    @Test
    void should_handle_voi_lut_sequence() {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
      addVoiLutSequence(dcm, 128.0, 256.0);

      var prObject = new PrDicomObject(dcm);

      assertTrue(prObject.getVoiLUT().isPresent(), "VOI LUT should be present");
    }

    @Test
    void should_handle_empty_lut_configuration() {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);

      var prObject = new PrDicomObject(dcm);

      assertAll(
          "Should handle empty LUT configuration",
          () -> assertFalse(prObject.getPrLut().isPresent()),
          () -> assertFalse(prObject.getPrLutExplanation().isPresent()),
          () -> assertFalse(prObject.getPrLutShapeMode().isPresent()),
          () -> assertFalse(prObject.getVoiLUT().isPresent()));
    }
  }

  @Nested
  class Applicability_tests {

    @Test
    void should_correctly_determine_image_frame_applicability() {
      var prObject = createPrObjectWithReferences();

      assertAll(
          "Should correctly determine image frame applicability",
          // Positive cases
          () -> assertTrue(prObject.isImageFrameApplicable(TEST_SERIES_UID, TEST_SOP_UID, 1)),

          // Negative cases
          () -> assertFalse(prObject.isImageFrameApplicable("wrong.series", TEST_SOP_UID, 1)),
          () -> assertFalse(prObject.isImageFrameApplicable(null, TEST_SOP_UID, 1)),
          () -> assertFalse(prObject.isImageFrameApplicable(TEST_SERIES_UID, "wrong.sop", 1)),
          () -> assertFalse(prObject.isImageFrameApplicable(TEST_SERIES_UID, null, 1)),
          () -> assertFalse(prObject.isImageFrameApplicable(TEST_SERIES_UID, TEST_SOP_UID, 999)));
    }

    @Test
    void should_correctly_determine_segmentation_segment_applicability() {
      var prObject = createPrObjectWithReferences();

      assertAll(
          "Should correctly determine segmentation segment applicability",
          // Positive cases
          () ->
              assertTrue(
                  prObject.isSegmentationSegmentApplicable(TEST_SERIES_UID, TEST_SOP_UID, 5)),

          // Negative cases
          () ->
              assertFalse(
                  prObject.isSegmentationSegmentApplicable("wrong.series", TEST_SOP_UID, 5)),
          () -> assertFalse(prObject.isSegmentationSegmentApplicable(null, TEST_SOP_UID, 5)),
          () ->
              assertFalse(
                  prObject.isSegmentationSegmentApplicable(TEST_SERIES_UID, "wrong.sop", 5)),
          () -> assertFalse(prObject.isSegmentationSegmentApplicable(TEST_SERIES_UID, null, 5)),
          () ->
              assertFalse(
                  prObject.isSegmentationSegmentApplicable(TEST_SERIES_UID, TEST_SOP_UID, 999)));
    }

    @Test
    void should_handle_empty_reference_sequences() {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
      var prObject = new PrDicomObject(dcm);

      assertAll(
          "Should handle empty reference sequences",
          () -> assertFalse(prObject.isImageFrameApplicable("any.series", "any.sop", 1)),
          () -> assertFalse(prObject.isSegmentationSegmentApplicable("any.series", "any.sop", 1)));
    }
  }

  @Nested
  class Getter_methods_tests {

    @Test
    void should_return_correct_basic_properties() {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
      dcm.setString(Tag.ContentLabel, VR.CS, TEST_CONTENT_LABEL);
      dcm.setInt(Tag.InstanceNumber, VR.IS, 42);

      var prObject = new PrDicomObject(dcm);

      assertAll(
          "Should return correct basic properties",
          () -> assertEquals(dcm, prObject.getDicomObject()),
          () -> assertEquals(TEST_CONTENT_LABEL, prObject.getPrContentLabel()),
          () -> assertEquals(Color.BLACK, prObject.getShutterColor()),
          () -> assertNull(prObject.getShutterShape()),
          () -> assertFalse(prObject.hasOverlay()));
    }

    @Test
    void should_return_default_content_label_when_not_specified() {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
      dcm.setInt(Tag.InstanceNumber, VR.IS, 123);

      var prObject = new PrDicomObject(dcm);

      assertEquals("PR 123", prObject.getPrContentLabel());
    }

    @Test
    void should_return_empty_collections_for_missing_sequences() {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
      var prObject = new PrDicomObject(dcm);

      assertAll(
          "Should return empty collections for missing sequences",
          () -> assertTrue(prObject.getOverlays().isEmpty()),
          () -> assertTrue(prObject.getShutterOverlays().isEmpty()),
          () -> assertTrue(prObject.getReferencedSeriesSequence().isEmpty()),
          () -> assertTrue(prObject.getGraphicAnnotationSequence().isEmpty()),
          () -> assertTrue(prObject.getGraphicLayerSequence().isEmpty()),
          () -> assertFalse(prObject.getVoiLUT().isPresent()),
          () -> assertFalse(prObject.getPrLut().isPresent()),
          () -> assertFalse(prObject.getPrLutExplanation().isPresent()));
    }

    @Test
    void should_return_modality_lut_module_with_rescale_parameters() {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
      addModalityLutParameters(dcm, 2.0, 1.0, "CT");

      var prObject = new PrDicomObject(dcm);

      var modalityLut = prObject.getModalityLutModule();
      assertAll(
          "Should return modality LUT module with correct parameters",
          () -> assertNotNull(modalityLut),
          () -> assertTrue(modalityLut.getRescaleSlope().isPresent()),
          () -> assertTrue(modalityLut.getRescaleIntercept().isPresent()),
          () -> assertEquals(2.0, modalityLut.getRescaleSlope().getAsDouble(), 0.001),
          () -> assertEquals(1.0, modalityLut.getRescaleIntercept().getAsDouble(), 0.001));
    }
  }

  @Nested
  class Presentation_group_tests {

    @Test
    void should_have_correct_group_descriptions() {
      var expectedDescriptions =
          new Object[][] {
            {
              PresentationGroup.BASIC_GRAYSCALE,
              "Basic grayscale presentation with standard windowing"
            },
            {PresentationGroup.COLOR_BASED, "Color and pseudo-color presentation states"},
            {PresentationGroup.SPECIALIZED, "Specialized modality-specific presentation states"},
            {PresentationGroup.VOLUMETRIC, "3D volumetric rendering presentation states"},
            {PresentationGroup.ADVANCED_BLENDING, "Advanced blending and compositing"},
            {PresentationGroup.ADVANCED_LUT, "Advanced LUT manipulation and variable modality LUT"}
          };

      for (var expected : expectedDescriptions) {
        var group = (PresentationGroup) expected[0];
        var expectedDescription = (String) expected[1];
        assertEquals(
            expectedDescription,
            group.getDescription(),
            "Group %s should have correct description".formatted(group.name()));
      }
    }

    @Test
    void should_categorize_all_presentation_state_types() {
      var expectedGroupSizes =
          new Object[][] {
            {PresentationGroup.BASIC_GRAYSCALE, 1},
            {PresentationGroup.COLOR_BASED, 3},
            {PresentationGroup.SPECIALIZED, 1},
            {PresentationGroup.VOLUMETRIC, 5},
            {PresentationGroup.ADVANCED_BLENDING, 1},
            {PresentationGroup.ADVANCED_LUT, 1}
          };

      var totalTypes = 0;
      for (var expected : expectedGroupSizes) {
        var group = (PresentationGroup) expected[0];
        var expectedSize = (Integer) expected[1];
        var types = PresentationStateType.getByGroup(group);

        assertEquals(
            expectedSize,
            types.size(),
            "Group %s should have %d types".formatted(group.name(), expectedSize));
        totalTypes += types.size();
      }

      assertEquals(
          PresentationStateType.values().length,
          totalTypes,
          "Total types should match all enum values");
    }
  }

  @Nested
  class Edge_cases_tests {

    @Test
    void should_handle_missing_presentation_date_time() {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
      var prObject = new PrDicomObject(dcm);

      assertDoesNotThrow(
          prObject::getPresentationCreationDateTime,
          "Should not throw exception for missing date/time");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  "})
    void should_handle_empty_or_whitespace_uids(String uid) {
      var dcm = createBasicAttributes(uid);

      var exception = assertThrows(IllegalStateException.class, () -> new PrDicomObject(dcm));
      assertTrue(
          exception.getMessage().contains("does not match any supported DICOM Presentation State"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void should_handle_applicability_checks_with_empty_strings(String emptyValue) {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
      var prObject = new PrDicomObject(dcm);

      assertAll(
          "Should handle empty string parameters gracefully",
          () -> assertFalse(prObject.isImageFrameApplicable(emptyValue, TEST_SOP_UID, 1)),
          () -> assertFalse(prObject.isSegmentationSegmentApplicable(emptyValue, TEST_SOP_UID, 1)));
    }

    @Test
    void should_handle_null_parameters_gracefully() {
      var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
      var prObject = new PrDicomObject(dcm);

      assertAll(
          "Should handle null parameters gracefully",
          () -> assertFalse(prObject.isImageFrameApplicable(null, TEST_SOP_UID, 1)),
          () -> assertFalse(prObject.isSegmentationSegmentApplicable(null, TEST_SOP_UID, 1)),
          () -> assertFalse(prObject.isImageFrameApplicable(TEST_SERIES_UID, null, 1)),
          () -> assertFalse(prObject.isSegmentationSegmentApplicable(TEST_SERIES_UID, null, 1)));
    }
  }

  // === Helper Methods ===

  private static Attributes createBasicAttributes(String sopClassUid) {
    var dcm = new Attributes();
    dcm.setString(Tag.SOPClassUID, VR.UI, sopClassUid);
    return dcm;
  }

  private static Attributes createAttributesWithDateTime(
      String sopClassUid, OffsetDateTime dateTime) {
    var dcm = createBasicAttributes(sopClassUid);
    var date = new Date(dateTime.toInstant().toEpochMilli());
    dcm.setDate(Tag.PresentationCreationDate, VR.DA, date);
    dcm.setDate(Tag.PresentationCreationTime, VR.TM, date);
    return dcm;
  }

  private static void addPresentationLutSequence(Attributes dcm, String explanation) {
    var lutSeq = dcm.newSequence(Tag.PresentationLUTSequence, 1);
    var lutItem = new Attributes();
    lutItem.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);
    lutItem.setBytes(Tag.LUTData, VR.OW, new byte[512]);
    lutSeq.add(lutItem);
    dcm.setString(Tag.LUTExplanation, VR.LO, explanation);
  }

  private static void addVoiLutSequence(Attributes dcm, double windowCenter, double windowWidth) {
    var voiSeq = dcm.newSequence(Tag.SoftcopyVOILUTSequence, 1);
    var voiItem = new Attributes();
    voiItem.setDouble(Tag.WindowCenter, VR.DS, windowCenter);
    voiItem.setDouble(Tag.WindowWidth, VR.DS, windowWidth);
    voiSeq.add(voiItem);
  }

  private static void addModalityLutParameters(
      Attributes dcm, double slope, double intercept, String type) {
    dcm.setDouble(Tag.RescaleSlope, VR.DS, slope);
    dcm.setDouble(Tag.RescaleIntercept, VR.DS, intercept);
    dcm.setString(Tag.RescaleType, VR.LO, type);
  }

  private static PrDicomObject createPrObjectWithReferences() {
    var dcm = createBasicAttributes(VALID_GRAYSCALE_UID);
    addModalityLutParameters(dcm, 2.0, 1.0, "CT");
    addReferencedSeriesSequence(dcm, TEST_SERIES_UID, TEST_SOP_UID, 1, 5);
    return new PrDicomObject(dcm);
  }

  private static void addReferencedSeriesSequence(
      Attributes dcm, String seriesUID, String sopUID, int frame, int segment) {
    var seriesSeq = dcm.newSequence(Tag.ReferencedSeriesSequence, 1);
    var rfs = new Attributes();
    rfs.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);

    var imageSeq = rfs.newSequence(Tag.ReferencedImageSequence, 1);
    var rfi = new Attributes();
    rfi.setString(Tag.ReferencedSOPClassUID, VR.UI, VALID_GRAYSCALE_UID);
    rfi.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sopUID);
    rfi.setInt(Tag.ReferencedFrameNumber, VR.IS, frame);
    rfi.setInt(Tag.ReferencedSegmentNumber, VR.IS, segment);

    imageSeq.add(rfi);
    seriesSeq.add(rfs);
  }
}
