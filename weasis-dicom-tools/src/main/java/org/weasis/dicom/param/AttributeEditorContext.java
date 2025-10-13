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

/**
 * Context information for DICOM attribute editing operations.
 *
 * <p>Provides configuration and state management for customizing DICOM attributes during transfer
 * operations. Contains source and destination node information, transfer syntax details, abort
 * controls, and pixel processing settings.
 *
 * @since 1.0
 */
public class AttributeEditorContext {

  /**
   * Abort status controls for DICOM operations.
   *
   * <p>Allows fine-grained control over operation termination at different levels.
   */
  public enum Abort {
    /** Continue normal operation. */
    NONE,
    /** Skip current file and continue with next file. */
    FILE_EXCEPTION,
    /** Terminate the entire DICOM association. */
    CONNECTION_EXCEPTION
  }

  private static final String DEFACING_PROPERTY = "defacing";

  private final String tsuid;
  private final DicomNode sourceNode;
  private final DicomNode destinationNode;
  private final Properties properties;

  private Abort abort;
  private String abortMessage;
  private MaskArea maskArea;

  /**
   * Creates a new attribute editor context.
   *
   * @param tsuid the transfer syntax UID for the operation
   * @param sourceNode the source DICOM node
   * @param destinationNode the destination DICOM node
   * @throws NullPointerException if any parameter is null
   */
  public AttributeEditorContext(String tsuid, DicomNode sourceNode, DicomNode destinationNode) {
    this.tsuid = tsuid;
    this.sourceNode = sourceNode;
    this.destinationNode = destinationNode;
    this.abort = Abort.NONE;
    this.properties = new Properties();
  }

  /**
   * Returns the current abort status.
   *
   * @return the abort status
   */
  public Abort getAbort() {
    return abort;
  }

  /**
   * Sets the abort status for the operation.
   *
   * @param abort the abort status to set
   */
  public void setAbort(Abort abort) {
    this.abort = Objects.requireNonNullElse(abort, Abort.NONE);
  }

  /**
   * Returns the abort message if any.
   *
   * @return the abort message, or null if none set
   */
  public String getAbortMessage() {
    return abortMessage;
  }

  /**
   * Sets an abort message explaining the reason for abortion.
   *
   * @param abortMessage the abort message
   */
  public void setAbortMessage(String abortMessage) {
    this.abortMessage = abortMessage;
  }

  /**
   * Returns the transfer syntax UID.
   *
   * @return the transfer syntax UID
   */
  public String getTsuid() {
    return tsuid;
  }

  /**
   * Returns the source DICOM node.
   *
   * @return the source node
   */
  public DicomNode getSourceNode() {
    return sourceNode;
  }

  /**
   * Returns the destination DICOM node.
   *
   * @return the destination node
   */
  public DicomNode getDestinationNode() {
    return destinationNode;
  }

  /**
   * Returns the mask area for pixel processing.
   *
   * @return the mask area, or null if none set
   */
  public MaskArea getMaskArea() {
    return maskArea;
  }

  /**
   * Sets the mask area for pixel processing operations.
   *
   * @param maskArea the mask area to apply
   */
  public void setMaskArea(MaskArea maskArea) {
    this.maskArea = maskArea;
  }

  /**
   * Returns the properties for this context.
   *
   * @return the mutable properties object
   */
  public Properties getProperties() {
    return properties;
  }

  /**
   * Creates an editable image with the configured mask area.
   *
   * @return the editable planar image with masking applied
   */
  public Editable<PlanarImage> getEditable() {
    return getMaskedImage(getMaskArea());
  }

  /**
   * Checks if pixel processing is enabled.
   *
   * @return true if mask area is set or defacing is enabled
   */
  public boolean hasPixelProcessing() {
    return hasMaskArea() || isDefacingEnabled();
  }

  private boolean hasMaskArea() {
    return Objects.nonNull(getMaskArea());
  }

  private boolean isDefacingEnabled() {
    return LangUtil.emptyToFalse(getProperties().getProperty(DEFACING_PROPERTY));
  }

  /**
   * Sets the abort status with an accompanying message.
   *
   * @param abort the abort status
   * @param message the abort message
   */
  public void setAbort(Abort abort, String message) {
    setAbort(abort);
    setAbortMessage(message);
  }

  /**
   * Checks if the operation should be aborted.
   *
   * @return true if abort status is not NONE
   */
  public boolean shouldAbort() {
    return abort != Abort.NONE;
  }

  @Override
  public String toString() {
    return "AttributeEditorContext{"
        + "tsuid='"
        + tsuid
        + '\''
        + ", sourceNode="
        + sourceNode
        + ", destinationNode="
        + destinationNode
        + ", abort="
        + abort
        + ", abortMessage='"
        + abortMessage
        + '\''
        + ", maskArea="
        + maskArea
        + ", propertiesCount="
        + properties.size()
        + '}';
  }
}
