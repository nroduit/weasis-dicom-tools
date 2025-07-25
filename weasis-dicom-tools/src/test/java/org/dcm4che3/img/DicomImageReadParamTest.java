/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.IIOParamController;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageTypeSpecifier;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.data.PrDicomObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.opencv.op.lut.LutShape;

/**
 * Test class for {@link DicomImageReadParam}.
 *
 * <p>This test class validates DICOM-specific image read parameters including window/level
 * operations, VOI LUT transformations, presentation state handling, and overlay configuration using
 * real test data.
 */
class DicomImageReadParamTest {

  private DicomImageReadParam param;

  @BeforeEach
  void setUp() {
    param = new DicomImageReadParam();
  }

  @Test
  @DisplayName("Default constructor should initialize with proper defaults")
  void testDefaultConstructor() {
    assertNotNull(param);
    assertTrue(param.canSetSourceRenderSize());

    // Window/Level defaults
    assertTrue(param.getWindowCenter().isEmpty());
    assertTrue(param.getWindowWidth().isEmpty());
    assertTrue(param.getLevelMin().isEmpty());
    assertTrue(param.getLevelMax().isEmpty());

    // LUT defaults
    assertTrue(param.getVoiLutShape().isEmpty());

    // Boolean defaults
    assertTrue(param.getApplyPixelPadding().isEmpty());
    assertTrue(param.getInverseLut().isEmpty());
    assertTrue(param.getFillOutsideLutRange().isEmpty());
    assertTrue(param.getApplyWindowLevelToColorImage().isEmpty());
    assertTrue(param.getKeepRgbForLossyJpeg().isEmpty());
    assertTrue(param.getReleaseImageAfterProcessing().isEmpty());

    // Index defaults
    assertEquals(0, param.getWindowIndex());
    assertEquals(0, param.getVoiLUTIndex());

    // Presentation state default
    assertTrue(param.getPresentationState().isEmpty());

    // Overlay defaults
    assertEquals(0xf, param.getOverlayActivationMask());
    assertEquals(0xffff, param.getOverlayGrayscaleValue());
    assertTrue(param.getOverlayColor().isEmpty());
  }

  @Test
  @DisplayName("Copy constructor should preserve source region")
  void testCopyConstructor() {
    // Create base parameter with region and render size
    ImageReadParam baseParam = new ImageReadParam();
    Rectangle sourceRegion = new Rectangle(10, 20, 100, 200);

    baseParam.setSourceRegion(sourceRegion);

    // Create DICOM param from base
    DicomImageReadParam dicomParam = new DicomImageReadParam(baseParam);

    assertNotNull(dicomParam);
    assertTrue(dicomParam.canSetSourceRenderSize());
    assertEquals(sourceRegion, dicomParam.getSourceRegion());
  }

  @Test
  @DisplayName("Window/Level parameters should be settable and retrievable")
  void testWindowLevelParameters() {
    // Initially empty
    assertTrue(param.getWindowCenter().isEmpty());
    assertTrue(param.getWindowWidth().isEmpty());

    // Set values
    param.setWindowCenter(100.0);
    param.setWindowWidth(200.0);

    // Verify values
    assertTrue(param.getWindowCenter().isPresent());
    assertEquals(100.0, param.getWindowCenter().getAsDouble(), 0.001);
    assertTrue(param.getWindowWidth().isPresent());
    assertEquals(200.0, param.getWindowWidth().getAsDouble(), 0.001);

    // Set to null
    param.setWindowCenter(null);
    param.setWindowWidth(null);

    // Should be empty again
    assertTrue(param.getWindowCenter().isEmpty());
    assertTrue(param.getWindowWidth().isEmpty());
  }

  @Test
  @DisplayName("Level min/max parameters should be settable and retrievable")
  void testLevelMinMaxParameters() {
    // Initially empty
    assertTrue(param.getLevelMin().isEmpty());
    assertTrue(param.getLevelMax().isEmpty());

    // Set values
    param.setLevelMin(-1000.0);
    param.setLevelMax(3000.0);

    // Verify values
    assertTrue(param.getLevelMin().isPresent());
    assertEquals(-1000.0, param.getLevelMin().getAsDouble(), 0.001);
    assertTrue(param.getLevelMax().isPresent());
    assertEquals(3000.0, param.getLevelMax().getAsDouble(), 0.001);

    // Set to null
    param.setLevelMin(null);
    param.setLevelMax(null);

    // Should be empty again
    assertTrue(param.getLevelMin().isEmpty());
    assertTrue(param.getLevelMax().isEmpty());
  }

