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
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HangingProtocolInitTest {

  @Nested
  class InitFromAttributes_Tests {

    @Test
    void init_with_empty_attributes_yields_empty_collections() {
      HangingProtocol hp = new HangingProtocol(new Attributes());
      assertTrue(hp.getImageSets().isEmpty());
      assertTrue(hp.getDisplaySets().isEmpty());
      assertTrue(hp.getNominalScreenDefinitions().isEmpty());
      assertTrue(hp.getHangingProtocolDefinitions().isEmpty());
      assertTrue(hp.getScrollingGroups().isEmpty());
      assertTrue(hp.getNavigationGroups().isEmpty());
    }

    @Test
    void init_with_definition_sequence_populates_definitions() {
      Attributes attrs = new Attributes();
      Sequence defSeq = attrs.newSequence(Tag.HangingProtocolDefinitionSequence, 1);
      Attributes defItem = new Attributes();
      defItem.setString(Tag.Modality, VR.CS, "CT");
      defSeq.add(defItem);

      HangingProtocol hp = new HangingProtocol(attrs);
      assertEquals(1, hp.getHangingProtocolDefinitions().size());
      assertEquals("CT", hp.getHangingProtocolDefinitions().get(0).getModality());
    }

    @Test
    void init_with_screen_definition_sequence_populates_screens() {
      Attributes attrs = new Attributes();
      Sequence screenSeq = attrs.newSequence(Tag.NominalScreenDefinitionSequence, 1);
      Attributes screenItem = new Attributes();
      screenItem.setInt(Tag.NumberOfVerticalPixels, VR.US, 1080);
      screenItem.setInt(Tag.NumberOfHorizontalPixels, VR.US, 1920);
      screenSeq.add(screenItem);

      HangingProtocol hp = new HangingProtocol(attrs);
      assertEquals(1, hp.getNominalScreenDefinitions().size());
      assertEquals(1080, hp.getNominalScreenDefinitions().get(0).getNumberOfVerticalPixels());
    }

    @Test
    void init_with_image_set_sequence_populates_image_sets() {
      Attributes attrs = buildAttributesWithImageSet();
      HangingProtocol hp = new HangingProtocol(attrs);
      assertEquals(1, hp.getImageSets().size());
    }

    @Test
    void init_with_image_set_missing_image_set_selector_sequence_throws() {
      Attributes attrs = new Attributes();
      Sequence imgSets = attrs.newSequence(Tag.ImageSetsSequence, 1);
      imgSets.add(new Attributes());
      assertThrows(IllegalArgumentException.class, () -> new HangingProtocol(attrs));
    }

    @Test
    void init_with_image_set_missing_time_based_sequence_throws() {
      Attributes attrs = new Attributes();
      Sequence imgSets = attrs.newSequence(Tag.ImageSetsSequence, 1);
      Attributes isItem = new Attributes();
      Sequence selectorSeq = isItem.newSequence(Tag.ImageSetSelectorSequence, 1);
      Attributes selectorItem = new Attributes();
      selectorItem.setString(Tag.ImageSetSelectorUsageFlag, VR.CS, "MATCH");
      selectorItem.setInt(Tag.SelectorAttribute, VR.AT, Tag.Modality);
      selectorItem.setString(Tag.SelectorAttributeVR, VR.CS, "CS");
      selectorItem.setString(Tag.SelectorCSValue, VR.CS, "CT");
      selectorSeq.add(selectorItem);
      imgSets.add(isItem);
      assertThrows(IllegalArgumentException.class, () -> new HangingProtocol(attrs));
    }

    @Test
    void init_with_image_set_wrong_image_set_number_throws() {
      Attributes attrs = buildAttributesWithImageSet();
      // Corrupt the image set number to break ordering invariant.
      Sequence imgSets = attrs.getSequence(Tag.ImageSetsSequence);
      Sequence tbis = imgSets.get(0).getSequence(Tag.TimeBasedImageSetsSequence);
      tbis.get(0).setInt(Tag.ImageSetNumber, VR.US, 99);
      assertThrows(IllegalArgumentException.class, () -> new HangingProtocol(attrs));
    }

    @Test
    void init_with_display_set_sequence_populates_display_sets() {
      Attributes attrs = buildAttributesWithImageSet();
      Sequence dsSeq = attrs.newSequence(Tag.DisplaySetsSequence, 1);
      Attributes dsItem = buildDisplaySetAttributes(1, 1, 1);
      dsSeq.add(dsItem);

      HangingProtocol hp = new HangingProtocol(attrs);
      assertEquals(1, hp.getDisplaySets().size());
      assertEquals(1, hp.getDisplaySets().get(0).getDisplaySetNumber());
    }

    @Test
    void init_with_display_set_wrong_number_throws() {
      Attributes attrs = buildAttributesWithImageSet();
      Sequence dsSeq = attrs.newSequence(Tag.DisplaySetsSequence, 1);
      // Wrong DisplaySetNumber.
      Attributes dsItem = buildDisplaySetAttributes(99, 1, 1);
      dsSeq.add(dsItem);
      assertThrows(IllegalArgumentException.class, () -> new HangingProtocol(attrs));
    }

    @Test
    void init_with_display_set_missing_presentation_group_throws() {
      Attributes attrs = buildAttributesWithImageSet();
      Sequence dsSeq = attrs.newSequence(Tag.DisplaySetsSequence, 1);
      Attributes dsItem = buildDisplaySetAttributes(1, 0, 1);
      dsSeq.add(dsItem);
      assertThrows(IllegalArgumentException.class, () -> new HangingProtocol(attrs));
    }

    @Test
    void init_with_display_set_referring_invalid_image_set_throws() {
      Attributes attrs = buildAttributesWithImageSet();
      Sequence dsSeq = attrs.newSequence(Tag.DisplaySetsSequence, 1);
      // ImageSetNumber=99 doesn't exist in the parsed image sets.
      Attributes dsItem = buildDisplaySetAttributes(1, 1, 99);
      dsSeq.add(dsItem);
      assertThrows(IllegalArgumentException.class, () -> new HangingProtocol(attrs));
    }

    @Test
    void init_with_scrolling_sequence_populates_groups() {
      Attributes attrs = buildAttributesWithImageSet();
      Sequence dsSeq = attrs.newSequence(Tag.DisplaySetsSequence, 1);
      dsSeq.add(buildDisplaySetAttributes(1, 1, 1));
      dsSeq.add(buildDisplaySetAttributes(2, 1, 1));

      Sequence scrollSeq = attrs.newSequence(Tag.SynchronizedScrollingSequence, 1);
      Attributes scrollItem = new Attributes();
      scrollItem.setInt(Tag.DisplaySetScrollingGroup, VR.US, 1, 2);
      scrollSeq.add(scrollItem);

      HangingProtocol hp = new HangingProtocol(attrs);
      assertEquals(1, hp.getScrollingGroups().size());
    }

    @Test
    void init_with_navigation_sequence_populates_groups() {
      Attributes attrs = buildAttributesWithImageSet();
      Sequence dsSeq = attrs.newSequence(Tag.DisplaySetsSequence, 1);
      dsSeq.add(buildDisplaySetAttributes(1, 1, 1));
      dsSeq.add(buildDisplaySetAttributes(2, 1, 1));

      Sequence navSeq = attrs.newSequence(Tag.NavigationIndicatorSequence, 1);
      Attributes navItem = new Attributes();
      navItem.setInt(Tag.ReferenceDisplaySets, VR.US, 1, 2);
      navSeq.add(navItem);

      HangingProtocol hp = new HangingProtocol(attrs);
      assertEquals(1, hp.getNavigationGroups().size());
    }
  }

  @Nested
  class WriteToDicomFile_Tests {

    @Test
    void write_to_null_path_throws() {
      HangingProtocol hp = new HangingProtocol();
      assertThrows(NullPointerException.class, () -> hp.writeToDicomFile(null));
    }
  }

  @Nested
  class HPImageSet_With_Selectors_Tests {

    @Test
    void contains_returns_true_when_all_selectors_match() {
      HangingProtocol hp = new HangingProtocol();
      HPImageSet is = hp.addNewImageSet(null);
      is.addImageSetSelector(
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.Modality, 0, VR.CS, new String[] {"CT"}));
      Attributes ds = new Attributes();
      ds.setString(Tag.Modality, VR.CS, "CT");
      assertTrue(is.contains(ds, 0));
    }

    @Test
    void contains_returns_false_when_any_selector_misses() {
      HangingProtocol hp = new HangingProtocol();
      HPImageSet is = hp.addNewImageSet(null);
      is.addImageSetSelector(
          HPSelectorFactory.createAttributeValueSelector(
              "NO_MATCH", null, Tag.Modality, 0, VR.CS, new String[] {"CT"}));
      Attributes ds = new Attributes();
      ds.setString(Tag.Modality, VR.CS, "MR");
      assertFalse(is.contains(ds, 0));
    }

    @Test
    void shared_selectors_constructor_propagates_selectors() {
      HangingProtocol hp = new HangingProtocol();
      HPImageSet is1 = hp.addNewImageSet(null);
      is1.addImageSetSelector(
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.Modality, 0, VR.CS, new String[] {"CT"}));
      HPImageSet is2 = hp.addNewImageSet(is1);
      assertEquals(is1.getImageSetSelectors().size(), is2.getImageSetSelectors().size());
    }
  }

  @Nested
  class HPDisplaySet_Filter_Sorting_Tests {

    @Test
    void add_and_remove_filter_operation() {
      HPDisplaySet ds = new HPDisplaySet();
      ds.addImageBox(new HPImageBox());
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.Modality, 0, VR.CS, new String[] {"CT"});
      ds.addFilterOperation(sel);
      assertEquals(1, ds.getFilterOperations().size());
      assertTrue(ds.removeFilterOperation(sel));
      assertEquals(0, ds.getFilterOperations().size());
    }

    @Test
    void remove_filter_operation_returns_false_when_absent() {
      HPDisplaySet ds = new HPDisplaySet();
      HPSelector sel =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.Modality, 0, VR.CS, new String[] {"CT"});
      assertFalse(ds.removeFilterOperation(sel));
    }

    @Test
    void remove_all_filter_operations_clears() {
      HPDisplaySet ds = new HPDisplaySet();
      ds.addFilterOperation(
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.Modality, 0, VR.CS, new String[] {"CT"}));
      ds.removeAllFilterOperations();
      assertEquals(0, ds.getFilterOperations().size());
    }

    @Test
    void add_and_remove_sorting_operation() {
      HPDisplaySet ds = new HPDisplaySet();
      HPComparator cmp =
          HPComparatorFactory.createSortByAttribute(
              null,
              Tag.AcquisitionNumber,
              1,
              org.weasis.dicom.hp.enums.SortingDirection.INCREASING);
      ds.addSortingOperation(cmp);
      assertEquals(1, ds.getSortingOperations().size());
      assertTrue(ds.removeSortingOperation(cmp));
    }

    @Test
    void remove_all_sorting_operations_clears() {
      HPDisplaySet ds = new HPDisplaySet();
      HPComparator cmp =
          HPComparatorFactory.createSortByAttribute(
              null,
              Tag.AcquisitionNumber,
              1,
              org.weasis.dicom.hp.enums.SortingDirection.INCREASING);
      ds.addSortingOperation(cmp);
      ds.removeAllSortingOperations();
      assertEquals(0, ds.getSortingOperations().size());
    }

    @Test
    void contains_runs_each_filter_in_order() {
      HPDisplaySet ds = new HPDisplaySet();
      ds.addFilterOperation(
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.Modality, 0, VR.CS, new String[] {"CT"}));
      Attributes a = new Attributes();
      a.setString(Tag.Modality, VR.CS, "CT");
      assertTrue(ds.contains(a, 0));

      a.setString(Tag.Modality, VR.CS, "MR");
      assertFalse(ds.contains(a, 0));
    }

    @Test
    void compare_returns_zero_when_no_comparators() {
      HPDisplaySet ds = new HPDisplaySet();
      assertEquals(0, ds.compare(new Attributes(), 0, new Attributes(), 0));
    }

    @Test
    void compare_dispatches_to_configured_comparator() {
      HPDisplaySet ds = new HPDisplaySet();
      ds.addSortingOperation(
          HPComparatorFactory.createSortByAttribute(
              null,
              Tag.AcquisitionNumber,
              1,
              org.weasis.dicom.hp.enums.SortingDirection.INCREASING));
      Attributes a = new Attributes();
      a.setInt(Tag.AcquisitionNumber, VR.IS, 1);
      Attributes b = new Attributes();
      b.setInt(Tag.AcquisitionNumber, VR.IS, 5);
      assertTrue(ds.compare(a, 0, b, 0) < 0);
    }
  }

  @Nested
  class HPImageBox_Tests {

    @Test
    void tile_layout_with_multiple_boxes_requires_TILED_type() {
      Attributes item = new Attributes();
      item.setString(Tag.ImageBoxLayoutType, VR.CS, "TILED");
      HPImageBox b = new HPImageBox(item, 4);
      assertNotNull(b);
    }

    @Test
    void multi_box_constructor_rejects_non_tiled_layout() {
      Attributes item = new Attributes();
      item.setString(Tag.ImageBoxLayoutType, VR.CS, "STACK");
      assertThrows(IllegalArgumentException.class, () -> new HPImageBox(item, 4));
    }
  }

  // --- helpers --------------------------------------------------------------

  /** Build the minimal Attributes graph for a HangingProtocol with one ImageSet. */
  private static Attributes buildAttributesWithImageSet() {
    Attributes attrs = new Attributes();
    Sequence imgSets = attrs.newSequence(Tag.ImageSetsSequence, 1);

    Attributes isItem = new Attributes();
    Sequence selectorSeq = isItem.newSequence(Tag.ImageSetSelectorSequence, 1);
    Attributes selectorItem = new Attributes();
    selectorItem.setString(Tag.ImageSetSelectorUsageFlag, VR.CS, "MATCH");
    selectorItem.setInt(Tag.SelectorAttribute, VR.AT, Tag.Modality);
    selectorItem.setString(Tag.SelectorAttributeVR, VR.CS, "CS");
    selectorItem.setString(Tag.SelectorCSValue, VR.CS, "CT");
    selectorSeq.add(selectorItem);

    Sequence tbisSeq = isItem.newSequence(Tag.TimeBasedImageSetsSequence, 1);
    Attributes tbisItem = new Attributes();
    tbisItem.setInt(Tag.ImageSetNumber, VR.US, 1);
    tbisItem.setString(Tag.ImageSetLabel, VR.LO, "test");
    tbisSeq.add(tbisItem);

    imgSets.add(isItem);
    return attrs;
  }

  /** Build the minimal Attributes for a DisplaySet item. */
  private static Attributes buildDisplaySetAttributes(
      int number, int presentationGroup, int imageSetNumber) {
    Attributes ds = new Attributes();
    ds.setInt(Tag.DisplaySetNumber, VR.US, number);
    ds.setInt(Tag.DisplaySetPresentationGroup, VR.US, presentationGroup);
    ds.setInt(Tag.ImageSetNumber, VR.US, imageSetNumber);
    Sequence ibSeq = ds.newSequence(Tag.ImageBoxesSequence, 1);
    Attributes ibItem = new Attributes();
    ibItem.setInt(Tag.ImageBoxNumber, VR.US, 1);
    ibSeq.add(ibItem);
    return ds;
  }
}
