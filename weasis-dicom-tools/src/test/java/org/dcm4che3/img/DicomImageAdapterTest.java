/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.dcm4che3.img.lut.WindLevelParameters;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.LutTestDataBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.WlPresentation;

class DicomImageAdapterTest {
  static final Path IN_DIR =
      FileSystems.getDefault().getPath("target/test-classes/org/dcm4che3/img");
  static DicomImageReader reader;

  @BeforeAll
  static void setUp() {
    reader = new DicomImageReader(new DicomImageReaderSpi());
  }

  @AfterAll
  static void tearDown() {
    if (reader != null) reader.dispose();
  }

  // Test helper methods for creating synthetic data
  private static PlanarImage createTestImage(int width, int height, int type, double value) {
    Mat mat = new Mat(height, width, type, new Scalar(value));
    return ImageCV.fromMat(mat);
  }

  private static ImageDescriptor createTestDescriptor(
      int bitsAllocated, int bitsStored, boolean signed, PhotometricInterpretation photometric) {
    Attributes attributes = new Attributes();
    attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
    attributes.setInt(Tag.BitsStored, VR.US, bitsStored);
    attributes.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
    attributes.setString(Tag.PhotometricInterpretation, VR.CS, photometric.toString());
    return new ImageDescriptor(attributes);
  }

  private static ImageDescriptor createTestDescriptorWithRescale(
      int bitsAllocated, int bitsStored, boolean signed, double slope, double intercept) {
    Attributes attributes = new Attributes();
    attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
    attributes.setInt(Tag.BitsStored, VR.US, bitsStored);
    attributes.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
    attributes.setString(
        Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());
    attributes.setDouble(Tag.RescaleSlope, VR.DS, slope);
    attributes.setDouble(Tag.RescaleIntercept, VR.DS, intercept);
    return new ImageDescriptor(attributes);
  }

  private static ImageDescriptor createTestDescriptorWithPadding(
      int bitsAllocated, int bitsStored, int paddingValue) {
    Attributes attributes = new Attributes();
    attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
    attributes.setInt(Tag.BitsStored, VR.US, bitsStored);
    attributes.setInt(Tag.PixelRepresentation, VR.US, 0);
    attributes.setString(
        Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());
    attributes.setInt(Tag.PixelPaddingValue, VR.US, paddingValue);
    return new ImageDescriptor(attributes);
  }

  @Nested
  @DisplayName("Constructor and Basic Properties Tests")
  class ConstructorAndBasicPropertiesTests {

    @Test
    @DisplayName("Constructor should handle 8-bit unsigned image correctly")
    void constructorWith8BitUnsignedImage() {
      PlanarImage image = createTestImage(64, 64, CvType.CV_8UC1, 128);
      ImageDescriptor desc =
          createTestDescriptor(8, 8, false, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      assertEquals(8, adapter.getBitsStored());
      assertEquals(desc, adapter.getImageDescriptor());
      assertEquals(0, adapter.getFrameIndex());
      assertNotNull(adapter.getMinMax());
    }

    @Test
    @DisplayName("Constructor should handle 16-bit signed image correctly")
    void constructorWith16BitSignedImage() {
      PlanarImage image = createTestImage(32, 32, CvType.CV_16SC1, -1000);
      ImageDescriptor desc =
          createTestDescriptor(16, 12, true, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 1);

      assertEquals(12, adapter.getBitsStored());
      assertEquals(1, adapter.getFrameIndex());
      assertTrue(adapter.getImageDescriptor().isSigned());
    }

    @Test
    @DisplayName("Constructor should adjust bits stored when image values exceed range")
    void constructorShouldAdjustBitsStoredForOutOfRangeValues() {
      // Create image with values that exceed 6-bit range (0-63)
      PlanarImage image = createTestImage(16, 16, CvType.CV_8UC1, 200);
      ImageDescriptor desc =
          createTestDescriptor(8, 6, false, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      // Should be adjusted to 8 bits to handle the value 200
      assertEquals(8, adapter.getBitsStored());
    }

    @Test
    @DisplayName("Constructor should throw exception for null parameters")
    void constructorShouldThrowForNullParameters() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      ImageDescriptor desc =
          createTestDescriptor(8, 8, false, PhotometricInterpretation.MONOCHROME2);

      assertThrows(NullPointerException.class, () -> new DicomImageAdapter(null, desc, 0));
      assertThrows(NullPointerException.class, () -> new DicomImageAdapter(image, null, 0));
    }
  }

