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
import org.dcm4che3.img.op.MaskArea;

/**
 * @author Nicolas Roduit
 */
public class DicomTranscodeParam {
  private final DicomImageReadParam readParam;
  private final DicomJpegWriteParam writeJpegParam;
  private final String outputTsuid;
  private final Map<String, MaskArea> maskMap;
  private boolean outputFmi;

  public DicomTranscodeParam(String dstTsuid) {
    this(null, dstTsuid);
  }

  public DicomTranscodeParam(DicomImageReadParam readParam, String dstTsuid) {
    this.readParam = readParam == null ? new DicomImageReadParam() : readParam;
    this.outputTsuid = dstTsuid;
    this.maskMap = new HashMap<>();
    if (DicomOutputData.isNativeSyntax(dstTsuid)) {
      this.writeJpegParam = null;
    } else {
      this.writeJpegParam = DicomJpegWriteParam.buildDicomImageWriteParam(dstTsuid);
    }
  }

  public DicomImageReadParam getReadParam() {
    return readParam;
  }

  public DicomJpegWriteParam getWriteJpegParam() {
    return writeJpegParam;
  }

  public boolean isOutputFmi() {
    return outputFmi;
  }

  public void setOutputFmi(boolean outputFmi) {
    this.outputFmi = outputFmi;
  }

  public String getOutputTsuid() {
    return outputTsuid;
  }

  public void addMaskMap(Map<? extends String, ? extends MaskArea> maskMap) {
    this.maskMap.putAll(maskMap);
  }

  public MaskArea getMask(String key) {
    MaskArea mask = maskMap.get(key);
    if (mask == null) {
      mask = maskMap.get("*");
    }
    return mask;
  }

  public void addMask(String stationName, MaskArea maskArea) {
    this.maskMap.put(stationName, maskArea);
  }

  public Map<String, MaskArea> getMaskMap() {
    return maskMap;
  }
}
