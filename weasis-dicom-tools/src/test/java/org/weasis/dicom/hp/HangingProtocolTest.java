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

import java.util.Arrays;
import java.util.List;
import org.dcm4che3.data.Sequence;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.macro.Code;

public class HangingProtocolTest {

  @Test
  public void testGetHPSelectorSpi() {
    assertNotNull(HangingProtocol.getHPSelectorSpi("IMAGE_PLANE"));
  }

  @Test
  public void testGetHPComparatorSpi() {
    assertNotNull(HangingProtocol.getHPComparatorSpi("ALONG_AXIS"));
    assertNotNull(HangingProtocol.getHPComparatorSpi("BY_ACQ_TIME"));
  }

  @Test
  public void testGetSupportedHPSelectorCategories() {
    String[] ss = HangingProtocol.getSupportedHPSelectorCategories();
    List<String> list = Arrays.asList(ss);
    assertTrue(list.contains("IMAGE_PLANE"));
  }

  @Test
  public void testGetSupportedHPComparatorCategories() {
    String[] ss = HangingProtocol.getSupportedHPComparatorCategories();
    List<String> list = Arrays.asList(ss);
    assertTrue(list.contains("ALONG_AXIS"));
    assertTrue(list.contains("BY_ACQ_TIME"));
  }

  @Test
  public void testAddImageSet() {
    HangingProtocol hp = new HangingProtocol();
    HPImageSet is1 = hp.addNewImageSet(null);
    HPImageSet is2 = hp.addNewImageSet(is1);
    assertEquals(is1.getImageSetSelectors(), is2.getImageSetSelectors());
    assertEquals(is1.getAttributes().getParent(), is2.getAttributes().getParent());
    List<HPImageSet> imageSets = hp.getImageSets();
    assertEquals(2, imageSets.size());

    HPTimeBasedImageSet tbis1 = new HPTimeBasedImageSet();
    tbis1.setImageSetLabel("TBIS1");
    Code code = new Code();
    code.setCodeMeaning("TBIS");
    code.setCodeValue("TBIS1");
    tbis1.setAbstractPriorCode(code);
    tbis1.setRelativeTime(new RelativeTime(3, 7, RelativeTimeUnits.DAYS));
    is2.addTimeBasedImageSet(tbis1);
    Sequence isseq = is2.getTimeBasedImageSetsSequence();
    assertNotNull(isseq);
    assertEquals(1, isseq.size());

    HPTimeBasedImageSet tbis2 = new HPTimeBasedImageSet();
    tbis2.setImageSetLabel("TBIS12");
    tbis2.setRelativeTime(new RelativeTime(7, 21, RelativeTimeUnits.DAYS));
    is2.addTimeBasedImageSet(tbis2);
    isseq = is2.getTimeBasedImageSetsSequence();
    assertNotNull(isseq);
    assertEquals(2, isseq.size());
  }

  @Test
  public void testCopy() {
    HangingProtocol src = new HangingProtocol();
    new HangingProtocol(src);
  }

