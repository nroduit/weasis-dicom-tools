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
 * Comprehensive test suite for the SOP Instance Reference hierarchy. Tests the inheritance chain:
 * SOPInstanceReference → SOPInstanceReferenceAndPurpose → SOPInstanceReferenceAndMAC
 */
@DisplayName("SOP Instance Reference Hierarchy Tests")
class SOPInstanceReferenceHierarchyTest {

  private static final String TEST_SOP_INSTANCE_UID = "1.2.3.4.5.6.7.8.9.10";
  private static final String TEST_SOP_CLASS_UID = "1.2.840.10008.5.1.4.1.1.2"; // CT Image Storage
  private static final int TEST_INSTANCE_NUMBER = 42;
  private static final int[] TEST_FRAME_NUMBERS = {1, 3, 5, 7};

  @Nested
  @DisplayName("SOPInstanceReference Base Tests")
  class SOPInstanceReferenceTest {

    private SOPInstanceReference reference;
    private Attributes attributes;

    @BeforeEach
    void setUp() {
      attributes = new Attributes();
      reference = new SOPInstanceReference(attributes);
    }

    @Test
    @DisplayName("Should create empty instance successfully")
    void shouldCreateEmptyInstance() {
      SOPInstanceReference emptyRef = new SOPInstanceReference();
      assertNotNull(emptyRef);
      assertNotNull(emptyRef.dcmItems);
    }

    @Test
    @DisplayName("Should handle SOP Instance UID correctly")
    void shouldHandleSOPInstanceUID() {
      // Test setting and getting
      reference.setReferencedSOPInstanceUID(TEST_SOP_INSTANCE_UID);
      assertEquals(TEST_SOP_INSTANCE_UID, reference.getReferencedSOPInstanceUID());

      // Test null handling
      reference.setReferencedSOPInstanceUID(null);
      assertNull(reference.getReferencedSOPInstanceUID());
    }

    @Test
    @DisplayName("Should handle SOP Class UID correctly")
    void shouldHandleSOPClassUID() {
      reference.setReferencedSOPClassUID(TEST_SOP_CLASS_UID);
      assertEquals(TEST_SOP_CLASS_UID, reference.getReferencedSOPClassUID());
    }

    @Test
    @DisplayName("Should handle instance number correctly")
    void shouldHandleInstanceNumber() {
      reference.setInstanceNumber(TEST_INSTANCE_NUMBER);
      assertEquals(TEST_INSTANCE_NUMBER, reference.getInstanceNumber());

      // Test null handling
      reference.setInstanceNumber(null); // Null is not allowed, should not change
      assertEquals(TEST_INSTANCE_NUMBER, reference.getInstanceNumber());
    }

    @Test
    @DisplayName("Should handle frame numbers correctly")
    void shouldHandleFrameNumbers() {
      // Test setting and getting multiple frames
      reference.setReferencedFrameNumber(TEST_FRAME_NUMBERS);
      assertArrayEquals(TEST_FRAME_NUMBERS, reference.getReferencedFrameNumber());

      // Test empty array (entire instance referenced)
      reference.setReferencedFrameNumber();
      int[] emptyFrames = reference.getReferencedFrameNumber();
      assertTrue(emptyFrames == null || emptyFrames.length == 0);

      // Test single frame
      reference.setReferencedFrameNumber(1);
      assertArrayEquals(new int[] {1}, reference.getReferencedFrameNumber());
    }

    @Test
    @DisplayName("Should convert sequence to collection correctly")
    void shouldConvertSequenceToCollection() {
      // Test with null sequence
      Collection<SOPInstanceReference> result =
          SOPInstanceReference.toSOPInstanceReferenceMacros(null);
      assertTrue(result.isEmpty());

      // Test with empty sequence - create using proper newSequence method
      Attributes emptyAttrs = new Attributes();
      Sequence emptySeq = emptyAttrs.newSequence(Tag.ReferencedInstanceSequence, 0);
      result = SOPInstanceReference.toSOPInstanceReferenceMacros(emptySeq);
      assertTrue(result.isEmpty());

      // Test with populated sequence
      Attributes seqContainer = new Attributes();
      Sequence seq = seqContainer.newSequence(Tag.ReferencedInstanceSequence, 2);

      Attributes item1 = new Attributes();
      item1.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.1");
      seq.add(item1);

      Attributes item2 = new Attributes();
      item2.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.2");
      seq.add(item2);

      result = SOPInstanceReference.toSOPInstanceReferenceMacros(seq);
      assertEquals(2, result.size());

      List<SOPInstanceReference> list = result.stream().toList();
      assertEquals("1.2.3.1", list.get(0).getReferencedSOPInstanceUID());
      assertEquals("1.2.3.2", list.get(1).getReferencedSOPInstanceUID());
    }

