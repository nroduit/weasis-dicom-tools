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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.op.MaskArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test class for {@link DicomTranscodeParam}.
 *
 * <p>This test class validates DICOM transcoding parameter functionality including transfer syntax
 * configuration, mask management, File Meta Information settings, and integration with DICOM
 * reading/writing parameters using real data structures and comprehensive validation.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomTranscodeParamTest {

  // Real DICOM Transfer Syntax UIDs
  private static final String IMPLICIT_VR_LITTLE_ENDIAN = UID.ImplicitVRLittleEndian;
  private static final String EXPLICIT_VR_LITTLE_ENDIAN = UID.ExplicitVRLittleEndian;
  private static final String JPEG_BASELINE = UID.JPEGBaseline8Bit;
  private static final String JPEG_LOSSLESS = UID.JPEGLossless;
  private static final String JPEG_2000_LOSSLESS = UID.JPEG2000Lossless;
  private static final String JPEG_LS_LOSSLESS = UID.JPEGLSLossless;

  // Test station identifiers
  private static final String STATION_A = "CT-SCANNER-01";
  private static final String STATION_B = "MR-UNIT-02";
  private static final String STATION_C = "US-MACHINE-03";

  private DicomImageReadParam customReadParam;
  private Map<String, MaskArea> predefinedMaskMap;
  private MaskArea ctScannerMask;
  private MaskArea mrUnitMask;
  private MaskArea ultrasoundMask;
  private MaskArea defaultWildcardMask;

  @BeforeEach
  void setUp() {
    customReadParam = createCustomReadParam();

    // Create realistic mask areas for different modalities
    ctScannerMask = new MaskArea(List.of(new Rectangle(10, 20, 512, 512)), Color.BLUE);
    mrUnitMask = new MaskArea(List.of(new Rectangle(25, 25, 256, 256)), Color.GREEN);
    ultrasoundMask = new MaskArea(List.of(new Rectangle(0, 0, 640, 480)));
    defaultWildcardMask = new MaskArea(List.of(new Rectangle(50, 50, 400, 300)), Color.RED);

    predefinedMaskMap =
        Map.of(
            STATION_A, ctScannerMask,
            STATION_B, mrUnitMask);
  }

  @Nested
  class Constructor_Tests {

    @Test
    void constructor_with_transfer_syntax_creates_instance_with_defaults() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      assertAll(
          "Basic construction validation",
          () -> assertNotNull(param),
          () -> assertEquals(JPEG_BASELINE, param.getOutputTsuid()),
          () -> assertNotNull(param.getReadParam()),
          () -> assertNotNull(param.getWriteJpegParam()),
          () -> assertTrue(param.isCompressionEnabled()),
          () -> assertFalse(param.isOutputFmi()),
          () -> assertFalse(param.hasMasks()),
          () -> assertEquals(0, param.getMaskMap().size()));
    }

    @Test
    void constructor_with_custom_read_param_uses_provided_values() {
      var param = new DicomTranscodeParam(customReadParam, EXPLICIT_VR_LITTLE_ENDIAN);

      assertAll(
          "Custom read param validation",
          () -> assertEquals(EXPLICIT_VR_LITTLE_ENDIAN, param.getOutputTsuid()),
          () -> assertSame(customReadParam, param.getReadParam()),
          () -> assertNull(param.getWriteJpegParam()),
          () -> assertFalse(param.isCompressionEnabled()),
          () -> assertFalse(param.isOutputFmi()));
    }

    @Test
    void constructor_with_null_read_param_creates_default() {
      var param = new DicomTranscodeParam(null, JPEG_LOSSLESS);

      assertAll(
          "Null read param handling",
          () -> assertEquals(JPEG_LOSSLESS, param.getOutputTsuid()),
          () -> assertNotNull(param.getReadParam()),
          () -> assertNotSame(customReadParam, param.getReadParam()),
          () -> assertNotNull(param.getWriteJpegParam()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void constructor_throws_exception_for_empty_transfer_syntax(String emptyTsuid) {
      var exception =
          assertThrows(IllegalArgumentException.class, () -> new DicomTranscodeParam(emptyTsuid));

      assertTrue(exception.getMessage().contains("Transfer syntax UID cannot be null or empty"));
    }

    @Test
    void constructor_throws_exception_for_null_transfer_syntax() {
      var exception =
          assertThrows(IllegalArgumentException.class, () -> new DicomTranscodeParam(null));

      assertTrue(exception.getMessage().contains("Transfer syntax UID cannot be null or empty"));
    }
  }

  @Nested
  class Transfer_Syntax_Tests {

    static Stream<String> nativeTransferSyntaxes() {
      return Stream.of(IMPLICIT_VR_LITTLE_ENDIAN, EXPLICIT_VR_LITTLE_ENDIAN);
    }

    static Stream<String> compressedTransferSyntaxes() {
      return Stream.of(
          JPEG_BASELINE, JPEG_LOSSLESS, JPEG_2000_LOSSLESS, JPEG_LS_LOSSLESS, UID.JPEG2000);
    }

    @ParameterizedTest
    @MethodSource("nativeTransferSyntaxes")
    void native_transfer_syntaxes_do_not_enable_compression(String nativeTsuid) {
      var param = new DicomTranscodeParam(nativeTsuid);

      assertAll(
          "Native syntax validation",
          () -> assertEquals(nativeTsuid, param.getOutputTsuid()),
          () -> assertFalse(param.isCompressionEnabled()),
          () -> assertNull(param.getWriteJpegParam()));
    }

    @ParameterizedTest
    @MethodSource("compressedTransferSyntaxes")
    void compressed_transfer_syntaxes_enable_compression(String compressedTsuid) {
      var param = new DicomTranscodeParam(compressedTsuid);

      assertAll(
          "Compressed syntax validation",
          () -> assertEquals(compressedTsuid, param.getOutputTsuid()),
          () -> assertTrue(param.isCompressionEnabled()),
          () -> assertNotNull(param.getWriteJpegParam()));
    }

    @Test
    void big_endian_syntax_throws_exception() {
      assertThrows(
          IllegalStateException.class, () -> new DicomTranscodeParam(UID.ExplicitVRBigEndian));
    }

    @Test
    void output_tsuid_returns_immutable_reference() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);
      var tsuid1 = param.getOutputTsuid();
      var tsuid2 = param.getOutputTsuid();

      assertAll(
          "Immutability check",
          () -> assertSame(tsuid1, tsuid2),
          () -> assertEquals(JPEG_BASELINE, tsuid1));
    }
  }

  @Nested
  class File_Meta_Information_Tests {

    @Test
    void default_output_fmi_is_false() {
      var param = new DicomTranscodeParam(EXPLICIT_VR_LITTLE_ENDIAN);

      assertFalse(param.isOutputFmi());
    }

    @Test
    void set_output_fmi_configures_correctly() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      param.setOutputFmi(true);
      assertTrue(param.isOutputFmi());

      param.setOutputFmi(false);
      assertFalse(param.isOutputFmi());
    }

    @Test
    void output_fmi_is_independent_across_instances() {
      var param1 = new DicomTranscodeParam(JPEG_BASELINE);
      var param2 = new DicomTranscodeParam(JPEG_BASELINE);

      param1.setOutputFmi(true);
      param2.setOutputFmi(false);

      assertAll(
          "Independence verification",
          () -> assertTrue(param1.isOutputFmi()),
          () -> assertFalse(param2.isOutputFmi()));
    }
  }

  @Nested
  class Mask_Management_Tests {

    @Test
    void default_mask_state_is_empty() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      assertAll(
          "Default mask state",
          () -> assertFalse(param.hasMasks()),
          () -> assertEquals(0, param.getMaskMap().size()),
          () -> assertNull(param.getMask(STATION_A)),
          () -> assertNull(param.getMask(null)),
          () -> assertFalse(param.hasMask(STATION_A)));
    }

    @Test
    void add_mask_adds_masks_correctly() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      param.addMask(STATION_A, ctScannerMask);

      assertAll(
          "Single mask addition",
          () -> assertTrue(param.hasMasks()),
          () -> assertEquals(1, param.getMaskMap().size()),
          () -> assertSame(ctScannerMask, param.getMask(STATION_A)),
          () -> assertTrue(param.hasMask(STATION_A)));
    }

    @Test
    void add_mask_with_null_station_adds_wildcard() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      param.addMask(null, defaultWildcardMask);

      assertAll(
          "Wildcard mask addition",
          () -> assertTrue(param.hasMasks()),
          () -> assertSame(defaultWildcardMask, param.getMask(null)),
          () -> assertSame(defaultWildcardMask, param.getMask("UNKNOWN_STATION")),
          () -> assertTrue(param.hasMask(null)),
          () -> assertTrue(param.hasMask("UNKNOWN_STATION")));
    }

    @Test
    void add_mask_with_null_area_removes_mask() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      param.addMask(STATION_A, ctScannerMask);
      assertTrue(param.hasMask(STATION_A));

      param.addMask(STATION_A, null);

      assertAll(
          "Mask removal via null",
          () -> assertFalse(param.hasMask(STATION_A)),
          () -> assertNull(param.getMask(STATION_A)));
    }

    @Test
    void add_mask_overwrites_existing_masks() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      param.addMask(STATION_A, ctScannerMask);
      assertSame(ctScannerMask, param.getMask(STATION_A));

      param.addMask(STATION_A, mrUnitMask);

      assertAll(
          "Mask overwriting",
          () -> assertSame(mrUnitMask, param.getMask(STATION_A)),
          () -> assertEquals(1, param.getMaskMap().size()));
    }

    @Test
    void get_mask_follows_correct_priority() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      param.addMask(null, defaultWildcardMask);
      param.addMask(STATION_A, ctScannerMask);

      assertAll(
          "Mask lookup priority",
          () -> assertSame(ctScannerMask, param.getMask(STATION_A)),
          () -> assertSame(defaultWildcardMask, param.getMask(STATION_B)),
          () -> assertSame(defaultWildcardMask, param.getMask("UNKNOWN")),
          () -> assertSame(defaultWildcardMask, param.getMask(null)));
    }

    @Test
    void get_mask_returns_null_when_no_masks_exist() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      assertAll(
          "No masks scenario",
          () -> assertNull(param.getMask(STATION_A)),
          () -> assertNull(param.getMask(null)),
          () -> assertNull(param.getMask("UNKNOWN")));
    }

    @Test
    void add_mask_map_adds_multiple_masks() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      param.addMaskMap(predefinedMaskMap);

      assertAll(
          "Multiple mask addition",
          () -> assertTrue(param.hasMasks()),
          () -> assertEquals(predefinedMaskMap.size(), param.getMaskMap().size()),
          () -> assertSame(ctScannerMask, param.getMask(STATION_A)),
          () -> assertSame(mrUnitMask, param.getMask(STATION_B)));
    }

    @Test
    void add_mask_map_throws_exception_for_null() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      var exception = assertThrows(NullPointerException.class, () -> param.addMaskMap(null));

      assertTrue(exception.getMessage().contains("Mask map cannot be null"));
    }

    @Test
    void add_mask_map_overwrites_existing_masks() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      param.addMask(STATION_A, ctScannerMask);
      assertSame(ctScannerMask, param.getMask(STATION_A));

      var newMaskMap =
          Map.of(
              STATION_A, mrUnitMask,
              STATION_B, ultrasoundMask);
      param.addMaskMap(newMaskMap);

      assertAll(
          "Mask map overwriting",
          () -> assertSame(mrUnitMask, param.getMask(STATION_A)),
          () -> assertSame(ultrasoundMask, param.getMask(STATION_B)),
          () -> assertEquals(2, param.getMaskMap().size()));
    }

    @Test
    void get_mask_map_returns_immutable_copy() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);
      param.addMask(STATION_A, ctScannerMask);

      var maskMapCopy = param.getMaskMap();

      // Attempting to modify the copy should fail
      assertThrows(
          UnsupportedOperationException.class, () -> maskMapCopy.put(STATION_B, mrUnitMask));
      assertThrows(UnsupportedOperationException.class, () -> maskMapCopy.remove(STATION_A));

      // Original should be unchanged
      assertAll(
          "Immutable copy verification",
          () -> assertTrue(param.hasMask(STATION_A)),
          () -> assertFalse(param.hasMask(STATION_B)),
          () -> assertEquals(1, param.getMaskMap().size()));
    }

    @Test
    void remove_mask_removes_correctly() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);
      param.addMask(STATION_A, ctScannerMask);
      param.addMask(STATION_B, mrUnitMask);

      var removed = param.removeMask(STATION_A);

      assertAll(
          "Mask removal",
          () -> assertTrue(removed),
          () -> assertFalse(param.hasMask(STATION_A)),
          () -> assertTrue(param.hasMask(STATION_B)),
          () -> assertEquals(1, param.getMaskMap().size()));

      // Removing non-existent mask returns false
      assertFalse(param.removeMask(STATION_C));
    }

    @Test
    void remove_wildcard_mask_works_correctly() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);
      param.addMask(null, defaultWildcardMask);
      param.addMask(STATION_A, ctScannerMask);

      var removed = param.removeMask(null);

      assertAll(
          "Wildcard mask removal",
          () -> assertTrue(removed),
          () -> assertNull(param.getMask(null)),
          () -> assertSame(ctScannerMask, param.getMask(STATION_A)),
          () -> assertEquals(1, param.getMaskMap().size()));
    }

    @Test
    void clear_masks_removes_all_masks() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);
      param.addMaskMap(predefinedMaskMap);
      param.addMask(null, defaultWildcardMask);

      assertTrue(param.hasMasks());

      param.clearMasks();

      assertAll(
          "Clear masks verification",
          () -> assertFalse(param.hasMasks()),
          () -> assertEquals(0, param.getMaskMap().size()),
          () -> assertNull(param.getMask(STATION_A)),
          () -> assertNull(param.getMask(null)));
    }

    @Test
    void has_mask_checks_both_specific_and_wildcard() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);

      assertAll(
          "Initially no masks",
          () -> assertFalse(param.hasMask(STATION_A)),
          () -> assertFalse(param.hasMask(null)));

      param.addMask(null, defaultWildcardMask);
      assertAll(
          "With wildcard mask",
          () -> assertTrue(param.hasMask(STATION_A)),
          () -> assertTrue(param.hasMask(null)),
          () -> assertTrue(param.hasMask("UNKNOWN")));

      param.addMask(STATION_A, ctScannerMask);
      assertAll(
          "With specific and wildcard masks",
          () -> assertTrue(param.hasMask(STATION_A)),
          () -> assertTrue(param.hasMask(STATION_B)));
    }
  }

  @Nested
  class Copy_Tests {

    @Test
    void copy_creates_independent_instance_with_same_values() {
      var original = new DicomTranscodeParam(customReadParam, JPEG_BASELINE);
      original.setOutputFmi(true);

      var copy = original.copy();

      assertAll(
          "Copy validation",
          () -> assertNotSame(original, copy),
          () -> assertEquals(original.getOutputTsuid(), copy.getOutputTsuid()),
          () -> assertSame(original.getReadParam(), copy.getReadParam()),
          () -> assertEquals(original.isOutputFmi(), copy.isOutputFmi()),
          () -> assertEquals(original.isCompressionEnabled(), copy.isCompressionEnabled()),
          () -> assertEquals(original.getMaskMap().size(), copy.getMaskMap().size()));
    }

    @Test
    void copy_preserves_all_masks() {
      var original = new DicomTranscodeParam(JPEG_LOSSLESS);
      original.addMaskMap(predefinedMaskMap);
      original.addMask(null, defaultWildcardMask);
      original.setOutputFmi(true);

      var copy = original.copy();

      assertAll(
          "Mask preservation in copy",
          () -> assertEquals(original.getMaskMap().size(), copy.getMaskMap().size()),
          () -> assertSame(ctScannerMask, copy.getMask(STATION_A)),
          () -> assertSame(mrUnitMask, copy.getMask(STATION_B)),
          () -> assertSame(defaultWildcardMask, copy.getMask(null)),
          () -> assertTrue(copy.isOutputFmi()));
    }

    @Test
    void copy_is_independent_of_original() {
      var original = new DicomTranscodeParam(EXPLICIT_VR_LITTLE_ENDIAN);
      original.addMask(STATION_A, ctScannerMask);
      original.setOutputFmi(false);

      var copy = original.copy();

      // Modify copy
      copy.addMask(STATION_B, mrUnitMask);
      copy.removeMask(STATION_A);
      copy.setOutputFmi(true);

      assertAll(
          "Original unchanged after copy modification",
          () -> assertTrue(original.hasMask(STATION_A)),
          () -> assertFalse(original.hasMask(STATION_B)),
          () -> assertFalse(original.isOutputFmi()),
          () -> assertEquals(1, original.getMaskMap().size()));

      assertAll(
          "Copy has expected modifications",
          () -> assertFalse(copy.hasMask(STATION_A)),
          () -> assertTrue(copy.hasMask(STATION_B)),
          () -> assertTrue(copy.isOutputFmi()),
          () -> assertEquals(1, copy.getMaskMap().size()));
    }

    @Test
    void copy_works_with_empty_masks() {
      var original = new DicomTranscodeParam(EXPLICIT_VR_LITTLE_ENDIAN);
      original.setOutputFmi(true);

      var copy = original.copy();

      assertAll(
          "Copy with no masks",
          () -> assertEquals(EXPLICIT_VR_LITTLE_ENDIAN, copy.getOutputTsuid()),
          () -> assertTrue(copy.isOutputFmi()),
          () -> assertFalse(copy.hasMasks()),
          () -> assertFalse(copy.isCompressionEnabled()));
    }
  }

  @Nested
  class ToString_Tests {

    @Test
    void toString_provides_meaningful_representation() {
      var param = new DicomTranscodeParam(JPEG_BASELINE);
      param.setOutputFmi(true);
      param.addMask(STATION_A, ctScannerMask);
      param.addMask(STATION_B, mrUnitMask);

      var result = param.toString();

      assertAll(
          "String representation content",
          () -> assertNotNull(result),
          () -> assertTrue(result.contains("DicomTranscodeParam")),
          () -> assertTrue(result.contains("outputTsuid='" + JPEG_BASELINE + "'")),
          () -> assertTrue(result.contains("compression=enabled")),
          () -> assertTrue(result.contains("outputFmi=true")),
          () -> assertTrue(result.contains("masks=2")));
    }

    static Stream<Arguments> toStringTestCases() {
      return Stream.of(
          Arguments.of(
              IMPLICIT_VR_LITTLE_ENDIAN, "compression=disabled", "outputFmi=false", "masks=0"),
          Arguments.of(
              EXPLICIT_VR_LITTLE_ENDIAN, "compression=disabled", "outputFmi=false", "masks=0"),
          Arguments.of(JPEG_2000_LOSSLESS, "compression=enabled", "outputFmi=false", "masks=0"));
    }

    @ParameterizedTest
    @MethodSource("toStringTestCases")
    void toString_handles_different_configurations(
        String tsuid, String compression, String fmi, String masks) {
      var param = new DicomTranscodeParam(tsuid);

      var result = param.toString();

      assertAll(
          "String representation for " + tsuid,
          () -> assertTrue(result.contains("DicomTranscodeParam")),
          () -> assertTrue(result.contains("outputTsuid='" + tsuid + "'")),
          () -> assertTrue(result.contains(compression)),
          () -> assertTrue(result.contains(fmi)),
          () -> assertTrue(result.contains(masks)));
    }
  }

  @Nested
  class Integration_Tests {

    @Test
    void full_configuration_workflow_works_correctly() {
      var param = new DicomTranscodeParam(customReadParam, JPEG_2000_LOSSLESS);

      // Configure all aspects
      param.setOutputFmi(true);
      param.addMaskMap(predefinedMaskMap);
      param.addMask(null, defaultWildcardMask);
      param.addMask(STATION_C, ultrasoundMask);

      assertAll(
          "Full configuration verification",
          () -> assertEquals(JPEG_2000_LOSSLESS, param.getOutputTsuid()),
          () -> assertSame(customReadParam, param.getReadParam()),
          () -> assertTrue(param.isCompressionEnabled()),
          () -> assertNotNull(param.getWriteJpegParam()),
          () -> assertTrue(param.isOutputFmi()),
          () -> assertTrue(param.hasMasks()),
          () -> assertEquals(4, param.getMaskMap().size()));

      // Test mask lookup priority
      assertAll(
          "Mask lookup verification",
          () -> assertSame(ultrasoundMask, param.getMask(STATION_C)),
          () -> assertSame(defaultWildcardMask, param.getMask("UNKNOWN")));

      // Test copy independence
      var copy = param.copy();
      copy.clearMasks();
      copy.setOutputFmi(false);

      assertAll(
          "Original unchanged after copy operations",
          () -> assertTrue(param.hasMasks()),
          () -> assertTrue(param.isOutputFmi()));
    }

    @Test
    void multiple_instances_maintain_independence() {
      var param1 = new DicomTranscodeParam(JPEG_BASELINE);
      var param2 = new DicomTranscodeParam(EXPLICIT_VR_LITTLE_ENDIAN);
      var param3 = new DicomTranscodeParam(JPEG_LS_LOSSLESS);

      // Configure each differently
      param1.setOutputFmi(true);
      param1.addMask(STATION_A, ctScannerMask);

      param2.setOutputFmi(false);
      param2.addMask(STATION_B, mrUnitMask);

      param3.setOutputFmi(true);
      param3.addMask(null, defaultWildcardMask);

      // Verify independence
      assertAll(
          "Instance 1 configuration",
          () -> assertEquals(JPEG_BASELINE, param1.getOutputTsuid()),
          () -> assertTrue(param1.isCompressionEnabled()),
          () -> assertTrue(param1.isOutputFmi()),
          () -> assertTrue(param1.hasMask(STATION_A)),
          () -> assertFalse(param1.hasMask(STATION_B)));

      assertAll(
          "Instance 2 configuration",
          () -> assertEquals(EXPLICIT_VR_LITTLE_ENDIAN, param2.getOutputTsuid()),
          () -> assertFalse(param2.isCompressionEnabled()),
          () -> assertFalse(param2.isOutputFmi()),
          () -> assertFalse(param2.hasMask(STATION_A)),
          () -> assertTrue(param2.hasMask(STATION_B)));

      assertAll(
          "Instance 3 configuration",
          () -> assertEquals(JPEG_LS_LOSSLESS, param3.getOutputTsuid()),
          () -> assertTrue(param3.isCompressionEnabled()),
          () -> assertTrue(param3.isOutputFmi()),
          () -> assertTrue(param3.hasMask("ANY_STATION")));
    }
  }

  private DicomImageReadParam createCustomReadParam() {
    var readParam = new DicomImageReadParam();
    readParam.setReleaseImageAfterProcessing(true);
    return readParam;
  }
}
