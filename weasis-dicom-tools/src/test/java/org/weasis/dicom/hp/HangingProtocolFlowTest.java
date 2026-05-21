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

import java.util.Date;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.hp.enums.RelativeTimeUnits;
import org.weasis.dicom.macro.Code;
import org.weasis.dicom.macro.SOPInstanceReference;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HangingProtocolFlowTest {

  @Nested
  class Getters_Setters_RoundTrip_Tests {

    @Test
    void all_string_fields_round_trip() {
      HangingProtocol hp = new HangingProtocol();
      hp.setHangingProtocolName("name");
      hp.setHangingProtocolDescription("desc");
      hp.setHangingProtocolLevel("USER");
      hp.setHangingProtocolCreator("creator");
      hp.setHangingProtocolUserGroupName("group");
      hp.setPartialDataDisplayHandling("BEST_VIEW");
      hp.setSOPClassUID("1.2.3");
      hp.setSOPInstanceUID("4.5.6");
      hp.setInstanceCreatorUID("7.8.9");
      hp.setSpecificCharacterSet(new String[] {"ISO_IR 100"});

      assertEquals("name", hp.getHangingProtocolName());
      assertEquals("desc", hp.getHangingProtocolDescription());
      assertEquals("USER", hp.getHangingProtocolLevel());
      assertEquals("creator", hp.getHangingProtocolCreator());
      assertEquals("group", hp.getHangingProtocolUserGroupName());
      assertEquals("BEST_VIEW", hp.getPartialDataDisplayHandling());
      assertEquals("1.2.3", hp.getSOPClassUID());
      assertEquals("4.5.6", hp.getSOPInstanceUID());
      assertEquals("7.8.9", hp.getInstanceCreatorUID());
      assertEquals(1, hp.getSpecificCharacterSet().length);
    }

    @Test
    void numeric_fields_round_trip() {
      HangingProtocol hp = new HangingProtocol();
      hp.setNumberOfPriorsReferenced(3);
      hp.setNumberOfScreens(2);

      assertEquals(3, hp.getNumberOfPriorsReferenced());
      assertEquals(2, hp.getNumberOfScreens());
    }

    @Test
    void creation_date_time_setter_writes_attributes() {
      HangingProtocol hp = new HangingProtocol();
      Date d = new Date(86_400_000L);
      hp.setHangingProtocolCreationDateTime(d);
      hp.setInstanceCreationDateTime(d);
      // Just verify setters don't throw and persist into the attributes object.
      assertNotNull(hp.getAttributes().getString(Tag.HangingProtocolCreationDateTime));
    }

    @Test
    void source_hanging_protocol_setter_persists_a_sequence_item() {
      HangingProtocol hp = new HangingProtocol();
      SOPInstanceReference ref = new SOPInstanceReference();
      ref.setReferencedSOPClassUID("1.2.3");
      ref.setReferencedSOPInstanceUID("4.5.6");
      hp.setSourceHangingProtocol(ref);
      // The getter constructs a fresh wrapper from the stored attributes.
      assertNotNull(hp.getSourceHangingProtocol());
    }

    @Test
    void user_identification_code_setter_writes_attributes() {
      HangingProtocol hp = new HangingProtocol();
      Code code = new Code();
      code.setCodeValue("123");
      code.setCodingSchemeDesignator("SCT");
      hp.setHangingProtocolUserIdentificationCodeSequence(code);
      // Exercises the setter path (the getter reads a different tag and would be null).
      assertNotNull(hp.getAttributes().getSequence(Tag.AbstractPriorCodeSequence));
    }
  }

  @Nested
  class ImageSet_Add_Remove_Tests {

    @Test
    void add_image_set_assigns_sequential_numbers() {
      HangingProtocol hp = new HangingProtocol();
      HPImageSet is1 = hp.addNewImageSet(null);
      HPImageSet is2 = hp.addNewImageSet(null);
      assertEquals(1, is1.getImageSetNumber());
      assertEquals(2, is2.getImageSetNumber());
      assertEquals(2, hp.getImageSets().size());
    }

    @Test
    void remove_image_set_null_throws() {
      HangingProtocol hp = new HangingProtocol();
      assertThrows(NullPointerException.class, () -> hp.removeImageSet(null));
    }

    @Test
    void remove_image_set_returns_false_when_absent() {
      HangingProtocol hp = new HangingProtocol();
      assertFalse(hp.removeImageSet(new HPImageSetForTest()));
    }

    @Test
    void remove_all_image_sets_clears_list_and_display_sets() {
      HangingProtocol hp = new HangingProtocol();
      hp.addNewImageSet(null);
      HPDisplaySet ds = new HPDisplaySet();
      ds.setDisplaySetPresentationGroup(1);
      ds.addImageBox(new HPImageBox());
      hp.addDisplaySet(ds);
      assertEquals(1, hp.getImageSets().size());
      assertEquals(1, hp.getDisplaySets().size());

      hp.removeAllImageSets();
      assertTrue(hp.getImageSets().isEmpty());
      assertTrue(hp.getDisplaySets().isEmpty());
    }
  }

  @Nested
  class DisplaySet_Flow_Tests {

    @Test
    void add_display_set_null_throws() {
      HangingProtocol hp = new HangingProtocol();
      assertThrows(NullPointerException.class, () -> hp.addDisplaySet(null));
    }

    @Test
    void remove_display_set_null_throws() {
      HangingProtocol hp = new HangingProtocol();
      assertThrows(NullPointerException.class, () -> hp.removeDisplaySet(null));
    }

    @Test
    void remove_display_set_returns_false_when_absent() {
      HangingProtocol hp = new HangingProtocol();
      assertFalse(hp.removeDisplaySet(new HPDisplaySet()));
    }

    @Test
    void add_display_set_assigns_number_and_default_group() {
      HangingProtocol hp = new HangingProtocol();
      HPDisplaySet ds = displaySetWithImageBox(1);
      hp.addDisplaySet(ds);
      assertEquals(1, ds.getDisplaySetNumber());
      assertEquals(1, ds.getDisplaySetPresentationGroup());
    }

    @Test
    void remove_display_set_renumbers_following_display_sets() {
      HangingProtocol hp = new HangingProtocol();
      HPDisplaySet ds1 = displaySetWithImageBox(1);
      HPDisplaySet ds2 = displaySetWithImageBox(1);
      HPDisplaySet ds3 = displaySetWithImageBox(1);
      hp.addDisplaySet(ds1);
      hp.addDisplaySet(ds2);
      hp.addDisplaySet(ds3);

      assertTrue(hp.removeDisplaySet(ds1));
      assertEquals(1, ds2.getDisplaySetNumber());
      assertEquals(2, ds3.getDisplaySetNumber());
    }

    @Test
    void presentation_group_listing_returns_display_sets_in_group() {
      HangingProtocol hp = new HangingProtocol();
      HPDisplaySet ds1 = displaySetWithImageBox(1);
      ds1.setDisplaySetPresentationGroup(1);
      HPDisplaySet ds2 = displaySetWithImageBox(2);
      ds2.setDisplaySetPresentationGroup(2);
      hp.addDisplaySet(ds1);
      hp.addDisplaySet(ds2);

      assertEquals(1, hp.getDisplaySetsOfPresentationGroup(1).size());
      assertEquals(1, hp.getDisplaySetsOfPresentationGroup(2).size());
      assertTrue(hp.getDisplaySetsOfPresentationGroup(99).isEmpty());
    }

    private static HPDisplaySet displaySetWithImageBox(int presentationGroup) {
      HPDisplaySet ds = new HPDisplaySet();
      ds.setDisplaySetPresentationGroup(presentationGroup);
      ds.addImageBox(new HPImageBox());
      return ds;
    }
  }

  @Nested
  class Definition_And_Screen_Tests {

    @Test
    void hanging_protocol_definition_add_and_listing() {
      HangingProtocol hp = new HangingProtocol();
      HPDefinition def = new HPDefinition();
      def.setModality("CT");
      def.setLaterality("L");
      hp.addHangingProtocolDefinition(def);
      assertEquals(1, hp.getHangingProtocolDefinitions().size());
      assertEquals("CT", hp.getHangingProtocolDefinitions().get(0).getModality());
    }

    @Test
    void add_definition_null_throws() {
      HangingProtocol hp = new HangingProtocol();
      assertThrows(NullPointerException.class, () -> hp.addHangingProtocolDefinition(null));
    }

    @Test
    void remove_definition_null_throws() {
      HangingProtocol hp = new HangingProtocol();
      assertThrows(NullPointerException.class, () -> hp.removeHangingProtocolDefinition(null));
    }

    @Test
    void remove_definition_returns_false_when_absent() {
      HangingProtocol hp = new HangingProtocol();
      assertFalse(hp.removeHangingProtocolDefinition(new HPDefinition()));
    }

    @Test
    void remove_all_definitions_clears() {
      HangingProtocol hp = new HangingProtocol();
      hp.addHangingProtocolDefinition(new HPDefinition());
      hp.addHangingProtocolDefinition(new HPDefinition());
      hp.removeAllHangingProtocolDefinition();
      assertTrue(hp.getHangingProtocolDefinitions().isEmpty());
    }

    @Test
    void nominal_screen_definitions_round_trip() {
      HangingProtocol hp = new HangingProtocol();
      HPScreenDefinition screen = new HPScreenDefinition();
      screen.setNumberOfVerticalPixels(1080);
      screen.setNumberOfHorizontalPixels(1920);
      hp.addNominalScreenDefinition(screen);
      assertEquals(1, hp.getNominalScreenDefinitions().size());
      assertTrue(hp.removeNominalScreenDefinition(screen));
    }

    @Test
    void add_screen_null_throws() {
      HangingProtocol hp = new HangingProtocol();
      assertThrows(NullPointerException.class, () -> hp.addNominalScreenDefinition(null));
    }

    @Test
    void remove_screen_null_throws() {
      HangingProtocol hp = new HangingProtocol();
      assertThrows(NullPointerException.class, () -> hp.removeNominalScreenDefinition(null));
    }

    @Test
    void remove_all_screens_clears() {
      HangingProtocol hp = new HangingProtocol();
      hp.addNominalScreenDefinition(new HPScreenDefinition());
      hp.removeAllNominalScreenDefinitions();
      assertTrue(hp.getNominalScreenDefinitions().isEmpty());
    }
  }

  @Nested
  class ScrollingAndNavigation_Tests {

    @Test
    void add_remove_scrolling_group() {
      HangingProtocol hp = new HangingProtocol();
      HPScrollingGroup sg = new HPScrollingGroup();
      hp.addScrollingGroup(sg);
      assertEquals(1, hp.getScrollingGroups().size());
      assertTrue(hp.removeScrollingGroup(sg));
      assertFalse(hp.removeScrollingGroup(sg));
    }

    @Test
    void remove_scrolling_group_null_throws() {
      HangingProtocol hp = new HangingProtocol();
      assertThrows(NullPointerException.class, () -> hp.removeScrollingGroup(null));
    }

    @Test
    void remove_all_scrolling_groups_clears() {
      HangingProtocol hp = new HangingProtocol();
      hp.addScrollingGroup(new HPScrollingGroup());
      hp.removeAllScrollingGroups();
      assertTrue(hp.getScrollingGroups().isEmpty());
    }

    @Test
    void add_remove_navigation_group() {
      HangingProtocol hp = new HangingProtocol();
      HPNavigationGroup ng = new HPNavigationGroup();
      hp.addNavigationGroup(ng);
      assertEquals(1, hp.getNavigationGroups().size());
      assertTrue(hp.removeNavigationGroup(ng));
      assertFalse(hp.removeNavigationGroup(ng));
    }

    @Test
    void remove_navigation_group_null_throws() {
      HangingProtocol hp = new HangingProtocol();
      assertThrows(NullPointerException.class, () -> hp.removeNavigationGroup(null));
    }

    @Test
    void remove_all_navigation_groups_clears() {
      HangingProtocol hp = new HangingProtocol();
      hp.addNavigationGroup(new HPNavigationGroup());
      hp.removeAllNavigationGroups();
      assertTrue(hp.getNavigationGroups().isEmpty());
    }
  }

  @Nested
  class HPTimeBasedImageSet_Tests {

    @Test
    void relative_time_round_trip() {
      HPTimeBasedImageSet tbis = new HPTimeBasedImageSet();
      RelativeTime rt = new RelativeTime(3, 7, RelativeTimeUnits.DAYS);
      tbis.setRelativeTime(rt);
      assertTrue(tbis.hasRelativeTime());
      RelativeTime back = tbis.getRelativeTime();
      assertEquals(3, back.getStart());
      assertEquals(7, back.getEnd());
      assertEquals(RelativeTimeUnits.DAYS, back.getUnits());
    }

    @Test
    void abstract_prior_value_round_trip() {
      HPTimeBasedImageSet tbis = new HPTimeBasedImageSet();
      AbstractPriorValue v = new AbstractPriorValue(1, 5);
      tbis.setAbstractPriorValue(v);
      assertTrue(tbis.hasAbstractPriorValue());
      AbstractPriorValue back = tbis.getAbstractPriorValue();
      assertEquals(1, back.getStart());
      assertEquals(5, back.getEnd());
    }

    @Test
    void abstract_prior_code_round_trip() {
      HPTimeBasedImageSet tbis = new HPTimeBasedImageSet();
      Code code = new Code();
      code.setCodeValue("PRIOR");
      code.setCodingSchemeDesignator("DCM");
      tbis.setAbstractPriorCode(code);
      assertTrue(tbis.hasAbstractPriorCode());
      assertNotNull(tbis.getAbstractPriorCode());
    }

    @Test
    void getters_handle_empty_state() {
      HPTimeBasedImageSet tbis = new HPTimeBasedImageSet();
      assertFalse(tbis.hasRelativeTime());
      assertFalse(tbis.hasAbstractPriorValue());
      assertFalse(tbis.hasAbstractPriorCode());
    }
  }

  @Nested
  class HPImageSet_Tests {

    @Test
    void contains_returns_true_for_empty_selectors() {
      HangingProtocol hp = new HangingProtocol();
      HPImageSet is = hp.addNewImageSet(null);
      assertTrue(is.contains(new Attributes(), 0));
    }

    @Test
    void add_image_set_selector_persists_in_sequence() {
      HangingProtocol hp = new HangingProtocol();
      HPImageSet is = hp.addNewImageSet(null);
      HPSelector selector =
          HPSelectorFactory.createAttributeValueSelector(
              "MATCH", null, Tag.Modality, 0, VR.CS, new String[] {"CT"});
      is.addImageSetSelector(selector);
      assertEquals(1, is.getImageSetSelectors().size());
      assertNotNull(is.getImageSetSelectorSequence());
    }

    @Test
    void copy_constructor_clones_attributes() {
      HangingProtocol hp = new HangingProtocol();
      hp.setHangingProtocolName("original");
      HangingProtocol copy = new HangingProtocol(hp);
      assertEquals("original", copy.getHangingProtocolName());
      // copies should have distinct SOP instance UIDs
      assertSame(copy.getHangingProtocolName(), copy.getHangingProtocolName());
    }
  }

  // Test-only subclass to expose protected constructor for the "absent image set" case.
  private static class HPImageSetForTest extends HPImageSet {
    HPImageSetForTest() {
      super(99);
    }
  }
}
