/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.lut;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.stream.Stream;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.DicomImageReadParam;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;

@DisplayNameGeneration(ReplaceUnderscores.class)
class WindLevelParametersTest {

  @Mock private DicomImageAdapter adapter;
  @Mock private PrDicomObject presentationState;

  @BeforeEach
  void setUp() throws Exception {
    try (AutoCloseable ignored = MockitoAnnotations.openMocks(this)) {
      setupDefaultAdapterBehavior();
    }
  }

  private void setupDefaultAdapterBehavior() {
    when(adapter.getDefaultWindow(any(DefaultWlPresentation.class))).thenReturn(100.0);
    when(adapter.getDefaultLevel(any(DefaultWlPresentation.class))).thenReturn(50.0);
    when(adapter.getDefaultShape(any(DefaultWlPresentation.class))).thenReturn(LutShape.LINEAR);
    when(adapter.getMinValue(any(DefaultWlPresentation.class))).thenReturn(0.0);
    when(adapter.getMaxValue(any(DefaultWlPresentation.class))).thenReturn(255.0);
  }

  private DicomImageReadParam createParams() {
    // All parameters start as empty/default
    return new DicomImageReadParam();
  }

  @Nested
  class Constructor_tests {

    @Test
    void single_parameter_constructor_uses_default_values() {
      var parameters = new WindLevelParameters(adapter);

      assertEquals(100.0, parameters.getWindow());
      assertEquals(50.0, parameters.getLevel());
      assertEquals(0.0, parameters.getLevelMin()); // min(0, 0) = 0
      assertEquals(255.0, parameters.getLevelMax()); // max(100, 255) = 255
      assertTrue(parameters.isPixelPadding());
      assertFalse(parameters.isInverseLut());
      assertFalse(parameters.isFillOutsideLutRange());
      assertFalse(parameters.isAllowWinLevelOnColorImage());
      assertEquals(LutShape.LINEAR, parameters.getLutShape());
      assertNull(parameters.getPresentationState());
    }

    @Test
    void throws_null_pointer_exception_when_adapter_is_null() {
      var params = createParams();

      assertThrows(NullPointerException.class, () -> new WindLevelParameters(null));
      assertThrows(NullPointerException.class, () -> new WindLevelParameters(null, params));
    }

    @Test
    void two_parameter_constructor_with_null_params_behaves_like_single_parameter_constructor() {
      var parameters = new WindLevelParameters(adapter, null);

      assertEquals(100.0, parameters.getWindow());
      assertEquals(50.0, parameters.getLevel());
      assertTrue(parameters.isPixelPadding());
      assertFalse(parameters.isInverseLut());
      assertFalse(parameters.isFillOutsideLutRange());
      assertFalse(parameters.isAllowWinLevelOnColorImage());
      assertEquals(LutShape.LINEAR, parameters.getLutShape());
      assertNull(parameters.getPresentationState());
    }
  }

  @Nested
  class Parameter_override_tests {

    @Test
    void overrides_boolean_parameters_from_dicom_image_read_param() {
      var params = createParams();
      params.setFillOutsideLutRange(true);
      params.setApplyWindowLevelToColorImage(true);
      params.setApplyPixelPadding(false);
      params.setInverseLut(true);

      var parameters = new WindLevelParameters(adapter, params);

      assertTrue(parameters.isFillOutsideLutRange());
      assertTrue(parameters.isAllowWinLevelOnColorImage());
      assertFalse(parameters.isPixelPadding());
      assertTrue(parameters.isInverseLut());
    }

    @Test
    void uses_default_values_when_parameters_are_not_set() {
      var params = createParams();
      var parameters = new WindLevelParameters(adapter, params);

      assertFalse(parameters.isFillOutsideLutRange());
      assertFalse(parameters.isAllowWinLevelOnColorImage());
      assertTrue(parameters.isPixelPadding());
      assertFalse(parameters.isInverseLut());
    }

    @Test
    void overrides_window_and_level_from_parameters() {
      var params = createParams();
      params.setWindowWidth(200.0);
      params.setWindowCenter(128.0);

      var parameters = new WindLevelParameters(adapter, params);

      assertEquals(200.0, parameters.getWindow());
      assertEquals(128.0, parameters.getLevel());
    }

