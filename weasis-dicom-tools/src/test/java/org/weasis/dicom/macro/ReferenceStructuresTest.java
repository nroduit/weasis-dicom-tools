/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.macro;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test suite for higher-level reference structures. Tests SeriesAndInstanceReference and
 * HierarchicalSOPInstanceReference.
 */
@DisplayName("Reference Structures Tests")
class ReferenceStructuresTest {

  private static final String TEST_SERIES_UID = "1.2.3.4.5.6.7.8.9.100";
  private static final String TEST_AE_TITLE = "TEST_AE";
  private static final String TEST_FILE_SET_ID = "DISK001";
  private static final String TEST_FILE_SET_UID = "1.2.3.4.5.6.7.8.9.200";

  @Nested
  @DisplayName("SeriesAndInstanceReference Tests")
  class SeriesAndInstanceReferenceTest {

    private SeriesAndInstanceReference reference;

    @BeforeEach
    void setUp() {
      reference = new SeriesAndInstanceReference();
    }

    @Test
    @DisplayName("Should handle series instance UID correctly")
    void shouldHandleSeriesInstanceUID() {
      reference.setSeriesInstanceUID(TEST_SERIES_UID);
      assertEquals(TEST_SERIES_UID, reference.getSeriesInstanceUID());
    }

    @Test
    @DisplayName("Should handle retrieve AE title correctly")
    void shouldHandleRetrieveAETitle() {
      reference.setRetrieveAETitle(TEST_AE_TITLE);
      assertEquals(TEST_AE_TITLE, reference.getRetrieveAETitle());
    }

    @Test
    @DisplayName("Should handle storage media file set ID correctly")
    void shouldHandleStorageMediaFileSetID() {
      reference.setStorageMediaFileSetID(TEST_FILE_SET_ID);
      assertEquals(TEST_FILE_SET_ID, reference.getStorageMediaFileSetID());
    }

    @Test
    @DisplayName("Should handle storage media file set UID correctly")
    void shouldHandleStorageMediaFileSetUID() {
      reference.setStorageMediaFileSetUID(TEST_FILE_SET_UID);
      assertEquals(TEST_FILE_SET_UID, reference.getStorageMediaFileSetUID());
    }

    @Test
    @DisplayName("Should handle referenced SOP instances correctly")
    void shouldHandleReferencedSOPInstances() {
      // Initially should be empty
      Collection<SOPInstanceReferenceAndMAC> instances = reference.getReferencedSOPInstances();
      assertTrue(instances.isEmpty());

      // Test setting empty collection
      reference.setReferencedSOPInstances(List.of());
      assertTrue(reference.getReferencedSOPInstances().isEmpty());
    }

    @Test
    @DisplayName("Should convert sequence correctly")
    void shouldConvertSequenceCorrectly() {
      // Test null sequence
      Collection<SeriesAndInstanceReference> result =
          SeriesAndInstanceReference.toSeriesAndInstanceReferenceMacros(null);
      assertTrue(result.isEmpty());

      // Test populated sequence using proper newSequence method
      Attributes seqContainer = new Attributes();
      Sequence seq = seqContainer.newSequence(Tag.ReferencedSeriesSequence, 1);
      Attributes item = new Attributes();
      item.setString(Tag.SeriesInstanceUID, VR.UI, TEST_SERIES_UID);
      item.setString(Tag.RetrieveAETitle, VR.AE, TEST_AE_TITLE);
      seq.add(item);

      result = SeriesAndInstanceReference.toSeriesAndInstanceReferenceMacros(seq);
      assertEquals(1, result.size());

      SeriesAndInstanceReference ref = result.iterator().next();
      assertEquals(TEST_SERIES_UID, ref.getSeriesInstanceUID());
      assertEquals(TEST_AE_TITLE, ref.getRetrieveAETitle());
    }

    @Test
    @DisplayName("Should handle complete series reference setup")
    void shouldHandleCompleteSeriesReferenceSetup() {
      reference.setSeriesInstanceUID(TEST_SERIES_UID);
      reference.setRetrieveAETitle(TEST_AE_TITLE);
      reference.setStorageMediaFileSetID(TEST_FILE_SET_ID);
      reference.setStorageMediaFileSetUID(TEST_FILE_SET_UID);

      assertEquals(TEST_SERIES_UID, reference.getSeriesInstanceUID());
      assertEquals(TEST_AE_TITLE, reference.getRetrieveAETitle());
      assertEquals(TEST_FILE_SET_ID, reference.getStorageMediaFileSetID());
      assertEquals(TEST_FILE_SET_UID, reference.getStorageMediaFileSetUID());
    }
  }

