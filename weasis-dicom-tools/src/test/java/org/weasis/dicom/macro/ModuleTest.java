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

/** Comprehensive test suite for the Module base class. */
@DisplayName("Module Tests")
class ModuleTest {

  // Test implementation of Module for testing purposes
  private static class TestModule extends Module {
    public TestModule(Attributes dcmItems) {
      super(dcmItems);
    }

    public TestModule() {
      super(new Attributes());
    }

    // Expose protected methods for testing
    public void testUpdateSequence(int tag, Module module) {
      updateSequence(tag, module);
    }

    public void testUpdateSequence(int tag, Collection<? extends Module> modules) {
      updateSequence(tag, modules);
    }
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTest {

    @Test
    @DisplayName("Should create module with valid attributes")
    void shouldCreateModuleWithValidAttributes() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5");

      TestModule module = new TestModule(attrs);
      assertNotNull(module);
      assertEquals(attrs, module.getAttributes());
      assertEquals("1.2.3.4.5", module.getAttributes().getString(Tag.StudyInstanceUID));
    }

    @Test
    @DisplayName("Should throw exception for null attributes")
    void shouldThrowExceptionForNullAttributes() {
      assertThrows(NullPointerException.class, () -> new TestModule(null));
    }

    @Test
    @DisplayName("Should create module with empty attributes")
    void shouldCreateModuleWithEmptyAttributes() {
      TestModule module = new TestModule();
      assertNotNull(module);
      assertNotNull(module.getAttributes());
      assertTrue(module.getAttributes().isEmpty());
    }
  }

  @Nested
  @DisplayName("Attributes Access Tests")
  class AttributesAccessTest {

    private TestModule module;
    private Attributes attributes;

    @BeforeEach
    void setUp() {
      attributes = new Attributes();
      module = new TestModule(attributes);
    }

    @Test
    @DisplayName("Should return same attributes instance")
    void shouldReturnSameAttributesInstance() {
      assertSame(attributes, module.getAttributes());
    }

    @Test
    @DisplayName("Should allow modifications through getAttributes")
    void shouldAllowModificationsThroughGetAttributes() {
      module.getAttributes().setString(Tag.PatientID, VR.LO, "12345");
      assertEquals("12345", attributes.getString(Tag.PatientID));
    }
  }

  @Nested
  @DisplayName("Sequence Manipulation Tests")
  class SequenceManipulationTest {

    private TestModule module;
    private Attributes attributes;

    @BeforeEach
    void setUp() {
      attributes = new Attributes();
      module = new TestModule(attributes);
    }

    @Test
    @DisplayName("Should remove all sequence items")
    void shouldRemoveAllSequenceItems() {
      // Create a sequence with items
      Sequence seq = attributes.newSequence(Tag.ReferencedImageSequence, 2);
      seq.add(new Attributes());
      seq.add(new Attributes());
      assertEquals(2, seq.size());

      // Remove all items
      module.removeAllSequenceItems(Tag.ReferencedImageSequence);
      assertEquals(0, seq.size());
    }

    @Test
    @DisplayName("Should handle removal from non-existent sequence")
    void shouldHandleRemovalFromNonExistentSequence() {
      assertDoesNotThrow(() -> module.removeAllSequenceItems(Tag.ReferencedImageSequence));
    }

    @Test
    @DisplayName("Should remove sequence item by index")
    void shouldRemoveSequenceItemByIndex() {
      Sequence seq = attributes.newSequence(Tag.ReferencedImageSequence, 3);
      Attributes item1 = new Attributes();
      item1.setString(Tag.SOPInstanceUID, VR.UI, "1.1.1");
      Attributes item2 = new Attributes();
      item2.setString(Tag.SOPInstanceUID, VR.UI, "2.2.2");
      Attributes item3 = new Attributes();
      item3.setString(Tag.SOPInstanceUID, VR.UI, "3.3.3");

      seq.add(item1);
      seq.add(item2);
      seq.add(item3);

      // Remove middle item
      module.removeSequenceItem(Tag.ReferencedImageSequence, 1);
      assertEquals(2, seq.size());
      assertEquals("1.1.1", seq.get(0).getString(Tag.SOPInstanceUID));
      assertEquals("3.3.3", seq.get(1).getString(Tag.SOPInstanceUID));
    }

