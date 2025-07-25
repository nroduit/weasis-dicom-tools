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
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.net.URL;
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

  private static final Map<String, List<PresetWindowLevel>> presetListByModality =
      getPresetListByModality();

  // Key codes for shortcuts
  private static final int AUTO_LEVEL_KEY = 0x30;
  private static final int FIRST_PRESET_KEY = 0x31;
  private static final int SECOND_PRESET_KEY = 0x32;

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

  /** Returns the minimum value of the window/level box. */
  public double getMinBox() {
    return level - window / 2.0;
  }

  /** Returns the maximum value of the window/level box. */
  public double getMaxBox() {
    return level + window / 2.0;
  }

  public void setKeyCode(int keyCode) {
    this.keyCode = keyCode;
  }

  /** Returns true if this preset represents an auto-level function. */
  public boolean isAutoLevel() {
    return keyCode == AUTO_LEVEL_KEY;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PresetWindowLevel that = (PresetWindowLevel) o;
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
    if (adapter == null || wl == null) {
      throw new IllegalArgumentException("Null parameter");
    }

    String dicomKeyWord = " " + type;
    ArrayList<PresetWindowLevel> presetList = new ArrayList<>();
    ImageDescriptor desc = adapter.getImageDescriptor();
    VoiLutModule vLut = desc.getVoiLutForFrame(adapter.getFrameIndex());

    buildPresetsFromWindowLevel(vLut, wl, dicomKeyWord, presetList);
    buildPresetsFromLutData(adapter, wl, vLut, dicomKeyWord, presetList);
    addAutoLevelPreset(adapter, wl, getDefaultLutShape(vLut, dicomKeyWord), presetList);
    addModalityPresets(adapter, desc, presetList);
    return presetList;
  }

  private static void buildPresetsFromWindowLevel(
      VoiLutModule vLut,
      WlPresentation wl,
      String dicomKeyWord,
      ArrayList<PresetWindowLevel> presetList) {
    List<Double> levelList = getWindowCenter(vLut, wl);
    List<Double> windowList = getWindowWidth(vLut, wl);

    List<String> wlExplanationList = vLut.getWindowCenterWidthExplanation();
    LutShape defaultLutShape = getDefaultLutShape(vLut, dicomKeyWord);

    buildPreset(
        levelList, windowList, wlExplanationList, dicomKeyWord, defaultLutShape, presetList);
  }

  private static void buildPresetsFromLutData(
      DicomImageAdapter adapter,
      WlPresentation wl,
      VoiLutModule vLut,
      String dicomKeyWord,
      ArrayList<PresetWindowLevel> presetList) {
    List<LookupTableCV> voiLUTsData = getVoiLutData(vLut, wl);
    List<String> voiLUTsExplanation = getVoiLUTExplanation(vLut, wl);

    if (voiLUTsData.isEmpty()) return;

    String defaultExplanation = "VOI LUT";
    for (int i = 0; i < voiLUTsData.size(); i++) {
      String explanation =
          getPresetExplanation(voiLUTsExplanation, i, defaultExplanation + " " + i);
      PresetWindowLevel preset =
          buildPresetFromLutData(adapter, voiLUTsData.get(i), wl, explanation + dicomKeyWord);

      if (preset != null) {
        setPresetKeyCode(preset, presetList.size());
        presetList.add(preset);
      }
    }
  }

  private static void addAutoLevelPreset(
      DicomImageAdapter adapter,
      WlPresentation wl,
      LutShape defaultLutShape,
      ArrayList<PresetWindowLevel> presetList) {
    PresetWindowLevel autoLevel =
        new PresetWindowLevel(
            "Auto Level [Image]",
            adapter.getFullDynamicWidth(wl),
            adapter.getFullDynamicCenter(wl),
            defaultLutShape);
    autoLevel.setKeyCode(AUTO_LEVEL_KEY);
    presetList.add(autoLevel);
  }

  private static void addModalityPresets(
      DicomImageAdapter adapter, ImageDescriptor desc, ArrayList<PresetWindowLevel> presetList) {
    // Exclude Secondary Capture CT and low bit depth images
    if (adapter.getBitsStored() > 8) {
      var modality = desc.getModality();
      if (StringUtil.hasText(modality)) {
        List<PresetWindowLevel> modPresets = presetListByModality.get(desc.getModality());
        if (modPresets != null) {
          presetList.addAll(modPresets);
        }
      }
    }
  }

  private static LutShape getDefaultLutShape(VoiLutModule vLut, String dicomKeyWord) {
    Optional<String> lutFunctionDescriptor = vLut.getVoiLutFunction();

    if (lutFunctionDescriptor.isPresent()) {
      String function = lutFunctionDescriptor.get();
      if ("SIGMOID".equalsIgnoreCase(function)) {
        return new LutShape(Function.SIGMOID, Function.SIGMOID + dicomKeyWord);
      } else if ("LINEAR".equalsIgnoreCase(function)) {
        return new LutShape(Function.LINEAR, Function.LINEAR + dicomKeyWord);
      }
    }

    return LutShape.LINEAR; // Default as per DICOM standard
  }

  private static String getPresetExplanation(
      List<String> explanationList, int index, String defaultExplanation) {
    if (index < explanationList.size()) {
      String explanation = explanationList.get(index);
      if (StringUtil.hasText(explanation)) {
        return explanation;
      }
    }
    return defaultExplanation;
  }

  private static void buildPreset(
      List<Double> levelList,
      List<Double> windowList,
      List<String> wlExplanationList,
      String dicomKeyWord,
      LutShape defaultLutShape,
      ArrayList<PresetWindowLevel> presetList) {
    if (levelList.isEmpty() || windowList.isEmpty()) return;
    String defaultExplanation = "Default";
    int presetCounter = 1;

    for (int i = 0; i < levelList.size(); i++) {
      String explanation =
          getPresetExplanation(wlExplanationList, i, defaultExplanation + " " + presetCounter);
      PresetWindowLevel preset =
          new PresetWindowLevel(
              explanation + dicomKeyWord, windowList.get(i), levelList.get(i), defaultLutShape);
      if (!presetList.contains(preset)) {
        setPresetKeyCode(preset, presetCounter - 1);
        presetList.add(preset);
        presetCounter++;
      }
    }
  }

  private static void setPresetKeyCode(PresetWindowLevel preset, int index) {
    if (index == 0) {
      preset.setKeyCode(FIRST_PRESET_KEY);
    } else if (index == 1) {
      preset.setKeyCode(SECOND_PRESET_KEY);
    }
  }

  private static List<Double> getWindowCenter(VoiLutModule vLut, WlPresentation wl) {
    List<Double> centers = new ArrayList<>();
    if (wl.getPresentationState() instanceof PrDicomObject pr) {
      pr.getVoiLUT().ifPresent(voiLutModule -> centers.addAll(voiLutModule.getWindowCenter()));
    }
    centers.addAll(vLut.getWindowCenter());
    return centers;
  }

  private static List<Double> getWindowWidth(VoiLutModule vLut, WlPresentation wl) {
    List<Double> widths = new ArrayList<>();

    if (wl.getPresentationState() instanceof PrDicomObject pr) {
      pr.getVoiLUT().ifPresent(voiLutModule -> widths.addAll(voiLutModule.getWindowWidth()));
    }
    widths.addAll(vLut.getWindowWidth());
    return widths;
  }

  private static List<LookupTableCV> getVoiLutData(VoiLutModule vLut, WlPresentation wl) {
    List<LookupTableCV> luts = new ArrayList<>();
    if (wl.getPresentationState() instanceof PrDicomObject pr) {
      pr.getVoiLUT().ifPresent(voiLutModule -> luts.addAll(voiLutModule.getLut()));
    }
    luts.addAll(vLut.getLut());
    return luts;
  }

  private static List<String> getVoiLUTExplanation(VoiLutModule vLut, WlPresentation wl) {
    List<String> explanations = new ArrayList<>();
    if (wl.getPresentationState() instanceof PrDicomObject pr) {
      pr.getVoiLUT()
          .ifPresent(voiLutModule -> explanations.addAll(voiLutModule.getLutExplanation()));
    }
    explanations.addAll(vLut.getLutExplanation());
    return explanations;
  }

  /**
   * Creates a preset from LUT data by calculating equivalent window/level values.
   *
   * @param adapter the DICOM image adapter
   * @param voiLUTsData the LUT data
   * @param wl the window/level presentation
   * @param explanation the preset explanation/name
   * @return the created preset or null if creation failed
   */
  public static PresetWindowLevel buildPresetFromLutData(
      DicomImageAdapter adapter, LookupTableCV voiLUTsData, WlPresentation wl, String explanation) {
    if (adapter == null || voiLUTsData == null || explanation == null) {
      return null;
    }

    Object lutData = extractLutData(voiLUTsData);
    if (lutData == null) return null;

    int[] valueRange = calculateLutValueRange(voiLUTsData, lutData, adapter, wl);
    double[] windowLevel = calculateWindowLevelFromRange(valueRange);

    LutShape newLutShape = new LutShape(voiLUTsData, explanation);
    return new PresetWindowLevel(
        newLutShape.toString(), windowLevel[0], windowLevel[1], newLutShape);
  }

  private static Object extractLutData(LookupTableCV voiLUTsData) {
    if (voiLUTsData.getDataType() == DataBuffer.TYPE_BYTE) {
      return voiLUTsData.getByteData(0);
    } else if (voiLUTsData.getDataType() <= DataBuffer.TYPE_SHORT) {
      return voiLUTsData.getShortData(0);
    }
    return null;
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

  /**
   * Loads preset configurations from XML file, organized by modality.
   *
   * @return map of modality to preset list, empty if loading fails
   */
  public static Map<String, List<PresetWindowLevel>> getPresetListByModality() {
    Map<String, List<PresetWindowLevel>> presets = new TreeMap<>();

    try (InputStream stream = openPresetFile()) {
      if (stream == null) return Collections.emptyMap();
      XMLInputFactory factory = createSecureXMLFactory();
      XMLStreamReader xmler = factory.createXMLStreamReader(stream);
      parsePresetsXML(xmler, presets);
    } catch (Exception e) {
      LOGGER.error("Cannot read presets file! ", e);
    }

    return presets;
  }

  private static InputStream openPresetFile() throws Exception {
    String pathString = System.getProperty("dicom.presets.path");
    Path path = StringUtil.hasText(pathString) ? Paths.get(pathString) : getPresetPath();

    return path != null && Files.isReadable(path) ? Files.newInputStream(path) : null;
  }

  private static Path getPresetPath() throws URISyntaxException {
    URL resource = PresetWindowLevel.class.getResource("presets.xml");
    if (resource == null) {
      LOGGER.warn("Presets XML file not found in classpath");
      return null;
    }
    return Paths.get(resource.toURI());
  }

  private static XMLInputFactory createSecureXMLFactory() {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    // Disable external entities for security
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    return factory;
  }

  private static void parsePresetsXML(
      XMLStreamReader xmler, Map<String, List<PresetWindowLevel>> presets)
      throws XMLStreamException {
    int eventType;
    while (xmler.hasNext()) {
      eventType = xmler.next();
      if (eventType == XMLStreamConstants.START_ELEMENT
          && "presets".equals(xmler.getName().getLocalPart())) {
        while (xmler.hasNext()) {
          readPresetListByModality(xmler, presets);
        }
      }
    }
  }

  private static Integer getKeyTagAttribute(XMLStreamReader xmler) {

    String value = xmler.getAttributeValue(null, "key");
    if (value != null) {
      return StringUtil.getInteger(value);
    }
    return null;
  }

  private static void readPresetListByModality(
      XMLStreamReader xmler, Map<String, List<PresetWindowLevel>> presets)
      throws XMLStreamException {
    int eventType = xmler.next();
    if (eventType != XMLStreamConstants.START_ELEMENT) return;

    String elementName = xmler.getName().getLocalPart();
    if (!"preset".equals(elementName) || xmler.getAttributeCount() < 4) return;
    try {
      PresetWindowLevel preset = parsePresetFromXML(xmler);
      String modality = xmler.getAttributeValue(null, "modality");
      presets.computeIfAbsent(modality, k -> new ArrayList<>()).add(preset);

    } catch (Exception e) {
      String name = xmler.getAttributeValue(null, "name");
      LOGGER.error("Preset {} cannot be read from xml file", name, e);
    }
  }

  private static PresetWindowLevel parsePresetFromXML(XMLStreamReader xmler) {
    String name = xmler.getAttributeValue(null, "name");
    double window = Double.parseDouble(xmler.getAttributeValue(null, "window"));
    double level = Double.parseDouble(xmler.getAttributeValue(null, "level"));
    String shape = xmler.getAttributeValue(null, "shape");
    Integer keyCode = getKeyTagAttribute(xmler);
    LutShape lutShape = LutShape.getLutShape(shape);
    PresetWindowLevel preset =
        new PresetWindowLevel(name, window, level, lutShape == null ? LutShape.LINEAR : lutShape);
    if (keyCode != null) {
      preset.setKeyCode(keyCode);
    }
    return preset;
  }
}
