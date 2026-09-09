/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.lut;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.PresentationStateLut;
import org.weasis.opencv.op.lut.WlPresentation;

/** Changes the process-wide provider, so it never runs in parallel with other preset tests. */
@Isolated
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ModalityPresetProviderTest {

  private static final WlPresentation WL =
      new WlPresentation() {
        @Override
        public boolean isPixelPadding() {
          return false;
        }

        @Override
        public PresentationStateLut getPresentationState() {
          return null;
        }
      };

  @AfterEach
  void restoreDefaultProvider() {
    PresetWindowLevel.setModalityPresetProvider(null);
  }

  @Test
  void default_provider_offers_no_configured_preset() {
    assertTrue(
        PresetWindowLevel.getModalityPresetProvider().getPresets(adapter("CT", 12), WL).isEmpty());
  }

  @Test
  void registered_provider_supplies_the_configured_presets_after_auto_level() {
    var lung = new PresetWindowLevel("Lung", 1500.0, -500.0, LutShape.LINEAR);
    lung.setId("weasis.ct.lung");
    PresetWindowLevel.setModalityPresetProvider((a, wl) -> List.of(lung));

    var presets = PresetWindowLevel.getPresetCollection(adapter("MR", 8), "[DICOM]", WL);

    assertEquals(2, presets.size());
    assertTrue(presets.get(0).isAutoLevel());
    assertSame(lung, presets.get(1));
  }

  @Test
  void failing_provider_keeps_the_presets_of_the_image() {
    PresetWindowLevel.setModalityPresetProvider(
        (a, wl) -> {
          throw new IllegalStateException("broken");
        });

    var presets = PresetWindowLevel.getPresetCollection(adapter("CT", 12), "[DICOM]", WL);

    assertEquals(1, presets.size());
    assertTrue(presets.get(0).isAutoLevel());
  }

  @Test
  void same_preset_is_matched_by_id_when_both_have_one_else_by_name() {
    var a = new PresetWindowLevel("Lung", 1500.0, -500.0, LutShape.LINEAR);
    var b = new PresetWindowLevel("Lung", 1600.0, -600.0, LutShape.LINEAR);
    assertTrue(a.isSamePreset(b));

    a.setId("weasis.ct.lung");
    assertTrue(a.isSamePreset(b));

    b.setId("site.ct.lung");
    assertFalse(a.isSamePreset(b));

    var renamed = new PresetWindowLevel("Poumon", 1500.0, -500.0, LutShape.LINEAR);
    renamed.setId("weasis.ct.lung");
    assertTrue(a.isSamePreset(renamed));
    assertFalse(a.isSamePreset(null));
  }

  @Test
  void fallback_default_goes_first_only_when_the_image_carries_no_window() {
    var soft = new PresetWindowLevel("Soft", 400.0, 40.0, LutShape.LINEAR);
    var brain = new PresetWindowLevel("Brain", 80.0, 40.0, LutShape.LINEAR);
    brain.setFallbackDefault(true);
    PresetWindowLevel.setModalityPresetProvider((a, wl) -> List.of(soft, brain));

    var withoutWindow = PresetWindowLevel.getPresetCollection(adapter("MR", 12), "[DICOM]", WL);
    var withWindow =
        PresetWindowLevel.getPresetCollection(
            adapter("MR", 12, List.of(300.0), List.of(600.0)), "[DICOM]", WL);

    assertEquals(List.of(brain, withoutWindow.get(1), soft), withoutWindow);
    assertTrue(withoutWindow.get(1).isAutoLevel());
    assertEquals("Default 1 [DICOM]", withWindow.get(0).getName());
    assertSame(brain, withWindow.get(3));
  }

  private static DicomImageAdapter adapter(String modality, int bitsStored) {
    return adapter(modality, bitsStored, List.of(), List.of());
  }

  private static DicomImageAdapter adapter(
      String modality, int bitsStored, List<Double> centers, List<Double> widths) {
    var adapter = mock(DicomImageAdapter.class);
    var descriptor = mock(ImageDescriptor.class);
    var voiLut = mock(VoiLutModule.class);
    when(voiLut.getWindowCenter()).thenReturn(centers);
    when(voiLut.getWindowWidth()).thenReturn(widths);
    when(voiLut.getWindowCenterWidthExplanation()).thenReturn(List.of());
    when(voiLut.getLut()).thenReturn(List.of());
    when(voiLut.getLutExplanation()).thenReturn(List.of());
    when(voiLut.getVoiLutFunction()).thenReturn(Optional.empty());

    when(adapter.getImageDescriptor()).thenReturn(descriptor);
    when(adapter.getFrameIndex()).thenReturn(0);
    when(adapter.getBitsStored()).thenReturn(bitsStored);
    when(adapter.getFullDynamicWidth(any())).thenReturn(4096.0);
    when(adapter.getFullDynamicCenter(any())).thenReturn(2048.0);
    when(descriptor.getModality()).thenReturn(modality);
    when(descriptor.getVoiLutForFrame(anyInt())).thenReturn(voiLut);
    return adapter;
  }
}