    @Test
    @DisplayName("Should handle complete reference setup")
    void shouldHandleCompleteReferenceSetup() {
      reference.setReferencedSOPInstanceUID(TEST_SOP_INSTANCE_UID);
      reference.setReferencedSOPClassUID(TEST_SOP_CLASS_UID);
      reference.setInstanceNumber(TEST_INSTANCE_NUMBER);
      reference.setReferencedFrameNumber(TEST_FRAME_NUMBERS);

      assertEquals(TEST_SOP_INSTANCE_UID, reference.getReferencedSOPInstanceUID());
      assertEquals(TEST_SOP_CLASS_UID, reference.getReferencedSOPClassUID());
      assertEquals(TEST_INSTANCE_NUMBER, reference.getInstanceNumber());
      assertArrayEquals(TEST_FRAME_NUMBERS, reference.getReferencedFrameNumber());
    }
  }

  @Nested
  @DisplayName("SOPInstanceReferenceAndPurpose Extended Tests")
  class SOPInstanceReferenceAndPurposeTest {

    private SOPInstanceReferenceAndPurpose reference;
    private Code testPurposeCode;

    @BeforeEach
    void setUp() {
      reference = new SOPInstanceReferenceAndPurpose();
      testPurposeCode = createTestPurposeCode();
    }

    private Code createTestPurposeCode() {
      Attributes codeAttrs = new Attributes();
      codeAttrs.setString(Tag.CodeValue, VR.SH, "121200");
      codeAttrs.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
      codeAttrs.setString(Tag.CodeMeaning, VR.LO, "Illustration");
      return new Code(codeAttrs);
    }

    @Test
    @DisplayName("Should inherit all base functionality")
    void shouldInheritBaseFunctionality() {
      // Test that it's still a SOPInstanceReference
      assertTrue(reference instanceof SOPInstanceReference);

      // Test inherited functionality
      reference.setReferencedSOPInstanceUID(TEST_SOP_INSTANCE_UID);
      assertEquals(TEST_SOP_INSTANCE_UID, reference.getReferencedSOPInstanceUID());
    }

    @Test
    @DisplayName("Should handle purpose code correctly")
    void shouldHandlePurposeCode() {
      // Test setting and getting purpose code
      reference.setPurposeOfReferenceCode(testPurposeCode);
      Code retrievedCode = reference.getPurposeOfReferenceCode();

      assertNotNull(retrievedCode);
      assertEquals("121200", retrievedCode.getCodeValue());
      assertEquals("DCM", retrievedCode.getCodingSchemeDesignator());
      assertEquals("Illustration", retrievedCode.getCodeMeaning());

      // Test null handling
      reference.setPurposeOfReferenceCode(null);
      assertNull(reference.getPurposeOfReferenceCode());
    }

    @Test
    @DisplayName("Should convert sequence with purpose codes correctly")
    void shouldConvertSequenceWithPurposeCodes() {
      Attributes seqContainer = new Attributes();
      Sequence seq = seqContainer.newSequence(Tag.ReferencedInstanceSequence, 1);

      Attributes item = new Attributes();
      item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, TEST_SOP_INSTANCE_UID);

      // Add purpose code sequence using proper newSequence method
      Sequence purposeSeq = item.newSequence(Tag.PurposeOfReferenceCodeSequence, 1);
      purposeSeq.add(testPurposeCode.dcmItems);
      seq.add(item);

      Collection<SOPInstanceReferenceAndPurpose> result =
          SOPInstanceReferenceAndPurpose.toSOPInstanceReferenceAndPurposesMacros(seq);

      assertEquals(1, result.size());
      SOPInstanceReferenceAndPurpose ref = result.iterator().next();
      assertEquals(TEST_SOP_INSTANCE_UID, ref.getReferencedSOPInstanceUID());
      assertNotNull(ref.getPurposeOfReferenceCode());
    }

