/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.hp.enums.ColorPalette;
import org.weasis.dicom.hp.enums.HangingProtocolLevel;
import org.weasis.dicom.hp.enums.ImageBoxLayoutType;
import org.weasis.dicom.hp.enums.ImagePlaneOrientation;
import org.weasis.dicom.hp.enums.PartialDataDisplayHandling;
import org.weasis.dicom.hp.enums.Presence;
import org.weasis.dicom.hp.enums.PresetWindowLevel;
import org.weasis.dicom.hp.enums.ReconstructionType;
import org.weasis.dicom.hp.enums.SelectorUsageFlag;
import org.weasis.dicom.hp.enums.SelectorValueNumber;
import org.weasis.dicom.hp.enums.SortingDirection;
import org.weasis.dicom.hp.enums.SortingOperation;
import org.weasis.dicom.hp.enums.SynchronizationType;
import org.weasis.dicom.hp.enums.TileDirection;
import org.weasis.dicom.hp.enums.YesNo;

/** Test suite for Hanging Protocol enum types. */
@DisplayName("Hanging Protocol Enums Test")
public class HangingProtocolEnumsTest {

  @Test
  @DisplayName("YesNo enum should have correct values")
  void testYesNo() {
    assertEquals("YES", YesNo.YES.getCodeString());
    assertEquals("NO", YesNo.NO.getCodeString());
    assertEquals(YesNo.YES, YesNo.fromString("YES"));
    assertEquals(YesNo.NO, YesNo.fromString("NO"));
    assertThrows(IllegalArgumentException.class, () -> YesNo.fromString("INVALID"));
  }

  @Test
  @DisplayName("SelectorUsageFlag enum should have correct values and behavior")
  void testSelectorUsageFlag() {
    assertEquals("MATCH", SelectorUsageFlag.MATCH.getCodeString());
    assertEquals("NO_MATCH", SelectorUsageFlag.NO_MATCH.getCodeString());
    assertTrue(SelectorUsageFlag.MATCH.isMatch());
    assertFalse(SelectorUsageFlag.NO_MATCH.isMatch());
    assertEquals(SelectorUsageFlag.MATCH, SelectorUsageFlag.fromString("MATCH"));
    assertEquals(SelectorUsageFlag.NO_MATCH, SelectorUsageFlag.fromString("NO_MATCH"));
    assertThrows(IllegalArgumentException.class, () -> SelectorUsageFlag.fromString("INVALID"));
  }

  @Test
  @DisplayName("Presence enum should have correct values and behavior")
  void testPresence() {
    assertEquals("PRESENT", Presence.PRESENT.getCodeString());
    assertEquals("NOT_PRESENT", Presence.NOT_PRESENT.getCodeString());
    assertTrue(Presence.PRESENT.isPresent());
    assertFalse(Presence.NOT_PRESENT.isPresent());
    assertEquals(Presence.PRESENT, Presence.fromString("PRESENT"));
    assertEquals(Presence.NOT_PRESENT, Presence.fromString("NOT_PRESENT"));
    assertThrows(IllegalArgumentException.class, () -> Presence.fromString("INVALID"));
  }

  @Test
  @DisplayName("SelectorValueNumber enum should have correct values")
  void testSelectorValueNumber() {
    assertEquals("ABSTRACT_PRIOR", SelectorValueNumber.ABSTRACT_PRIOR.getCodeString());
    assertEquals("RELATIVE_TIME", SelectorValueNumber.RELATIVE_TIME.getCodeString());
    assertEquals(
        SelectorValueNumber.ABSTRACT_PRIOR, SelectorValueNumber.fromString("ABSTRACT_PRIOR"));
    assertEquals(
        SelectorValueNumber.RELATIVE_TIME, SelectorValueNumber.fromString("RELATIVE_TIME"));
    assertThrows(IllegalArgumentException.class, () -> SelectorValueNumber.fromString("INVALID"));
  }

  @Test
  @DisplayName("SortingDirection enum should have correct values and sign")
  void testSortingDirection() {
    assertEquals("INCREASING", SortingDirection.INCREASING.getCodeString());
    assertEquals("DECREASING", SortingDirection.DECREASING.getCodeString());
    assertEquals(1, SortingDirection.INCREASING.getSign());
    assertEquals(-1, SortingDirection.DECREASING.getSign());
    assertEquals(SortingDirection.INCREASING, SortingDirection.fromString("INCREASING"));
    assertEquals(SortingDirection.DECREASING, SortingDirection.fromString("DECREASING"));
    assertThrows(IllegalArgumentException.class, () -> SortingDirection.fromString("INVALID"));
  }

  @Test
  @DisplayName("SortingOperation enum should have correct values")
  void testSortingOperation() {
    assertEquals("ALONG_AXIS", SortingOperation.ALONG_AXIS.getCodeString());
    assertEquals("BY_ACQ_TIME", SortingOperation.BY_ACQ_TIME.getCodeString());
    assertEquals(SortingOperation.ALONG_AXIS, SortingOperation.fromString("ALONG_AXIS"));
    assertEquals(SortingOperation.BY_ACQ_TIME, SortingOperation.fromString("BY_ACQ_TIME"));
    assertThrows(IllegalArgumentException.class, () -> SortingOperation.fromString("INVALID"));
  }

