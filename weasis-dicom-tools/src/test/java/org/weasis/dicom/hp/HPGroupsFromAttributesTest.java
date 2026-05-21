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
import org.weasis.dicom.hp.enums.SortingDirection;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HPGroupsFromAttributesTest {

  @Nested
  class HPNavigationGroup_FromAttributes_Tests {

    @Test
    void constructor_with_two_references_and_navigation_set_is_valid() {
      List<HPDisplaySet> displaySets =
          List.of(displaySetWithNumber(1), displaySetWithNumber(2), displaySetWithNumber(3));
      Attributes attrs = new Attributes();
      attrs.setInt(Tag.NavigationDisplaySet, VR.US, 1);
      attrs.setInt(Tag.ReferenceDisplaySets, VR.US, 2, 3);

      HPNavigationGroup g = new HPNavigationGroup(attrs, displaySets);
      assertEquals(2, g.getReferenceDisplaySets().size());
      assertEquals(displaySets.get(0), g.getNavigationDisplaySet());
      assertTrue(g.isValid());
    }

    @Test
    void constructor_with_navigation_set_out_of_range_throws() {
      Attributes attrs = new Attributes();
      attrs.setInt(Tag.NavigationDisplaySet, VR.US, 99);
      attrs.setInt(Tag.ReferenceDisplaySets, VR.US, 1, 2);

      assertThrows(
          IllegalArgumentException.class,
          () ->
              new HPNavigationGroup(
                  attrs, List.of(displaySetWithNumber(1), displaySetWithNumber(2))));
    }

    @Test
    void constructor_with_reference_set_out_of_range_throws() {
      Attributes attrs = new Attributes();
      attrs.setInt(Tag.NavigationDisplaySet, VR.US, 1);
      attrs.setInt(Tag.ReferenceDisplaySets, VR.US, 1, 99);

      assertThrows(
          IllegalArgumentException.class,
          () -> new HPNavigationGroup(attrs, List.of(displaySetWithNumber(1))));
    }
  }

  @Nested
  class HPScrollingGroup_FromAttributes_Tests {

    @Test
    void constructor_with_valid_group_populates_display_sets() {
      Attributes attrs = new Attributes();
      attrs.setInt(Tag.DisplaySetScrollingGroup, VR.US, 1, 2);

      HPScrollingGroup g =
          new HPScrollingGroup(attrs, List.of(displaySetWithNumber(1), displaySetWithNumber(2)));
      assertEquals(2, g.getDisplaySets().size());
      assertTrue(g.isValid());
    }

    @Test
    void constructor_with_out_of_range_display_set_throws() {
      Attributes attrs = new Attributes();
      attrs.setInt(Tag.DisplaySetScrollingGroup, VR.US, 1, 99);

      assertThrows(
          IllegalArgumentException.class,
          () -> new HPScrollingGroup(attrs, List.of(displaySetWithNumber(1))));
    }
  }

  @Nested
  class HPComparatorFactory_Decorators_Tests {

    @Test
    void seq_decorator_returns_zero_when_first_dataset_missing_nested_value() {
      HPComparator wrapped = sortByPixelSpacingViaSequencePointer();
      Attributes a = new Attributes(); // no SharedFunctionalGroupsSequence
      Attributes b = withNestedPixelSpacing(0.5);
      assertEquals(0, wrapped.compare(a, 0, b, 0));
    }

    @Test
    void seq_decorator_returns_zero_when_second_dataset_missing_nested_value() {
      HPComparator wrapped = sortByPixelSpacingViaSequencePointer();
      Attributes a = withNestedPixelSpacing(0.5);
      Attributes b = new Attributes();
      assertEquals(0, wrapped.compare(a, 0, b, 0));
    }

    @Test
    void seq_decorator_compares_nested_values_when_both_present() {
      HPComparator wrapped = sortByPixelSpacingViaSequencePointer();
      Attributes a = withNestedPixelSpacing(0.5);
      Attributes b = withNestedPixelSpacing(1.0);
      // Direction depends on sign, but result should be non-zero.
      assertTrue(wrapped.compare(a, 1, b, 1) != 0);
    }

    @Test
    void fctgrp_decorator_returns_zero_when_neither_dataset_has_groups() {
      HPComparator wrapped = sortByPixelSpacingViaFunctionalGroupPointer();
      assertEquals(0, wrapped.compare(new Attributes(), 1, new Attributes(), 1));
    }

    private static HPComparator sortByPixelSpacingViaSequencePointer() {
      Attributes sortOp = new Attributes();
      sortOp.setInt(Tag.SelectorAttribute, VR.AT, Tag.PixelSpacing);
      sortOp.setString(Tag.SelectorAttributeVR, VR.CS, "FD");
      sortOp.setInt(Tag.SelectorValueNumber, VR.US, 1);
      sortOp.setInt(Tag.SelectorSequencePointer, VR.AT, Tag.SharedFunctionalGroupsSequence);
      sortOp.setInt(Tag.FunctionalGroupPointer, VR.AT, 0);
      sortOp.setString(Tag.SortingDirection, VR.CS, SortingDirection.INCREASING.getCodeString());
      return HPComparatorFactory.createHPComparator(sortOp);
    }

    private static HPComparator sortByPixelSpacingViaFunctionalGroupPointer() {
      Attributes sortOp = new Attributes();
      sortOp.setInt(Tag.SelectorAttribute, VR.AT, Tag.PixelSpacing);
      sortOp.setString(Tag.SelectorAttributeVR, VR.CS, "FD");
      sortOp.setInt(Tag.SelectorValueNumber, VR.US, 1);
      sortOp.setInt(Tag.SelectorSequencePointer, VR.AT, 0);
      sortOp.setInt(Tag.FunctionalGroupPointer, VR.AT, Tag.PixelMeasuresSequence);
      sortOp.setString(Tag.SortingDirection, VR.CS, SortingDirection.INCREASING.getCodeString());
      return HPComparatorFactory.createHPComparator(sortOp);
    }

    /**
     * Build an Attributes graph whose SharedFunctionalGroupsSequence's first item contains
     * PixelSpacing with the given x value.
     */
    private static Attributes withNestedPixelSpacing(double value) {
      Attributes top = new Attributes();
      org.dcm4che3.data.Sequence shared = top.newSequence(Tag.SharedFunctionalGroupsSequence, 1);
      Attributes item = new Attributes();
      item.setDouble(Tag.PixelSpacing, VR.FD, value);
      shared.add(item);
      return top;
    }
  }

  private static HPDisplaySet displaySetWithNumber(int n) {
    HPDisplaySet ds = new HPDisplaySet();
    ds.setDisplaySetNumber(n);
    return ds;
  }
}
