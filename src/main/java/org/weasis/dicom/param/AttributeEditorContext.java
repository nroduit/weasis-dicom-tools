/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import static org.dcm4che3.img.Transcoder.getMaskedImage;

import java.util.Objects;
import java.util.Properties;
import org.dcm4che3.img.op.MaskArea;
import org.dcm4che3.img.util.Editable;
import org.weasis.core.util.LangUtil;
import org.weasis.opencv.data.PlanarImage;

public class AttributeEditorContext {

  /** Abort status allows to skip the file transfer or abort the DICOM association */
  public enum Abort {
    // Do nothing
    NONE,
    // Allows to skip the bulk data transfer to go to the next file
    FILE_EXCEPTION,
    // Stop the DICOM connection. Attention, this will abort other transfers when there are several
    // destinations for one source.
    CONNECTION_EXCEPTION
  }

  private final String tsuid;
  private final DicomNode sourceNode;
  private final DicomNode destinationNode;
  private final Properties properties;

  private Abort abort;
  private String abortMessage;
  private MaskArea maskArea;

  public AttributeEditorContext(String tsuid, DicomNode sourceNode, DicomNode destinationNode) {
    this.tsuid = tsuid;
    this.sourceNode = sourceNode;
    this.destinationNode = destinationNode;
    this.abort = Abort.NONE;
    this.properties = new Properties();
  }

  public Abort getAbort() {
    return abort;
  }

  public void setAbort(Abort abort) {
    this.abort = abort;
  }

  public String getAbortMessage() {
    return abortMessage;
  }

  public void setAbortMessage(String abortMessage) {
    this.abortMessage = abortMessage;
  }

  public String getTsuid() {
    return tsuid;
  }

  public DicomNode getSourceNode() {
    return sourceNode;
  }

  public DicomNode getDestinationNode() {
    return destinationNode;
  }

  public MaskArea getMaskArea() {
    return maskArea;
  }

  public void setMaskArea(MaskArea maskArea) {
    this.maskArea = maskArea;
  }

  public Properties getProperties() {
    return properties;
  }

  public Editable<PlanarImage> getEditable() {
    return getMaskedImage(getMaskArea());
  }

  public boolean hasPixelProcessing() {
    return Objects.nonNull(getMaskArea())
        || LangUtil.getEmptytoFalse(getProperties().getProperty("defacing"));
  }
}
