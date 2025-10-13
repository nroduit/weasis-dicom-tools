/*
 * Copyright (c) 2017-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.param;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class CstoreParamsTest {

  private static final String TEST_URL = "http://example.com/config.xml";

  @Nested
  class Constructor_with_all_parameters {

    @Test
    void creates_params_with_all_valid_parameters() throws MalformedURLException {
      var editors = createTestEditors();
      var url = URI.create(TEST_URL).toURL();

      var params = new CstoreParams(editors, true, url);

      assertThat(params).hasEditors(editors).hasExtendNegotiation(true).hasExtendSopClassesURL(url);
    }

    @Test
    void creates_params_with_null_editors() throws MalformedURLException {
      var url = URI.create(TEST_URL).toURL();

      var params = new CstoreParams(null, true, url);

      assertThat(params).hasEmptyEditors().hasExtendNegotiation(true).hasExtendSopClassesURL(url);
    }

    @Test
    void creates_params_with_empty_editors() throws MalformedURLException {
      var url = URI.create(TEST_URL).toURL();

      var params = new CstoreParams(List.of(), true, url);

      assertThat(params).hasEmptyEditors().hasExtendNegotiation(true).hasExtendSopClassesURL(url);
    }

    @Test
    void creates_params_with_extend_negotiation_disabled() {
      var editors = createTestEditors();

      var params = new CstoreParams(editors, false, null);

      assertThat(params)
          .hasEditors(editors)
          .hasExtendNegotiation(false)
          .hasNullExtendSopClassesURL();
    }

    @Test
    void throws_exception_when_extend_negotiation_enabled_but_url_is_null() {
      var editors = createTestEditors();

      var exception =
          assertThrows(IllegalArgumentException.class, () -> new CstoreParams(editors, true, null));

      assertEquals(
          "Extended negotiation is enabled but no configuration URL provided",
          exception.getMessage());
    }
  }

  @Nested
  class Constructor_with_editors_only {

    @Test
    void creates_params_with_editors_only() {
      var editors = createTestEditors();

      var params = new CstoreParams(editors);

      assertThat(params)
          .hasEditors(editors)
          .hasExtendNegotiation(false)
          .hasNullExtendSopClassesURL();
    }

    @Test
    void creates_params_with_null_editors() {
      var params = new CstoreParams(null);

      assertThat(params).hasEmptyEditors().hasExtendNegotiation(false).hasNullExtendSopClassesURL();
    }

    @Test
    void creates_params_with_empty_editors() {
      var params = new CstoreParams(List.of());

      assertThat(params).hasEmptyEditors().hasExtendNegotiation(false).hasNullExtendSopClassesURL();
    }
  }

  @Nested
  class Get_dicom_editors {

    @Test
    void returns_immutable_list_of_editors() {
      var editors = createTestEditors();
      var params = new CstoreParams(editors);

      var result = params.getDicomEditors();

      assertEquals(editors, result);
      assertThrows(
          UnsupportedOperationException.class, () -> result.add(mock(AttributeEditor.class)));
    }

    @Test
    void returns_empty_list_when_no_editors() {
      var params = new CstoreParams(null);

      var result = params.getDicomEditors();

      assertTrue(result.isEmpty());
      assertThrows(
          UnsupportedOperationException.class, () -> result.add(mock(AttributeEditor.class)));
    }

    @Test
    void defensive_copy_prevents_external_modification() {
      var editors = new ArrayList<AttributeEditor>();
      editors.add(createDefaultAttributeEditor());
      var params = new CstoreParams(editors);

      // Modify original list
      editors.add(mock(AttributeEditor.class));

      // Params should be unchanged
      assertEquals(1, params.getDicomEditors().size());
    }
  }

  @Nested
  class Has_dicom_editors {

    @Test
    void returns_true_when_editors_present() {
      var editors = createTestEditors();
      var params = new CstoreParams(editors);

      assertTrue(params.hasDicomEditors());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "empty"})
    void returns_false_when_no_editors(String editorType) {
      List<AttributeEditor> editors =
          switch (editorType) {
            case "null" -> null;
            case "empty" -> List.of();
            default -> throw new IllegalArgumentException("Unknown type: " + editorType);
          };

      var params = new CstoreParams(editors);

      assertFalse(params.hasDicomEditors());
    }
  }

  @Nested
  class Is_extend_negotiation {

    @Test
    void returns_true_when_extend_negotiation_enabled() throws MalformedURLException {
      var url = URI.create(TEST_URL).toURL();
      var params = new CstoreParams(null, true, url);

      assertTrue(params.isExtendNegotiation());
    }

    @Test
    void returns_false_when_extend_negotiation_disabled() {
      var params = new CstoreParams(null, false, null);

      assertFalse(params.isExtendNegotiation());
    }
  }

  @Nested
  class Get_extend_sop_classes_url {

    @Test
    void returns_url_when_set() throws MalformedURLException {
      var url = URI.create(TEST_URL).toURL();
      var params = new CstoreParams(null, true, url);

      assertEquals(url, params.getExtendSopClassesURL());
    }

    @Test
    void returns_null_when_not_set() {
      var params = new CstoreParams(null, false, null);

      assertNull(params.getExtendSopClassesURL());
    }
  }

  @Nested
  class Equals_and_hash_code {

    @Test
    void equals_returns_true_for_same_instance() {
      var params = new CstoreParams(null);

      assertEquals(params, params);
    }

    @Test
    void equals_returns_true_for_identical_params() throws MalformedURLException {
      var editors = createTestEditors();
      var url = URI.create(TEST_URL).toURL();

      var params1 = new CstoreParams(editors, true, url);
      var params2 = new CstoreParams(editors, true, url);

      assertEquals(params1, params2);
    }

    @Test
    void equals_returns_false_for_different_editors() {
      var editors1 = createTestEditors();
      var editors2 = List.of(mock(AttributeEditor.class));

      var params1 = new CstoreParams(editors1);
      var params2 = new CstoreParams(editors2);

      assertNotEquals(params1, params2);
    }

    @Test
    void equals_returns_false_for_different_extend_negotiation() throws MalformedURLException {
      var url = URI.create(TEST_URL).toURL();

      var params1 = new CstoreParams(null, true, url);
      var params2 = new CstoreParams(null, false, null);

      assertNotEquals(params1, params2);
    }

    @Test
    void equals_returns_false_for_different_urls() throws MalformedURLException {
      var url1 = URI.create("http://example1.com/config.xml").toURL();
      var url2 = URI.create("http://example2.com/config.xml").toURL();

      var params1 = new CstoreParams(null, true, url1);
      var params2 = new CstoreParams(null, true, url2);

      assertNotEquals(params1, params2);
    }

    @Test
    void equals_returns_false_for_null() {
      var params = new CstoreParams(null);

      assertNotEquals(null, params);
    }

    @Test
    void equals_returns_false_for_different_class() {
      var params = new CstoreParams(null);

      assertNotEquals("not a CstoreParams", params);
    }

    @Test
    void hash_code_is_consistent() throws MalformedURLException {
      var editors = createTestEditors();
      var url = URI.create(TEST_URL).toURL();
      var params = new CstoreParams(editors, true, url);

      int hash1 = params.hashCode();
      int hash2 = params.hashCode();

      assertEquals(hash1, hash2);
    }

    @Test
    void hash_code_is_equal_for_equal_objects() throws MalformedURLException {
      var editors = createTestEditors();
      var url = URI.create(TEST_URL).toURL();

      var params1 = new CstoreParams(editors, true, url);
      var params2 = new CstoreParams(editors, true, url);

      assertEquals(params1.hashCode(), params2.hashCode());
    }
  }

  @Nested
  class To_string {

    @Test
    void includes_all_relevant_information() throws MalformedURLException {
      var editors = createTestEditors();
      var url = URI.create(TEST_URL).toURL();
      var params = new CstoreParams(editors, true, url);

      var result = params.toString();

      assertAll(
          () -> assertTrue(result.contains("CstoreParams")),
          () -> assertTrue(result.contains("editorsCount=" + editors.size())),
          () -> assertTrue(result.contains("extendNegotiation=true")),
          () -> assertTrue(result.contains("extendSopClassesURL=" + url)));
    }

    @Test
    void handles_null_url() {
      var params = new CstoreParams(null, false, null);

      var result = params.toString();

      assertAll(
          () -> assertTrue(result.contains("editorsCount=0")),
          () -> assertTrue(result.contains("extendNegotiation=false")),
          () -> assertTrue(result.contains("extendSopClassesURL=null")));
    }
  }

  // Helper methods
  private List<AttributeEditor> createTestEditors() {
    return List.of(createDefaultAttributeEditor(), createMockAttributeEditor());
  }

  private DefaultAttributeEditor createDefaultAttributeEditor() {
    var attributes = new Attributes();
    attributes.setString(Tag.PatientName, VR.PN, "Test^Patient");
    attributes.setString(Tag.PatientID, VR.LO, "TEST123");
    return new DefaultAttributeEditor(false, attributes);
  }

  private AttributeEditor createMockAttributeEditor() {
    return mock(AttributeEditor.class);
  }

  // Custom assertion methods for better readability
  private static CstoreParamsAssert assertThat(CstoreParams params) {
    return new CstoreParamsAssert(params);
  }

  private static final class CstoreParamsAssert {
    private final CstoreParams params;

    private CstoreParamsAssert(CstoreParams params) {
      this.params = params;
    }

    CstoreParamsAssert hasEditors(List<AttributeEditor> expected) {
      assertEquals(expected, params.getDicomEditors());
      return this;
    }

    CstoreParamsAssert hasEmptyEditors() {
      assertTrue(params.getDicomEditors().isEmpty());
      return this;
    }

    CstoreParamsAssert hasExtendNegotiation(boolean expected) {
      assertEquals(expected, params.isExtendNegotiation());
      return this;
    }

    CstoreParamsAssert hasExtendSopClassesURL(URL expected) {
      assertEquals(expected, params.getExtendSopClassesURL());
      return this;
    }

    CstoreParamsAssert hasNullExtendSopClassesURL() {
      assertNull(params.getExtendSopClassesURL());
      return this;
    }
  }
}
