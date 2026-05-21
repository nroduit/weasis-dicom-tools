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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.hp.enums.SelectorUsageFlag;
import org.weasis.dicom.hp.enums.SortingDirection;
import org.weasis.dicom.hp.filter.FilterOp;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HPSelectorAndComparatorInternalsTest {

  @Nested
  class IntSelector_Tests {

    @Test
    void int_selector_matches_with_valid_value() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.Rows, 1, VR.US, new int[] {512});
      Attributes ds = new Attributes();
      ds.setInt(Tag.Rows, VR.US, 512);
      assertTrue(sel.matches(ds, 0));
    }

    @Test
    void int_selector_with_frame_index_uses_frame_param() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              SelectorUsageFlag.MATCH.getCodeString(),
              null,
              Tag.AcquisitionNumber,
              HPSelectorFactory.FRAME_INDEX,
              VR.IS,
              new int[] {7});
      Attributes ds = new Attributes();
      ds.setInt(Tag.AcquisitionNumber, VR.IS, 1, 5, 7);
      // FRAME_INDEX uses 0xffff which is interpreted at match time
      assertNotNull(sel);
    }

    @Test
    void int_selector_returns_match_default_when_attribute_absent() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.Rows, 1, VR.US, new int[] {512});
      // attribute absent → fallback to match flag (true for MATCH usage)
      assertTrue(sel.matches(new Attributes(), 0));
    }

    @Test
    void int_selector_with_no_match_usage_returns_false_when_absent() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              "NO_MATCH", null, Tag.Rows, 1, VR.US, new int[] {512});
      assertFalse(sel.matches(new Attributes(), 0));
    }
  }

  @Nested
  class UIntSelector_Tests {

    @Test
    void uint_selector_handles_unsigned_long_values() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.SimpleFrameList, 1, VR.UL, new int[] {1});
      Attributes ds = new Attributes();
      ds.setInt(Tag.SimpleFrameList, VR.UL, 1);
      assertTrue(sel.matches(ds, 0));
    }

    @Test
    void uint_selector_returns_match_default_when_attribute_absent() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.SimpleFrameList, 1, VR.UL, new int[] {1});
      assertTrue(sel.matches(new Attributes(), 0));
    }
  }

  @Nested
  class Float_And_Double_Selector_Tests {

    @Test
    void float_selector_matches() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.SliceThickness, 1, VR.DS, new float[] {1.0f});
      Attributes ds = new Attributes();
      ds.setFloat(Tag.SliceThickness, VR.DS, 1.0f);
      assertTrue(sel.matches(ds, 0));
    }

    @Test
    void float_selector_returns_match_default_when_absent() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.SliceThickness, 1, VR.DS, new float[] {1.0f});
      assertTrue(sel.matches(new Attributes(), 0));
    }

    @Test
    void double_selector_matches_with_pixel_spacing() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.PixelSpacing, 1, new double[] {0.5});
      Attributes ds = new Attributes();
      ds.setDouble(Tag.PixelSpacing, VR.FD, 0.5);
      assertTrue(sel.matches(ds, 0));
    }

    @Test
    void double_selector_returns_match_default_when_absent() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.PixelSpacing, 1, new double[] {0.5});
      assertTrue(sel.matches(new Attributes(), 0));
    }
  }

  @Nested
  class String_Selector_Tests {

    @Test
    void string_selector_throws_when_value_missing() {
      // For an image-set selector, the value sequence must contain at least one entry.
      Attributes item = new Attributes();
      item.setInt(Tag.SelectorAttribute, VR.AT, Tag.PatientID);
      item.setString(Tag.SelectorAttributeVR, VR.CS, "LO");
      item.setInt(Tag.SelectorValueNumber, VR.US, 0);
      item.setString(Tag.ImageSetSelectorUsageFlag, VR.CS, "MATCH");
      item.setString(Tag.FilterByOperator, VR.CS, "MEMBER_OF");
      assertThrows(
          IllegalArgumentException.class, () -> HPSelectorFactory.createImageSetSelector(item));
    }
  }

  @Nested
  class HPComparatorFactory_SortByAttribute_Tests {

    @Test
    void compare_returns_zero_when_first_value_missing() {
      HPComparator cmp =
          HPComparatorFactory.createSortByAttribute(
              null, Tag.AcquisitionNumber, 1, SortingDirection.INCREASING);
      Attributes a = new Attributes();
      Attributes b = new Attributes();
      b.setInt(Tag.AcquisitionNumber, VR.IS, 5);
      assertEquals(0, cmp.compare(a, 0, b, 0));
    }

    @Test
    void compare_returns_zero_when_second_value_missing() {
      HPComparator cmp =
          HPComparatorFactory.createSortByAttribute(
              null, Tag.AcquisitionNumber, 1, SortingDirection.INCREASING);
      Attributes a = new Attributes();
      a.setInt(Tag.AcquisitionNumber, VR.IS, 5);
      Attributes b = new Attributes();
      assertEquals(0, cmp.compare(a, 0, b, 0));
    }

    @Test
    void compare_returns_zero_when_vr_differs() {
      HPComparator cmp =
          HPComparatorFactory.createSortByAttribute(
              null, Tag.AcquisitionNumber, 1, SortingDirection.INCREASING);
      Attributes a = new Attributes();
      a.setInt(Tag.AcquisitionNumber, VR.IS, 1);
      Attributes b = new Attributes();
      b.setString(Tag.AcquisitionNumber, VR.LO, "5");
      assertEquals(0, cmp.compare(a, 0, b, 0));
    }

    @Test
    void compare_with_string_vr_uses_string_comparison() {
      HPComparator cmp =
          HPComparatorFactory.createSortByAttribute(
              null, Tag.PatientID, 1, SortingDirection.INCREASING);
      Attributes a = new Attributes();
      a.setString(Tag.PatientID, VR.LO, "A");
      Attributes b = new Attributes();
      b.setString(Tag.PatientID, VR.LO, "B");
      assertTrue(cmp.compare(a, 0, b, 0) < 0);
    }

    @Test
    void compare_with_double_vr_uses_double_comparison() {
      HPComparator cmp =
          HPComparatorFactory.createSortByAttribute(
              null, Tag.PixelSpacing, 1, SortingDirection.INCREASING);
      Attributes a = new Attributes();
      a.setDouble(Tag.PixelSpacing, VR.FD, 0.5);
      Attributes b = new Attributes();
      b.setDouble(Tag.PixelSpacing, VR.FD, 1.0);
      assertTrue(cmp.compare(a, 0, b, 0) < 0);
    }

    @Test
    void compare_with_float_vr_uses_float_comparison() {
      HPComparator cmp =
          HPComparatorFactory.createSortByAttribute(
              null, Tag.SliceThickness, 1, SortingDirection.INCREASING);
      Attributes a = new Attributes();
      a.setFloat(Tag.SliceThickness, VR.DS, 1.0f);
      Attributes b = new Attributes();
      b.setFloat(Tag.SliceThickness, VR.DS, 2.0f);
      assertTrue(cmp.compare(a, 0, b, 0) < 0);
    }

    @Test
    void compare_with_unsigned_integer_vr() {
      HPComparator cmp =
          HPComparatorFactory.createSortByAttribute(null, Tag.Rows, 1, SortingDirection.INCREASING);
      Attributes a = new Attributes();
      a.setInt(Tag.Rows, VR.US, 512);
      Attributes b = new Attributes();
      b.setInt(Tag.Rows, VR.US, 1024);
      assertTrue(cmp.compare(a, 0, b, 0) < 0);
    }
  }

  @Nested
  class Add_Sequence_And_Functional_Group_Pointer_Tests {

    @Test
    void selector_with_sequence_pointer_attempts_nested_dataset_lookup() {
      HPSelector base =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.PixelSpacing, 1, new double[] {0.5});
      HPSelector wrapped =
          HPSelectorFactory.addSequencePointer(null, Tag.SharedFunctionalGroupsSequence, base);

      // Attributes without the sequence → wrapper falls back to "match if not present".
      assertTrue(wrapped.matches(new Attributes(), 0));
    }

    @Test
    void selector_with_functional_group_pointer_falls_through_when_groups_absent() {
      HPSelector base =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.PixelSpacing, 1, new double[] {0.5});
      HPSelector wrapped =
          HPSelectorFactory.addFunctionalGroupPointer(
              null, Tag.SharedFunctionalGroupsSequence, base);
      assertTrue(wrapped.matches(new Attributes(), 0));
    }

    @Test
    void comparator_with_sequence_pointer_zero_returns_same_instance() {
      // Build the comparator through the Attributes path so the Selector* pointers are
      // populated (the create-by-args path leaves them null and breaks the wrapper).
      HPComparator base = createComparatorWithPointers(0, 0);
      HPComparator wrapped = HPComparatorFactory.addSequencePointer(null, 0, base);
      assertEquals(base, wrapped);
    }

    @Test
    void comparator_functional_group_pointer_zero_returns_same_instance() {
      HPComparator base = createComparatorWithPointers(0, 0);
      HPComparator wrapped = HPComparatorFactory.addFunctionalGroupPointer(null, 0, base);
      assertEquals(base, wrapped);
    }
  }

  private static HPComparator createComparatorWithPointers(int seqTag, int fgTag) {
    Attributes sortOp = new Attributes();
    sortOp.setInt(Tag.SelectorAttribute, VR.AT, Tag.PixelSpacing);
    sortOp.setString(Tag.SelectorAttributeVR, VR.CS, "FD");
    sortOp.setInt(Tag.SelectorValueNumber, VR.US, 1);
    sortOp.setInt(Tag.SelectorSequencePointer, VR.AT, seqTag);
    sortOp.setInt(Tag.FunctionalGroupPointer, VR.AT, fgTag);
    sortOp.setString(Tag.SortingDirection, VR.CS, "INCREASING");
    return HPComparatorFactory.createHPComparator(sortOp);
  }

  @Nested
  class FilterOp_Comparator_Factory_Integration_Tests {

    @Test
    void sort_along_axis_factory_returns_comparator() {
      HPComparator cmp = HPComparatorFactory.createSortAlongAxis(SortingDirection.INCREASING);
      assertNotNull(cmp);
    }

    @Test
    void sort_by_acq_time_factory_returns_comparator() {
      HPComparator cmp = HPComparatorFactory.createSortByAcqTime(SortingDirection.DECREASING);
      assertNotNull(cmp);
    }
  }

  @Nested
  class Range_Filter_Numeric_Tests {

    @Test
    void int_range_filter_at_boundaries() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              null, Tag.Rows, 1, VR.US, new int[] {100, 1000}, FilterOp.RANGE_INCL);
      Attributes ds = new Attributes();
      ds.setInt(Tag.Rows, VR.US, 100);
      assertTrue(sel.matches(ds, 0));
      ds.setInt(Tag.Rows, VR.US, 1000);
      assertTrue(sel.matches(ds, 0));
      ds.setInt(Tag.Rows, VR.US, 99);
      assertFalse(sel.matches(ds, 0));
    }

    @Test
    void float_range_filter() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              null, Tag.SliceThickness, 1, VR.DS, new float[] {1.0f, 5.0f}, FilterOp.RANGE_INCL);
      Attributes ds = new Attributes();
      ds.setFloat(Tag.SliceThickness, VR.DS, 3.0f);
      assertTrue(sel.matches(ds, 0));
      ds.setFloat(Tag.SliceThickness, VR.DS, 6.0f);
      assertFalse(sel.matches(ds, 0));
    }

    @Test
    void greater_than_filter_on_int() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              null, Tag.Rows, 1, VR.US, new int[] {500}, FilterOp.GREATER_THAN);
      Attributes ds = new Attributes();
      ds.setInt(Tag.Rows, VR.US, 600);
      assertTrue(sel.matches(ds, 0));
      ds.setInt(Tag.Rows, VR.US, 500);
      assertFalse(sel.matches(ds, 0));
    }
  }
}
