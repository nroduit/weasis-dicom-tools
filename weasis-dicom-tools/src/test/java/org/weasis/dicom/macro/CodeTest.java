/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.ref.CodingScheme;

class CodeTest {

  @Test
  void getCode_returnsCorrectValues() {
    Attributes attrs = new Attributes();
    Code code = new Code(attrs);
    code.setURNCodeValue("URNCodeValue");
    assertEquals("URNCodeValue", code.getExistingCodeValue());
    code.setLongCodeValue("longCodeValue");
    assertEquals("longCodeValue", code.getExistingCodeValue());
    code.setCodeValue("testCode");
    assertEquals("testCode", code.getExistingCodeValue());
    code.setCodeMeaning("testMeaning");
    code.setCodingSchemeDesignator("FMA");
    code.setCodingSchemeVersion("testVersion");
    code.setContextGroupExtensionCreatorUID("testContextGroupExtensionCreatorUID");
    code.setContextGroupExtensionFlag("testContextGroupExtensionFlag");
    Date date = new Date();
    code.setContextGroupLocalVersion(date);
    code.setContextGroupVersion(date);
    code.setContextIdentifier("testContextIdentifier");
    code.setContextUID("testContextUID");
    code.setMappingResource("testMappingResource");

    assertEquals("testCode", code.getCodeValue());
    assertEquals("testMeaning", code.getCodeMeaning());
    assertEquals("FMA", code.getCodingSchemeDesignator());
    assertEquals(CodingScheme.FMA, code.getCodingScheme());
    assertEquals("testVersion", code.getCodingSchemeVersion());
    assertEquals("testContextGroupExtensionCreatorUID", code.getContextGroupExtensionCreatorUID());
    assertEquals("testContextGroupExtensionFlag", code.getContextGroupExtensionFlag());
    assertEquals(date, code.getContextGroupLocalVersion());
    assertEquals(date, code.getContextGroupVersion());
    assertEquals("testContextIdentifier", code.getContextIdentifier());
    assertEquals("testContextUID", code.getContextUID());
    assertEquals("testMappingResource", code.getMappingResource());
  }

  @Test
  void getCode_returnsNullWhenNotSet() {
    Code code = new Code();
    assertNull(code.getCodeValue());
    assertNull(code.getCodeMeaning());
    assertNull(code.getCodingSchemeDesignator());
  }

  @Test
  void toCodeMacros_returnsEmptyListWhenSequenceIsNull() {
    List<Code> result = Code.toCodeMacros(null);
    assertTrue(result.isEmpty());
  }

  @Test
  void toCodeMacros_returnsListOfCodesWhenSequenceIsNotEmpty() {
    Attributes dcm = new Attributes();
    Sequence seq = dcm.newSequence(Tag.AnatomicRegionModifierSequence, 1);
    List<Code> result = Code.toCodeMacros(seq);
    assertTrue(result.isEmpty());

    Attributes attrs = new Attributes();
    attrs.setString(Tag.CodeValue, VR.SH, "testCode");
    seq.add(attrs);
    result = Code.toCodeMacros(seq);
    assertEquals(1, result.size());
    assertEquals("testCode", result.get(0).getCodeValue());
  }
}
