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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.geom.ImageOrientation.Plan;
import org.weasis.dicom.hp.enums.SelectorUsageFlag;
import org.weasis.dicom.hp.enums.SortingDirection;
import org.weasis.dicom.hp.filter.FilterOp;
import org.weasis.dicom.hp.plugins.AlongAxisComparator;
import org.weasis.dicom.hp.plugins.ImagePlaneSelector;
import org.weasis.dicom.macro.Code;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HPFactoryAndGroupsTest {

  @Nested
  class HPSelectorFactory_Tests {

    @Test
    void create_string_attribute_value_selector_matches_when_dcm_value_is_in_params() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              SelectorUsageFlag.MATCH.getCodeString(),
              null,
              Tag.PatientID,
              0,
              VR.LO,
              new String[] {"P1", "P2"});
      Attributes ds = new Attributes();
      ds.setString(Tag.PatientID, VR.LO, "P1");
      assertTrue(sel.matches(ds, 0));

      ds.setString(Tag.PatientID, VR.LO, "P3");
      assertFalse(sel.matches(ds, 0));
    }

    @Test
    void create_int_attribute_value_selector_matches() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              SelectorUsageFlag.MATCH.getCodeString(),
              null,
              Tag.Rows,
              0,
              VR.US,
              new int[] {512, 1024});
      Attributes ds = new Attributes();
      ds.setInt(Tag.Rows, VR.US, 512);
      assertTrue(sel.matches(ds, 0));

      ds.setInt(Tag.Rows, VR.US, 256);
      assertFalse(sel.matches(ds, 0));
    }

    @Test
    void create_float_attribute_value_selector_matches() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              SelectorUsageFlag.MATCH.getCodeString(),
              null,
              Tag.SliceThickness,
              0,
              VR.DS,
              new float[] {1.0f, 2.0f});
      Attributes ds = new Attributes();
      ds.setFloat(Tag.SliceThickness, VR.DS, 1.0f);
      assertTrue(sel.matches(ds, 0));
    }

    @Test
    void create_double_attribute_value_selector_matches() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              SelectorUsageFlag.MATCH.getCodeString(),
              null,
              Tag.PixelSpacing,
              0,
              new double[] {0.5, 1.0});
      Attributes ds = new Attributes();
      ds.setDouble(Tag.PixelSpacing, VR.FD, 0.5);
      assertTrue(sel.matches(ds, 0));
    }

    @Test
    void create_attribute_value_selector_with_range_filter() {
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              null, Tag.Rows, 1, VR.US, new int[] {100, 1024}, FilterOp.RANGE_INCL);
      Attributes ds = new Attributes();
      ds.setInt(Tag.Rows, VR.US, 500);
      assertTrue(sel.matches(ds, 0));
      ds.setInt(Tag.Rows, VR.US, 50);
      assertFalse(sel.matches(ds, 0));
    }

    @Test
    void create_attribute_presence_selector() {
      HPSelector sel =
          HPSelectorFactory.createAttributePresenceSelector(null, Tag.PatientName, "PRESENT");
      Attributes ds = new Attributes();
      ds.setString(Tag.PatientName, VR.PN, "DOE^JOHN");
      assertTrue(sel.matches(ds, 0));
      assertFalse(sel.matches(new Attributes(), 0));
    }

    @Test
    void create_attribute_presence_selector_for_not_present() {
      HPSelector sel =
          HPSelectorFactory.createAttributePresenceSelector(null, Tag.PatientName, "NOT_PRESENT");
      assertTrue(sel.matches(new Attributes(), 0));
      Attributes ds = new Attributes();
      ds.setString(Tag.PatientName, VR.PN, "DOE^JOHN");
      assertFalse(sel.matches(ds, 0));
    }

    @Test
    void create_image_plane_selector() {
      HPSelector sel = HPSelectorFactory.createImagePlaneSelector(new Plan[] {Plan.TRANSVERSE});
      assertNotNull(sel);
      assertTrue(sel instanceof ImagePlaneSelector);
    }

    @Test
    void create_image_set_selector_validates_usage_flag() {
      Attributes item = new Attributes();
      assertThrows(
          IllegalArgumentException.class, () -> HPSelectorFactory.createImageSetSelector(item));
    }

    @Test
    void create_display_set_filter_with_filter_by_category_delegates_to_spi() {
      Attributes filterOp = new Attributes();
      filterOp.setString(Tag.FilterByCategory, VR.CS, "IMAGE_PLANE");
      filterOp.setString(Tag.SelectorAttributeVR, VR.CS, "CS");
      filterOp.setString(Tag.SelectorCSValue, VR.CS, "TRANSVERSE");
      HPSelector sel = HPSelectorFactory.createDisplaySetFilter(filterOp);
      assertNotNull(sel);
      assertTrue(sel instanceof ImagePlaneSelector);
    }

    @Test
    void create_display_set_filter_with_unsupported_category_throws() {
      Attributes filterOp = new Attributes();
      filterOp.setString(Tag.FilterByCategory, VR.CS, "UNKNOWN_CAT");
      assertThrows(
          IllegalArgumentException.class, () -> HPSelectorFactory.createDisplaySetFilter(filterOp));
    }

    @Test
    void create_code_value_selector_matches_on_code() {
      Code allowed = code("123", "SCT", null);
      HPSelector sel =
          HPSelectorFactory.createCodeValueSelector(
              SelectorUsageFlag.MATCH.getCodeString(),
              null,
              Tag.SelectorCodeSequenceValue,
              0,
              new Code[] {allowed});
      assertNotNull(sel);
      assertNotNull(sel.getAttributes());
    }

    @Test
    void add_sequence_pointer_returns_same_selector_when_tag_zero() {
      HPSelector inner =
          HPSelectorFactory.createAttributeValueSelector(
              null, null, Tag.Rows, 0, VR.US, new int[] {512}, FilterOp.MEMBER_OF);
      HPSelector decorated = HPSelectorFactory.addSequencePointer(null, 0, inner);
      assertSame(inner, decorated);
    }

    @Test
    void add_functional_group_pointer_returns_same_when_tag_zero() {
      HPSelector inner =
          HPSelectorFactory.createAttributeValueSelector(
              null, null, Tag.Rows, 0, VR.US, new int[] {512}, FilterOp.MEMBER_OF);
      HPSelector decorated = HPSelectorFactory.addFunctionalGroupPointer(null, 0, inner);
      assertSame(inner, decorated);
    }

    @Test
    void add_sequence_pointer_wraps_selector_and_persists_tag() {
      HPSelector inner =
          HPSelectorFactory.createAttributeValueSelector(
              null, null, Tag.Rows, 0, VR.US, new int[] {512}, FilterOp.MEMBER_OF);
      HPSelector decorated =
          HPSelectorFactory.addSequencePointer(null, Tag.SharedFunctionalGroupsSequence, inner);
      assertNotNull(decorated);
      assertEquals(
          Tag.SharedFunctionalGroupsSequence, decorated.getSelectorSequencePointer().intValue());
    }

    @Test
    void getVR_returns_OB_for_unknown_vr_string() {
      assertEquals(VR.OB, HPSelectorFactory.getVR("XXXX"));
      assertEquals(VR.CS, HPSelectorFactory.getVR("CS"));
    }
  }

  @Nested
  class HPComparatorFactory_Tests {

    @Test
    void create_comparator_with_sort_by_category_delegates_to_spi() {
      Attributes sortOp = new Attributes();
      sortOp.setString(Tag.SortByCategory, VR.CS, "ALONG_AXIS");
      sortOp.setString(Tag.SortingDirection, VR.CS, "INCREASING");
      HPComparator cmp = HPComparatorFactory.createHPComparator(sortOp);
      assertNotNull(cmp);
      assertTrue(cmp instanceof AlongAxisComparator);
    }

    @Test
    void create_comparator_with_unknown_category_throws() {
      Attributes sortOp = new Attributes();
      sortOp.setString(Tag.SortByCategory, VR.CS, "UNKNOWN_CAT");
      assertThrows(
          IllegalArgumentException.class, () -> HPComparatorFactory.createHPComparator(sortOp));
    }

    @Test
    void create_comparator_without_category_and_with_sort_attribute_uses_attribute_comparator() {
      // Both pointers must be present (even at zero) — HPComparatorFactory.addSequencePointer
      // unboxes the value without null-checking.
      Attributes sortOp = new Attributes();
      sortOp.setInt(Tag.SelectorAttribute, VR.AT, Tag.AcquisitionNumber);
      sortOp.setString(Tag.SelectorAttributeVR, VR.CS, "IS");
      sortOp.setInt(Tag.SelectorValueNumber, VR.US, 1);
      sortOp.setInt(Tag.SelectorSequencePointer, VR.AT, 0);
      sortOp.setInt(Tag.FunctionalGroupPointer, VR.AT, 0);
      sortOp.setString(Tag.SortingDirection, VR.CS, "INCREASING");
      HPComparator cmp = HPComparatorFactory.createHPComparator(sortOp);
      assertNotNull(cmp);
    }

    @Test
    void create_comparator_without_sorting_direction_throws() {
      Attributes sortOp = new Attributes();
      sortOp.setInt(Tag.SelectorAttribute, VR.AT, Tag.AcquisitionNumber);
      sortOp.setString(Tag.SelectorAttributeVR, VR.CS, "IS");
      sortOp.setInt(Tag.SelectorValueNumber, VR.US, 1);
      sortOp.setInt(Tag.SelectorSequencePointer, VR.AT, 0);
      sortOp.setInt(Tag.FunctionalGroupPointer, VR.AT, 0);
      assertThrows(
          IllegalArgumentException.class, () -> HPComparatorFactory.createHPComparator(sortOp));
    }

    @Test
    void create_sort_by_attribute_comparator_via_factory_method() {
      HPComparator cmp =
          HPComparatorFactory.createSortByAttribute(
              null, Tag.AcquisitionNumber, 1, SortingDirection.INCREASING);
      assertNotNull(cmp);

      Attributes a = new Attributes();
      a.setInt(Tag.AcquisitionNumber, VR.IS, 1);
      Attributes b = new Attributes();
      b.setInt(Tag.AcquisitionNumber, VR.IS, 5);

      assertTrue(cmp.compare(a, 0, b, 0) < 0);
      assertTrue(cmp.compare(b, 0, a, 0) > 0);
    }

    @Test
    void create_sort_by_attribute_comparator_with_decreasing_direction() {
      HPComparator cmp =
          HPComparatorFactory.createSortByAttribute(
              null, Tag.AcquisitionNumber, 1, SortingDirection.DECREASING);
      Attributes a = new Attributes();
      a.setInt(Tag.AcquisitionNumber, VR.IS, 1);
      Attributes b = new Attributes();
      b.setInt(Tag.AcquisitionNumber, VR.IS, 5);
      assertTrue(cmp.compare(a, 0, b, 0) > 0);
    }
  }

  @Nested
  class HPNavigationGroup_Tests {

    @Test
    void empty_constructor_starts_with_no_references() {
      HPNavigationGroup g = new HPNavigationGroup();
      assertTrue(g.getReferenceDisplaySets().isEmpty());
      assertFalse(g.isValid());
    }

    @Test
    void add_two_display_sets_yields_valid_group_without_navigation_set() {
      HPNavigationGroup g = new HPNavigationGroup();
      g.addReferenceDisplaySet(displaySetWithNumber(1));
      g.addReferenceDisplaySet(displaySetWithNumber(2));
      assertEquals(2, g.getReferenceDisplaySets().size());
      assertTrue(g.isValid());
    }

    @Test
    void single_reference_with_navigation_set_is_valid() {
      HPNavigationGroup g = new HPNavigationGroup();
      HPDisplaySet nav = displaySetWithNumber(1);
      HPDisplaySet ref = displaySetWithNumber(2);
      g.setNavigationDisplaySet(nav);
      g.addReferenceDisplaySet(ref);
      assertTrue(g.isValid());
      assertEquals(nav, g.getNavigationDisplaySet());
    }

    @Test
    void add_reference_without_display_set_number_throws() {
      HPNavigationGroup g = new HPNavigationGroup();
      // A fresh HPDisplaySet has no DisplaySetNumber, so unboxing fails inside add.
      assertThrows(NullPointerException.class, () -> g.addReferenceDisplaySet(new HPDisplaySet()));
    }

    @Test
    void remove_reference_returns_false_when_absent() {
      HPNavigationGroup g = new HPNavigationGroup();
      assertFalse(g.removeReferenceDisplaySet(displaySetWithNumber(99)));
    }

    @Test
    void remove_reference_returns_true_after_add() {
      HPNavigationGroup g = new HPNavigationGroup();
      HPDisplaySet ds = displaySetWithNumber(1);
      g.addReferenceDisplaySet(ds);
      assertTrue(g.removeReferenceDisplaySet(ds));
    }

    @Test
    void remove_reference_null_throws() {
      HPNavigationGroup g = new HPNavigationGroup();
      assertThrows(NullPointerException.class, () -> g.removeReferenceDisplaySet(null));
    }

    @Test
    void constructor_from_attributes_validates_singleton_without_navigation() {
      Attributes a = new Attributes();
      a.setInt(Tag.ReferenceDisplaySets, VR.US, 1);
      assertThrows(
          IllegalArgumentException.class,
          () -> new HPNavigationGroup(a, List.of(new HPDisplaySet())));
    }

    @Test
    void constructor_from_attributes_requires_reference_display_sets() {
      Attributes a = new Attributes();
      assertThrows(IllegalArgumentException.class, () -> new HPNavigationGroup(a, List.of()));
    }

    @Test
    void update_attributes_handles_null_navigation_display_set() {
      HPNavigationGroup g = new HPNavigationGroup();
      g.setNavigationDisplaySet(null);
      g.updateAttributes();
      // nothing thrown, attributes refreshed
      assertFalse(g.getAttributes().containsValue(Tag.NavigationDisplaySet));
    }
  }

  @Nested
  class HPScrollingGroup_Tests {

    @Test
    void empty_constructor_starts_empty() {
      HPScrollingGroup g = new HPScrollingGroup();
      assertTrue(g.getDisplaySets().isEmpty());
      assertFalse(g.isValid());
    }

    @Test
    void two_displaysets_make_a_valid_group() {
      HPScrollingGroup g = new HPScrollingGroup();
      g.addDisplaySet(displaySetWithNumber(1));
      g.addDisplaySet(displaySetWithNumber(2));
      assertTrue(g.isValid());
      assertEquals(2, g.getDisplaySets().size());
    }

    @Test
    void add_without_display_set_number_throws() {
      HPScrollingGroup g = new HPScrollingGroup();
      assertThrows(NullPointerException.class, () -> g.addDisplaySet(new HPDisplaySet()));
    }

    @Test
    void remove_returns_false_when_absent() {
      HPScrollingGroup g = new HPScrollingGroup();
      assertFalse(g.removeDisplaySet(displaySetWithNumber(1)));
    }

    @Test
    void remove_null_throws() {
      HPScrollingGroup g = new HPScrollingGroup();
      assertThrows(NullPointerException.class, () -> g.removeDisplaySet(null));
    }

    @Test
    void remove_returns_true_after_add() {
      HPScrollingGroup g = new HPScrollingGroup();
      HPDisplaySet ds = displaySetWithNumber(1);
      g.addDisplaySet(ds);
      assertTrue(g.removeDisplaySet(ds));
    }

    @Test
    void constructor_from_attributes_requires_at_least_two_display_sets() {
      Attributes a = new Attributes();
      a.setInt(Tag.DisplaySetScrollingGroup, VR.US, 1);
      assertThrows(
          IllegalArgumentException.class,
          () -> new HPScrollingGroup(a, List.of(new HPDisplaySet())));
    }

    @Test
    void constructor_from_attributes_requires_display_set_scrolling_group() {
      assertThrows(
          IllegalArgumentException.class, () -> new HPScrollingGroup(new Attributes(), List.of()));
    }
  }

  @Nested
  class AbstractPriorValue_Tests {

    @Test
    void constructor_stores_start_and_end() {
      AbstractPriorValue v = new AbstractPriorValue(1, 5);
      assertEquals(1, v.getStart());
      assertEquals(5, v.getEnd());
    }
  }

  @Nested
  class RelativeTime_Tests {

    @Test
    void default_constructor_uses_zero_with_seconds_units() {
      RelativeTime t = new RelativeTime();
      assertEquals(0, t.getStart());
      assertEquals(0, t.getEnd());
      assertTrue(t.isCurrentTime());
    }

    @Test
    void start_and_end_getters_return_values() {
      RelativeTime t = new RelativeTime(3, 7, org.weasis.dicom.hp.enums.RelativeTimeUnits.DAYS);
      assertEquals(3, t.getStart());
      assertEquals(7, t.getEnd());
      assertFalse(t.isCurrentTime());
      assertEquals(org.weasis.dicom.hp.enums.RelativeTimeUnits.DAYS, t.getUnits());
    }

    @Test
    void getStartDate_and_getEndDate_return_non_null() {
      RelativeTime t = new RelativeTime(1, 2, org.weasis.dicom.hp.enums.RelativeTimeUnits.HOURS);
      assertNotNull(t.getStartDate());
      assertNotNull(t.getEndDate());
    }
  }

  // --- helpers --------------------------------------------------------------

  private static HPDisplaySet displaySetWithNumber(int n) {
    HPDisplaySet ds = new HPDisplaySet();
    ds.setDisplaySetNumber(n);
    return ds;
  }

  private static Code code(String value, String designator, String version) {
    Code c = new Code();
    c.getAttributes().setString(Tag.CodeValue, VR.SH, value);
    c.getAttributes().setString(Tag.CodingSchemeDesignator, VR.SH, designator);
    if (version != null) {
      c.getAttributes().setString(Tag.CodingSchemeVersion, VR.SH, version);
    }
    return c;
  }
}
