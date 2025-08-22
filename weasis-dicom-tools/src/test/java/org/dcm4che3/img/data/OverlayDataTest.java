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

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.osgi.OpenCVNativeLoader;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

@DisplayName("OverlayData Tests")
@DisplayNameGeneration(ReplaceUnderscores.class)
class OverlayDataTest {

  private static final int GROUP1_OFFSET = 1 << OverlayData.GROUP_OFFSET_SHIFT;
  private static final int GROUP2_OFFSET = 2 << OverlayData.GROUP_OFFSET_SHIFT;

  @BeforeAll
  static void load_native_lib() {
    var loader = new OpenCVNativeLoader();
    loader.init();
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Presentation_state_overlay_tests {

    @Test
    void should_extract_pr_overlays_when_activation_layer_is_present() {
      byte[] testData = {1, 2, 3, 4, 5, 6};
      var dcm = OverlayDataTest.createDicomWithOverlay(GROUP1_OFFSET, testData);
      dcm.setString(Tag.OverlayActivationLayer | GROUP1_OFFSET, VR.CS, "LAYER1");

      var result = OverlayData.getPrOverlayData(dcm, 0xffff);

      assertEquals(1, result.size());
      assertArrayEquals(testData, result.get(0).data());
    }

    @Test
    void should_not_extract_pr_overlays_when_activation_layer_is_missing() {
      var dcm = createDicomWithOverlay(GROUP1_OFFSET, createTestOverlayBytes());

      var result = OverlayData.getPrOverlayData(dcm, 0xffff);

      assertTrue(result.isEmpty());
    }

    @Test
    void should_extract_multiple_pr_overlays_with_different_layers() {
      var dcm = new Attributes();
      addOverlayToDicom(dcm, GROUP1_OFFSET, new byte[] {1, 2}, 1, 2);
      dcm.setString(Tag.OverlayActivationLayer | GROUP1_OFFSET, VR.CS, "LAYER1");

      addOverlayToDicom(dcm, GROUP2_OFFSET, new byte[] {3, 4}, 2, 1);
      dcm.setString(Tag.OverlayActivationLayer | GROUP2_OFFSET, VR.CS, "LAYER2");

      var result = OverlayData.getPrOverlayData(dcm, 0xffff);

      assertEquals(2, result.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LAYER1", "GRAPHICS", "TEXT", "OVERLAY1", "USER_DEFINED"})
    void should_handle_different_activation_layer_names(String layerName) {
      var dcm = createDicomWithOverlay(GROUP1_OFFSET, createTestOverlayBytes());
      dcm.setString(Tag.OverlayActivationLayer | GROUP1_OFFSET, VR.CS, layerName);

      var result = OverlayData.getPrOverlayData(dcm, 0xffff);

      assertEquals(1, result.size());
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Overlay_image_generation_tests {

    private final TestImageFactory imageFactory = new TestImageFactory();

    @Test
    void should_return_current_image_when_no_overlays_present() {
      var sourceImage = imageFactory.createTestImage(9, 9);
      var currentImage = imageFactory.createTestImage(9, 9);
      var descriptor = createTestImageDescriptor();
      var params = new TestDicomImageReadParam();

      var result = OverlayData.getOverlayImage(sourceImage, currentImage, descriptor, params, 0);

      assertSame(currentImage, result);
    }

    @Test
    void should_return_current_image_when_dimensions_dont_match() {
      var sourceImage = imageFactory.createTestImage(9, 9);
      var currentImage = imageFactory.createTestImage(5, 5);
      var descriptor = createTestImageDescriptor(List.of(createTestOverlayData()));
      var params = new TestDicomImageReadParam();

      var result = OverlayData.getOverlayImage(sourceImage, currentImage, descriptor, params, 0);

      assertSame(currentImage, result);
    }

    @Test
    void should_apply_overlays_when_present() {
      var sourceImage = imageFactory.createTestImage(9, 9);
      var currentImage = imageFactory.createTestImage(9, 9);
      var overlayData = new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9});
      var descriptor = createTestImageDescriptor(List.of(overlayData));
      var params = new TestDicomImageReadParam().withOverlayColor(Color.RED);

      var result = OverlayData.getOverlayImage(sourceImage, currentImage, descriptor, params, 0);

      assertNotSame(currentImage, result);
      assertNotNull(result);
    }

    @Test
    void should_apply_presentation_state_overlays() {
      var sourceImage = imageFactory.createTestImage(9, 9);
      var currentImage = imageFactory.createTestImage(9, 9);
      var prOverlays = List.of(createTestOverlayData());
      var descriptor = createTestImageDescriptor();
      Attributes attributes = new Attributes();
      attributes.setString(Tag.OverlayActivationLayer | GROUP1_OFFSET, VR.CS, "LAYER1");
      attributes.setString(Tag.OverlayRows | GROUP1_OFFSET, VR.US, "2");
      attributes.setString(Tag.OverlayColumns | GROUP1_OFFSET, VR.US, "3");
      attributes.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
      attributes.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5.6");
      var params =
          new TestDicomImageReadParam()
              .withPresentationState(new TestPrDicomObject(attributes, prOverlays));

      var result = OverlayData.getOverlayImage(sourceImage, currentImage, descriptor, params, 0);

      assertNotSame(currentImage, result);
    }

    @Test
    void should_create_overlay_image_from_overlay_list() {
      var source = ImageCV.fromMat(Mat.zeros(9, 9, CvType.CV_8UC1));
      var overlayData = new OverlayData(0, 4, 5, 1, 1, new int[] {3, 3}, new byte[] {7, 5, 1});

      var result = OverlayData.getOverlayImage(source, List.of(overlayData), 0);

      assertAll(
          "Overlay image properties",
          () -> assertEquals(source.width(), result.width()),
          () -> assertEquals(source.height(), result.height()));

      // Verify overlay pixels are set correctly
      byte[] pixelData = new byte[result.width() * result.height()];
      result.get(0, 0, pixelData);

      assertAll(
          "Pixel verification",
          () -> assertEquals(0, pixelData[0], "Background should be 0"),
          () -> assertEquals(-1, pixelData[20], "Overlay pixel should be -1"),
          () -> assertEquals(-1, pixelData[21], "Overlay pixel should be -1"),
          () -> assertEquals(-1, pixelData[22], "Overlay pixel should be -1"),
          () -> assertEquals(0, pixelData[13], "Background should be 0"));
    }

    @Test
    void should_handle_embedded_overlays() {
      var embeddedOverlays = List.of(new EmbeddedOverlay(0, 2));
      var sourceWithOverlay = new ImageCV(9, 9, CvType.CV_8UC1);
      sourceWithOverlay.put(0, 0, new byte[] {4, 0, 0, 0, 0, 0, 0, 0, 0}); // bit 2 set
      var currentImage = imageFactory.createTestImage(9, 9);
      var descriptor = createTestImageDescriptor(List.of(), embeddedOverlays);
      var params = new TestDicomImageReadParam().withOverlayColor(Color.WHITE);

      var result =
          OverlayData.getOverlayImage(sourceWithOverlay, currentImage, descriptor, params, 0);
      assertSame(currentImage, result); // No overlays should be applied
    }

    @ParameterizedTest
    @MethodSource("provideOverlayColors")
    void should_handle_different_overlay_colors(Color overlayColor) {
      var sourceImage = imageFactory.createTestImage(5, 5);
      var currentImage = imageFactory.createTestImage(5, 5);
      var overlayData = new OverlayData(0, 2, 2, 1, 1, new int[] {1, 1}, new byte[] {1, 2, 3, 4});
      var descriptor = createTestImageDescriptor(List.of(overlayData));
      var params = new TestDicomImageReadParam().withOverlayColor(overlayColor);

      assertDoesNotThrow(
          () -> {
            var result =
                OverlayData.getOverlayImage(sourceImage, currentImage, descriptor, params, 0);
            assertNotNull(result);
          });
      sourceImage.close();
      currentImage.close();
    }

    static Stream<Arguments> provideOverlayColors() {
      return Stream.of(
          Arguments.of(Color.RED),
          Arguments.of(Color.GREEN),
          Arguments.of(Color.BLUE),
          Arguments.of(Color.WHITE),
          Arguments.of((Color) null));
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Frame_handling_tests {

    @Test
    void should_handle_multi_frame_overlays_correctly() {
      var multiFrameOverlay =
          new OverlayData(
              0, 2, 2, 1, 3, new int[] {1, 1}, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
      var source = new ImageCV(5, 5, CvType.CV_8UC1);

      var frames =
          List.of(
              OverlayData.getOverlayImage(source, List.of(multiFrameOverlay), 0),
              OverlayData.getOverlayImage(source, List.of(multiFrameOverlay), 1),
              OverlayData.getOverlayImage(source, List.of(multiFrameOverlay), 2),
              OverlayData.getOverlayImage(source, List.of(multiFrameOverlay), 5));

      frames.forEach(
          frame -> {
            assertNotNull(frame);
            assertEquals(source.width(), frame.width());
            assertEquals(source.height(), frame.height());
          });
    }

    @Test
    void should_handle_frame_origin_offset_correctly() {
      var overlayWithOffset =
          new OverlayData(0, 2, 2, 3, 2, new int[] {1, 1}, new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
      var source = new ImageCV(5, 5, CvType.CV_8UC1);

      var beforeOrigin = OverlayData.getOverlayImage(source, List.of(overlayWithOffset), 1);
      var atOrigin = OverlayData.getOverlayImage(source, List.of(overlayWithOffset), 2);
      var afterOrigin = OverlayData.getOverlayImage(source, List.of(overlayWithOffset), 3);

      assertAll(
          "Frame handling with offset",
          () -> assertNotNull(beforeOrigin),
          () -> assertNotNull(atOrigin),
          () -> assertNotNull(afterOrigin));
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, -1, 0, 1, 10, 100})
    void should_handle_various_frame_indices(int frameIndex) {
      var overlay = createTestOverlayData();
      var source = new ImageCV(5, 5, CvType.CV_8UC1);

      assertDoesNotThrow(
          () -> {
            var result = OverlayData.getOverlayImage(source, List.of(overlay), frameIndex);
            assertNotNull(result);
          });
      source.close();
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Equals_and_hash_code_tests {

    private final OverlayData baseOverlay =
        new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9});
    private final OverlayData identicalOverlay =
        new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9});

    @Test
    void should_be_equal_to_identical_overlay() {
      assertAll(
          "Equality checks",
          () -> assertEquals(baseOverlay, identicalOverlay),
          () -> assertEquals(baseOverlay.hashCode(), identicalOverlay.hashCode()),
          () -> assertEquals(baseOverlay.toString(), identicalOverlay.toString()));
    }

    @Test
    void should_not_be_equal_to_null() {
      assertNotEquals(null, baseOverlay);
    }

    @Test
    void should_not_be_equal_to_different_class() {
      assertNotEquals("not an overlay", baseOverlay);
    }

    @ParameterizedTest
    @MethodSource("provideDifferentOverlays")
    void should_not_be_equal_when_fields_differ(OverlayData differentOverlay) {
      assertAll(
          "Inequality checks",
          () -> assertNotEquals(baseOverlay, differentOverlay),
          () -> assertNotEquals(baseOverlay.hashCode(), differentOverlay.hashCode()));
    }

    static Stream<Arguments> provideDifferentOverlays() {
      return Stream.of(
          Arguments.of(new OverlayData(1, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9})),
          Arguments.of(new OverlayData(0, 3, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 9})),
          Arguments.of(new OverlayData(0, 2, 4, 1, 1, new int[] {1, 1}, new byte[] {8, 9})),
          Arguments.of(new OverlayData(0, 2, 3, 2, 1, new int[] {1, 1}, new byte[] {8, 9})),
          Arguments.of(new OverlayData(0, 2, 3, 1, 2, new int[] {1, 1}, new byte[] {8, 9})),
          Arguments.of(new OverlayData(0, 2, 3, 1, 1, new int[] {2, 2}, new byte[] {8, 9})),
          Arguments.of(new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, new byte[] {8, 10})));
    }

    @Test
    void should_handle_null_arrays_in_comparison() {
      var overlayWithNullOrigin = new OverlayData(0, 2, 3, 1, 1, null, new byte[] {8, 9});
      var overlayWithNullData = new OverlayData(0, 2, 3, 1, 1, new int[] {1, 1}, null);

      assertAll(
          "Null array handling",
          () -> assertNotEquals(baseOverlay, overlayWithNullOrigin),
          () -> assertNotEquals(baseOverlay, overlayWithNullData),
          () -> assertNotEquals(overlayWithNullOrigin, overlayWithNullData));
    }
  }

  @Nested
  @DisplayNameGeneration(ReplaceUnderscores.class)
  class Edge_case_tests {

    @Test
    void should_handle_zero_sized_overlays() {
      var zeroSizedOverlay = new OverlayData(0, 0, 0, 1, 1, new int[] {1, 1}, new byte[] {});
      try (var source = new ImageCV(5, 5, CvType.CV_8UC1)) {
        assertDoesNotThrow(
            () -> {
              var result = OverlayData.getOverlayImage(source, List.of(zeroSizedOverlay), 0);
              assertNotNull(result);
            });
      }
    }

    @Test
    void should_handle_overlay_data_out_of_bounds_gracefully() {
      var overlayOutOfBounds =
          new OverlayData(0, 10, 10, 1, 1, new int[] {1, 1}, new byte[] {1, 2});
      try (var source = new ImageCV(5, 5, CvType.CV_8UC1)) {
        assertDoesNotThrow(
            () -> {
              var result = OverlayData.getOverlayImage(source, List.of(overlayOutOfBounds), 0);
              assertNotNull(result);
            });
      }
    }

    @Test
    void should_handle_large_overlay_origins() {
      var overlayWithLargeOrigin =
          new OverlayData(0, 2, 2, 1, 1, new int[] {100, 100}, new byte[] {1, 2, 3, 4});
      var source = new ImageCV(5, 5, CvType.CV_8UC1);

      assertDoesNotThrow(
          () -> {
            var result = OverlayData.getOverlayImage(source, List.of(overlayWithLargeOrigin), 0);
            assertNotNull(result);
          });

      source.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 255, 1000, 10000})
    void should_handle_various_overlay_data_sizes(int dataSize) {
      var largeData = new byte[dataSize];
      var overlay = new OverlayData(0, 10, 10, 1, 1, new int[] {1, 1}, largeData);
      var source = new ImageCV(15, 15, CvType.CV_8UC1);

      assertDoesNotThrow(
          () -> {
            var result = OverlayData.getOverlayImage(source, List.of(overlay), 0);
            assertNotNull(result);
          });

      source.close();
    }
  }

