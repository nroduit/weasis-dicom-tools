/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.storescu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.DimseRSPHandler;
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
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.DicomProgress;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class StoreSCUTest {

  private static final String TEST_SOP_CLASS_UID = "1.2.840.10008.5.1.4.1.1.2";
  private static final String TEST_SOP_INSTANCE_UID = "1.2.3.4.5.6.7.8.9";
  private static final String TEST_TRANSFER_SYNTAX_UID = UID.ExplicitVRLittleEndian;
  private static final String TEST_AE_TITLE = "STORESCU";

  @TempDir Path tempDir;

  @Mock private DicomProgress mockProgress;
  @Mock private AttributeEditor mockEditor;
  @Mock private Association mockAssociation;
  @Mock private DimseRSP mockDimseRSP;

  private ApplicationEntity ae;
  private StoreSCU storeSCU;

  @BeforeEach
  void setUp() throws IOException {
    ae = new ApplicationEntity(TEST_AE_TITLE);
    storeSCU = new StoreSCU(ae, mockProgress);
    storeSCU.setTmpFileDirectory(tempDir);
  }

  @Nested
  class Construction {

    @Test
    void creates_instance_without_progress() throws IOException {
      var scu = new StoreSCU(ae, null);

      assertNotNull(scu);
      assertNotNull(scu.getAAssociateRQ());
      assertNotNull(scu.getState());
      assertNull(scu.getState().getProgress());
    }

    @Test
    void creates_instance_with_progress() throws IOException {
      var scu = new StoreSCU(ae, mockProgress);

      assertNotNull(scu);
      assertSame(mockProgress, scu.getState().getProgress());
    }

    @Test
    void creates_instance_with_dicom_editors() throws IOException {
      var editors = List.of(mockEditor);
      var scu = new StoreSCU(ae, mockProgress, editors);

      assertNotNull(scu);
      assertSame(mockProgress, scu.getState().getProgress());
    }

    @Test
    void initializes_default_presentation_context() throws IOException {
      var scu = new StoreSCU(ae, null);

      var rq = scu.getAAssociateRQ();
      assertEquals(1, rq.getNumberOfPresentationContexts());

      var pc = rq.getPresentationContext(1);
      assertEquals(UID.Verification, pc.getAbstractSyntax());
      assertEquals(UID.ImplicitVRLittleEndian, pc.getTransferSyntaxes()[0]);
    }
  }

  @Nested
  class Configuration {

    @Test
    void sets_priority() {
      var priority = 1;

      storeSCU.setPriority(priority);

      assertDoesNotThrow(() -> storeSCU.setPriority(priority));
    }

    @Test
    void sets_uid_suffix() {
      var uidSuffix = ".TEST";

      storeSCU.setUIDSuffix(uidSuffix);

      assertDoesNotThrow(() -> storeSCU.setUIDSuffix(uidSuffix));
    }

    @Test
    void sets_tmp_file_prefix() {
      var prefix = "test-prefix-";

      storeSCU.setTmpFilePrefix(prefix);

      assertDoesNotThrow(() -> storeSCU.setTmpFilePrefix(prefix));
    }

    @Test
    void sets_tmp_file_prefix_with_null() {
      storeSCU.setTmpFilePrefix(null);

      assertDoesNotThrow(() -> storeSCU.setTmpFilePrefix(null));
    }

    @Test
    void sets_tmp_file_suffix() {
      var suffix = ".tmp";

      storeSCU.setTmpFileSuffix(suffix);

      assertDoesNotThrow(() -> storeSCU.setTmpFileSuffix(suffix));
    }

    @Test
    void sets_tmp_file_directory() throws IOException {
      var tmpDir = tempDir.resolve("tmp");
      Files.createDirectories(tmpDir);
      assertDoesNotThrow(() -> storeSCU.setTmpFileDirectory(tmpDir));
    }

    @Test
    void sets_tmp_file_directory_with_null() {
      assertDoesNotThrow(() -> storeSCU.setTmpFileDirectory(null));
    }

    @Test
    void sets_tmp_file() {
      var tmpFile = tempDir.resolve("test.tmp");
      assertDoesNotThrow(() -> storeSCU.setTmpFile(tmpFile));
    }

    @Test
    void sets_tmp_file_with_null() {
      storeSCU.setTmpFile(null);

      assertDoesNotThrow(() -> storeSCU.setTmpFile(null));
    }

    @Test
    void enables_sop_class_relationship_ext_neg() {
      storeSCU.enableSOPClassRelationshipExtNeg(true);
      storeSCU.enableSOPClassRelationshipExtNeg(false);

      assertDoesNotThrow(() -> storeSCU.enableSOPClassRelationshipExtNeg(true));
    }

    @Test
    void sets_attributes() {
      var attrs = new Attributes();
      attrs.setString(Tag.PatientName, VR.PN, "Test Patient");

      storeSCU.setAttributes(attrs);

      assertEquals(attrs, storeSCU.getAttributes());
    }

    @Test
    void sets_rsp_handler_factory() {
      StoreSCU.RSPHandlerFactory factory = file -> mock(DimseRSPHandler.class);

      storeSCU.setRspHandlerFactory(factory);

      assertDoesNotThrow(() -> storeSCU.setRspHandlerFactory(factory));
    }

    @Test
    void sets_rsp_handler_factory_with_null() {
      storeSCU.setRspHandlerFactory(null);

      assertDoesNotThrow(() -> storeSCU.setRspHandlerFactory(null));
    }
  }

  @Nested
  class RelatedSOPClasses {

    @Test
    void initializes_related_sop_classes() {
      var props = new Properties();
      props.setProperty(TEST_SOP_CLASS_UID, "1.2.3,4.5.6");

      storeSCU.relSOPClasses.init(props);

      var negotiation = storeSCU.relSOPClasses.getCommonExtendedNegotiation(TEST_SOP_CLASS_UID);
      assertNotNull(negotiation);
      assertEquals(TEST_SOP_CLASS_UID, negotiation.getSOPClassUID());
    }

    @Test
    void handles_null_properties() {
      assertDoesNotThrow(() -> storeSCU.relSOPClasses.init(null));
      assertTrue(storeSCU.relSOPClasses.isEmpty());
    }

    @Test
    void returns_default_negotiation_for_unknown_sop_class() {
      var unknownSopClass = "1.2.3.4.5.6.7.8.9.10";

      var negotiation = storeSCU.relSOPClasses.getCommonExtendedNegotiation(unknownSopClass);

      assertNotNull(negotiation);
      assertEquals(unknownSopClass, negotiation.getSOPClassUID());
    }
  }

  @Nested
  class FileScanning {

    @Test
    void scans_files_with_default_printout() throws IOException {
      var testFile = createRealDicomFile("test.dcm");
      var fileNames = List.of(testFile.toString());

      storeSCU.scanFiles(fileNames);

      assertEquals(1, storeSCU.getFilesScanned());
    }

    @Test
    void scans_files_without_printout() throws IOException {
      var testFile = createRealDicomFile("test.dcm");
      var fileNames = List.of(testFile.toString());

      storeSCU.scanFiles(fileNames, false);

      assertEquals(1, storeSCU.getFilesScanned());
    }

    @Test
    void scans_directory() throws IOException {
      createRealDicomFile("test1.dcm");
      createRealDicomFile("test2.dcm");

      storeSCU.scanFiles(List.of(tempDir.toString()));

      assertEquals(2, storeSCU.getFilesScanned());
    }

    @Test
    void handles_non_dicom_files_gracefully() throws IOException {
      var textFile = tempDir.resolve("test.txt");
      Files.writeString(textFile, "This is not a DICOM file");

      storeSCU.scanFiles(List.of(textFile.toString()), false);

      assertEquals(0, storeSCU.getFilesScanned());
    }

    @Test
    void handles_non_existent_files() throws IOException {
      var nonExistentFile = tempDir.resolve("nonexistent.dcm");

      assertDoesNotThrow(() -> storeSCU.scanFiles(List.of(nonExistentFile.toString()), false));
      assertEquals(0, storeSCU.getFilesScanned());
    }
  }

  @Nested
  class FileAddition {

    @Test
    void adds_file_with_valid_attributes() throws IOException {
      var writer = new StringWriter();
      var file = createRealDicomFile("test.dcm");
      var fmi = createTestFileMetaInformation();

      var result = storeSCU.addFile(writer, file, 132, fmi);

      assertTrue(result);
      var output = writer.toString();
      assertTrue(output.contains(TEST_SOP_INSTANCE_UID));
      assertTrue(output.contains(TEST_SOP_CLASS_UID));
      assertTrue(output.contains(TEST_TRANSFER_SYNTAX_UID));
    }

    @Test
    void rejects_file_with_missing_sop_class_uid() throws IOException {
      var writer = new StringWriter();
      var file = createRealDicomFile("test.dcm");
      var fmi = new Attributes();
      fmi.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, TEST_SOP_INSTANCE_UID);
      fmi.setString(Tag.TransferSyntaxUID, VR.UI, TEST_TRANSFER_SYNTAX_UID);

      var result = storeSCU.addFile(writer, file, 132, fmi);

      assertFalse(result);
    }

    @Test
    void rejects_file_with_missing_sop_instance_uid() throws IOException {
      var writer = new StringWriter();
      var file = createRealDicomFile("test.dcm");
      var fmi = new Attributes();
      fmi.setString(Tag.MediaStorageSOPClassUID, VR.UI, TEST_SOP_CLASS_UID);
      fmi.setString(Tag.TransferSyntaxUID, VR.UI, TEST_TRANSFER_SYNTAX_UID);

      var result = storeSCU.addFile(writer, file, 132, fmi);

      assertFalse(result);
    }

    @Test
    void adds_presentation_contexts_for_new_sop_class() throws IOException {
      var writer = new StringWriter();
      var file = createRealDicomFile("test.dcm");
      var fmi = createTestFileMetaInformation();

      storeSCU.addFile(writer, file, 132, fmi);

      var rq = storeSCU.getAAssociateRQ();
      assertTrue(rq.containsPresentationContextFor(TEST_SOP_CLASS_UID));
    }
  }

  @Nested
  class StateTracking {

    @Test
    void tracks_files_scanned() throws IOException {
      var testFile = createRealDicomFile("test.dcm");

      storeSCU.scanFiles(List.of(testFile.toString()));

      assertEquals(1, storeSCU.getFilesScanned());
      assertTrue(storeSCU.getTotalSize() >= 0);
    }

    @Test
    void returns_dicom_state() {
      var state = storeSCU.getState();

      assertNotNull(state);
      assertSame(mockProgress, state.getProgress());
    }

    @Test
    void initializes_counters_to_zero() {
      assertEquals(0, storeSCU.getFilesScanned());
      assertEquals(0, storeSCU.getTotalSize());
    }
  }

  // Helper methods

  private Path createRealDicomFile(String filename) throws IOException {
    return createRealDicomFile(tempDir.resolve(filename));
  }

  private Path createRealDicomFile(Path filePath) throws IOException {
    Files.createDirectories(filePath.getParent());
    StreamUtil.copyFile(Path.of("src/test/resources/org/dcm4che3/img/prLUTs.dcm"), filePath);
    return filePath;
  }

  private Attributes createTestFileMetaInformation() {
    var fmi = new Attributes();
    fmi.setString(Tag.MediaStorageSOPClassUID, VR.UI, TEST_SOP_CLASS_UID);
    fmi.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, TEST_SOP_INSTANCE_UID);
    fmi.setString(Tag.TransferSyntaxUID, VR.UI, TEST_TRANSFER_SYNTAX_UID);
    fmi.setString(Tag.ImplementationClassUID, VR.UI, "1.2.3.4.5");
    fmi.setString(Tag.ImplementationVersionName, VR.SH, "Test");
    return fmi;
  }
}
