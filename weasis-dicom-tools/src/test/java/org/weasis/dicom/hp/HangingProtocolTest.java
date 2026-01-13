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
}
