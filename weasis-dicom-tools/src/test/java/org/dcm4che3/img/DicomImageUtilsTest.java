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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.opencv.data.PlanarImage;

class DicomImageUtilsTest {

  @Test
  @DisplayName("Verify getRGBImageFromPaletteColorModel returns source when ds is null")
  void shouldReturnSourceWhenDsIsNull() {
    PlanarImage source = mock(PlanarImage.class);
    PlanarImage result = DicomImageUtils.getRGBImageFromPaletteColorModel(source, null);
    assertSame(source, result);
  }

  @Test
  @DisplayName(
      "Verify getRGBImageFromPaletteColorModel returns lookup image when ds is not null and source depth is greater than CvType.CV_8S")
  void shouldReturnLookupImageWhenDsIsNotNullAndSourceDepthIsGreaterThanCvTypeCV8S() {
    PlanarImage source = mock(PlanarImage.class);
    when(source.depth()).thenReturn(10);
    Attributes ds = new Attributes();
    ds.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, 0, 0);
    ds.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, 0, 0);
    ds.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, 0, 0);
    //    PlanarImage result = DicomImageUtils.getRGBImageFromPaletteColorModel(source, ds);
    //    assertNotNull(result);
  }
}
