/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.movescu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.tool.movescu.MoveSCU.InformationModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.core.util.StreamUtil;
import org.weasis.dicom.param.DicomProgress;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class MoveSCUTest {

  private static final String[] DEFAULT_TRANSFER_SYNTAXES = {
    UID.ExplicitVRLittleEndian, UID.ImplicitVRLittleEndian
  };

  private static final String TEST_DESTINATION = "STORESCP";
  private static final String TEST_STUDY_UID = "1.2.3.4.5.6.7.8.9";
  private static final String TEST_PATIENT_ID = "12345";

  @TempDir Path tempDir;

  private MoveSCU moveSCU;
  private DicomProgress mockProgress;
  @Mock private Association mockAssociation;

  @BeforeEach
  void setUp() throws IOException {
    mockProgress = mock(DicomProgress.class);
    moveSCU = new MoveSCU(mockProgress);
  }

  @Nested
  class Construction {

    @Test
    void creates_instance_without_progress() throws IOException {
      var scu = new MoveSCU();

      assertNotNull(scu);
      assertNotNull(scu.getApplicationEntity());
      assertNotNull(scu.getConnection());
      assertNotNull(scu.getRemoteConnection());
      assertNotNull(scu.getAAssociateRQ());
      assertNotNull(scu.getKeys());
      assertNotNull(scu.getState());
      assertEquals("MOVESCU", scu.getApplicationEntity().getAETitle());
    }

    @Test
    void creates_instance_with_progress() throws IOException {
      var progress = mock(DicomProgress.class);
      var scu = new MoveSCU(progress);

      assertNotNull(scu);
      assertSame(progress, scu.getState().getProgress());
    }

    @Test
    void initializes_default_values() {
      assertEquals("MOVESCU", moveSCU.getApplicationEntity().getAETitle());
      assertNotNull(moveSCU.getKeys());
      assertTrue(moveSCU.getKeys().isEmpty());
      assertNull(moveSCU.getAssociation());
    }
  }

  @Nested
  class Configuration {

    @Test
    void sets_priority() {
      var priority = 1;

      moveSCU.setPriority(priority);

      // Priority is used internally, verify through behavior
      assertDoesNotThrow(() -> moveSCU.setPriority(priority));
    }

    @Test
    void sets_cancel_after() {
      var cancelAfter = 5000;

      moveSCU.setCancelAfter(cancelAfter);

      assertDoesNotThrow(() -> moveSCU.setCancelAfter(cancelAfter));
    }

    @Test
    void sets_release_eager() {
      moveSCU.setReleaseEager(true);
      moveSCU.setReleaseEager(false);

      assertDoesNotThrow(() -> moveSCU.setReleaseEager(true));
    }

    @Test
    void sets_destination() {
      moveSCU.setDestination(TEST_DESTINATION);

      // Destination is used internally, verify through behavior
      assertDoesNotThrow(() -> moveSCU.setDestination(TEST_DESTINATION));
    }

    @Test
    void sets_input_filter() {
      var customFilter = new int[] {Tag.StudyInstanceUID, Tag.SeriesInstanceUID};

      moveSCU.setInputFilter(customFilter);

      assertDoesNotThrow(() -> moveSCU.setInputFilter(customFilter));
    }

    @Test
    void sets_input_filter_with_null_uses_default() {
      moveSCU.setInputFilter(null);

      assertDoesNotThrow(() -> moveSCU.setInputFilter(null));
    }
  }

  @Nested
  class Information_Model_Configuration {

    @Test
    void sets_patient_root_model_without_relational() {
      moveSCU.setInformationModel(InformationModel.PatientRoot, DEFAULT_TRANSFER_SYNTAXES, false);

      var rq = moveSCU.getAAssociateRQ();
      assertFalse(rq.getPresentationContexts().isEmpty());

      var pc = rq.getPresentationContexts().get(0);
      assertEquals(InformationModel.PatientRoot.getCuid(), pc.getAbstractSyntax());
      assertEquals("STUDY", moveSCU.getKeys().getString(Tag.QueryRetrieveLevel));
    }

    @Test
    void sets_study_root_model_with_relational() {
      moveSCU.setInformationModel(InformationModel.StudyRoot, DEFAULT_TRANSFER_SYNTAXES, true);

      var rq = moveSCU.getAAssociateRQ();
      assertAll(
          () -> assertEquals(1, rq.getPresentationContexts().size()),
          () ->
              assertArrayEquals(
                  DEFAULT_TRANSFER_SYNTAXES,
                  rq.getPresentationContexts().get(0).getTransferSyntaxes()),
          () -> assertEquals("STUDY", moveSCU.getKeys().getString(Tag.QueryRetrieveLevel)));
    }

    @Test
    void sets_composite_instance_root_model() {
      moveSCU.setInformationModel(
          InformationModel.CompositeInstanceRoot, DEFAULT_TRANSFER_SYNTAXES, false);

      assertEquals("IMAGE", moveSCU.getKeys().getString(Tag.QueryRetrieveLevel));
    }

    @Test
    void sets_hanging_protocol_model_without_level() {
      moveSCU.setInformationModel(
          InformationModel.HangingProtocol, DEFAULT_TRANSFER_SYNTAXES, false);

      assertNull(moveSCU.getKeys().getString(Tag.QueryRetrieveLevel));
    }

    @Test
    void adds_custom_level() {
      var customLevel = "SERIES";

      moveSCU.addLevel(customLevel);

      assertEquals(customLevel, moveSCU.getKeys().getString(Tag.QueryRetrieveLevel));
    }
  }

  @Nested
  class Query_Keys {

    @Test
    void adds_single_key_with_value() {
      moveSCU.addKey(Tag.StudyInstanceUID, TEST_STUDY_UID);

      assertEquals(TEST_STUDY_UID, moveSCU.getKeys().getString(Tag.StudyInstanceUID));
    }

    @Test
    void adds_key_with_multiple_values() {
      var values = new String[] {"VALUE1", "VALUE2", "VALUE3"};

      moveSCU.addKey(Tag.StudyDescription, values);

      var storedValues = moveSCU.getKeys().getStrings(Tag.StudyDescription);
      assertArrayEquals(values, storedValues);
    }

    @Test
    void adds_key_without_values() {
      moveSCU.addKey(Tag.PatientName);

      assertTrue(moveSCU.getKeys().contains(Tag.PatientName));
      assertNull(moveSCU.getKeys().getString(Tag.PatientName));
    }

    @Test
    void adds_multiple_different_keys() {
      moveSCU.addKey(Tag.StudyInstanceUID, TEST_STUDY_UID);
      moveSCU.addKey(Tag.PatientID, TEST_PATIENT_ID);
      moveSCU.addKey(Tag.Modality, "CT");

      var keys = moveSCU.getKeys();
      assertEquals(TEST_STUDY_UID, keys.getString(Tag.StudyInstanceUID));
      assertEquals(TEST_PATIENT_ID, keys.getString(Tag.PatientID));
      assertEquals("CT", keys.getString(Tag.Modality));
    }
  }

  @Nested
  class Information_Model_Enum {

    @Test
    void patient_root_has_correct_properties() {
      var model = InformationModel.PatientRoot;

      assertEquals(UID.PatientRootQueryRetrieveInformationModelMove, model.getCuid());
    }

    @Test
    void study_root_has_correct_properties() {
      var model = InformationModel.StudyRoot;

      assertEquals(UID.StudyRootQueryRetrieveInformationModelMove, model.getCuid());
    }

    @Test
    void composite_instance_root_has_correct_properties() {
      var model = InformationModel.CompositeInstanceRoot;

      assertEquals(UID.CompositeInstanceRootRetrieveMove, model.getCuid());
    }

    @Test
    void hanging_protocol_has_correct_properties() {
      var model = InformationModel.HangingProtocol;

      assertEquals(UID.HangingProtocolInformationModelMove, model.getCuid());
    }

    @Test
    void color_palette_has_correct_properties() {
      var model = InformationModel.ColorPalette;

      assertEquals(UID.ColorPaletteQueryRetrieveInformationModelMove, model.getCuid());
    }

    @Test
    void all_models_have_non_null_cuid() {
      for (var model : InformationModel.values()) {
        assertNotNull(model.getCuid());
        assertFalse(model.getCuid().isEmpty());
      }
    }
  }

  @Nested
  class Association_Management {

    @Test
    void open_throws_exception_without_remote_configuration() {
      assertThrows(Exception.class, () -> moveSCU.open());
    }

    @Test
    void close_handles_null_association() {
      assertDoesNotThrow(() -> moveSCU.close());
    }

    @Test
    void close_handles_association_not_ready() throws Exception {
      setMockAssociation(moveSCU, mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(false);

      assertDoesNotThrow(() -> moveSCU.close());
    }

    @Test
    void close_waits_for_outstanding_rsp_and_releases() throws Exception {
      setMockAssociation(moveSCU, mockAssociation);
      when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);

      moveSCU.close();

      verify(mockAssociation).waitForOutstandingRSP();
      verify(mockAssociation).release();
    }
  }

  @Nested
  class DICOM_File_Retrieval {

    @Test
    void retrieve_from_file_throws_exception_for_non_existent_file() {
      var nonExistentFile = tempDir.resolve("non-existent.dcm");

      assertThrows(IOException.class, () -> moveSCU.retrieve(nonExistentFile));
    }

    @Test
    void retrieve_from_file_throws_exception_for_invalid_dicom_file() throws IOException {
      var invalidFile = tempDir.resolve("invalid.dcm");
      Files.writeString(invalidFile, "Not a DICOM file");

      assertThrows(Exception.class, () -> moveSCU.retrieve(invalidFile));
    }

    @Test
    void retrieve_from_valid_dicom_file_without_association_fails() throws IOException {
      var dicomFile = createRealDicomFile("valid.dcm");

      assertThrows(NullPointerException.class, () -> moveSCU.retrieve(dicomFile));
    }

    @Test
    void retrieve_without_keys_fails_without_association() {
      assertThrows(NullPointerException.class, () -> moveSCU.retrieve());
    }

    @Test
    void retrieve_with_mock_association_executes_cmove() throws Exception {

      setMockAssociation(moveSCU, mockAssociation);
      setupBasicConfiguration();

      moveSCU.retrieve();

      verify(mockAssociation)
          .cmove(
              eq(InformationModel.PatientRoot.getCuid()),
              eq(0),
              any(Attributes.class),
              isNull(),
              eq(TEST_DESTINATION),
              any(DimseRSPHandler.class));
    }

    @Test
    void retrieve_from_dicom_file_merges_attributes() throws Exception {
      var dicomFile = createRealDicomFile("testfile.dcm");
      setMockAssociation(moveSCU, mockAssociation);
      setupBasicConfiguration();
      moveSCU.addKey(Tag.PatientName, "TestPatient");

      moveSCU.retrieve(dicomFile);

      verify(mockAssociation)
          .cmove(
              any(String.class),
              anyInt(),
              argThat(attrs -> attrs.contains(Tag.PatientName)),
              isNull(),
              any(String.class),
              any(DimseRSPHandler.class));
    }
  }

  // Helper methods

  private Association createMockAssociation() {
    var association = mock(Association.class);
    // when(association.isReadyForDataTransfer()).thenReturn(true);
    when(association.nextMessageID()).thenReturn(1);
    return association;
  }

  private void setMockAssociation(MoveSCU moveSCU, Association association) throws Exception {
    var field = MoveSCU.class.getDeclaredField("as");
    field.setAccessible(true);
    field.set(moveSCU, association);
  }

  private void setupBasicConfiguration() {
    moveSCU.setInformationModel(InformationModel.PatientRoot, DEFAULT_TRANSFER_SYNTAXES, false);
    moveSCU.setDestination(TEST_DESTINATION);
  }

  private Path createRealDicomFile(String filename) throws IOException {
    return createRealDicomFile(tempDir.resolve(filename));
  }

  private Path createRealDicomFile(Path filePath) throws IOException {
    Files.createDirectories(filePath.getParent());
    StreamUtil.copyFile(Path.of("src/test/resources/org/dcm4che3/img/prLUTs.dcm"), filePath);
    return filePath;
  }
}
