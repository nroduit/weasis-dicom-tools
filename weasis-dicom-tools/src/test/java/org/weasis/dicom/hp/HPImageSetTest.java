/*
 * Copyright (c) 1150 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.SAXReader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

public class HPImageSetTest {
  static final Path IN_DIR =
      FileSystems.getDefault().getPath("target/test-classes/org/weasis/dicom/hp");

  private static Attributes attributes;

  @BeforeAll
  static void setUp() throws IOException, ParserConfigurationException, SAXException {
    File file = new File(IN_DIR.toString(), "NeurosurgeryPlan.xml");
    attributes = SAXReader.parse(new FileInputStream(file));
  }

  @AfterAll
  static void tearDown() {
    attributes = null;
  }

  @Test
  void testContains() {
    HangingProtocol hp = new HangingProtocol(new Attributes(attributes));
    List<HPImageSet> list = hp.getImageSets();
    assertEquals(3, list.size());
    HPImageSet is1 = list.get(0);
    HPImageSet is2 = list.get(1);
    HPImageSet is3 = list.get(2);

    Attributes o = new Attributes();
    assertFalse(is1.contains(o, 0));
    assertFalse(is2.contains(o, 0));
    assertFalse(is3.contains(o, 0));
    o.setString(Tag.BodyPartExamined, VR.CS, "HEAD");
    assertFalse(is1.contains(o, 0));
    assertFalse(is2.contains(o, 0));
    assertFalse(is3.contains(o, 0));
    o.setString(Tag.Modality, VR.CS, "CT");
    assertFalse(is1.contains(o, 0));
    assertTrue(is2.contains(o, 0));
    assertTrue(is3.contains(o, 0));
    o.setString(Tag.Modality, VR.CS, "MR");
    assertTrue(is1.contains(o, 0));
    assertFalse(is2.contains(o, 0));
    assertFalse(is3.contains(o, 0));
  }

  @Test
  void testGetImageSetSelectorSequence() {
    HangingProtocol hp = new HangingProtocol(new Attributes(attributes));
    List<HPImageSet> list = hp.getImageSets();
    assertEquals(3, list.size());
    HPImageSet is1 = list.get(0);
    HPImageSet is2 = list.get(1);
    HPImageSet is3 = list.get(2);

    Sequence is1selSeq = is1.getImageSetSelectorSequence();
    assertNull(is1selSeq);
    Sequence is2selSeq = is2.getImageSetSelectorSequence();
    assertNull(is2selSeq);
    Sequence is3selSeq = is3.getImageSetSelectorSequence();
    assertNull(is3selSeq);
  }

  @Test
  void testGetTimeBasedImageSetsSequence() {
    HangingProtocol hp = new HangingProtocol(new Attributes(attributes));
    List<HPImageSet> list = hp.getImageSets();
    assertEquals(3, list.size());
    HPImageSet is1 = list.get(0);
    HPImageSet is2 = list.get(1);

    Sequence tbis1Seq = is1.getTimeBasedImageSetsSequence();
    assertNull(tbis1Seq);

    HPTimeBasedImageSet tbis2 = new HPTimeBasedImageSet();
    tbis2.setImageSetLabel("TBIS12");
    tbis2.setRelativeTime(new RelativeTime(7, 21, RelativeTimeUnits.DAYS));
    is2.addTimeBasedImageSet(tbis2);
    Sequence tbis2lSeq = is2.getTimeBasedImageSetsSequence();
    assertNotNull(tbis2lSeq);
    assertEquals(1, tbis2lSeq.size());
  }
}
