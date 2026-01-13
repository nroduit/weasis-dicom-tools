/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.stream.Stream;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageTypeSpecifier;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.data.PrDicomObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.opencv.op.lut.LutShape;

/**
 * Test class for {@link DicomImageReadParam}.
 *
 * <p>This test class validates DICOM-specific image read parameters including window/level
 * operations, VOI LUT transformations, presentation state handling, and overlay configuration using
 * real test data structures.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomImageReadParamTest {

  private DicomImageReadParam param;

  @BeforeEach
  void setUp() {
    param = new DicomImageReadParam();
  }

  @Nested
  class Constructor_Tests {

    @Test
    void default_constructor_should_initialize_with_proper_defaults() {
      assertNotNull(param);
      assertTrue(param.canSetSourceRenderSize());

      assertAll(
          "Window/Level defaults",
          () -> assertTrue(param.getWindowCenter().isEmpty()),
          () -> assertTrue(param.getWindowWidth().isEmpty()),
          () -> assertTrue(param.getLevelMin().isEmpty()),
          () -> assertTrue(param.getLevelMax().isEmpty()));

      assertAll(
          "LUT and boolean defaults",
          () -> assertTrue(param.getVoiLutShape().isEmpty()),
          () -> assertTrue(param.getApplyPixelPadding().isEmpty()),
          () -> assertTrue(param.getInverseLut().isEmpty()),
          () -> assertTrue(param.getFillOutsideLutRange().isEmpty()),
          () -> assertTrue(param.getApplyWindowLevelToColorImage().isEmpty()),
          () -> assertTrue(param.getKeepRgbForLossyJpeg().isEmpty()),
          () -> assertTrue(param.getReleaseImageAfterProcessing().isEmpty()));

      assertAll(
          "Index and state defaults",
          () -> assertEquals(0, param.getWindowIndex()),
          () -> assertEquals(0, param.getVoiLUTIndex()),
          () -> assertTrue(param.getPresentationState().isEmpty()));

      assertAll(
          "Overlay defaults",
          () -> assertEquals(0xf, param.getOverlayActivationMask()),
          () -> assertEquals(0xffff, param.getOverlayGrayscaleValue()),
          () -> assertTrue(param.getOverlayColor().isEmpty()));
    }

    @Test
    void copy_constructor_should_preserve_source_parameters() {
      var baseParam = new ImageReadParam();
      var sourceRegion = new Rectangle(10, 20, 100, 200);
      var renderSize = new Dimension(320, 240);

      baseParam.setSourceRegion(sourceRegion);

      var dicomParam = new DicomImageReadParam(baseParam);
      dicomParam.setSourceRenderSize(renderSize);

      assertAll(
          "Copy constructor verification",
          () -> assertNotNull(dicomParam),
          () -> assertTrue(dicomParam.canSetSourceRenderSize()),
          () -> assertEquals(sourceRegion, dicomParam.getSourceRegion()),
          () -> assertEquals(renderSize, dicomParam.getSourceRenderSize()));
    }

    @Test
    void copy_constructor_should_handle_empty_base_param() {
      var baseParam = new ImageReadParam();
      var dicomParam = new DicomImageReadParam(baseParam);

      assertAll(
          "Empty base parameter handling",
          () -> assertNotNull(dicomParam),
          () -> assertNull(dicomParam.getSourceRegion()),
          () -> assertNull(dicomParam.getSourceRenderSize()));
    }
  }

  @Nested
  class Window_Level_Parameter_Tests {

    @Test
    void window_level_parameters_should_be_settable_and_retrievable() {
      assertAll(
          "Initially empty",
          () -> assertTrue(param.getWindowCenter().isEmpty()),
          () -> assertTrue(param.getWindowWidth().isEmpty()));

      param.setWindowCenter(100.0);
      param.setWindowWidth(200.0);

      assertAll(
          "After setting values",
          () -> assertTrue(param.getWindowCenter().isPresent()),
          () -> assertEquals(100.0, param.getWindowCenter().getAsDouble(), 0.001),
          () -> assertTrue(param.getWindowWidth().isPresent()),
          () -> assertEquals(200.0, param.getWindowWidth().getAsDouble(), 0.001));

      param.setWindowCenter(null);
      param.setWindowWidth(null);

      assertAll(
          "After setting to null",
          () -> assertTrue(param.getWindowCenter().isEmpty()),
          () -> assertTrue(param.getWindowWidth().isEmpty()));
    }

    @Test
    void level_min_max_parameters_should_be_settable_and_retrievable() {
      assertAll(
          "Initially empty",
          () -> assertTrue(param.getLevelMin().isEmpty()),
          () -> assertTrue(param.getLevelMax().isEmpty()));

      param.setLevelMin(-1000.0);
      param.setLevelMax(3000.0);

      assertAll(
          "After setting values",
          () -> assertTrue(param.getLevelMin().isPresent()),
          () -> assertEquals(-1000.0, param.getLevelMin().getAsDouble(), 0.001),
          () -> assertTrue(param.getLevelMax().isPresent()),
          () -> assertEquals(3000.0, param.getLevelMax().getAsDouble(), 0.001));

      param.setLevelMin(null);
      param.setLevelMax(null);

      assertAll(
          "After setting to null",
          () -> assertTrue(param.getLevelMin().isEmpty()),
          () -> assertTrue(param.getLevelMax().isEmpty()));
    }

    @ParameterizedTest
    @MethodSource("extremeDoubleValues")
    void window_level_parameters_should_handle_extreme_values(double value) {
      param.setWindowCenter(value);
      param.setWindowWidth(Math.abs(value)); // Width should be positive

      assertEquals(value, param.getWindowCenter().getAsDouble());
      assertEquals(Math.abs(value), param.getWindowWidth().getAsDouble());
    }

    @ParameterizedTest
    @MethodSource("realisticDicomRanges")
    void level_min_max_should_handle_realistic_dicom_values(
        String modality, double min, double max) {
      param.setLevelMin(min);
      param.setLevelMax(max);

      assertAll(
          "Realistic " + modality + " values",
          () -> assertEquals(min, param.getLevelMin().getAsDouble()),
          () -> assertEquals(max, param.getLevelMax().getAsDouble()));
    }

    private static Stream<Double> extremeDoubleValues() {
      return Stream.of(Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_VALUE, 0.0, 1.0, -1.0);
    }

    private static Stream<Arguments> realisticDicomRanges() {
      return Stream.of(
          Arguments.of("CT Hounsfield", -1024.0, 3071.0),
          Arguments.of("MR 12-bit", 0.0, 4095.0),
          Arguments.of("X-ray 16-bit", 0.0, 65535.0),
          Arguments.of("US 8-bit", 0.0, 255.0));
    }
  }

  @Nested
  class VOI_LUT_Parameter_Tests {

    @Test
    void voi_lut_shape_should_be_settable_and_retrievable() {
      assertTrue(param.getVoiLutShape().isEmpty());

      param.setVoiLutShape(LutShape.LINEAR);
      assertAll(
          "LINEAR shape",
          () -> assertTrue(param.getVoiLutShape().isPresent()),
          () -> assertEquals(LutShape.LINEAR, param.getVoiLutShape().get()));

      param.setVoiLutShape(LutShape.SIGMOID);
      assertAll(
          "SIGMOID shape",
          () -> assertTrue(param.getVoiLutShape().isPresent()),
          () -> assertEquals(LutShape.SIGMOID, param.getVoiLutShape().get()));

      param.setVoiLutShape(null);
      assertTrue(param.getVoiLutShape().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LINEAR", "SIGMOID", "LOG", "LOG_INV"})
    void should_handle_all_lut_shape_types(String shapeName) {
      var shape = LutShape.getLutShape(shapeName);
      param.setVoiLutShape(shape);

      assertAll(
          "LUT shape " + shapeName,
          () -> assertTrue(param.getVoiLutShape().isPresent()),
          () -> assertEquals(shape, param.getVoiLutShape().get()));
    }
  }

  @Nested
  class Boolean_Rendering_Option_Tests {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void boolean_rendering_options_should_be_settable_and_retrievable(boolean value) {
      setBooleanOptions(value);
      verifyBooleanOptions(value);
    }

    @Test
    void boolean_options_should_handle_null_values_correctly() {
      setBooleanOptions(true);
      verifyAllBooleanOptionsPresent();

      setBooleanOptions(null);
      verifyAllBooleanOptionsEmpty();
    }

    private void setBooleanOptions(Boolean value) {
      param.setApplyPixelPadding(value);
      param.setInverseLut(value);
      param.setFillOutsideLutRange(value);
      param.setApplyWindowLevelToColorImage(value);
      param.setKeepRgbForLossyJpeg(value);
      param.setReleaseImageAfterProcessing(value);
    }

    private void verifyBooleanOptions(boolean expected) {
      assertAll(
          "Boolean option values",
          () -> assertEquals(expected, param.getApplyPixelPadding().get()),
          () -> assertEquals(expected, param.getInverseLut().get()),
          () -> assertEquals(expected, param.getFillOutsideLutRange().get()),
          () -> assertEquals(expected, param.getApplyWindowLevelToColorImage().get()),
          () -> assertEquals(expected, param.getKeepRgbForLossyJpeg().get()),
          () -> assertEquals(expected, param.getReleaseImageAfterProcessing().get()));
    }

    private void verifyAllBooleanOptionsPresent() {
      assertAll(
          "All boolean options present",
          () -> assertTrue(param.getApplyPixelPadding().isPresent()),
          () -> assertTrue(param.getInverseLut().isPresent()),
          () -> assertTrue(param.getFillOutsideLutRange().isPresent()),
          () -> assertTrue(param.getApplyWindowLevelToColorImage().isPresent()),
          () -> assertTrue(param.getKeepRgbForLossyJpeg().isPresent()),
          () -> assertTrue(param.getReleaseImageAfterProcessing().isPresent()));
    }

    private void verifyAllBooleanOptionsEmpty() {
      assertAll(
          "All boolean options empty",
          () -> assertTrue(param.getApplyPixelPadding().isEmpty()),
          () -> assertTrue(param.getInverseLut().isEmpty()),
          () -> assertTrue(param.getFillOutsideLutRange().isEmpty()),
          () -> assertTrue(param.getApplyWindowLevelToColorImage().isEmpty()),
          () -> assertTrue(param.getKeepRgbForLossyJpeg().isEmpty()),
          () -> assertTrue(param.getReleaseImageAfterProcessing().isEmpty()));
    }
  }

  @Nested
  class Index_Parameter_Tests {

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "5, 5", "10, 10", "-5, 0", "-1, 0"})
    void index_parameters_should_clamp_negative_values_to_zero(int input, int expected) {
      param.setWindowIndex(input);
      param.setVoiLUTIndex(input);

      assertAll(
          "Index clamping",
          () -> assertEquals(expected, param.getWindowIndex()),
          () -> assertEquals(expected, param.getVoiLUTIndex()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 100, Integer.MAX_VALUE})
    void should_handle_large_positive_indices(int index) {
      param.setWindowIndex(index);
      param.setVoiLUTIndex(index);

      assertAll(
          "Large positive indices",
          () -> assertEquals(index, param.getWindowIndex()),
          () -> assertEquals(index, param.getVoiLUTIndex()));
    }
  }

  @Nested
  class Presentation_State_Tests {

    @Test
    void presentation_state_should_be_settable_and_retrievable() {
      assertTrue(param.getPresentationState().isEmpty());

      var presentationState = createTestPresentationState();

      param.setPresentationState(presentationState);
      assertAll(
          "Presentation state set",
          () -> assertTrue(param.getPresentationState().isPresent()),
          () -> assertEquals(presentationState, param.getPresentationState().get()));

      param.setPresentationState(null);
      assertTrue(param.getPresentationState().isEmpty());
    }

    @Test
    void should_handle_various_presentation_state_types() {
      var grayscalePr = createPresentationState("1.2.840.10008.5.1.4.1.1.11.1");
      var colorPr = createPresentationState("1.2.840.10008.5.1.4.1.1.11.3");

      param.setPresentationState(grayscalePr);
      assertEquals(grayscalePr, param.getPresentationState().get());

      param.setPresentationState(colorPr);
      assertEquals(colorPr, param.getPresentationState().get());
    }

    private PrDicomObject createTestPresentationState() {
      return createPresentationState("1.2.840.10008.5.1.4.1.1.11.1");
    }

    private PrDicomObject createPresentationState(String sopClassUid) {
      var attributes = new Attributes();
      attributes.setString(Tag.SOPClassUID, VR.UI, sopClassUid);
      attributes.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5." + System.currentTimeMillis());
      attributes.setString(Tag.PresentationCreationDate, VR.DA, "20240101");
      attributes.setString(Tag.PresentationCreationTime, VR.TM, "120000");
      return new PrDicomObject(attributes);
    }
  }

  @Nested
  class Overlay_Parameter_Tests {

    @Test
    void overlay_parameters_should_be_settable_and_retrievable() {
      param.setOverlayActivationMask(0x5);
      assertEquals(0x5, param.getOverlayActivationMask());

      param.setOverlayGrayscaleValue(0x8000);
      assertEquals(0x8000, param.getOverlayGrayscaleValue());

      assertTrue(param.getOverlayColor().isEmpty());

      var red = Color.RED;
      param.setOverlayColor(red);
      assertAll(
          "Overlay color",
          () -> assertTrue(param.getOverlayColor().isPresent()),
          () -> assertEquals(red, param.getOverlayColor().get()));

      param.setOverlayColor(null);
      assertTrue(param.getOverlayColor().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0x1, 0xf, 0xff, 0x0, 0xffff, 0x5555, 0xaaaa})
    void overlay_activation_mask_should_handle_various_bit_patterns(int mask) {
      param.setOverlayActivationMask(mask);
      assertEquals(mask, param.getOverlayActivationMask());
    }

    @ParameterizedTest
    @ValueSource(ints = {0x0000, 0xffff, 0x8000, 0x7fff, 0x1234, 0xabcd})
    void overlay_grayscale_value_should_handle_full_range(int value) {
      param.setOverlayGrayscaleValue(value);
      assertEquals(value, param.getOverlayGrayscaleValue());
    }

    @ParameterizedTest
    @MethodSource("colorVariations")
    void overlay_color_should_handle_various_color_types(Color color) {
      param.setOverlayColor(color);
      assertAll(
          "Color verification",
          () -> assertTrue(param.getOverlayColor().isPresent()),
          () -> assertEquals(color, param.getOverlayColor().get()));
    }

    private static Stream<Color> colorVariations() {
      return Stream.of(
          Color.RED,
          Color.GREEN,
          Color.BLUE,
          Color.WHITE,
          Color.BLACK,
          new Color(128, 64, 192),
          new Color(128, 64, 192, 200), // with alpha
          new Color(128, 128, 128) // grayscale
          );
    }
  }

  @Nested
  class Unsupported_Operation_Tests {

    @ParameterizedTest
    @MethodSource("unsupportedOperations")
    void unsupported_operations_should_throw_exception(String operation, Runnable action) {
      var exception = assertThrows(UnsupportedOperationException.class, action::run);
      assertAll(
          "Exception for " + operation,
          () -> assertNotNull(exception.getMessage()),
          () ->
              assertTrue(
                  exception.getMessage().contains("Not compatible with the native DICOM Decoder")));
    }

    private static Stream<Arguments> unsupportedOperations() {
      var param = new DicomImageReadParam();
      return Stream.of(
          Arguments.of(
              "setDestinationOffset",
              (Runnable) () -> param.setDestinationOffset(new Point(10, 20))),
          Arguments.of("setController", (Runnable) () -> param.setController(p -> true)),
          Arguments.of(
              "setSourceBands", (Runnable) () -> param.setSourceBands(new int[] {0, 1, 2})),
          Arguments.of(
              "setSourceSubsampling", (Runnable) () -> param.setSourceSubsampling(2, 2, 0, 0)),
          Arguments.of(
              "setDestination",
              (Runnable)
                  () ->
                      param.setDestination(
                          new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_GRAY))),
          Arguments.of(
              "setDestinationBands", (Runnable) () -> param.setDestinationBands(new int[] {0})),
          Arguments.of(
              "setDestinationType",
              (Runnable)
                  () ->
                      param.setDestinationType(
                          ImageTypeSpecifier.createFromBufferedImageType(
                              BufferedImage.TYPE_BYTE_GRAY))),
          Arguments.of(
              "setSourceProgressivePasses",
              (Runnable) () -> param.setSourceProgressivePasses(1, 3)));
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void parameter_combinations_should_work_together() {
      // Set complete window/level configuration
      param.setWindowCenter(512.0);
      param.setWindowWidth(1024.0);
      param.setLevelMin(-1024.0);
      param.setLevelMax(3071.0);
      param.setVoiLutShape(LutShape.SIGMOID);
      param.setWindowIndex(2);
      param.setVoiLUTIndex(1);

      // Set rendering options
      param.setApplyPixelPadding(true);
      param.setInverseLut(false);
      param.setFillOutsideLutRange(true);
      param.setApplyWindowLevelToColorImage(false);
      param.setKeepRgbForLossyJpeg(true);
      param.setReleaseImageAfterProcessing(true);

      // Set overlay configuration
      param.setOverlayActivationMask(0x7);
      param.setOverlayGrayscaleValue(0xc000);
      param.setOverlayColor(Color.YELLOW);

      verifyCompleteConfiguration();
    }

    @Test
    void inheritance_from_image_read_param_should_preserve_basic_functionality() {
      assertNull(param.getSourceRegion());

      var region = new Rectangle(50, 100, 200, 300);
      param.setSourceRegion(region);
      assertEquals(region, param.getSourceRegion());

      var renderSize = new Dimension(100, 150);
      param.setSourceRenderSize(renderSize);
      assertEquals(renderSize, param.getSourceRenderSize());

      assertTrue(param.canSetSourceRenderSize());
    }

    @Test
    void should_handle_complete_dicom_workflow_scenario() {
      // Simulate CT scan processing
      param.setWindowCenter(400.0); // Typical CT abdomen window
      param.setWindowWidth(400.0);
      param.setLevelMin(-1024.0); // Air in Hounsfield units
      param.setLevelMax(3071.0); // Bone in Hounsfield units
      param.setApplyPixelPadding(true);
      param.setInverseLut(false); // Standard CT display

      // Add overlay for annotations
      param.setOverlayActivationMask(0x3); // First two overlay planes
      param.setOverlayColor(Color.GREEN);

      // Set source region for cropping
      param.setSourceRegion(new Rectangle(100, 100, 400, 400));

      verifyCtWorkflowConfiguration();
    }

    private void verifyCompleteConfiguration() {
      assertAll(
          "Window/Level configuration",
          () -> assertEquals(512.0, param.getWindowCenter().getAsDouble()),
          () -> assertEquals(1024.0, param.getWindowWidth().getAsDouble()),
          () -> assertEquals(-1024.0, param.getLevelMin().getAsDouble()),
          () -> assertEquals(3071.0, param.getLevelMax().getAsDouble()),
          () -> assertEquals(LutShape.SIGMOID, param.getVoiLutShape().get()),
          () -> assertEquals(2, param.getWindowIndex()),
          () -> assertEquals(1, param.getVoiLUTIndex()));

      assertAll(
          "Rendering options",
          () -> assertTrue(param.getApplyPixelPadding().get()),
          () -> assertFalse(param.getInverseLut().get()),
          () -> assertTrue(param.getFillOutsideLutRange().get()),
          () -> assertFalse(param.getApplyWindowLevelToColorImage().get()),
          () -> assertTrue(param.getKeepRgbForLossyJpeg().get()),
          () -> assertTrue(param.getReleaseImageAfterProcessing().get()));

      assertAll(
          "Overlay configuration",
          () -> assertEquals(0x7, param.getOverlayActivationMask()),
          () -> assertEquals(0xc000, param.getOverlayGrayscaleValue()),
          () -> assertEquals(Color.YELLOW, param.getOverlayColor().get()));
    }

    private void verifyCtWorkflowConfiguration() {
      assertAll(
          "CT workflow verification",
          () -> assertEquals(400.0, param.getWindowCenter().getAsDouble()),
          () -> assertEquals(400.0, param.getWindowWidth().getAsDouble()),
          () -> assertEquals(-1024.0, param.getLevelMin().getAsDouble()),
          () -> assertEquals(3071.0, param.getLevelMax().getAsDouble()),
          () -> assertTrue(param.getApplyPixelPadding().get()),
          () -> assertFalse(param.getInverseLut().get()),
          () -> assertEquals(0x3, param.getOverlayActivationMask()),
          () -> assertEquals(Color.GREEN, param.getOverlayColor().get()),
          () -> assertEquals(new Rectangle(100, 100, 400, 400), param.getSourceRegion()));
    }
  }
}