    @Test
    @DisplayName("Should handle invalid index gracefully")
    void shouldHandleInvalidIndexGracefully() {
      Sequence seq = attributes.newSequence(Tag.ReferencedImageSequence, 1);
      seq.add(new Attributes());

      assertDoesNotThrow(() -> module.removeSequenceItem(Tag.ReferencedImageSequence, -1));
      assertDoesNotThrow(() -> module.removeSequenceItem(Tag.ReferencedImageSequence, 10));
      assertEquals(1, seq.size());
    }

    @Test
    @DisplayName("Should remove sequence item by attributes")
    void shouldRemoveSequenceItemByAttributes() {
      Sequence seq = attributes.newSequence(Tag.ReferencedImageSequence, 2);
      Attributes item1 = new Attributes();
      item1.setString(Tag.SOPInstanceUID, VR.UI, "1.1.1");
      Attributes item2 = new Attributes();
      item2.setString(Tag.SOPInstanceUID, VR.UI, "2.2.2");

      seq.add(item1);
      seq.add(item2);

      // Remove specific item
      module.removeSequenceItem(Tag.ReferencedImageSequence, item1);
      assertEquals(1, seq.size());
      assertEquals("2.2.2", seq.get(0).getString(Tag.SOPInstanceUID));
    }
  }

  @Nested
  @DisplayName("Update Sequence Tests")
  class UpdateSequenceTest {

    private TestModule module;
    private Attributes attributes;

    @BeforeEach
    void setUp() {
      attributes = new Attributes();
      module = new TestModule(attributes);
    }

    @Test
    @DisplayName("Should update sequence with single module")
    void shouldUpdateSequenceWithSingleModule() {
      TestModule childModule = new TestModule();
      childModule.getAttributes().setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5");

      module.testUpdateSequence(Tag.ReferencedImageSequence, childModule);

      Sequence seq = attributes.getSequence(Tag.ReferencedImageSequence);
      assertNotNull(seq);
      assertEquals(1, seq.size());
      assertEquals("1.2.3.4.5", seq.get(0).getString(Tag.SOPInstanceUID));
    }

    @Test
    @DisplayName("Should remove sequence when module is null")
    void shouldRemoveSequenceWhenModuleIsNull() {
      // First create a sequence
      Sequence seq = attributes.newSequence(Tag.ReferencedImageSequence, 1);
      seq.add(new Attributes());

      // Then update with null
      module.testUpdateSequence(Tag.ReferencedImageSequence, (Module) null);

      // Sequence should still exist but be empty
      seq = attributes.getSequence(Tag.ReferencedImageSequence);
      assertTrue(seq == null || seq.isEmpty());
    }

    @Test
    @DisplayName("Should update sequence with multiple modules")
    void shouldUpdateSequenceWithMultipleModules() {
      TestModule module1 = new TestModule();
      module1.getAttributes().setString(Tag.SOPInstanceUID, VR.UI, "1.1.1");
      TestModule module2 = new TestModule();
      module2.getAttributes().setString(Tag.SOPInstanceUID, VR.UI, "2.2.2");
      TestModule module3 = new TestModule();
      module3.getAttributes().setString(Tag.SOPInstanceUID, VR.UI, "3.3.3");

      Collection<TestModule> modules = List.of(module1, module2, module3);
      module.testUpdateSequence(Tag.ReferencedImageSequence, modules);

      Sequence seq = attributes.getSequence(Tag.ReferencedImageSequence);
      assertNotNull(seq);
      assertEquals(3, seq.size());
      assertEquals("1.1.1", seq.get(0).getString(Tag.SOPInstanceUID));
      assertEquals("2.2.2", seq.get(1).getString(Tag.SOPInstanceUID));
      assertEquals("3.3.3", seq.get(2).getString(Tag.SOPInstanceUID));
    }

