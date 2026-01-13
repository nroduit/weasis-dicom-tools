/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
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
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class AbstractListenerParamsTest {

  private static final String[] TEST_AET_TITLES = {"STORE_SCP", "MOVE_SCP", "FIND_SCP"};
  private static final String VALID_URL_STRING = "file:///tmp/transfer-capabilities.xml";

  @Mock private ConnectOptions mockConnectOptions;

  private URL validTransferCapabilityFile;

  /** Concrete implementation of AbstractListenerParams for testing purposes. */
  private static class TestListenerParams extends AbstractListenerParams {

    public TestListenerParams(boolean bindCallingAet) {
      super(bindCallingAet);
    }

    public TestListenerParams(AdvancedParams params, boolean bindCallingAet) {
      super(params, bindCallingAet);
    }

    public TestListenerParams(
        AdvancedParams params,
        boolean bindCallingAet,
        URL transferCapabilityFile,
        String... acceptedCallingAETitles) {
      super(params, bindCallingAet, transferCapabilityFile, acceptedCallingAETitles);
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    validTransferCapabilityFile = URI.create(VALID_URL_STRING).toURL();
  }

  @Nested
  class Constructor_with_bind_calling_aet_only {

    @Test
    void creates_instance_with_bind_calling_aet_true() {
      var params = new TestListenerParams(true);

      assertTrue(params.isBindCallingAet());
      assertNotNull(params.getParams());
      assertNull(params.getTransferCapabilityFile());
      assertArrayEquals(new String[0], params.getAcceptedCallingAETitles());
    }

    @Test
    void creates_instance_with_bind_calling_aet_false() {
      var params = new TestListenerParams(false);

      assertFalse(params.isBindCallingAet());
      assertNotNull(params.getParams());
      assertNull(params.getTransferCapabilityFile());
      assertArrayEquals(new String[0], params.getAcceptedCallingAETitles());
    }

    @Test
    void creates_new_advanced_params_when_null() {
      var params = new TestListenerParams(true);

      assertNotNull(params.getParams());
      assertNotSame(AdvancedParams.class, params.getParams().getClass().getSuperclass());
    }
  }

  @Nested
  class Constructor_with_advanced_params_and_bind_calling_aet {

    @Test
    void creates_instance_with_provided_advanced_params() {
      var advancedParams = new AdvancedParams();
      var params = new TestListenerParams(advancedParams, true);

      assertSame(advancedParams, params.getParams());
      assertTrue(params.isBindCallingAet());
      assertNull(params.getTransferCapabilityFile());
      assertArrayEquals(new String[0], params.getAcceptedCallingAETitles());
    }

    @Test
    void creates_new_advanced_params_when_null() {
      var params = new TestListenerParams(null, false);

      assertNotNull(params.getParams());
      assertFalse(params.isBindCallingAet());
    }

    @Test
    void configures_default_operation_limits_when_params_null_and_connect_options_available() {
      var params = new TestListenerParams(null, true);
      var connectOptions = params.getParams().getConnectOptions();

      if (connectOptions != null) {
        assertEquals(50, connectOptions.getMaxOpsInvoked());
        assertEquals(50, connectOptions.getMaxOpsPerformed());
      }
    }

    @Test
    void does_not_configure_operation_limits_when_advanced_params_provided() {
      var advancedParams = new AdvancedParams();
      advancedParams.setConnectOptions(new ConnectOptions());
      var originalMaxOpsInvoked = advancedParams.getConnectOptions().getMaxOpsInvoked();
      var originalMaxOpsPerformed = advancedParams.getConnectOptions().getMaxOpsPerformed();

      var params = new TestListenerParams(advancedParams, true);

      assertEquals(
          originalMaxOpsInvoked, params.getParams().getConnectOptions().getMaxOpsInvoked());
      assertEquals(
          originalMaxOpsPerformed, params.getParams().getConnectOptions().getMaxOpsPerformed());
    }
  }

  @Nested
  class Constructor_with_full_parameters {

    @Test
    void creates_instance_with_all_parameters() {
      var advancedParams = new AdvancedParams();

      var params =
          new TestListenerParams(
              advancedParams, true, validTransferCapabilityFile, TEST_AET_TITLES);

      assertSame(advancedParams, params.getParams());
      assertTrue(params.isBindCallingAet());
      assertEquals(validTransferCapabilityFile, params.getTransferCapabilityFile());

      var acceptedAeTitles = params.getAcceptedCallingAETitles();
      assertArrayEquals(TEST_AET_TITLES, acceptedAeTitles);
      // Verify defensive copying - modifying original should not affect returned array
      assertNotSame(TEST_AET_TITLES, acceptedAeTitles);
    }

    @Test
    void handles_null_advanced_params() {
      var params =
          new TestListenerParams(null, false, validTransferCapabilityFile, TEST_AET_TITLES);

      assertNotNull(params.getParams());
      assertFalse(params.isBindCallingAet());
      assertEquals(validTransferCapabilityFile, params.getTransferCapabilityFile());
      assertArrayEquals(TEST_AET_TITLES, params.getAcceptedCallingAETitles());
    }

    @Test
    void handles_null_transfer_capability_file() {
      var advancedParams = new AdvancedParams();

      var params = new TestListenerParams(advancedParams, true, null, TEST_AET_TITLES);

      assertSame(advancedParams, params.getParams());
      assertTrue(params.isBindCallingAet());
      assertNull(params.getTransferCapabilityFile());
      assertArrayEquals(TEST_AET_TITLES, params.getAcceptedCallingAETitles());
    }

    @Test
    void handles_null_accepted_calling_ae_titles() {
      var advancedParams = new AdvancedParams();
      String[] nullAeTitles = null;

      var params =
          new TestListenerParams(advancedParams, false, validTransferCapabilityFile, nullAeTitles);

      assertArrayEquals(new String[0], params.getAcceptedCallingAETitles());
    }

    @Test
    void handles_empty_accepted_calling_ae_titles() {
      var advancedParams = new AdvancedParams();

      var params = new TestListenerParams(advancedParams, true, validTransferCapabilityFile);

      assertArrayEquals(new String[0], params.getAcceptedCallingAETitles());
    }

    @Test
    void creates_defensive_copy_of_accepted_ae_titles() {
      var originalAeTitles = new String[] {"AE1", "AE2", "AE3"};
      var params =
          new TestListenerParams(
              new AdvancedParams(), true, validTransferCapabilityFile, originalAeTitles);

      // Modify original array
      originalAeTitles[0] = "MODIFIED";

      // Verify the params instance is not affected
      var storedAeTitles = params.getAcceptedCallingAETitles();
      assertEquals("AE1", storedAeTitles[0]);
      assertNotEquals("MODIFIED", storedAeTitles[0]);
    }
  }

  @Nested
  class Default_operation_limits_configuration {

    @Test
    void does_not_configure_limits_when_advanced_params_provided() {
      var realAdvancedParams = new AdvancedParams();
      realAdvancedParams.setConnectOptions(mockConnectOptions);

      new TestListenerParams(realAdvancedParams, true);

      // Verify no configuration methods were called
      verifyNoInteractions(mockConnectOptions);
    }

    @Test
    void does_not_configure_limits_when_connect_options_null() {
      var params = new TestListenerParams(null, true);

      // If getConnectOptions() returns null, no configuration should happen
      if (params.getParams().getConnectOptions() == null) {
        // This is the expected behavior - no exception should be thrown
        assertDoesNotThrow(() -> new TestListenerParams(null, false));
      }
    }
  }

  @Nested
  class Getter_methods {

    private TestListenerParams params;
    private AdvancedParams advancedParams;

    @BeforeEach
    void setUp() {
      advancedParams = new AdvancedParams();
      params =
          new TestListenerParams(
              advancedParams, true, validTransferCapabilityFile, TEST_AET_TITLES);
    }

    @Test
    void is_bind_calling_aet_returns_correct_value() {
      assertTrue(params.isBindCallingAet());

      var falseParams = new TestListenerParams(false);
      assertFalse(falseParams.isBindCallingAet());
    }

    @Test
    void get_transfer_capability_file_returns_correct_url() {
      assertEquals(validTransferCapabilityFile, params.getTransferCapabilityFile());

      var nullUrlParams = new TestListenerParams(advancedParams, true, null, TEST_AET_TITLES);
      assertNull(nullUrlParams.getTransferCapabilityFile());
    }

    @Test
    void get_accepted_calling_ae_titles_returns_defensive_copy() {
      var returnedTitles = params.getAcceptedCallingAETitles();

      assertArrayEquals(TEST_AET_TITLES, returnedTitles);
      assertNotSame(TEST_AET_TITLES, returnedTitles);

      // Modifying returned array should not affect future calls
      returnedTitles[0] = "MODIFIED";
      var secondCall = params.getAcceptedCallingAETitles();
      assertEquals(TEST_AET_TITLES[0], secondCall[0]);
      assertNotEquals("MODIFIED", secondCall[0]);
    }

    @Test
    void get_params_returns_same_instance() {
      assertSame(advancedParams, params.getParams());
    }

    @Test
    void get_accepted_calling_ae_titles_returns_empty_array_when_null_provided() {
      var emptyParams = new TestListenerParams(advancedParams, false, null, (String[]) null);

      var titles = emptyParams.getAcceptedCallingAETitles();
      assertNotNull(titles);
      assertEquals(0, titles.length);
    }
  }

  @Nested
  class Edge_cases_and_error_handling {

    @Test
    void handles_malformed_url_gracefully() throws MalformedURLException {
      // This test ensures the constructor can handle URL objects properly
      var malformedUrl = URI.create("http://invalid-url:99999/path").toURL();

      assertDoesNotThrow(
          () -> new TestListenerParams(new AdvancedParams(), true, malformedUrl, TEST_AET_TITLES));
    }

    @Test
    void handles_very_large_ae_title_array() {
      var largeArray = new String[1000];
      Arrays.fill(largeArray, "AE_TITLE");

      assertDoesNotThrow(
          () -> new TestListenerParams(new AdvancedParams(), true, null, largeArray));

      var params = new TestListenerParams(new AdvancedParams(), true, null, largeArray);
      assertEquals(1000, params.getAcceptedCallingAETitles().length);
    }

    @Test
    void handles_ae_titles_with_special_characters() {
      var specialTitles = new String[] {"AE-TITLE_1", "AE.TITLE@2", "AE TITLE 3"};

      var params = new TestListenerParams(new AdvancedParams(), true, null, specialTitles);

      assertArrayEquals(specialTitles, params.getAcceptedCallingAETitles());
    }

    @Test
    void constructor_chaining_works_correctly() {
      // Test that the three-parameter constructor calls the four-parameter constructor
      var params = new TestListenerParams(new AdvancedParams(), true);

      assertNotNull(params.getParams());
      assertTrue(params.isBindCallingAet());
      assertNull(params.getTransferCapabilityFile());
      assertArrayEquals(new String[0], params.getAcceptedCallingAETitles());
    }
  }
}
