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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.data.PrDicomObject.PresentationGroup;
import org.dcm4che3.img.data.PrDicomObject.PresentationStateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class PrDicomObjectTest {

  @Nested
  @DisplayName("Constructor and Validation Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Should throw exception when SOPClassUID is not a presentation state")
    void shouldThrowExceptionForInvalidSopClassUid() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2"); // CT Image Storage

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> new PrDicomObject(dcm));

      assertTrue(
          exception.getMessage().contains("does not match any supported DICOM Presentation State"));
    }

    @Test
    @DisplayName("Should create object successfully with valid grayscale presentation state")
    void shouldCreateObjectWithValidGrayscalePresentationState() {
      LocalDateTime dateTime = LocalDateTime.now();
      Instant instant = dateTime.atZone(ZoneOffset.systemDefault()).toInstant();
      Date date = Date.from(instant);

      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
      dcm.setDate(Tag.PresentationCreationDate, VR.DA, date);
      dcm.setDate(Tag.PresentationCreationTime, VR.TM, date);

      PrDicomObject prDicomObject = new PrDicomObject(dcm);

      assertNotNull(prDicomObject);
      assertEquals(
          PresentationStateType.GRAYSCALE_SOFTCOPY, prDicomObject.getPresentationStateType());
      assertEquals(PresentationGroup.BASIC_GRAYSCALE, prDicomObject.getPresentationGroup());
      assertEquals(
          dateTime.truncatedTo(ChronoUnit.SECONDS),
          prDicomObject.getPresentationCreationDateTime().truncatedTo(ChronoUnit.SECONDS));
    }

    @ParameterizedTest
    @EnumSource(PresentationStateType.class)
    @DisplayName("Should create object successfully for all presentation state types")
    void shouldCreateObjectForAllPresentationStateTypes(PresentationStateType type) {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, type.getUid());

      PrDicomObject prDicomObject = new PrDicomObject(dcm);

      assertNotNull(prDicomObject);
      assertEquals(type, prDicomObject.getPresentationStateType());
      assertEquals(type.getGroup(), prDicomObject.getPresentationGroup());
      assertEquals(type.getDescription(), prDicomObject.getSopClassDescription());
    }

    @Test
    @DisplayName("Should handle null DICOM attributes gracefully")
    void shouldThrowExceptionForNullAttributes() {
      assertThrows(NullPointerException.class, () -> new PrDicomObject(null));
    }
  }

  @Nested
  @DisplayName("Presentation State Type Tests")
  class PresentationStateTypeTests {

    @Test
    @DisplayName("Should find presentation state type by UID")
    void shouldFindPresentationStateTypeByUid() {
      String grayscaleUid = "1.2.840.10008.5.1.4.1.1.11.1";
      Optional<PresentationStateType> type = PresentationStateType.fromUid(grayscaleUid);

      assertTrue(type.isPresent());
      assertEquals(PresentationStateType.GRAYSCALE_SOFTCOPY, type.get());
      assertEquals(grayscaleUid, type.get().getUid());
    }

    @Test
    @DisplayName("Should return empty for unknown UID")
    void shouldReturnEmptyForUnknownUid() {
      Optional<PresentationStateType> type = PresentationStateType.fromUid("unknown.uid");
      assertFalse(type.isPresent());
    }

    @Test
    @DisplayName("Should group presentation states by functional group")
    void shouldGroupPresentationStatesByFunctionalGroup() {
      Set<PresentationStateType> basicGrayscale =
          PresentationStateType.getByGroup(PresentationGroup.BASIC_GRAYSCALE);
      Set<PresentationStateType> colorBased =
          PresentationStateType.getByGroup(PresentationGroup.COLOR_BASED);
      Set<PresentationStateType> volumetric =
          PresentationStateType.getByGroup(PresentationGroup.VOLUMETRIC);

      assertEquals(1, basicGrayscale.size());
      assertTrue(basicGrayscale.contains(PresentationStateType.GRAYSCALE_SOFTCOPY));

      assertEquals(3, colorBased.size());
      assertTrue(colorBased.contains(PresentationStateType.COLOR_SOFTCOPY));
      assertTrue(colorBased.contains(PresentationStateType.PSEUDO_COLOR_SOFTCOPY));
      assertTrue(colorBased.contains(PresentationStateType.BLENDING_SOFTCOPY));

      assertEquals(5, volumetric.size());
      assertTrue(volumetric.contains(PresentationStateType.GRAYSCALE_PLANAR_MPR_VOLUMETRIC));
      assertTrue(volumetric.contains(PresentationStateType.VOLUME_RENDERING_VOLUMETRIC));
    }

    @Test
    @DisplayName("Should validate all SOP class UIDs are in range 11.1-11.12")
    void shouldValidateAllSopClassUidsInRange() {
      for (PresentationStateType type : PresentationStateType.values()) {
        String uid = type.getUid();
        assertTrue(uid.startsWith("1.2.840.10008.5.1.4.1.1.11."));

        String suffix = uid.substring("1.2.840.10008.5.1.4.1.1.11.".length());
        int number = Integer.parseInt(suffix);
        assertTrue(number >= 1 && number <= 12, "UID " + uid + " is not in range 11.1-11.12");
      }
    }
  }

  @Nested
  @DisplayName("Capability Testing")
  class CapabilityTests {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "1.2.840.10008.5.1.4.1.1.11.6", // GRAYSCALE_PLANAR_MPR_VOLUMETRIC
          "1.2.840.10008.5.1.4.1.1.11.7", // COMPOSITING_PLANAR_MPR_VOLUMETRIC
          "1.2.840.10008.5.1.4.1.1.11.9", // VOLUME_RENDERING_VOLUMETRIC
          "1.2.840.10008.5.1.4.1.1.11.10", // SEGMENTED_VOLUME_RENDERING_VOLUMETRIC
          "1.2.840.10008.5.1.4.1.1.11.11" // MULTIPLE_VOLUME_RENDERING_VOLUMETRIC
        })
    @DisplayName("Should support volumetric rendering for volumetric presentation states")
    void shouldSupportVolumetricRenderingForVolumetricStates(String uid) {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, uid);

      PrDicomObject prObject = new PrDicomObject(dcm);

      assertTrue(prObject.supportsVolumetricRendering());
      assertEquals(PresentationGroup.VOLUMETRIC, prObject.getPresentationGroup());
    }

    @Test
    @DisplayName("Should support advanced blending for advanced blending presentation state")
    void shouldSupportAdvancedBlendingForAdvancedBlendingState() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.8"); // ADVANCED_BLENDING

      PrDicomObject prObject = new PrDicomObject(dcm);

      assertTrue(prObject.supportsAdvancedBlending());
      assertEquals(PresentationGroup.ADVANCED_BLENDING, prObject.getPresentationGroup());
    }

    @Test
    @DisplayName("Should not support volumetric rendering for basic grayscale presentation state")
    void shouldNotSupportVolumetricRenderingForBasicGrayscale() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1"); // GRAYSCALE_SOFTCOPY

      PrDicomObject prObject = new PrDicomObject(dcm);

      assertFalse(prObject.supportsVolumetricRendering());
      assertFalse(prObject.supportsAdvancedBlending());
    }
  }

  @Nested
  @DisplayName("LUT and Presentation Tests")
  class LutAndPresentationTests {

    @Test
    @DisplayName("Should handle presentation LUT sequence")
    void shouldHandlePresentationLutSequence() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");

      // Add presentation LUT sequence
      Sequence lutSeq = dcm.newSequence(Tag.PresentationLUTSequence, 1);
      Attributes lutItem = new Attributes();
      lutItem.setInt(Tag.LUTDescriptor, VR.US, 256, 0, 8);
      lutItem.setBytes(Tag.LUTData, VR.OW, new byte[512]);
      lutSeq.add(lutItem);

      dcm.setString(Tag.LUTExplanation, VR.LO, "Test LUT");

      PrDicomObject prObject = new PrDicomObject(dcm);

      assertTrue(prObject.getPrLutExplanation().isPresent());
      assertEquals("Test LUT", prObject.getPrLutExplanation().get());
      assertTrue(prObject.getPrLutShapeMode().isPresent());
      assertEquals("IDENTITY", prObject.getPrLutShapeMode().get());
    }

    @Test
    @DisplayName("Should handle presentation LUT shape")
    void shouldHandlePresentationLutShape() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
      dcm.setString(Tag.PresentationLUTShape, VR.CS, "INVERSE");

      PrDicomObject prObject = new PrDicomObject(dcm);

      assertTrue(prObject.getPrLutShapeMode().isPresent());
      assertEquals("INVERSE", prObject.getPrLutShapeMode().get());
      assertFalse(prObject.getPrLut().isPresent());
    }

    @Test
    @DisplayName("Should handle VOI LUT sequence")
    void shouldHandleVoiLutSequence() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");

      // Add VOI LUT sequence
      Sequence voiSeq = dcm.newSequence(Tag.SoftcopyVOILUTSequence, 1);
      Attributes voiItem = new Attributes();
      voiItem.setDouble(Tag.WindowCenter, VR.DS, 128.0);
      voiItem.setDouble(Tag.WindowWidth, VR.DS, 256.0);
      voiSeq.add(voiItem);

      PrDicomObject prObject = new PrDicomObject(dcm);

      assertTrue(prObject.getVoiLUT().isPresent());
    }
  }

  @Nested
  @DisplayName("Applicability Tests")
  class ApplicabilityTests {

    private PrDicomObject createPrObjectWithReferences() {
      String seriesInstanceUID = "1.2.276.0.7230010.3.200.12.1";
      String sopInstanceUID = "1.2.276.0.7230010.3.200.12.1.1";
      int frame = 1;
      int segment = 5;

      Attributes pr = new Attributes();
      pr.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
      pr.setDouble(Tag.RescaleSlope, VR.DS, 2.0);
      pr.setDouble(Tag.RescaleIntercept, VR.DS, 1.0);
      pr.setString(Tag.RescaleType, VR.LO, "CT");

      Sequence seriesSeq = pr.newSequence(Tag.ReferencedSeriesSequence, 1);
      Attributes rfs = new Attributes();
      rfs.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);

      Sequence imageSeq = rfs.newSequence(Tag.ReferencedImageSequence, 1);
      Attributes rfi = new Attributes();
      rfi.setString(Tag.ReferencedSOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
      rfi.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sopInstanceUID);
      rfi.setInt(Tag.ReferencedFrameNumber, VR.IS, frame);
      rfi.setInt(Tag.ReferencedSegmentNumber, VR.IS, segment);
      imageSeq.add(rfi);
      seriesSeq.add(rfs);

      return new PrDicomObject(pr);
    }

    @Test
    @DisplayName("Should correctly determine image frame applicability")
    void shouldCorrectlyDetermineImageFrameApplicability() {
      PrDicomObject prObject = createPrObjectWithReferences();
      String seriesUID = "1.2.276.0.7230010.3.200.12.1";
      String sopUID = "1.2.276.0.7230010.3.200.12.1.1";

      // Positive cases
      assertTrue(prObject.isImageFrameApplicable(seriesUID, sopUID, 1));

      // Negative cases
      assertFalse(prObject.isImageFrameApplicable("wrong.series", sopUID, 1));
      assertFalse(prObject.isImageFrameApplicable(null, sopUID, 1));
      assertFalse(prObject.isImageFrameApplicable(seriesUID, "wrong.sop", 1));
      assertFalse(prObject.isImageFrameApplicable(seriesUID, null, 1));
      assertFalse(prObject.isImageFrameApplicable(seriesUID, sopUID, 999));
    }

    @Test
    @DisplayName("Should correctly determine segmentation segment applicability")
    void shouldCorrectlyDetermineSegmentationSegmentApplicability() {
      PrDicomObject prObject = createPrObjectWithReferences();
      String seriesUID = "1.2.276.0.7230010.3.200.12.1";
      String sopUID = "1.2.276.0.7230010.3.200.12.1.1";

      // Positive cases
      assertTrue(prObject.isSegmentationSegmentApplicable(seriesUID, sopUID, 5));

      // Negative cases
      assertFalse(prObject.isSegmentationSegmentApplicable("wrong.series", sopUID, 5));
      assertFalse(prObject.isSegmentationSegmentApplicable(null, sopUID, 5));
      assertFalse(prObject.isSegmentationSegmentApplicable(seriesUID, "wrong.sop", 5));
      assertFalse(prObject.isSegmentationSegmentApplicable(seriesUID, null, 5));
      assertFalse(prObject.isSegmentationSegmentApplicable(seriesUID, sopUID, 999));
    }

    @Test
    @DisplayName("Should handle empty reference sequences")
    void shouldHandleEmptyReferenceSequences() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");

      PrDicomObject prObject = new PrDicomObject(dcm);

      assertFalse(prObject.isImageFrameApplicable("any.series", "any.sop", 1));
      assertFalse(prObject.isSegmentationSegmentApplicable("any.series", "any.sop", 1));
    }
  }

  @Nested
  @DisplayName("Getter Methods Tests")
  class GetterMethodsTests {

    @Test
    @DisplayName("Should return correct basic properties")
    void shouldReturnCorrectBasicProperties() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
      dcm.setString(Tag.ContentLabel, VR.CS, "TEST_PR");
      dcm.setInt(Tag.InstanceNumber, VR.IS, 42);

      PrDicomObject prObject = new PrDicomObject(dcm);

      assertEquals(dcm, prObject.getDicomObject());
      assertEquals("TEST_PR", prObject.getPrContentLabel());
      assertEquals(Color.BLACK, prObject.getShutterColor());
      assertNull(prObject.getShutterShape());
      assertFalse(prObject.hasOverlay());
    }

    @Test
    @DisplayName("Should return default content label when not specified")
    void shouldReturnDefaultContentLabelWhenNotSpecified() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
      dcm.setInt(Tag.InstanceNumber, VR.IS, 123);

      PrDicomObject prObject = new PrDicomObject(dcm);

      assertEquals("PR 123", prObject.getPrContentLabel());
    }

    @Test
    @DisplayName("Should return empty collections for missing sequences")
    void shouldReturnEmptyCollectionsForMissingSequences() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");

      PrDicomObject prObject = new PrDicomObject(dcm);

      assertTrue(prObject.getOverlays().isEmpty());
      assertTrue(prObject.getShutterOverlays().isEmpty());
      assertTrue(prObject.getReferencedSeriesSequence().isEmpty());
      assertTrue(prObject.getGraphicAnnotationSequence().isEmpty());
      assertTrue(prObject.getGraphicLayerSequence().isEmpty());
      assertFalse(prObject.getVoiLUT().isPresent());
      assertFalse(prObject.getPrLut().isPresent());
      assertFalse(prObject.getPrLutExplanation().isPresent());
    }

    @Test
    @DisplayName("Should return modality LUT module")
    void shouldReturnModalityLutModule() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
      dcm.setDouble(Tag.RescaleSlope, VR.DS, 2.0);
      dcm.setDouble(Tag.RescaleIntercept, VR.DS, 1.0);
      dcm.setString(Tag.RescaleType, VR.LO, "CT");

      PrDicomObject prObject = new PrDicomObject(dcm);

      assertNotNull(prObject.getModalityLutModule());
      assertTrue(prObject.getModalityLutModule().getRescaleSlope().isPresent());
      assertTrue(prObject.getModalityLutModule().getRescaleIntercept().isPresent());
      assertEquals(2.0, prObject.getModalityLutModule().getRescaleSlope().getAsDouble(), 0.001);
      assertEquals(1.0, prObject.getModalityLutModule().getRescaleIntercept().getAsDouble(), 0.001);
    }
  }

  @Nested
  @DisplayName("Presentation Group Tests")
  class PresentationGroupTests {

    @Test
    @DisplayName("Should have correct group descriptions")
    void shouldHaveCorrectGroupDescriptions() {
      assertEquals(
          "Basic grayscale presentation with standard windowing",
          PresentationGroup.BASIC_GRAYSCALE.getDescription());
      assertEquals(
          "Color and pseudo-color presentation states",
          PresentationGroup.COLOR_BASED.getDescription());
      assertEquals(
          "Specialized modality-specific presentation states",
          PresentationGroup.SPECIALIZED.getDescription());
      assertEquals(
          "3D volumetric rendering presentation states",
          PresentationGroup.VOLUMETRIC.getDescription());
      assertEquals(
          "Advanced blending and compositing",
          PresentationGroup.ADVANCED_BLENDING.getDescription());
      assertEquals(
          "Advanced LUT manipulation and variable modality LUT",
          PresentationGroup.ADVANCED_LUT.getDescription());
    }

    @Test
    @DisplayName("Should categorize all presentation state types")
    void shouldCategorizeAllPresentationStateTypes() {
      // Verify each group has the expected number of types
      assertEquals(1, PresentationStateType.getByGroup(PresentationGroup.BASIC_GRAYSCALE).size());
      assertEquals(3, PresentationStateType.getByGroup(PresentationGroup.COLOR_BASED).size());
      assertEquals(1, PresentationStateType.getByGroup(PresentationGroup.SPECIALIZED).size());
      assertEquals(5, PresentationStateType.getByGroup(PresentationGroup.VOLUMETRIC).size());
      assertEquals(1, PresentationStateType.getByGroup(PresentationGroup.ADVANCED_BLENDING).size());
      assertEquals(1, PresentationStateType.getByGroup(PresentationGroup.ADVANCED_LUT).size());

      // Verify total count matches all enum values
      int totalTypes = 0;
      for (PresentationGroup group : PresentationGroup.values()) {
        totalTypes += PresentationStateType.getByGroup(group).size();
      }
      assertEquals(PresentationStateType.values().length, totalTypes);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle presentation creation date/time gracefully when missing")
    void shouldHandleMissingPresentationDateTime() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");

      PrDicomObject prObject = new PrDicomObject(dcm);

      // Should not throw exception, may return null or epoch time
      assertDoesNotThrow(prObject::getPresentationCreationDateTime);
    }

    @Test
    @DisplayName("Should handle empty string UIDs gracefully")
    void shouldHandleEmptyStringUids() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "");

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> new PrDicomObject(dcm));

      assertTrue(
          exception.getMessage().contains("does not match any supported DICOM Presentation State"));
    }

    @Test
    @DisplayName("Should handle applicability checks with empty strings")
    void shouldHandleApplicabilityChecksWithEmptyStrings() {
      Attributes dcm = new Attributes();
      dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");

      PrDicomObject prObject = new PrDicomObject(dcm);

      assertFalse(prObject.isImageFrameApplicable("", "sop", 1));
      assertFalse(prObject.isImageFrameApplicable("   ", "sop", 1)); // whitespace only
      assertFalse(prObject.isSegmentationSegmentApplicable("", "sop", 1));
    }
  }
}
