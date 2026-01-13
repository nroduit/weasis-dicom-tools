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
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.SAXReader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

public class HPDisplaySetTest {
  static final Path IN_DIR =
      FileSystems.getDefault().getPath("target/test-classes/org/weasis/dicom/hp");

  private static final String CORONAL =
      "1.000000\\0.000000\\0.000000\\0.000000\\0.000000\\-1.000000";
  private static final String SAGITAL =
      "0.000000\\-1.000000\\0.000000\\0.000000\\0.000000\\-1.000000";
  private static final String TRANSVERSE =
      "1.000000\\0.000000\\0.000000\\0.000000\\1.000000\\0.000000";

  private final Attributes CT_CORONAL =
      initializeAttributes(
          "ORIGINAL\\PRIMARY\\LOCALIZER",
          "CT",
          "HEAD",
          "-248.187592\\0.000000\\30.000000",
          CORONAL);
  private final Attributes CT_SAGITAL =
      initializeAttributes(
          "ORIGINAL\\PRIMARY\\LOCALIZER", "CT", "HEAD", "0.000000\\248.187592\\30.000000", SAGITAL);
  private final Attributes CT_TRANSVERSE1 =
      initializeAttributes(
          "ORIGINAL\\PRIMARY\\AXIAL",
          "CT",
          "HEAD",
          "-158.135818\\-179.035812\\-59.200001",
          TRANSVERSE);
  private final Attributes CT_TRANSVERSE2 =
      initializeAttributes(
          "ORIGINAL\\PRIMARY\\AXIAL",
          "CT",
          "HEAD",
          "-158.135818\\-179.035812\\-29.200001",
          TRANSVERSE);
  private final Attributes MR_TRANSVERSE1 =
      initializeAttributes(
          "ORIGINAL\\PRIMARY", "MR", "HEAD", "-120.000000\\-116.699997\\-19.799999", TRANSVERSE);
  private final Attributes MR_TRANSVERSE2 =
      initializeAttributes(
          "ORIGINAL\\PRIMARY", "MR", "HEAD", "-120.000000\\-116.699997\\-5.800000", TRANSVERSE);

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

  private static Attributes initializeAttributes(
      String type, String modality, String bodyPart, String position, String orientation) {
    Attributes dcm = new Attributes();
    dcm.setString(Tag.ImageType, VR.CS, type);
    dcm.setString(Tag.Modality, VR.CS, modality);
    dcm.setString(Tag.BodyPartExamined, VR.CS, bodyPart);
    dcm.setString(Tag.ImagePositionPatient, VR.DS, position);
    dcm.setString(Tag.ImageOrientationPatient, VR.DS, orientation);
    return dcm;
  }

  @Test
  public final void testNeurosurgeryPlan() {
    HangingProtocol neurosurgeryPlan = new HangingProtocol(new Attributes(attributes));

    assertEquals(4, neurosurgeryPlan.getNumberOfPresentationGroups());
    List<HPDisplaySet> ctOnlyDisplay = neurosurgeryPlan.getDisplaySetsOfPresentationGroup(1);
    assertEquals(5, ctOnlyDisplay.size());
    List<HPDisplaySet> mrOnlyDisplay = neurosurgeryPlan.getDisplaySetsOfPresentationGroup(2);
    assertEquals(5, mrOnlyDisplay.size());
    List<HPDisplaySet> mrctCombined = neurosurgeryPlan.getDisplaySetsOfPresentationGroup(3);
    assertEquals(6, mrctCombined.size());
    List<HPDisplaySet> ctNewctOldCombined = neurosurgeryPlan.getDisplaySetsOfPresentationGroup(4);
    assertEquals(6, ctNewctOldCombined.size());

    HPDisplaySet ds5 = ctOnlyDisplay.get(4);
    HPImageSet is2 = ds5.getImageSet();
    assertTrue(is2.contains(CT_CORONAL, 0));
    assertTrue(is2.contains(CT_SAGITAL, 0));
    assertTrue(is2.contains(CT_TRANSVERSE1, 0));
    assertTrue(is2.contains(CT_TRANSVERSE2, 0));
    assertFalse(is2.contains(MR_TRANSVERSE1, 0));
    assertFalse(is2.contains(MR_TRANSVERSE2, 0));
    assertFalse(ds5.contains(CT_CORONAL, 0));
    assertFalse(ds5.contains(CT_SAGITAL, 0));
    assertTrue(ds5.contains(CT_TRANSVERSE1, 0));
    assertTrue(ds5.contains(CT_TRANSVERSE2, 0));
    assertTrue(ds5.compare(CT_TRANSVERSE1, 1, CT_TRANSVERSE2, 1) < 0);

    HPDisplaySet ds10 = mrOnlyDisplay.get(4);
    HPImageSet is1 = ds10.getImageSet();
    assertFalse(is1.contains(CT_CORONAL, 0));
    assertFalse(is1.contains(CT_SAGITAL, 0));
    assertFalse(is1.contains(CT_TRANSVERSE1, 0));
    assertFalse(is1.contains(CT_TRANSVERSE2, 0));
    assertTrue(is1.contains(MR_TRANSVERSE1, 0));
    assertTrue(is1.contains(MR_TRANSVERSE2, 0));
    assertTrue(ds10.contains(MR_TRANSVERSE1, 0));
    assertTrue(ds10.contains(MR_TRANSVERSE2, 0));
    assertTrue(ds10.compare(MR_TRANSVERSE1, 1, MR_TRANSVERSE2, 1) < 0);

    List<HPSelector> filterOps = ds10.getFilterOperations();
    assertEquals(1, filterOps.size());
    HPSelector filterOp = filterOps.get(0);
    assertEquals("IMAGE_PLANE", filterOp.getFilterByCategory());

    List<HPComparator> sortingOps = ds10.getSortingOperations();
    assertEquals(1, sortingOps.size());
    HPComparator sortingOp = sortingOps.get(0);
    assertEquals("ALONG_AXIS", sortingOp.getSortByCategory());
  }
}
