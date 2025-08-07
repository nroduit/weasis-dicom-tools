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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for document and request reference classes. */
@DisplayName("Document Reference Tests")
class DocumentReferenceTest {

  private static final String TEST_STUDY_UID = "1.2.3.4.5.6.7.8.9.100";
  private static final String TEST_ACCESSION_NUMBER = "ACC123456";
  private static final String TEST_PLACER_ORDER = "PO-789";
  private static final String TEST_FILLER_ORDER = "FO-012";
  private static final String TEST_PROCEDURE_ID = "PROC-345";
  private static final String TEST_PROCEDURE_DESC = "Chest CT with contrast";
  private static final String TEST_REASON = "Chest pain, rule out PE";

  @Nested
  @DisplayName("ReferencedRequest Tests")
  class ReferencedRequestTest {

    private ReferencedRequest referencedRequest;

    @BeforeEach
    void setUp() {
      referencedRequest = new ReferencedRequest();
    }

    @Test
    @DisplayName("Should create empty referenced request")
    void shouldCreateEmptyReferencedRequest() {
      ReferencedRequest empty = new ReferencedRequest();
      assertNotNull(empty);
      assertNotNull(empty.getAttributes());
    }

    @Test
    @DisplayName("Should create referenced request from attributes")
    void shouldCreateReferencedRequestFromAttributes() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.StudyInstanceUID, VR.UI, TEST_STUDY_UID);
      attrs.setString(Tag.AccessionNumber, VR.SH, TEST_ACCESSION_NUMBER);

