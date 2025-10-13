/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SopInstanceTest {

  private static final String SOP_INSTANCE_UID = "1.2.3.4.5";
  private static final String SOP_CLASS_UID = "1.2.840.10008.5.1.4.1.1.1";
  private static final Integer INSTANCE_NUMBER = 1;
  private static final String TRANSFER_SYNTAX_UID = "1.2.840.10008.1.2.1";

  private SopInstance sopInstance;

  @BeforeEach
  void setUp() {
    sopInstance = new SopInstance(SOP_INSTANCE_UID, SOP_CLASS_UID, INSTANCE_NUMBER);
  }

  @Nested
  class Constructor_Tests {

    @Test
    void constructor_with_uid_and_number_creates_instance() {
      var instance = new SopInstance("1.2.3.4.6", 2);

      assertEquals("1.2.3.4.6", instance.getSopInstanceUID());
      assertNull(instance.getSopClassUID());
      assertEquals(2, instance.getInstanceNumber());
    }

    @Test
    void constructor_with_full_parameters_creates_instance() {
      assertEquals(SOP_INSTANCE_UID, sopInstance.getSopInstanceUID());
      assertEquals(SOP_CLASS_UID, sopInstance.getSopClassUID());
      assertEquals(INSTANCE_NUMBER, sopInstance.getInstanceNumber());
    }

    @Test
    void constructor_with_null_sop_instance_uid_throws_exception() {
      assertThrows(
          NullPointerException.class, () -> new SopInstance(null, SOP_CLASS_UID, INSTANCE_NUMBER));
    }

    @Test
    void constructor_accepts_null_sop_class_uid() {
      var instance = new SopInstance(SOP_INSTANCE_UID, null, INSTANCE_NUMBER);
      assertNull(instance.getSopClassUID());
    }

    @Test
    void constructor_accepts_null_instance_number() {
      var instance = new SopInstance(SOP_INSTANCE_UID, SOP_CLASS_UID, null);
      assertNull(instance.getInstanceNumber());
    }
  }

  @Nested
  class Basic_Properties_Tests {

    @Test
    void getStringInstanceNumber_returns_string_representation() {
      assertEquals("1", sopInstance.getStringInstanceNumber());
    }

    @Test
    void getStringInstanceNumber_with_null_returns_null() {
      var instance = new SopInstance(SOP_INSTANCE_UID, null);
      assertNull(instance.getStringInstanceNumber());
    }

    @Test
    void setTransferSyntaxUID_with_valid_uid_sets_trimmed_value() {
      sopInstance.setTransferSyntaxUID("  " + TRANSFER_SYNTAX_UID + "  ");
      assertEquals(TRANSFER_SYNTAX_UID, sopInstance.getTransferSyntaxUID());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void setTransferSyntaxUID_with_null_or_empty_sets_null(String input) {
      sopInstance.setTransferSyntaxUID(input);
      assertNull(sopInstance.getTransferSyntaxUID());
    }

    @Test
    void setImageComments_sets_and_gets_value() {
      String comments = "Test comments";
      sopInstance.setImageComments(comments);
      assertEquals(comments, sopInstance.getImageComments());
    }

    @Test
    void setDirectDownloadFile_sets_and_gets_value() {
      String file = "http://example.com/file.dcm";
      sopInstance.setDirectDownloadFile(file);
      assertEquals(file, sopInstance.getDirectDownloadFile());
    }

    @Test
    void setGraphicModel_sets_and_gets_value() {
      Object model = new Object();
      sopInstance.setGraphicModel(model);
      assertEquals(model, sopInstance.getGraphicModel());
    }
  }

  @Nested
  class XML_Serialization_Tests {

    @Test
    void toXml_with_complete_instance_generates_valid_xml() throws IOException {
      setupCompleteInstance();

      var writer = new StringWriter();
      sopInstance.toXml(writer);
      String xml = writer.toString();

      assertAll(
          () -> assertTrue(xml.contains("<Instance ")),
          () -> assertTrue(xml.contains("SOPInstanceUID=\"" + SOP_INSTANCE_UID + "\"")),
          () -> assertTrue(xml.contains("SOPClassUID=\"" + SOP_CLASS_UID + "\"")),
          () -> assertTrue(xml.contains("TransferSyntaxUID=\"" + TRANSFER_SYNTAX_UID + "\"")),
          () -> assertTrue(xml.contains("InstanceNumber=\"1\"")),
          () -> assertTrue(xml.contains("/>")),
          () -> assertFalse(xml.contains("</Instance>")) // Self-closing tag
          );
    }

    @Test
    void toXml_with_minimal_instance_generates_valid_xml() throws IOException {
      var minimalInstance = new SopInstance(SOP_INSTANCE_UID, 1);

      var writer = new StringWriter();
      minimalInstance.toXml(writer);
      String xml = writer.toString();

      assertTrue(xml.contains("SOPInstanceUID=\"" + SOP_INSTANCE_UID + "\""));
      assertFalse(xml.contains("SOPClassUID="));
    }

    private void setupCompleteInstance() {
      sopInstance.setTransferSyntaxUID(TRANSFER_SYNTAX_UID);
      sopInstance.setImageComments("Test image");
      sopInstance.setDirectDownloadFile("http://example.com/file.dcm");
    }
  }

  @Nested
  class Comparison_Tests {

    @Test
    void compareTo_prioritizes_instance_numbers() {
      var instance1 = new SopInstance("1.2.3.4.5", 1);
      var instance2 = new SopInstance("1.2.3.4.6", 2);

      assertTrue(instance1.compareTo(instance2) < 0);
      assertTrue(instance2.compareTo(instance1) > 0);
    }

    @Test
    void compareTo_falls_back_to_sop_instance_uid() {
      var instance1 = new SopInstance("1.2.3.4.5", 1);
      var instance2 = new SopInstance("1.2.3.4.6", 1);

      assertTrue(instance1.compareTo(instance2) < 0);
    }

    @Test
    void compareTo_handles_null_instance_numbers() {
      var instance1 = new SopInstance("1.2.3.4.5", (Integer) null);
      var instance2 = new SopInstance("1.2.3.4.6", 1);

      assertTrue(instance1.compareTo(instance2) > 0); // Null comes after
    }

    @Test
    void compareTo_normalizes_uid_lengths_for_proper_sorting() {
      // Test UIDs with different lengths to verify normalization
      var instance1 = new SopInstance("1.2.3.4.5.1", 1);
      var instance2 = new SopInstance("1.2.3.4.5.10", 1);

      // Should be sorted numerically, not lexicographically
      assertTrue(instance1.compareTo(instance2) < 0);
    }

    @Test
    void compareTo_with_same_instance_returns_zero() {
      assertEquals(0, sopInstance.compareTo(sopInstance));
    }
  }

  @Nested
  class Static_Map_Operations_Tests {

    private Map<String, SopInstance> instanceMap;

    @BeforeEach
    void setUp() {
      instanceMap = new HashMap<>();
    }

    @Test
    void addSopInstance_with_valid_instance_adds_to_map() {
      SopInstance.addSopInstance(instanceMap, sopInstance);

      assertEquals(1, instanceMap.size());
      assertTrue(instanceMap.containsValue(sopInstance));
    }

    @Test
    void addSopInstance_with_null_instance_ignores_operation() {
      SopInstance.addSopInstance(instanceMap, null);
      assertTrue(instanceMap.isEmpty());
    }

    @Test
    void addSopInstance_with_null_map_ignores_operation() {
      assertDoesNotThrow(() -> SopInstance.addSopInstance(null, sopInstance));
    }

    @Test
    void getSopInstance_with_existing_parameters_returns_instance() {
      SopInstance.addSopInstance(instanceMap, sopInstance);

      assertEquals(
          sopInstance, SopInstance.getSopInstance(instanceMap, SOP_INSTANCE_UID, INSTANCE_NUMBER));
    }

    @Test
    void getSopInstance_with_non_existing_parameters_returns_null() {
      assertNull(SopInstance.getSopInstance(instanceMap, "non.existing", 1));
    }

    @Test
    void getSopInstance_with_null_uid_returns_null() {
      assertNull(SopInstance.getSopInstance(instanceMap, null, INSTANCE_NUMBER));
    }

    @Test
    void getSopInstance_with_null_map_returns_null() {
      assertNull(SopInstance.getSopInstance(null, SOP_INSTANCE_UID, INSTANCE_NUMBER));
    }

    @Test
    void removeSopInstance_with_existing_parameters_removes_and_returns_instance() {
      SopInstance.addSopInstance(instanceMap, sopInstance);

      assertEquals(
          sopInstance,
          SopInstance.removeSopInstance(instanceMap, SOP_INSTANCE_UID, INSTANCE_NUMBER));
      assertTrue(instanceMap.isEmpty());
    }

    @Test
    void removeSopInstance_with_non_existing_parameters_returns_null() {
      assertNull(SopInstance.removeSopInstance(instanceMap, "non.existing", 1));
    }

    @Test
    void map_operations_handle_null_instance_numbers() {
      var instanceWithoutNumber = new SopInstance(SOP_INSTANCE_UID, (Integer) null);

      SopInstance.addSopInstance(instanceMap, instanceWithoutNumber);
      assertEquals(
          instanceWithoutNumber, SopInstance.getSopInstance(instanceMap, SOP_INSTANCE_UID, null));
      assertEquals(
          instanceWithoutNumber,
          SopInstance.removeSopInstance(instanceMap, SOP_INSTANCE_UID, null));
    }
  }

  @Nested
  class Equality_Tests {

    @Test
    void equals_with_same_uid_and_instance_number_returns_true() {
      var instance1 = new SopInstance(SOP_INSTANCE_UID, INSTANCE_NUMBER);
      var instance2 = new SopInstance(SOP_INSTANCE_UID, INSTANCE_NUMBER);

      assertEquals(instance1, instance2);
      assertEquals(instance1.hashCode(), instance2.hashCode());
    }

    @Test
    void equals_with_different_uid_returns_false() {
      var instance1 = new SopInstance("1.2.3.4.5", INSTANCE_NUMBER);
      var instance2 = new SopInstance("1.2.3.4.6", INSTANCE_NUMBER);

      assertNotEquals(instance1, instance2);
    }

    @Test
    void equals_with_different_instance_number_returns_false() {
      var instance1 = new SopInstance(SOP_INSTANCE_UID, 1);
      var instance2 = new SopInstance(SOP_INSTANCE_UID, 2);

      assertNotEquals(instance1, instance2);
    }

    @Test
    void equals_handles_null_instance_numbers() {
      var instance1 = new SopInstance(SOP_INSTANCE_UID, (Integer) null);
      var instance2 = new SopInstance(SOP_INSTANCE_UID, (Integer) null);
      var instance3 = new SopInstance(SOP_INSTANCE_UID, 1);

      assertEquals(instance1, instance2);
      assertNotEquals(instance1, instance3);
      assertNotEquals(instance3, instance1);
    }

    @Test
    void equals_with_self_returns_true() {
      assertEquals(sopInstance, sopInstance);
    }

    @Test
    void equals_with_null_returns_false() {
      assertNotEquals(sopInstance, null);
    }

    @Test
    void equals_with_different_class_returns_false() {
      assertNotEquals(sopInstance, "string");
    }
  }
}
