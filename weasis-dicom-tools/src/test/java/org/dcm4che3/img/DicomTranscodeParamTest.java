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

import java.awt.Color;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.op.MaskArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test class for {@link DicomTranscodeParam}.
 *
 * <p>This test class validates DICOM transcoding parameter functionality including transfer syntax
 * configuration, mask management, File Meta Information settings, and integration with DICOM
 * reading/writing parameters using real data structures and comprehensive validation.
 */
class DicomTranscodeParamTest {

  // Standard DICOM Transfer Syntax UIDs for testing
  private static final String IMPLICIT_VR_LITTLE_ENDIAN = UID.ImplicitVRLittleEndian;
  private static final String EXPLICIT_VR_LITTLE_ENDIAN = UID.ExplicitVRLittleEndian;
  private static final String JPEG_BASELINE = UID.JPEGBaseline8Bit;
  private static final String JPEG_LOSSLESS = UID.JPEGLossless;
  private static final String JPEG_2000_LOSSLESS = UID.JPEG2000Lossless;

  // Test station names and mask areas
  private static final String STATION_A = "STATION_A";
  private static final String STATION_B = "STATION_B";
  private static final String STATION_C = "STATION_C";

  private DicomImageReadParam testReadParam;
  private Map<String, MaskArea> testMaskMap;
  private MaskArea maskAreaA;
  private MaskArea maskAreaB;
  private MaskArea maskAreaC;
  private MaskArea wildcardMaskArea;

  @BeforeEach
  void setUp() {
    testReadParam = createTestReadParam();
    testMaskMap = createTestMaskMap();
    maskAreaA = new MaskArea(List.of(new Rectangle(10, 20, 100, 150)), Color.ORANGE);
    maskAreaB = new MaskArea(List.of(new Rectangle(50, 60, 200, 200)), Color.BLUE);
    maskAreaC = new MaskArea(List.of(new Rectangle(0, 0, 512, 512)));
    wildcardMaskArea = new MaskArea(List.of(new Rectangle(25, 25, 300, 400)), Color.RED);
  }

  // Constructor Tests

