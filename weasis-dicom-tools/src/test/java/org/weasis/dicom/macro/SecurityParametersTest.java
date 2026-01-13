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

/** Comprehensive test suite for security-related macro classes. */
@DisplayName("Security Parameters Tests")
class SecurityParametersTest {

  private static final String TEST_TRANSFER_SYNTAX_UID = "1.2.840.10008.1.2.1";
  private static final String TEST_MAC_ALGORITHM = "RIPEMD160";
  private static final int TEST_MAC_ID = 1;
  private static final int[] TEST_SIGNED_TAGS = {
    Tag.PatientID, Tag.StudyInstanceUID, Tag.SeriesInstanceUID
  };

  @Nested
  @DisplayName("MACParameters Tests")
  class MACParametersTest {

    private MACParameters macParams;

    @BeforeEach
    void setUp() {
      macParams = new MACParameters();
    }

    @Test
    @DisplayName("Should create empty MAC parameters")
    void shouldCreateEmptyMACParameters() {
      MACParameters empty = new MACParameters();
      assertNotNull(empty);
      assertNotNull(empty.getAttributes());
    }

    @Test
    @DisplayName("Should create MAC parameters from attributes")
    void shouldCreateMACParametersFromAttributes() {
      Attributes attrs = new Attributes();
      attrs.setInt(Tag.MACIDNumber, VR.US, TEST_MAC_ID);
      attrs.setString(Tag.MACAlgorithm, VR.CS, TEST_MAC_ALGORITHM);

      MACParameters params = new MACParameters(attrs);
      assertEquals(TEST_MAC_ID, params.getMACIDNumber());
      assertEquals(TEST_MAC_ALGORITHM, params.getMACAlgorithm());
    }

    @Test
    @DisplayName("Should handle MAC ID number correctly")
    void shouldHandleMACIDNumber() {
      macParams.setMACIDNumber(TEST_MAC_ID);
      assertEquals(TEST_MAC_ID, macParams.getMACIDNumber());

      // Test default value
      MACParameters defaultParams = new MACParameters();
      assertEquals(-1, defaultParams.getMACIDNumber());
    }

    @Test
    @DisplayName("Should handle MAC algorithm correctly")
    void shouldHandleMACAlgorithm() {
      macParams.setMACAlgorithm(TEST_MAC_ALGORITHM);
      assertEquals(TEST_MAC_ALGORITHM, macParams.getMACAlgorithm());

      macParams.setMACAlgorithm(null);
      assertNull(macParams.getMACAlgorithm());
    }

    @Test
    @DisplayName("Should handle transfer syntax UID correctly")
    void shouldHandleTransferSyntaxUID() {
      macParams.setMACCalculationTransferSyntaxUID(TEST_TRANSFER_SYNTAX_UID);
      assertEquals(TEST_TRANSFER_SYNTAX_UID, macParams.getMACCalculationTransferSyntaxUID());

      macParams.setMACCalculationTransferSyntaxUID(null);
      assertNull(macParams.getMACCalculationTransferSyntaxUID());
    }

    @Test
    @DisplayName("Should handle data elements signed correctly")
    void shouldHandleDataElementsSigned() {
      macParams.setDataElementsSigned(TEST_SIGNED_TAGS);
      assertArrayEquals(TEST_SIGNED_TAGS, macParams.getDataElementsSigned());

      // Test empty array
      macParams.setDataElementsSigned(new int[0]);
      int[] result = macParams.getDataElementsSigned();
      assertNotNull(result);
      assertEquals(0, result.length);
    }

