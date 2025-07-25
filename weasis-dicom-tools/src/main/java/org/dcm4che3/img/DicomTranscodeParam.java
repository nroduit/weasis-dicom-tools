/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.img.op.MaskArea;
import org.weasis.core.util.StringUtil;

/**
 * Configuration parameters for DICOM image transcoding operations.
 *
 * <p>This class encapsulates all the settings required for transcoding DICOM images from one
 * transfer syntax to another, including compression parameters, reading settings, and optional
 * image masking capabilities.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Automatic configuration based on target transfer syntax
 *   <li>Support for both native and compressed transfer syntaxes
 *   <li>Station-specific image masking capabilities
 *   <li>File Meta Information (FMI) output control
 *   <li>Integration with DICOM reading and writing parameters
 * </ul>
 *
 * @author Nicolas Roduit
 * @see DicomImageReadParam
 * @see DicomJpegWriteParam
 * @see MaskArea
 */
public class DicomTranscodeParam {

  // Constants for mask handling
  private static final String WILDCARD_MASK_KEY = "*";

  private final DicomImageReadParam readParam;
  private final DicomJpegWriteParam writeJpegParam;
  private final String outputTsuid;
  private final Map<String, MaskArea> maskMap;

  // Configuration flags
  private boolean outputFmi;

  /**
   * Creates transcoding parameters for the specified target transfer syntax. Uses default DICOM
   * image reading parameters.
   *
   * @param dstTsuid the target transfer syntax UID
   * @throws IllegalArgumentException if dstTsuid is null or empty
   */
  public DicomTranscodeParam(String dstTsuid) {
    this(null, dstTsuid);
  }

  /**
   * Creates transcoding parameters with custom reading parameters and target transfer syntax.
   *
   * @param readParam the DICOM image reading parameters, or null for defaults
   * @param dstTsuid the target transfer syntax UID
   * @throws IllegalArgumentException if dstTsuid is null or empty
   */
  public DicomTranscodeParam(DicomImageReadParam readParam, String dstTsuid) {
    this.outputTsuid = validateTransferSyntax(dstTsuid);
    this.readParam = readParam != null ? readParam : new DicomImageReadParam();
    this.maskMap = new HashMap<>();
    this.writeJpegParam = initializeWriteParam(dstTsuid);
    this.outputFmi = false;
  }

  private String validateTransferSyntax(String tsuid) {
    if (!StringUtil.hasText(tsuid)) {
      throw new IllegalArgumentException("Transfer syntax UID cannot be null or empty");
    }
    return tsuid;
  }

  private DicomJpegWriteParam initializeWriteParam(String tsuid) {
    return DicomOutputData.isNativeSyntax(tsuid)
        ? null
        : DicomJpegWriteParam.buildDicomImageWriteParam(tsuid);
  }

  /**
   * Gets the DICOM image reading parameters.
   *
   * @return the reading parameters used for DICOM image processing
   */
  public DicomImageReadParam getReadParam() {
    return readParam;
  }

  /**
   * Gets the JPEG writing parameters for compressed transfer syntaxes.
   *
   * @return the JPEG writing parameters, or null if the target is a native transfer syntax
   */
  public DicomJpegWriteParam getWriteJpegParam() {
    return writeJpegParam;
  }

  /**
   * Checks if File Meta Information should be included in the output.
   *
   * @return true if FMI should be output, false otherwise
   */
  public boolean isOutputFmi() {
    return outputFmi;
  }

  /**
   * Configures whether to include File Meta Information in the output.
   *
   * <p>When enabled, the output DICOM file will include the File Meta Information header as
   * specified in DICOM Part 10. This is typically required for standalone DICOM files but may be
   * omitted for streaming or embedded scenarios.
   *
   * @param outputFmi true to include FMI in output, false to omit it
   */
  public void setOutputFmi(boolean outputFmi) {
    this.outputFmi = outputFmi;
  }