  @Nested
  @DisplayName("HierarchicalSOPInstanceReference Tests")
  class HierarchicalSOPInstanceReferenceTest {

    private HierarchicalSOPInstanceReference reference;

    @BeforeEach
    void setUp() {
      reference = new HierarchicalSOPInstanceReference();
    }

    @Test
    @DisplayName("Should handle study instance UID correctly")
    void shouldHandleStudyInstanceUID() {
      String studyUID = "1.2.3.4.5.6.7.8.9.300";
      reference.setStudyInstanceUID(studyUID);
      assertEquals(studyUID, reference.getStudyInstanceUID());
    }

    @Test
    @DisplayName("Should handle referenced series correctly")
    void shouldHandleReferencedSeries() {
      // Initially should be empty
      Collection<SeriesAndInstanceReference> series = reference.getReferencedSeries();
      assertTrue(series.isEmpty());

      // Test setting empty collection
      reference.setReferencedSeries(List.of());
      assertTrue(reference.getReferencedSeries().isEmpty());
    }

    @Test
    @DisplayName("Should convert sequence correctly")
    void shouldConvertSequenceCorrectly() {
      Collection<HierarchicalSOPInstanceReference> result =
          HierarchicalSOPInstanceReference.toHierarchicalSOPInstanceReferenceMacros(null);
      assertTrue(result.isEmpty());
      // Test with actual sequence using proper newSequence method
      Attributes seqContainer = new Attributes();
      Sequence seq =
          seqContainer.newSequence(Tag.StudiesContainingOtherReferencedInstancesSequence, 1);

      Attributes item = new Attributes();
      item.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9.300");
      seq.add(item);

      result = HierarchicalSOPInstanceReference.toHierarchicalSOPInstanceReferenceMacros(seq);
      assertEquals(1, result.size());
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTest {

    @Test
    @DisplayName("Should create complete hierarchical structure")
    void shouldCreateCompleteHierarchicalStructure() {
      // Create hierarchical reference
      HierarchicalSOPInstanceReference hierRef = new HierarchicalSOPInstanceReference();
      hierRef.setStudyInstanceUID("1.2.3.4.5.6.7.8.9.300");

      // Create series reference
      SeriesAndInstanceReference seriesRef = new SeriesAndInstanceReference();
      seriesRef.setSeriesInstanceUID(TEST_SERIES_UID);
      seriesRef.setRetrieveAETitle(TEST_AE_TITLE);

      // Verify structure
      assertNotNull(hierRef.getStudyInstanceUID());
      assertNotNull(seriesRef.getSeriesInstanceUID());
      assertNotNull(seriesRef.getRetrieveAETitle());
    }

    @Test
    @DisplayName("Should handle complex sequence structures")
    void shouldHandleComplexSequenceStructures() {
      // Create a complete hierarchical structure using proper sequence creation
      Attributes rootAttrs = new Attributes();
      Sequence studySeq =
          rootAttrs.newSequence(Tag.StudiesContainingOtherReferencedInstancesSequence, 1);

      Attributes studyItem = new Attributes();
      studyItem.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9.300");

      // Add series sequence within study
      Sequence seriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, 1);
      Attributes seriesItem = new Attributes();
      seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, TEST_SERIES_UID);
      seriesItem.setString(Tag.RetrieveAETitle, VR.AE, TEST_AE_TITLE);

      // Add instance sequence within series
      seriesItem.newSequence(Tag.ReferencedInstanceSequence, 0); // Empty for now

      seriesSeq.add(seriesItem);
      studySeq.add(studyItem);

      // Verify the structure was created correctly
      assertNotNull(rootAttrs.getSequence(Tag.StudiesContainingOtherReferencedInstancesSequence));
      assertEquals(1, studySeq.size());
      assertEquals("1.2.3.4.5.6.7.8.9.300", studyItem.getString(Tag.StudyInstanceUID));
    }
  }
}
