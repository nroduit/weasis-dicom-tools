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
import static org.mockito.Mockito.*;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.osgi.OpenCVNativeLoader;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

@DisplayName("OverlayData Tests")
class OverlayDataTest {

  private static final int GROUP1_OFFSET = 1 << OverlayData.GROUP_OFFSET_SHIFT;
  private static final int GROUP2_OFFSET = 2 << OverlayData.GROUP_OFFSET_SHIFT;

  @Mock private ImageDescriptor mockImageDescriptor;
  @Mock private DicomImageReadParam mockParams;
  @Mock private PrDicomObject mockPrDicomObject;

  @BeforeAll
  static void loadNativeLib() {
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();
  }

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Nested
  @DisplayName("Overlay Data Extraction Tests")
  class OverlayDataExtractionTests {

    @Test
    @DisplayName("Should return empty list when no overlays are present")
    void shouldReturnEmptyListWhenNoOverlaysPresent() {
      // Given
      Attributes dcm = new Attributes();

      // When
      List<OverlayData> result = OverlayData.getOverlayData(dcm, 0);

      // Then
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list when DICOM attributes are null")
    void shouldReturnEmptyListWhenDicomAttributesNull() {
      // When
      List<OverlayData> result = OverlayData.getOverlayData(null, 0xffff);

      // Then
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list when activation mask is zero")
    void shouldReturnEmptyListWhenActivationMaskZero() {
      // Given
      Attributes dcm = createDicomWithOverlay(GROUP1_OFFSET, createOverlayData());

      // When
      List<OverlayData> result = OverlayData.getOverlayData(dcm, 0);

      // Then
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should extract single overlay when present")
    void shouldExtractSingleOverlayWhenPresent() {
      // Given
      byte[] testData = {0, 0, 0, 1, 1, 1, 0, 0, 0};
      Attributes dcm = createDicomWithOverlay(GROUP1_OFFSET, testData);

      // When
      List<OverlayData> result = OverlayData.getOverlayData(dcm, 0xffff);

      // Then
      assertEquals(1, result.size());
      OverlayData overlay = result.get(0);
      assertEquals(GROUP1_OFFSET, overlay.groupOffset());
      assertEquals(3, overlay.rows());
      assertEquals(3, overlay.columns());
      assertEquals(1, overlay.imageFrameOrigin());
      assertEquals(1, overlay.framesInOverlay());
      assertArrayEquals(new int[] {1, 1}, overlay.origin());
      assertArrayEquals(testData, overlay.data());
    }

    @Test
    @DisplayName("Should extract multiple overlays when present")
    void shouldExtractMultipleOverlaysWhenPresent() {
      // Given
      byte[] testData1 = {1, 2, 3};
      byte[] testData2 = {4, 5, 6};
      Attributes dcm = new Attributes();
      addOverlayToDicom(dcm, GROUP1_OFFSET, testData1, 2, 2);
      addOverlayToDicom(dcm, GROUP2_OFFSET, testData2, 3, 3);

      // When
      List<OverlayData> result = OverlayData.getOverlayData(dcm, 0xffff);

      // Then
      assertEquals(2, result.size());

      OverlayData overlay1 =
          result.stream().filter(o -> o.groupOffset() == GROUP1_OFFSET).findFirst().orElseThrow();
      assertEquals(2, overlay1.rows());
      assertEquals(2, overlay1.columns());
      assertArrayEquals(testData1, overlay1.data());

      OverlayData overlay2 =
          result.stream().filter(o -> o.groupOffset() == GROUP2_OFFSET).findFirst().orElseThrow();
      assertEquals(3, overlay2.rows());
      assertEquals(3, overlay2.columns());
      assertArrayEquals(testData2, overlay2.data());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16, 255, 0xffff})
    @DisplayName("Should respect activation mask")
    void shouldRespectActivationMask(int activationMask) {
      // Given
      Attributes dcm = new Attributes();
      for (int i = 0; i < OverlayData.MAX_OVERLAY_GROUPS; i++) {
        int groupOffset = i << OverlayData.GROUP_OFFSET_SHIFT;
        addOverlayToDicom(dcm, groupOffset, new byte[] {(byte) i}, 1, 1);
      }

      // When
      List<OverlayData> result = OverlayData.getOverlayData(dcm, activationMask);

      // Then
      int expectedCount = Integer.bitCount(activationMask);
      assertEquals(expectedCount, result.size());

      for (OverlayData overlay : result) {
        int groupIndex = overlay.groupOffset() >> OverlayData.GROUP_OFFSET_SHIFT;
        assertTrue(
            (activationMask & (1 << groupIndex)) != 0,
            "Group " + groupIndex + " should be activated");
      }
    }

    @Test
    @DisplayName("Should handle missing overlay data gracefully")
    void shouldHandleMissingOverlayDataGracefully() {
      // Given
      Attributes dcm = new Attributes();
      dcm.setInt(Tag.OverlayRows | GROUP1_OFFSET, VR.US, 3);
      dcm.setInt(Tag.OverlayColumns | GROUP1_OFFSET, VR.US, 3);
      // Note: no overlay data set

      // When
      List<OverlayData> result = OverlayData.getOverlayData(dcm, 0xffff);

      // Then
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("Presentation State Overlay Tests")
  class PresentationStateOverlayTests {

    @Test
    @DisplayName("Should extract PR overlays when activation layer is present")
    void shouldExtractPrOverlaysWhenActivationLayerPresent() {
      // Given
      byte[] testData = {1, 2, 3, 4, 5, 6};
      Attributes dcm = createDicomWithOverlay(GROUP1_OFFSET, testData);
      dcm.setString(Tag.OverlayActivationLayer | GROUP1_OFFSET, VR.CS, "LAYER1");

      // When
      List<OverlayData> result = OverlayData.getPrOverlayData(dcm, 0xffff);

      // Then
      assertEquals(1, result.size());
      assertArrayEquals(testData, result.get(0).data());
    }

    @Test
    @DisplayName("Should not extract PR overlays when activation layer is missing")
    void shouldNotExtractPrOverlaysWhenActivationLayerMissing() {
      // Given
      Attributes dcm = createDicomWithOverlay(GROUP1_OFFSET, createOverlayData());
      // Note: no activation layer set

      // When
      List<OverlayData> result = OverlayData.getPrOverlayData(dcm, 0xffff);

      // Then
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should extract multiple PR overlays with different layers")
    void shouldExtractMultiplePrOverlaysWithDifferentLayers() {
      // Given
      Attributes dcm = new Attributes();
      addOverlayToDicom(dcm, GROUP1_OFFSET, new byte[] {1, 2}, 1, 2);
      dcm.setString(Tag.OverlayActivationLayer | GROUP1_OFFSET, VR.CS, "LAYER1");

      addOverlayToDicom(dcm, GROUP2_OFFSET, new byte[] {3, 4}, 2, 1);
      dcm.setString(Tag.OverlayActivationLayer | GROUP2_OFFSET, VR.CS, "LAYER2");

      // When
      List<OverlayData> result = OverlayData.getPrOverlayData(dcm, 0xffff);

      // Then
      assertEquals(2, result.size());
    }
  }

  @Nested
  @DisplayName("Overlay Image Generation Tests")
  class OverlayImageGenerationTests {

    private PlanarImage sourceImage;
    private PlanarImage currentImage;

    @BeforeEach
    void setUpImages() {
      sourceImage = new ImageCV(9, 9, CvType.CV_8UC1);
      currentImage = new ImageCV(9, 9, CvType.CV_8UC1);
    }

    @Test
    @DisplayName("Should return current image when no overlays present")
    void shouldReturnCurrentImageWhenNoOverlaysPresent() {
      // Given
      when(mockImageDescriptor.getEmbeddedOverlay()).thenReturn(Collections.emptyList());
      when(mockImageDescriptor.getOverlayData()).thenReturn(Collections.emptyList());
      when(mockParams.getPresentationState()).thenReturn(Optional.empty());

      // When
      PlanarImage result =
          OverlayData.getOverlayImage(
              sourceImage, currentImage, mockImageDescriptor, mockParams, 0);

      // Then
      assertSame(currentImage, result);
    }

    @Test
    @DisplayName("Should return current image when dimensions don't match")
    void shouldReturnCurrentImageWhenDimensionsDontMatch() {
      // Given
      PlanarImage differentSizeImage = new ImageCV(5, 5, CvType.CV_8UC1);
      when(mockImageDescriptor.getEmbeddedOverlay()).thenReturn(Collections.emptyList());
      when(mockImageDescriptor.getOverlayData())
          .thenReturn(Collections.singletonList(createTestOverlayData()));
      when(mockParams.getPresentationState()).thenReturn(Optional.empty());

      // When
      PlanarImage result =
          OverlayData.getOverlayImage(
              sourceImage, differentSizeImage, mockImageDescriptor, mockParams, 0);

      // Then
      assertSame(differentSizeImage, result);
    }

    @Test
    @DisplayName("Should apply overlays when present")
    void shouldApplyOverlaysWhenPresent() {
      // Given
      List<OverlayData> overlayDataList =
          Collections.singletonList(
              new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9}));

      when(mockImageDescriptor.getEmbeddedOverlay()).thenReturn(Collections.emptyList());
      when(mockImageDescriptor.getOverlayData()).thenReturn(overlayDataList);
      when(mockParams.getPresentationState()).thenReturn(Optional.empty());
      when(mockParams.getOverlayColor()).thenReturn(Optional.of(Color.RED));

      // When
      PlanarImage result =
          OverlayData.getOverlayImage(
              sourceImage, currentImage, mockImageDescriptor, mockParams, 0);

      // Then
      assertNotSame(currentImage, result);
      assertNotNull(result);
    }

    @Test
    @DisplayName("Should apply presentation state overlays")
    void shouldApplyPresentationStateOverlays() {
      // Given
      List<OverlayData> prOverlays = Collections.singletonList(createTestOverlayData());

      when(mockPrDicomObject.getOverlays()).thenReturn(prOverlays);
      when(mockImageDescriptor.getEmbeddedOverlay()).thenReturn(Collections.emptyList());
      when(mockImageDescriptor.getOverlayData()).thenReturn(Collections.emptyList());
      when(mockParams.getPresentationState()).thenReturn(Optional.of(mockPrDicomObject));
      when(mockParams.getOverlayColor()).thenReturn(Optional.empty());

      // When
      PlanarImage result =
          OverlayData.getOverlayImage(
              sourceImage, currentImage, mockImageDescriptor, mockParams, 0);

      // Then
      assertNotSame(currentImage, result);
      verify(mockPrDicomObject).getOverlays();
    }

    @Test
    @DisplayName("Should create overlay image from overlay list")
    void shouldCreateOverlayImageFromOverlayList() {
      // Given
      PlanarImage source = ImageCV.fromMat(Mat.zeros(9, 9, CvType.CV_8UC1));
      OverlayData overlayData =
          new OverlayData(0, 4, 5, 1, 1, new int[] {3, 3}, new byte[] {7, 5, 1});

      // When
      PlanarImage result =
          OverlayData.getOverlayImage(source, Collections.singletonList(overlayData), 0);

      // Then
      assertEquals(source.width(), result.width());
      assertEquals(source.height(), result.height());

      // Verify overlay pixels are set correctly
      byte[] pixelData = new byte[result.width() * result.height()];
      result.get(0, 0, pixelData);

      // Check specific pixel values based on overlay data
      assertEquals(0, pixelData[0]); // Background
      assertEquals(-1, pixelData[20]); // Overlay pixel
      assertEquals(-1, pixelData[21]); // Overlay pixel
      assertEquals(-1, pixelData[22]); // Overlay pixel
      assertEquals(0, pixelData[13]); // Background
    }

    @Test
    @DisplayName("Should handle embedded overlays")
    void shouldHandleEmbeddedOverlays() {
      // Given
      List<EmbeddedOverlay> embeddedOverlays = Collections.singletonList(new EmbeddedOverlay(0, 2));

      // Create source image with embedded overlay bits
      ImageCV sourceWithOverlay = new ImageCV(9, 9, CvType.CV_8UC1);
      sourceWithOverlay.put(0, 0, new byte[] {4, 0, 0, 0, 0, 0, 0, 0, 0}); // bit 2 set

      when(mockImageDescriptor.getEmbeddedOverlay()).thenReturn(embeddedOverlays);
      when(mockImageDescriptor.getOverlayData()).thenReturn(Collections.emptyList());
      when(mockParams.getPresentationState()).thenReturn(Optional.empty());
      when(mockParams.getOverlayColor()).thenReturn(Optional.of(Color.WHITE));

      // When
      PlanarImage result =
          OverlayData.getOverlayImage(
              sourceWithOverlay, currentImage, mockImageDescriptor, mockParams, 0);

      // Then
      assertNotSame(currentImage, result);
    }
  }

  @Nested
  @DisplayName("Frame Handling Tests")
  class FrameHandlingTests {

    @Test
    @DisplayName("Should handle multi-frame overlays correctly")
    void shouldHandleMultiFrameOverlaysCorrectly() {
      // Given
      OverlayData multiFrameOverlay =
          new OverlayData(
              0, 2, 2, 1, 3, new int[] {1, 1}, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
      PlanarImage source = new ImageCV(5, 5, CvType.CV_8UC1);

      // When - Test different frames
      PlanarImage frame0 =
          OverlayData.getOverlayImage(source, Collections.singletonList(multiFrameOverlay), 0);
      PlanarImage frame1 =
          OverlayData.getOverlayImage(source, Collections.singletonList(multiFrameOverlay), 1);
      PlanarImage frame2 =
          OverlayData.getOverlayImage(source, Collections.singletonList(multiFrameOverlay), 2);
      PlanarImage frameOutOfRange =
          OverlayData.getOverlayImage(source, Collections.singletonList(multiFrameOverlay), 5);

      // Then
      assertNotNull(frame0);
      assertNotNull(frame1);
      assertNotNull(frame2);
      assertNotNull(frameOutOfRange);

      // Verify dimensions
      assertEquals(source.width(), frame0.width());
      assertEquals(source.height(), frame0.height());
    }

    @Test
    @DisplayName("Should handle frame origin offset correctly")
    void shouldHandleFrameOriginOffsetCorrectly() {
      // Given
      OverlayData overlayWithOffset =
          new OverlayData(0, 2, 2, 3, 2, new int[] {1, 1}, new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
      PlanarImage source = new ImageCV(5, 5, CvType.CV_8UC1);

      // When - Test frames before, at, and after the frame origin
      PlanarImage beforeOrigin =
          OverlayData.getOverlayImage(source, Collections.singletonList(overlayWithOffset), 1);
      PlanarImage atOrigin =
          OverlayData.getOverlayImage(source, Collections.singletonList(overlayWithOffset), 2);
      PlanarImage afterOrigin =
          OverlayData.getOverlayImage(source, Collections.singletonList(overlayWithOffset), 3);

      // Then
      assertNotNull(beforeOrigin);
      assertNotNull(atOrigin);
      assertNotNull(afterOrigin);
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsAndHashCodeTests {

    private OverlayData baseOverlay;
    private OverlayData identicalOverlay;

    @BeforeEach
    void setUpOverlays() {
      baseOverlay = new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9});
      identicalOverlay = new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9});
    }

    @Test
    @DisplayName("Should be equal to identical overlay")
    void shouldBeEqualToIdenticalOverlay() {
      assertEquals(baseOverlay, identicalOverlay);
      assertEquals(baseOverlay.hashCode(), identicalOverlay.hashCode());
      assertEquals(baseOverlay.toString(), identicalOverlay.toString());
    }

    @Test
    @DisplayName("Should not be equal to null")
    void shouldNotBeEqualToNull() {
      assertNotEquals(null, baseOverlay);
    }

    @Test
    @DisplayName("Should not be equal to different class")
    void shouldNotBeEqualToDifferentClass() {
      assertNotEquals(baseOverlay, "not an overlay");
    }

    @ParameterizedTest
    @DisplayName("Should not be equal when fields differ")
    @ValueSource(
        strings = {
          "groupOffset",
          "rows",
          "columns",
          "imageFrameOrigin",
          "framesInOverlay",
          "origin",
          "data"
        })
    void shouldNotBeEqualWhenFieldsDiffer(String fieldName) {
      OverlayData differentOverlay =
          switch (fieldName) {
            case "groupOffset" ->
                new OverlayData(1, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9});
            case "rows" -> new OverlayData(0, 3, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9});
            case "columns" -> new OverlayData(0, 2, 4, 1, 1, new int[] {1, 1}, new byte[] {8, 9});
            case "imageFrameOrigin" ->
                new OverlayData(0, 2, 3, 2, 1, new int[] {1, 1}, new byte[] {8, 9});
            case "framesInOverlay" ->
                new OverlayData(0, 2, 3, 1, 2, new int[] {1, 1}, new byte[] {8, 9});
            case "origin" -> new OverlayData(0, 2, 3, 1, 1, new int[] {2, 2}, new byte[] {8, 9});
            case "data" -> new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 10});
            default -> throw new IllegalArgumentException("Unknown field: " + fieldName);
          };

