/*
 * Copyright (c) 2025 Weasis Team and other contributors.
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

import java.io.IOException;
import java.util.EnumSet;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.UserIdentityRQ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class AdvancedParamsTest {

  private AdvancedParams advancedParams;

  @Mock private UserIdentityRQ mockIdentity;
  @Mock private ConnectOptions mockConnectOptions;
  @Mock private TlsOptions mockTlsOptions;
  @Mock private Connection mockConnection;
  @Mock private Connection mockRemoteConnection;
  @Mock private Device mockDevice;
  @Mock private AAssociateRQ mockAssociateRQ;
  @Mock private ApplicationEntity mockApplicationEntity;
  @Mock private KeyManager mockKeyManager;
  @Mock private TrustManager mockTrustManager;

  @BeforeEach
  void setUp() {
    advancedParams = new AdvancedParams();
  }

  @Nested
  class Constants {
    @Test
    void should_have_correct_transfer_syntax_constants() {
      assertArrayEquals(
          new String[] {
            UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndian
          },
          AdvancedParams.IVR_LE_FIRST);

      assertArrayEquals(
          new String[] {
            UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndian, UID.ImplicitVRLittleEndian
          },
          AdvancedParams.EVR_LE_FIRST);

      assertArrayEquals(
          new String[] {
            UID.ExplicitVRBigEndian, UID.ExplicitVRLittleEndian, UID.ImplicitVRLittleEndian
          },
          AdvancedParams.EVR_BE_FIRST);

      assertArrayEquals(new String[] {UID.ImplicitVRLittleEndian}, AdvancedParams.IVR_LE_ONLY);
    }
  }

  @Nested
  class DefaultValues {
    @Test
    void should_have_default_values() {
      assertNull(advancedParams.getInformationModel());
      assertTrue(advancedParams.getQueryOptions().isEmpty());
      assertArrayEquals(AdvancedParams.IVR_LE_FIRST, advancedParams.getTsuidOrder());
      assertNull(advancedParams.getProxy());
      assertEquals(org.dcm4che3.net.Priority.NORMAL, advancedParams.getPriority());
      assertNull(advancedParams.getIdentity());
      assertNull(advancedParams.getConnectOptions());
      assertNull(advancedParams.getTlsOptions());
    }
  }

  @Nested
  class InformationModel {
    @Test
    void should_set_and_get_information_model() {
      var testModel = "TestModel";
      advancedParams.setInformationModel(testModel);
      assertEquals(testModel, advancedParams.getInformationModel());
    }

    @Test
    void should_handle_null_information_model() {
      advancedParams.setInformationModel(null);
      assertNull(advancedParams.getInformationModel());
    }
  }

  @Nested
  class QueryOptions {
    @Test
    void should_set_query_options() {
      var options = EnumSet.of(QueryOption.RELATIONAL, QueryOption.DATETIME);
      advancedParams.setQueryOptions(options);

      assertEquals(2, advancedParams.getQueryOptions().size());
      assertTrue(advancedParams.getQueryOptions().contains(QueryOption.RELATIONAL));
      assertTrue(advancedParams.getQueryOptions().contains(QueryOption.DATETIME));
    }

    @Test
    void should_clear_existing_options_when_setting_new_ones() {
      advancedParams.setQueryOptions(EnumSet.of(QueryOption.RELATIONAL));
      advancedParams.setQueryOptions(EnumSet.of(QueryOption.DATETIME));

      assertEquals(1, advancedParams.getQueryOptions().size());
      assertTrue(advancedParams.getQueryOptions().contains(QueryOption.DATETIME));
      assertFalse(advancedParams.getQueryOptions().contains(QueryOption.RELATIONAL));
    }

    @Test
    void should_clear_all_options_when_setting_null() {
      advancedParams.setQueryOptions(EnumSet.of(QueryOption.RELATIONAL));
      advancedParams.setQueryOptions(null);

      assertTrue(advancedParams.getQueryOptions().isEmpty());
    }
  }

  @Nested
  class TsuidOrder {
    @Test
    void should_set_and_get_tsuid_order() {
      var customOrder = new String[] {UID.ExplicitVRBigEndian, UID.ImplicitVRLittleEndian};
      advancedParams.setTsuidOrder(customOrder);

      assertArrayEquals(customOrder, advancedParams.getTsuidOrder());
    }

    @Test
    void should_return_defensive_copy_of_tsuid_order() {
      var originalOrder = advancedParams.getTsuidOrder();
      originalOrder[0] = "modified";

      assertArrayEquals(AdvancedParams.IVR_LE_FIRST, advancedParams.getTsuidOrder());
    }

    @Test
    void should_throw_exception_when_setting_null_tsuid_order() {
      assertThrows(NullPointerException.class, () -> advancedParams.setTsuidOrder(null));
    }
  }

  @Nested
  class Proxy {
    @Test
    void should_set_and_get_proxy() {
      var proxyConfig = "user:pass@proxy.example.com:8080";
      advancedParams.setProxy(proxyConfig);
      assertEquals(proxyConfig, advancedParams.getProxy());
    }

    @Test
    void should_handle_null_proxy() {
      advancedParams.setProxy(null);
      assertNull(advancedParams.getProxy());
    }
  }

  @Nested
  class PriorityTest {
    @Test
    void should_set_and_get_priority() {
      advancedParams.setPriority(Priority.HIGH);
      assertEquals(Priority.HIGH, advancedParams.getPriority());
    }
  }

  @Nested
  class Identity {
    @Test
    void should_set_and_get_identity() {
      advancedParams.setIdentity(mockIdentity);
      assertEquals(mockIdentity, advancedParams.getIdentity());
    }
  }

  @Nested
  class ConnectConfiguration {
    @Test
    void should_set_and_get_connect_options() {
      advancedParams.setConnectOptions(mockConnectOptions);
      assertEquals(mockConnectOptions, advancedParams.getConnectOptions());
    }

    @Test
    void should_set_and_get_tls_options() {
      advancedParams.setTlsOptions(mockTlsOptions);
      assertEquals(mockTlsOptions, advancedParams.getTlsOptions());
    }
  }

  @Nested
  class ConfigureConnect {
    private DicomNode calledNode;

    @BeforeEach
    void setUp() {
      calledNode = new DicomNode("CALLED_AET", "localhost", 11112);
    }

    @Test
    void should_configure_association_request_and_remote_connection() {
      advancedParams.setIdentity(mockIdentity);

      advancedParams.configureConnect(mockAssociateRQ, mockRemoteConnection, calledNode);

      verify(mockAssociateRQ).setCalledAET("CALLED_AET");
      verify(mockAssociateRQ).setUserIdentityRQ(mockIdentity);
      verify(mockRemoteConnection).setHostname("localhost");
      verify(mockRemoteConnection).setPort(11112);
    }

    @Test
    void should_configure_without_identity() {
      advancedParams.configureConnect(mockAssociateRQ, mockRemoteConnection, calledNode);

      verify(mockAssociateRQ).setCalledAET("CALLED_AET");
      verify(mockAssociateRQ, never()).setUserIdentityRQ(any());
      verify(mockRemoteConnection).setHostname("localhost");
      verify(mockRemoteConnection).setPort(11112);
    }
  }

  @Nested
  class ConfigureBind {
    private DicomNode callingNode;

    @BeforeEach
    void setUp() {
      callingNode = new DicomNode("CALLING_AET", "127.0.0.1", 11113);
    }

    @Test
    void should_configure_connection_with_calling_node() {
      advancedParams.configureBind(mockConnection, callingNode);

      verify(mockConnection).setHostname("127.0.0.1");
      verify(mockConnection).setPort(11113);
    }

    @Test
    void should_configure_application_entity_and_connection() {
      advancedParams.configureBind(mockApplicationEntity, mockConnection, callingNode);

      verify(mockApplicationEntity).setAETitle("CALLING_AET");
      verify(mockConnection).setHostname("127.0.0.1");
      verify(mockConnection).setPort(11113);
    }

    @Test
    void should_handle_null_hostname_and_port() {
      var nodeWithNulls = new DicomNode("TEST_AET");

      advancedParams.configureBind(mockConnection, nodeWithNulls);

      verify(mockConnection, never()).setHostname(any());
      verify(mockConnection, never()).setPort(anyInt());
    }
  }

  @Nested
  class Configure {
    @BeforeEach
    void setUp() {
      setupMockConnectOptions();
    }

    @Test
    void should_configure_all_connection_options() throws IOException {
      advancedParams.setConnectOptions(mockConnectOptions);

      advancedParams.configure(mockConnection);

      verifyAllConnectionOptionsSet();
    }

    private void setupMockConnectOptions() {
      when(mockConnectOptions.getBacklog()).thenReturn(10);
      when(mockConnectOptions.getConnectTimeout()).thenReturn(3000);
      when(mockConnectOptions.getRequestTimeout()).thenReturn(5000);
      when(mockConnectOptions.getAcceptTimeout()).thenReturn(5000);
      when(mockConnectOptions.getReleaseTimeout()).thenReturn(5000);
      when(mockConnectOptions.getResponseTimeout()).thenReturn(5000);
      when(mockConnectOptions.getRetrieveTimeout()).thenReturn(5000);
      when(mockConnectOptions.getIdleTimeout()).thenReturn(5000);
      when(mockConnectOptions.getSocloseDelay()).thenReturn(50);
      when(mockConnectOptions.getSorcvBuffer()).thenReturn(8192);
      when(mockConnectOptions.getSosndBuffer()).thenReturn(8192);
      when(mockConnectOptions.getMaxPdulenRcv()).thenReturn(16384);
      when(mockConnectOptions.getMaxPdulenSnd()).thenReturn(16384);
      when(mockConnectOptions.getMaxOpsInvoked()).thenReturn(5);
      when(mockConnectOptions.getMaxOpsPerformed()).thenReturn(5);
      when(mockConnectOptions.isPackPDV()).thenReturn(true);
      when(mockConnectOptions.isTcpNoDelay()).thenReturn(true);
    }

    private void verifyAllConnectionOptionsSet() {
      verify(mockConnection).setBacklog(10);
      verify(mockConnection).setConnectTimeout(3000);
      verify(mockConnection).setRequestTimeout(5000);
      verify(mockConnection).setAcceptTimeout(5000);
      verify(mockConnection).setReleaseTimeout(5000);
      verify(mockConnection).setResponseTimeout(5000);
      verify(mockConnection).setRetrieveTimeout(5000);
      verify(mockConnection).setIdleTimeout(5000);
      verify(mockConnection).setSocketCloseDelay(50);
      verify(mockConnection).setReceiveBufferSize(8192);
      verify(mockConnection).setSendBufferSize(8192);
      verify(mockConnection).setReceivePDULength(16384);
      verify(mockConnection).setSendPDULength(16384);
      verify(mockConnection).setMaxOpsInvoked(5);
      verify(mockConnection).setMaxOpsPerformed(5);
      verify(mockConnection).setPackPDV(true);
      verify(mockConnection).setTcpNoDelay(true);
    }
  }

  @Nested
  class Integration {
    @Test
    void should_create_complete_configuration() {
      // Given
      var advancedParams = createCompleteAdvancedParams();
      var calledNode = new DicomNode("CALLED_AET", "remote.hospital.com", 104);
      var callingNode = new DicomNode("CALLING_AET", "localhost", 11112);

      // When & Then - Should not throw exceptions
      assertDoesNotThrow(
          () -> {
            advancedParams.configureConnect(mockAssociateRQ, mockRemoteConnection, calledNode);
            advancedParams.configureBind(mockApplicationEntity, mockConnection, callingNode);
            advancedParams.configure(mockConnection);
          });
    }

    private AdvancedParams createCompleteAdvancedParams() {
      var params = new AdvancedParams();

      params.setInformationModel("TestModel");
      params.setQueryOptions(EnumSet.of(QueryOption.RELATIONAL));
      params.setTsuidOrder(AdvancedParams.EVR_LE_FIRST);
      params.setProxy("proxy.example.com:8080");
      params.setPriority(Priority.HIGH);
      params.setIdentity(mockIdentity);

      var connectOptions = createConnectOptions();
      params.setConnectOptions(connectOptions);

      return params;
    }

    private ConnectOptions createConnectOptions() {
      var connectOptions = new ConnectOptions();
      connectOptions.setConnectTimeout(3000);
      connectOptions.setAcceptTimeout(5000);
      connectOptions.setMaxOpsInvoked(5);
      connectOptions.setMaxOpsPerformed(5);
      return connectOptions;
    }
  }
}
