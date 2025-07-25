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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmbeddedOverlayTest {

  @Nested
  @DisplayName("Basic functionality tests")
  class BasicFunctionalityTests {

    @Test
    @DisplayName("Should return empty list when input is null")
    void shouldReturnEmptyListWhenInputIsNull() {
      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(null);

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list when no overlays are present")
    void shouldReturnEmptyListWhenNoOverlaysArePresent() {
      Attributes dcm = new Attributes();

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list when only standard overlays are present")
    void shouldReturnEmptyListWhenOnlyStandardOverlaysArePresent() {
      Attributes dcm = createBasicDicomAttributes(16, 12);
      addStandardOverlay(dcm, 0);
      addStandardOverlay(dcm, 1);

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("Embedded overlay detection tests")
  class EmbeddedOverlayDetectionTests {

    @Test
    @DisplayName("Should detect single embedded overlay")
    void shouldDetectSingleEmbeddedOverlay() {
      Attributes dcm = createBasicDicomAttributes(16, 12);
      addEmbeddedOverlay(dcm, 0, 16, 15);

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(1, result.size());
      EmbeddedOverlay overlay = result.get(0);
      assertEquals(0, overlay.groupOffset());
      assertEquals(15, overlay.bitPosition());
    }

    @Test
    @DisplayName("Should detect multiple embedded overlays")
    void shouldDetectMultipleEmbeddedOverlays() {
      Attributes dcm = createBasicDicomAttributes(16, 12);
      addEmbeddedOverlay(dcm, 0, 16, 15);
      addEmbeddedOverlay(dcm, 1, 16, 14);
      addEmbeddedOverlay(dcm, 3, 16, 13);

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(3, result.size());
      assertOverlayExists(result, 0, 15);
      assertOverlayExists(result, 1 << 17, 14);
      assertOverlayExists(result, 3 << 17, 13);
    }

    @Test
    @DisplayName("Should handle mixed embedded and standard overlays")
    void shouldHandleMixedEmbeddedAndStandardOverlays() {
      Attributes dcm = createBasicDicomAttributes(16, 12);
      addEmbeddedOverlay(dcm, 0, 16, 15);
      addStandardOverlay(dcm, 1);
      addEmbeddedOverlay(dcm, 2, 16, 14);
      addStandardOverlay(dcm, 3);

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(2, result.size());
      assertOverlayExists(result, 0, 15);
      assertOverlayExists(result, 2 << 17, 14);
    }
  }

  @Nested
  @DisplayName("Bit position validation tests")
  class BitPositionValidationTests {

    @Test
    @DisplayName("Should ignore overlays with bit position less than bits stored")
    void shouldIgnoreOverlaysWithBitPositionLessThanBitsStored() {
      Attributes dcm = createBasicDicomAttributes(16, 12);
      addEmbeddedOverlay(dcm, 0, 16, 11); // bitPosition < bitsStored
      addEmbeddedOverlay(dcm, 1, 16, 7); // bitPosition < bitsStored

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should accept overlays with bit position equal to bits stored")
    void shouldAcceptOverlaysWithBitPositionEqualToBitsStored() {
      Attributes dcm = createBasicDicomAttributes(16, 12);
      addEmbeddedOverlay(dcm, 0, 16, 12); // bitPosition == bitsStored

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(1, result.size());
      assertEquals(12, result.get(0).bitPosition());
    }

    @ParameterizedTest
    @CsvSource({
      "8, 8, 8, true",
      "16, 12, 12, true",
      "16, 12, 13, true",
      "16, 12, 15, true",
      "16, 12, 11, false",
      "16, 12, 5, false",
      "32, 16, 16, true",
      "32, 16, 15, false"
    })
    @DisplayName("Should validate bit positions correctly")
    void shouldValidateBitPositionsCorrectly(
        int bitsAllocated, int bitsStored, int bitPosition, boolean shouldBeIncluded) {

      Attributes dcm = createBasicDicomAttributes(bitsAllocated, bitsStored);
      addEmbeddedOverlay(dcm, 0, bitsAllocated, bitPosition);

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      if (shouldBeIncluded) {
        assertEquals(1, result.size());
        assertEquals(bitPosition, result.get(0).bitPosition());
      } else {
        assertTrue(result.isEmpty());
      }
    }
  }

  @Nested
  @DisplayName("Default values and edge cases tests")
  class DefaultValuesAndEdgeCasesTests {

    @Test
    @DisplayName("Should use default bits allocated when not specified")
    void shouldUseDefaultBitsAllocatedWhenNotSpecified() {
      Attributes dcm = new Attributes();
      // Don't set BitsAllocated - should default to 8
      // Don't set BitsStored - should default to bitsAllocated (8)
      addEmbeddedOverlay(dcm, 0, 16, 8); // bitPosition == default bitsStored

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should use bits allocated as default for bits stored")
    void shouldUseBitsAllocatedAsDefaultForBitsStored() {
      Attributes dcm = new Attributes();
      dcm.setInt(Tag.BitsAllocated, VR.US, 12);
      // Don't set BitsStored - should default to bitsAllocated (12)
      addEmbeddedOverlay(dcm, 0, 16, 12); // bitPosition == default bitsStored

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should use default bit position when not specified")
    void shouldUseDefaultBitPositionWhenNotSpecified() {
      Attributes dcm = createBasicDicomAttributes(16, 12);
      int groupOffset = 0;
      dcm.setInt(Tag.OverlayBitsAllocated | groupOffset, VR.US, 16);
      // Don't set OverlayBitPosition - should default to 0

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertTrue(result.isEmpty()); // Should be ignored because 0 < 12 (bitsStored)
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 10, 15})
    @DisplayName("Should process all overlay groups up to maximum")
    void shouldProcessAllOverlayGroupsUpToMaximum(int groupIndex) {
      Attributes dcm = createBasicDicomAttributes(16, 12);
      addEmbeddedOverlay(dcm, groupIndex, 16, 15);

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertEquals(1, result.size());
      assertEquals(groupIndex << 17, result.get(0).groupOffset());
    }

    @Test
    @DisplayName("Should not process overlay groups beyond maximum")
    void shouldNotProcessOverlayGroupsBeyondMaximum() {
      Attributes dcm = createBasicDicomAttributes(16, 12);
      // Try to add overlay at group 16 (beyond the 0-15 range)
      int invalidGroupOffset = 16 << 17;
      dcm.setInt(Tag.OverlayBitsAllocated | invalidGroupOffset, VR.US, 16);
      dcm.setInt(Tag.OverlayBitPosition | invalidGroupOffset, VR.US, 15);

      List<EmbeddedOverlay> result = EmbeddedOverlay.getEmbeddedOverlay(dcm);

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("Record functionality tests")
  class RecordFunctionalityTests {

    @Test
    @DisplayName("Should create overlay record with correct values")
    void shouldCreateOverlayRecordWithCorrectValues() {
      int expectedGroupOffset = 5 << 17;
      int expectedBitPosition = 14;

      EmbeddedOverlay overlay = new EmbeddedOverlay(expectedGroupOffset, expectedBitPosition);

      assertEquals(expectedGroupOffset, overlay.groupOffset());
      assertEquals(expectedBitPosition, overlay.bitPosition());
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
      EmbeddedOverlay overlay1 = new EmbeddedOverlay(1 << 17, 15);
      EmbeddedOverlay overlay2 = new EmbeddedOverlay(1 << 17, 15);
      EmbeddedOverlay overlay3 = new EmbeddedOverlay(2 << 17, 15);

      assertEquals(overlay1, overlay2);
      assertEquals(overlay1.hashCode(), overlay2.hashCode());
      assertNotEquals(overlay1, overlay3);
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToStringCorrectly() {
      EmbeddedOverlay overlay = new EmbeddedOverlay(1 << 17, 15);
      String toString = overlay.toString();

      assertTrue(toString.contains("groupOffset"));
      assertTrue(toString.contains("bitPosition"));
      assertTrue(toString.contains("131072")); // 1 << 17
      assertTrue(toString.contains("15"));
    }
  }

  // Helper methods
  private Attributes createBasicDicomAttributes(int bitsAllocated, int bitsStored) {
    Attributes dcm = new Attributes();
    dcm.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
    dcm.setInt(Tag.BitsStored, VR.US, bitsStored);
    dcm.setInt(Tag.OverlayRows, VR.US, 512);
    dcm.setInt(Tag.OverlayColumns, VR.US, 512);
    return dcm;
  }

  private void addEmbeddedOverlay(
      Attributes dcm, int groupIndex, int bitsAllocated, int bitPosition) {
    int groupOffset = groupIndex << 17;
    dcm.setString(Tag.OverlayType | groupOffset, VR.CS, "G");
    dcm.setInt(Tag.OverlayOrigin | groupOffset, VR.SS, 1, 1);
    dcm.setInt(Tag.OverlayBitsAllocated | groupOffset, VR.US, bitsAllocated);
    dcm.setInt(Tag.OverlayBitPosition | groupOffset, VR.US, bitPosition);
  }

  private void addStandardOverlay(Attributes dcm, int groupIndex) {
    int groupOffset = groupIndex << 17;
    dcm.setString(Tag.OverlayType | groupOffset, VR.CS, "G");
    dcm.setInt(Tag.OverlayOrigin | groupOffset, VR.SS, 1, 1);
    dcm.setInt(Tag.OverlayBitsAllocated | groupOffset, VR.US, 1); // Standard overlay
    dcm.setInt(Tag.OverlayBitPosition | groupOffset, VR.US, 0);
  }

  private void assertOverlayExists(
      List<EmbeddedOverlay> overlays, int expectedGroupOffset, int expectedBitPosition) {
    assertTrue(
        overlays.stream()
            .anyMatch(
                overlay ->
                    overlay.groupOffset() == expectedGroupOffset
                        && overlay.bitPosition() == expectedBitPosition),
        String.format(
            "Expected overlay with groupOffset=%d and bitPosition=%d not found",
            expectedGroupOffset, expectedBitPosition));
  }
}
