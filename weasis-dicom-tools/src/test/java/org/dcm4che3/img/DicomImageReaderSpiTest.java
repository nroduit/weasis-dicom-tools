/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DicomImageReaderSpiTest {

  @Test
  @DisplayName("Check DicomImageReaderSpi")
  void checkDicomImageReaderSpi() throws IOException {
    DicomImageReaderSpi readerSpi = new DicomImageReaderSpi();
    Assertions.assertEquals("DICOM Image Reader (dcm4che)", readerSpi.getDescription(null));
    Assertions.assertInstanceOf(DicomImageReader.class, readerSpi.createReaderInstance(null));

    byte[] dicomBytes = new byte[132];
    dicomBytes[128] = 'D';
    dicomBytes[129] = 'I';
    dicomBytes[130] = 'C';
    dicomBytes[131] = 'M';
    ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(dicomBytes));
    Assertions.assertTrue(readerSpi.canDecodeInput(iis));

    dicomBytes[128] = 'A';
    Assertions.assertFalse(
        readerSpi.canDecodeInput(
            new MemoryCacheImageInputStream(new ByteArrayInputStream(dicomBytes))));
  }
}