  /**
   * Gets the target transfer syntax UID.
   *
   * @return the transfer syntax UID for transcoded output
   */
  public String getOutputTsuid() {
    return outputTsuid;
  }

  /**
   * Adds multiple image masks from the provided map. Existing masks with the same keys will be
   * overwritten.
   *
   * @param maskMap the map of station names to mask areas to add
   * @throws IllegalArgumentException if maskMap is null
   */
  public void addMaskMap(Map<? extends String, ? extends MaskArea> maskMap) {
    Objects.requireNonNull(maskMap, "Mask map cannot be null");
    this.maskMap.putAll(maskMap);
  }

  /**
   * Gets the image mask for the specified station or the default wildcard mask.
   *
   * <p>The lookup follows this priority:
   *
   * <ol>
   *   <li>Exact match for the provided key
   *   <li>Wildcard mask (*) if exact match not found
   *   <li>null if neither exists
   * </ol>
   *
   * @param key the station name or identifier to look up
   * @return the mask area for the key, wildcard mask, or null if not found
   */
  public MaskArea getMask(String key) {
    if (key == null) {
      return maskMap.get(WILDCARD_MASK_KEY);
    }
    return Optional.ofNullable(maskMap.get(key)).orElse(maskMap.get(WILDCARD_MASK_KEY));
  }

  /**
   * Adds or updates an image mask for the specified station.
   *
   * @param stationName the station name or identifier (null for wildcard)
   * @param maskArea the mask area to apply, or null to remove the mask
   */
  public void addMask(String stationName, MaskArea maskArea) {
    String key = stationName != null ? stationName : WILDCARD_MASK_KEY;
    if (maskArea != null) {
      this.maskMap.put(key, maskArea);
    } else {
      this.maskMap.remove(key);
    }
  }

  /**
   * Gets a copy of the current mask map. Modifications to the returned map will not affect this
   * instance.
   *
   * @return a new map containing all current masks
   */
  public Map<String, MaskArea> getMaskMap() {
    return new HashMap<>(maskMap);
  }

  /**
   * Checks if the transcoding configuration supports JPEG compression.
   *
   * @return true if JPEG parameters are available (compressed syntax), false for native syntax
   */
  public boolean isCompressionEnabled() {
    return writeJpegParam != null;
  }

  /**
   * Checks if any image masks are configured.
   *
   * @return true if at least one mask is configured, false otherwise
   */
  public boolean hasMasks() {
    return !maskMap.isEmpty();
  }

  /** Removes all configured masks. */
  public void clearMasks() {
    maskMap.clear();
  }

  /**
   * Removes the mask for the specified station.
   *
   * @param stationName the station name, or null for wildcard mask
   * @return true if a mask was removed, false if no mask existed
   */
  public boolean removeMask(String stationName) {
    String key = stationName != null ? stationName : WILDCARD_MASK_KEY;
    return maskMap.remove(key) != null;
  }

  /**
   * Checks if a mask exists for the specified station (including wildcard).
   *
   * @param stationName the station name to check
   * @return true if a specific or wildcard mask exists, false otherwise
   */
  public boolean hasMask(String stationName) {
    return getMask(stationName) != null;
  }

  /**
   * Creates a copy of this transcoding parameter configuration. The mask map is deep-copied to
   * ensure independence.
   *
   * @return a new DicomTranscodeParam instance with the same settings
   */
  public DicomTranscodeParam copy() {
    DicomTranscodeParam copy = new DicomTranscodeParam(readParam, outputTsuid);
    copy.outputFmi = this.outputFmi;
    copy.maskMap.putAll(this.maskMap);
    return copy;
  }

  @Override
  public String toString() {
    return "DicomTranscodeParam{"
        + "outputTsuid='"
        + outputTsuid
        + '\''
        + ", compression="
        + (writeJpegParam != null ? "enabled" : "disabled")
        + ", outputFmi="
        + outputFmi
        + ", masks="
        + maskMap.size()
        + '}';
  }
}
