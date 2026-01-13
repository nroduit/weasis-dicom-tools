/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.tool.findscu;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.text.DecimalFormat;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.io.SAXWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.util.SafeClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

/**
 * A Service Class User (SCU) implementation for DICOM C-FIND operations.package
 * org.dcm4che3.tool.movescu;
 *
 * <p>import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;
 *
 * <p>import java.io.IOException; import java.nio.file.Files; import java.nio.file.Path; import
 * java.security.GeneralSecurityException; import java.util.concurrent.TimeUnit; import
 * org.dcm4che3.data.Attributes; import org.dcm4che3.data.Tag; import org.dcm4che3.data.UID; import
 * org.dcm4che3.data.VR; import org.dcm4che3.net.ApplicationEntity; import
 * org.dcm4che3.net.Association; import org.dcm4che3.net.Connection; import
 * org.dcm4che3.net.DimseRSPHandler; import org.dcm4che3.net.IncompatibleConnectionException; import
 * org.dcm4che3.net.pdu.AAssociateRQ; import org.dcm4che3.net.pdu.ExtendedNegotiation; import
 * org.dcm4che3.net.pdu.PresentationContext; import
 * org.dcm4che3.tool.movescu.MoveSCU.InformationModel; import org.junit.jupiter.api.BeforeEach;
 * import org.junit.jupiter.api.DisplayNameGeneration; import
 * org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores; import
 * org.junit.jupiter.api.Nested; import org.junit.jupiter.api.Test; import
 * org.junit.jupiter.api.io.TempDir; import org.junit.jupiter.api.extension.ExtendWith; import
 * org.mockito.junit.jupiter.MockitoExtension; import
 * org.weasis.dicom.param.DicomProgress; @DisplayNameGeneration(ReplaceUnderscores.class) @ExtendWith(MockitoExtension.class)
 * class MoveSCUTest {
 *
 * <p>private static final String[] DEFAULT_TRANSFER_SYNTAXES = { UID.ExplicitVRLittleEndian,
 * UID.ImplicitVRLittleEndian };
 *
 * <p>private static final String TEST_DESTINATION = "STORESCP"; private static final String
 * TEST_STUDY_UID = "1.2.3.4.5.6.7.8.9"; private static final String TEST_PATIENT_ID =
 * "12345"; @TempDir Path tempDir;
 *
 * <p>private MoveSCU moveSCU; private DicomProgress mockProgress; @BeforeEach void setUp() throws
 * IOException { mockProgress = mock(DicomProgress.class); moveSCU = new MoveSCU(mockProgress);
 * } @Nested class Construction { @Test void creates_instance_without_progress() throws IOException
 * { var scu = new MoveSCU();
 *
 * <p>assertNotNull(scu); assertNotNull(scu.getApplicationEntity());
 * assertNotNull(scu.getConnection()); assertNotNull(scu.getRemoteConnection());
 * assertNotNull(scu.getAAssociateRQ()); assertNotNull(scu.getKeys());
 * assertNotNull(scu.getState()); assertEquals("MOVESCU", scu.getApplicationEntity().getAETitle());
 * } @Test void creates_instance_with_progress() throws IOException { var progress =
 * mock(DicomProgress.class); var scu = new MoveSCU(progress);
 *
 * <p>assertNotNull(scu); assertSame(progress, scu.getState().getProgress()); } @Test void
 * initializes_default_values() { assertEquals("MOVESCU",
 * moveSCU.getApplicationEntity().getAETitle()); assertNotNull(moveSCU.getKeys());
 * assertTrue(moveSCU.getKeys().isEmpty()); assertNull(moveSCU.getAssociation()); } } @Nested class
 * Configuration { @Test void sets_priority() { var priority = 1;
 *
 * <p>moveSCU.setPriority(priority);
 *
 * <p>// Priority is used internally, verify through behavior assertDoesNotThrow(() ->
 * moveSCU.setPriority(priority)); } @Test void sets_cancel_after() { var cancelAfter = 5000;
 *
 * <p>moveSCU.setCancelAfter(cancelAfter);
 *
 * <p>assertDoesNotThrow(() -> moveSCU.setCancelAfter(cancelAfter)); } @Test void
 * sets_release_eager() { moveSCU.setReleaseEager(true); moveSCU.setReleaseEager(false);
 *
 * <p>assertDoesNotThrow(() -> moveSCU.setReleaseEager(true)); } @Test void sets_destination() {
 * moveSCU.setDestination(TEST_DESTINATION);
 *
 * <p>// Destination is used internally, verify through behavior assertDoesNotThrow(() ->
 * moveSCU.setDestination(TEST_DESTINATION)); } @Test void sets_input_filter() { var customFilter =
 * new int[]{Tag.StudyInstanceUID, Tag.SeriesInstanceUID};
 *
 * <p>moveSCU.setInputFilter(customFilter);
 *
 * <p>assertDoesNotThrow(() -> moveSCU.setInputFilter(customFilter)); } @Test void
 * sets_input_filter_with_null_uses_default() { moveSCU.setInputFilter(null);
 *
 * <p>assertDoesNotThrow(() -> moveSCU.setInputFilter(null)); } } @Nested class
 * Information_Model_Configuration { @Test void sets_patient_root_model_without_relational() {
 * moveSCU.setInformationModel( InformationModel.PatientRoot, DEFAULT_TRANSFER_SYNTAXES, false );
 *
 * <p>var rq = moveSCU.getAAssociateRQ(); assertFalse(rq.getPresentationContexts().isEmpty());
 *
 * <p>var pc = rq.getPresentationContexts().get(0);
 * assertEquals(InformationModel.PatientRoot.getCuid(), pc.getAbstractSyntax());
 * assertEquals("STUDY", moveSCU.getKeys().getString(Tag.QueryRetrieveLevel)); } @Test void
 * sets_study_root_model_with_relational() { moveSCU.setInformationModel(
 * InformationModel.StudyRoot, DEFAULT_TRANSFER_SYNTAXES, true );
 *
 * <p>var rq = moveSCU.getAAssociateRQ(); var extNeg =
 * rq.getExtendedNegotiationFor(InformationModel.StudyRoot.getCuid()); assertNotNull(extNeg);
 * assertEquals("STUDY", moveSCU.getKeys().getString(Tag.QueryRetrieveLevel)); } @Test void
 * sets_composite_instance_root_model() { moveSCU.setInformationModel(
 * InformationModel.CompositeInstanceRoot, DEFAULT_TRANSFER_SYNTAXES, false );
 *
 * <p>assertEquals("IMAGE", moveSCU.getKeys().getString(Tag.QueryRetrieveLevel)); } @Test void
 * sets_hanging_protocol_model_without_level() { moveSCU.setInformationModel(
 * InformationModel.HangingProtocol, DEFAULT_TRANSFER_SYNTAXES, false );
 *
 * <p>assertNull(moveSCU.getKeys().getString(Tag.QueryRetrieveLevel)); } @Test void
 * adds_custom_level() { var customLevel = "SERIES";
 *
 * <p>moveSCU.addLevel(customLevel);
 *
 * <p>assertEquals(customLevel, moveSCU.getKeys().getString(Tag.QueryRetrieveLevel)); } } @Nested
 * class Query_Keys { @Test void adds_single_key_with_value() { moveSCU.addKey(Tag.StudyInstanceUID,
 * TEST_STUDY_UID);
 *
 * <p>assertEquals(TEST_STUDY_UID, moveSCU.getKeys().getString(Tag.StudyInstanceUID)); } @Test void
 * adds_key_with_multiple_values() { var values = new String[]{"VALUE1", "VALUE2", "VALUE3"};
 *
 * <p>moveSCU.addKey(Tag.StudyDescription, values);
 *
 * <p>var storedValues = moveSCU.getKeys().getStrings(Tag.StudyDescription);
 * assertArrayEquals(values, storedValues); } @Test void adds_key_without_values() {
 * moveSCU.addKey(Tag.PatientName);
 *
 * <p>assertTrue(moveSCU.getKeys().contains(Tag.PatientName));
 * assertNull(moveSCU.getKeys().getString(Tag.PatientName)); } @Test void
 * adds_multiple_different_keys() { moveSCU.addKey(Tag.StudyInstanceUID, TEST_STUDY_UID);
 * moveSCU.addKey(Tag.PatientID, TEST_PATIENT_ID); moveSCU.addKey(Tag.Modality, "CT");
 *
 * <p>var keys = moveSCU.getKeys(); assertEquals(TEST_STUDY_UID,
 * keys.getString(Tag.StudyInstanceUID)); assertEquals(TEST_PATIENT_ID,
 * keys.getString(Tag.PatientID)); assertEquals("CT", keys.getString(Tag.Modality)); } } @Nested
 * class Information_Model_Enum { @Test void patient_root_has_correct_properties() { var model =
 * InformationModel.PatientRoot;
 *
 * <p>assertEquals(UID.PatientRootQueryRetrieveInformationModelMove, model.getCuid()); } @Test void
 * study_root_has_correct_properties() { var model = InformationModel.StudyRoot;
 *
 * <p>assertEquals(UID.StudyRootQueryRetrieveInformationModelMove, model.getCuid()); } @Test void
 * composite_instance_root_has_correct_properties() { var model =
 * InformationModel.CompositeInstanceRoot;
 *
 * <p>assertEquals(UID.CompositeInstanceRootRetrieveMove, model.getCuid()); } @Test void
 * hanging_protocol_has_correct_properties() { var model = InformationModel.HangingProtocol;
 *
 * <p>assertEquals(UID.HangingProtocolInformationModelMove, model.getCuid()); } @Test void
 * color_palette_has_correct_properties() { var model = InformationModel.ColorPalette;
 *
 * <p>assertEquals(UID.ColorPaletteQueryRetrieveInformationModelMove, model.getCuid()); } @Test void
 * all_models_have_non_null_cuid() { for (var model : InformationModel.values()) {
 * assertNotNull(model.getCuid()); assertFalse(model.getCuid().isEmpty()); } } } @Nested class
 * Association_Management { @Test void open_throws_exception_without_remote_configuration() {
 * assertThrows(Exception.class, () -> moveSCU.open()); } @Test void
 * close_handles_null_association() { assertDoesNotThrow(() -> moveSCU.close()); } @Test void
 * close_handles_association_not_ready() throws Exception { var mockAssociation =
 * createMockAssociation(); when(mockAssociation.isReadyForDataTransfer()).thenReturn(false);
 * setMockAssociation(mockAssociation);
 *
 * <p>assertDoesNotThrow(() -> moveSCU.close()); } @Test void
 * close_waits_for_outstanding_rsp_and_releases() throws Exception { var mockAssociation =
 * createMockAssociation(); when(mockAssociation.isReadyForDataTransfer()).thenReturn(true);
 * setMockAssociation(mockAssociation);
 *
 * <p>moveSCU.close();
 *
 * <p>verify(mockAssociation).waitForOutstandingRSP(); verify(mockAssociation).release(); }
 * } @Nested class DICOM_File_Retrieval { @Test void
 * retrieve_from_file_throws_exception_for_non_existent_file() { var nonExistentFile =
 * tempDir.resolve("non-existent.dcm");
 *
 * <p>assertThrows(IOException.class, () -> moveSCU.retrieve(nonExistentFile)); } @Test void
 * retrieve_from_file_throws_exception_for_invalid_dicom_file() throws IOException { var invalidFile
 * = tempDir.resolve("invalid.dcm"); Files.writeString(invalidFile, "Not a DICOM file");
 *
 * <p>assertThrows(Exception.class, () -> moveSCU.retrieve(invalidFile)); } @Test void
 * retrieve_from_valid_dicom_file_without_association_fails() throws IOException { var dicomFile =
 * createValidDicomFile();
 *
 * <p>assertThrows(NullPointerException.class, () -> moveSCU.retrieve(dicomFile)); } @Test void
 * retrieve_without_keys_fails_without_association() { assertThrows(NullPointerException.class, ()
 * -> moveSCU.retrieve()); } @Test void retrieve_with_mock_association_executes_cmove() throws
 * Exception { var mockAssociation = createMockAssociation(); setMockAssociation(mockAssociation);
 * setupBasicConfiguration();
 *
 * <p>moveSCU.retrieve();
 *
 * <p>verify(mockAssociation).cmove( eq(InformationModel.PatientRoot.getCuid()), eq(0),
 * any(Attributes.class), isNull(), eq(TEST_DESTINATION), any(DimseRSPHandler.class) ); } @Test void
 * retrieve_from_dicom_file_merges_attributes() throws Exception { var dicomFile =
 * createValidDicomFile(); var mockAssociation = createMockAssociation();
 * setMockAssociation(mockAssociation); setupBasicConfiguration(); moveSCU.addKey(Tag.PatientName,
 * "TestPatient");
 *
 * <p>moveSCU.retrieve(dicomFile);
 *
 * <p>verify(mockAssociation).cmove( any(String.class), anyInt(), argThat(attrs ->
 * attrs.contains(Tag.PatientName)), isNull(), any(String.class), any(DimseRSPHandler.class) ); }
 * } @Nested class Progress_Handling { @Test void handles_progress_cancellation() throws Exception {
 * when(mockProgress.isCancel()).thenReturn(true); var mockAssociation = createMockAssociation();
 * setMockAssociation(mockAssociation); setupBasicConfiguration();
 *
 * <p>// This should not throw an exception even with cancellation assertDoesNotThrow(() ->
 * moveSCU.retrieve()); } @Test void handles_null_progress() throws Exception { var
 * scuWithoutProgress = new MoveSCU(); var mockAssociation = createMockAssociation(); // Use
 * reflection to set the association for testing var field = MoveSCU.class.getDeclaredField("as");
 * field.setAccessible(true); field.set(scuWithoutProgress, mockAssociation);
 *
 * <p>scuWithoutProgress.setInformationModel( InformationModel.PatientRoot,
 * DEFAULT_TRANSFER_SYNTAXES, false ); scuWithoutProgress.setDestination(TEST_DESTINATION);
 *
 * <p>assertDoesNotThrow(() -> scuWithoutProgress.retrieve()); } }
 *
 * <p>// Helper methods
 *
 * <p>private Association createMockAssociation() { var association = mock(Association.class);
 * when(association.isReadyForDataTransfer()).thenReturn(true);
 * when(association.nextMessageID()).thenReturn(1); return association; }
 *
 * <p>private void setMockAssociation(Association mockAssociation) throws Exception { var field =
 * MoveSCU.class.getDeclaredField("as"); field.setAccessible(true); field.set(moveSCU,
 * mockAssociation); }
 *
 * <p>private void setupBasicConfiguration() { moveSCU.setInformationModel(
 * InformationModel.PatientRoot, DEFAULT_TRANSFER_SYNTAXES, false );
 * moveSCU.setDestination(TEST_DESTINATION); }
 *
 * <p>private Path createValidDicomFile() throws IOException { var dicomFile =
 * tempDir.resolve("test.dcm");
 *
 * <p>// Create a minimal valid DICOM file structure for testing var dicomContent =
 * createMinimalDicomContent(); Files.write(dicomFile, dicomContent);
 *
 * <p>return dicomFile; }
 *
 * <p>private byte[] createMinimalDicomContent() { // Create a minimal valid DICOM file structure
 * var content = new byte[132 + 100]; // Preamble + DICM + minimal dataset
 *
 * <p>// DICOM file preamble (128 bytes of zeros) for (int i = 0; i < 128; i++) { content[i] = 0; }
 *
 * <p>// DICM prefix content[128] = 'D'; content[129] = 'I'; content[130] = 'C'; content[131] = 'M';
 *
 * <p>// Add minimal file meta information // This is a very simplified structure that might work
 * with DicomInputStream // File Meta Information Group Length (0002,0000) content[132] = 0x02; //
 * Group content[133] = 0x00; content[134] = 0x00; // Element content[135] = 0x00; content[136] =
 * 'U'; // VR: UL content[137] = 'L'; content[138] = 0x00; // Reserved content[139] = 0x00;
 * content[140] = 0x04; // Length content[141] = 0x00; content[142] = 0x00; content[143] = 0x00;
 * content[144] = 0x00; // Value: 0 (we'll set this correctly if needed) content[145] = 0x00;
 * content[146] = 0x00; content[147] = 0x00;
 *
 * <p>return content; } }
 *
 * <p>Supports Query/Retrieve, Modality Worklist Management, Unified Worklist and Procedure Step,
 * Hanging Protocol Query/Retrieve, and Color Palette Query/Retrieve service classes. The
 * application sends query keys to a Service Class Provider (SCP) and processes responses.
 *
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Nicolas Roduit
 */
