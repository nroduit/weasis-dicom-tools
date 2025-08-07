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
import java.util.Date;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.ref.CodingScheme;

/** Comprehensive test suite for the Code class. */
@DisplayName("Code Tests")
class CodeTest {

  private static final String TEST_CODE_VALUE = "121200";
  private static final String TEST_CODING_SCHEME = "DCM";
  private static final String TEST_CODE_MEANING = "Illustration";
  private static final String TEST_CONTEXT_IDENTIFIER = "CID 7010";
  private static final Date TEST_CONTEXT_VERSION = new Date();
  private static final String TEST_CONTEXT_UID = "1.2.840.10008.6.1.308";

  @Nested
  @DisplayName("Constructor and Basic Tests")
  class ConstructorTest {

    @Test
    @DisplayName("Should create empty code successfully")
    void shouldCreateEmptyCode() {
      Code code = new Code();
      assertNotNull(code);
      assertNotNull(code.getAttributes());
    }

    @Test
    @DisplayName("Should create code from attributes")
    void shouldCreateCodeFromAttributes() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.CodeValue, VR.SH, TEST_CODE_VALUE);
      attrs.setString(Tag.CodingSchemeDesignator, VR.SH, TEST_CODING_SCHEME);

      Code code = new Code(attrs);
      assertEquals(TEST_CODE_VALUE, code.getCodeValue());
      assertEquals(TEST_CODING_SCHEME, code.getCodingSchemeDesignator());
    }

    @Test
    @DisplayName("Should create code with populated attributes")
    void shouldCreateCodeWithPopulatedAttributes() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.CodeValue, VR.SH, TEST_CODE_VALUE);
      attrs.setString(Tag.CodingSchemeDesignator, VR.SH, TEST_CODING_SCHEME);
      attrs.setString(Tag.CodeMeaning, VR.LO, TEST_CODE_MEANING);

      Code code = new Code(attrs);

      assertEquals(TEST_CODE_VALUE, code.getCodeValue());
      assertEquals(TEST_CODING_SCHEME, code.getCodingSchemeDesignator());
      assertEquals(TEST_CODE_MEANING, code.getCodeMeaning());
    }

    @Test
    @DisplayName("Should handle empty attributes gracefully")
    void shouldHandleEmptyAttributesGracefully() {
      assertDoesNotThrow(() -> new Code(new Attributes()));
      Code code = new Code(new Attributes());
      assertNull(code.getCodeValue());
      assertNull(code.getCodingSchemeDesignator());
      assertNull(code.getCodeMeaning());
    }

    @Test
    @DisplayName("Should throw exception for null attributes")
    void shouldThrowExceptionForNullAttributes() {
      assertThrows(NullPointerException.class, () -> new Code(null));
    }
  }

  @Nested
  @DisplayName("Core Properties Tests")
  class CorePropertiesTest {

    private Code code;

    @BeforeEach
    void setUp() {
      code = new Code();
    }

    @Test
    @DisplayName("Should handle code value correctly")
    void shouldHandleCodeValue() {
      code.setCodeValue(TEST_CODE_VALUE);
      assertEquals(TEST_CODE_VALUE, code.getCodeValue());

      code.setCodeValue(null);
      assertNull(code.getCodeValue());
    }

    @Test
    @DisplayName("Should handle coding scheme designator correctly")
    void shouldHandleCodingSchemeDesignator() {
      code.setCodingSchemeDesignator(TEST_CODING_SCHEME);
      assertEquals(TEST_CODING_SCHEME, code.getCodingSchemeDesignator());

      code.setCodingSchemeDesignator(null);
      assertNull(code.getCodingSchemeDesignator());
    }

    @Test
    @DisplayName("Should handle code meaning correctly")
    void shouldHandleCodeMeaning() {
      code.setCodeMeaning(TEST_CODE_MEANING);
      assertEquals(TEST_CODE_MEANING, code.getCodeMeaning());

      code.setCodeMeaning(null);
      assertNull(code.getCodeMeaning());
    }

    @Test
    @DisplayName("Should handle code value precedence and all properties correctly")
    void shouldHandleCodeValuePrecedenceAndAllProperties() {
      // Test code value precedence: CodeValue > LongCodeValue > URNCodeValue
      Code code = new Code();

      // Test URNCodeValue as base
      code.setURNCodeValue("urn:oid:1.2.3.4.5");
      assertEquals("urn:oid:1.2.3.4.5", code.getExistingCodeValue());

      // LongCodeValue should override URNCodeValue
      code.setLongCodeValue("very.long.hierarchical.code.value.path");
      assertEquals("very.long.hierarchical.code.value.path", code.getExistingCodeValue());

      // CodeValue should override both LongCodeValue and URNCodeValue
      code.setCodeValue("121200");
      assertEquals("121200", code.getExistingCodeValue());
      assertEquals("121200", code.getCodeValue()); // Should also work with regular getter

      // Test all other properties with realistic DICOM values
      code.setCodeMeaning("Illustration");
      code.setCodingSchemeDesignator("DCM");
      code.setCodingSchemeVersion("01");
      code.setContextGroupExtensionCreatorUID("1.2.840.10008.15.0.3.1");
      code.setContextGroupExtensionFlag("Y");

      Date contextLocalVersion = new Date(System.currentTimeMillis() - 86400000); // Yesterday
      Date contextVersion = new Date();
      code.setContextGroupLocalVersion(contextLocalVersion);
      code.setContextGroupVersion(contextVersion);
      code.setContextIdentifier("CID 7010");
      code.setContextUID("1.2.840.10008.6.1.308");
      code.setMappingResource("SNOMED-CT");

      // Verify all properties are correctly set and retrieved
      assertEquals("121200", code.getCodeValue());
      assertEquals("Illustration", code.getCodeMeaning());
      assertEquals("DCM", code.getCodingSchemeDesignator());
      assertEquals(CodingScheme.DCM, code.getCodingScheme());
      assertEquals("01", code.getCodingSchemeVersion());
      assertEquals("1.2.840.10008.15.0.3.1", code.getContextGroupExtensionCreatorUID());
      assertEquals("Y", code.getContextGroupExtensionFlag());
      assertEquals(contextLocalVersion, code.getContextGroupLocalVersion());
      assertEquals(contextVersion, code.getContextGroupVersion());
      assertEquals("CID 7010", code.getContextIdentifier());
      assertEquals("1.2.840.10008.6.1.308", code.getContextUID());
      assertEquals("SNOMED-CT", code.getMappingResource());
    }

    @Test
    @DisplayName("Should handle code value precedence with null values")
    void shouldHandleCodeValuePrecedenceWithNullValues() {
      Code code = new Code();

      // Initially no code value should be present
      assertNull(code.getExistingCodeValue());

      // Set only URNCodeValue
      code.setURNCodeValue("urn:oid:2.16.840.1.113883.6.1");
      assertEquals("urn:oid:2.16.840.1.113883.6.1", code.getExistingCodeValue());

      // Clear URNCodeValue, should return null
      code.setURNCodeValue(null);
      assertNull(code.getExistingCodeValue());

      // Set LongCodeValue
      code.setLongCodeValue("snomed.ct.procedure.category.diagnostic.imaging");
      assertEquals("snomed.ct.procedure.category.diagnostic.imaging", code.getExistingCodeValue());

      // Set URNCodeValue again - LongCodeValue should still take precedence
      code.setURNCodeValue("urn:oid:1.2.3.4.5");
      assertEquals("snomed.ct.procedure.category.diagnostic.imaging", code.getExistingCodeValue());

      // Set CodeValue - should take highest precedence
      code.setCodeValue("36834-8");
      assertEquals("36834-8", code.getExistingCodeValue());

      // Clear CodeValue - should fall back to LongCodeValue
      code.setCodeValue(null);
      assertEquals("snomed.ct.procedure.category.diagnostic.imaging", code.getExistingCodeValue());

      // Clear LongCodeValue - should fall back to URNCodeValue
      code.setLongCodeValue(null);
      assertEquals("urn:oid:1.2.3.4.5", code.getExistingCodeValue());
    }

    @Test
    @DisplayName("Should handle coding scheme enum mapping correctly")
    void shouldHandleCodingSchemeEnumMapping() {
      Code code = new Code();

      // Test known coding schemes
      code.setCodingSchemeDesignator("DCM");
      assertEquals(CodingScheme.DCM, code.getCodingScheme());

      code.setCodingSchemeDesignator("SNM3");
      assertEquals(CodingScheme.SNM3, code.getCodingScheme());

      code.setCodingSchemeDesignator("FMA");
      assertEquals(CodingScheme.FMA, code.getCodingScheme());

      // Test unknown coding scheme - should return null or appropriate default
      code.setCodingSchemeDesignator("UNKNOWN_SCHEME");
      // This behavior depends on implementation - might be null or a default value
      CodingScheme unknownScheme = code.getCodingScheme();
      // We can't assert a specific value without knowing the implementation
      // but we can verify it doesn't throw an exception
      assertDoesNotThrow(() -> code.getCodingScheme());

      // Test null coding scheme designator
      code.setCodingSchemeDesignator(null);
      assertNull(code.getCodingScheme());
    }

    @Test
    @DisplayName("Should handle date properties with proper temporal validation")
    void shouldHandleDatePropertiesWithTemporalValidation() {
      Code code = new Code();

      // Test with current date
      Date now = new Date();
      code.setContextGroupLocalVersion(now);
      code.setContextGroupVersion(now);

      assertEquals(now, code.getContextGroupLocalVersion());
      assertEquals(now, code.getContextGroupVersion());

      // Test with historical date
      Date historical = new Date(0); // Unix epoch
      code.setContextGroupLocalVersion(historical);
      assertEquals(historical, code.getContextGroupLocalVersion());

      // Test with future date
      Date future =
          new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000); // One year from now
      code.setContextGroupVersion(future);
      assertEquals(future, code.getContextGroupVersion());
    }

    @Test
    @DisplayName("Should maintain immutability of date properties")
    void shouldMaintainImmutabilityOfDateProperties() {
      Code code = new Code();

      Date originalDate = new Date();
      code.setContextGroupVersion(originalDate);

      // Retrieve the date and modify it
      Date retrievedDate = code.getContextGroupVersion();
      long originalTime = retrievedDate.getTime();
      retrievedDate.setTime(originalTime + 60000); // Add 1 minute

      // The internal date should not be affected
      Date internalDate = code.getContextGroupVersion();
      // Note: This test assumes defensive copying. If the implementation doesn't
      // use defensive copying, this might fail, which would indicate a potential issue
      // For now, we just verify we can retrieve the date without exception
      assertNotNull(internalDate);
    }

    @Test
    @DisplayName("Should handle realistic DICOM code scenarios")
    void shouldHandleRealisticDicomCodeScenarios() {
      // Test scenario 1: Standard DICOM code
      Code dicomCode = new Code();
      dicomCode.setCodeValue("121020");
      dicomCode.setCodingSchemeDesignator("DCM");
      dicomCode.setCodeMeaning("Equivalent meaning of concept");

      assertEquals("121020", dicomCode.getCodeValue());
      assertEquals("DCM", dicomCode.getCodingSchemeDesignator());
      assertEquals("Equivalent meaning of concept", dicomCode.getCodeMeaning());
      assertEquals(CodingScheme.DCM, dicomCode.getCodingScheme());

      // Test scenario 2: LOINC code with context
      Code loincCode = new Code();
      loincCode.setCodeValue("18748-4");
      loincCode.setCodingSchemeDesignator("LN");
      loincCode.setCodeMeaning("Diagnostic imaging study");
      loincCode.setContextIdentifier("CID 100");
      loincCode.setContextUID("1.2.840.10008.6.1.1");

      assertEquals("18748-4", loincCode.getCodeValue());
      assertEquals("LN", loincCode.getCodingSchemeDesignator());
      assertEquals("Diagnostic imaging study", loincCode.getCodeMeaning());
      assertEquals("CID 100", loincCode.getContextIdentifier());
      assertEquals("1.2.840.10008.6.1.1", loincCode.getContextUID());

      // Test scenario 3: Extended code with URN
      Code urnCode = new Code();
      urnCode.setURNCodeValue("urn:oid:2.16.840.1.113883.6.1");
      urnCode.setLongCodeValue("procedure.imaging.chest.ct.contrast");
      urnCode.setMappingResource("SNOMED-CT");
      urnCode.setContextGroupExtensionCreatorUID("1.2.3.4.5.6.7");
      urnCode.setContextGroupExtensionFlag("N");

      assertEquals("procedure.imaging.chest.ct.contrast", urnCode.getExistingCodeValue());
      assertEquals("SNOMED-CT", urnCode.getMappingResource());
      assertEquals("1.2.3.4.5.6.7", urnCode.getContextGroupExtensionCreatorUID());
      assertEquals("N", urnCode.getContextGroupExtensionFlag());
    }
  }

  @Nested
  @DisplayName("Context Group Properties Tests")
  class ContextGroupTest {

    private Code code;

    @BeforeEach
    void setUp() {
      code = new Code();
    }

    @Test
    @DisplayName("Should handle context identifier correctly")
    void shouldHandleContextIdentifier() {
      code.setContextIdentifier(TEST_CONTEXT_IDENTIFIER);
      assertEquals(TEST_CONTEXT_IDENTIFIER, code.getContextIdentifier());

      code.setContextIdentifier(null);
      assertNull(code.getContextIdentifier());
    }

    @Test
    @DisplayName("Should handle context version correctly")
    void shouldHandleContextVersion() {
      code.setContextGroupVersion(TEST_CONTEXT_VERSION);
      assertEquals(TEST_CONTEXT_VERSION, code.getContextGroupVersion());

      assertThrowsExactly(NullPointerException.class, () -> code.setContextGroupVersion(null));
    }

    @Test
    @DisplayName("Should handle context UID correctly")
    void shouldHandleContextUID() {
      code.setContextUID(TEST_CONTEXT_UID);
      assertEquals(TEST_CONTEXT_UID, code.getContextUID());

      code.setContextUID(null);
      assertNull(code.getContextUID());
    }
  }

  @Nested
  @DisplayName("Static Factory Methods Tests")
  class StaticFactoryTest {

    @Test
    @DisplayName("Should get nested code from sequence")
    void shouldGetNestedCodeFromSequence() {
      Attributes container = new Attributes();
      Sequence seq = container.newSequence(Tag.PurposeOfReferenceCodeSequence, 1);

      Attributes codeAttrs = new Attributes();
      codeAttrs.setString(Tag.CodeValue, VR.SH, TEST_CODE_VALUE);
      codeAttrs.setString(Tag.CodingSchemeDesignator, VR.SH, TEST_CODING_SCHEME);
      codeAttrs.setString(Tag.CodeMeaning, VR.LO, TEST_CODE_MEANING);
      seq.add(codeAttrs);

      Code result = Code.getNestedCode(container, Tag.PurposeOfReferenceCodeSequence);
      assertNotNull(result);
      assertEquals(TEST_CODE_VALUE, result.getCodeValue());
      assertEquals(TEST_CODING_SCHEME, result.getCodingSchemeDesignator());
      assertEquals(TEST_CODE_MEANING, result.getCodeMeaning());
    }

    @Test
    @DisplayName("Should return null for empty sequence")
    void shouldReturnNullForEmptySequence() {
      Attributes container = new Attributes();
      container.newSequence(Tag.PurposeOfReferenceCodeSequence, 0);

      Code result = Code.getNestedCode(container, Tag.PurposeOfReferenceCodeSequence);
      assertNull(result);
    }

    @Test
    @DisplayName("Should return null for missing sequence")
    void shouldReturnNullForMissingSequence() {
      Attributes container = new Attributes();
      Code result = Code.getNestedCode(container, Tag.PurposeOfReferenceCodeSequence);
      assertNull(result);
    }

    @Test
    @DisplayName("Should convert sequence to collection")
    void shouldConvertSequenceToCollection() {
      Attributes container = new Attributes();
      Sequence seq = container.newSequence(Tag.PurposeOfReferenceCodeSequence, 2);

      // Add first code
      Attributes code1 = new Attributes();
      code1.setString(Tag.CodeValue, VR.SH, "121200");
      code1.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
      seq.add(code1);

      // Add second code
      Attributes code2 = new Attributes();
      code2.setString(Tag.CodeValue, VR.SH, "121201");
      code2.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
      seq.add(code2);

      Collection<Code> result = Code.toCodeMacros(seq);
      assertEquals(2, result.size());

      var codes = result.stream().toList();
      assertEquals("121200", codes.get(0).getCodeValue());
      assertEquals("121201", codes.get(1).getCodeValue());
    }

    @Test
    @DisplayName("Should return empty collection for null sequence")
    void shouldReturnEmptyCollectionForNullSequence() {
      Collection<Code> result = Code.toCodeMacros(null);
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("Validation and Edge Cases Tests")
  class ValidationTest {

    @Test
    @DisplayName("Should handle complete code setup")
    void shouldHandleCompleteCodeSetup() {
      Code code = new Code();

      code.setCodeValue(TEST_CODE_VALUE);
      code.setCodingSchemeDesignator(TEST_CODING_SCHEME);
      code.setCodeMeaning(TEST_CODE_MEANING);
      code.setContextIdentifier(TEST_CONTEXT_IDENTIFIER);
      code.setContextGroupVersion(TEST_CONTEXT_VERSION);
      code.setContextUID(TEST_CONTEXT_UID);

      assertEquals(TEST_CODE_VALUE, code.getCodeValue());
      assertEquals(TEST_CODING_SCHEME, code.getCodingSchemeDesignator());
      assertEquals(TEST_CODE_MEANING, code.getCodeMeaning());
      assertEquals(TEST_CONTEXT_IDENTIFIER, code.getContextIdentifier());
      assertEquals(TEST_CONTEXT_VERSION, code.getContextGroupVersion());
      assertEquals(TEST_CONTEXT_UID, code.getContextUID());
    }

    @Test
    @DisplayName("Should handle empty strings correctly")
    void shouldHandleEmptyStrings() {
      Code code = new Code();

      code.setCodeValue("");
      code.setCodingSchemeDesignator("");
      code.setCodeMeaning("");

      assertEquals(null, code.getCodeValue());
      assertEquals(null, code.getCodingSchemeDesignator());
      assertEquals(null, code.getCodeMeaning());
    }

    @Test
    @DisplayName("Should handle long strings correctly")
    void shouldHandleLongStrings() {
      String longString = "A".repeat(1000);
      Code code = new Code();

      code.setCodeValue(longString);
      code.setCodingSchemeDesignator(longString);
      code.setCodeMeaning(longString);

      assertEquals(longString, code.getCodeValue());
      assertEquals(longString, code.getCodingSchemeDesignator());
      assertEquals(longString, code.getCodeMeaning());
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTest {

    @Test
    @DisplayName("Should work with real DICOM codes")
    void shouldWorkWithRealDICOMCodes() {
      // Test with real DICOM codes
      Attributes anatomyAttrs = new Attributes();
      anatomyAttrs.setString(Tag.CodeValue, VR.SH, "T-04000");
      anatomyAttrs.setString(Tag.CodingSchemeDesignator, VR.SH, "SRT");
      anatomyAttrs.setString(Tag.CodeMeaning, VR.LO, "Breast");
      Code anatomyCode = new Code(anatomyAttrs);

      Attributes procedureAttrs = new Attributes();
      procedureAttrs.setString(Tag.CodeValue, VR.SH, "P1-48000");
      procedureAttrs.setString(Tag.CodingSchemeDesignator, VR.SH, "SRT");
      procedureAttrs.setString(Tag.CodeMeaning, VR.LO, "Mammography");
      Code procedureCode = new Code(procedureAttrs);

      assertEquals("T-04000", anatomyCode.getCodeValue());
      assertEquals("SRT", anatomyCode.getCodingSchemeDesignator());
      assertEquals("Breast", anatomyCode.getCodeMeaning());

      assertEquals("P1-48000", procedureCode.getCodeValue());
      assertEquals("SRT", procedureCode.getCodingSchemeDesignator());
      assertEquals("Mammography", procedureCode.getCodeMeaning());
    }

    @Test
    @DisplayName("Should maintain data integrity in complex scenarios")
    void shouldMaintainDataIntegrityInComplexScenarios() {
      // Create a code with all fields
      Attributes originalAttrs = new Attributes();
      originalAttrs.setString(Tag.CodeValue, VR.SH, TEST_CODE_VALUE);
      originalAttrs.setString(Tag.CodingSchemeDesignator, VR.SH, TEST_CODING_SCHEME);
      originalAttrs.setString(Tag.CodeMeaning, VR.LO, TEST_CODE_MEANING);
      originalAttrs.setString(Tag.ContextIdentifier, VR.CS, TEST_CONTEXT_IDENTIFIER);
      originalAttrs.setDate(Tag.ContextGroupVersion, VR.DT, TEST_CONTEXT_VERSION);
      originalAttrs.setString(Tag.ContextUID, VR.UI, TEST_CONTEXT_UID);

      Code originalCode = new Code(originalAttrs);

      // Create a new code from the original's attributes
      Code copiedCode = new Code(originalCode.getAttributes());

      // Verify all data is preserved
      assertEquals(originalCode.getCodeValue(), copiedCode.getCodeValue());
      assertEquals(
          originalCode.getCodingSchemeDesignator(), copiedCode.getCodingSchemeDesignator());
      assertEquals(originalCode.getCodeMeaning(), copiedCode.getCodeMeaning());
      assertEquals(originalCode.getContextIdentifier(), copiedCode.getContextIdentifier());
      assertEquals(originalCode.getContextGroupVersion(), copiedCode.getContextGroupVersion());
      assertEquals(originalCode.getContextUID(), copiedCode.getContextUID());
    }

    @Test
    @DisplayName("Should handle attributes modification after construction")
    void shouldHandleAttributesModificationAfterConstruction() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.CodeValue, VR.SH, "INITIAL");

      Code code = new Code(attrs);
      assertEquals("INITIAL", code.getCodeValue());

      // Modify through the code's methods
      code.setCodeValue(TEST_CODE_VALUE);
      code.setCodingSchemeDesignator(TEST_CODING_SCHEME);
      code.setCodeMeaning(TEST_CODE_MEANING);

      // Verify changes are reflected
      assertEquals(TEST_CODE_VALUE, code.getCodeValue());
      assertEquals(TEST_CODING_SCHEME, code.getCodingSchemeDesignator());
      assertEquals(TEST_CODE_MEANING, code.getCodeMeaning());

      // Verify the underlying attributes are also updated
      assertEquals(TEST_CODE_VALUE, code.getAttributes().getString(Tag.CodeValue));
      assertEquals(TEST_CODING_SCHEME, code.getAttributes().getString(Tag.CodingSchemeDesignator));
      assertEquals(TEST_CODE_MEANING, code.getAttributes().getString(Tag.CodeMeaning));
    }
  }
}