      assertNotEquals(baseOverlay, differentOverlay);
      assertNotEquals(baseOverlay.hashCode(), differentOverlay.hashCode());
    }

    @Test
    @DisplayName("Should handle null arrays in comparison")
    void shouldHandleNullArraysInComparison() {
      OverlayData overlayWithNullOrigin = new OverlayData(0, 2, 3, 1, 1, null, new byte[] {8, 9});
      OverlayData overlayWithNullData = new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, null);

      assertNotEquals(baseOverlay, overlayWithNullOrigin);
      assertNotEquals(baseOverlay, overlayWithNullData);
      assertNotEquals(overlayWithNullOrigin, overlayWithNullData);
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle zero-sized overlays")
    void shouldHandleZeroSizedOverlays() {
      OverlayData zeroSizedOverlay =
          new OverlayData(0, 0, 0, 1, 1, new int[] {1, 1}, new byte[] {});
      try (ImageCV source = new ImageCV(5, 5, CvType.CV_8UC1)) {
        assertDoesNotThrow(
            () -> {
              PlanarImage result =
                  OverlayData.getOverlayImage(
                      source, Collections.singletonList(zeroSizedOverlay), 0);
              assertNotNull(result);
            });
      }
    }

    @Test
    @DisplayName("Should handle overlay data out of bounds gracefully")
    void shouldHandleOverlayDataOutOfBoundsGracefully() {
      // Create overlay with data that might cause out-of-bounds access
      OverlayData overlayOutOfBounds =
          new OverlayData(
              0, 10, 10, 1, 1, new int[] {1, 1}, new byte[] {1, 2}); // Small data for large overlay
      try (ImageCV source = new ImageCV(5, 5, CvType.CV_8UC1)) {

        assertDoesNotThrow(
            () -> {
              PlanarImage result =
                  OverlayData.getOverlayImage(
                      source, Collections.singletonList(overlayOutOfBounds), 0);
              assertNotNull(result);
            });
      }
    }

