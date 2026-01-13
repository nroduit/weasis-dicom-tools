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
import java.nio.file.FileSystems;
import java.nio.file.Path;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.hp.filter.FilterOp;

public class HPSelectorTest {
  static final Path IN_DIR =
      FileSystems.getDefault().getPath("target/test-classes/org/weasis/dicom/hp");

  private static Attributes attributes;

  @BeforeAll
  static void setUp() throws Exception {
    File file = new File(IN_DIR.toString(), "NeurosurgeryPlan.dcm");
    try (DicomInputStream in = new DicomInputStream(file)) {
      attributes = in.readDataset();
    }
  }

  @AfterAll
  static void tearDown() {
    attributes = null;
  }

  @Test
  public void testImageTypeDisplaySetFilter() {
    HPSelector sel =
        HPSelectorFactory.createAttributeValueSelector(
            null, Tag.ImageType, 3, VR.CS, new String[] {"AXIAL"}, FilterOp.MEMBER_OF);
    assertTrue(sel.matches(attributes, 0));
  }

  @Test
  public void testPrivateSHDisplaySetFilter() {
    HPSelector sel =
        HPSelectorFactory.createAttributeValueSelector(
            "GEMS_PARM_01", 0x00430027, 0, VR.SH, new String[] {"/1.0:1"}, FilterOp.MEMBER_OF);
    assertTrue(sel.matches(attributes, 0));
  }

  @Test
  public void testPrivateSSDisplaySetFilter() {
    HPSelector sel =
        HPSelectorFactory.createAttributeValueSelector(
            "GEMS_PARM_01", 0x00430012, 1, VR.SS, new int[] {14}, FilterOp.MEMBER_OF);
    assertTrue(sel.matches(attributes, 0));
  }

  @Test
  public void testPrivateUSDisplaySetFilter() {
    HPSelector sel =
        HPSelectorFactory.createAttributeValueSelector(
            "GEMS_PARM_01", 0x00430026, 2, VR.US, new int[] {1}, FilterOp.MEMBER_OF);
    assertTrue(sel.matches(attributes, 0));
  }

  @Test
  public void testPrivateSLDisplaySetFilter() {
    HPSelector sel =
        HPSelectorFactory.createAttributeValueSelector(
            "GEMS_PARM_01", 0x0043001A, 0, VR.SL, new int[] {7}, FilterOp.MEMBER_OF);
    assertTrue(sel.matches(attributes, 0));
  }

  @Test
  public void testPrivateDSDisplaySetFilter() {
    HPSelector sel =
        HPSelectorFactory.createAttributeValueSelector(
            "GEMS_PARM_01", 0x00430018, 2, VR.DS, new float[] {1f}, FilterOp.GREATER_THAN);
    assertTrue(sel.matches(attributes, 0));
  }

  @Test
  public void testPrivateFLDisplaySetFilter() {
    HPSelector sel =
        HPSelectorFactory.createAttributeValueSelector(
            "GEMS_PARM_01", 0x00430040, 0, VR.FL, new float[] {178f, 179f}, FilterOp.RANGE_INCL);
    assertTrue(sel.matches(attributes, 0));
  }
}
