/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.op;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.net.Status;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;

@DisplayNameGeneration(ReplaceUnderscores.class)
class EchoTest {

  private static final String CALLING_AET = "TEST-SCU";
  private static final String CALLED_AET = "TEST-SCP";

  @Nested
  class Parameter_validation_tests {

    @Test
    void throws_exception_when_calling_aet_string_is_null() {
      var calledNode = createValidCalledNode();

      var exception =
          assertThrows(NullPointerException.class, () -> Echo.process((String) null, calledNode));

      assertEquals("callingAET cannot be null", exception.getMessage());
    }

    @Test
    void throws_exception_when_called_node_is_null_with_string_aet() {
      var exception =
          assertThrows(NullPointerException.class, () -> Echo.process(CALLING_AET, null));

      assertEquals("calledNode cannot be null", exception.getMessage());
    }

    @Test
    void throws_exception_when_calling_node_is_null() {
      var calledNode = createValidCalledNode();

      var exception =
          assertThrows(
              NullPointerException.class, () -> Echo.process((DicomNode) null, calledNode));

      assertEquals("callingNode cannot be null", exception.getMessage());
    }

    @Test
    void throws_exception_when_called_node_is_null_with_dicom_node() {
      var callingNode = createValidCallingNode();

      var exception =
          assertThrows(NullPointerException.class, () -> Echo.process(callingNode, null));

      assertEquals("calledNode cannot be null", exception.getMessage());
    }

    @Test
    void throws_exception_when_calling_node_is_null_with_advanced_params() {
      var calledNode = createValidCalledNode();
      var params = new AdvancedParams();

      var exception =
          assertThrows(NullPointerException.class, () -> Echo.process(params, null, calledNode));

      assertEquals("callingNode cannot be null", exception.getMessage());
    }

    @Test
    void throws_exception_when_called_node_is_null_with_advanced_params() {
      var callingNode = createValidCallingNode();
      var params = new AdvancedParams();

      var exception =
          assertThrows(NullPointerException.class, () -> Echo.process(params, callingNode, null));

      assertEquals("calledNode cannot be null", exception.getMessage());
    }
  }

  @Nested
  class Connection_failure_handling_tests {

    @Test
    void returns_failure_state_for_invalid_hostname() {
      var callingNode = createValidCallingNode();
      var calledNode = new DicomNode(CALLED_AET, "invalid.hostname.test", 11112);

      var result = Echo.process(null, callingNode, calledNode);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
      assertTrue(result.getMessage().contains("invalid.hostname.test"));
    }

    @Test
    void returns_failure_state_for_unreachable_port() {
      assertThrows(
          IllegalArgumentException.class, () -> new DicomNode(CALLED_AET, "localhost", -1));
      assertThrows(
          IllegalArgumentException.class, () -> new DicomNode(CALLED_AET, "127.0.0.1", 99999));
    }
  }

  @Nested
  class Method_delegation_tests {

    @Test
    void string_aet_method_creates_dicom_node_internally() {
      var calledNode = new DicomNode(CALLED_AET, "invalid.test", 104);

      var result = Echo.process(CALLING_AET, calledNode);

      assertNotNull(result);
      // Should fail due to invalid hostname, but the method should complete
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void two_node_method_uses_null_advanced_params() {
      var callingNode = createValidCallingNode();
      var calledNode = new DicomNode(CALLED_AET, "invalid.test", 104);

      var result = Echo.process(callingNode, calledNode);

      assertNotNull(result);
      // Should fail due to invalid hostname, but the method should complete
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void handles_null_advanced_params_gracefully() {
      var callingNode = createValidCallingNode();
      var calledNode = new DicomNode(CALLED_AET, "invalid.test", 104);

      var result = Echo.process(null, callingNode, calledNode);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  // Helper methods
  private DicomNode createValidCallingNode() {
    return new DicomNode(CALLING_AET);
  }

  private DicomNode createValidCalledNode() {
    return new DicomNode(CALLED_AET, "localhost", 11112);
  }
}