    @Test
    @DisplayName("Should handle large overlay origins")
    void shouldHandleLargeOverlayOrigins() {
      OverlayData overlayWithLargeOrigin =
          new OverlayData(0, 2, 2, 1, 1, new int[] {100, 100}, new byte[] {1, 2, 3, 4});
      PlanarImage source = new ImageCV(5, 5, CvType.CV_8UC1);

      assertDoesNotThrow(
          () -> {
            PlanarImage result =
                OverlayData.getOverlayImage(
                    source, Collections.singletonList(overlayWithLargeOrigin), 0);
            assertNotNull(result);
          });
    }

    @Test
    @DisplayName("Should handle negative frame indices")
    void shouldHandleNegativeFrameIndices() {
      OverlayData overlay = createTestOverlayData();
      try (ImageCV source = new ImageCV(5, 5, CvType.CV_8UC1)) {

        assertDoesNotThrow(
            () -> {
              PlanarImage result =
                  OverlayData.getOverlayImage(source, Collections.singletonList(overlay), -1);
              assertNotNull(result);
            });
      }
    }
  }

  // Helper methods

  private static byte[] createOverlayData() {
    return new byte[] {0, 0, 0, 1, 1, 1, 0, 0, 0};
  }

  private static OverlayData createTestOverlayData() {
    return new OverlayData(0, 3, 3, 1, 1, new int[] {1, 1}, createOverlayData());
  }

  private static Attributes createDicomWithOverlay(int groupOffset, byte[] data) {
    Attributes dcm = new Attributes();
    addOverlayToDicom(dcm, groupOffset, data, 3, 3);
    return dcm;
  }

  private static void addOverlayToDicom(
      Attributes dcm, int groupOffset, byte[] data, int rows, int columns) {
    dcm.setString(Tag.OverlayType | groupOffset, VR.CS, "G");
    dcm.setInt(Tag.OverlayOrigin | groupOffset, VR.SS, 1, 1);
    dcm.setInt(Tag.OverlayRows | groupOffset, VR.US, rows);
    dcm.setInt(Tag.OverlayColumns | groupOffset, VR.US, columns);
    dcm.setInt(Tag.OverlayBitsAllocated | groupOffset, VR.US, 1);
    dcm.setInt(Tag.OverlayBitPosition | groupOffset, VR.US, 0);
    dcm.setBytes(Tag.OverlayData | groupOffset, VR.OB, data);
  }
}
