/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.osgi.OpenCVNativeLoader;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

class OverlayDataTest {
  @BeforeAll
  public static void loadNativeLib() {
    // Load the native OpenCV library
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();
  }

  @Test
  @DisplayName("Verify getOverlayData returns empty list when no overlays are present")
  void shouldReturnEmptyListWhenNoOverlaysArePresent() {
    Attributes dcm = new Attributes();
    List<OverlayData> result = OverlayData.getOverlayData(dcm, 0);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("Verify getOverlayData returns overlays when overlays are present")
  void shouldReturnOverlaysWhenOverlaysArePresent() {
    byte[] data = new byte[] {0, 0, 0, 1, 1, 1, 0, 0, 0};

    Attributes dcm = new Attributes();
    dcm.setInt(Tag.BitsAllocated, VR.US, 16);
    dcm.setInt(Tag.BitsStored, VR.US, 12);

    int gg0000 = 1 << 17;
    ;
    dcm.setString(Tag.OverlayType | gg0000, VR.CS, "G");
    dcm.setInt(Tag.OverlayOrigin | gg0000, VR.SS, 1, 1);
    dcm.setInt(Tag.OverlayRows | gg0000, VR.US, 3);
    dcm.setInt(Tag.OverlayColumns | gg0000, VR.US, 3);
    dcm.setInt(Tag.OverlayBitsAllocated | gg0000, VR.US, 1);
    dcm.setInt(Tag.OverlayBitPosition | gg0000, VR.US, 0);
    dcm.setBytes(Tag.OverlayData | gg0000, VR.OB, data);
    List<OverlayData> result = OverlayData.getOverlayData(dcm, 0xffff);

    assertEquals(1, result.size());
    assertEquals(1 << 17, result.get(0).groupOffset());
    assertEquals(3, result.get(0).rows());
    assertEquals(3, result.get(0).columns());
    assertEquals(1, result.get(0).imageFrameOrigin());
    assertEquals(1, result.get(0).framesInOverlay());
    assertArrayEquals(data, result.get(0).data());

    // Test with PR overlay
    dcm.setString(Tag.OverlayActivationLayer | gg0000, VR.CS, "LAYER2");
    result = OverlayData.getPrOverlayData(dcm, -1);
    assertEquals(1, result.size());
    assertEquals(1 << 17, result.get(0).groupOffset());
    assertEquals(3, result.get(0).rows());
    assertEquals(3, result.get(0).columns());
    assertEquals(1, result.get(0).imageFrameOrigin());
    assertEquals(1, result.get(0).framesInOverlay());
    assertArrayEquals(data, result.get(0).data());
  }

  @Test
  @DisplayName("Verify equals method ")
  void verifyEquals() {
    OverlayData overlayData1 = new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9});
    OverlayData overlayData2 = new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9});
    assertNotEquals(overlayData1, null);
    assertEquals(overlayData1, overlayData1);
    assertEquals(overlayData1, overlayData2);
    assertEquals(overlayData1.hashCode(), overlayData2.hashCode());
    assertEquals(overlayData1.toString(), overlayData2.toString());

    overlayData2 = new OverlayData(1, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9});
    assertNotEquals(overlayData1, overlayData2);
    assertNotEquals(overlayData1.hashCode(), overlayData2.hashCode());

    overlayData2 = new OverlayData(1, 2, 3, 1, 1, null, new byte[] {8, 9});
    assertNotEquals(overlayData1, overlayData2);
    assertNotEquals(overlayData1.hashCode(), overlayData2.hashCode());

    overlayData2 = new OverlayData(1, 2, 3, 1, 1, null, new byte[] {8, 10});
    assertNotEquals(overlayData1, overlayData2);
    assertNotEquals(overlayData1.hashCode(), overlayData2.hashCode());
  }

  @Test
  @DisplayName("Verify getOverlayImage")
  void verifyGetOverlayImage() {
    PlanarImage imageSource = new ImageCV(9, 9, CvType.CV_8UC1);
    PlanarImage currentImage = new ImageCV(9, 9, CvType.CV_8UC1);
    ImageDescriptor desc = Mockito.mock(ImageDescriptor.class);
    DicomImageReadParam params = Mockito.mock(DicomImageReadParam.class);

    when(desc.getEmbeddedOverlay()).thenReturn(new ArrayList<>());
    when(desc.getOverlayData()).thenReturn(new ArrayList<>());
    PlanarImage result = OverlayData.getOverlayImage(imageSource, currentImage, desc, params, 0);
    assertEquals(currentImage, result);

    List<OverlayData> overlayDataList = new ArrayList<>();
    OverlayData overlayData = new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9});
    overlayDataList.add(overlayData);
    when(desc.getEmbeddedOverlay()).thenReturn(new ArrayList<>());
    when(desc.getOverlayData()).thenReturn(overlayDataList);

    result = OverlayData.getOverlayImage(imageSource, currentImage, desc, params, 0);

    assertNotEquals(currentImage, result);
  }

  @Test
  @DisplayName("Verify getOverlayImage2")
  void verifyGetOverlayImage2() {
    PlanarImage imageSource = ImageCV.fromMat(Mat.zeros(9, 9, CvType.CV_8UC1));
    OverlayData overlayData =
        new OverlayData(0, 4, 5, 1, 1, new int[] {3, 3}, new byte[] {7, 5, 1});

    PlanarImage result =
        OverlayData.getOverlayImage(imageSource, Collections.singletonList(overlayData), 0);

    assertEquals(imageSource.width(), result.width());
    assertEquals(imageSource.height(), result.height());

    byte[] pixelData = new byte[result.width() * result.height()];
    result.get(0, 0, pixelData);
    assertEquals(0, pixelData[0]);
    assertEquals(-1, pixelData[20]);
    assertEquals(-1, pixelData[21]);
    assertEquals(-1, pixelData[22]);
    assertEquals(0, pixelData[13]);
  }
}