    @Test
    void uses_adapter_defaults_when_window_level_parameters_are_not_set() {
      var params = createParams();
      var parameters = new WindLevelParameters(adapter, params);

      assertEquals(100.0, parameters.getWindow());
      assertEquals(50.0, parameters.getLevel());
    }

    @Test
    void overrides_lut_shape_from_parameters() {
      var params = createParams();
      params.setVoiLutShape(LutShape.SIGMOID);

      var parameters = new WindLevelParameters(adapter, params);

      assertEquals(LutShape.SIGMOID, parameters.getLutShape());
    }

    @Test
    void overrides_presentation_state_from_parameters() {
      var params = createParams();
      params.setPresentationState(presentationState);

      var parameters = new WindLevelParameters(adapter, params);

      assertEquals(presentationState, parameters.getPresentationState());
    }
  }

  @Nested
  class Level_min_max_calculation_tests {

    @Test
    void calculates_level_min_and_max_based_on_window_and_level() {
      when(adapter.getDefaultWindow(any())).thenReturn(100.0);
      when(adapter.getDefaultLevel(any())).thenReturn(128.0);
      when(adapter.getMinValue(any())).thenReturn(-1000.0);
      when(adapter.getMaxValue(any())).thenReturn(3000.0);

      var parameters = new WindLevelParameters(adapter);

      // levelMin = min(128 - 50, -1000) = min(78, -1000) = -1000
      // levelMax = max(128 + 50, 3000) = max(178, 3000) = 3000
      assertEquals(-1000.0, parameters.getLevelMin());
      assertEquals(3000.0, parameters.getLevelMax());
    }

    @Test
    void uses_parameter_values_for_level_min_and_max_when_specified() {
      var params = createParams();
      params.setLevelMin(10.0);
      params.setLevelMax(200.0);

      var parameters = new WindLevelParameters(adapter, params);

      // levelMin = min(10.0, 0.0) = 0.0
      // levelMax = max(200.0, 255.0) = 255.0
      assertEquals(0.0, parameters.getLevelMin());
      assertEquals(255.0, parameters.getLevelMax());
    }

    @Test
    void prioritizes_adapter_values_when_they_extend_the_range() {
      var params = createParams();
      params.setLevelMin(50.0);
      params.setLevelMax(150.0);

      when(adapter.getMinValue(any())).thenReturn(-100.0);
      when(adapter.getMaxValue(any())).thenReturn(300.0);

      var parameters = new WindLevelParameters(adapter, params);

      assertEquals(-100.0, parameters.getLevelMin());
      assertEquals(300.0, parameters.getLevelMax());
    }

    @ParameterizedTest
    @CsvSource({
      "100.0, 50.0, 0.0, 255.0, 0.0, 255.0", // normal case
      "0.0, 100.0, 0.0, 255.0, 0.0, 255.0", // zero window
      "200.0, 128.0, -50.0, 300.0, -50.0, 300.0", // wide range
      "50.0, 0.0, -1000.0, 1000.0, -1000.0, 1000.0" // extreme range
    })
    void calculates_bounds_correctly_for_various_scenarios(
        double window,
        double level,
        double adapterMin,
        double adapterMax,
        double expectedMin,
        double expectedMax) {

      when(adapter.getDefaultWindow(any())).thenReturn(window);
      when(adapter.getDefaultLevel(any())).thenReturn(level);
      when(adapter.getMinValue(any())).thenReturn(adapterMin);
      when(adapter.getMaxValue(any())).thenReturn(adapterMax);

      var parameters = new WindLevelParameters(adapter);

      assertEquals(expectedMin, parameters.getLevelMin());
      assertEquals(expectedMax, parameters.getLevelMax());
    }
  }

  @Nested
  class Edge_case_tests {

    @ParameterizedTest
    @ValueSource(doubles = {-100.0, 0.0, 0.0001, 1000.0, Double.MAX_VALUE})
    void handles_various_window_values(double windowValue) {
      when(adapter.getDefaultWindow(any())).thenReturn(windowValue);

      var parameters = new WindLevelParameters(adapter);

      if (windowValue < MathUtil.DOUBLE_EPSILON) {
        windowValue = 1.0;
      }
      assertEquals(windowValue, parameters.getWindow());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -500.0, 500.0, Double.MIN_VALUE, Double.MAX_VALUE})
    void handles_various_level_values(double levelValue) {
      when(adapter.getDefaultLevel(any())).thenReturn(levelValue);

      var parameters = new WindLevelParameters(adapter);

      assertEquals(levelValue, parameters.getLevel());
    }