    @Test
    @DisplayName("Should convert sequence to collection correctly")
    void shouldConvertSequenceToCollection() {
      // Test null sequence
      Collection<MACParameters> result = MACParameters.toMACParametersMacros(null);
      assertTrue(result.isEmpty());

      // Test empty sequence
      Attributes container = new Attributes();
      Sequence emptySeq = container.newSequence(Tag.MACParametersSequence, 0);
      result = MACParameters.toMACParametersMacros(emptySeq);
      assertTrue(result.isEmpty());

      // Test populated sequence
      Sequence seq = container.newSequence(Tag.MACParametersSequence, 2);

      Attributes mac1 = new Attributes();
      mac1.setInt(Tag.MACIDNumber, VR.US, 1);
      mac1.setString(Tag.MACAlgorithm, VR.CS, "RIPEMD160");
      seq.add(mac1);

      Attributes mac2 = new Attributes();
      mac2.setInt(Tag.MACIDNumber, VR.US, 2);
      mac2.setString(Tag.MACAlgorithm, VR.CS, "SHA1");
      seq.add(mac2);

      result = MACParameters.toMACParametersMacros(seq);
      assertEquals(2, result.size());

      List<MACParameters> macList = result.stream().toList();
      assertEquals(1, macList.get(0).getMACIDNumber());
      assertEquals("RIPEMD160", macList.get(0).getMACAlgorithm());
      assertEquals(2, macList.get(1).getMACIDNumber());
      assertEquals("SHA1", macList.get(1).getMACAlgorithm());
    }

    @Test
    @DisplayName("Should handle complete MAC parameters setup")
    void shouldHandleCompleteMACParametersSetup() {
      macParams.setMACIDNumber(TEST_MAC_ID);
      macParams.setMACAlgorithm(TEST_MAC_ALGORITHM);
      macParams.setMACCalculationTransferSyntaxUID(TEST_TRANSFER_SYNTAX_UID);
      macParams.setDataElementsSigned(TEST_SIGNED_TAGS);

      assertEquals(TEST_MAC_ID, macParams.getMACIDNumber());
      assertEquals(TEST_MAC_ALGORITHM, macParams.getMACAlgorithm());
      assertEquals(TEST_TRANSFER_SYNTAX_UID, macParams.getMACCalculationTransferSyntaxUID());
      assertArrayEquals(TEST_SIGNED_TAGS, macParams.getDataElementsSigned());
    }

    @Test
    @DisplayName("Should handle various MAC algorithms")
    void shouldHandleVariousMACAlgorithms() {
      String[] algorithms = {"RIPEMD160", "SHA1", "MD5", "SHA256", "SHA384", "SHA512"};

      for (String algorithm : algorithms) {
        macParams.setMACAlgorithm(algorithm);
        assertEquals(algorithm, macParams.getMACAlgorithm());
      }
    }
  }

  @Nested
  @DisplayName("DigitalSignatures Tests")
  class DigitalSignaturesTest {

    private DigitalSignatures digitalSigs;

    @BeforeEach
    void setUp() {
      digitalSigs = new DigitalSignatures();
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

      DigitalSignatures sigs = new DigitalSignatures(attrs);
      assertNotNull(sigs);
      assertEquals(TEST_MAC_ID, sigs.getAttributes().getInt(Tag.MACIDNumber, -1));
    }

    @Test
    @DisplayName("Should handle values correctly for digital signatures")
    void shouldHandleValuesForDigitalSignatures() {
      digitalSigs.getAttributes().setInt(Tag.MACIDNumber, VR.US, TEST_MAC_ID);
      assertEquals(TEST_MAC_ID, digitalSigs.getMACIDNumber());

      // Test default value
      DigitalSignatures defaultSigs = new DigitalSignatures();
      assertEquals(-1, defaultSigs.getMACIDNumber());
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
      Sequence seq = container.newSequence(Tag.DigitalSignaturesSequence, 1);
      Attributes sigAttrs = new Attributes();
      sigAttrs.setInt(Tag.MACIDNumber, VR.US, TEST_MAC_ID);
      seq.add(sigAttrs);

      result = DigitalSignatures.toDigitalSignaturesMacros(seq);
      assertEquals(1, result.size());

      DigitalSignatures sig = result.iterator().next();
      assertEquals(TEST_MAC_ID, sig.getAttributes().getInt(Tag.MACIDNumber, -1));
    }

