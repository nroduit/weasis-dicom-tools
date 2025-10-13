/*
 * Copyright (c) 2014-2020 Weasis Team and other contributors.
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

import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.stream.Stream;
import org.dcm4che3.net.Association;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class DicomNodeTest {

  private static final String VALID_AET = "TESTNODE";
  private static final String VALID_HOSTNAME = "localhost";
  private static final Integer VALID_PORT = 11112;
  private static final Long VALID_ID = 123L;

  @Mock private Association mockAssociation;
  @Mock private Socket mockSocket;
  @Mock private InetAddress mockInetAddress;

  @Nested
  class Constructor_Tests {

    @Test
    void creates_node_with_aet_only() {
      var node = new DicomNode(VALID_AET);

      assertEquals(VALID_AET, node.getAet());
      assertNull(node.getHostname());
      assertNull(node.getPort());
      assertNull(node.getId());
      assertFalse(node.isValidateHostname());
    }

    @Test
    void creates_node_with_aet_and_port() {
      var node = new DicomNode(VALID_AET, VALID_PORT);

      assertEquals(VALID_AET, node.getAet());
      assertNull(node.getHostname());
      assertEquals(VALID_PORT, node.getPort());
      assertNull(node.getId());
      assertFalse(node.isValidateHostname());
    }

    @Test
    void creates_node_with_aet_hostname_and_port() {
      var node = new DicomNode(VALID_AET, VALID_HOSTNAME, VALID_PORT);

      assertEquals(VALID_AET, node.getAet());
      assertEquals(VALID_HOSTNAME, node.getHostname());
      assertEquals(VALID_PORT, node.getPort());
      assertNull(node.getId());
      assertFalse(node.isValidateHostname());
    }

    @Test
    void creates_node_with_id_aet_hostname_and_port() {
      var node = new DicomNode(VALID_ID, VALID_AET, VALID_HOSTNAME, VALID_PORT);

      assertEquals(VALID_ID, node.getId());
      assertEquals(VALID_AET, node.getAet());
      assertEquals(VALID_HOSTNAME, node.getHostname());
      assertEquals(VALID_PORT, node.getPort());
      assertFalse(node.isValidateHostname());
    }

    @Test
    void creates_node_with_full_configuration() {
      var node = new DicomNode(VALID_ID, VALID_AET, VALID_HOSTNAME, VALID_PORT, true);

      assertEquals(VALID_ID, node.getId());
      assertEquals(VALID_AET, node.getAet());
      assertEquals(VALID_HOSTNAME, node.getHostname());
      assertEquals(VALID_PORT, node.getPort());
      assertTrue(node.isValidateHostname());
    }

    @Test
    void trims_whitespace_from_aet() {
      var node = new DicomNode("  " + VALID_AET + "  ");

      assertEquals(VALID_AET, node.getAet());
    }

    @Test
    void allows_null_optional_parameters() {
      var node = new DicomNode(null, VALID_AET, null, null, false);

      assertNull(node.getId());
      assertEquals(VALID_AET, node.getAet());
      assertNull(node.getHostname());
      assertNull(node.getPort());
    }
  }

  @Nested
  class Validation_Tests {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void throws_exception_for_invalid_aet(String invalidAet) {
      var exception = assertThrows(IllegalArgumentException.class, () -> new DicomNode(invalidAet));

      assertEquals("Missing AETitle", exception.getMessage());
    }

    @Test
    void throws_exception_for_aet_too_long() {
      var longAet = "A".repeat(17);

      var exception = assertThrows(IllegalArgumentException.class, () -> new DicomNode(longAet));

      assertEquals("AETitle has more than 16 characters", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100, 65536, 70000})
    void throws_exception_for_invalid_port(int invalidPort) {
      var exception =
          assertThrows(IllegalArgumentException.class, () -> new DicomNode(VALID_AET, invalidPort));

      assertEquals("Port is out of bound", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 80, 443, 11112, 65535})
    void accepts_valid_ports(int validPort) {
      assertDoesNotThrow(() -> new DicomNode(VALID_AET, validPort));
    }

    @Test
    void accepts_exactly_16_character_aet() {
      var maxAet = "A".repeat(16);

      assertDoesNotThrow(() -> new DicomNode(maxAet));
      assertEquals(maxAet, new DicomNode(maxAet).getAet());
    }
  }

  @Nested
  class Hostname_Comparison_Tests {

    @Test
    void returns_true_for_identical_hostnames() {
      var node = new DicomNode(VALID_AET, "example.com", VALID_PORT);

      assertTrue(node.equalsHostname("example.com"));
    }

    @Test
    void returns_true_for_null_hostnames() {
      var node = new DicomNode(VALID_AET, null, VALID_PORT);

      assertTrue(node.equalsHostname(null));
    }

    @Test
    void resolves_hostnames_to_ip_for_comparison() {
      var node = new DicomNode(VALID_AET, "localhost", VALID_PORT);

      // Both should resolve to 127.0.0.1
      assertTrue(node.equalsHostname("127.0.0.1"));
    }

    @Test
    void handles_unresolvable_hostnames() {
      var node = new DicomNode(VALID_AET, "invalid.example", VALID_PORT);

      // Should fall back to string comparison when resolution fails
      assertTrue(node.equalsHostname("invalid.example"));
      assertFalse(node.equalsHostname("different.invalid"));
    }
  }

  @Nested
  class Convert_To_IP_Tests {

    @Test
    void returns_default_ip_for_null_hostname() {
      assertEquals("127.0.0.1", DicomNode.convertToIP(null));
    }

    @Test
    void returns_default_ip_for_empty_hostname() {
      assertEquals("127.0.0.1", DicomNode.convertToIP(""));
    }

    @Test
    void returns_default_ip_for_whitespace_hostname() {
      assertEquals("127.0.0.1", DicomNode.convertToIP("   "));
    }

    @Test
    void resolves_localhost_to_loopback_ip() {
      var result = DicomNode.convertToIP("localhost");

      assertTrue(result.equals("127.0.0.1") || result.equals("::1"));
    }

    @Test
    void returns_ip_address_unchanged() {
      var ip = "192.168.1.100";

      assertEquals(ip, DicomNode.convertToIP(ip));
    }

    @Test
    void returns_original_hostname_for_unresolvable() {
      var unresolvableHostname = "definitely.invalid.hostname.example";

      assertEquals(unresolvableHostname, DicomNode.convertToIP(unresolvableHostname));
    }
  }

  @Nested
  class Equals_And_HashCode_Tests {

    private static Stream<Arguments> equalNodesProvider() {
      return Stream.of(
          Arguments.of("same simple nodes", new DicomNode("TEST"), new DicomNode("TEST")),
          Arguments.of(
              "same full nodes",
              new DicomNode("TEST", "localhost", 11112),
              new DicomNode("TEST", "localhost", 11112)),
          Arguments.of(
              "same with id ignored",
              new DicomNode(1L, "TEST", "localhost", 11112),
              new DicomNode(2L, "TEST", "localhost", 11112)),
          Arguments.of(
              "same with null values",
              new DicomNode("TEST", null, null),
              new DicomNode("TEST", null, null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("equalNodesProvider")
    void equal_nodes_are_equal(String description, DicomNode node1, DicomNode node2) {
      assertEquals(node1, node2);
      assertEquals(node2, node1);
      assertEquals(node1.hashCode(), node2.hashCode());
    }

    private static Stream<Arguments> unequalNodesProvider() {
      return Stream.of(
          Arguments.of("different aet", new DicomNode("TEST1"), new DicomNode("TEST2")),
          Arguments.of(
              "different hostname",
              new DicomNode("TEST", "host1", 11112),
              new DicomNode("TEST", "host2", 11112)),
          Arguments.of(
              "different port",
              new DicomNode("TEST", "localhost", 11112),
              new DicomNode("TEST", "localhost", 11113)),
          Arguments.of(
              "one null hostname",
              new DicomNode("TEST", null, 11112),
              new DicomNode("TEST", "localhost", 11112)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unequalNodesProvider")
    void unequal_nodes_are_not_equal(String description, DicomNode node1, DicomNode node2) {
      assertNotEquals(node1, node2);
      assertNotEquals(node2, node1);
    }

    @Test
    void equals_handles_null_and_different_types() {
      var node = new DicomNode(VALID_AET);

      assertNotEquals(null, node);
      assertNotEquals("not a DicomNode", node);
    }

    @Test
    void same_instance_is_equal_to_itself() {
      var node = new DicomNode(VALID_AET, VALID_HOSTNAME, VALID_PORT);

      assertEquals(node, node);
    }

    @Test
    void equal_nodes_in_collections() {
      var node1 = new DicomNode("TEST", "localhost", 11112);
      var node2 = new DicomNode("TEST", "localhost", 11112);
      var nodes = List.of(node1);

      assertTrue(nodes.contains(node2));
    }
  }

  @Nested
  class ToString_Tests {

    @Test
    void formats_complete_node_information() {
      var node = new DicomNode(VALID_AET, VALID_HOSTNAME, VALID_PORT);
      var result = node.toString();

      assertTrue(result.contains("DicomNode"));
      assertTrue(result.contains(VALID_AET));
      assertTrue(result.contains(VALID_HOSTNAME));
      assertTrue(result.contains(VALID_PORT.toString()));
    }

    @Test
    void handles_null_values_in_formatting() {
      var node = new DicomNode(VALID_AET);
      var result = node.toString();

      assertTrue(result.contains("DicomNode"));
      assertTrue(result.contains(VALID_AET));
      assertTrue(result.contains("null"));
    }

    @Test
    void produces_consistent_string_representation() {
      var node1 = new DicomNode(VALID_AET, VALID_HOSTNAME, VALID_PORT);
      var node2 = new DicomNode(VALID_AET, VALID_HOSTNAME, VALID_PORT);

      assertEquals(node1.toString(), node2.toString());
    }
  }

  @Nested
  class Association_Builder_Tests {

    @Test
    void builds_local_dicom_node_from_association() throws Exception {
      var localAet = "LOCAL_AET";
      var localIp = "192.168.1.10";
      var localPort = 11112;

      when(mockAssociation.getSocket()).thenReturn(mockSocket);
      when(mockAssociation.getLocalAET()).thenReturn(localAet);
      when(mockSocket.getLocalAddress()).thenReturn(mockInetAddress);
      when(mockSocket.getLocalPort()).thenReturn(localPort);
      when(mockInetAddress.getHostAddress()).thenReturn(localIp);

      var node = DicomNode.buildLocalDicomNode(mockAssociation);

      assertEquals(localAet, node.getAet());
      assertEquals(localIp, node.getHostname());
      assertEquals(localPort, node.getPort());
      assertNull(node.getId());
      assertFalse(node.isValidateHostname());
    }

    @Test
    void builds_remote_dicom_node_from_association() throws Exception {
      var remoteAet = "REMOTE_AET";
      var remoteIp = "10.0.0.100";
      var remotePort = 104;

      when(mockAssociation.getSocket()).thenReturn(mockSocket);
      when(mockAssociation.getRemoteAET()).thenReturn(remoteAet);
      when(mockSocket.getInetAddress()).thenReturn(mockInetAddress);
      when(mockSocket.getPort()).thenReturn(remotePort);
      when(mockInetAddress.getHostAddress()).thenReturn(remoteIp);

      var node = DicomNode.buildRemoteDicomNode(mockAssociation);

      assertEquals(remoteAet, node.getAet());
      assertEquals(remoteIp, node.getHostname());
      assertEquals(remotePort, node.getPort());
      assertNull(node.getId());
      assertFalse(node.isValidateHostname());
    }

    @Test
    void handles_null_inet_address_in_local_node() {
      var localAet = "LOCAL_AET";
      var localPort = 11112;

      when(mockAssociation.getSocket()).thenReturn(mockSocket);
      when(mockAssociation.getLocalAET()).thenReturn(localAet);
      when(mockSocket.getLocalAddress()).thenReturn(null);
      when(mockSocket.getLocalPort()).thenReturn(localPort);

      var node = DicomNode.buildLocalDicomNode(mockAssociation);

      assertEquals(localAet, node.getAet());
      assertNull(node.getHostname());
      assertEquals(localPort, node.getPort());
    }

    @Test
    void handles_null_inet_address_in_remote_node() {
      var remoteAet = "REMOTE_AET";
      var remotePort = 104;

      when(mockAssociation.getSocket()).thenReturn(mockSocket);
      when(mockAssociation.getRemoteAET()).thenReturn(remoteAet);
      when(mockSocket.getInetAddress()).thenReturn(null);
      when(mockSocket.getPort()).thenReturn(remotePort);

      var node = DicomNode.buildRemoteDicomNode(mockAssociation);

      assertEquals(remoteAet, node.getAet());
      assertNull(node.getHostname());
      assertEquals(remotePort, node.getPort());
    }
  }

  @Nested
  class Immutability_Tests {

    @Test
    void node_fields_are_immutable_after_construction() {
      var node = new DicomNode(VALID_ID, VALID_AET, VALID_HOSTNAME, VALID_PORT, true);

      // Verify getters return the same values consistently
      assertEquals(VALID_ID, node.getId());
      assertEquals(VALID_AET, node.getAet());
      assertEquals(VALID_HOSTNAME, node.getHostname());
      assertEquals(VALID_PORT, node.getPort());
      assertTrue(node.isValidateHostname());

      // Call getters multiple times to ensure consistency
      assertEquals(VALID_ID, node.getId());
      assertEquals(VALID_AET, node.getAet());
    }

    @Test
    void modifications_to_constructor_params_dont_affect_node() {
      var mutableAet = new StringBuilder(VALID_AET);
      var node = new DicomNode(mutableAet.toString());

      mutableAet.append("_MODIFIED");

      assertEquals(VALID_AET, node.getAet());
    }
  }

  @Nested
  class Integration_Scenarios {

    @Test
    void typical_pacs_node_scenario() {
      var pacsNode = new DicomNode("PACS_SERVER", "10.0.0.100", 104);

      assertEquals("PACS_SERVER", pacsNode.getAet());
      assertEquals("10.0.0.100", pacsNode.getHostname());
      assertEquals(104, pacsNode.getPort());
      assertFalse(pacsNode.isValidateHostname());

      assertTrue(pacsNode.equalsHostname("10.0.0.100"));
      assertFalse(pacsNode.equalsHostname("10.0.0.101"));
    }

    @Test
    void workstation_node_with_validation() {
      var workstationNode = new DicomNode(1L, "WORKSTATION", "ws01.hospital.org", 11112, true);

      assertEquals(1L, workstationNode.getId());
      assertEquals("WORKSTATION", workstationNode.getAet());
      assertEquals("ws01.hospital.org", workstationNode.getHostname());
      assertEquals(11112, workstationNode.getPort());
      assertTrue(workstationNode.isValidateHostname());
    }

    @Test
    void comparing_nodes_from_different_sources() {
      var configNode = new DicomNode("SCANNER", "scanner.radiology.local", 104);
      var discoveredNode = new DicomNode("SCANNER", "scanner.radiology.local", 104);

      assertEquals(configNode, discoveredNode);
      assertEquals(configNode.hashCode(), discoveredNode.hashCode());
      assertTrue(configNode.equalsHostname(discoveredNode.getHostname()));
    }

    @Test
    void nodes_in_network_topology() {
      var centralPacs = new DicomNode("CENTRAL_PACS", "pacs.central.hospital", 104);
      var departmentPacs = new DicomNode("DEPT_PACS", "pacs.radiology.hospital", 104);
      var workstation = new DicomNode("WS001", "10.20.30.40", 11112);

      var nodes = List.of(centralPacs, departmentPacs, workstation);

      assertEquals(3, nodes.size());
      assertTrue(nodes.contains(new DicomNode("CENTRAL_PACS", "pacs.central.hospital", 104)));
      assertFalse(nodes.contains(new DicomNode("UNKNOWN", "unknown.host", 104)));
    }
  }

  @Nested
  class Edge_Cases {

    @Test
    void handles_maximum_valid_aet_length() {
      var maxLengthAet = "1234567890123456"; // exactly 16 chars
      var node = new DicomNode(maxLengthAet);

      assertEquals(maxLengthAet, node.getAet());
    }

    @Test
    void handles_port_boundary_values() {
      assertDoesNotThrow(() -> new DicomNode(VALID_AET, 1));
      assertDoesNotThrow(() -> new DicomNode(VALID_AET, 65535));
    }

    @Test
    void handles_various_hostname_formats() {
      var ipv4Node = new DicomNode(VALID_AET, "192.168.1.1", VALID_PORT);
      var fqdnNode = new DicomNode(VALID_AET, "server.example.com", VALID_PORT);
      var shortNameNode = new DicomNode(VALID_AET, "server", VALID_PORT);

      assertEquals("192.168.1.1", ipv4Node.getHostname());
      assertEquals("server.example.com", fqdnNode.getHostname());
      assertEquals("server", shortNameNode.getHostname());
    }

    @Test
    void handles_whitespace_variations_in_aet() {
      var nodeWithSpaces = new DicomNode("  TEST_AET  ");
      var nodeWithTabs = new DicomNode("\tTEST_AET\t");
      var nodeWithMixed = new DicomNode(" \t TEST_AET \t ");

      assertEquals("TEST_AET", nodeWithSpaces.getAet());
      assertEquals("TEST_AET", nodeWithTabs.getAet());
      assertEquals("TEST_AET", nodeWithMixed.getAet());
    }
  }
}