  @Test
  @DisplayName(
      "Constructor with transfer syntax should create instance with default read parameters")
  void testConstructorWithTransferSyntax() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    assertNotNull(param);
    assertEquals(JPEG_BASELINE, param.getOutputTsuid());
    assertNotNull(param.getReadParam());
    assertNotNull(param.getWriteJpegParam()); // JPEG should have write param
    assertTrue(param.isCompressionEnabled());
    assertFalse(param.isOutputFmi());
    assertFalse(param.hasMasks());
    assertEquals(0, param.getMaskMap().size());
  }

  @Test
  @DisplayName("Constructor with custom read param and transfer syntax should use provided values")
  void testConstructorWithCustomReadParam() {
    DicomTranscodeParam param = new DicomTranscodeParam(testReadParam, EXPLICIT_VR_LITTLE_ENDIAN);

    assertEquals(EXPLICIT_VR_LITTLE_ENDIAN, param.getOutputTsuid());
    assertSame(testReadParam, param.getReadParam());
    assertNull(param.getWriteJpegParam()); // Native syntax should not have write param
    assertFalse(param.isCompressionEnabled());
    assertFalse(param.isOutputFmi());
  }

  @Test
  @DisplayName("Constructor with null read param should create default")
  void testConstructorWithNullReadParam() {
    DicomTranscodeParam param = new DicomTranscodeParam(null, JPEG_LOSSLESS);

    assertEquals(JPEG_LOSSLESS, param.getOutputTsuid());
    assertNotNull(param.getReadParam());
    assertNotSame(testReadParam, param.getReadParam());
    assertNotNull(param.getWriteJpegParam());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t", "\n"})
  @DisplayName("Constructor should throw IllegalArgumentException for empty transfer syntax")
  void testConstructorWithEmptyTransferSyntax(String emptyTsuid) {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new DicomTranscodeParam(emptyTsuid));

    assertTrue(exception.getMessage().contains("Transfer syntax UID cannot be null or empty"));
  }

  @Test
  @DisplayName("Constructor should throw IllegalArgumentException for null transfer syntax")
  void testConstructorWithNullTransferSyntax() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new DicomTranscodeParam((String) null));

    assertTrue(exception.getMessage().contains("Transfer syntax UID cannot be null or empty"));
  }

  // Transfer Syntax and Compression Tests

  @ParameterizedTest
  @ValueSource(
      strings = {
        IMPLICIT_VR_LITTLE_ENDIAN,
        EXPLICIT_VR_LITTLE_ENDIAN,
      })
  @DisplayName("Native transfer syntaxes should not enable compression")
  void testNativeTransferSyntaxes(String nativeTsuid) {
    DicomTranscodeParam param = new DicomTranscodeParam(nativeTsuid);

    assertEquals(nativeTsuid, param.getOutputTsuid());
    assertFalse(param.isCompressionEnabled());
    assertNull(param.getWriteJpegParam());
  }

  @ParameterizedTest
  @ValueSource(strings = {JPEG_BASELINE, JPEG_LOSSLESS, JPEG_2000_LOSSLESS, UID.JPEGLSLossless})
  @DisplayName("Compressed transfer syntaxes should enable compression")
  void testCompressedTransferSyntaxes(String compressedTsuid) {
    DicomTranscodeParam param = new DicomTranscodeParam(compressedTsuid);

    assertEquals(compressedTsuid, param.getOutputTsuid());
    assertTrue(param.isCompressionEnabled());
    assertNotNull(param.getWriteJpegParam());
  }

  @Test
  @DisplayName("getOutputTsuid should return immutable transfer syntax")
  void testGetOutputTsuidImmutable() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);
    String tsuid1 = param.getOutputTsuid();
    String tsuid2 = param.getOutputTsuid();

    assertSame(tsuid1, tsuid2);
    assertEquals(JPEG_BASELINE, tsuid1);
  }

  // File Meta Information Tests

  @Test
  @DisplayName("Default outputFmi should be false")
  void testDefaultOutputFmiFalse() {
    DicomTranscodeParam param = new DicomTranscodeParam(EXPLICIT_VR_LITTLE_ENDIAN);

    assertFalse(param.isOutputFmi());
  }

  @Test
  @DisplayName("setOutputFmi should configure FMI output correctly")
  void testSetOutputFmi() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    // Set to true
    param.setOutputFmi(true);
    assertTrue(param.isOutputFmi());

    // Set back to false
    param.setOutputFmi(false);
    assertFalse(param.isOutputFmi());
  }

  @Test
  @DisplayName("outputFmi should be independent across instances")
  void testOutputFmiIndependence() {
    DicomTranscodeParam param1 = new DicomTranscodeParam(JPEG_BASELINE);
    DicomTranscodeParam param2 = new DicomTranscodeParam(JPEG_BASELINE);

    param1.setOutputFmi(true);
    param2.setOutputFmi(false);

    assertTrue(param1.isOutputFmi());
    assertFalse(param2.isOutputFmi());
  }

  // Mask Management Tests

  @Test
  @DisplayName("Default mask state should be empty")
  void testDefaultMaskState() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    assertFalse(param.hasMasks());
    assertEquals(0, param.getMaskMap().size());
    assertNull(param.getMask(STATION_A));
    assertNull(param.getMask(null));
    assertFalse(param.hasMask(STATION_A));
  }

  @Test
  @DisplayName("addMask should add masks correctly")
  void testAddMask() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    param.addMask(STATION_A, maskAreaA);

    assertTrue(param.hasMasks());
    assertEquals(1, param.getMaskMap().size());
    assertSame(maskAreaA, param.getMask(STATION_A));
    assertTrue(param.hasMask(STATION_A));
  }

  @Test
  @DisplayName("addMask with null station name should add wildcard mask")
  void testAddWildcardMask() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    param.addMask(null, wildcardMaskArea);

    assertTrue(param.hasMasks());
    assertSame(wildcardMaskArea, param.getMask(null));
    assertSame(wildcardMaskArea, param.getMask("ANY_STATION"));
    assertTrue(param.hasMask(null));
    assertTrue(param.hasMask("ANY_STATION"));
  }

  @Test
  @DisplayName("addMask with null mask area should remove mask")
  void testAddNullMaskRemoves() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    // Add mask first
    param.addMask(STATION_A, maskAreaA);
    assertTrue(param.hasMask(STATION_A));

    // Remove by adding null
    param.addMask(STATION_A, null);
    assertFalse(param.hasMask(STATION_A));
    assertNull(param.getMask(STATION_A));
  }

  @Test
  @DisplayName("addMask should overwrite existing masks")
  void testAddMaskOverwrite() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    param.addMask(STATION_A, maskAreaA);
    assertSame(maskAreaA, param.getMask(STATION_A));

    param.addMask(STATION_A, maskAreaB);
    assertSame(maskAreaB, param.getMask(STATION_A));
    assertEquals(1, param.getMaskMap().size());
  }

  @Test
  @DisplayName("getMask should follow correct priority: exact match, then wildcard")
  void testGetMaskPriority() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    // Add wildcard mask
    param.addMask(null, wildcardMaskArea);
    param.addMask(STATION_A, maskAreaA);

    // Exact match should take precedence
    assertSame(maskAreaA, param.getMask(STATION_A));

    // Non-existent station should get wildcard
    assertSame(wildcardMaskArea, param.getMask(STATION_B));
    assertSame(wildcardMaskArea, param.getMask("UNKNOWN_STATION"));

    // Null key should get wildcard directly
    assertSame(wildcardMaskArea, param.getMask(null));
  }

  @Test
  @DisplayName("getMask should return null when no masks exist")
  void testGetMaskNoMasks() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    assertNull(param.getMask(STATION_A));
    assertNull(param.getMask(null));
    assertNull(param.getMask("ANY_STATION"));
  }

  @Test
  @DisplayName("addMaskMap should add multiple masks correctly")
  void testAddMaskMap() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    param.addMaskMap(testMaskMap);

    assertTrue(param.hasMasks());
    assertEquals(testMaskMap.size(), param.getMaskMap().size());

    for (Map.Entry<String, MaskArea> entry : testMaskMap.entrySet()) {
      assertSame(entry.getValue(), param.getMask(entry.getKey()));
    }
  }

  @Test
  @DisplayName("addMaskMap should throw NullPointerException for null map")
  void testAddMaskMapNull() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> param.addMaskMap(null));

    assertTrue(exception.getMessage().contains("Mask map cannot be null"));
  }

  @Test
  @DisplayName("addMaskMap should overwrite existing masks with same keys")
  void testAddMaskMapOverwrite() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    // Add initial mask
    param.addMask(STATION_A, maskAreaA);
    assertSame(maskAreaA, param.getMask(STATION_A));

    // Create map with different mask for same station
    Map<String, MaskArea> newMaskMap = new HashMap<>();
    newMaskMap.put(STATION_A, maskAreaB);
    newMaskMap.put(STATION_B, maskAreaC);

    param.addMaskMap(newMaskMap);

    // Should overwrite existing and add new
    assertSame(maskAreaB, param.getMask(STATION_A));
    assertSame(maskAreaC, param.getMask(STATION_B));
    assertEquals(2, param.getMaskMap().size());
  }

  @Test
  @DisplayName("getMaskMap should return independent copy")
  void testGetMaskMapIndependent() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);
    param.addMask(STATION_A, maskAreaA);

    Map<String, MaskArea> copyMap = param.getMaskMap();

    // Modify the copy
    copyMap.put(STATION_B, maskAreaB);
    copyMap.remove(STATION_A);

    // Original should be unchanged
    assertTrue(param.hasMask(STATION_A));
    assertFalse(param.hasMask(STATION_B));
    assertEquals(1, param.getMaskMap().size());
  }

  @Test
  @DisplayName("removeMask should remove masks correctly")
  void testRemoveMask() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);
    param.addMask(STATION_A, maskAreaA);
    param.addMask(STATION_B, maskAreaB);

    assertTrue(param.removeMask(STATION_A));
    assertFalse(param.hasMask(STATION_A));
    assertTrue(param.hasMask(STATION_B));
    assertEquals(1, param.getMaskMap().size());

    // Removing non-existent mask should return false
    assertFalse(param.removeMask(STATION_C));
    assertEquals(1, param.getMaskMap().size());
  }

  @Test
  @DisplayName("removeMask with null should remove wildcard mask")
  void testRemoveWildcardMask() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);
    param.addMask(null, wildcardMaskArea);
    param.addMask(STATION_A, maskAreaA);

    assertTrue(param.removeMask(null));
    assertNull(param.getMask(null));
    assertSame(maskAreaA, param.getMask(STATION_A)); // Other masks unaffected
    assertEquals(1, param.getMaskMap().size());
  }

  @Test
  @DisplayName("clearMasks should remove all masks")
  void testClearMasks() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);
    param.addMaskMap(testMaskMap);
    param.addMask(null, wildcardMaskArea);

    assertTrue(param.hasMasks());

    param.clearMasks();

    assertFalse(param.hasMasks());
    assertEquals(0, param.getMaskMap().size());
    assertNull(param.getMask(STATION_A));
    assertNull(param.getMask(null));
  }

  @Test
  @DisplayName("hasMask should check both specific and wildcard masks")
  void testHasMask() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);

    // No masks initially
    assertFalse(param.hasMask(STATION_A));
    assertFalse(param.hasMask(null));

    // Add wildcard mask
    param.addMask(null, wildcardMaskArea);
    assertTrue(param.hasMask(STATION_A)); // Should find wildcard
    assertTrue(param.hasMask(null));
    assertTrue(param.hasMask("UNKNOWN"));

    // Add specific mask
    param.addMask(STATION_A, maskAreaA);
    assertTrue(param.hasMask(STATION_A)); // Should find specific
    assertTrue(param.hasMask(STATION_B)); // Should find wildcard
  }

  // Copy Tests

  @Test
  @DisplayName("copy should create independent instance with same values")
  void testCopyBasic() {
    DicomTranscodeParam original = new DicomTranscodeParam(testReadParam, JPEG_BASELINE);
    original.setOutputFmi(true);

    DicomTranscodeParam copy = original.copy();

    assertNotSame(original, copy);
    assertEquals(original.getOutputTsuid(), copy.getOutputTsuid());
    assertSame(original.getReadParam(), copy.getReadParam()); // ReadParam should be same reference
    assertEquals(original.isOutputFmi(), copy.isOutputFmi());
    assertEquals(original.isCompressionEnabled(), copy.isCompressionEnabled());
    assertEquals(original.getMaskMap().size(), copy.getMaskMap().size());
  }

  @Test
  @DisplayName("copy should preserve all masks")
  void testCopyWithMasks() {
    DicomTranscodeParam original = new DicomTranscodeParam(JPEG_LOSSLESS);
    original.addMaskMap(testMaskMap);
    original.addMask(null, wildcardMaskArea);
    original.setOutputFmi(true);

    DicomTranscodeParam copy = original.copy();

    assertEquals(original.getMaskMap().size(), copy.getMaskMap().size());
    for (Map.Entry<String, MaskArea> entry : original.getMaskMap().entrySet()) {
      assertSame(entry.getValue(), copy.getMask(entry.getKey()));
    }
    assertSame(wildcardMaskArea, copy.getMask(null));
    assertTrue(copy.isOutputFmi());
  }

  @Test
  @DisplayName("copy should be independent - changes to copy should not affect original")
  void testCopyIndependence() {
    DicomTranscodeParam original = new DicomTranscodeParam(EXPLICIT_VR_LITTLE_ENDIAN);
    original.addMask(STATION_A, maskAreaA);
    original.setOutputFmi(false);

    DicomTranscodeParam copy = original.copy();

    // Modify copy
    copy.addMask(STATION_B, maskAreaB);
    copy.removeMask(STATION_A);
    copy.setOutputFmi(true);

    // Original should be unchanged
    assertTrue(original.hasMask(STATION_A));
    assertFalse(original.hasMask(STATION_B));
    assertFalse(original.isOutputFmi());
    assertEquals(1, original.getMaskMap().size());

    // Copy should have new values
    assertFalse(copy.hasMask(STATION_A));
    assertTrue(copy.hasMask(STATION_B));
    assertTrue(copy.isOutputFmi());
    assertEquals(1, copy.getMaskMap().size());
  }

  @Test
  @DisplayName("copy should work with no masks")
  void testCopyWithNoMasks() {
    DicomTranscodeParam original = new DicomTranscodeParam(EXPLICIT_VR_LITTLE_ENDIAN);
    original.setOutputFmi(true);

    DicomTranscodeParam copy = original.copy();

    assertEquals(EXPLICIT_VR_LITTLE_ENDIAN, copy.getOutputTsuid());
    assertTrue(copy.isOutputFmi());
    assertFalse(copy.hasMasks());
    assertFalse(copy.isCompressionEnabled());
  }

  // toString Tests

  @Test
  @DisplayName("toString should provide meaningful representation")
  void testToString() {
    DicomTranscodeParam param = new DicomTranscodeParam(JPEG_BASELINE);
    param.setOutputFmi(true);
    param.addMask(STATION_A, maskAreaA);
    param.addMask(STATION_B, maskAreaB);

    String result = param.toString();

    assertNotNull(result);
    assertTrue(result.contains("DicomTranscodeParam"));
    assertTrue(result.contains("outputTsuid='" + JPEG_BASELINE + "'"));
    assertTrue(result.contains("compression=enabled"));
    assertTrue(result.contains("outputFmi=true"));
    assertTrue(result.contains("masks=2"));
  }

  @Test
  @DisplayName("toString should handle native syntax")
  void testToStringNativeSyntax() {
    DicomTranscodeParam param = new DicomTranscodeParam(IMPLICIT_VR_LITTLE_ENDIAN);

    String result = param.toString();

    assertTrue(result.contains("outputTsuid='" + IMPLICIT_VR_LITTLE_ENDIAN + "'"));
    assertTrue(result.contains("compression=disabled"));
    assertTrue(result.contains("outputFmi=false"));
    assertTrue(result.contains("masks=0"));
  }

  @Test
  @DisplayName("toString should handle empty configuration")
  void testToStringEmpty() {
    DicomTranscodeParam param = new DicomTranscodeParam(EXPLICIT_VR_LITTLE_ENDIAN);

    String result = param.toString();

    assertTrue(result.contains("DicomTranscodeParam"));
    assertTrue(result.contains("compression=disabled"));
    assertTrue(result.contains("outputFmi=false"));
    assertTrue(result.contains("masks=0"));
  }

  // Integration Tests

  @Test
  @DisplayName("Full configuration workflow should work correctly")
  void testFullConfigurationWorkflow() {
    DicomTranscodeParam param = new DicomTranscodeParam(testReadParam, JPEG_2000_LOSSLESS);

    // Configure all aspects
    param.setOutputFmi(true);
    param.addMaskMap(testMaskMap);
    param.addMask(null, wildcardMaskArea);
    param.addMask(STATION_C, maskAreaC);

    // Verify all settings
    assertEquals(JPEG_2000_LOSSLESS, param.getOutputTsuid());
    assertSame(testReadParam, param.getReadParam());
    assertTrue(param.isCompressionEnabled());
    assertNotNull(param.getWriteJpegParam());
    assertTrue(param.isOutputFmi());

    // Verify mask functionality
    assertTrue(param.hasMasks());
    assertEquals(
        testMaskMap.size() + 2, param.getMaskMap().size()); // testMaskMap + wildcard + STATION_C

    // Test mask lookup priority
    assertSame(maskAreaC, param.getMask(STATION_C)); // Specific mask
    assertSame(wildcardMaskArea, param.getMask("UNKNOWN")); // Wildcard fallback

    // Create copy and verify independence
    DicomTranscodeParam copy = param.copy();
    copy.clearMasks();
    copy.setOutputFmi(false);

    // Original should be unchanged
    assertTrue(param.hasMasks());
    assertTrue(param.isOutputFmi());
  }

  @Test
  @DisplayName("Multiple instances should be independent")
  void testMultipleInstancesIndependence() {
    DicomTranscodeParam param1 = new DicomTranscodeParam(JPEG_BASELINE);
    DicomTranscodeParam param2 = new DicomTranscodeParam(EXPLICIT_VR_LITTLE_ENDIAN);
    DicomTranscodeParam param3 = new DicomTranscodeParam(UID.JPEGLSLossless);

    // Configure each differently
    param1.setOutputFmi(true);
    param1.addMask(STATION_A, maskAreaA);

    param2.setOutputFmi(false);
    param2.addMask(STATION_B, maskAreaB);

    param3.setOutputFmi(true);
    param3.addMask(null, wildcardMaskArea);

    // Verify each maintains its configuration
    assertEquals(JPEG_BASELINE, param1.getOutputTsuid());
    assertTrue(param1.isCompressionEnabled());
    assertTrue(param1.isOutputFmi());
    assertTrue(param1.hasMask(STATION_A));
    assertFalse(param1.hasMask(STATION_B));

    assertEquals(EXPLICIT_VR_LITTLE_ENDIAN, param2.getOutputTsuid());
    assertFalse(param2.isCompressionEnabled());
    assertFalse(param2.isOutputFmi());
    assertFalse(param2.hasMask(STATION_A));
    assertTrue(param2.hasMask(STATION_B));

    assertEquals(UID.JPEGLSLossless, param3.getOutputTsuid());
    assertTrue(param3.isCompressionEnabled());
    assertTrue(param3.isOutputFmi());
    assertTrue(param3.hasMask("ANY_STATION")); // Wildcard
  }

  // Helper methods for creating test data

  private DicomImageReadParam createTestReadParam() {
    DicomImageReadParam readParam = new DicomImageReadParam();
    readParam.setReleaseImageAfterProcessing(true);
    return readParam;
  }

  private Map<String, MaskArea> createTestMaskMap() {
    Map<String, MaskArea> maskMap = new HashMap<>();
    MaskArea maskAreaA = new MaskArea(List.of(new Rectangle(10, 20, 100, 150)), Color.ORANGE);
    maskMap.put(STATION_A, maskAreaA);
    MaskArea maskAreaB = new MaskArea(List.of(new Rectangle(50, 60, 200, 200)));
    maskMap.put(STATION_B, maskAreaB);
    return maskMap;
  }
}
