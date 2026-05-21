/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * Exercises the Attributes-based constructors of HPSelectorFactory's inner selectors via the public
 * createImageSetSelector entry point. Each VR path goes through a distinct subclass (Str,
 * IntSelector, UIntSelector, Flt, Dbl, CodeValueSelector, AttributePresenceSelector).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class HPSelectorAttributesCtorTest {

  @Test
  void createImageSetSelector_with_CS_VR_returns_string_selector() {
    Attributes item = baseSelectorItem(VR.CS);
    item.setString(Tag.SelectorCSValue, VR.CS, "CT");
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);

    Attributes dcm = new Attributes();
    dcm.setString(Tag.SelectorAttribute, VR.CS, "CT");
    // Verify the selector is wired up. Use the actual SelectorAttribute tag from item.
    int tag = item.getInt(Tag.SelectorAttribute, 0);
    Attributes ds = new Attributes();
    ds.setString(tag, VR.CS, "CT");
    assertTrue(sel.matches(ds, 0));
  }

  @Test
  void createImageSetSelector_with_LO_VR_uses_LO_value_path() {
    Attributes item = baseSelectorItem(VR.LO);
    item.setString(Tag.SelectorLOValue, VR.LO, "TestValue");
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    Attributes ds = new Attributes();
    ds.setString(item.getInt(Tag.SelectorAttribute, 0), VR.LO, "TestValue");
    assertTrue(sel.matches(ds, 0));
  }

  @Test
  void createImageSetSelector_with_US_VR_returns_int_selector() {
    Attributes item = baseSelectorItem(VR.US);
    item.setInt(Tag.SelectorUSValue, VR.US, 512);
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    Attributes ds = new Attributes();
    ds.setInt(item.getInt(Tag.SelectorAttribute, 0), VR.US, 512);
    assertTrue(sel.matches(ds, 0));
  }

  @Test
  void createImageSetSelector_with_UL_VR_returns_uint_selector() {
    Attributes item = baseSelectorItem(VR.UL);
    item.setInt(Tag.SelectorULValue, VR.UL, 1);
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    Attributes ds = new Attributes();
    ds.setInt(item.getInt(Tag.SelectorAttribute, 0), VR.UL, 1);
    assertTrue(sel.matches(ds, 0));
  }

  @Test
  void createImageSetSelector_with_DS_VR_returns_float_selector() {
    Attributes item = baseSelectorItem(VR.DS);
    item.setFloat(Tag.SelectorDSValue, VR.DS, 1.5f);
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    Attributes ds = new Attributes();
    ds.setFloat(item.getInt(Tag.SelectorAttribute, 0), VR.DS, 1.5f);
    assertTrue(sel.matches(ds, 0));
  }

  @Test
  void createImageSetSelector_with_FD_VR_returns_double_selector() {
    Attributes item = baseSelectorItem(VR.FD);
    item.setDouble(Tag.SelectorFDValue, VR.FD, 0.75);
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    Attributes ds = new Attributes();
    ds.setDouble(item.getInt(Tag.SelectorAttribute, 0), VR.FD, 0.75);
    assertTrue(sel.matches(ds, 0));
  }

  @Test
  void createImageSetSelector_with_IS_VR_returns_int_selector() {
    Attributes item = baseSelectorItem(VR.IS);
    item.setInt(Tag.SelectorISValue, VR.IS, 42);
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    Attributes ds = new Attributes();
    ds.setInt(item.getInt(Tag.SelectorAttribute, 0), VR.IS, 42);
    assertTrue(sel.matches(ds, 0));
  }

  @Test
  void createImageSetSelector_with_SL_VR_returns_int_selector() {
    Attributes item = baseSelectorItem(VR.SL);
    item.setInt(Tag.SelectorSLValue, VR.SL, -1);
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    Attributes ds = new Attributes();
    ds.setInt(item.getInt(Tag.SelectorAttribute, 0), VR.SL, -1);
    assertTrue(sel.matches(ds, 0));
  }

  @Test
  void createImageSetSelector_with_SS_VR_returns_int_selector() {
    Attributes item = baseSelectorItem(VR.SS);
    item.setInt(Tag.SelectorSSValue, VR.SS, -5);
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    Attributes ds = new Attributes();
    ds.setInt(item.getInt(Tag.SelectorAttribute, 0), VR.SS, -5);
    assertTrue(sel.matches(ds, 0));
  }

  @Test
  void createImageSetSelector_with_FL_VR_returns_float_selector() {
    Attributes item = baseSelectorItem(VR.FL);
    item.setFloat(Tag.SelectorFLValue, VR.FL, 2.0f);
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    Attributes ds = new Attributes();
    ds.setFloat(item.getInt(Tag.SelectorAttribute, 0), VR.FL, 2.0f);
    assertTrue(sel.matches(ds, 0));
  }

  @Test
  void createImageSetSelector_with_AT_VR_returns_int_selector() {
    Attributes item = baseSelectorItem(VR.AT);
    item.setInt(Tag.SelectorATValue, VR.AT, Tag.PatientID);
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    assertNotNull(sel);
  }

  @Test
  void createImageSetSelector_with_SH_VR_returns_string_selector() {
    Attributes item = baseSelectorItem(VR.SH);
    item.setString(Tag.SelectorSHValue, VR.SH, "ABC");
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    assertNotNull(sel);
  }

  @Test
  void createImageSetSelector_with_ST_VR_returns_string_selector() {
    Attributes item = baseSelectorItem(VR.ST);
    item.setString(Tag.SelectorSTValue, VR.ST, "TEXT");
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    assertNotNull(sel);
  }

  @Test
  void createImageSetSelector_with_LT_VR_returns_string_selector() {
    Attributes item = baseSelectorItem(VR.LT);
    item.setString(Tag.SelectorLTValue, VR.LT, "longtext");
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    assertNotNull(sel);
  }

  @Test
  void createImageSetSelector_with_PN_VR_returns_string_selector() {
    Attributes item = baseSelectorItem(VR.PN);
    item.setString(Tag.SelectorPNValue, VR.PN, "DOE^JOHN");
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    assertNotNull(sel);
  }

  @Test
  void createImageSetSelector_with_UT_VR_returns_string_selector() {
    Attributes item = baseSelectorItem(VR.UT);
    item.setString(Tag.SelectorUTValue, VR.UT, "unlimited");
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    assertNotNull(sel);
  }

  @Test
  void createImageSetSelector_with_SQ_VR_returns_code_value_selector() {
    Attributes item = baseSelectorItem(VR.SQ);
    Sequence codeSeq = item.newSequence(Tag.SelectorCodeSequenceValue, 1);
    Attributes codeItem = new Attributes();
    codeItem.setString(Tag.CodeValue, VR.SH, "123");
    codeItem.setString(Tag.CodingSchemeDesignator, VR.SH, "SCT");
    codeSeq.add(codeItem);
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    assertNotNull(sel);
  }

  @Test
  void createImageSetSelector_with_missing_int_values_throws() {
    Attributes item = baseSelectorItem(VR.US);
    // No SelectorUSValue set.
    assertThrows(
        IllegalArgumentException.class, () -> HPSelectorFactory.createImageSetSelector(item));
  }

  @Test
  void createImageSetSelector_returns_false_when_no_match_usage_and_attribute_absent() {
    Attributes item = baseSelectorItem(VR.US);
    item.setString(Tag.ImageSetSelectorUsageFlag, VR.CS, "NO_MATCH");
    item.setInt(Tag.SelectorUSValue, VR.US, 512);
    HPSelector sel = HPSelectorFactory.createImageSetSelector(item);
    assertFalse(sel.matches(new Attributes(), 0));
  }

  /** Build the minimal Attributes for an Image Set Selector item with the given VR. */
  private static Attributes baseSelectorItem(VR vr) {
    Attributes item = new Attributes();
    item.setString(Tag.ImageSetSelectorUsageFlag, VR.CS, "MATCH");
    item.setInt(Tag.SelectorAttribute, VR.AT, Tag.Modality);
    item.setString(Tag.SelectorAttributeVR, VR.CS, vr.toString());
    item.setInt(Tag.SelectorValueNumber, VR.US, 1);
    item.setString(Tag.FilterByOperator, VR.CS, "MEMBER_OF");
    return item;
  }
}
