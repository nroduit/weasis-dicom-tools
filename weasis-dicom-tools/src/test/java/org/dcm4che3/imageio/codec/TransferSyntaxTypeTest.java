/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.imageio.codec;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TransferSyntaxTypeTest {

  @Test
  void forUIDReturnsCorrectTypeForValidUID() {
    Assertions.assertEquals(
        TransferSyntaxType.NATIVE, TransferSyntaxType.forUID(UID.ImplicitVRLittleEndian));
    Assertions.assertEquals(
        TransferSyntaxType.DEFLATED, TransferSyntaxType.forUID(UID.DeflatedExplicitVRLittleEndian));
    Assertions.assertEquals(
        TransferSyntaxType.JPEG_BASELINE, TransferSyntaxType.forUID(UID.JPEGBaseline8Bit));
    Assertions.assertEquals(
        TransferSyntaxType.JPEG_EXTENDED, TransferSyntaxType.forUID(UID.JPEGExtended12Bit));
    Assertions.assertEquals(
        TransferSyntaxType.JPEG_SPECTRAL,
        TransferSyntaxType.forUID(UID.JPEGSpectralSelectionNonHierarchical68));
    Assertions.assertEquals(
        TransferSyntaxType.JPEG_PROGRESSIVE,
        TransferSyntaxType.forUID(UID.JPEGFullProgressionNonHierarchical1012));
    Assertions.assertEquals(
        TransferSyntaxType.JPEG_LOSSLESS, TransferSyntaxType.forUID(UID.JPEGLossless));
    Assertions.assertEquals(
        TransferSyntaxType.JPEG_LS, TransferSyntaxType.forUID(UID.JPEGLSLossless));
    Assertions.assertEquals(
        TransferSyntaxType.JPEG_2000, TransferSyntaxType.forUID(UID.JPEG2000Lossless));
    Assertions.assertEquals(TransferSyntaxType.JPIP, TransferSyntaxType.forUID(UID.JPIPReferenced));
    Assertions.assertEquals(TransferSyntaxType.MPEG, TransferSyntaxType.forUID(UID.MPEG2MPML));
    Assertions.assertEquals(TransferSyntaxType.RLE, TransferSyntaxType.forUID(UID.RLELossless));
    Assertions.assertEquals(TransferSyntaxType.UNKNOWN, TransferSyntaxType.forUID("unknown"));
  }

  @Test
  void isLossyCompressionReturnsCorrectValue() {
    Assertions.assertTrue(TransferSyntaxType.isLossyCompression(UID.JPEGBaseline8Bit));
    Assertions.assertTrue(TransferSyntaxType.isLossyCompression(UID.JPEGExtended12Bit));
    Assertions.assertTrue(TransferSyntaxType.isLossyCompression(UID.JPEGLSNearLossless));
    Assertions.assertTrue(TransferSyntaxType.isLossyCompression(UID.JPEG2000));
    Assertions.assertTrue(TransferSyntaxType.isLossyCompression(UID.JPEG2000MC));
    Assertions.assertTrue(TransferSyntaxType.isLossyCompression(UID.HTJ2K));
    Assertions.assertTrue(TransferSyntaxType.isLossyCompression(UID.MPEG4HP41));

    Assertions.assertFalse(TransferSyntaxType.isLossyCompression(UID.ExplicitVRLittleEndian));
  }

  @Test
  void isYBRCompressionReturnsCorrectValue() {
    Assertions.assertTrue(TransferSyntaxType.isYBRCompression(UID.JPEGBaseline8Bit));
    Assertions.assertTrue(TransferSyntaxType.isYBRCompression(UID.JPEGExtended12Bit));
    Assertions.assertTrue(TransferSyntaxType.isYBRCompression(UID.JPEG2000Lossless));
    Assertions.assertTrue(TransferSyntaxType.isYBRCompression(UID.HTJ2K));

    Assertions.assertFalse(TransferSyntaxType.isYBRCompression(UID.ExplicitVRLittleEndian));
  }

  @Test
  void adjustBitsStoredTo12ReturnsCorrectValue() {
    Attributes attrs = new Attributes();
    attrs.setInt(Tag.BitsStored, VR.US, 10);
    Assertions.assertTrue(TransferSyntaxType.JPEG_EXTENDED.adjustBitsStoredTo12(attrs));
    Assertions.assertEquals(12, attrs.getInt(Tag.BitsStored, 0));
    Assertions.assertEquals(11, attrs.getInt(Tag.HighBit, 0));

    attrs.setInt(Tag.BitsStored, VR.US, 7);
    Assertions.assertFalse(TransferSyntaxType.JPEG_EXTENDED.adjustBitsStoredTo12(attrs));
    Assertions.assertEquals(7, attrs.getInt(Tag.BitsStored, 0));

    attrs.setInt(Tag.BitsStored, VR.US, 12);
    Assertions.assertFalse(TransferSyntaxType.JPEG_EXTENDED.adjustBitsStoredTo12(attrs));
    Assertions.assertEquals(12, attrs.getInt(Tag.BitsStored, 0));

    attrs.setInt(Tag.BitsStored, VR.US, 10);
    Assertions.assertFalse(TransferSyntaxType.NATIVE.adjustBitsStoredTo12(attrs));
    Assertions.assertEquals(10, attrs.getInt(Tag.BitsStored, 0));
  }

  @Test
  void isPixeldataEncapsulatedReturnsCorrectValue() {
    Assertions.assertTrue(TransferSyntaxType.JPEG_BASELINE.isPixeldataEncapsulated());
    Assertions.assertFalse(TransferSyntaxType.NATIVE.isPixeldataEncapsulated());
  }

  @Test
  void canEncodeSignedReturnsCorrectValue() {
    Assertions.assertFalse(TransferSyntaxType.JPEG_BASELINE.canEncodeSigned());
    Assertions.assertTrue(TransferSyntaxType.NATIVE.canEncodeSigned());
  }

  @Test
  void mayFrameSpanMultipleFragmentsReturnsCorrectValue() {
    Assertions.assertTrue(TransferSyntaxType.JPEG_BASELINE.mayFrameSpanMultipleFragments());
    Assertions.assertFalse(TransferSyntaxType.NATIVE.mayFrameSpanMultipleFragments());
  }

  @Test
  void getPlanarConfigurationReturnsCorrectValue() {
    Assertions.assertEquals(0, TransferSyntaxType.JPEG_BASELINE.getPlanarConfiguration());
    Assertions.assertEquals(0, TransferSyntaxType.NATIVE.getPlanarConfiguration());
  }

  @Test
  void getMaxBitsStoredReturnsCorrectValue() {
    Assertions.assertEquals(8, TransferSyntaxType.JPEG_BASELINE.getMaxBitsStored());
    Assertions.assertEquals(16, TransferSyntaxType.NATIVE.getMaxBitsStored());
  }
}