    @Test
    @DisplayName("Should handle multiple digital signatures")
    void shouldHandleMultipleDigitalSignatures() {
      Attributes container = new Attributes();
      Sequence seq = container.newSequence(Tag.DigitalSignaturesSequence, 3);

      // Add multiple signatures
      for (int i = 1; i <= 3; i++) {
        Attributes sigAttrs = new Attributes();
        sigAttrs.setInt(Tag.MACIDNumber, VR.US, i);
        seq.add(sigAttrs);
      }

      Collection<DigitalSignatures> result = DigitalSignatures.toDigitalSignaturesMacros(seq);
      assertEquals(3, result.size());

      List<DigitalSignatures> sigList = result.stream().toList();
      for (int i = 0; i < 3; i++) {
        assertEquals(i + 1, sigList.get(i).getAttributes().getInt(Tag.MACIDNumber, -1));
      }
    }
  }

  @Nested
  @DisplayName("Security Integration Tests")
  class SecurityIntegrationTest {

    @Test
    @DisplayName("Should handle combined MAC parameters and digital signatures")
    void shouldHandleCombinedMACParametersAndDigitalSignatures() {
      // Create a container with both MAC parameters and digital signatures
      Attributes container = new Attributes();

      // Add MAC parameters sequence
      Sequence macSeq = container.newSequence(Tag.MACParametersSequence, 1);
      Attributes macAttrs = new Attributes();
      macAttrs.setInt(Tag.MACIDNumber, VR.US, TEST_MAC_ID);
      macAttrs.setString(Tag.MACAlgorithm, VR.CS, TEST_MAC_ALGORITHM);
      macSeq.add(macAttrs);

      // Add digital signatures sequence
      Sequence sigSeq = container.newSequence(Tag.DigitalSignaturesSequence, 1);
      Attributes sigAttrs = new Attributes();
      sigAttrs.setInt(Tag.MACIDNumber, VR.US, TEST_MAC_ID);
      sigSeq.add(sigAttrs);

      // Test conversion
      Collection<MACParameters> macParams =
          MACParameters.toMACParametersMacros(container.getSequence(Tag.MACParametersSequence));
      Collection<DigitalSignatures> digitalSigs =
          DigitalSignatures.toDigitalSignaturesMacros(
              container.getSequence(Tag.DigitalSignaturesSequence));

      assertEquals(1, macParams.size());
      assertEquals(1, digitalSigs.size());

      MACParameters mac = macParams.iterator().next();
      assertEquals(TEST_MAC_ID, mac.getMACIDNumber());
      assertEquals(TEST_MAC_ALGORITHM, mac.getMACAlgorithm());
    }

    @Test
    @DisplayName("Should maintain security parameter relationships")
    void shouldMaintainSecurityParameterRelationships() {
      // Test scenario where MAC parameters and digital signatures share the same MAC ID
      MACParameters macParams = new MACParameters();
      macParams.setMACIDNumber(TEST_MAC_ID);
      macParams.setMACAlgorithm(TEST_MAC_ALGORITHM);
      macParams.setDataElementsSigned(TEST_SIGNED_TAGS);

      DigitalSignatures digitalSigs = new DigitalSignatures();
      digitalSigs.getAttributes().setInt(Tag.MACIDNumber, VR.US, TEST_MAC_ID);

      // Verify they can reference the same MAC ID
      assertEquals(
          macParams.getMACIDNumber(), digitalSigs.getAttributes().getInt(Tag.MACIDNumber, -1));
    }

    @Test
    @DisplayName("Should handle complex security scenarios")
    void shouldHandleComplexSecurityScenarios() {
      // Create multiple MAC parameters with different algorithms
      MACParameters mac1 = new MACParameters();
      mac1.setMACIDNumber(1);
      mac1.setMACAlgorithm("RIPEMD160");
      mac1.setDataElementsSigned(new int[] {Tag.PatientID, Tag.PatientName});

      MACParameters mac2 = new MACParameters();
      mac2.setMACIDNumber(2);
      mac2.setMACAlgorithm("SHA256");
      mac2.setDataElementsSigned(new int[] {Tag.StudyInstanceUID, Tag.SeriesInstanceUID});

      // Verify independence
      assertNotEquals(mac1.getMACIDNumber(), mac2.getMACIDNumber());
      assertNotEquals(mac1.getMACAlgorithm(), mac2.getMACAlgorithm());
      assertFalse(
          java.util.Arrays.equals(mac1.getDataElementsSigned(), mac2.getDataElementsSigned()));
    }
  }
}
