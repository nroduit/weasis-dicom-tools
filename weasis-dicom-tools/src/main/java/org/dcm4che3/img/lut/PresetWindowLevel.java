/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.lut;

import java.awt.image.DataBuffer;
import java.lang.reflect.Array;
import java.util.*;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.LutShape.Function;
import org.weasis.opencv.op.lut.WlPresentation;

/**
 * Represents a window/level preset for DICOM image display.
 *
 * <p>This class encapsulates window/level values with associated LUT shape and keyboard shortcuts
 * for quick access to predefined display settings. It provides functionality to build presets from
 * DICOM data and XML configuration files.
 *
 * @author Nicolas Roduit
 */
public class PresetWindowLevel {
  private static final Logger LOGGER = LoggerFactory.getLogger(PresetWindowLevel.class);

  private static final int AUTO_LEVEL_KEY = 0x30;
  private static final int FIRST_PRESET_KEY = 0x31;
  private static final int SECOND_PRESET_KEY = 0x32;

  private static volatile ModalityPresetProvider modalityPresetProvider;

  private final String name;
  private final double window;
  private final double level;
  private final LutShape shape;
  private int keyCode = 0;
  private String id;
  private boolean fallbackDefault;

  /**
   * Creates a new window/level preset.
   *
   * @param name the display name of the preset
   * @param window the window width value
   * @param level the window center/level value
   * @param shape the LUT shape to apply
   * @throws NullPointerException if any parameter is null
   */
  public PresetWindowLevel(String name, Double window, Double level, LutShape shape) {
    this.name = Objects.requireNonNull(name);
    this.window = Objects.requireNonNull(window);
    this.level = Objects.requireNonNull(level);
    this.shape = Objects.requireNonNull(shape);
  }

  public String getName() {
    return name;
  }

  public double getWindow() {
    return window;
  }

  public double getLevel() {
    return level;
  }

  public LutShape getLutShape() {
    return shape;
  }

  public int getKeyCode() {
    return keyCode;
  }

  public double getMinBox() {
    return level - window / 2.0;
  }

  public double getMaxBox() {
    return level + window / 2.0;
  }

  public void setKeyCode(int keyCode) {
    this.keyCode = keyCode;
  }

  public boolean isAutoLevel() {
    return keyCode == AUTO_LEVEL_KEY;
  }

  /** Stable identifier of a configured preset; null for presets built from the image. */
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  /**
   * Whether this configured preset becomes the default, placed first, for an image that carries no
   * window/level and no VOI LUT. Values carried by the image always stay the default.
   */
  public boolean isFallbackDefault() {
    return fallbackDefault;
  }

  public void setFallbackDefault(boolean fallbackDefault) {
    this.fallbackDefault = fallbackDefault;
  }

  /**
   * Whether both presets are the same preset on different images: same id when both have one,
   * otherwise same name. Unlike {@link #equals(Object)}, the values may differ.
   */
  public boolean isSamePreset(PresetWindowLevel other) {
    if (other == null) {
      return false;
    }
    if (id != null && other.id != null) {
      return id.equals(other.id);
    }
    return name.equals(other.name);
  }

  /** Sets the source of the configured presets; null offers none. */
  public static void setModalityPresetProvider(ModalityPresetProvider provider) {
    modalityPresetProvider = provider;
  }