  // Helper classes and methods

  static byte[] createTestOverlayBytes() {
    return new byte[] {0, 0, 0, 1, 1, 1, 0, 0, 0};
  }

  static OverlayData createTestOverlayData() {
    return new OverlayData(0, 3, 3, 1, 1, new int[] {1, 1}, createTestOverlayBytes());
  }

  static Attributes createDicomWithOverlay(int groupOffset, byte[] data) {
    var dcm = new Attributes();
    addOverlayToDicom(dcm, groupOffset, data, 3, 3);
    return dcm;
  }

  static void addOverlayToDicom(
      Attributes dcm, int groupOffset, byte[] data, int rows, int columns) {
    dcm.setString(Tag.OverlayType | groupOffset, VR.CS, "G");
    dcm.setInt(Tag.OverlayOrigin | groupOffset, VR.SS, 1, 1);
    dcm.setInt(Tag.OverlayRows | groupOffset, VR.US, rows);
    dcm.setInt(Tag.OverlayColumns | groupOffset, VR.US, columns);
    dcm.setInt(Tag.OverlayBitsAllocated | groupOffset, VR.US, 1);
    dcm.setInt(Tag.OverlayBitPosition | groupOffset, VR.US, 0);
    dcm.setBytes(Tag.OverlayData | groupOffset, VR.OB, data);
  }