    @Test
    @DisplayName("Should handle empty or null reference setup with purpose code")
    void shouldHandleEmptyReferenceSetupWithPurposeCode() {
      // Initially should be empty
      assertNull(reference.getReferencedSOPInstanceUID());
      assertNull(reference.getPurposeOfReferenceCode());

      // Set purpose code without SOP instance UID
      reference.setPurposeOfReferenceCode(testPurposeCode);
      assertNotNull(reference.getPurposeOfReferenceCode());
      assertNull(reference.getReferencedSOPInstanceUID());

      // Set SOP instance UID without purpose code
      reference.setReferencedSOPInstanceUID(TEST_SOP_INSTANCE_UID);
      assertEquals(TEST_SOP_INSTANCE_UID, reference.getReferencedSOPInstanceUID());
      assertNotNull(reference.getPurposeOfReferenceCode());

      Collection<SOPInstanceReferenceAndPurpose> result =
          SOPInstanceReferenceAndPurpose.toSOPInstanceReferenceAndPurposesMacros(null);
      assertTrue(result.isEmpty());
      // Test with empty sequence
      Attributes emptyAttrs = new Attributes();
      Sequence emptySeq = emptyAttrs.newSequence(Tag.ReferencedInstanceSequence, 0);
      result = SOPInstanceReferenceAndPurpose.toSOPInstanceReferenceAndPurposesMacros(emptySeq);
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("SOPInstanceReferenceAndMAC Complete Tests")
  class SOPInstanceReferenceAndMACTest {

    private SOPInstanceReferenceAndMAC reference;

    @BeforeEach
    void setUp() {
      reference = new SOPInstanceReferenceAndMAC();
    }

    @Test
    @DisplayName("Should inherit all extended functionality")
    void shouldInheritExtendedFunctionality() {
      // Test inheritance chain
      assertTrue(reference instanceof SOPInstanceReference);
      assertTrue(reference instanceof SOPInstanceReferenceAndPurpose);

      // Test base functionality
      reference.setReferencedSOPInstanceUID(TEST_SOP_INSTANCE_UID);
      assertEquals(TEST_SOP_INSTANCE_UID, reference.getReferencedSOPInstanceUID());

      // Test purpose functionality
      Code purposeCode = new Code(new Attributes());
      reference.setPurposeOfReferenceCode(purposeCode);
      assertNotNull(reference.getPurposeOfReferenceCode());
    }

    @Test
    @DisplayName("Should handle MAC parameters correctly")
    void shouldHandleMACParameters() {
      // Initially should be empty
      Collection<MACParameters> macParams = reference.getMACParameters();
      assertTrue(macParams.isEmpty());

      // Test setting empty collection
      reference.setMACParameters(List.of());
      assertTrue(reference.getMACParameters().isEmpty());
    }

    @Test
    @DisplayName("Should handle digital signatures correctly")
    void shouldHandleDigitalSignatures() {
      // Initially should be empty
      Collection<DigitalSignatures> signatures = reference.getDigitalSignatures();
      assertTrue(signatures.isEmpty());

      // Test setting empty collection
      reference.setDigitalSignatures(List.of());
      assertTrue(reference.getDigitalSignatures().isEmpty());
    }

    @Test
    @DisplayName("Should convert sequence with security features correctly")
    void shouldConvertSequenceWithSecurityFeatures() {
      Attributes seqContainer = new Attributes();
      Sequence seq = seqContainer.newSequence(Tag.ReferencedInstanceSequence, 1);

      Attributes item = new Attributes();
      item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, TEST_SOP_INSTANCE_UID);

      // Add empty MAC and signature sequences using proper newSequence method
      item.newSequence(Tag.MACParametersSequence, 0);
      item.newSequence(Tag.DigitalSignaturesSequence, 0);
      seq.add(item);

      Collection<SOPInstanceReferenceAndMAC> result =
          SOPInstanceReferenceAndMAC.toSOPInstanceReferenceAndMacMacros(seq);

      assertEquals(1, result.size());
      SOPInstanceReferenceAndMAC ref = result.iterator().next();
      assertEquals(TEST_SOP_INSTANCE_UID, ref.getReferencedSOPInstanceUID());
      assertTrue(ref.getMACParameters().isEmpty());
      assertTrue(ref.getDigitalSignatures().isEmpty());
    }
  }

  @Nested
  @DisplayName("Polymorphism and Integration Tests")
  class PolymorphismTest {

    @Test
    @DisplayName("Should handle polymorphic behavior correctly")
    void shouldHandlePolymorphicBehavior() {
      SOPInstanceReference baseRef = new SOPInstanceReferenceAndMAC();
      baseRef.setReferencedSOPInstanceUID(TEST_SOP_INSTANCE_UID);

      assertEquals(TEST_SOP_INSTANCE_UID, baseRef.getReferencedSOPInstanceUID());
      assertTrue(baseRef instanceof SOPInstanceReferenceAndMAC);
    }

    @Test
    @DisplayName("Should maintain data integrity across hierarchy")
    void shouldMaintainDataIntegrityAcrossHierarchy() {
      SOPInstanceReferenceAndMAC macRef = new SOPInstanceReferenceAndMAC();

      // Set base data
      macRef.setReferencedSOPInstanceUID(TEST_SOP_INSTANCE_UID);
      macRef.setReferencedSOPClassUID(TEST_SOP_CLASS_UID);
      macRef.setInstanceNumber(TEST_INSTANCE_NUMBER);

      // Set purpose data
      Code purposeCode = new Code(new Attributes());
      macRef.setPurposeOfReferenceCode(purposeCode);

      // Verify all data is maintained
      assertEquals(TEST_SOP_INSTANCE_UID, macRef.getReferencedSOPInstanceUID());
      assertEquals(TEST_SOP_CLASS_UID, macRef.getReferencedSOPClassUID());
      assertEquals(TEST_INSTANCE_NUMBER, macRef.getInstanceNumber());
      assertNotNull(macRef.getPurposeOfReferenceCode());
      assertNotNull(macRef.getMACParameters());
      assertNotNull(macRef.getDigitalSignatures());
    }
  }
}