  @Test
  public void testMammographyHangingProtocol() {
    // Create a new hanging protocol for mammography
    HangingProtocol hp = new HangingProtocol();

    // Set basic metadata
    hp.setHangingProtocolName("Mammography 4-View Protocol");
    hp.setHangingProtocolDescription(
        "Standard mammography screening protocol with bilateral CC and MLO views");
    hp.setHangingProtocolLevel("SERIES");
    hp.setHangingProtocolCreator("Test Creator");
    hp.setHangingProtocolCreationDateTime(new java.util.Date());
    hp.setNumberOfScreens(1);
    hp.setNumberOfPriorsReferenced(0);

    // Create image sets for different mammography views
    // Image Set 1: Right CC (Cranio-Caudal)
    HPImageSet rightCC = hp.addNewImageSet(null);
    rightCC.setImageSetNumber(1);

    // Image Set 2: Right MLO (Medio-Lateral Oblique)
    HPImageSet rightMLO = hp.addNewImageSet(null);
    rightMLO.setImageSetNumber(2);

    // Image Set 3: Left CC
    HPImageSet leftCC = hp.addNewImageSet(null);
    leftCC.setImageSetNumber(3);

    // Image Set 4: Left MLO
    HPImageSet leftMLO = hp.addNewImageSet(null);
    leftMLO.setImageSetNumber(4);

    // Create display sets for a typical 2x2 mammography layout
    // Display Set 1: Right CC - Top Right
    HPDisplaySet dsRightCC = new HPDisplaySet();
    dsRightCC.setImageSet(rightCC);
    dsRightCC.setDisplaySetLabel("R CC");
    dsRightCC.setDisplaySetPresentationGroup(1);
    HPImageBox imageBox1 = new HPImageBox();
    imageBox1.setImageBoxLayoutType("TILED");
    imageBox1.setImageBoxTileHorizontalDimension(1);
    imageBox1.setImageBoxTileVerticalDimension(1);
    dsRightCC.addImageBox(imageBox1);
    hp.addDisplaySet(dsRightCC);

    // Display Set 2: Right MLO - Bottom Right
    HPDisplaySet dsRightMLO = new HPDisplaySet();
    dsRightMLO.setImageSet(rightMLO);
    dsRightMLO.setDisplaySetLabel("R MLO");
    dsRightMLO.setDisplaySetPresentationGroup(1);
    HPImageBox imageBox2 = new HPImageBox();
    imageBox2.setImageBoxLayoutType("TILED");
    imageBox2.setImageBoxTileHorizontalDimension(1);
    imageBox2.setImageBoxTileVerticalDimension(1);
    dsRightMLO.addImageBox(imageBox2);
    hp.addDisplaySet(dsRightMLO);

    // Display Set 3: Left CC - Top Left
    HPDisplaySet dsLeftCC = new HPDisplaySet();
    dsLeftCC.setImageSet(leftCC);
    dsLeftCC.setDisplaySetLabel("L CC");
    dsLeftCC.setDisplaySetPresentationGroup(1);
    HPImageBox imageBox3 = new HPImageBox();
    imageBox3.setImageBoxLayoutType("TILED");
    imageBox3.setImageBoxTileHorizontalDimension(1);
    imageBox3.setImageBoxTileVerticalDimension(1);
    dsLeftCC.addImageBox(imageBox3);
    hp.addDisplaySet(dsLeftCC);

    // Display Set 4: Left MLO - Bottom Left
    HPDisplaySet dsLeftMLO = new HPDisplaySet();
    dsLeftMLO.setImageSet(leftMLO);
    dsLeftMLO.setDisplaySetLabel("L MLO");
    dsLeftMLO.setDisplaySetPresentationGroup(1);
    HPImageBox imageBox4 = new HPImageBox();
    imageBox4.setImageBoxLayoutType("TILED");
    imageBox4.setImageBoxTileHorizontalDimension(1);
    imageBox4.setImageBoxTileVerticalDimension(1);
    dsLeftMLO.addImageBox(imageBox4);
    hp.addDisplaySet(dsLeftMLO);

    // Verify the hanging protocol structure
    assertEquals("Mammography 4-View Protocol", hp.getHangingProtocolName());
    assertEquals(1, hp.getNumberOfScreens());
    assertEquals(4, hp.getImageSets().size());
    assertEquals(4, hp.getDisplaySets().size());
    assertEquals(1, hp.getNumberOfPresentationGroups());

    // Verify image sets
    List<HPImageSet> imageSets = hp.getImageSets();
    assertEquals(1, imageSets.get(0).getImageSetNumber());
    assertEquals(2, imageSets.get(1).getImageSetNumber());
    assertEquals(3, imageSets.get(2).getImageSetNumber());
    assertEquals(4, imageSets.get(3).getImageSetNumber());

    // Verify display sets
    List<HPDisplaySet> displaySets = hp.getDisplaySets();
    assertEquals("R CC", displaySets.get(0).getDisplaySetLabel());
    assertEquals("R MLO", displaySets.get(1).getDisplaySetLabel());
    assertEquals("L CC", displaySets.get(2).getDisplaySetLabel());
    assertEquals("L MLO", displaySets.get(3).getDisplaySetLabel());

    // Verify all display sets are in the same presentation group
    for (HPDisplaySet ds : displaySets) {
      assertEquals(1, ds.getDisplaySetPresentationGroup());
    }

    // Verify display sets are correctly associated with image sets
    assertEquals(rightCC, displaySets.get(0).getImageSet());
    assertEquals(rightMLO, displaySets.get(1).getImageSet());
    assertEquals(leftCC, displaySets.get(2).getImageSet());
    assertEquals(leftMLO, displaySets.get(3).getImageSet());

    // Verify each display set has one image box
    for (HPDisplaySet ds : displaySets) {
      assertEquals(1, ds.getImageBoxes().size());
      assertEquals("TILED", ds.getImageBoxes().get(0).getImageBoxLayoutType());
    }
  }
}