  // Helper method to create ImageDescriptor instances for testing
  private static ImageDescriptor createTestImageDescriptor() {
    return createTestImageDescriptor(List.of(), List.of());
  }

  private static ImageDescriptor createTestImageDescriptor(List<OverlayData> overlayData) {
    return createTestImageDescriptor(overlayData, List.of());
  }

  private static ImageDescriptor createTestImageDescriptor(
      List<OverlayData> overlayData, List<EmbeddedOverlay> embeddedOverlays) {
    var dcm = new Attributes();

    // Set basic image attributes
    dcm.setInt(Tag.Rows, VR.US, 9);
    dcm.setInt(Tag.Columns, VR.US, 9);
    dcm.setInt(Tag.SamplesPerPixel, VR.US, 1);
    dcm.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
    dcm.setInt(Tag.BitsAllocated, VR.US, 8);
    dcm.setInt(Tag.BitsStored, VR.US, 8);
    dcm.setInt(Tag.HighBit, VR.US, 7);
    dcm.setInt(Tag.PixelRepresentation, VR.US, 0);
    dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
    dcm.setString(Tag.Modality, VR.CS, "CT");

    // Add overlay data to DICOM attributes if provided
    for (int i = 0; i < overlayData.size(); i++) {
      var overlay = overlayData.get(i);
      int groupOffset = i << OverlayData.GROUP_OFFSET_SHIFT;
      addOverlayToDicom(dcm, groupOffset, overlay.data(), overlay.rows(), overlay.columns());
    }

    // Add embedded overlays if provided
    for (var embeddedOverlay : embeddedOverlays) {
      int groupOffset = embeddedOverlay.groupOffset() << OverlayData.GROUP_OFFSET_SHIFT;
      dcm.setInt(Tag.OverlayBitPosition | groupOffset, VR.US, embeddedOverlay.bitPosition());
    }

    return new ImageDescriptor(dcm);
  }