  @Test
  @DisplayName("PartialDataDisplayHandling enum should have correct values")
  void testPartialDataDisplayHandling() {
    assertEquals("MAINTAIN_LAYOUT", PartialDataDisplayHandling.MAINTAIN_LAYOUT.getCodeString());
    assertEquals("ADAPT_LAYOUT", PartialDataDisplayHandling.ADAPT_LAYOUT.getCodeString());
    assertEquals(
        PartialDataDisplayHandling.MAINTAIN_LAYOUT,
        PartialDataDisplayHandling.fromString("MAINTAIN_LAYOUT"));
    assertEquals(
        PartialDataDisplayHandling.ADAPT_LAYOUT,
        PartialDataDisplayHandling.fromString("ADAPT_LAYOUT"));
    assertThrows(
        IllegalArgumentException.class, () -> PartialDataDisplayHandling.fromString("INVALID"));
  }

  @Test
  @DisplayName("HangingProtocolLevel enum should have correct values")
  void testHangingProtocolLevel() {
    assertEquals("MANUFACTURER", HangingProtocolLevel.MANUFACTURER.getCodeString());
    assertEquals("SITE", HangingProtocolLevel.SITE.getCodeString());
    assertEquals("SINGLE_USER", HangingProtocolLevel.SINGLE_USER.getCodeString());
    assertEquals("USER_GROUP", HangingProtocolLevel.USER_GROUP.getCodeString());
    assertEquals(
        HangingProtocolLevel.MANUFACTURER, HangingProtocolLevel.fromString("MANUFACTURER"));
    assertEquals(HangingProtocolLevel.SITE, HangingProtocolLevel.fromString("SITE"));
    assertEquals(HangingProtocolLevel.SINGLE_USER, HangingProtocolLevel.fromString("SINGLE_USER"));
    assertEquals(HangingProtocolLevel.USER_GROUP, HangingProtocolLevel.fromString("USER_GROUP"));
    assertThrows(IllegalArgumentException.class, () -> HangingProtocolLevel.fromString("INVALID"));
  }

  @Test
  @DisplayName("ReconstructionType enum should have correct values")
  void testReconstructionType() {
    assertEquals("COLOR", ReconstructionType.COLOR.getCodeString());
    assertEquals("MPR", ReconstructionType.MPR.getCodeString());
    assertEquals("3D_RENDERING", ReconstructionType.RENDERING_3D.getCodeString());
    assertEquals("SLAB", ReconstructionType.SLAB.getCodeString());
    assertEquals(ReconstructionType.COLOR, ReconstructionType.fromString("COLOR"));
    assertEquals(ReconstructionType.MPR, ReconstructionType.fromString("MPR"));
    assertEquals(ReconstructionType.RENDERING_3D, ReconstructionType.fromString("3D_RENDERING"));
    assertEquals(ReconstructionType.SLAB, ReconstructionType.fromString("SLAB"));
    assertThrows(IllegalArgumentException.class, () -> ReconstructionType.fromString("INVALID"));
  }

  @Test
  @DisplayName("ImagePlaneOrientation enum should have correct values")
  void testImagePlaneOrientation() {
    assertEquals("SAGITTAL", ImagePlaneOrientation.SAGITTAL.getCodeString());
    assertEquals("TRANSVERSE", ImagePlaneOrientation.TRANSVERSE.getCodeString());
    assertEquals("CORONAL", ImagePlaneOrientation.CORONAL.getCodeString());
    assertEquals("OBLIQUE", ImagePlaneOrientation.OBLIQUE.getCodeString());
    assertEquals(ImagePlaneOrientation.SAGITTAL, ImagePlaneOrientation.fromString("SAGITTAL"));
    assertEquals(ImagePlaneOrientation.TRANSVERSE, ImagePlaneOrientation.fromString("TRANSVERSE"));
    assertEquals(ImagePlaneOrientation.CORONAL, ImagePlaneOrientation.fromString("CORONAL"));
    assertEquals(ImagePlaneOrientation.OBLIQUE, ImagePlaneOrientation.fromString("OBLIQUE"));
    assertThrows(IllegalArgumentException.class, () -> ImagePlaneOrientation.fromString("INVALID"));
  }