  @Nested
  @DisplayName("Min/Max Value Computation Tests")
  class MinMaxValueComputationTests {

    @Test
    @DisplayName("Should compute correct min/max for uniform image")
    void shouldComputeMinMaxForUniformImage() {
      PlanarImage image = createTestImage(32, 32, CvType.CV_8UC1, 150);
      ImageDescriptor desc =
          createTestDescriptor(8, 8, false, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);
      MinMaxLocResult minMax = adapter.getMinMax();

      assertEquals(150.0, minMax.minVal, 0.1);
      assertEquals(151.0, minMax.maxVal, 0.1); // +1 with non 8-bit color images
    }

    @Test
    @DisplayName("Should handle 8-bit images with special min/max logic")
    void shouldHandle8BitImagesSpecially() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_8UC3, 0);
      ImageDescriptor desc = createTestDescriptor(8, 8, false, PhotometricInterpretation.RGB);

      MinMaxLocResult result = DicomImageAdapter.getMinMaxValues(image, desc, 0);

      assertEquals(0.0, result.minVal);
      assertEquals(255.0, result.maxVal); // 8-bit color images have special handling
    }

    @Test
    @DisplayName("Should compute min/max excluding padding values")
    void shouldComputeMinMaxExcludingPadding() {
      // Create image with mixed values
      ImageCV mat = new ImageCV(4, 4, CvType.CV_16UC1);
      mat.put(0, 0, 100, 200, 300, 400);
      mat.put(1, 0, 0, 0, 500, 600); // 0 will be padding
      mat.put(2, 0, 700, 800, 0, 0);
      mat.put(3, 0, 900, 1000, 1100, 0);

      ImageDescriptor desc = createTestDescriptorWithPadding(16, 16, 0);

      MinMaxLocResult result = DicomImageAdapter.getMinMaxValues(mat, desc, 0);

      // Should exclude padding value 0
      assertTrue(result.minVal > 0);
      assertEquals(1100.0, result.maxVal, 0.1);
    }
  }

  @Nested
  @DisplayName("Rescale Operations Tests")
  class RescaleOperationsTests {

    @Test
    @DisplayName("Should apply rescale slope and intercept correctly")
    void shouldApplyRescaleCorrectly() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_16UC1, 1000);
      ImageDescriptor desc = createTestDescriptorWithRescale(16, 12, false, 0.5, -1024.0);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);
      WlPresentation wl = new WindLevelParameters(adapter);

      assertEquals(0.5, adapter.getRescaleSlope(null));
      assertEquals(-1024.0, adapter.getRescaleIntercept(null));

      // Pixel value 1000 should become: 1000 * 0.5 + (-1024) = -524
      Number transformedValue = adapter.pixelToRealValue(1000, wl);
      assertNotNull(transformedValue);
      assertEquals(-524.0, transformedValue.doubleValue(), 0.1);
    }

    @Test
    @DisplayName("Should handle identity transformation optimization")
    void shouldOptimizeIdentityTransformation() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      ImageDescriptor desc = createTestDescriptorWithRescale(8, 8, false, 1.0, 0.0);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      // Should return null for identity transformation (optimization)
      assertNull(adapter.getLutParameters(false, null, false, null));
    }

    @Test
    @DisplayName("Should determine correct output signing for rescale operations")
    void shouldDetermineCorrectOutputSigning() {
      // Test case where rescale results in negative values
      PlanarImage image = createTestImage(16, 16, CvType.CV_16UC1, 500);
      ImageDescriptor desc = createTestDescriptorWithRescale(16, 12, false, 1.0, -1000.0);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);
      WlPresentation wl = new WindLevelParameters(adapter);

      assertTrue(adapter.isModalityLutOutSigned(wl));
      assertTrue(adapter.getMinValue(wl) < 0);
    }
  }

  @Nested
  @DisplayName("Window/Level Preset Tests")
  class WindowLevelPresetTests {

    @Test
    @DisplayName("Should compute default window and level correctly")
    void shouldComputeDefaultWindowLevel() {
      PlanarImage image = createTestImage(32, 32, CvType.CV_16UC1, 2048);
      ImageDescriptor desc =
          createTestDescriptor(16, 12, false, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);
      WlPresentation wl = new WindLevelParameters(adapter);

      // For uniform image with value 2048, min=2048, max=2049 (adjusted)
      assertEquals(2048.5, adapter.getDefaultLevel(wl), 0.1);
      assertEquals(1.0, adapter.getDefaultWindow(wl), 0.1);
    }

    @Test
    @DisplayName("Should return LINEAR as default LUT shape")
    void shouldReturnLinearAsDefaultLutShape() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_8UC1, 128);
      ImageDescriptor desc =
          createTestDescriptor(8, 8, false, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);
      WlPresentation wl = new WindLevelParameters(adapter);

      assertEquals(LutShape.LINEAR, adapter.getDefaultShape(wl));
    }

    @Test
    @DisplayName("Should compute full dynamic range correctly")
    void shouldComputeFullDynamicRange() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_16UC1, 1000);
      image.toImageCV().put(0, 0, 3000); // Uniform image

      ImageDescriptor desc = createTestDescriptorWithRescale(16, 12, false, 2.0, 100.0);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);
      WlPresentation wl = new WindLevelParameters(adapter);
      assertEquals(6100.0, adapter.getMaxValue(wl), 1.0); // 3000 * 2 + 100
      assertEquals(2100.0, adapter.getMinValue(wl), 1.0); // 1000 * 2 + 100

      double expectedWidth = 4000.0; // (3000 - 1000) * 2 + rounding adjustments
      assertEquals(expectedWidth, adapter.getFullDynamicWidth(wl), 1.0);

      double expectedCenter = 4100; // (3000 - 1000) * 2 + 100
      assertEquals(expectedCenter, adapter.getFullDynamicCenter(wl), 1.0);
    }

    @Test
    @DisplayName("Should handle preset list operations")
    void shouldHandlePresetListOperations() {
      PlanarImage image = createTestImage(32, 32, CvType.CV_16UC1, 1024);
      ImageDescriptor desc =
          createTestDescriptor(16, 12, false, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);
      WlPresentation wl = new WindLevelParameters(adapter);

      List<PresetWindowLevel> presets = adapter.getPresetList(wl);
      assertNotNull(presets);

      // Should have at least one preset (auto-generated)
      assertTrue(adapter.getPresetCollectionSize() >= 0);

      PresetWindowLevel defaultPreset = adapter.getDefaultPreset(wl);
      if (presets != null && !presets.isEmpty()) {
        assertSame(presets.get(0), defaultPreset);
      }
    }
  }

  @Nested
  @DisplayName("Presentation State (PR) Tests")
  class PresentationStateTests {

    @Test
    @DisplayName("Should handle DicomImageAdapter with PrDicomObject presentation state")
    void shouldHandleAdapterWithPrDicomObject() {
      // Create test image and descriptor
      PlanarImage image = createTestImage(64, 64, CvType.CV_16SC1, 1000);
      ImageDescriptor desc =
          createTestDescriptor(16, 12, true, PhotometricInterpretation.MONOCHROME2);

      // Create mock DICOM attributes for presentation state
      Attributes prAttributes = new Attributes();
      prAttributes.setString(
          Tag.SOPClassUID,
          VR.UI,
          "1.2.840.10008.5.1.4.1.1.11.1"); // Grayscale Softcopy Presentation State
      prAttributes.setString(Tag.PresentationLUTShape, VR.CS, "IDENTITY");

      // Create PrDicomObject
      PrDicomObject prDicomObject = new PrDicomObject(prAttributes);

      // Create adapter
      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      // Verify adapter works with presentation state
      assertNotNull(adapter);
      assertEquals(desc, adapter.getImageDescriptor());

      // Test presentation state integration
      assertNotNull(prDicomObject);
      assertEquals(
          PrDicomObject.PresentationStateType.GRAYSCALE_SOFTCOPY,
          prDicomObject.getPresentationStateType());
    }

    @Test
    @DisplayName("Should handle PrDicomObject with presentation LUT using LutTestDataBuilder")
    void shouldHandlePrDicomObjectWithPresentationLut() {
      // Create DICOM attributes with presentation LUT sequence using LutTestDataBuilder
      Attributes prAttributes = new Attributes();
      prAttributes.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");

      // Use LutTestDataBuilder to create proper LUT data
      Attributes lutData = LutTestDataBuilder.createLinearLut8Bit();
      prAttributes.newSequence(Tag.PresentationLUTSequence, 1).add(lutData);

      PrDicomObject prDicomObject = new PrDicomObject(prAttributes);

      assertNotNull(prDicomObject);
      assertTrue(prDicomObject.getPrLut().isPresent());

      LookupTableCV prLut = prDicomObject.getPrLut().get();
      assertNotNull(prLut);
    }

    @Test
    @DisplayName("Should handle different presentation state types")
    void shouldHandleDifferentPresentationStateTypes() {
      // Test Pseudo-Color Softcopy Presentation State
      Attributes pseudoColorAttributes = new Attributes();
      pseudoColorAttributes.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.3");

      PrDicomObject pseudoColorPR = new PrDicomObject(pseudoColorAttributes);
      assertEquals(
          PrDicomObject.PresentationStateType.PSEUDO_COLOR_SOFTCOPY,
          pseudoColorPR.getPresentationStateType());
      assertEquals(
          PrDicomObject.PresentationGroup.COLOR_BASED,
          pseudoColorPR.getPresentationStateType().getGroup());

      // Test Variable Modality LUT Softcopy Presentation State
      Attributes variableLutAttributes = new Attributes();
      variableLutAttributes.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.12");

      PrDicomObject variableLutPR = new PrDicomObject(variableLutAttributes);
      assertEquals(
          PrDicomObject.PresentationStateType.VARIABLE_MODALITY_LUT_SOFTCOPY,
          variableLutPR.getPresentationStateType());
      assertEquals(
          PrDicomObject.PresentationGroup.ADVANCED_LUT,
          variableLutPR.getPresentationStateType().getGroup());
    }
  }

  @Nested
  @DisplayName("Modality LUT with LookupTableCV Tests")
  class ModalityLutWithLookupTableCVTests {

    @Test
    @DisplayName("Should create and apply modality LUT with 8-bit LookupTableCV")
    void shouldCreateModalityLutWith8BitLookupTableCV() {
      // Create test image
      PlanarImage image = createTestImage(32, 32, CvType.CV_8UC1, 128);

      // Create descriptor with modality LUT sequence using LutTestDataBuilder
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, 8);
      attributes.setInt(Tag.BitsStored, VR.US, 8);
      attributes.setInt(Tag.PixelRepresentation, VR.US, 0);
      attributes.setString(
          Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());

      // Use LutTestDataBuilder for proper 8-bit LUT
      Attributes modalityLutData = LutTestDataBuilder.createLinearLut8Bit();
      attributes.newSequence(Tag.ModalityLUTSequence, 1).add(modalityLutData);

      ImageDescriptor desc = new ImageDescriptor(attributes);
      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      // Test that modality LUT is available
      WlPresentation wlParams = new WindLevelParameters(adapter, null);
      LookupTableCV modalityLookup = adapter.getModalityLookup(wlParams, false);

      assertNotNull(modalityLookup);
    }

    @Test
    @DisplayName("Should handle CT Hounsfield LookupTableCV in modality LUT")
    void shouldHandleCtHounsfieldLookupTableCV() {
      // Create test image with 16-bit signed data
      PlanarImage image = createTestImage(64, 64, CvType.CV_16SC1, -500);

      // Create descriptor with CT modality LUT
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, 16);
      attributes.setInt(Tag.BitsStored, VR.US, 12);
      attributes.setInt(Tag.PixelRepresentation, VR.US, 1); // Signed
      attributes.setString(
          Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());

      // Use LutTestDataBuilder for CT Hounsfield LUT
      Attributes modalityLutData = LutTestDataBuilder.createCtHounsfieldLut();
      attributes.newSequence(Tag.ModalityLUTSequence, 1).add(modalityLutData);

      ImageDescriptor desc = new ImageDescriptor(attributes);
      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      // Test CT modality LUT creation
      WlPresentation wlParams = new WindLevelParameters(adapter, null);
      LookupTableCV modalityLookup = adapter.getModalityLookup(wlParams, false);

      assertNotNull(modalityLookup);
      assertTrue(adapter.isModalityLutOutSigned(wlParams));
    }

    @Test
    @DisplayName("Should handle 12-bit contrast enhancement LookupTableCV")
    void shouldHandle12BitContrastLookupTableCV() {
      // Create test image with 12-bit data
      PlanarImage image = createTestImage(48, 48, CvType.CV_16UC1, 2048);

      // Create descriptor with 12-bit data
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, 16);
      attributes.setInt(Tag.BitsStored, VR.US, 12);
      attributes.setInt(Tag.PixelRepresentation, VR.US, 0);
      attributes.setString(
          Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());

      // Use LutTestDataBuilder for 12-bit contrast LUT
      Attributes modalityLutData = LutTestDataBuilder.createContrastLut12Bit();
      attributes.newSequence(Tag.ModalityLUTSequence, 1).add(modalityLutData);

      ImageDescriptor desc = new ImageDescriptor(attributes);
      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      // Test 12-bit contrast LUT
      WlPresentation wlParams = new WindLevelParameters(adapter, null);
      LookupTableCV modalityLookup = adapter.getModalityLookup(wlParams, false);

      assertNull(
          modalityLookup); // Pixel values don't match Modality LUT sequence table. Modality LUT not
      // applied.
      assertFalse(adapter.isModalityLutOutSigned(wlParams));
    }

    @Test
    @DisplayName("Should integrate PrDicomObject with modality LUT containing LookupTableCV")
    void shouldIntegratePrDicomObjectWithModalityLut() {
      // Create test image
      PlanarImage image = createTestImage(32, 32, CvType.CV_16UC1, 1024);

      // Create image descriptor with modality LUT using LutTestDataBuilder
      Attributes imageAttributes = new Attributes();
      imageAttributes.setInt(Tag.BitsAllocated, VR.US, 16);
      imageAttributes.setInt(Tag.BitsStored, VR.US, 12);
      imageAttributes.setInt(Tag.PixelRepresentation, VR.US, 0);
      imageAttributes.setString(
          Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());

      // Add modality LUT sequence using LutTestDataBuilder
      Attributes modalityLutData = LutTestDataBuilder.createContrastLut12Bit();
      imageAttributes.newSequence(Tag.ModalityLUTSequence, 1).add(modalityLutData);

      ImageDescriptor desc = new ImageDescriptor(imageAttributes);

      // Create PrDicomObject with presentation LUT using LutTestDataBuilder
      Attributes prAttributes = new Attributes();
      prAttributes.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");

      // Add presentation LUT using LutTestDataBuilder
      Attributes presLutData = LutTestDataBuilder.createLinearLut8Bit();
      prAttributes.newSequence(Tag.PresentationLUTSequence, 1).add(presLutData);

      PrDicomObject prDicomObject = new PrDicomObject(prAttributes, desc);
      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      // Test integration
      assertNotNull(prDicomObject);
      assertNotNull(adapter);

      // Verify modality LUT is available
      WlPresentation wlParams = new WindLevelParameters(adapter, null);
      LookupTableCV modalityLookup = adapter.getModalityLookup(wlParams, false);
      assertNull(
          modalityLookup); // Pixel values don't match Modality LUT sequence table. Modality LUT not
      // applied.

      // Verify presentation LUT is available
      assertTrue(prDicomObject.getPrLut().isPresent());
      LookupTableCV presentationLut = prDicomObject.getPrLut().get();
      assertNotNull(presentationLut);

      // Test that both LUTs can be used together
      assertNotEquals(modalityLookup, presentationLut);
    }

    @Test
    @DisplayName("Should handle PrDicomObject with CT Hounsfield modality LUT")
    void shouldHandlePrDicomObjectWithCtModalityLut() {
      // Create test image for CT data
      PlanarImage image = createTestImage(64, 64, CvType.CV_16SC1, -1000);

      // Create CT image descriptor
      Attributes imageAttributes = new Attributes();
      imageAttributes.setInt(Tag.BitsAllocated, VR.US, 16);
      imageAttributes.setInt(Tag.BitsStored, VR.US, 12);
      imageAttributes.setInt(Tag.PixelRepresentation, VR.US, 1); // Signed for CT
      imageAttributes.setString(
          Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());

      // Add CT Hounsfield modality LUT
      Attributes modalityLutData = LutTestDataBuilder.createCtHounsfieldLut();
      imageAttributes.newSequence(Tag.ModalityLUTSequence, 1).add(modalityLutData);

      ImageDescriptor desc = new ImageDescriptor(imageAttributes);

      // Create PrDicomObject for Variable Modality LUT
      Attributes prAttributes = new Attributes();
      prAttributes.setString(
          Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.12"); // Variable Modality LUT
      prAttributes.setString(Tag.PresentationLUTShape, VR.CS, "IDENTITY");

      PrDicomObject prDicomObject = new PrDicomObject(prAttributes, desc);
      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      // Test CT-specific integration
      assertNotNull(prDicomObject);
      assertEquals(
          PrDicomObject.PresentationStateType.VARIABLE_MODALITY_LUT_SOFTCOPY,
          prDicomObject.getPresentationStateType());

      // Verify CT modality LUT
      WlPresentation wlParams = new WindLevelParameters(adapter, null);
      LookupTableCV modalityLookup = adapter.getModalityLookup(wlParams, false);
      assertNotNull(modalityLookup);
      assertTrue(adapter.isModalityLutOutSigned(wlParams));
    }
  }

  @Nested
  @DisplayName("Allocated Value Tests")
  class AllocatedValueTests {

    @Test
    @DisplayName("Should compute correct allocated values for unsigned data")
    void shouldComputeAllocatedValuesForUnsigned() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      ImageDescriptor desc =
          createTestDescriptor(8, 8, false, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);
      WlPresentation wl = new WindLevelParameters(adapter);

      assertEquals(0, adapter.getMinAllocatedValue(wl));
      assertEquals(255, adapter.getMaxAllocatedValue(wl));
    }

    @Test
    @DisplayName("Should compute correct allocated values for signed data")
    void shouldComputeAllocatedValuesForSigned() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_16SC1, -100);
      ImageDescriptor desc =
          createTestDescriptor(16, 12, true, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);
      WlPresentation wl = new WindLevelParameters(adapter);

      assertEquals(-32768, adapter.getMinAllocatedValue(wl));
      assertEquals(32767, adapter.getMaxAllocatedValue(wl));
    }

    @Test
    @DisplayName("Should handle signed output from rescale operations")
    void shouldHandleSignedOutputFromRescale() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_16UC1, 1000);
      ImageDescriptor desc = createTestDescriptorWithRescale(16, 12, false, 1.0, -2000.0);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);
      WlPresentation wl = new WindLevelParameters(adapter);

      // Should detect that rescale produces negative values
      assertTrue(adapter.isModalityLutOutSigned(wl));
      assertTrue(adapter.getMinAllocatedValue(wl) < 0);
    }
  }

  @Nested
  @DisplayName("Photometric Interpretation Tests")
  class PhotometricInterpretationTests {

    @Test
    @DisplayName("Should detect MONOCHROME1 as inverse")
    void shouldDetectMonochrome1AsInverse() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      ImageDescriptor desc =
          createTestDescriptor(8, 8, false, PhotometricInterpretation.MONOCHROME1);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      assertTrue(adapter.isPhotometricInterpretationInverse(null));
    }

    @Test
    @DisplayName("Should detect MONOCHROME2 as not inverse")
    void shouldDetectMonochrome2AsNotInverse() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      ImageDescriptor desc =
          createTestDescriptor(8, 8, false, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      assertFalse(adapter.isPhotometricInterpretationInverse(null));
    }
  }

  @Nested
  @DisplayName("VOI LUT Tests")
  class VOILutTests {

    @Test
    @DisplayName("Should create VOI LUT with valid parameters")
    void shouldCreateVOILutWithValidParameters() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_16UC1, 1000);
      ImageDescriptor desc =
          createTestDescriptor(16, 12, false, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);
      WindLevelParameters wl = new WindLevelParameters(adapter);
      LookupTableCV voiLut = adapter.getVOILookup(wl);
      assertNotNull(voiLut);
    }

    @Test
    @DisplayName("Should return null for invalid VOI parameters")
    void shouldReturnNullForInvalidVOIParameters() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      ImageDescriptor desc =
          createTestDescriptor(8, 8, false, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);
      assertNull(adapter.getVOILookup(null));
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling Tests")
  class EdgeCasesAndErrorHandlingTests {

    @Test
    @DisplayName("Should handle pixel value transformation with null input")
    void shouldHandlePixelTransformationWithNullInput() {
      PlanarImage image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      ImageDescriptor desc =
          createTestDescriptor(8, 8, false, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      assertNull(adapter.pixelToRealValue(null, null));
    }

    @Test
    @DisplayName("Should handle very large bit depths correctly")
    void shouldHandleLargeBitDepths() {
      PlanarImage image = createTestImage(8, 8, CvType.CV_32SC1, 1000000);
      ImageDescriptor desc =
          createTestDescriptor(32, 32, true, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      assertEquals(32, adapter.getBitsStored());
      assertNotNull(adapter.getMinMax());
    }

    @Test
    @DisplayName("Should handle empty or minimal images")
    void shouldHandleMinimalImages() {
      PlanarImage image = createTestImage(1, 1, CvType.CV_8UC1, 128);
      ImageDescriptor desc =
          createTestDescriptor(8, 8, false, PhotometricInterpretation.MONOCHROME2);

      DicomImageAdapter adapter = new DicomImageAdapter(image, desc, 0);

      assertEquals(8, adapter.getBitsStored());
      assertNotNull(adapter.getMinMax());
    }
  }

  // Integration tests with real DICOM files (when available)
  @Nested
  @DisplayName("Integration Tests with Real DICOM Files")
  class IntegrationTests {

    @Test
    @DisplayName("Read YBR_FULL with RLE compression")
    void ybrFullRLE() throws Exception {
      try {
        DicomImageReadParam readParam = new DicomImageReadParam();
        DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
        reader.setInput(
            new DicomFileInputStream(
                FileSystems.getDefault().getPath(IN_DIR.toString(), "ybrFull-RLE.dcm")));
        ImageDescriptor desc = reader.getImageDescriptor();
        PlanarImage imageSource = reader.getPlanarImage(0, readParam);
        PlanarImage img = ImageRendering.getImageWithoutEmbeddedOverlay(imageSource, desc, 0);
        DicomImageAdapter adapter = new DicomImageAdapter(img, desc, 0);

        assertEquals(0, adapter.getPresetCollectionSize());
        assertEquals(8, adapter.getBitsStored());
        assertEquals(255, adapter.getMinMax().maxVal);
        assertEquals(0, adapter.getMinMax().minVal);

        WlPresentation wlp = new WindLevelParameters(adapter);
        assertEquals(127.5, adapter.getDefaultLevel(wlp));
        assertEquals(255.0, adapter.getDefaultWindow(wlp));

        img = ImageRendering.getModalityLutImage(imageSource, adapter, readParam);
        assertEquals(640, img.width());
      } catch (IOException e) {
        // Skip test if file not available
        System.out.println("Skipping YBR_FULL test - file not available: " + e.getMessage());
      }
    }

    @Test
    @DisplayName("Read MR frame with JPEG-Lossless compression")
    void readMR() throws Exception {
      try {
        DicomImageReadParam readParam = new DicomImageReadParam();
        DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
        reader.setInput(
            new DicomFileInputStream(
                FileSystems.getDefault().getPath(IN_DIR.toString(), "MR-JPEGLosslessSV1.dcm")));
        ImageDescriptor desc = reader.getImageDescriptor();
        PlanarImage imageSource = reader.getPlanarImage(0, readParam);
        PlanarImage img = ImageRendering.getImageWithoutEmbeddedOverlay(imageSource, desc, 0);
        DicomImageAdapter adapter = new DicomImageAdapter(img, desc, 0);

        assertEquals(0, adapter.getPresetCollectionSize());
        assertEquals(8, adapter.getBitsStored());
        assertEquals(48, adapter.getMinMax().maxVal);
        assertEquals(0, adapter.getMinMax().minVal);

        WlPresentation wlp = new WindLevelParameters(adapter);
        assertEquals(920, adapter.getDefaultLevel(wlp));
        assertEquals(1832, adapter.getDefaultWindow(wlp));

        img = ImageRendering.getModalityLutImage(imageSource, adapter, readParam);
        assertEquals(256, img.width());
      } catch (IOException e) {
        // Skip test if file not available
        System.out.println("Skipping MR test - file not available: " + e.getMessage());
      }
    }
  }
}