  @Test
  @DisplayName("VOI LUT shape should be settable and retrievable")
  void testVoiLutShape() {
    // Initially empty
    assertTrue(param.getVoiLutShape().isEmpty());

    // Set LINEAR shape
    param.setVoiLutShape(LutShape.LINEAR);
    assertTrue(param.getVoiLutShape().isPresent());
    assertEquals(LutShape.LINEAR, param.getVoiLutShape().get());

    // Set SIGMOID shape
    param.setVoiLutShape(LutShape.SIGMOID);
    assertTrue(param.getVoiLutShape().isPresent());
    assertEquals(LutShape.SIGMOID, param.getVoiLutShape().get());

    // Set to null
    param.setVoiLutShape(null);
    assertTrue(param.getVoiLutShape().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisplayName("Boolean rendering options should be settable and retrievable")
  void testBooleanRenderingOptions(boolean value) {
    // Test all boolean options
    param.setApplyPixelPadding(value);
    param.setInverseLut(value);
    param.setFillOutsideLutRange(value);
    param.setApplyWindowLevelToColorImage(value);
    param.setKeepRgbForLossyJpeg(value);
    param.setReleaseImageAfterProcessing(value);

    // Verify all values
    assertTrue(param.getApplyPixelPadding().isPresent());
    assertEquals(value, param.getApplyPixelPadding().get());

    assertTrue(param.getInverseLut().isPresent());
    assertEquals(value, param.getInverseLut().get());

    assertTrue(param.getFillOutsideLutRange().isPresent());
    assertEquals(value, param.getFillOutsideLutRange().get());

    assertTrue(param.getApplyWindowLevelToColorImage().isPresent());
    assertEquals(value, param.getApplyWindowLevelToColorImage().get());

    assertTrue(param.getKeepRgbForLossyJpeg().isPresent());
    assertEquals(value, param.getKeepRgbForLossyJpeg().get());

    assertTrue(param.getReleaseImageAfterProcessing().isPresent());
    assertEquals(value, param.getReleaseImageAfterProcessing().get());
  }

  @Test
  @DisplayName("Boolean options should handle null values correctly")
  void testBooleanOptionsWithNull() {
    // Set all to true first
    param.setApplyPixelPadding(true);
    param.setInverseLut(true);
    param.setFillOutsideLutRange(true);
    param.setApplyWindowLevelToColorImage(true);
    param.setKeepRgbForLossyJpeg(true);
    param.setReleaseImageAfterProcessing(true);

    // All should be present
    assertTrue(param.getApplyPixelPadding().isPresent());
    assertTrue(param.getInverseLut().isPresent());
    assertTrue(param.getFillOutsideLutRange().isPresent());
    assertTrue(param.getApplyWindowLevelToColorImage().isPresent());
    assertTrue(param.getKeepRgbForLossyJpeg().isPresent());
    assertTrue(param.getReleaseImageAfterProcessing().isPresent());

    // Set all to null
    param.setApplyPixelPadding(null);
    param.setInverseLut(null);
    param.setFillOutsideLutRange(null);
    param.setApplyWindowLevelToColorImage(null);
    param.setKeepRgbForLossyJpeg(null);
    param.setReleaseImageAfterProcessing(null);

    // All should be empty
    assertTrue(param.getApplyPixelPadding().isEmpty());
    assertTrue(param.getInverseLut().isEmpty());
    assertTrue(param.getFillOutsideLutRange().isEmpty());
    assertTrue(param.getApplyWindowLevelToColorImage().isEmpty());
    assertTrue(param.getKeepRgbForLossyJpeg().isEmpty());
    assertTrue(param.getReleaseImageAfterProcessing().isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
    "0, 0",
    "1, 1",
    "5, 5",
    "10, 10",
    "-5, 0", // Should clamp negative values to 0
    "-1, 0" // Should clamp negative values to 0
  })
  @DisplayName("Index parameters should clamp negative values to zero")
  void testIndexParameters(int input, int expected) {
    param.setWindowIndex(input);
    param.setVoiLUTIndex(input);

    assertEquals(expected, param.getWindowIndex());
    assertEquals(expected, param.getVoiLUTIndex());
  }

  @Test
  @DisplayName("Presentation state should be settable and retrievable")
  void testPresentationState() {
    // Initially empty
    assertTrue(param.getPresentationState().isEmpty());

    // Create test presentation state
    Attributes prAttributes = new Attributes();
    prAttributes.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.11.1");
    prAttributes.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5");
    prAttributes.setString(Tag.PresentationCreationDate, VR.DA, "20240101");
    prAttributes.setString(Tag.PresentationCreationTime, VR.TM, "120000");

    PrDicomObject presentationState = new PrDicomObject(prAttributes);

    // Set presentation state
    param.setPresentationState(presentationState);
    assertTrue(param.getPresentationState().isPresent());
    assertEquals(presentationState, param.getPresentationState().get());

    // Set to null
    param.setPresentationState(null);
    assertTrue(param.getPresentationState().isEmpty());
  }

  @Test
  @DisplayName("Overlay parameters should be settable and retrievable")
  void testOverlayParameters() {
    // Test activation mask
    param.setOverlayActivationMask(0x5); // Binary: 0101
    assertEquals(0x5, param.getOverlayActivationMask());

    // Test grayscale value
    param.setOverlayGrayscaleValue(0x8000);
    assertEquals(0x8000, param.getOverlayGrayscaleValue());

    // Test overlay color
    assertTrue(param.getOverlayColor().isEmpty());

    Color red = Color.RED;
    param.setOverlayColor(red);
    assertTrue(param.getOverlayColor().isPresent());
    assertEquals(red, param.getOverlayColor().get());

    // Set color to null
    param.setOverlayColor(null);
    assertTrue(param.getOverlayColor().isEmpty());
  }

  @Test
  @DisplayName("Overlay activation mask should handle various bit patterns")
  void testOverlayActivationMaskPatterns() {
    // Test common patterns
    param.setOverlayActivationMask(0x1); // Only first overlay
    assertEquals(0x1, param.getOverlayActivationMask());

    param.setOverlayActivationMask(0xf); // First 4 overlays (default)
    assertEquals(0xf, param.getOverlayActivationMask());

    param.setOverlayActivationMask(0xff); // First 8 overlays
    assertEquals(0xff, param.getOverlayActivationMask());

    param.setOverlayActivationMask(0x0); // No overlays
    assertEquals(0x0, param.getOverlayActivationMask());

    param.setOverlayActivationMask(0xffff); // All possible overlays
    assertEquals(0xffff, param.getOverlayActivationMask());
  }

  @Test
  @DisplayName("Overlay grayscale value should handle full range")
  void testOverlayGrayscaleValueRange() {
    // Test minimum value
    param.setOverlayGrayscaleValue(0x0000);
    assertEquals(0x0000, param.getOverlayGrayscaleValue());

    // Test maximum value
    param.setOverlayGrayscaleValue(0xffff);
    assertEquals(0xffff, param.getOverlayGrayscaleValue());

    // Test intermediate values
    param.setOverlayGrayscaleValue(0x8000);
    assertEquals(0x8000, param.getOverlayGrayscaleValue());

    param.setOverlayGrayscaleValue(0x7fff);
    assertEquals(0x7fff, param.getOverlayGrayscaleValue());
  }

  @Test
  @DisplayName("Overlay color should handle various color types")
  void testOverlayColorTypes() {
    // Test basic colors
    param.setOverlayColor(Color.RED);
    assertEquals(Color.RED, param.getOverlayColor().get());

    param.setOverlayColor(Color.GREEN);
    assertEquals(Color.GREEN, param.getOverlayColor().get());

    param.setOverlayColor(Color.BLUE);
    assertEquals(Color.BLUE, param.getOverlayColor().get());

    // Test custom color with alpha
    Color customColor = new Color(128, 64, 192, 200);
    param.setOverlayColor(customColor);
    assertEquals(customColor, param.getOverlayColor().get());

    // Test grayscale color
    Color gray = new Color(128, 128, 128);
    param.setOverlayColor(gray);
    assertEquals(gray, param.getOverlayColor().get());
  }

  // Tests for unsupported operations
  @Test
  @DisplayName("setDestinationOffset should throw UnsupportedOperationException")
  void testSetDestinationOffsetThrows() {
    Point offset = new Point(10, 20);
    UnsupportedOperationException exception =
        assertThrows(UnsupportedOperationException.class, () -> param.setDestinationOffset(offset));
    assertTrue(exception.getMessage().contains("Not compatible with the native DICOM Decoder"));
  }

  @Test
  @DisplayName("setController should throw UnsupportedOperationException")
  void testSetControllerThrows() {
    IIOParamController controller = (param) -> true;
    UnsupportedOperationException exception =
        assertThrows(UnsupportedOperationException.class, () -> param.setController(controller));
    assertTrue(exception.getMessage().contains("Not compatible with the native DICOM Decoder"));
  }

  @Test
  @DisplayName("setSourceBands should throw UnsupportedOperationException")
  void testSetSourceBandsThrows() {
    int[] sourceBands = {0, 1, 2};
    UnsupportedOperationException exception =
        assertThrows(UnsupportedOperationException.class, () -> param.setSourceBands(sourceBands));
    assertTrue(exception.getMessage().contains("Not compatible with the native DICOM Decoder"));
  }

  @Test
  @DisplayName("setSourceSubsampling should throw UnsupportedOperationException")
  void testSetSourceSubsamplingThrows() {
    UnsupportedOperationException exception =
        assertThrows(
            UnsupportedOperationException.class, () -> param.setSourceSubsampling(2, 2, 0, 0));
    assertTrue(exception.getMessage().contains("Not compatible with the native DICOM Decoder"));
  }

  @Test
  @DisplayName("setDestination should throw UnsupportedOperationException")
  void testSetDestinationThrows() {
    BufferedImage destination = new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_GRAY);
    UnsupportedOperationException exception =
        assertThrows(UnsupportedOperationException.class, () -> param.setDestination(destination));
    assertTrue(exception.getMessage().contains("Not compatible with the native DICOM Decoder"));
  }

  @Test
  @DisplayName("setDestinationBands should throw UnsupportedOperationException")
  void testSetDestinationBandsThrows() {
    int[] destinationBands = {0};
    UnsupportedOperationException exception =
        assertThrows(
            UnsupportedOperationException.class, () -> param.setDestinationBands(destinationBands));
    assertTrue(exception.getMessage().contains("Not compatible with the native DICOM Decoder"));
  }

  @Test
  @DisplayName("setDestinationType should throw UnsupportedOperationException")
  void testSetDestinationTypeThrows() {
    ImageTypeSpecifier destinationType =
        ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_BYTE_GRAY);
    UnsupportedOperationException exception =
        assertThrows(
            UnsupportedOperationException.class, () -> param.setDestinationType(destinationType));
    assertTrue(exception.getMessage().contains("Not compatible with the native DICOM Decoder"));
  }