  public static ModalityPresetProvider getModalityPresetProvider() {
    ModalityPresetProvider provider = modalityPresetProvider;
    return provider == null ? (adapter, wl) -> List.of() : provider;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    var that = (PresetWindowLevel) o;
    return Double.compare(that.window, window) == 0
        && Double.compare(that.level, level) == 0
        && name.equals(that.name)
        && shape.equals(that.shape);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, window, level, shape);
  }

  /**
   * Builds a collection of window/level presets from DICOM image data.
   *
   * @param adapter the DICOM image adapter containing the data
   * @param type the type identifier to append to preset names
   * @param wl the window/level presentation state
   * @return list of presets extracted from the image data
   * @throws IllegalArgumentException if adapter or wl is null
   */
  public static List<PresetWindowLevel> getPresetCollection(
      DicomImageAdapter adapter, String type, WlPresentation wl) {
    Objects.requireNonNull(adapter, "adapter cannot be null");
    Objects.requireNonNull(wl, "wl cannot be null");

    var presetBuilder = new PresetCollectionBuilder(adapter, type, wl);
    return presetBuilder.buildPresets();
  }

  /** Creates a preset from LUT data by calculating equivalent window/level values. */
  public static PresetWindowLevel buildPresetFromLutData(
      DicomImageAdapter adapter, LookupTableCV voiLUTsData, WlPresentation wl, String explanation) {
    if (adapter == null || voiLUTsData == null || explanation == null) {
      return null;
    }

    var lutData = extractLutData(voiLUTsData);
    if (lutData == null) return null;

    var valueRange = calculateLutValueRange(voiLUTsData, lutData, adapter, wl);
    var windowLevel = calculateWindowLevelFromRange(valueRange);
    var newLutShape = new LutShape(voiLUTsData, explanation);

    return new PresetWindowLevel(
        newLutShape.toString(), windowLevel[0], windowLevel[1], newLutShape);
  }

  private static Object extractLutData(LookupTableCV voiLUTsData) {
    return switch (voiLUTsData.getDataType()) {
      case DataBuffer.TYPE_BYTE -> voiLUTsData.getByteData(0);
      case DataBuffer.TYPE_SHORT, DataBuffer.TYPE_USHORT -> voiLUTsData.getShortData(0);
      default -> null;
    };
  }

  private static int[] calculateLutValueRange(
      LookupTableCV voiLUTsData, Object lutData, DicomImageAdapter adapter, WlPresentation wl) {
    int minValue = voiLUTsData.getOffset();
    int maxValue = voiLUTsData.getOffset() + Array.getLength(lutData) - 1;

    // Ensure proper ordering and clamp to allocated value range
    minValue = Math.max(Math.min(minValue, maxValue), adapter.getMinAllocatedValue(wl));
    maxValue = Math.min(Math.max(minValue, maxValue), adapter.getMaxAllocatedValue(wl));

    return new int[] {minValue, maxValue};
  }

  private static double[] calculateWindowLevelFromRange(int[] range) {
    double width = (double) range[1] - range[0];
    double center = range[0] + width / 2.0;
    return new double[] {width, center};
  }

  /** Helper class to build preset collections from DICOM data. */
  private static class PresetCollectionBuilder {
    private final DicomImageAdapter adapter;
    private final String dicomKeyWord;
    private final WlPresentation wl;
    private final List<PresetWindowLevel> presetList;
    private final ImageDescriptor desc;
    private final VoiLutModule vLut;

    PresetCollectionBuilder(DicomImageAdapter adapter, String type, WlPresentation wl) {
      this.adapter = adapter;
      this.dicomKeyWord = " " + type;
      this.wl = wl;
      this.presetList = new ArrayList<>();
      this.desc = adapter.getImageDescriptor();
      this.vLut = desc.getVoiLutForFrame(adapter.getFrameIndex());
    }

    List<PresetWindowLevel> buildPresets() {
      buildPresetsFromWindowLevel();
      buildPresetsFromLutData();
      boolean carriedByImage = !presetList.isEmpty();
      addAutoLevelPreset();
      addModalityPresets(carriedByImage);
      return presetList;
    }

    // The presentation state windows come first, each source with its own explanations and VOI
    // LUT Function (PS3.3 C.11.2 for the image, C.11.8 for the presentation state).
    private void buildPresetsFromWindowLevel() {
      int presetCounter = 1;
      VoiLutModule prVoi = effectiveVoi();
      if (prVoi != vLut) {
        presetCounter = addWindowPresets(prVoi, presetCounter);
      }
      addWindowPresets(vLut, presetCounter);
    }

    private VoiLutModule effectiveVoi() {
      if (wl.getPresentationState() instanceof PrDicomObject pr) {
        var prVoi = pr.getVoiLUT();
        if (prVoi.isPresent()) {
          return prVoi.get();
        }
      }
      return vLut;
    }

    private int addWindowPresets(VoiLutModule voi, int firstPresetNumber) {
      var levelList = voi.getWindowCenter();
      var windowList = voi.getWindowWidth();
      var explanationList = voi.getWindowCenterWidthExplanation();
      var lutShape = getLutShape(voi);
      int presetCounter = firstPresetNumber;
      int count = Math.min(levelList.size(), windowList.size());
      for (int i = 0; i < count; i++) {
        var explanation = getPresetExplanation(explanationList, i, "Default " + presetCounter);
        var preset =
            new PresetWindowLevel(
                explanation + dicomKeyWord, windowList.get(i), levelList.get(i), lutShape);

        if (!presetList.contains(preset)) {
          setPresetKeyCode(preset, presetCounter - 1);
          presetList.add(preset);
          presetCounter++;
        }
      }
      return presetCounter;
    }

    private void buildPresetsFromLutData() {
      var voiLUTsData = getVoiLutData();
      var voiLUTsExplanation = getVoiLUTExplanation();

      if (voiLUTsData.isEmpty()) return;

      var defaultExplanation = "VOI LUT";
      for (int i = 0; i < voiLUTsData.size(); i++) {
        var explanation = getPresetExplanation(voiLUTsExplanation, i, defaultExplanation + " " + i);
        var preset =
            buildPresetFromLutData(adapter, voiLUTsData.get(i), wl, explanation + dicomKeyWord);

        if (preset != null) {
          setPresetKeyCode(preset, presetList.size());
          presetList.add(preset);
        }
      }
    }

    private void addAutoLevelPreset() {
      var autoLevel =
          new PresetWindowLevel(
              "Auto Level [Image]",
              adapter.getFullDynamicWidth(wl),
              adapter.getFullDynamicCenter(wl),
              getDefaultLutShape());
      autoLevel.setKeyCode(AUTO_LEVEL_KEY);
      presetList.add(autoLevel);
    }

    private void addModalityPresets(boolean carriedByImage) {
      List<PresetWindowLevel> modPresets;
      try {
        modPresets = getModalityPresetProvider().getPresets(adapter, wl);
      } catch (RuntimeException e) {
        LOGGER.error("Cannot get the configured presets", e);
        return;
      }
      if (modPresets == null) {
        return;
      }
      PresetWindowLevel fallback =
          carriedByImage
              ? null
              : modPresets.stream()
                  .filter(PresetWindowLevel::isFallbackDefault)
                  .findFirst()
                  .orElse(null);
      if (fallback != null) {
        presetList.add(0, fallback);
      }
      modPresets.stream().filter(p -> p != fallback).forEach(presetList::add);
    }

    private List<LookupTableCV> getVoiLutData() {
      var luts = new ArrayList<LookupTableCV>();
      if (wl.getPresentationState() instanceof PrDicomObject pr) {
        pr.getVoiLUT().ifPresent(voiLutModule -> luts.addAll(voiLutModule.getLut()));
      }
      luts.addAll(vLut.getLut());
      return luts;
    }

    private List<String> getVoiLUTExplanation() {
      var explanations = new ArrayList<String>();
      if (wl.getPresentationState() instanceof PrDicomObject pr) {
        pr.getVoiLUT()
            .ifPresent(voiLutModule -> explanations.addAll(voiLutModule.getLutExplanation()));
      }
      explanations.addAll(vLut.getLutExplanation());
      return explanations;
    }

    private LutShape getLutShape(VoiLutModule voi) {
      return voi.getVoiLutFunction()
          .map(
              function ->
                  switch (function.trim().toUpperCase(Locale.ROOT)) {
                    case "SIGMOID" ->
                        new LutShape(Function.SIGMOID, Function.SIGMOID + dicomKeyWord);
                    case "LINEAR_EXACT" ->
                        new LutShape(Function.LINEAR_EXACT, Function.LINEAR_EXACT + dicomKeyWord);
                    case "LINEAR" -> new LutShape(Function.LINEAR, Function.LINEAR + dicomKeyWord);
                    default -> {
                      // Deviation: an undefined VOI LUT Function (PS3.3 C.11.2.1.3 only defines
                      // LINEAR, LINEAR_EXACT and SIGMOID) falls back to LINEAR instead of
                      // rejecting the window.
                      LOGGER.warn("Unknown VOI LUT Function '{}', LINEAR is used", function);
                      yield LutShape.LINEAR;
                    }
                  })
          .orElse(LutShape.LINEAR);
    }

    private static String getPresetExplanation(
        List<String> explanationList, int index, String defaultExplanation) {
      if (index < explanationList.size()) {
        var explanation = explanationList.get(index);
        if (StringUtil.hasText(explanation)) {
          return explanation;
        }
      }
      return defaultExplanation;
    }

    private static void setPresetKeyCode(PresetWindowLevel preset, int index) {
      if (index == 0) {
        preset.setKeyCode(FIRST_PRESET_KEY);
      } else if (index == 1) {
        preset.setKeyCode(SECOND_PRESET_KEY);
      }
    }
  }
}
