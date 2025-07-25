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

import java.util.Optional;
import java.util.OptionalDouble;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.data.PrDicomObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.weasis.opencv.op.lut.LutShape;

class WindLevelParametersTest {

  @Mock private DicomImageAdapter adapter;

  @Mock private DicomImageReadParam params;

  @Mock private PrDicomObject presentationState;

  @Mock private LutShape lutShape;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    // Setup default adapter behavior
    when(adapter.getDefaultWindow(any())).thenReturn(100.0);
    when(adapter.getDefaultLevel(any())).thenReturn(50.0);
    when(adapter.getDefaultShape(any())).thenReturn(LutShape.LINEAR);
    when(adapter.getMinValue(any())).thenReturn(0.0);
    when(adapter.getMaxValue(any())).thenReturn(255.0);
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Single parameter constructor should use default values")
    void shouldUseDefaultValuesWithSingleParameterConstructor() {
      WindLevelParameters parameters = new WindLevelParameters(adapter);

      assertEquals(100.0, parameters.getWindow());
      assertEquals(50.0, parameters.getLevel());
      assertEquals(
          0.0,
          parameters.getLevelMin()); // min(level - window/2, adapter.getMinValue) = min(0, 0) = 0
      assertEquals(
          255.0,
          parameters
              .getLevelMax()); // max(level + window/2, adapter.getMaxValue) = max(100, 255) = 255
      assertTrue(parameters.isPixelPadding());
      assertFalse(parameters.isInverseLut());
      assertFalse(parameters.isFillOutsideLutRange());
      assertFalse(parameters.isAllowWinLevelOnColorImage());
      assertEquals(LutShape.LINEAR, parameters.getLutShape());
      assertNull(parameters.getPresentationState());
    }

    @Test
    @DisplayName("Should throw NullPointerException when adapter is null")
    void shouldThrowNullPointerExceptionWhenAdapterIsNull() {
      assertThrows(NullPointerException.class, () -> new WindLevelParameters(null));
      assertThrows(NullPointerException.class, () -> new WindLevelParameters(null, params));
    }