  @Test
  @DisplayName("setSourceProgressivePasses should throw UnsupportedOperationException")
  void testSetSourceProgressivePassesThrows() {
    UnsupportedOperationException exception =
        assertThrows(
            UnsupportedOperationException.class, () -> param.setSourceProgressivePasses(1, 3));
    assertTrue(exception.getMessage().contains("Not compatible with the native DICOM Decoder"));
  }

  @Test
  @DisplayName("Window/Level parameters should handle extreme values")
  void testWindowLevelExtremeValues() {
    // Test very large values
    param.setWindowCenter(Double.MAX_VALUE);
    param.setWindowWidth(Double.MAX_VALUE);

    assertEquals(Double.MAX_VALUE, param.getWindowCenter().getAsDouble());
    assertEquals(Double.MAX_VALUE, param.getWindowWidth().getAsDouble());

    // Test very small values
    param.setWindowCenter(-Double.MAX_VALUE);
    param.setWindowWidth(Double.MIN_VALUE);

    assertEquals(-Double.MAX_VALUE, param.getWindowCenter().getAsDouble());
    assertEquals(Double.MIN_VALUE, param.getWindowWidth().getAsDouble());

    // Test zero values
    param.setWindowCenter(0.0);
    param.setWindowWidth(0.0);

    assertEquals(0.0, param.getWindowCenter().getAsDouble());
    assertEquals(0.0, param.getWindowWidth().getAsDouble());
  }