    static Stream<Arguments> specialWindowCases() {
      return Stream.of(
          Arguments.of(0.0, 100.0, 0.0, 255.0, 1.0, 255.0, "zero window"),
          Arguments.of(0.01, 3.0, 0.0, 255.0, 0.01, 255.0, "float window"),
          Arguments.of(-50.0, 100.0, 0.0, 255.0, 1.0, 255.0, "negative window"),
          Arguments.of(Double.NaN, 100.0, 0.0, 255.0, 1.0, 255.0, "NaN window defaults to 1.0"),
          Arguments.of(
              Double.POSITIVE_INFINITY,
              100.0,
              0.0,
              255.0,
              1.0,
              255.0,
              "infinite window defaults to 1.0"));
    }

    @ParameterizedTest
    @MethodSource("specialWindowCases")
    void handles_special_window_cases(
        double window,
        double level,
        double minVal,
        double maxVal,
        double expectedWindow,
        double expectedMax,
        String description) {
      when(adapter.getDefaultWindow(any())).thenReturn(window);
      when(adapter.getDefaultLevel(any())).thenReturn(level);
      when(adapter.getMinValue(any())).thenReturn(minVal);
      when(adapter.getMaxValue(any())).thenReturn(maxVal);

      var parameters = new WindLevelParameters(adapter);

      assertEquals(expectedWindow, parameters.getWindow(), description + " - window");
      assertEquals(level, parameters.getLevel(), description + " - level");
      assertEquals(0.0, parameters.getLevelMin(), description + " - levelMin");
      assertEquals(expectedMax, parameters.getLevelMax(), description + " - levelMax");
    }
  }

  @Nested
  class Equals_and_hash_code_tests {

    @Test
    void equals_returns_true_for_identical_parameters() {
      var params1 = new WindLevelParameters(adapter);
      var params2 = new WindLevelParameters(adapter);

      assertEquals(params1, params2);
      assertEquals(params1.hashCode(), params2.hashCode());
    }

    @Test
    void equals_returns_false_for_different_window_values() {
      when(adapter.getDefaultWindow(any())).thenReturn(100.0);
      var params1 = new WindLevelParameters(adapter);

      when(adapter.getDefaultWindow(any())).thenReturn(200.0);
      var params2 = new WindLevelParameters(adapter);

      assertNotEquals(params1, params2);
    }

    @Test
    void equals_handles_null_and_different_types() {
      var parameters = new WindLevelParameters(adapter);

      assertNotEquals(null, parameters);
      assertNotEquals("string", parameters);
      assertEquals(parameters, parameters);
    }

    @Test
    void equals_considers_all_fields() {
      var baseParams = createParams();
      baseParams.setWindowWidth(200.0);
      baseParams.setWindowCenter(100.0);
      baseParams.setApplyPixelPadding(false);

      var params1 = new WindLevelParameters(adapter, baseParams);

      var differentParams = createParams();
      differentParams.setWindowWidth(200.0);
      differentParams.setWindowCenter(100.0);
      differentParams.setApplyPixelPadding(true); // Different value

      var params2 = new WindLevelParameters(adapter, differentParams);

      assertNotEquals(params1, params2);
    }
  }

  @Nested
  class Integration_tests {

    @Test
    void works_with_complete_parameter_set() {
      var params = createParams();
      params.setWindowWidth(300.0);
      params.setWindowCenter(150.0);
      params.setLevelMin(-50.0);
      params.setLevelMax(400.0);
      params.setFillOutsideLutRange(true);
      params.setApplyWindowLevelToColorImage(true);
      params.setApplyPixelPadding(false);
      params.setInverseLut(true);
      params.setVoiLutShape(LutShape.SIGMOID);
      params.setPresentationState(presentationState);

      when(adapter.getMinValue(any())).thenReturn(-100.0);
      when(adapter.getMaxValue(any())).thenReturn(500.0);

      var parameters = new WindLevelParameters(adapter, params);

      assertEquals(300.0, parameters.getWindow());
      assertEquals(150.0, parameters.getLevel());
      assertEquals(-100.0, parameters.getLevelMin()); // min(-50, -100) = -100
      assertEquals(500.0, parameters.getLevelMax()); // max(400, 500) = 500
      assertTrue(parameters.isFillOutsideLutRange());
      assertTrue(parameters.isAllowWinLevelOnColorImage());
      assertFalse(parameters.isPixelPadding());
      assertTrue(parameters.isInverseLut());
      assertEquals(LutShape.SIGMOID, parameters.getLutShape());
      assertEquals(presentationState, parameters.getPresentationState());
    }

    @Test
    void toString_contains_all_important_values() {
      var params = createParams();
      params.setWindowWidth(200.0);
      params.setWindowCenter(100.0);

      var parameters = new WindLevelParameters(adapter, params);
      var string = parameters.toString();

      assertTrue(string.contains("window=200"));
      assertTrue(string.contains("level=100"));
      assertTrue(string.contains("levelMin="));
      assertTrue(string.contains("levelMax="));
      assertTrue(string.contains("pixelPadding="));
      assertTrue(string.contains("inverseLut="));
      assertTrue(string.contains("WindLevelParameters"));
    }

    @Test
    void verifies_essential_adapter_interactions() {
      var params = createParams();
      new WindLevelParameters(adapter, params);

      verify(adapter).getDefaultWindow(any(DefaultWlPresentation.class));
      verify(adapter).getDefaultLevel(any(DefaultWlPresentation.class));
      verify(adapter).getDefaultShape(any(DefaultWlPresentation.class));
      verify(adapter).getMinValue(any(DefaultWlPresentation.class));
      verify(adapter).getMaxValue(any(DefaultWlPresentation.class));
    }

    static Stream<Arguments> lutShapeTestCases() {
      return Stream.of(
          Arguments.of(LutShape.LINEAR, "linear"),
          Arguments.of(LutShape.SIGMOID, "sigmoid"),
          Arguments.of(LutShape.SIGMOID_NORM, "sigmoid normalized"),
          Arguments.of(LutShape.LOG, "logarithmic"),
          Arguments.of(LutShape.LOG_INV, "inverse logarithmic"));
    }

    @ParameterizedTest
    @MethodSource("lutShapeTestCases")
    void handles_all_lut_shapes(LutShape shape, String description) {
      var params = createParams();
      params.setVoiLutShape(shape);

      var parameters = new WindLevelParameters(adapter, params);

      assertEquals(shape, parameters.getLutShape(), "Should handle " + description);
    }
  }

  @Nested
  class Real_world_scenarios {

    @Test
    void ct_scan_typical_values() {
      // Typical CT scan values
      when(adapter.getDefaultWindow(any())).thenReturn(400.0);
      when(adapter.getDefaultLevel(any())).thenReturn(40.0);
      when(adapter.getMinValue(any())).thenReturn(-1024.0);
      when(adapter.getMaxValue(any())).thenReturn(3071.0);

      var parameters = new WindLevelParameters(adapter);

      assertEquals(400.0, parameters.getWindow());
      assertEquals(40.0, parameters.getLevel());
      assertEquals(-1024.0, parameters.getLevelMin());
      assertEquals(3071.0, parameters.getLevelMax());
    }

    @Test
    void mri_scan_typical_values() {
      // Typical MRI values
      when(adapter.getDefaultWindow(any())).thenReturn(1000.0);
      when(adapter.getDefaultLevel(any())).thenReturn(500.0);
      when(adapter.getMinValue(any())).thenReturn(0.0);
      when(adapter.getMaxValue(any())).thenReturn(4095.0);

      var parameters = new WindLevelParameters(adapter);

      assertEquals(1000.0, parameters.getWindow());
      assertEquals(500.0, parameters.getLevel());
      assertEquals(0.0, parameters.getLevelMin());
      assertEquals(4095.0, parameters.getLevelMax());
    }

    @Test
    void x_ray_8_bit_typical_values() {
      // Typical X-ray 8-bit values
      when(adapter.getDefaultWindow(any())).thenReturn(255.0);
      when(adapter.getDefaultLevel(any())).thenReturn(128.0);
      when(adapter.getMinValue(any())).thenReturn(0.0);
      when(adapter.getMaxValue(any())).thenReturn(255.0);

      var parameters = new WindLevelParameters(adapter);

      assertEquals(255.0, parameters.getWindow(), 0.5);
      assertEquals(128.0, parameters.getLevel(), 0.5);
      assertEquals(0.0, parameters.getLevelMin(), 0.5);
      assertEquals(255.0, parameters.getLevelMax(), 0.5);
    }
  }
}
