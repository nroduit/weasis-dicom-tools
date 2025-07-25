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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;

/**
 * Factory for creating synthetic DICOM test data with various configurations. This class helps
 * generate DICOM attributes for testing without relying on external files.
 */
public class DicomTestDataFactory {

  private static final String DEFAULT_SOP_CLASS_UID = UID.SecondaryCaptureImageStorage;
  private static final String DEFAULT_SOP_INSTANCE_UID = "1.2.3.4.5.6.7.8.9.10.11.12.13.14.15";
  private static final String DEFAULT_STUDY_INSTANCE_UID = "1.2.3.4.5.6.7.8.9.10.11.12.13";
  private static final String DEFAULT_SERIES_INSTANCE_UID = "1.2.3.4.5.6.7.8.9.10.11.12.13.14";

  /** Creates basic DICOM image attributes with standard required elements. */
  public Attributes createBasicImageAttributes(
      int width, int height, String transferSyntax, boolean multiFrame) {
    Attributes attrs = new Attributes();

    // Required DICOM elements
    attrs.setString(Tag.SOPClassUID, VR.UI, DEFAULT_SOP_CLASS_UID);
    attrs.setString(Tag.SOPInstanceUID, VR.UI, DEFAULT_SOP_INSTANCE_UID);
    attrs.setString(Tag.StudyInstanceUID, VR.UI, DEFAULT_STUDY_INSTANCE_UID);
    attrs.setString(Tag.SeriesInstanceUID, VR.UI, DEFAULT_SERIES_INSTANCE_UID);
    attrs.setString(Tag.TransferSyntaxUID, VR.UI, transferSyntax);

    // Image attributes
    attrs.setInt(Tag.Rows, VR.US, height);
    attrs.setInt(Tag.Columns, VR.US, width);
    attrs.setInt(Tag.BitsAllocated, VR.US, 16);
    attrs.setInt(Tag.BitsStored, VR.US, 16);
    attrs.setInt(Tag.HighBit, VR.US, 15);
    attrs.setInt(Tag.PixelRepresentation, VR.US, 0);
    attrs.setInt(Tag.SamplesPerPixel, VR.US, 1);
    attrs.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");

    if (multiFrame) {
      attrs.setInt(Tag.NumberOfFrames, VR.IS, 3);
    }

    // Patient/Study information
    attrs.setString(Tag.PatientName, VR.PN, "Test^Patient");
    attrs.setString(Tag.PatientID, VR.LO, "TEST001");
    attrs.setString(Tag.StudyDate, VR.DA, "20240101");
    attrs.setString(Tag.StudyTime, VR.TM, "120000");
    attrs.setString(Tag.Modality, VR.CS, "OT");

    return attrs;
  }

  /** Creates monochrome image attributes with configurable bit depth and sign. */
  public Attributes createMonochromeImageAttributes(
      int width, int height, int bitsStored, boolean signed, String transferSyntax) {
    Attributes attrs = createBasicImageAttributes(width, height, transferSyntax, false);

    int bitsAllocated = bitsStored <= 8 ? 8 : 16;
    attrs.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
    attrs.setInt(Tag.BitsStored, VR.US, bitsStored);
    attrs.setInt(Tag.HighBit, VR.US, bitsStored - 1);
    attrs.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);

    return attrs;
  }

  /** Creates color image attributes with specified photometric interpretation. */
  public Attributes createColorImageAttributes(
      int width, int height, String photometricInterpretation, String transferSyntax) {
    Attributes attrs = createBasicImageAttributes(width, height, transferSyntax, false);

    attrs.setInt(Tag.SamplesPerPixel, VR.US, 3);
    attrs.setString(Tag.PhotometricInterpretation, VR.CS, photometricInterpretation);
    attrs.setInt(Tag.PlanarConfiguration, VR.US, 0); // Interleaved
    attrs.setInt(Tag.BitsAllocated, VR.US, 8);
    attrs.setInt(Tag.BitsStored, VR.US, 8);
    attrs.setInt(Tag.HighBit, VR.US, 7);

    return attrs;
  }

  /** Creates ultrasound image attributes with typical US-specific elements. */
  public Attributes createUltrasoundImageAttributes(int width, int height, String transferSyntax) {
    Attributes attrs = createBasicImageAttributes(width, height, transferSyntax, false);

    attrs.setString(Tag.Modality, VR.CS, "US");
    attrs.setString(Tag.SOPClassUID, VR.UI, UID.UltrasoundImageStorage);

    // Add US-specific attributes
    attrs.setDouble(Tag.FrameTime, VR.DS, 33.33); // ~30 FPS
    attrs.setString(Tag.TransducerData, VR.LO, "Test Transducer");

    return attrs;
  }
}