  @Test
  @DisplayName("PresetWindowLevel enum should have correct values")
  void testPresetWindowLevel() {
    assertEquals("LUNG", PresetWindowLevel.LUNG.getCodeString());
    assertEquals("MEDIASTINUM", PresetWindowLevel.MEDIASTINUM.getCodeString());
    assertEquals("ABDO_PELVIS", PresetWindowLevel.ABDO_PELVIS.getCodeString());
    assertEquals("LIVER", PresetWindowLevel.LIVER.getCodeString());
    assertEquals("SOFT_TISSUE", PresetWindowLevel.SOFT_TISSUE.getCodeString());
    assertEquals("BONE", PresetWindowLevel.BONE.getCodeString());
    assertEquals("BRAIN", PresetWindowLevel.BRAIN.getCodeString());
    assertEquals("POST_FOSSA", PresetWindowLevel.POST_FOSSA.getCodeString());
    assertEquals(PresetWindowLevel.LUNG, PresetWindowLevel.fromString("LUNG"));
    assertEquals(PresetWindowLevel.MEDIASTINUM, PresetWindowLevel.fromString("MEDIASTINUM"));
    assertEquals(PresetWindowLevel.ABDO_PELVIS, PresetWindowLevel.fromString("ABDO_PELVIS"));
    assertEquals(PresetWindowLevel.LIVER, PresetWindowLevel.fromString("LIVER"));
    assertEquals(PresetWindowLevel.SOFT_TISSUE, PresetWindowLevel.fromString("SOFT_TISSUE"));
    assertEquals(PresetWindowLevel.BONE, PresetWindowLevel.fromString("BONE"));
    assertEquals(PresetWindowLevel.BRAIN, PresetWindowLevel.fromString("BRAIN"));
    assertEquals(PresetWindowLevel.POST_FOSSA, PresetWindowLevel.fromString("POST_FOSSA"));
    assertThrows(IllegalArgumentException.class, () -> PresetWindowLevel.fromString("INVALID"));
  }

  @Test
  @DisplayName("ColorPalette enum should have correct values")
  void testColorPalette() {
    assertEquals("BLACK_BODY", ColorPalette.BLACK_BODY.getCodeString());
    assertEquals("HOT_IRON", ColorPalette.HOT_IRON.getCodeString());
    assertEquals("DEFAULT", ColorPalette.DEFAULT.getCodeString());
    assertEquals(ColorPalette.BLACK_BODY, ColorPalette.fromString("BLACK_BODY"));
    assertEquals(ColorPalette.HOT_IRON, ColorPalette.fromString("HOT_IRON"));
    assertEquals(ColorPalette.DEFAULT, ColorPalette.fromString("DEFAULT"));
    assertThrows(IllegalArgumentException.class, () -> ColorPalette.fromString("INVALID"));
  }

  @Test
  @DisplayName("ImageBoxLayoutType enum should have correct values")
  void testImageBoxLayoutType() {
    assertEquals("TILED", ImageBoxLayoutType.TILED.getCodeString());
    assertEquals("STACK", ImageBoxLayoutType.STACK.getCodeString());
    assertEquals("CINE", ImageBoxLayoutType.CINE.getCodeString());
    assertEquals("PROCESSED", ImageBoxLayoutType.PROCESSED.getCodeString());
    assertEquals("SINGLE", ImageBoxLayoutType.SINGLE.getCodeString());
    assertEquals(ImageBoxLayoutType.TILED, ImageBoxLayoutType.fromString("TILED"));
    assertEquals(ImageBoxLayoutType.STACK, ImageBoxLayoutType.fromString("STACK"));
    assertEquals(ImageBoxLayoutType.CINE, ImageBoxLayoutType.fromString("CINE"));
    assertEquals(ImageBoxLayoutType.PROCESSED, ImageBoxLayoutType.fromString("PROCESSED"));
    assertEquals(ImageBoxLayoutType.SINGLE, ImageBoxLayoutType.fromString("SINGLE"));
    assertThrows(IllegalArgumentException.class, () -> ImageBoxLayoutType.fromString("INVALID"));
  }

  @Test
  @DisplayName("TileDirection enum should have correct values")
  void testTileDirection() {
    assertEquals("VERTICAL", TileDirection.VERTICAL.getCodeString());
    assertEquals("HORIZONTAL", TileDirection.HORIZONTAL.getCodeString());
    assertEquals(TileDirection.VERTICAL, TileDirection.fromString("VERTICAL"));
    assertEquals(TileDirection.HORIZONTAL, TileDirection.fromString("HORIZONTAL"));
    assertThrows(IllegalArgumentException.class, () -> TileDirection.fromString("INVALID"));
  }

  @Test
  @DisplayName("SynchronizationType enum should have correct values")
  void testSynchronizationType() {
    assertEquals("PAGE", SynchronizationType.PAGE.getCodeString());
    assertEquals("ROW_COLUMN", SynchronizationType.ROW_COLUMN.getCodeString());
    assertEquals("IMAGE", SynchronizationType.IMAGE.getCodeString());
    assertEquals(SynchronizationType.PAGE, SynchronizationType.fromString("PAGE"));
    assertEquals(SynchronizationType.ROW_COLUMN, SynchronizationType.fromString("ROW_COLUMN"));
    assertEquals(SynchronizationType.IMAGE, SynchronizationType.fromString("IMAGE"));
    assertThrows(IllegalArgumentException.class, () -> SynchronizationType.fromString("INVALID"));
  }
}