public class FindSCU implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(FindSCU.class);

  /** Supported DICOM information models for C-FIND operations. */
  public enum InformationModel {
    PatientRoot(UID.PatientRootQueryRetrieveInformationModelFind, "STUDY"),
    StudyRoot(UID.StudyRootQueryRetrieveInformationModelFind, "STUDY"),
    PatientStudyOnly(UID.PatientStudyOnlyQueryRetrieveInformationModelFind, "STUDY"),
    MWL(UID.ModalityWorklistInformationModelFind, null),
    UPSPull(UID.UnifiedProcedureStepPull, null),
    UPSWatch(UID.UnifiedProcedureStepWatch, null),
    UPSQuery(UID.UnifiedProcedureStepQuery, null),
    HangingProtocol(UID.HangingProtocolInformationModelFind, null),
    ColorPalette(UID.ColorPaletteQueryRetrieveInformationModelFind, null);

    final String cuid;
    final String level;

    InformationModel(String cuid, String level) {
      this.cuid = cuid;
      this.level = level;
    }

    public void adjustQueryOptions(EnumSet<QueryOption> queryOptions) {
      if (level == null) {
        queryOptions.add(QueryOption.RELATIONAL);
        queryOptions.add(QueryOption.DATETIME);
      }
    }

    public String getCuid() {
      return cuid;
    }
  }

  private SAXTransformerFactory saxtf;

  private final Device device = new Device("findscu");
  private final ApplicationEntity ae = new ApplicationEntity("FINDSCU");
  private final Connection conn = new Connection();
  private final Connection remote = new Connection();
  private final AAssociateRQ rq = new AAssociateRQ();

  // Core DICOM operation parameters
  private int priority;
  private int cancelAfter;
  private InformationModel model;

  private final Attributes keys = new Attributes();
  private int[] inFilter;

  // Output configuration
  private Path outDir;
  private DecimalFormat outFileFormat;
  private boolean catOut = false;

  // XML output configuration
  private boolean xml = false;
  private boolean xmlIndent = false;
  private boolean xmlIncludeKeyword = true;
  private boolean xmlIncludeNamespaceDeclaration = false;
  private Path xsltFile;
  private Templates xsltTpls;

  // Runtime state
  private Association as;
  private OutputStream out;

  private final AtomicInteger totNumMatches = new AtomicInteger();

  private final DicomState state;

  public FindSCU() {
    device.addConnection(conn);
    device.addApplicationEntity(ae);
    ae.addConnection(conn);
    state = new DicomState(new DicomProgress());
  }

  public final void setPriority(int priority) {
    this.priority = priority;
  }

  public final void setInformationModel(
      InformationModel model, String[] tss, EnumSet<QueryOption> queryOptions) {
    this.model = model;
    rq.addPresentationContext(new PresentationContext(1, model.cuid, tss));
    if (!queryOptions.isEmpty()) {
      model.adjustQueryOptions(queryOptions);
      rq.addExtendedNegotiation(
          new ExtendedNegotiation(
              model.cuid, QueryOption.toExtendedNegotiationInformation(queryOptions)));
    }
    if (model.level != null) {
      addLevel(model.level);
    }
  }

  public void addLevel(String level) {
    keys.setString(Tag.QueryRetrieveLevel, VR.CS, level);
  }

  public final void setCancelAfter(int cancelAfter) {
    this.cancelAfter = cancelAfter;
  }

  public final void setOutputDirectory(Path outDir) {
    this.outDir = outDir;
  }

  public final void setOutputFileFormat(String outFileFormat) {
    this.outFileFormat = new DecimalFormat(outFileFormat);
  }

  public final void setXSLT(Path xsltFile) {
    this.xsltFile = xsltFile;
  }

  public final void setXML(boolean xml) {
    this.xml = xml;
  }

  public final void setXMLIndent(boolean indent) {
    this.xmlIndent = indent;
  }

  public final void setXMLIncludeKeyword(boolean includeKeyword) {
    this.xmlIncludeKeyword = includeKeyword;
  }

  public final void setXMLIncludeNamespaceDeclaration(boolean includeNamespaceDeclaration) {
    this.xmlIncludeNamespaceDeclaration = includeNamespaceDeclaration;
  }

  public final void setConcatenateOutputFiles(boolean catOut) {
    this.catOut = catOut;
  }

  public final void setInputFilter(int[] inFilter) {
    this.inFilter = inFilter;
  }

  // Getters
  public ApplicationEntity getApplicationEntity() {
    return ae;
  }

  public Connection getRemoteConnection() {
    return remote;
  }

  public AAssociateRQ getAAssociateRQ() {
    return rq;
  }

  public Association getAssociation() {
    return as;
  }

  public Device getDevice() {
    return device;
  }

  public Attributes getKeys() {
    return keys;
  }

  public Connection getConnection() {
    return conn;
  }

  public DicomState getState() {
    return state;
  }

  public void open()
      throws IOException,
          InterruptedException,
          IncompatibleConnectionException,
          GeneralSecurityException {
    as = ae.connect(conn, remote, rq);
  }

  @Override
  public void close() throws IOException, InterruptedException {
    if (as != null && as.isReadyForDataTransfer()) {
      as.waitForOutstandingRSP();
      as.release();
    }
    SafeClose.close(out);
    out = null;
  }

  /** Queries with attributes loaded from file. */
  public void query(Path filePath) throws Exception {
    var attrs = loadAttributesFromFile(filePath);
    attrs = applyInputFilter(attrs);
    mergeKeys(attrs, keys);
    query(attrs);
  }

  /** Queries with current key attributes. */
  public void query() throws IOException, InterruptedException {
    query(keys);
  }

  /** Queries with custom response handler. */
  public void query(DimseRSPHandler rspHandler) throws IOException, InterruptedException {
    query(keys, rspHandler);
  }

  private void query(Attributes keys) throws IOException, InterruptedException {
    var rspHandler = createResponseHandler();
    query(keys, rspHandler);
  }

  private void query(Attributes keys, DimseRSPHandler rspHandler)
      throws IOException, InterruptedException {
    as.cfind(model.cuid, priority, keys, null, rspHandler);
  }

  private Attributes loadAttributesFromFile(Path filePath) throws Exception {
    var fileName = filePath.getFileName().toString().toLowerCase();

    if (fileName.endsWith(".xml")) {
      return SAXReader.parse(filePath.toString());
    } else {
      try (var dis = new DicomInputStream(filePath.toFile())) {
        return dis.readDataset();
      }
    }
  }

  private Attributes applyInputFilter(Attributes attrs) {
    if (inFilter == null) {
      return attrs;
    }

    var filtered = new Attributes(inFilter.length + 1);
    filtered.addSelected(attrs, inFilter);
    return filtered;
  }

  private DimseRSPHandler createResponseHandler() {
    return new DimseRSPHandler(as.nextMessageID()) {
      int cancelAfter = FindSCU.this.cancelAfter;
      int numMatches;

      @Override
      public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
        super.onDimseRSP(as, cmd, data);
        int status = cmd.getInt(Tag.Status, -1);
        if (Status.isPending(status)) {
          FindSCU.this.onResult(data);
          ++numMatches;
          if (shouldCancel(numMatches)) {
            cancelQuery(as);
          }
        } else {
          state.setStatus(status);
        }
      }

      private boolean shouldCancel(int numMatches) {
        return cancelAfter != 0 && numMatches >= cancelAfter;
      }

      private void cancelQuery(Association as) {
        try {
          cancel(as);
          cancelAfter = 0;
        } catch (IOException e) {
          LOGGER.error("Failed to cancel query", e);
        }
      }
    };
  }

  private void onResult(Attributes data) {
    state.addDicomRSP(data);
    int numMatches = totNumMatches.incrementAndGet();
    if (outDir == null) {
      return;
    }

    try {
      ensureOutputStreamReady(numMatches);
      writeResult(data);
      out.flush();
    } catch (Exception e) {
      LOGGER.error("Failed to write result", e);
      closeOutputStream();
    } finally {
      if (!catOut) {
        closeOutputStream();
      }
    }
  }

  private void ensureOutputStreamReady(int numMatches) throws IOException {
    if (out == null) {
      Files.createDirectories(outDir);
      var outputFile = outDir.resolve(generateFileName(numMatches));
      out = new BufferedOutputStream(Files.newOutputStream(outputFile));
    }
  }

  private void writeResult(Attributes data) throws Exception {
    if (xml) {
      writeAsXML(data, out);
    } else {
      var dos =
          new DicomOutputStream(out, UID.ImplicitVRLittleEndian); // NOSONAR - managed by 'out'
      dos.writeDataset(null, data);
    }
  }

  private void closeOutputStream() {
    SafeClose.close(out);
    out = null;
  }

  private String generateFileName(int sequenceNumber) {
    var formatter = outFileFormat;
    if (formatter == null) {
      return String.format("%07d", sequenceNumber);
    }

    synchronized (formatter) {
      return formatter.format(sequenceNumber);
    }
  }

  private void writeAsXML(Attributes attrs, OutputStream out) throws Exception {
    var th = getTransformerHandler();
    th.getTransformer().setOutputProperty(OutputKeys.INDENT, xmlIndent ? "yes" : "no");
    th.setResult(new StreamResult(out));
    var saxWriter = new SAXWriter(th);
    saxWriter.setIncludeKeyword(xmlIncludeKeyword);
    saxWriter.setIncludeNamespaceDeclaration(xmlIncludeNamespaceDeclaration);
    saxWriter.write(attrs);
  }

  private TransformerHandler getTransformerHandler() throws Exception {
    var tf = getOrCreateSaxTransformerFactory();
    return xsltTpls != null ? tf.newTransformerHandler(xsltTpls) : tf.newTransformerHandler();
  }

  private SAXTransformerFactory getOrCreateSaxTransformerFactory() throws Exception {
    if (saxtf == null) {
      saxtf = (SAXTransformerFactory) TransformerFactory.newInstance();
      saxtf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    }

    if (xsltFile != null && xsltTpls == null) {
      xsltTpls = saxtf.newTemplates(new StreamSource(xsltFile.toFile()));
    }

    return saxtf;
  }

  /** Merges key attributes with query attributes, handling nested sequences. */
  static void mergeKeys(Attributes attrs, Attributes keys) {
    try {
      attrs.accept(new MergeNested(keys), false);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to merge keys", e);
    }
    attrs.addAll(keys);
  }

  /** Visitor for merging nested sequence attributes. */
  private static class MergeNested implements Attributes.Visitor {
    private final Attributes keys;

    MergeNested(Attributes keys) {
      this.keys = keys;
    }

    @Override
    public boolean visit(Attributes attrs, int tag, VR vr, Object val) {
      if (isNotEmptySequence(val)) {
        var keyValue = keys.remove(tag);
        if (isNotEmptySequence(keyValue)) {
          ((Sequence) val).get(0).addAll(((Sequence) keyValue).get(0));
        }
      }
      return true;
    }

    private static boolean isNotEmptySequence(Object val) {
      return val instanceof Sequence sequence && !sequence.isEmpty();
    }
  }
}
