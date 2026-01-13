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
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
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

  private static final Map<String, List<PresetWindowLevel>> PRESET_LIST_BY_MODALITY =
      loadPresetsByModality();

  private static final int AUTO_LEVEL_KEY = 0x30;
  private static final int FIRST_PRESET_KEY = 0x31;
  private static final int SECOND_PRESET_KEY = 0x32;
  private static final int MIN_BITS_FOR_MODALITY_PRESETS = 8;

  private final String name;
  private final double window;
  private final double level;
  private final LutShape shape;
  private int keyCode = 0;

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

  private static Map<String, List<PresetWindowLevel>> loadPresetsByModality() {
    try (var stream = openPresetFile()) {
      if (stream == null) return Collections.emptyMap();
      var factory = createSecureXMLFactory();
      var xmlReader = factory.createXMLStreamReader(stream);
      var presets = new TreeMap<String, List<PresetWindowLevel>>();
      parsePresetsXML(xmlReader, presets);
      return presets;
    } catch (Exception e) {
      LOGGER.error("Cannot read presets file!", e);
      return Collections.emptyMap();
    }
  }

  private static InputStream openPresetFile() throws URISyntaxException, IOException {
    var pathString = System.getProperty("dicom.presets.path");
    if (!StringUtil.hasText(pathString)) {
      return  PresetWindowLevel.class.getResourceAsStream("presets.xml");
    }

    var path =  Paths.get(pathString);
    return Files.isReadable(path) ? Files.newInputStream(path) : null;
  }

  public static XMLInputFactory createSecureXMLFactory() {
    var factory = XMLInputFactory.newInstance();
    // Disable external entities for security
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    return factory;
  }

  public static void parsePresetsXML(
      XMLStreamReader xmler, Map<String, List<PresetWindowLevel>> presets)
      throws XMLStreamException {
    while (xmler.hasNext()) {
      if (xmler.next() == XMLStreamConstants.START_ELEMENT
          && "presets".equals(xmler.getName().getLocalPart())) {
        while (xmler.hasNext()) {
          readPresetListByModality(xmler, presets);
        }
      }
    }
  }

  private static void readPresetListByModality(
      XMLStreamReader xmler, Map<String, List<PresetWindowLevel>> presets)
      throws XMLStreamException {
    if (xmler.next() != XMLStreamConstants.START_ELEMENT) return;

    var elementName = xmler.getName().getLocalPart();
    if (!"preset".equals(elementName) || xmler.getAttributeCount() < 4) return;
    try {
      var preset = parsePresetFromXML(xmler);
      var modality = xmler.getAttributeValue(null, "modality");
      presets.computeIfAbsent(modality, k -> new ArrayList<>()).add(preset);

    } catch (Exception e) {
      var name = xmler.getAttributeValue(null, "name");
      LOGGER.error("Preset {} cannot be read from xml file", name, e);
    }
  }

  private static PresetWindowLevel parsePresetFromXML(XMLStreamReader xmler) {
    var name = xmler.getAttributeValue(null, "name");
    var window = Double.parseDouble(xmler.getAttributeValue(null, "window"));
    var level = Double.parseDouble(xmler.getAttributeValue(null, "level"));
    var shape = xmler.getAttributeValue(null, "shape");
    var keyCode = getKeyTagAttribute(xmler);
    var lutShape = LutShape.getLutShape(shape);

    var preset =
        new PresetWindowLevel(
            name, window, level, Objects.requireNonNullElse(lutShape, LutShape.LINEAR));
    if (keyCode != null) {
      preset.setKeyCode(keyCode);
    }
    return preset;
  }

  private static Integer getKeyTagAttribute(XMLStreamReader xmler) {
    var value = xmler.getAttributeValue(null, "key");
    return value != null ? StringUtil.getInteger(value) : null;
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
      addAutoLevelPreset();
      addModalityPresets();
      return presetList;
    }

    private void buildPresetsFromWindowLevel() {
      var levelList = getWindowCenter();
      var windowList = getWindowWidth();
      var explanationList = getWindowCenterWidthExplanation();
      var defaultLutShape = getDefaultLutShape();

      if (levelList.isEmpty() || windowList.isEmpty()) return;

      var defaultExplanation = "Default";
      var presetCounter = 1;

      for (int i = 0; i < levelList.size(); i++) {
        var explanation =
            getPresetExplanation(explanationList, i, defaultExplanation + " " + presetCounter);
        var preset =
            new PresetWindowLevel(
                explanation + dicomKeyWord, windowList.get(i), levelList.get(i), defaultLutShape);

        if (!presetList.contains(preset)) {
          setPresetKeyCode(preset, presetCounter - 1);
          presetList.add(preset);
          presetCounter++;
        }
      }
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

    private void addModalityPresets() {
      // Exclude Secondary Capture CT and low bit depth images
      if (adapter.getBitsStored() > MIN_BITS_FOR_MODALITY_PRESETS) {
        var modality = desc.getModality();
        if (StringUtil.hasText(modality)) {
          var modPresets = PRESET_LIST_BY_MODALITY.get(modality);
          if (modPresets != null) {
            presetList.addAll(modPresets);
          }
        }
      }
    }

    private List<Double> getWindowCenter() {
      var centers = new ArrayList<Double>();
      if (wl.getPresentationState() instanceof PrDicomObject pr) {
        pr.getVoiLUT().ifPresent(voiLutModule -> centers.addAll(voiLutModule.getWindowCenter()));
      }
      centers.addAll(vLut.getWindowCenter());
      return centers;
    }

    private List<Double> getWindowWidth() {
      var widths = new ArrayList<Double>();
      if (wl.getPresentationState() instanceof PrDicomObject pr) {
        pr.getVoiLUT().ifPresent(voiLutModule -> widths.addAll(voiLutModule.getWindowWidth()));
      }
      widths.addAll(vLut.getWindowWidth());
      return widths;
    }

    private List<String> getWindowCenterWidthExplanation() {
      return vLut.getWindowCenterWidthExplanation();
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

    private LutShape getDefaultLutShape() {
      return vLut.getVoiLutFunction()
          .map(
              function ->
                  switch (function.toUpperCase()) {
                    case "SIGMOID" ->
                        new LutShape(Function.SIGMOID, Function.SIGMOID + dicomKeyWord);
                    case "LINEAR" -> new LutShape(Function.LINEAR, Function.LINEAR + dicomKeyWord);
                    default -> LutShape.LINEAR;
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
      switch (index) {
        case 0 -> preset.setKeyCode(FIRST_PRESET_KEY);
        case 1 -> preset.setKeyCode(SECOND_PRESET_KEY);
      }
    }
  }
}