    @Test
    @DisplayName(
        "Two parameter constructor with null params should behave like single parameter constructor")
    void shouldBehaveLikeSingleParameterConstructorWhenParamsIsNull() {
      WindLevelParameters parameters = new WindLevelParameters(adapter, null);

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
  @DisplayName("Parameter Override Tests")
  class ParameterOverrideTests {

    @Test
    @DisplayName("Should override boolean parameters from DicomImageReadParam")
    void shouldOverrideBooleanParameters() {
      when(params.getFillOutsideLutRange()).thenReturn(Optional.of(true));
      when(params.getApplyWindowLevelToColorImage()).thenReturn(Optional.of(true));
      when(params.getApplyPixelPadding()).thenReturn(Optional.of(false));
      when(params.getInverseLut()).thenReturn(Optional.of(true));

      WindLevelParameters parameters = new WindLevelParameters(adapter, params);

      assertTrue(parameters.isFillOutsideLutRange());
      assertTrue(parameters.isAllowWinLevelOnColorImage());
      assertFalse(parameters.isPixelPadding());
      assertTrue(parameters.isInverseLut());
    }

    @Test
    @DisplayName("Should use default values when optional parameters are empty")
    void shouldUseDefaultValuesWhenOptionalParametersAreEmpty() {
      when(params.getFillOutsideLutRange()).thenReturn(Optional.empty());
      when(params.getApplyWindowLevelToColorImage()).thenReturn(Optional.empty());
      when(params.getApplyPixelPadding()).thenReturn(Optional.empty());
      when(params.getInverseLut()).thenReturn(Optional.empty());

      WindLevelParameters parameters = new WindLevelParameters(adapter, params);

      assertFalse(parameters.isFillOutsideLutRange());
      assertFalse(parameters.isAllowWinLevelOnColorImage());
      assertTrue(parameters.isPixelPadding());
      assertFalse(parameters.isInverseLut());
    }

    @Test
    @DisplayName("Should override window and level from parameters")
    void shouldOverrideWindowAndLevelFromParameters() {
      when(params.getWindowWidth()).thenReturn(OptionalDouble.of(200.0));
      when(params.getWindowCenter()).thenReturn(OptionalDouble.of(128.0));

      WindLevelParameters parameters = new WindLevelParameters(adapter, params);

      assertEquals(200.0, parameters.getWindow());
      assertEquals(128.0, parameters.getLevel());
    }

    @Test
    @DisplayName("Should use adapter defaults when window/level parameters are empty")
    void shouldUseAdapterDefaultsWhenWindowLevelParametersAreEmpty() {
      when(params.getWindowWidth()).thenReturn(OptionalDouble.empty());
      when(params.getWindowCenter()).thenReturn(OptionalDouble.empty());

      WindLevelParameters parameters = new WindLevelParameters(adapter, params);

      assertEquals(100.0, parameters.getWindow());
      assertEquals(50.0, parameters.getLevel());
    }

    @Test
    @DisplayName("Should override LUT shape from parameters")
    void shouldOverrideLutShapeFromParameters() {
      when(params.getVoiLutShape()).thenReturn(Optional.of(LutShape.SIGMOID));

      WindLevelParameters parameters = new WindLevelParameters(adapter, params);

      assertEquals(LutShape.SIGMOID, parameters.getLutShape());
    }

    @Test
    @DisplayName("Should override presentation state from parameters")
    void shouldOverridePresentationStateFromParameters() {
      when(params.getPresentationState()).thenReturn(Optional.of(presentationState));

      WindLevelParameters parameters = new WindLevelParameters(adapter, params);

      assertEquals(presentationState, parameters.getPresentationState());
    }
  }

  @Nested
  @DisplayName("Level Min/Max Calculation Tests")
  class LevelMinMaxCalculationTests {

    @Test
    @DisplayName("Should calculate levelMin and levelMax based on window and level")
    void shouldCalculateLevelMinMaxBasedOnWindowAndLevel() {
      when(adapter.getDefaultWindow(any())).thenReturn(100.0);
      when(adapter.getDefaultLevel(any())).thenReturn(128.0);
      when(adapter.getMinValue(any())).thenReturn(-1000.0);
      when(adapter.getMaxValue(any())).thenReturn(3000.0);

      WindLevelParameters parameters = new WindLevelParameters(adapter);

      // levelMin = min(level - window/2, adapter.getMinValue) = min(128 - 50, -1000) = min(78,
      // -1000) = -1000
      // levelMax = max(level + window/2, adapter.getMaxValue) = max(128 + 50, 3000) = max(178,
      // 3000) = 3000
      assertEquals(-1000.0, parameters.getLevelMin());
      assertEquals(3000.0, parameters.getLevelMax());
    }

    @Test
    @DisplayName("Should use parameter values for levelMin and levelMax when specified")
    void shouldUseParameterValuesForLevelMinMax() {
      when(params.getLevelMin()).thenReturn(OptionalDouble.of(10.0));
      when(params.getLevelMax()).thenReturn(OptionalDouble.of(200.0));
      when(adapter.getMinValue(any())).thenReturn(0.0);
      when(adapter.getMaxValue(any())).thenReturn(255.0);

      WindLevelParameters parameters = new WindLevelParameters(adapter, params);

      // levelMin = min(paramMin, adapterMin) = min(10.0, 0.0) = 0.0
      // levelMax = max(paramMax, adapterMax) = max(200.0, 255.0) = 255.0
      assertEquals(0.0, parameters.getLevelMin());
      assertEquals(255.0, parameters.getLevelMax());
    }

    @Test
    @DisplayName("Should prioritize adapter values when they extend the range")
    void shouldPrioritizeAdapterValuesWhenTheyExtendRange() {
      when(params.getLevelMin()).thenReturn(OptionalDouble.of(50.0));
      when(params.getLevelMax()).thenReturn(OptionalDouble.of(150.0));
      when(adapter.getMinValue(any())).thenReturn(-100.0); // Extends below param min
      when(adapter.getMaxValue(any())).thenReturn(300.0); // Extends above param max

      WindLevelParameters parameters = new WindLevelParameters(adapter, params);

      assertEquals(-100.0, parameters.getLevelMin());
      assertEquals(300.0, parameters.getLevelMax());
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -100.0, 1000.0, Double.MAX_VALUE})
    @DisplayName("Should handle various window values")
    void shouldHandleVariousWindowValues(double windowValue) {
      when(adapter.getDefaultWindow(any())).thenReturn(windowValue);

      WindLevelParameters parameters = new WindLevelParameters(adapter);

      assertEquals(windowValue, parameters.getWindow());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -500.0, 500.0, Double.MIN_VALUE, Double.MAX_VALUE})
    @DisplayName("Should handle various level values")
    void shouldHandleVariousLevelValues(double levelValue) {
      when(adapter.getDefaultLevel(any())).thenReturn(levelValue);

      WindLevelParameters parameters = new WindLevelParameters(adapter);

      assertEquals(levelValue, parameters.getLevel());
    }