    @Test
    @DisplayName("Should remove sequence when collection is null")
    void shouldRemoveSequenceWhenCollectionIsNull() {
      // First create a sequence
      Sequence seq = attributes.newSequence(Tag.ReferencedImageSequence, 1);
      seq.add(new Attributes());

      // Then update with null collection
      module.testUpdateSequence(Tag.ReferencedImageSequence, (Collection<TestModule>) null);

      // Sequence should be removed or empty
      seq = attributes.getSequence(Tag.ReferencedImageSequence);
      assertTrue(seq == null || seq.isEmpty());
    }

    @Test
    @DisplayName("Should remove sequence when collection is empty")
    void shouldRemoveSequenceWhenCollectionIsEmpty() {
      // First create a sequence
      Sequence seq = attributes.newSequence(Tag.ReferencedImageSequence, 1);
      seq.add(new Attributes());

      // Then update with empty collection
      module.testUpdateSequence(Tag.ReferencedImageSequence, List.of());

      // Sequence should be removed or empty
      seq = attributes.getSequence(Tag.ReferencedImageSequence);
      assertTrue(seq == null || seq.isEmpty());
    }

    @Test
    @DisplayName("Should handle circular parent references")
    void shouldHandleCircularParentReferences() {
      // Create attributes with parent relationship
      Attributes parentAttrs = new Attributes();
      Sequence parentSeq = parentAttrs.newSequence(Tag.ReferencedImageSequence, 1);
      Attributes childAttrs = new Attributes();
      childAttrs.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5");
      parentSeq.add(childAttrs);

      TestModule childModule = new TestModule(childAttrs);

      // This should not cause issues due to parent handling
      assertDoesNotThrow(() -> module.testUpdateSequence(Tag.ReferencedImageSequence, childModule));

      Sequence seq = attributes.getSequence(Tag.ReferencedImageSequence);
      assertNotNull(seq);
      assertEquals(1, seq.size());
      assertEquals("1.2.3.4.5", seq.get(0).getString(Tag.SOPInstanceUID));
    }
  }

  @Nested
  @DisplayName("Integration and Edge Cases")
  class IntegrationTest {

    @Test
    @DisplayName("Should handle complex sequence operations")
    void shouldHandleComplexSequenceOperations() {
      TestModule module = new TestModule();

      // Create initial sequence
      TestModule child1 = new TestModule();
      child1.getAttributes().setString(Tag.SOPInstanceUID, VR.UI, "1.1.1");
      module.testUpdateSequence(Tag.ReferencedImageSequence, child1);

      // Update with multiple modules
      TestModule child2 = new TestModule();
      child2.getAttributes().setString(Tag.SOPInstanceUID, VR.UI, "2.2.2");
      TestModule child3 = new TestModule();
      child3.getAttributes().setString(Tag.SOPInstanceUID, VR.UI, "3.3.3");

      module.testUpdateSequence(Tag.ReferencedImageSequence, List.of(child2, child3));

      Sequence seq = module.getAttributes().getSequence(Tag.ReferencedImageSequence);
      assertEquals(2, seq.size());
      assertEquals("2.2.2", seq.get(0).getString(Tag.SOPInstanceUID));
      assertEquals("3.3.3", seq.get(1).getString(Tag.SOPInstanceUID));

      // Remove by clearing
      module.removeAllSequenceItems(Tag.ReferencedImageSequence);
      assertEquals(0, seq.size());
    }

    @Test
    @DisplayName("Should maintain data integrity across operations")
    void shouldMaintainDataIntegrityAcrossOperations() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.PatientID, VR.LO, "12345");
      attrs.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5");

      TestModule module = new TestModule(attrs);

      // Perform sequence operations
      TestModule childModule = new TestModule();
      childModule.getAttributes().setString(Tag.SOPInstanceUID, VR.UI, "6.7.8.9.0");
      module.testUpdateSequence(Tag.ReferencedImageSequence, childModule);

      // Verify original data is preserved
      assertEquals("12345", module.getAttributes().getString(Tag.PatientID));
      assertEquals("1.2.3.4.5", module.getAttributes().getString(Tag.StudyInstanceUID));

      // Verify sequence was added correctly
      Sequence seq = module.getAttributes().getSequence(Tag.ReferencedImageSequence);
      assertNotNull(seq);
      assertEquals(1, seq.size());
      assertEquals("6.7.8.9.0", seq.get(0).getString(Tag.SOPInstanceUID));
    }
  }
}
