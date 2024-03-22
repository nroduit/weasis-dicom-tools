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

import java.util.Optional;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.DicomImageReadParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WindLevelParametersTest {

  @Test
  @DisplayName("Verify WindLevelParameters constructor sets correct values with adapter only")
  void shouldSetCorrectValuesWithAdapterOnly() {
    DicomImageAdapter adapter = Mockito.mock(DicomImageAdapter.class);
    Mockito.when(adapter.getDefaultWindow(Mockito.any())).thenReturn(10.0);

    WindLevelParameters parameters = new WindLevelParameters(adapter);

    assertEquals(10.0, parameters.getWindow());
    assertEquals(0.0, parameters.getLevel());
    assertFalse(parameters.isFillOutsideLutRange());
    assertTrue(parameters.isPixelPadding());
    assertFalse(parameters.isInverseLut());
    assertFalse(parameters.isAllowWinLevelOnColorImage());
    assertNull(parameters.getPresentationState());
  }

  @Test
  @DisplayName("Verify WindLevelParameters constructor sets correct values with adapter and params")
  void shouldSetCorrectValuesWithAdapterAndParams() {
    DicomImageAdapter adapter = Mockito.mock(DicomImageAdapter.class);
    DicomImageReadParam params = Mockito.mock(DicomImageReadParam.class);
    Mockito.when(adapter.getDefaultWindow(Mockito.any())).thenReturn(10.0);
    Mockito.when(params.getFillOutsideLutRange()).thenReturn(Optional.of(true));
    Mockito.when(params.getApplyWindowLevelToColorImage()).thenReturn(Optional.of(true));
    Mockito.when(params.getApplyPixelPadding()).thenReturn(Optional.of(false));
    Mockito.when(params.getInverseLut()).thenReturn(Optional.of(true));

    WindLevelParameters parameters = new WindLevelParameters(adapter, params);

    assertEquals(10.0, parameters.getWindow());
    assertEquals(0.0, parameters.getLevel());
    assertTrue(parameters.isFillOutsideLutRange());
    assertFalse(parameters.isPixelPadding());
    assertTrue(parameters.isInverseLut());
    assertTrue(parameters.isAllowWinLevelOnColorImage());
    assertNull(parameters.getPresentationState());
  }
}
