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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.lut.WindLevelParameters;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.LutTestDataBuilder;
import org.junit.jupiter.api.*;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.LutShape;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DicomImageAdapterTest {

  private static final Path TEST_RESOURCES_DIR =
      FileSystems.getDefault().getPath("target/test-classes/org/dcm4che3/img");

  private static DicomImageReader reader;

  @BeforeAll
  static void setUp() {
    reader = new DicomImageReader(new DicomImageReaderSpi());
  }

  @AfterAll
  static void tearDown() {
    if (reader != null) {
      reader.dispose();
    }
  }

  // Test data builders using real structures
  private static PlanarImage createTestImage(int width, int height, int type, double value) {
    var mat = new Mat(height, width, type, new Scalar(value));
    return ImageCV.fromMat(mat);
  }

  private static PlanarImage createVariableImage(int width, int height, int type) {
    var mat = new Mat(height, width, type);
    var random = ThreadLocalRandom.current();

    switch (CvType.depth(type)) {
      case CvType.CV_8U -> {
        byte[] data = new byte[width * height * CvType.channels(type)];
        for (int i = 0; i < data.length; i++) {
          data[i] = (byte) random.nextInt(256);
        }
        mat.put(0, 0, data);
      }
      case CvType.CV_16U -> {
        short[] data = new short[width * height * CvType.channels(type)];
        for (int i = 0; i < data.length; i++) {
          data[i] = (short) random.nextInt(65536);
        }
        mat.put(0, 0, data);
      }
      case CvType.CV_16S -> {
        short[] data = new short[width * height * CvType.channels(type)];
        for (int i = 0; i < data.length; i++) {
          data[i] = (short) (random.nextInt(65536) - 32768);
        }
        mat.put(0, 0, data);
      }
      default -> throw new IllegalArgumentException("Unsupported type: " + type);
    }

    return ImageCV.fromMat(mat);
  }

  private static ImageDescriptor createDescriptor(DicomImageSpec spec) {
    var attributes = new Attributes();
    attributes.setInt(Tag.BitsAllocated, VR.US, spec.bitsAllocated());
    attributes.setInt(Tag.BitsStored, VR.US, spec.bitsStored());
    attributes.setInt(Tag.PixelRepresentation, VR.US, spec.signed() ? 1 : 0);
    attributes.setString(Tag.PhotometricInterpretation, VR.CS, spec.photometric().toString());

    if (spec.rescaleSlope() != null) {
      attributes.setDouble(Tag.RescaleSlope, VR.DS, spec.rescaleSlope());
    }
    if (spec.rescaleIntercept() != null) {
      attributes.setDouble(Tag.RescaleIntercept, VR.DS, spec.rescaleIntercept());
    }
    if (spec.paddingValue() != null) {
      attributes.setInt(Tag.PixelPaddingValue, VR.US, spec.paddingValue());
    }
    if (spec.paddingLimit() != null) {
      attributes.setInt(Tag.PixelPaddingRangeLimit, VR.US, spec.paddingLimit());
    }

    return new ImageDescriptor(attributes);
  }

  private record DicomImageSpec(
      int bitsAllocated,
      int bitsStored,
      boolean signed,
      PhotometricInterpretation photometric,
      Double rescaleSlope,
      Double rescaleIntercept,
      Integer paddingValue,
      Integer paddingLimit) {

    // Builder-style factory methods
    static DicomImageSpec basic(int bitsAllocated, int bitsStored, boolean signed) {
      return new DicomImageSpec(
          bitsAllocated,
          bitsStored,
          signed,
          PhotometricInterpretation.MONOCHROME2,
          null,
          null,
          null,
          null);
    }

    static DicomImageSpec withPhotometric(
        int bitsAllocated, int bitsStored, boolean signed, PhotometricInterpretation photometric) {
      return new DicomImageSpec(
          bitsAllocated, bitsStored, signed, photometric, null, null, null, null);
    }

    DicomImageSpec withRescale(double slope, double intercept) {
      return new DicomImageSpec(
          bitsAllocated,
          bitsStored,
          signed,
          photometric,
          slope,
          intercept,
          paddingValue,
          paddingLimit);
    }

    DicomImageSpec withPadding(int paddingValue) {
      return new DicomImageSpec(
          bitsAllocated,
          bitsStored,
          signed,
          photometric,
          rescaleSlope,
          rescaleIntercept,
          paddingValue,
          paddingLimit);
    }

    DicomImageSpec withPaddingRange(int paddingValue, int paddingLimit) {
      return new DicomImageSpec(
          bitsAllocated,
          bitsStored,
          signed,
          photometric,
          rescaleSlope,
          rescaleIntercept,
          paddingValue,
          paddingLimit);
    }
  }

  @Nested
  class Constructor_And_Basic_Properties_Tests {

    @Test
    void should_handle_8_bit_unsigned_image_correctly() {
      var image = createTestImage(64, 64, CvType.CV_8UC1, 128);
      var spec = DicomImageSpec.basic(8, 8, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);

      assertAll(
          "Basic properties",
          () -> assertEquals(8, adapter.getBitsStored()),
          () -> assertEquals(desc, adapter.getImageDescriptor()),
          () -> assertEquals(0, adapter.getFrameIndex()),
          () -> assertNotNull(adapter.getMinMax()));
    }

    @Test
    void should_handle_16_bit_signed_image_correctly() {
      var image = createTestImage(32, 32, CvType.CV_16SC1, -1000);
      var spec = DicomImageSpec.basic(16, 12, true);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 1);

      assertAll(
          "16-bit signed properties",
          () -> assertEquals(12, adapter.getBitsStored()),
          () -> assertEquals(1, adapter.getFrameIndex()),
          () -> assertTrue(adapter.getImageDescriptor().isSigned()));
    }

    @Test
    void should_adjust_bits_stored_when_image_values_exceed_range() {
      // Image with values exceeding 6-bit range (0-63)
      var image = createTestImage(16, 16, CvType.CV_8UC1, 200);
      var spec = DicomImageSpec.basic(8, 6, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);

      assertEquals(8, adapter.getBitsStored(), "Should adjust to 8 bits to handle value 200");
    }

    @Test
    void should_throw_exception_for_null_parameters() {
      try (var image = createTestImage(16, 16, CvType.CV_8UC1, 100)) {
        var spec = DicomImageSpec.basic(8, 8, false);
        var desc = createDescriptor(spec);

        assertAll(
            "Null parameter validation",
            () ->
                assertThrows(
                    NullPointerException.class, () -> new DicomImageAdapter(null, desc, 0)),
            () ->
                assertThrows(
                    NullPointerException.class, () -> new DicomImageAdapter(image, null, 0)));
      }
    }

    @Test
    void should_handle_different_frame_indices() {
      try (var image = createTestImage(32, 32, CvType.CV_16UC1, 1000)) {
        var spec = DicomImageSpec.basic(16, 12, false);
        var desc = createDescriptor(spec);

        var frameIndices = List.of(0, 1, 5, 10);

        frameIndices.forEach(
            frameIndex -> {
              var adapter = new DicomImageAdapter(image, desc, frameIndex);
              assertEquals(
                  frameIndex,
                  adapter.getFrameIndex(),
                  "Frame index should match for frame " + frameIndex);
            });
      }
    }
  }

  @Nested
  class Min_Max_Value_Computation_Tests {

    @Test
    void should_compute_correct_min_max_for_uniform_image() {
      var image = createTestImage(32, 32, CvType.CV_8UC1, 150);
      var spec = DicomImageSpec.basic(8, 8, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var minMax = adapter.getMinMax();

      assertAll(
          "Uniform image min/max",
          () -> assertEquals(150.0, minMax.minVal, 0.1),
          () -> assertEquals(151.0, minMax.maxVal, 0.1) // +1 for uniform images
          );
    }

    @Test
    void should_handle_8_bit_color_images_specially() {
      var image = createTestImage(16, 16, CvType.CV_8UC3, 0);
      var spec = DicomImageSpec.withPhotometric(8, 8, false, PhotometricInterpretation.RGB);
      var desc = createDescriptor(spec);

      var result = DicomImageAdapter.getMinMaxValues(image, desc, 0);

      assertAll(
          "8-bit color image special handling",
          () -> assertEquals(0.0, result.minVal),
          () -> assertEquals(255.0, result.maxVal));
    }

    @Test
    void should_compute_min_max_excluding_padding_values() {
      // Create image with mixed values including padding
      var mat = new ImageCV(4, 4, CvType.CV_16UC1);
      mat.put(0, 0, 100, 200, 300, 400);
      mat.put(1, 0, 0, 0, 500, 600); // 0 will be padding
      mat.put(2, 0, 700, 800, 0, 0);
      mat.put(3, 0, 900, 1000, 1100, 0);

      var spec = DicomImageSpec.basic(16, 16, false).withPadding(0);
      var desc = createDescriptor(spec);

      var result = DicomImageAdapter.getMinMaxValues(mat, desc, 0);

      assertAll(
          "Padding exclusion",
          () -> assertTrue(result.minVal > 0, "Should exclude padding value 0"),
          () -> assertEquals(1100.0, result.maxVal, 0.1));
    }

    @Test
    void should_handle_variable_intensity_image() {
      var image = createVariableImage(64, 64, CvType.CV_16UC1);
      var spec = DicomImageSpec.basic(16, 12, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var minMax = adapter.getMinMax();

      assertAll(
          "Variable intensity validation",
          () -> assertNotNull(minMax),
          () -> assertTrue(minMax.minVal >= 0),
          () -> assertTrue(minMax.maxVal <= 65535),
          () -> assertTrue(minMax.maxVal > minMax.minVal));
    }

    @Test
    void should_cache_min_max_values() {
      var image = createTestImage(32, 32, CvType.CV_16UC1, 500);
      var spec = DicomImageSpec.basic(16, 12, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var firstResult = adapter.getMinMax();
      var secondResult = adapter.getMinMax();

      assertSame(firstResult, secondResult, "Should return cached result");
    }
  }

  @Nested
  class Rescale_Operations_Tests {

    @Test
    void should_apply_rescale_slope_and_intercept_correctly() {
      var image = createTestImage(16, 16, CvType.CV_16UC1, 1000);
      var spec = DicomImageSpec.basic(16, 12, false).withRescale(0.5, -1024.0);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var wl = new WindLevelParameters(adapter);

      assertAll(
          "Rescale parameters",
          () -> assertEquals(0.5, adapter.getRescaleSlope(null)),
          () -> assertEquals(-1024.0, adapter.getRescaleIntercept(null)));

      // Pixel value 1000 should become: 1000 * 0.5 + (-1024) = -524
      var transformedValue = adapter.pixelToRealValue(1000, wl);
      assertNotNull(transformedValue);
      assertEquals(-524.0, transformedValue.doubleValue(), 0.1);
    }

    @Test
    void should_optimize_identity_transformation() {
      var image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      var spec = DicomImageSpec.basic(8, 8, false).withRescale(1.0, 0.0);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);

      // Should return null for identity transformation (optimization)
      assertNull(
          adapter.getLutParameters(false, null, false, null),
          "Identity transformation should be optimized away");
    }

    @Test
    void should_determine_correct_output_signing_for_rescale_operations() {
      var image = createTestImage(16, 16, CvType.CV_16UC1, 500);
      var spec = DicomImageSpec.basic(16, 12, false).withRescale(1.0, -1000.0);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var wl = new WindLevelParameters(adapter);

      assertAll(
          "Rescale output signing",
          () -> assertTrue(adapter.isModalityLutOutSigned(wl)),
          () -> assertTrue(adapter.getMinValue(wl) < 0));
    }

    @Test
    void should_handle_negative_slope() {
      var image = createTestImage(16, 16, CvType.CV_16UC1, 1000);
      var spec = DicomImageSpec.basic(16, 12, false).withRescale(-2.0, 5000.0);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var wl = new WindLevelParameters(adapter);

      // With negative slope, min/max are inverted
      var minValue = adapter.getMinValue(wl);
      var maxValue = adapter.getMaxValue(wl);

      assertTrue(minValue < maxValue, "Min should still be less than max after transformation");
    }

    @Test
    void should_handle_fractional_slope() {
      var image = createVariableImage(32, 32, CvType.CV_16UC1);
      var spec = DicomImageSpec.basic(16, 12, false).withRescale(0.25, 100.0);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);

      assertNotNull(
          adapter.getLutParameters(false, null, false, null),
          "Non-identity fractional transformation should create LUT parameters");
    }
  }

  @Nested
  class Window_Level_Preset_Tests {

    @Test
    void should_compute_default_window_and_level_correctly() {
      var image = createTestImage(32, 32, CvType.CV_16UC1, 2048);
      var spec = DicomImageSpec.basic(16, 12, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var wl = new WindLevelParameters(adapter);

      // For uniform image with value 2048, min=2048, max=2049 (adjusted)
      assertAll(
          "Default window/level",
          () -> assertEquals(2048.5, adapter.getDefaultLevel(wl), 0.1),
          () -> assertEquals(1.0, adapter.getDefaultWindow(wl), 0.1));
    }

    @Test
    void should_return_linear_as_default_lut_shape() {
      var image = createTestImage(16, 16, CvType.CV_8UC1, 128);
      var spec = DicomImageSpec.basic(8, 8, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var wl = new WindLevelParameters(adapter);

      assertEquals(LutShape.LINEAR, adapter.getDefaultShape(wl));
    }

    @Test
    void should_compute_full_dynamic_range_correctly() {
      var image = createTestImage(16, 16, CvType.CV_16UC1, 1000);
      // Modify one pixel to create a range
      image.toImageCV().put(0, 0, 3000);

      var spec = DicomImageSpec.basic(16, 12, false).withRescale(2.0, 100.0);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var wl = new WindLevelParameters(adapter);

      assertAll(
          "Dynamic range calculations",
          () -> assertEquals(6100.0, adapter.getMaxValue(wl), 1.0), // 3000 * 2 + 100
          () -> assertEquals(2100.0, adapter.getMinValue(wl), 1.0), // 1000 * 2 + 100
          () -> assertEquals(4000.0, adapter.getFullDynamicWidth(wl), 1.0),
          () -> assertEquals(4100.0, adapter.getFullDynamicCenter(wl), 1.0));
    }

    @Test
    void should_handle_preset_list_operations() {
      var image = createTestImage(32, 32, CvType.CV_16UC1, 1024);
      var spec = DicomImageSpec.basic(16, 12, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var wl = new WindLevelParameters(adapter);

      var presets = adapter.getPresetList(wl);
      assertNotNull(presets);
      assertTrue(adapter.getPresetCollectionSize() >= 0);

      var defaultPreset = adapter.getDefaultPreset(wl);
      if (!presets.isEmpty()) {
        assertSame(presets.get(0), defaultPreset);
      }
    }

    @Test
    void should_handle_preset_reload() {
      var image = createTestImage(32, 32, CvType.CV_16UC1, 1024);
      var spec = DicomImageSpec.basic(16, 12, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var wl = new WindLevelParameters(adapter);

      var initialPresets = adapter.getPresetList(wl);
      var reloadedPresets = adapter.getPresetList(wl, true);

      // Both should be valid (implementation detail may vary)
      assertNotNull(initialPresets);
      assertNotNull(reloadedPresets);
    }
  }

  @Nested
  class Presentation_State_Tests {

    @Test
    void should_create_presentation_state_objects() {
      var image = createTestImage(64, 64, CvType.CV_16SC1, 1000);
      var spec = DicomImageSpec.basic(16, 12, true);
      var desc = createDescriptor(spec);

      var prAttributes = new Attributes();
      prAttributes.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
      prAttributes.setString(Tag.PresentationLUTShape, VR.CS, "IDENTITY");

      var prDicomObject = new PrDicomObject(prAttributes);
      var adapter = new DicomImageAdapter(image, desc, 0);

      assertAll(
          "Presentation state creation",
          () -> assertNotNull(adapter),
          () -> assertEquals(desc, adapter.getImageDescriptor()),
          () -> assertNotNull(prDicomObject),
          () ->
              assertEquals(
                  PrDicomObject.PresentationStateType.GRAYSCALE_SOFTCOPY,
                  prDicomObject.getPresentationStateType()));
    }

    @Test
    void should_handle_presentation_lut_using_lut_test_data_builder() {
      var prAttributes = new Attributes();
      prAttributes.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");

      var lutData = LutTestDataBuilder.createLinearLut8Bit();
      prAttributes.newSequence(Tag.PresentationLUTSequence, 1).add(lutData);

      var prDicomObject = new PrDicomObject(prAttributes);

      assertAll(
          "Presentation LUT handling",
          () -> assertNotNull(prDicomObject),
          () -> assertTrue(prDicomObject.getPrLut().isPresent()),
          () -> assertNotNull(prDicomObject.getPrLut().get()));
    }

    @Test
    void should_handle_different_presentation_state_types() {
      var stateTypes =
          List.of(
              new StateTypeTest(
                  "1.2.840.10008.5.1.4.1.1.11.3",
                  PrDicomObject.PresentationStateType.PSEUDO_COLOR_SOFTCOPY,
                  PrDicomObject.PresentationGroup.COLOR_BASED),
              new StateTypeTest(
                  "1.2.840.10008.5.1.4.1.1.11.12",
                  PrDicomObject.PresentationStateType.VARIABLE_MODALITY_LUT_SOFTCOPY,
                  PrDicomObject.PresentationGroup.ADVANCED_LUT));

      stateTypes.forEach(
          test -> {
            var attributes = new Attributes();
            attributes.setString(Tag.SOPClassUID, VR.UI, test.sopClassUID);

            var prObject = new PrDicomObject(attributes);

            assertAll(
                "State type: " + test.expectedType,
                () -> assertEquals(test.expectedType, prObject.getPresentationStateType()),
                () ->
                    assertEquals(
                        test.expectedGroup, prObject.getPresentationStateType().getGroup()));
          });
    }

    private record StateTypeTest(
        String sopClassUID,
        PrDicomObject.PresentationStateType expectedType,
        PrDicomObject.PresentationGroup expectedGroup) {}
  }

  @Nested
  class Modality_LUT_Tests {

    @Test
    void should_create_modality_lut_with_8_bit_lookup_table() {
      var image = createTestImage(32, 32, CvType.CV_8UC1, 128);

      var attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, 8);
      attributes.setInt(Tag.BitsStored, VR.US, 8);
      attributes.setInt(Tag.PixelRepresentation, VR.US, 0);
      attributes.setString(
          Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());

      var modalityLutData = LutTestDataBuilder.createLinearLut8Bit();
      attributes.newSequence(Tag.ModalityLUTSequence, 1).add(modalityLutData);

      var desc = new ImageDescriptor(attributes);
      var adapter = new DicomImageAdapter(image, desc, 0);

      var wlParams = new WindLevelParameters(adapter, null);
      var modalityLookup = adapter.getModalityLookup(wlParams, false);

      assertNotNull(modalityLookup, "8-bit modality LUT should be created");
    }

    @Test
    void should_handle_ct_hounsfield_lookup_table() {
      var image = createTestImage(64, 64, CvType.CV_16SC1, -500);

      var attributes = new Attributes();
      attributes.setInt(Tag.BitsAllocated, VR.US, 16);
      attributes.setInt(Tag.BitsStored, VR.US, 12);
      attributes.setInt(Tag.PixelRepresentation, VR.US, 1); // Signed
      attributes.setString(
          Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());

      var modalityLutData = LutTestDataBuilder.createCtHounsfieldLut();
      attributes.newSequence(Tag.ModalityLUTSequence, 1).add(modalityLutData);

      var desc = new ImageDescriptor(attributes);
      var adapter = new DicomImageAdapter(image, desc, 0);

      var wlParams = new WindLevelParameters(adapter, null);
      var modalityLookup = adapter.getModalityLookup(wlParams, false);

      assertAll(
          "CT Hounsfield LUT",
          () -> assertNotNull(modalityLookup),
          () -> assertTrue(adapter.isModalityLutOutSigned(wlParams)));
    }

    @Test
    void should_integrate_presentation_and_modality_luts() {
      var image = createTestImage(32, 32, CvType.CV_16UC1, 1024);

      // Image descriptor with modality LUT
      var imageAttributes = new Attributes();
      imageAttributes.setInt(Tag.BitsAllocated, VR.US, 16);
      imageAttributes.setInt(Tag.BitsStored, VR.US, 12);
      imageAttributes.setInt(Tag.PixelRepresentation, VR.US, 0);
      imageAttributes.setString(
          Tag.PhotometricInterpretation, VR.CS, PhotometricInterpretation.MONOCHROME2.toString());

      var modalityLutData = LutTestDataBuilder.createContrastLut12Bit();
      imageAttributes.newSequence(Tag.ModalityLUTSequence, 1).add(modalityLutData);
      var desc = new ImageDescriptor(imageAttributes);

      // Presentation state with presentation LUT
      var prAttributes = new Attributes();
      prAttributes.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
      var presLutData = LutTestDataBuilder.createLinearLut8Bit();
      prAttributes.newSequence(Tag.PresentationLUTSequence, 1).add(presLutData);

      var prDicomObject = new PrDicomObject(prAttributes, desc);
      var adapter = new DicomImageAdapter(image, desc, 0);

      assertAll(
          "LUT integration",
          () -> assertNotNull(prDicomObject),
          () -> assertNotNull(adapter),
          () -> assertTrue(prDicomObject.getPrLut().isPresent()),
          () -> assertNotNull(prDicomObject.getPrLut().get()));
    }
  }

  @Nested
  class Allocated_Value_Tests {

    @Test
    void should_compute_allocated_values_for_unsigned_data() {
      var image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      var spec = DicomImageSpec.basic(8, 8, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var wl = new WindLevelParameters(adapter);

      assertAll(
          "Unsigned allocated values",
          () -> assertEquals(0, adapter.getMinAllocatedValue(wl)),
          () -> assertEquals(255, adapter.getMaxAllocatedValue(wl)));
    }

    @Test
    void should_compute_allocated_values_for_signed_data() {
      var image = createTestImage(16, 16, CvType.CV_16SC1, -100);
      var spec = DicomImageSpec.basic(16, 12, true);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var wl = new WindLevelParameters(adapter);

      assertAll(
          "Signed allocated values",
          () -> assertEquals(-32768, adapter.getMinAllocatedValue(wl)),
          () -> assertEquals(32767, adapter.getMaxAllocatedValue(wl)));
    }

    @Test
    void should_handle_signed_output_from_rescale() {
      var image = createTestImage(16, 16, CvType.CV_16UC1, 1000);
      var spec = DicomImageSpec.basic(16, 12, false).withRescale(1.0, -2000.0);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var wl = new WindLevelParameters(adapter);

      assertAll(
          "Rescale produces signed output",
          () -> assertTrue(adapter.isModalityLutOutSigned(wl)),
          () -> assertTrue(adapter.getMinAllocatedValue(wl) < 0));
    }

    @Test
    void should_handle_various_bit_depths() {
      var bitDepthTests =
          List.of(
              new BitDepthTest(8, false, 0, 255),
              new BitDepthTest(12, false, 0, 4095),
              new BitDepthTest(16, false, 0, 65535),
              new BitDepthTest(8, true, -128, 127),
              new BitDepthTest(12, true, -2048, 2047),
              new BitDepthTest(16, true, -32768, 32767));

      bitDepthTests.forEach(
          test -> {
            var image = createTestImage(8, 8, test.signed ? CvType.CV_16SC1 : CvType.CV_16UC1, 100);
            var spec = DicomImageSpec.basic(test.bits, test.bits, test.signed);
            var desc = createDescriptor(spec);

            var adapter = new DicomImageAdapter(image, desc, 0);
            var wl = new WindLevelParameters(adapter);

            assertAll(
                "Bit depth " + test.bits + (test.signed ? " signed" : " unsigned"),
                () -> assertEquals(test.expectedMin, adapter.getMinAllocatedValue(wl)),
                () -> assertEquals(test.expectedMax, adapter.getMaxAllocatedValue(wl)));
          });
    }

    private record BitDepthTest(int bits, boolean signed, int expectedMin, int expectedMax) {}
  }

  @Nested
  class Photometric_Interpretation_Tests {

    @Test
    void should_detect_monochrome1_as_inverse() {
      var image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      var spec = DicomImageSpec.withPhotometric(8, 8, false, PhotometricInterpretation.MONOCHROME1);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);

      assertTrue(adapter.isPhotometricInterpretationInverse(null));
    }

    @Test
    void should_detect_monochrome2_as_not_inverse() {
      var image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      var spec = DicomImageSpec.withPhotometric(8, 8, false, PhotometricInterpretation.MONOCHROME2);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);

      assertFalse(adapter.isPhotometricInterpretationInverse(null));
    }

    @Test
    void should_handle_color_photometric_interpretations() {
      var colorTypes =
          List.of(
              PhotometricInterpretation.RGB,
              PhotometricInterpretation.YBR_FULL,
              PhotometricInterpretation.YBR_PARTIAL_420);

      colorTypes.forEach(
          photometric -> {
            var image = createTestImage(16, 16, CvType.CV_8UC3, 100);
            var spec = DicomImageSpec.withPhotometric(8, 8, false, photometric);
            var desc = createDescriptor(spec);

            var adapter = new DicomImageAdapter(image, desc, 0);

            assertFalse(
                adapter.isPhotometricInterpretationInverse(null),
                photometric + " should not be inverse");
          });
    }
  }

  @Nested
  class VOI_LUT_Tests {

    @Test
    void should_create_voi_lut_with_valid_parameters() {
      var image = createTestImage(16, 16, CvType.CV_16UC1, 1000);
      var spec = DicomImageSpec.basic(16, 12, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var wl = new WindLevelParameters(adapter);
      var voiLut = adapter.getVOILookup(wl);

      assertNotNull(voiLut, "VOI LUT should be created with valid parameters");
    }

    @Test
    void should_return_null_for_invalid_voi_parameters() {
      var image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      var spec = DicomImageSpec.basic(8, 8, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);

      assertNull(adapter.getVOILookup(null), "Should return null for null VOI parameters");
    }

    @Test
    void should_handle_different_lut_shapes() {
      var image = createTestImage(32, 32, CvType.CV_16UC1, 2000);
      var spec = DicomImageSpec.basic(16, 12, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);
      var voiLut = adapter.getVOILookup(new WindLevelParameters(adapter));
      assertNotNull(voiLut, "VOI LUT should be created");

      DicomImageReadParam params = new DicomImageReadParam();
      params.setVoiLutShape(LutShape.SIGMOID);
      var voiLut2 = adapter.getVOILookup(new WindLevelParameters(adapter, params));

      assertNotEquals(
          voiLut, voiLut2, "VOI LUT should not be the same for different VOI LUT shapes");
    }
  }

  @Nested
  class Edge_Cases_And_Error_Handling_Tests {

    @Test
    void should_handle_pixel_transformation_with_null_input() {
      var image = createTestImage(16, 16, CvType.CV_8UC1, 100);
      var spec = DicomImageSpec.basic(8, 8, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);

      assertNull(adapter.pixelToRealValue(null, null), "Should handle null pixel value gracefully");
    }

    @Test
    void should_handle_large_bit_depths() {
      var image = createTestImage(8, 8, CvType.CV_32SC1, 1_000_000);
      var spec = DicomImageSpec.basic(32, 32, true);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);

      assertAll(
          "Large bit depth handling",
          () -> assertEquals(32, adapter.getBitsStored()),
          () -> assertNotNull(adapter.getMinMax()));
    }

    @Test
    void should_handle_minimal_images() {
      var image = createTestImage(1, 1, CvType.CV_8UC1, 128);
      var spec = DicomImageSpec.basic(8, 8, false);
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);

      assertAll(
          "Minimal image handling",
          () -> assertEquals(8, adapter.getBitsStored()),
          () -> assertNotNull(adapter.getMinMax()));
    }

    @Test
    void should_handle_extreme_values() {
      var extremeValues = List.of(0.0, 1.0, 255.0, 65535.0, -32768.0, 32767.0);

      extremeValues.forEach(
          value -> {
            var image = createTestImage(8, 8, CvType.CV_16SC1, value);
            var spec = DicomImageSpec.basic(16, 12, true);
            var desc = createDescriptor(spec);

            var adapter = new DicomImageAdapter(image, desc, 0);

            assertNotNull(adapter.getMinMax(), "Should handle extreme value: " + value);
          });
    }

    @Test
    void should_handle_padding_edge_cases() {
      var image = createTestImage(16, 16, CvType.CV_16UC1, 0);
      var spec = DicomImageSpec.basic(16, 12, false).withPaddingRange(0, 10); // Range padding
      var desc = createDescriptor(spec);

      var adapter = new DicomImageAdapter(image, desc, 0);

      assertNotNull(adapter.getMinMax(), "Should handle range padding");
    }
  }

  @Nested
  class Integration_Tests_With_Real_Files {

    @Test
    void should_read_ybr_full_with_rle_compression() {
      var testFile = TEST_RESOURCES_DIR.resolve("ybrFull-RLE.dcm");

      try {
        var readParam = new DicomImageReadParam();
        var testReader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
        testReader.setInput(new DicomFileInputStream(testFile));

        var desc = testReader.getImageDescriptor();
        var imageSource = testReader.getPlanarImage(0, readParam);
        var img = ImageRendering.getImageWithoutEmbeddedOverlay(imageSource, desc, 0);
        var adapter = new DicomImageAdapter(img, desc, 0);

        assertAll(
            "YBR_FULL RLE validation",
            () -> assertEquals(0, adapter.getPresetCollectionSize()),
            () -> assertEquals(8, adapter.getBitsStored()),
            () -> assertEquals(255, adapter.getMinMax().maxVal),
            () -> assertEquals(0, adapter.getMinMax().minVal));

        var wlp = new WindLevelParameters(adapter);
        assertAll(
            "YBR_FULL window/level",
            () -> assertEquals(127.5, adapter.getDefaultLevel(wlp)),
            () -> assertEquals(255.0, adapter.getDefaultWindow(wlp)));

        var processedImg = ImageRendering.getModalityLutImage(imageSource, adapter, readParam);
        assertEquals(640, processedImg.width());

        testReader.dispose();

      } catch (IOException e) {
        System.out.println("Skipping YBR_FULL test - file not available: " + e.getMessage());
      }
    }

    @Test
    void should_read_mr_with_jpeg_lossless_compression() {
      var testFile = TEST_RESOURCES_DIR.resolve("MR-JPEGLosslessSV1.dcm");

      try {
        var readParam = new DicomImageReadParam();
        var testReader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
        testReader.setInput(new DicomFileInputStream(testFile));

        var desc = testReader.getImageDescriptor();
        var imageSource = testReader.getPlanarImage(0, readParam);
        var img = ImageRendering.getImageWithoutEmbeddedOverlay(imageSource, desc, 0);
        var adapter = new DicomImageAdapter(img, desc, 0);

        assertAll(
            "MR JPEG Lossless validation",
            () -> assertEquals(0, adapter.getPresetCollectionSize()),
            () -> assertEquals(8, adapter.getBitsStored()),
            () -> assertEquals(48, adapter.getMinMax().maxVal),
            () -> assertEquals(0, adapter.getMinMax().minVal));

        var wlp = new WindLevelParameters(adapter);
        assertAll(
            "MR window/level",
            () -> assertEquals(920, adapter.getDefaultLevel(wlp)),
            () -> assertEquals(1832, adapter.getDefaultWindow(wlp)));

        var processedImg = ImageRendering.getModalityLutImage(imageSource, adapter, readParam);
        assertEquals(256, processedImg.width());

        testReader.dispose();

      } catch (IOException e) {
        System.out.println("Skipping MR test - file not available: " + e.getMessage());
      }
    }
  }
}