      ReferencedRequest request = new ReferencedRequest(attrs);
      assertEquals(TEST_STUDY_UID, request.getStudyInstanceUID());
      assertEquals(TEST_ACCESSION_NUMBER, request.getAccessionNumber());
    }

    @Test
    @DisplayName("Should handle study instance UID correctly")
    void shouldHandleStudyInstanceUID() {
      referencedRequest.setStudyInstanceUID(TEST_STUDY_UID);
      assertEquals(TEST_STUDY_UID, referencedRequest.getStudyInstanceUID());

      referencedRequest.setStudyInstanceUID(null);
      assertNull(referencedRequest.getStudyInstanceUID());
    }

    @Test
    @DisplayName("Should handle accession number correctly")
    void shouldHandleAccessionNumber() {
      referencedRequest.setAccessionNumber(TEST_ACCESSION_NUMBER);
      assertEquals(TEST_ACCESSION_NUMBER, referencedRequest.getAccessionNumber());

      referencedRequest.setAccessionNumber(null);
      assertNull(referencedRequest.getAccessionNumber());
    }

    @Test
    @DisplayName("Should handle placer order number correctly")
    void shouldHandlePlacerOrderNumber() {
      referencedRequest.setPlacerOrderNumberImagingServiceRequest(TEST_PLACER_ORDER);
      assertEquals(
          TEST_PLACER_ORDER, referencedRequest.getPlacerOrderNumberImagingServiceRequest());

      referencedRequest.setPlacerOrderNumberImagingServiceRequest(null);
      assertNull(referencedRequest.getPlacerOrderNumberImagingServiceRequest());
    }

    @Test
    @DisplayName("Should handle filler order number correctly")
    void shouldHandleFillerOrderNumber() {
      referencedRequest.setFillerOrderNumberImagingServiceRequest(TEST_FILLER_ORDER);
      assertEquals(
          TEST_FILLER_ORDER, referencedRequest.getFillerOrderNumberImagingServiceRequest());

      referencedRequest.setFillerOrderNumberImagingServiceRequest(null);
      assertNull(referencedRequest.getFillerOrderNumberImagingServiceRequest());
    }

    @Test
    @DisplayName("Should handle requested procedure ID correctly")
    void shouldHandleRequestedProcedureID() {
      referencedRequest.setRequestedProcedureID(TEST_PROCEDURE_ID);
      assertEquals(TEST_PROCEDURE_ID, referencedRequest.getRequestedProcedureID());

      referencedRequest.setRequestedProcedureID(null);
      assertNull(referencedRequest.getRequestedProcedureID());
    }

    @Test
    @DisplayName("Should handle requested procedure description correctly")
    void shouldHandleRequestedProcedureDescription() {
      referencedRequest.setRequestedProcedureDescription(TEST_PROCEDURE_DESC);
      assertEquals(TEST_PROCEDURE_DESC, referencedRequest.getRequestedProcedureDescription());

      referencedRequest.setRequestedProcedureDescription(null);
      assertNull(referencedRequest.getRequestedProcedureDescription());
    }

    @Test
    @DisplayName("Should handle reason for requested procedure correctly")
    void shouldHandleReasonForRequestedProcedure() {
      referencedRequest.setReasonForTheRequestedProcedure(TEST_REASON);
      assertEquals(TEST_REASON, referencedRequest.getReasonForTheRequestedProcedure());

      referencedRequest.setReasonForTheRequestedProcedure(null);
      assertNull(referencedRequest.getReasonForTheRequestedProcedure());
    }

    @Test
    @DisplayName("Should handle referenced study SOP instance correctly")
    void shouldHandleReferencedStudySOPInstance() {
      SOPInstanceReference sopRef = new SOPInstanceReference();
      sopRef.setReferencedSOPInstanceUID("1.2.3.4.5.6.7.8.9.200");
      sopRef.setReferencedSOPClassUID("1.2.840.10008.5.1.4.1.1.2");

      referencedRequest.setReferencedStudySOPInstance(sopRef);

      SOPInstanceReference retrieved = referencedRequest.getReferencedStudySOPInstance();
      assertNotNull(retrieved);
      assertEquals("1.2.3.4.5.6.7.8.9.200", retrieved.getReferencedSOPInstanceUID());
      assertEquals("1.2.840.10008.5.1.4.1.1.2", retrieved.getReferencedSOPClassUID());

      // Test null handling
      referencedRequest.setReferencedStudySOPInstance(null);
      assertNull(referencedRequest.getReferencedStudySOPInstance());
    }

    @Test
    @DisplayName("Should handle requested procedure code correctly")
    void shouldHandleRequestedProcedureCode() {
      Attributes codeAttrs = new Attributes();
      codeAttrs.setString(Tag.CodeValue, VR.SH, "71020");
      codeAttrs.setString(Tag.CodingSchemeDesignator, VR.SH, "CPT");
      codeAttrs.setString(Tag.CodeMeaning, VR.LO, "Chest X-ray");
      Code procedureCode = new Code(codeAttrs);
      referencedRequest.setRequestedProcedureCode(procedureCode);

      Code retrieved = referencedRequest.getRequestedProcedureCode();
      assertNotNull(retrieved);
      assertEquals("71020", retrieved.getCodeValue());
      assertEquals("CPT", retrieved.getCodingSchemeDesignator());
      assertEquals("Chest X-ray", retrieved.getCodeMeaning());

      // Test null handling
      referencedRequest.setRequestedProcedureCode(null);
      assertNull(referencedRequest.getRequestedProcedureCode());
    }

    @Test
    @DisplayName("Should handle reason for requested procedure code correctly")
    void shouldHandleReasonForRequestedProcedureCode() {
      Attributes codeAttrs = new Attributes();
      codeAttrs.setString(Tag.CodeValue, VR.SH, "R06.02");
      codeAttrs.setString(Tag.CodingSchemeDesignator, VR.SH, "ICD-10-CM");
      codeAttrs.setString(Tag.CodeMeaning, VR.LO, "Shortness of breath");
      Code reasonCode = new Code(codeAttrs);
      referencedRequest.setReasonForRequestedProcedureCode(reasonCode);

      Code retrieved = referencedRequest.getReasonForRequestedProcedureCode();
      assertNotNull(retrieved);
      assertEquals("R06.02", retrieved.getCodeValue());
      assertEquals("ICD-10-CM", retrieved.getCodingSchemeDesignator());
      assertEquals("Shortness of breath", retrieved.getCodeMeaning());

      // Test null handling
      referencedRequest.setReasonForRequestedProcedureCode(null);
      assertNull(referencedRequest.getReasonForRequestedProcedureCode());
    }

    @Test
    @DisplayName("Should convert sequence to collection correctly")
    void shouldConvertSequenceToCollection() {
      // Test null sequence
      Collection<ReferencedRequest> result = ReferencedRequest.toReferencedRequestMacros(null);
      assertTrue(result.isEmpty());

      // Test empty sequence
      Attributes container = new Attributes();
      Sequence emptySeq = container.newSequence(Tag.ReferencedRequestSequence, 0);
      result = ReferencedRequest.toReferencedRequestMacros(emptySeq);
      assertTrue(result.isEmpty());

      // Test populated sequence
      Sequence seq = container.newSequence(Tag.ReferencedRequestSequence, 2);

      Attributes request1 = new Attributes();
      request1.setString(Tag.StudyInstanceUID, VR.UI, "1.1.1.1.1");
      request1.setString(Tag.AccessionNumber, VR.SH, "ACC001");
      seq.add(request1);

      Attributes request2 = new Attributes();
      request2.setString(Tag.StudyInstanceUID, VR.UI, "2.2.2.2.2");
      request2.setString(Tag.AccessionNumber, VR.SH, "ACC002");
      seq.add(request2);

      result = ReferencedRequest.toReferencedRequestMacros(seq);
      assertEquals(2, result.size());

      List<ReferencedRequest> requestList = result.stream().toList();
      assertEquals("1.1.1.1.1", requestList.get(0).getStudyInstanceUID());
      assertEquals("ACC001", requestList.get(0).getAccessionNumber());
      assertEquals("2.2.2.2.2", requestList.get(1).getStudyInstanceUID());
      assertEquals("ACC002", requestList.get(1).getAccessionNumber());
    }

    @Test
    @DisplayName("Should handle complete referenced request setup")
    void shouldHandleCompleteReferencedRequestSetup() {
      referencedRequest.setStudyInstanceUID(TEST_STUDY_UID);
      referencedRequest.setAccessionNumber(TEST_ACCESSION_NUMBER);
      referencedRequest.setPlacerOrderNumberImagingServiceRequest(TEST_PLACER_ORDER);
      referencedRequest.setFillerOrderNumberImagingServiceRequest(TEST_FILLER_ORDER);
      referencedRequest.setRequestedProcedureID(TEST_PROCEDURE_ID);
      referencedRequest.setRequestedProcedureDescription(TEST_PROCEDURE_DESC);
      referencedRequest.setReasonForTheRequestedProcedure(TEST_REASON);

      assertEquals(TEST_STUDY_UID, referencedRequest.getStudyInstanceUID());
      assertEquals(TEST_ACCESSION_NUMBER, referencedRequest.getAccessionNumber());
      assertEquals(
          TEST_PLACER_ORDER, referencedRequest.getPlacerOrderNumberImagingServiceRequest());
      assertEquals(
          TEST_FILLER_ORDER, referencedRequest.getFillerOrderNumberImagingServiceRequest());
      assertEquals(TEST_PROCEDURE_ID, referencedRequest.getRequestedProcedureID());
      assertEquals(TEST_PROCEDURE_DESC, referencedRequest.getRequestedProcedureDescription());
      assertEquals(TEST_REASON, referencedRequest.getReasonForTheRequestedProcedure());
    }
  }

  @Nested
  @DisplayName("DigitalSignatures Tests")
  class DigitalSignaturesTest {

    private DigitalSignatures digitalSignatures;
    private static final int TEST_MAC_ID = 12345;
    private static final String TEST_SIGNATURE_UID = "1.2.3.4.5.6.7.8.9.1000";
    private static final String TEST_CERT_TYPE = "X509_1993_SIG";
    private static final String TEST_TIMESTAMP_TYPE = "RFC3161";
    private static final byte[] TEST_CERTIFICATE = {
      0x30, (byte) 0x82, 0x02, 0x4C, 0x30, (byte) 0x82, 0x01, 0x55
    };
    private static final byte[] TEST_SIGNATURE = {0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48};
    private static final byte[] TEST_TIMESTAMP = {0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57};

    @BeforeEach
    void setUp() {
      digitalSignatures = new DigitalSignatures();
    }

    @Test
    @DisplayName("Should create empty digital signatures")
    void shouldCreateEmptyDigitalSignatures() {
      DigitalSignatures empty = new DigitalSignatures();
      assertNotNull(empty);
      assertNotNull(empty.getAttributes());
    }

    @Test
    @DisplayName("Should create digital signatures from attributes")
    void shouldCreateDigitalSignaturesFromAttributes() {
      Attributes attrs = new Attributes();
      attrs.setInt(Tag.MACIDNumber, VR.US, TEST_MAC_ID);
      attrs.setString(Tag.DigitalSignatureUID, VR.UI, TEST_SIGNATURE_UID);

      DigitalSignatures signatures = new DigitalSignatures(attrs);
      assertEquals(TEST_MAC_ID, signatures.getMACIDNumber());
      assertEquals(TEST_SIGNATURE_UID, signatures.getDigitalSignatureUID());
    }

    @Test
    @DisplayName("Should handle MAC ID number correctly")
    void shouldHandleMACIDNumber() {
      // Test default value
      DigitalSignatures defaultSigs = new DigitalSignatures();
      assertEquals(-1, defaultSigs.getMACIDNumber());

      // Test setting and getting
      digitalSignatures.setMACIDNumber(TEST_MAC_ID);
      assertEquals(TEST_MAC_ID, digitalSignatures.getMACIDNumber());

      // Test with zero
      digitalSignatures.setMACIDNumber(0);
      assertEquals(0, digitalSignatures.getMACIDNumber());

      // Test with maximum valid value
      digitalSignatures.setMACIDNumber(65535);
      assertEquals(65535, digitalSignatures.getMACIDNumber());
    }

    @Test
    @DisplayName("Should handle digital signature UID correctly")
    void shouldHandleDigitalSignatureUID() {
      // Initially should be null
      assertNull(digitalSignatures.getDigitalSignatureUID());

      // Test setting and getting
      digitalSignatures.setDigitalSignatureUID(TEST_SIGNATURE_UID);
      assertEquals(TEST_SIGNATURE_UID, digitalSignatures.getDigitalSignatureUID());

      // Test null handling
      digitalSignatures.setDigitalSignatureUID(null);
      assertNull(digitalSignatures.getDigitalSignatureUID());
    }

    @Test
    @DisplayName("Should handle digital signature date time correctly")
    void shouldHandleDigitalSignatureDateTime() {
      // Initially should be null
      assertNull(digitalSignatures.getDigitalSignatureDateTime());

      // Test setting and getting
      Date testDate = new Date();
      digitalSignatures.setDigitalSignatureDateTime(testDate);
      assertEquals(testDate, digitalSignatures.getDigitalSignatureDateTime());
    }

    @Test
    @DisplayName("Should handle certificate type correctly")
    void shouldHandleCertificateType() {
      // Initially should be null
      assertNull(digitalSignatures.getCertificateType());

      // Test setting and getting
      digitalSignatures.setCertificateType(TEST_CERT_TYPE);
      assertEquals(TEST_CERT_TYPE, digitalSignatures.getCertificateType());

      // Test null handling
      digitalSignatures.setCertificateType(null);
      assertNull(digitalSignatures.getCertificateType());
    }

    @Test
    @DisplayName("Should handle certificate of signer correctly")
    void shouldHandleCertificateOfSigner() throws IOException {
      // Initially should be null
      assertNull(digitalSignatures.getCertificateOfSigner());

      // Test setting and getting
      digitalSignatures.setCertificateOfSigner(TEST_CERTIFICATE);
      assertArrayEquals(TEST_CERTIFICATE, digitalSignatures.getCertificateOfSigner());

      // Test empty array
      byte[] emptyArray = new byte[0];
      digitalSignatures.setCertificateOfSigner(emptyArray);
      assertArrayEquals(emptyArray, digitalSignatures.getCertificateOfSigner());
    }

    @Test
    @DisplayName("Should handle signature correctly")
    void shouldHandleSignature() throws IOException {
      // Initially should be null
      assertNull(digitalSignatures.getSignature());

      // Test setting and getting
      digitalSignatures.setSignature(TEST_SIGNATURE);
      assertArrayEquals(TEST_SIGNATURE, digitalSignatures.getSignature());

      // Test empty array
      byte[] emptyArray = new byte[0];
      digitalSignatures.setSignature(emptyArray);
      assertArrayEquals(emptyArray, digitalSignatures.getSignature());
    }

    @Test
    @DisplayName("Should handle certified timestamp type correctly")
    void shouldHandleCertifiedTimestampType() {
      // Initially should be null
      assertNull(digitalSignatures.getCertifiedTimestampType());

      // Test setting and getting
      digitalSignatures.setCertifiedTimestampType(TEST_TIMESTAMP_TYPE);
      assertEquals(TEST_TIMESTAMP_TYPE, digitalSignatures.getCertifiedTimestampType());

      // Test null handling
      digitalSignatures.setCertifiedTimestampType(null);
      assertNull(digitalSignatures.getCertifiedTimestampType());
    }

    @Test
    @DisplayName("Should handle certified timestamp correctly")
    void shouldHandleCertifiedTimestamp() throws IOException {
      // Initially should be null
      assertNull(digitalSignatures.getCertifiedTimestamp());

      // Test setting and getting
      digitalSignatures.setCertifiedTimestamp(TEST_TIMESTAMP);
      assertArrayEquals(TEST_TIMESTAMP, digitalSignatures.getCertifiedTimestamp());

      // Test empty array
      byte[] emptyArray = new byte[0];
      digitalSignatures.setCertifiedTimestamp(emptyArray);
      assertArrayEquals(emptyArray, digitalSignatures.getCertifiedTimestamp());
    }

    @Test
    @DisplayName("Should handle digital signature purpose code correctly")
    void shouldHandleDigitalSignaturePurposeCode() {
      // Initially should be null
      assertNull(digitalSignatures.getDigitalSignaturePurposeCode());

      // Create test purpose code
      Attributes codeAttrs = new Attributes();
      codeAttrs.setString(Tag.CodeValue, VR.SH, "121020");
      codeAttrs.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
      codeAttrs.setString(Tag.CodeMeaning, VR.LO, "Integrity Check");
      Code purposeCode = new Code(codeAttrs);

      // Test setting and getting
      digitalSignatures.setDigitalSignaturePurposeCode(purposeCode);
      Code retrieved = digitalSignatures.getDigitalSignaturePurposeCode();
      assertNotNull(retrieved);
      assertEquals("121020", retrieved.getCodeValue());
      assertEquals("DCM", retrieved.getCodingSchemeDesignator());
      assertEquals("Integrity Check", retrieved.getCodeMeaning());

      // Test null handling
      digitalSignatures.setDigitalSignaturePurposeCode(null);
      assertNull(digitalSignatures.getDigitalSignaturePurposeCode());
    }

    @Test
    @DisplayName("Should convert sequence to collection correctly")
    void shouldConvertSequenceToCollection() {
      // Test null sequence
      Collection<DigitalSignatures> result = DigitalSignatures.toDigitalSignaturesMacros(null);
      assertTrue(result.isEmpty());

      // Test empty sequence
      Attributes container = new Attributes();
      Sequence emptySeq = container.newSequence(Tag.DigitalSignaturesSequence, 0);
      result = DigitalSignatures.toDigitalSignaturesMacros(emptySeq);
      assertTrue(result.isEmpty());

      // Test populated sequence
      Sequence seq = container.newSequence(Tag.DigitalSignaturesSequence, 2);

      Attributes sig1 = new Attributes();
      sig1.setInt(Tag.MACIDNumber, VR.US, 100);
      sig1.setString(Tag.DigitalSignatureUID, VR.UI, "1.2.3.4.5.6.7.8.9.100");
      seq.add(sig1);

      Attributes sig2 = new Attributes();
      sig2.setInt(Tag.MACIDNumber, VR.US, 200);
      sig2.setString(Tag.DigitalSignatureUID, VR.UI, "1.2.3.4.5.6.7.8.9.200");
      seq.add(sig2);

      result = DigitalSignatures.toDigitalSignaturesMacros(seq);
      assertEquals(2, result.size());

      List<DigitalSignatures> sigList = result.stream().toList();
      assertEquals(100, sigList.get(0).getMACIDNumber());
      assertEquals("1.2.3.4.5.6.7.8.9.100", sigList.get(0).getDigitalSignatureUID());
      assertEquals(200, sigList.get(1).getMACIDNumber());
      assertEquals("1.2.3.4.5.6.7.8.9.200", sigList.get(1).getDigitalSignatureUID());
    }

    @Test
    @DisplayName("Should handle complete digital signatures setup")
    void shouldHandleCompleteDigitalSignaturesSetup() throws IOException {
      Date testDate = new Date();

      // Create purpose code
      Attributes codeAttrs = new Attributes();
      codeAttrs.setString(Tag.CodeValue, VR.SH, "121020");
      codeAttrs.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
      codeAttrs.setString(Tag.CodeMeaning, VR.LO, "Integrity Check");
      Code purposeCode = new Code(codeAttrs);

      // Set all properties
      digitalSignatures.setMACIDNumber(TEST_MAC_ID);
      digitalSignatures.setDigitalSignatureUID(TEST_SIGNATURE_UID);
      digitalSignatures.setDigitalSignatureDateTime(testDate);
      digitalSignatures.setCertificateType(TEST_CERT_TYPE);
      digitalSignatures.setCertificateOfSigner(TEST_CERTIFICATE);
      digitalSignatures.setSignature(TEST_SIGNATURE);
      digitalSignatures.setCertifiedTimestampType(TEST_TIMESTAMP_TYPE);
      digitalSignatures.setCertifiedTimestamp(TEST_TIMESTAMP);
      digitalSignatures.setDigitalSignaturePurposeCode(purposeCode);

      // Verify all properties
      assertEquals(TEST_MAC_ID, digitalSignatures.getMACIDNumber());
      assertEquals(TEST_SIGNATURE_UID, digitalSignatures.getDigitalSignatureUID());
      assertEquals(testDate, digitalSignatures.getDigitalSignatureDateTime());
      assertEquals(TEST_CERT_TYPE, digitalSignatures.getCertificateType());
      assertArrayEquals(TEST_CERTIFICATE, digitalSignatures.getCertificateOfSigner());
      assertArrayEquals(TEST_SIGNATURE, digitalSignatures.getSignature());
      assertEquals(TEST_TIMESTAMP_TYPE, digitalSignatures.getCertifiedTimestampType());
      assertArrayEquals(TEST_TIMESTAMP, digitalSignatures.getCertifiedTimestamp());
      assertNotNull(digitalSignatures.getDigitalSignaturePurposeCode());
      assertEquals("121020", digitalSignatures.getDigitalSignaturePurposeCode().getCodeValue());
    }

    @Test
    @DisplayName("Should handle edge cases for numeric values")
    void shouldHandleEdgeCasesForNumericValues() {
      // Test MAC ID edge cases
      digitalSignatures.setMACIDNumber(0);
      assertEquals(0, digitalSignatures.getMACIDNumber());

      digitalSignatures.setMACIDNumber(65535); // Max value for US VR
      assertEquals(65535, digitalSignatures.getMACIDNumber());
    }

    @Test
    @DisplayName("Should handle large binary data")
    void shouldHandleLargeBinaryData() throws IOException {
      // Test with larger binary data
      byte[] largeCertificate = new byte[1024];
      for (int i = 0; i < largeCertificate.length; i++) {
        largeCertificate[i] = (byte) (i % 256);
      }

      digitalSignatures.setCertificateOfSigner(largeCertificate);
      assertArrayEquals(largeCertificate, digitalSignatures.getCertificateOfSigner());

      byte[] largeSignature = new byte[2048];
      for (int i = 0; i < largeSignature.length; i++) {
        largeSignature[i] = (byte) ((i * 2) % 256);
      }

      digitalSignatures.setSignature(largeSignature);
      assertArrayEquals(largeSignature, digitalSignatures.getSignature());
    }
  }

  @Nested
  @DisplayName("KODocumentModule Tests")
  class KODocumentModuleTest {

    private KODocumentModule koDocument;
    private static final String TEST_INSTANCE_NUMBER = "001";
    private static final String TEST_STUDY_UID_1 = "1.2.3.4.5.6.7.8.9.100";
    private static final String TEST_STUDY_UID_2 = "1.2.3.4.5.6.7.8.9.200";
    private static final String TEST_SERIES_UID_1 = "1.2.3.4.5.6.7.8.9.101";
    private static final String TEST_SERIES_UID_2 = "1.2.3.4.5.6.7.8.9.201";
    private static final String TEST_SOP_INSTANCE_UID_1 = "1.2.3.4.5.6.7.8.9.102";
    private static final String TEST_SOP_INSTANCE_UID_2 = "1.2.3.4.5.6.7.8.9.202";
    private static final String TEST_SOP_CLASS_UID_CT = "1.2.840.10008.5.1.4.1.1.2";
    private static final String TEST_SOP_CLASS_UID_MR = "1.2.840.10008.5.1.4.1.1.4";

    @BeforeEach
    void setUp() {
      koDocument = new KODocumentModule();
    }

    @Test
    @DisplayName("Should create empty KO document module")
    void shouldCreateEmptyKODocumentModule() {
      KODocumentModule empty = new KODocumentModule();
      assertNotNull(empty);
      assertNotNull(empty.getAttributes());
    }

    @Test
    @DisplayName("Should create KO document module from attributes")
    void shouldCreateKODocumentModuleFromAttributes() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.InstanceNumber, VR.IS, TEST_INSTANCE_NUMBER);
      Date contentDate = new Date();
      attrs.setDate(Tag.ContentDateAndTime, contentDate);

      KODocumentModule koDoc = new KODocumentModule(attrs);
      assertEquals(TEST_INSTANCE_NUMBER, koDoc.getInstanceNumber());
      assertEquals(contentDate, koDoc.getContentDateTime());
    }

    @Test
    @DisplayName("Should handle instance number correctly")
    void shouldHandleInstanceNumber() {
      // Initially should be null
      assertNull(koDocument.getInstanceNumber());

      // Test setting and getting
      koDocument.setInstanceNumber(TEST_INSTANCE_NUMBER);
      assertEquals(TEST_INSTANCE_NUMBER, koDocument.getInstanceNumber());

      // Test with numeric string
      koDocument.setInstanceNumber("12345");
      assertEquals("12345", koDocument.getInstanceNumber());

      // Test null handling
      koDocument.setInstanceNumber(null);
      assertNull(koDocument.getInstanceNumber());
    }

    @Test
    @DisplayName("Should handle content date time correctly")
    void shouldHandleContentDateTime() {
      // Initially should be null
      assertNull(koDocument.getContentDateTime());

      // Test setting and getting
      Date testDate = new Date();
      koDocument.setContentDateTime(testDate);
      assertEquals(testDate, koDocument.getContentDateTime());

      // Test with specific date
      Date specificDate = new Date(System.currentTimeMillis() - 86400000); // Yesterday
      koDocument.setContentDateTime(specificDate);
      assertEquals(specificDate, koDocument.getContentDateTime());
    }

    @Test
    @DisplayName("Should handle referenced requests correctly")
    void shouldHandleReferencedRequests() {
      // Initially should be empty
      Collection<ReferencedRequest> requests = koDocument.getReferencedRequests();
      assertTrue(requests.isEmpty());

      // Create test referenced requests
      ReferencedRequest request1 = new ReferencedRequest();
      request1.setStudyInstanceUID(TEST_STUDY_UID_1);
      request1.setAccessionNumber("ACC001");
      request1.setRequestedProcedureID("RP001");
      request1.setRequestedProcedureDescription("Chest CT");

      ReferencedRequest request2 = new ReferencedRequest();
      request2.setStudyInstanceUID(TEST_STUDY_UID_2);
      request2.setAccessionNumber("ACC002");
      request2.setRequestedProcedureID("RP002");
      request2.setRequestedProcedureDescription("Abdominal MRI");

      List<ReferencedRequest> requestList = List.of(request1, request2);
      koDocument.setReferencedRequest(requestList);

      Collection<ReferencedRequest> retrieved = koDocument.getReferencedRequests();
      assertEquals(2, retrieved.size());

      List<ReferencedRequest> retrievedList = retrieved.stream().toList();
      assertEquals(TEST_STUDY_UID_1, retrievedList.get(0).getStudyInstanceUID());
      assertEquals("ACC001", retrievedList.get(0).getAccessionNumber());
      assertEquals(TEST_STUDY_UID_2, retrievedList.get(1).getStudyInstanceUID());
      assertEquals("ACC002", retrievedList.get(1).getAccessionNumber());
    }

    @Test
    @DisplayName("Should handle current requested procedure evidences correctly")
    void shouldHandleCurrentRequestedProcedureEvidences() {
      // Initially should be empty
      Collection<HierarchicalSOPInstanceReference> evidences =
          koDocument.getCurrentRequestedProcedureEvidences();
      assertTrue(evidences.isEmpty());

      // Create test evidence references
      HierarchicalSOPInstanceReference evidence1 =
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_1, TEST_SERIES_UID_1, TEST_SOP_INSTANCE_UID_1, TEST_SOP_CLASS_UID_CT);

      HierarchicalSOPInstanceReference evidence2 =
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_2, TEST_SERIES_UID_2, TEST_SOP_INSTANCE_UID_2, TEST_SOP_CLASS_UID_MR);

      List<HierarchicalSOPInstanceReference> evidenceList = List.of(evidence1, evidence2);
      koDocument.setCurrentRequestedProcedureEvidences(evidenceList);

      Collection<HierarchicalSOPInstanceReference> retrieved =
          koDocument.getCurrentRequestedProcedureEvidences();
      assertEquals(2, retrieved.size());

      // Verify the evidences contain the expected data
      List<HierarchicalSOPInstanceReference> retrievedList = retrieved.stream().toList();
      assertNotNull(retrievedList.get(0));
      assertNotNull(retrievedList.get(1));
    }

    @Test
    @DisplayName("Should handle identical documents correctly")
    void shouldHandleIdenticalDocuments() {
      // Initially should be empty
      Collection<HierarchicalSOPInstanceReference> identicalDocs =
          koDocument.getIdenticalDocuments();
      assertTrue(identicalDocs.isEmpty());

      // Create test identical document references
      HierarchicalSOPInstanceReference doc1 =
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_1,
              TEST_SERIES_UID_1,
              "1.2.3.4.5.6.7.8.9.301",
              "1.2.840.10008.5.1.4.1.1.88.59");

      HierarchicalSOPInstanceReference doc2 =
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_2,
              TEST_SERIES_UID_2,
              "1.2.3.4.5.6.7.8.9.302",
              "1.2.840.10008.5.1.4.1.1.88.59");

      List<HierarchicalSOPInstanceReference> docList = List.of(doc1, doc2);
      koDocument.setIdenticalDocuments(docList);

      Collection<HierarchicalSOPInstanceReference> retrieved = koDocument.getIdenticalDocuments();
      assertEquals(2, retrieved.size());

      // Verify the documents contain the expected data
      List<HierarchicalSOPInstanceReference> retrievedList = retrieved.stream().toList();
      assertNotNull(retrievedList.get(0));
      assertNotNull(retrievedList.get(1));
    }

    @Test
    @DisplayName("Should handle null and empty collections correctly")
    void shouldHandleNullAndEmptyCollectionsCorrectly() {
      // Test setting null referenced requests
      koDocument.setReferencedRequest(null);
      assertTrue(koDocument.getReferencedRequests().isEmpty());

      // Test setting empty referenced requests
      koDocument.setReferencedRequest(List.of());
      assertTrue(koDocument.getReferencedRequests().isEmpty());

      // Test setting null procedure evidences
      koDocument.setCurrentRequestedProcedureEvidences(null);
      assertTrue(koDocument.getCurrentRequestedProcedureEvidences().isEmpty());

      // Test setting empty procedure evidences
      koDocument.setCurrentRequestedProcedureEvidences(List.of());
      assertTrue(koDocument.getCurrentRequestedProcedureEvidences().isEmpty());

      // Test setting null identical documents
      koDocument.setIdenticalDocuments(null);
      assertTrue(koDocument.getIdenticalDocuments().isEmpty());

      // Test setting empty identical documents
      koDocument.setIdenticalDocuments(List.of());
      assertTrue(koDocument.getIdenticalDocuments().isEmpty());
    }

    @Test
    @DisplayName("Should handle complete KO document setup")
    void shouldHandleCompleteKODocumentSetup() {
      Date contentDate = new Date();

      // Set basic properties
      koDocument.setInstanceNumber(TEST_INSTANCE_NUMBER);
      koDocument.setContentDateTime(contentDate);

      // Create and set referenced request
      ReferencedRequest request = new ReferencedRequest();
      request.setStudyInstanceUID(TEST_STUDY_UID_1);
      request.setAccessionNumber("ACC12345");
      request.setRequestedProcedureDescription("Comprehensive Imaging Study");
      koDocument.setReferencedRequest(List.of(request));

      // Create and set procedure evidence
      HierarchicalSOPInstanceReference evidence =
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_1, TEST_SERIES_UID_1, TEST_SOP_INSTANCE_UID_1, TEST_SOP_CLASS_UID_CT);
      koDocument.setCurrentRequestedProcedureEvidences(List.of(evidence));

      // Create and set identical document
      HierarchicalSOPInstanceReference identicalDoc =
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_2,
              TEST_SERIES_UID_2,
              "1.2.3.4.5.6.7.8.9.400",
              "1.2.840.10008.5.1.4.1.1.88.59");
      koDocument.setIdenticalDocuments(List.of(identicalDoc));

      // Verify all properties are correctly set
      assertEquals(TEST_INSTANCE_NUMBER, koDocument.getInstanceNumber());
      assertEquals(contentDate, koDocument.getContentDateTime());
      assertEquals(1, koDocument.getReferencedRequests().size());
      assertEquals(1, koDocument.getCurrentRequestedProcedureEvidences().size());
      assertEquals(1, koDocument.getIdenticalDocuments().size());

      // Verify specific content
      ReferencedRequest retrievedRequest = koDocument.getReferencedRequests().iterator().next();
      assertEquals(TEST_STUDY_UID_1, retrievedRequest.getStudyInstanceUID());
      assertEquals("ACC12345", retrievedRequest.getAccessionNumber());
    }

    @Test
    @DisplayName("Should handle multiple evidence references from same study")
    void shouldHandleMultipleEvidenceReferencesFromSameStudy() {
      // Create multiple evidence references from the same study but different series
      HierarchicalSOPInstanceReference evidence1 =
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_1, TEST_SERIES_UID_1, TEST_SOP_INSTANCE_UID_1, TEST_SOP_CLASS_UID_CT);

      HierarchicalSOPInstanceReference evidence2 =
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_1,
              "1.2.3.4.5.6.7.8.9.111",
              "1.2.3.4.5.6.7.8.9.112",
              TEST_SOP_CLASS_UID_CT);

      HierarchicalSOPInstanceReference evidence3 =
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_1,
              "1.2.3.4.5.6.7.8.9.121",
              "1.2.3.4.5.6.7.8.9.122",
              TEST_SOP_CLASS_UID_CT);

      List<HierarchicalSOPInstanceReference> evidenceList =
          List.of(evidence1, evidence2, evidence3);
      koDocument.setCurrentRequestedProcedureEvidences(evidenceList);

      Collection<HierarchicalSOPInstanceReference> retrieved =
          koDocument.getCurrentRequestedProcedureEvidences();
      assertEquals(3, retrieved.size());
    }

    @Test
    @DisplayName("Should handle instance number edge cases")
    void shouldHandleInstanceNumberEdgeCases() {
      // Test with leading zeros
      koDocument.setInstanceNumber("00123");
      assertEquals("00123", koDocument.getInstanceNumber());

      // Test with large number
      koDocument.setInstanceNumber("999999");
      assertEquals("999999", koDocument.getInstanceNumber());

      // Test with alphanumeric (though typically numeric)
      koDocument.setInstanceNumber("1A2B3C");
      assertEquals("1A2B3C", koDocument.getInstanceNumber());

      // Test with whitespace
      koDocument.setInstanceNumber("  123  ");
      assertEquals("123", koDocument.getInstanceNumber());
    }

    @Test
    @DisplayName("Should handle date time precision correctly")
    void shouldHandleDateTimePrecisionCorrectly() {
      // Test with millisecond precision
      long currentTime = System.currentTimeMillis();
      Date preciseDate = new Date(currentTime);

      koDocument.setContentDateTime(preciseDate);
      Date retrieved = koDocument.getContentDateTime();

      assertNotNull(retrieved);
      // DICOM DateTime might have different precision, so we check it's reasonably close
      long timeDiff = Math.abs(retrieved.getTime() - preciseDate.getTime());
      assertTrue(timeDiff < 1000, "Time difference should be less than 1 second");
    }

    @Test
    @DisplayName("Should handle complex evidence hierarchy")
    void shouldHandleComplexEvidenceHierarchy() {
      // Create evidence from multiple studies and series
      List<HierarchicalSOPInstanceReference> complexEvidences = new ArrayList<>();

      // Study 1 - Series 1 - Multiple instances
      complexEvidences.add(
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_1, TEST_SERIES_UID_1, TEST_SOP_INSTANCE_UID_1, TEST_SOP_CLASS_UID_CT));
      complexEvidences.add(
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_1, TEST_SERIES_UID_1, "1.2.3.4.5.6.7.8.9.103", TEST_SOP_CLASS_UID_CT));

      // Study 1 - Series 2 - Single instance
      complexEvidences.add(
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_1,
              "1.2.3.4.5.6.7.8.9.105",
              "1.2.3.4.5.6.7.8.9.106",
              TEST_SOP_CLASS_UID_MR));

      // Study 2 - Series 1 - Single instance
      complexEvidences.add(
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_2, TEST_SERIES_UID_2, TEST_SOP_INSTANCE_UID_2, TEST_SOP_CLASS_UID_MR));

      koDocument.setCurrentRequestedProcedureEvidences(complexEvidences);

      Collection<HierarchicalSOPInstanceReference> retrieved =
          koDocument.getCurrentRequestedProcedureEvidences();
      assertEquals(4, retrieved.size());
    }

    @Test
    @DisplayName("Should maintain data integrity across multiple operations")
    void shouldMaintainDataIntegrityAcrossMultipleOperations() {
      // Initial setup
      koDocument.setInstanceNumber("001");
      Date initialDate = new Date();
      koDocument.setContentDateTime(initialDate);

      // Add some referenced requests
      ReferencedRequest request1 = new ReferencedRequest();
      request1.setStudyInstanceUID(TEST_STUDY_UID_1);
      koDocument.setReferencedRequest(List.of(request1));

      // Verify initial state
      assertEquals("001", koDocument.getInstanceNumber());
      assertEquals(initialDate, koDocument.getContentDateTime());
      assertEquals(1, koDocument.getReferencedRequests().size());

      // Update instance number - should not affect other properties
      koDocument.setInstanceNumber("002");
      assertEquals("002", koDocument.getInstanceNumber());
      assertEquals(initialDate, koDocument.getContentDateTime());
      assertEquals(1, koDocument.getReferencedRequests().size());

      // Add evidence - should not affect other properties
      HierarchicalSOPInstanceReference evidence =
          createTestHierarchicalSOPReference(
              TEST_STUDY_UID_1, TEST_SERIES_UID_1, TEST_SOP_INSTANCE_UID_1, TEST_SOP_CLASS_UID_CT);
      koDocument.setCurrentRequestedProcedureEvidences(List.of(evidence));

      assertEquals("002", koDocument.getInstanceNumber());
      assertEquals(initialDate, koDocument.getContentDateTime());
      assertEquals(1, koDocument.getReferencedRequests().size());
      assertEquals(1, koDocument.getCurrentRequestedProcedureEvidences().size());

      // Update date - should not affect other properties
      Date newDate = new Date(System.currentTimeMillis() + 60000);
      koDocument.setContentDateTime(newDate);

      assertEquals("002", koDocument.getInstanceNumber());
      assertEquals(newDate, koDocument.getContentDateTime());
      assertEquals(1, koDocument.getReferencedRequests().size());
      assertEquals(1, koDocument.getCurrentRequestedProcedureEvidences().size());
    }

    // Helper method to create test hierarchical SOP instance references
    private HierarchicalSOPInstanceReference createTestHierarchicalSOPReference(
        String studyUID, String seriesUID, String instanceUID, String classUID) {
      // Create a minimal hierarchical SOP instance reference for testing
      // Note: This assumes HierarchicalSOPInstanceReference has appropriate constructors/setters
      // If not available, you might need to create it through Attributes
      Attributes attrs = new Attributes();
      attrs.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
      attrs.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
      attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI, instanceUID);
      attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, classUID);

      return new HierarchicalSOPInstanceReference(attrs);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTest {

    @Test
    @DisplayName("Should handle complex document reference scenarios")
    void shouldHandleComplexDocumentReferenceScenarios() {
      // Create a complete referenced request with all fields
      ReferencedRequest request = new ReferencedRequest();
      request.setStudyInstanceUID(TEST_STUDY_UID);
      request.setAccessionNumber(TEST_ACCESSION_NUMBER);
      request.setPlacerOrderNumberImagingServiceRequest(TEST_PLACER_ORDER);
      request.setFillerOrderNumberImagingServiceRequest(TEST_FILLER_ORDER);
      request.setRequestedProcedureID(TEST_PROCEDURE_ID);
      request.setRequestedProcedureDescription(TEST_PROCEDURE_DESC);
      request.setReasonForTheRequestedProcedure(TEST_REASON);

      // Add codes using Attributes
      Attributes procedureCodeAttrs = new Attributes();
      procedureCodeAttrs.setString(Tag.CodeValue, VR.SH, "71020");
      procedureCodeAttrs.setString(Tag.CodingSchemeDesignator, VR.SH, "CPT");
      procedureCodeAttrs.setString(Tag.CodeMeaning, VR.LO, "Chest X-ray");
      Code procedureCode = new Code(procedureCodeAttrs);

      Attributes reasonCodeAttrs = new Attributes();
      reasonCodeAttrs.setString(Tag.CodeValue, VR.SH, "R06.02");
      reasonCodeAttrs.setString(Tag.CodingSchemeDesignator, VR.SH, "ICD-10-CM");
      reasonCodeAttrs.setString(Tag.CodeMeaning, VR.LO, "Shortness of breath");
      Code reasonCode = new Code(reasonCodeAttrs);
      request.setRequestedProcedureCode(procedureCode);
      request.setReasonForRequestedProcedureCode(reasonCode);

      // Add SOP instance reference
      SOPInstanceReference sopRef = new SOPInstanceReference();
      sopRef.setReferencedSOPInstanceUID("1.2.3.4.5.6.7.8.9.200");
      sopRef.setReferencedSOPClassUID("1.2.840.10008.5.1.4.1.1.2");
      request.setReferencedStudySOPInstance(sopRef);

      // Create KO document and add the request
      KODocumentModule koDoc = new KODocumentModule();
      koDoc.setReferencedRequest(List.of(request));

      // Verify everything is preserved
      Collection<ReferencedRequest> retrieved = koDoc.getReferencedRequests();
      assertEquals(1, retrieved.size());

      ReferencedRequest retrievedRequest = retrieved.iterator().next();
      assertEquals(TEST_STUDY_UID, retrievedRequest.getStudyInstanceUID());
      assertEquals(TEST_ACCESSION_NUMBER, retrievedRequest.getAccessionNumber());
      assertNotNull(retrievedRequest.getRequestedProcedureCode());
      assertNotNull(retrievedRequest.getReasonForRequestedProcedureCode());
      assertNotNull(retrievedRequest.getReferencedStudySOPInstance());
    }

    @Test
    @DisplayName("Should maintain data integrity across sequence operations")
    void shouldMaintainDataIntegrityAcrossSequenceOperations() {
      // Create multiple referenced requests
      ReferencedRequest request1 = new ReferencedRequest();
      request1.setStudyInstanceUID("1.1.1.1.1");
      request1.setAccessionNumber("ACC001");

      ReferencedRequest request2 = new ReferencedRequest();
      request2.setStudyInstanceUID("2.2.2.2.2");
      request2.setAccessionNumber("ACC002");

      ReferencedRequest request3 = new ReferencedRequest();
      request3.setStudyInstanceUID("3.3.3.3.3");
      request3.setAccessionNumber("ACC003");

      KODocumentModule koDoc = new KODocumentModule();

      // Add first set
      koDoc.setReferencedRequest(List.of(request1, request2));
      assertEquals(2, koDoc.getReferencedRequests().size());

      // Replace with different set
      koDoc.setReferencedRequest(List.of(request3));
      assertEquals(1, koDoc.getReferencedRequests().size());

      ReferencedRequest retrieved = koDoc.getReferencedRequests().iterator().next();
      assertEquals("3.3.3.3.3", retrieved.getStudyInstanceUID());
      assertEquals("ACC003", retrieved.getAccessionNumber());

      // Clear all
      koDoc.setReferencedRequest(List.of());
      assertTrue(koDoc.getReferencedRequests().isEmpty());
    }
  }
}