  // Test data classes

  static class TestImageFactory {
    PlanarImage createTestImage(int width, int height) {
      return new ImageCV(height, width, CvType.CV_8UC1);
    }
  }

  static class TestDicomImageReadParam extends DicomImageReadParam {
    private Color overlayColor;
    private PrDicomObject presentationState;

    TestDicomImageReadParam withOverlayColor(Color color) {
      this.overlayColor = color;
      return this;
    }

    TestDicomImageReadParam withPresentationState(PrDicomObject prDicom) {
      this.presentationState = prDicom;
      return this;
    }

    @Override
    public Optional<Color> getOverlayColor() {
      return Optional.ofNullable(overlayColor);
    }

    @Override
    public Optional<PrDicomObject> getPresentationState() {
      return Optional.ofNullable(presentationState);
    }
  }

  static class TestPrDicomObject extends PrDicomObject {
    private final List<OverlayData> overlays;

    TestPrDicomObject(Attributes attributes, List<OverlayData> overlays) {
      super(attributes);
      this.overlays = overlays;
    }

    @Override
    public List<OverlayData> getOverlays() {
      return overlays;
    }

    // Default implementations for other methods
    public String getSOPInstanceUID() {
      return "1.2.3.4.5.6";
    }

    public String getSOPClassUID() {
      return "1.2.840.10008.5.1.4.1.1.11.1";
    }
  }
}
