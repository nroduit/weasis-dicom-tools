/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.hp.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.geom.ImageOrientation.Plan;
import org.weasis.dicom.hp.HPComparator;
import org.weasis.dicom.hp.HPSelector;
import org.weasis.dicom.hp.enums.SortingDirection;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HPPluginsTest {

  @Nested
  class AlongAxisComparator_Tests {

    @Test
    void constructor_from_sorting_direction_populates_attributes() {
      AlongAxisComparator cmp = new AlongAxisComparator(SortingDirection.INCREASING);
      Attributes attrs = cmp.getAttributes();
      assertEquals("ALONG_AXIS", attrs.getString(Tag.SortByCategory));
      assertEquals("INCREASING", attrs.getString(Tag.SortingDirection));
    }

    @Test
    void constructor_from_attributes_uses_sorting_direction() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.SortingDirection, VR.CS, "DECREASING");
      AlongAxisComparator cmp = new AlongAxisComparator(attrs);
      assertSame(attrs, cmp.getAttributes());
    }

    @Test
    void constructor_from_attributes_throws_when_sorting_direction_missing() {
      Attributes attrs = new Attributes();
      assertThrows(IllegalArgumentException.class, () -> new AlongAxisComparator(attrs));
    }

    @Test
    void compare_returns_negative_for_increasing_when_first_position_smaller() {
      AlongAxisComparator cmp = new AlongAxisComparator(SortingDirection.INCREASING);
      Attributes a = withAxialFrame(0.0);
      Attributes b = withAxialFrame(10.0);
      assertTrue(cmp.compare(a, 0, b, 0) < 0);
      assertTrue(cmp.compare(b, 0, a, 0) > 0);
    }

    @Test
    void compare_returns_positive_for_decreasing_when_first_position_smaller() {
      AlongAxisComparator cmp = new AlongAxisComparator(SortingDirection.DECREASING);
      Attributes a = withAxialFrame(0.0);
      Attributes b = withAxialFrame(10.0);
      assertTrue(cmp.compare(a, 0, b, 0) > 0);
    }

    @Test
    void compare_returns_zero_for_equal_positions() {
      AlongAxisComparator cmp = new AlongAxisComparator(SortingDirection.INCREASING);
      Attributes a = withAxialFrame(5.0);
      Attributes b = withAxialFrame(5.0);
      assertEquals(0, cmp.compare(a, 0, b, 0));
    }

    @Test
    void compare_returns_zero_when_image_position_missing() {
      AlongAxisComparator cmp = new AlongAxisComparator(SortingDirection.INCREASING);
      Attributes a = new Attributes();
      Attributes b = new Attributes();
      assertEquals(0, cmp.compare(a, 0, b, 0));
    }

    @Test
    void compare_falls_back_to_per_frame_functional_groups() {
      AlongAxisComparator cmp = new AlongAxisComparator(SortingDirection.INCREASING);
      Attributes a = withPerFrameFunctionalGroups(0.0);
      Attributes b = withPerFrameFunctionalGroups(10.0);
      assertTrue(cmp.compare(a, 0, b, 0) < 0);
    }

    private Attributes withAxialFrame(double z) {
      Attributes attrs = new Attributes();
      attrs.setDouble(Tag.ImagePositionPatient, VR.DS, 0.0, 0.0, z);
      attrs.setDouble(Tag.ImageOrientationPatient, VR.DS, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
      return attrs;
    }

    private Attributes withPerFrameFunctionalGroups(double z) {
      Attributes attrs = new Attributes();
      Sequence perFrame = attrs.newSequence(Tag.PerFrameFunctionalGroupsSequence, 1);
      Attributes frameItem = new Attributes();
      Sequence planePos = frameItem.newSequence(Tag.PlanePositionSequence, 1);
      Attributes pos = new Attributes();
      pos.setDouble(Tag.ImagePositionPatient, VR.DS, 0.0, 0.0, z);
      pos.setDouble(Tag.ImageOrientationPatient, VR.DS, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
      planePos.add(pos);
      perFrame.add(frameItem);
      return attrs;
    }
  }

  @Nested
  class ByAcqTimeComparator_Tests {

    @Test
    void constructor_from_sorting_direction_populates_attributes() {
      ByAcqTimeComparator cmp = new ByAcqTimeComparator(SortingDirection.INCREASING);
      Attributes attrs = cmp.getAttributes();
      assertEquals("BY_ACQ_TIME", attrs.getString(Tag.SortByCategory));
      assertEquals("INCREASING", attrs.getString(Tag.SortingDirection));
    }

    @Test
    void constructor_from_attributes_throws_when_direction_missing() {
      assertThrows(IllegalArgumentException.class, () -> new ByAcqTimeComparator(new Attributes()));
    }

    @Test
    void compare_uses_acquisition_date_time() {
      ByAcqTimeComparator cmp = new ByAcqTimeComparator(SortingDirection.INCREASING);
      // Use the combined DT path: dcm4che's getDate(int da, int tm) returns null even
      // when both tags are populated, so the only working primary path is AcquisitionDateTime.
      Attributes a = new Attributes();
      a.setDate(Tag.AcquisitionDateTime, VR.DT, new Date(JAN_1_1970));
      Attributes b = new Attributes();
      b.setDate(Tag.AcquisitionDateTime, VR.DT, new Date(JAN_2_1970));
      assertTrue(cmp.compare(a, 0, b, 0) < 0);
      assertTrue(cmp.compare(b, 0, a, 0) > 0);
    }

    @Test
    void compare_decreasing_direction_inverts_sign() {
      ByAcqTimeComparator cmp = new ByAcqTimeComparator(SortingDirection.DECREASING);
      Attributes a = new Attributes();
      a.setDate(Tag.AcquisitionDateTime, VR.DT, new Date(JAN_1_1970));
      Attributes b = new Attributes();
      b.setDate(Tag.AcquisitionDateTime, VR.DT, new Date(JAN_2_1970));
      assertTrue(cmp.compare(a, 0, b, 0) > 0);
    }

    @Test
    void compare_returns_zero_when_either_side_has_no_date() {
      ByAcqTimeComparator cmp = new ByAcqTimeComparator(SortingDirection.INCREASING);
      Attributes withDate = new Attributes();
      withDate.setDate(Tag.AcquisitionDateTime, VR.DT, new Date(JAN_1_1970));
      assertEquals(0, cmp.compare(withDate, 0, new Attributes(), 0));
      assertEquals(0, cmp.compare(new Attributes(), 0, withDate, 0));
    }

    private static final long JAN_1_1970 = 0L;
    private static final long JAN_2_1970 = 86_400_000L;
  }

  @Nested
  class ImagePlaneSelector_Tests {

    @Test
    void constructor_from_planes_populates_attributes() {
      ImagePlaneSelector sel = new ImagePlaneSelector(new Plan[] {Plan.TRANSVERSE, Plan.SAGITTAL});
      Attributes attrs = sel.getAttributes();
      assertEquals("IMAGE_PLANE", attrs.getString(Tag.FilterByCategory));
      assertEquals("CS", attrs.getString(Tag.SelectorAttributeVR));
      String[] values = attrs.getStrings(Tag.SelectorCSValue);
      assertEquals(2, values.length);
      assertEquals("TRANSVERSE", values[0]);
      assertEquals("SAGITTAL", values[1]);
    }

    @Test
    void constructor_from_attributes_reads_planes() {
      Attributes attrs = csFilterOp("TRANSVERSE", "CORONAL");
      ImagePlaneSelector sel = new ImagePlaneSelector(attrs);
      assertSame(attrs, sel.getAttributes());
    }

    @Test
    void constructor_throws_when_selector_vr_missing() {
      Attributes attrs = new Attributes();
      assertThrows(IllegalArgumentException.class, () -> new ImagePlaneSelector(attrs));
    }

    @Test
    void constructor_throws_when_selector_vr_not_cs() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.SelectorAttributeVR, VR.CS, "DS");
      attrs.setString(Tag.SelectorCSValue, VR.CS, "TRANSVERSE");
      assertThrows(IllegalArgumentException.class, () -> new ImagePlaneSelector(attrs));
    }

    @Test
    void constructor_throws_when_cs_value_missing() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.SelectorAttributeVR, VR.CS, "CS");
      assertThrows(IllegalArgumentException.class, () -> new ImagePlaneSelector(attrs));
    }

    @Test
    void min_cosine_getter_and_setter() {
      ImagePlaneSelector sel = new ImagePlaneSelector(new Plan[] {Plan.TRANSVERSE});
      assertEquals(ImagePlaneSelector.DEF_MIN_COSINE, sel.getMinCosine());
      sel.setMinCosine(0.95f);
      assertEquals(0.95f, sel.getMinCosine());
    }

    @Test
    void matches_returns_true_when_iop_matches_selected_plane() {
      ImagePlaneSelector sel = new ImagePlaneSelector(new Plan[] {Plan.TRANSVERSE});
      Attributes attrs = new Attributes();
      attrs.setDouble(Tag.ImageOrientationPatient, VR.DS, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
      assertTrue(sel.matches(attrs, 0));
    }

    @Test
    void matches_returns_false_when_iop_is_different_plane() {
      ImagePlaneSelector sel = new ImagePlaneSelector(new Plan[] {Plan.SAGITTAL});
      Attributes attrs = new Attributes();
      attrs.setDouble(Tag.ImageOrientationPatient, VR.DS, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
      assertFalse(sel.matches(attrs, 0));
    }

    @Test
    void matches_uses_patient_orientation_when_iop_absent() {
      ImagePlaneSelector sel = new ImagePlaneSelector(new Plan[] {Plan.TRANSVERSE});
      Attributes attrs = new Attributes();
      attrs.setString(Tag.PatientOrientation, VR.CS, "L", "P");
      assertTrue(sel.matches(attrs, 0));
    }

    @Test
    void matches_returns_true_when_no_orientation_information_present() {
      // Falls back to "no filter" when nothing identifies the plane.
      ImagePlaneSelector sel = new ImagePlaneSelector(new Plan[] {Plan.SAGITTAL});
      assertTrue(sel.matches(new Attributes(), 0));
    }

    private Attributes csFilterOp(String... values) {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.SelectorAttributeVR, VR.CS, "CS");
      attrs.setString(Tag.SelectorCSValue, VR.CS, values);
      return attrs;
    }
  }

  @Nested
  class Service_Tests {

    @Test
    void along_axis_service_exposes_category_and_factory() {
      AlongAxisComparatorService svc = new AlongAxisComparatorService();
      assertEquals("ALONG_AXIS", svc.getCategoryName());
      Attributes sortOp = new Attributes();
      sortOp.setString(Tag.SortingDirection, VR.CS, "INCREASING");
      HPComparator cmp = svc.createHPComparator(sortOp);
      assertNotNull(cmp);
      assertTrue(cmp instanceof AlongAxisComparator);
    }

    @Test
    void by_acq_time_service_exposes_category_and_factory() {
      ByAcqTimeComparatorService svc = new ByAcqTimeComparatorService();
      assertEquals("BY_ACQ_TIME", svc.getCategoryName());
      Attributes sortOp = new Attributes();
      sortOp.setString(Tag.SortingDirection, VR.CS, "INCREASING");
      HPComparator cmp = svc.createHPComparator(sortOp);
      assertNotNull(cmp);
      assertTrue(cmp instanceof ByAcqTimeComparator);
    }

    @Test
    void image_plane_service_round_trips_min_cosine_property() {
      ImagePlaneSelectorService svc = new ImagePlaneSelectorService();
      assertEquals("IMAGE_PLANE", svc.getCategoryName());
      assertEquals(ImagePlaneSelector.DEF_MIN_COSINE, (Float) svc.getProperty("MinCosine"));
      svc.setProperty("MinCosine", 0.95f);
      assertEquals(0.95f, (Float) svc.getProperty("MinCosine"));
    }

    @Test
    void image_plane_service_rejects_unknown_property() {
      ImagePlaneSelectorService svc = new ImagePlaneSelectorService();
      assertThrows(IllegalArgumentException.class, () -> svc.setProperty("Other", 0.9f));
      assertThrows(IllegalArgumentException.class, () -> svc.getProperty("Other"));
    }

    @Test
    void image_plane_service_rejects_out_of_range_min_cosine() {
      ImagePlaneSelectorService svc = new ImagePlaneSelectorService();
      assertThrows(IllegalArgumentException.class, () -> svc.setProperty("MinCosine", 0.5f));
      assertThrows(IllegalArgumentException.class, () -> svc.setProperty("MinCosine", 1.5f));
    }

    @Test
    void image_plane_service_creates_selector_with_configured_min_cosine() {
      ImagePlaneSelectorService svc = new ImagePlaneSelectorService();
      svc.setProperty("MinCosine", 0.95f);
      Attributes filterOp = new Attributes();
      filterOp.setString(Tag.SelectorAttributeVR, VR.CS, "CS");
      filterOp.setString(Tag.SelectorCSValue, VR.CS, "TRANSVERSE");
      HPSelector sel = svc.createHPSelector(filterOp);
      assertNotNull(sel);
      assertEquals(0.95f, ((ImagePlaneSelector) sel).getMinCosine());
    }
  }
}