  @Test
  @DisplayName("Level min/max should handle realistic DICOM values")
  void testLevelMinMaxRealisticValues() {
    // CT scan range (typical Hounsfield units)
    param.setLevelMin(-1024.0);
    param.setLevelMax(3071.0);

    assertEquals(-1024.0, param.getLevelMin().getAsDouble());
    assertEquals(3071.0, param.getLevelMax().getAsDouble());

    // MR scan range
    param.setLevelMin(0.0);
    param.setLevelMax(4095.0);

    assertEquals(0.0, param.getLevelMin().getAsDouble());
    assertEquals(4095.0, param.getLevelMax().getAsDouble());

    // X-ray range (12-bit)
    param.setLevelMin(0.0);
    param.setLevelMax(4095.0);

    assertEquals(0.0, param.getLevelMin().getAsDouble());
    assertEquals(4095.0, param.getLevelMax().getAsDouble());
  }

  @Test
  @DisplayName("Parameter combinations should work together")
  void testParameterCombinations() {
    // Set a complete window/level configuration
    param.setWindowCenter(512.0);
    param.setWindowWidth(1024.0);
    param.setLevelMin(-1024.0);
    param.setLevelMax(3071.0);
    param.setVoiLutShape(LutShape.SIGMOID);
    param.setWindowIndex(2);
    param.setVoiLUTIndex(1);

    // Set rendering options
    param.setApplyPixelPadding(true);
    param.setInverseLut(false);
    param.setFillOutsideLutRange(true);
    param.setApplyWindowLevelToColorImage(false);
    param.setKeepRgbForLossyJpeg(true);
    param.setReleaseImageAfterProcessing(true);

    // Set overlay configuration
    param.setOverlayActivationMask(0x7); // First 3 overlays
    param.setOverlayGrayscaleValue(0xc000);
    param.setOverlayColor(Color.YELLOW);

    // Verify all parameters are set correctly
    assertEquals(512.0, param.getWindowCenter().getAsDouble());
    assertEquals(1024.0, param.getWindowWidth().getAsDouble());
    assertEquals(-1024.0, param.getLevelMin().getAsDouble());
    assertEquals(3071.0, param.getLevelMax().getAsDouble());
    assertEquals(LutShape.SIGMOID, param.getVoiLutShape().get());
    assertEquals(2, param.getWindowIndex());
    assertEquals(1, param.getVoiLUTIndex());

    assertTrue(param.getApplyPixelPadding().get());
    assertFalse(param.getInverseLut().get());
    assertTrue(param.getFillOutsideLutRange().get());
    assertFalse(param.getApplyWindowLevelToColorImage().get());
    assertTrue(param.getKeepRgbForLossyJpeg().get());
    assertTrue(param.getReleaseImageAfterProcessing().get());

    assertEquals(0x7, param.getOverlayActivationMask());
    assertEquals(0xc000, param.getOverlayGrayscaleValue());
    assertEquals(Color.YELLOW, param.getOverlayColor().get());
  }

  @Test
  @DisplayName("Inheritance from ImageReadParam should preserve basic functionality")
  void testImageReadParamInheritance() {
    // Test that basic ImageReadParam functionality is preserved
    assertNull(param.getSourceRegion());

    // Set source region (should work)
    Rectangle region = new Rectangle(50, 100, 200, 300);
    param.setSourceRegion(region);
    assertEquals(region, param.getSourceRegion());

    // Set source render size (should work)
    Dimension renderSize = new Dimension(100, 150);
    param.setSourceRenderSize(renderSize);
    assertEquals(renderSize, param.getSourceRenderSize());

    // Verify canSetSourceRenderSize is enabled
    assertTrue(param.canSetSourceRenderSize());
  }
}
