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

import java.util.List;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class EmbeddedOverlayTest {

  // Test data constants using Java 17 features
  private static final DicomTestData BASIC_16_12 = new DicomTestData(16, 12);
  private static final DicomTestData BASIC_8_8 = new DicomTestData(8, 8);
  private static final DicomTestData BASIC_32_16 = new DicomTestData(32, 16);

  private static final int GROUP_SHIFT = 17;
  private static final int STANDARD_OVERLAY_BITS = 1;

  @Nested
  class Basic_functionality_tests {

    @Test
    void return_empty_list_when_input_is_null() {
      var result = EmbeddedOverlay.getEmbeddedOverlay(null);

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void return_empty_list_when_no_overlays_are_present() {
      var dcm = new Attributes();

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void return_empty_list_when_only_standard_overlays_are_present() {
      var dcm = BASIC_16_12.createDicomAttributes();
      addStandardOverlay(dcm, 0);
      addStandardOverlay(dcm, 1);

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class Embedded_overlay_detection_tests {

    @Test
    void detect_single_embedded_overlay() {
      var dcm = BASIC_16_12.createDicomAttributes();
      addEmbeddedOverlay(dcm, 0, 16, 15);

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(1, result.size());
      var overlay = result.get(0);
      assertEquals(0, overlay.groupOffset());
      assertEquals(15, overlay.bitPosition());
    }

    @Test
    void detect_multiple_embedded_overlays() {
      var dcm = BASIC_16_12.createDicomAttributes();
      var expectedOverlays =
          List.of(
              new OverlayTestData(0, 16, 15),
              new OverlayTestData(1, 16, 14),
              new OverlayTestData(3, 16, 13));

      expectedOverlays.forEach(
          overlay ->
              addEmbeddedOverlay(
                  dcm, overlay.groupIndex(), overlay.bitsAllocated(), overlay.bitPosition()));

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(3, result.size());
      expectedOverlays.forEach(
          expected ->
              assertOverlayExists(
                  result, expected.groupIndex() << GROUP_SHIFT, expected.bitPosition()));
    }

    @Test
    void handle_mixed_embedded_and_standard_overlays() {
      var dcm = BASIC_16_12.createDicomAttributes();
      addEmbeddedOverlay(dcm, 0, 16, 15);
      addStandardOverlay(dcm, 1);
      addEmbeddedOverlay(dcm, 2, 16, 14);
      addStandardOverlay(dcm, 3);

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(2, result.size());
      assertOverlayExists(result, 0, 15);
      assertOverlayExists(result, 2 << GROUP_SHIFT, 14);
    }
  }

  @Nested
  class Bit_position_validation_tests {

    @Test
    void ignore_overlays_with_bit_position_less_than_bits_stored() {
      var dcm = BASIC_16_12.createDicomAttributes();
      addEmbeddedOverlay(dcm, 0, 16, 11); // bitPosition < bitsStored
      addEmbeddedOverlay(dcm, 1, 16, 7); // bitPosition < bitsStored

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertTrue(result.isEmpty());
    }

    @Test
    void accept_overlays_with_bit_position_equal_to_bits_stored() {
      var dcm = BASIC_16_12.createDicomAttributes();
      addEmbeddedOverlay(dcm, 0, 16, 12); // bitPosition == bitsStored

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(1, result.size());
      assertEquals(12, result.get(0).bitPosition());
    }

    @ParameterizedTest
    @MethodSource("bitPositionTestCases")
    void validate_bit_positions_correctly(
        DicomTestData testData, int bitPosition, boolean shouldBeIncluded) {
      var dcm = testData.createDicomAttributes();
      addEmbeddedOverlay(dcm, 0, testData.bitsAllocated(), bitPosition);

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      if (shouldBeIncluded) {
        assertEquals(1, result.size());
        assertEquals(bitPosition, result.get(0).bitPosition());
      } else {
        assertTrue(result.isEmpty());
      }
    }

    private static Stream<Arguments> bitPositionTestCases() {
      return Stream.of(
          Arguments.of(BASIC_8_8, 8, true),
          Arguments.of(BASIC_16_12, 12, true),
          Arguments.of(BASIC_16_12, 13, true),
          Arguments.of(BASIC_16_12, 15, true),
          Arguments.of(BASIC_16_12, 11, false),
          Arguments.of(BASIC_16_12, 5, false),
          Arguments.of(BASIC_32_16, 16, true),
          Arguments.of(BASIC_32_16, 15, false));
    }
  }

  @Nested
  class Default_values_and_edge_cases_tests {

    @Test
    void use_default_bits_allocated_when_not_specified() {
      var dcm = new Attributes();
      // Don't set BitsAllocated - should default to 8
      // Don't set BitsStored - should default to bitsAllocated (8)
      addEmbeddedOverlay(dcm, 0, 16, 8); // bitPosition == default bitsStored

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(1, result.size());
    }

    @Test
    void use_bits_allocated_as_default_for_bits_stored() {
      var dcm = new Attributes();
      dcm.setInt(Tag.BitsAllocated, VR.US, 12);
      // Don't set BitsStored - should default to bitsAllocated (12)
      addEmbeddedOverlay(dcm, 0, 16, 12); // bitPosition == default bitsStored

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(1, result.size());
    }

    @Test
    void use_default_bit_position_when_not_specified() {
      var dcm = BASIC_16_12.createDicomAttributes();
      int groupOffset = 0;
      dcm.setInt(Tag.OverlayBitsAllocated | groupOffset, VR.US, 16);
      // Don't set OverlayBitPosition - should default to 0

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertTrue(result.isEmpty()); // Should be ignored because 0 < 12 (bitsStored)
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 10, 15})
    void process_all_overlay_groups_up_to_maximum(int groupIndex) {
      var dcm = BASIC_16_12.createDicomAttributes();
      addEmbeddedOverlay(dcm, groupIndex, 16, 15);

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(1, result.size());
      assertEquals(groupIndex << GROUP_SHIFT, result.get(0).groupOffset());
    }

    @Test
    void not_process_overlay_groups_beyond_maximum() {
      var dcm = BASIC_16_12.createDicomAttributes();
      // Try to add overlay at group 16 (beyond the 0-15 range)
      int invalidGroupOffset = 16 << GROUP_SHIFT;
      dcm.setInt(Tag.OverlayBitsAllocated | invalidGroupOffset, VR.US, 16);
      dcm.setInt(Tag.OverlayBitPosition | invalidGroupOffset, VR.US, 15);

      var result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class Record_functionality_tests {

    @Test
    void create_overlay_record_with_correct_values() {
      int expectedGroupOffset = 5 << GROUP_SHIFT;
      int expectedBitPosition = 14;

      var overlay = new EmbeddedOverlay(expectedGroupOffset, expectedBitPosition);

      assertEquals(expectedGroupOffset, overlay.groupOffset());
      assertEquals(expectedBitPosition, overlay.bitPosition());
    }

    @ParameterizedTest
    @MethodSource("equalityTestCases")
    void implement_equals_and_hashCode_correctly(
        EmbeddedOverlay overlay1, EmbeddedOverlay overlay2, boolean shouldBeEqual) {
      if (shouldBeEqual) {
        assertEquals(overlay1, overlay2);
        assertEquals(overlay1.hashCode(), overlay2.hashCode());
      } else {
        assertNotEquals(overlay1, overlay2);
      }
    }

    private static Stream<Arguments> equalityTestCases() {
      var overlay1 = new EmbeddedOverlay(1 << GROUP_SHIFT, 15);
      var overlay2 = new EmbeddedOverlay(1 << GROUP_SHIFT, 15);
      var overlay3 = new EmbeddedOverlay(2 << GROUP_SHIFT, 15);
      var overlay4 = new EmbeddedOverlay(1 << GROUP_SHIFT, 14);

      return Stream.of(
          Arguments.of(overlay1, overlay2, true),
          Arguments.of(overlay1, overlay3, false),
          Arguments.of(overlay1, overlay4, false));
    }

    @Test
    void implement_toString_correctly() {
      var overlay = new EmbeddedOverlay(1 << GROUP_SHIFT, 15);
      var toString = overlay.toString();

      assertAll(
          () -> assertTrue(toString.contains("groupOffset")),
          () -> assertTrue(toString.contains("bitPosition")),
          () -> assertTrue(toString.contains("131072")), // 1 << 17
          () -> assertTrue(toString.contains("15")));
    }
  }

  // Test data records using Java 17 features
  private record DicomTestData(int bitsAllocated, int bitsStored) {
    Attributes createDicomAttributes() {
      var dcm = new Attributes();
      dcm.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
      dcm.setInt(Tag.BitsStored, VR.US, bitsStored);
      dcm.setInt(Tag.OverlayRows, VR.US, 512);
      dcm.setInt(Tag.OverlayColumns, VR.US, 512);
      return dcm;
    }
  }

  private record OverlayTestData(int groupIndex, int bitsAllocated, int bitPosition) {}

  // Helper methods
  private static void addEmbeddedOverlay(
      Attributes dcm, int groupIndex, int bitsAllocated, int bitPosition) {
    int groupOffset = groupIndex << GROUP_SHIFT;
    dcm.setString(Tag.OverlayType | groupOffset, VR.CS, "G");
    dcm.setInt(Tag.OverlayOrigin | groupOffset, VR.SS, 1, 1);
    dcm.setInt(Tag.OverlayBitsAllocated | groupOffset, VR.US, bitsAllocated);
    dcm.setInt(Tag.OverlayBitPosition | groupOffset, VR.US, bitPosition);
  }

  private static void addStandardOverlay(Attributes dcm, int groupIndex) {
    int groupOffset = groupIndex << GROUP_SHIFT;
    dcm.setString(Tag.OverlayType | groupOffset, VR.CS, "G");
    dcm.setInt(Tag.OverlayOrigin | groupOffset, VR.SS, 1, 1);
    dcm.setInt(Tag.OverlayBitsAllocated | groupOffset, VR.US, STANDARD_OVERLAY_BITS);
    dcm.setInt(Tag.OverlayBitPosition | groupOffset, VR.US, 0);
  }

  private static void assertOverlayExists(
      List<EmbeddedOverlay> overlays, int expectedGroupOffset, int expectedBitPosition) {
    assertTrue(
        overlays.stream()
            .anyMatch(
                overlay ->
                    overlay.groupOffset() == expectedGroupOffset
                        && overlay.bitPosition() == expectedBitPosition),
        () ->
            "Expected overlay with groupOffset=%d and bitPosition=%d not found"
                .formatted(expectedGroupOffset, expectedBitPosition));
  }
}