    @Test
    @DisplayName("Should handle zero window correctly")
    void shouldHandleZeroWindowCorrectly() {
      when(adapter.getDefaultWindow(any())).thenReturn(0.0);
      when(adapter.getDefaultLevel(any())).thenReturn(100.0);
      when(adapter.getMinValue(any())).thenReturn(0.0);
      when(adapter.getMaxValue(any())).thenReturn(255.0);

      WindLevelParameters parameters = new WindLevelParameters(adapter);

      assertEquals(0.0, parameters.getWindow());
      assertEquals(100.0, parameters.getLevel());
      // With zero window: levelMin = min(100 - 0/2, 0) = min(100, 0) = 0
      // levelMax = max(100 + 0/2, 255) = max(100, 255) = 255
      assertEquals(0.0, parameters.getLevelMin());
      assertEquals(255.0, parameters.getLevelMax());
    }

    @Test
    @DisplayName("Should handle negative window correctly")
    void shouldHandleNegativeWindowCorrectly() {
      when(adapter.getDefaultWindow(any())).thenReturn(-50.0);
      when(adapter.getDefaultLevel(any())).thenReturn(100.0);
      when(adapter.getMinValue(any())).thenReturn(0.0);
      when(adapter.getMaxValue(any())).thenReturn(255.0);

      WindLevelParameters parameters = new WindLevelParameters(adapter);

      assertEquals(-50.0, parameters.getWindow());
      assertEquals(100.0, parameters.getLevel());
      // With negative window: levelMin = min(100 - (-50)/2, 0) = min(125, 0) = 0
      // levelMax = max(100 + (-50)/2, 255) = max(75, 255) = 255
      assertEquals(0.0, parameters.getLevelMin());
      assertEquals(255.0, parameters.getLevelMax());
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should work with complete parameter set")
    void shouldWorkWithCompleteParameterSet() {
      when(params.getWindowWidth()).thenReturn(OptionalDouble.of(300.0));
      when(params.getWindowCenter()).thenReturn(OptionalDouble.of(150.0));
      when(params.getLevelMin()).thenReturn(OptionalDouble.of(-50.0));
      when(params.getLevelMax()).thenReturn(OptionalDouble.of(400.0));
      when(params.getFillOutsideLutRange()).thenReturn(Optional.of(true));
      when(params.getApplyWindowLevelToColorImage()).thenReturn(Optional.of(true));
      when(params.getApplyPixelPadding()).thenReturn(Optional.of(false));
      when(params.getInverseLut()).thenReturn(Optional.of(true));
      when(params.getVoiLutShape()).thenReturn(Optional.of(LutShape.SIGMOID));
      when(params.getPresentationState()).thenReturn(Optional.of(presentationState));

      when(adapter.getMinValue(any())).thenReturn(-100.0);
      when(adapter.getMaxValue(any())).thenReturn(500.0);

      WindLevelParameters parameters = new WindLevelParameters(adapter, params);

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
    @DisplayName("Should verify adapter interactions")
    void shouldVerifyAdapterInteractions() {
      new WindLevelParameters(adapter, params);

      verify(adapter, atLeastOnce()).getDefaultWindow(any());
      verify(adapter, atLeastOnce()).getDefaultLevel(any());
      verify(adapter, atLeastOnce()).getDefaultShape(any());
      verify(adapter, atLeastOnce()).getMinValue(any());
      verify(adapter, atLeastOnce()).getMaxValue(any());
    }

    @Test
    @DisplayName("Should verify parameter interactions when params provided")
    void shouldVerifyParameterInteractions() {
      when(params.getPresentationState()).thenReturn(Optional.empty());
      when(params.getFillOutsideLutRange()).thenReturn(Optional.empty());
      when(params.getApplyWindowLevelToColorImage()).thenReturn(Optional.empty());
      when(params.getApplyPixelPadding()).thenReturn(Optional.empty());
      when(params.getInverseLut()).thenReturn(Optional.empty());
      when(params.getWindowWidth()).thenReturn(OptionalDouble.empty());
      when(params.getWindowCenter()).thenReturn(OptionalDouble.empty());
      when(params.getVoiLutShape()).thenReturn(Optional.empty());
      when(params.getLevelMin()).thenReturn(OptionalDouble.empty());
      when(params.getLevelMax()).thenReturn(OptionalDouble.empty());

      new WindLevelParameters(adapter, params);

      verify(params).getPresentationState();
      verify(params).getFillOutsideLutRange();
      verify(params).getApplyWindowLevelToColorImage();
      verify(params).getApplyPixelPadding();
      verify(params).getInverseLut();
      verify(params).getWindowWidth();
      verify(params).getWindowCenter();
      verify(params).getVoiLutShape();
      verify(params).getLevelMin();
      verify(params).getLevelMax();
    }
  }
}
