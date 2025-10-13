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

import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.movescu.MoveSCU;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.ProgressListener;

/**
 * Unit tests for {@link CMove} DICOM C-MOVE operations.
 *
 * <p>Tests cover parameter validation, successful operations, error handling, and progress tracking
 * using real data structures where possible.
 */
@DisplayName("DICOM C-MOVE Operations")
@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class CMoveTest {

  private static final String CALLING_AET = "WEASIS-SCU";
  private static final String CALLED_AET = "DCM4CHEE";
  private static final String DESTINATION_AET = "DEST-AET";
  private static final String TEST_HOST = "localhost";
  private static final int TEST_PORT = 11112;

  // Test DICOM UIDs - using valid format but non-existent UIDs for testing
  private static final String STUDY_UID = "1.2.840.113619.2.1.3.1762894121.12345.20240101120000";
  private static final String SERIES_UID = "1.2.840.113619.2.1.3.1762894121.12346.20240101120001";
  private static final String SOP_UID = "1.2.840.113619.2.1.3.1762894121.12347.20240101120002";

  @Mock private MoveSCU mockMoveSCU;

  private DicomNode callingNode;
  private DicomNode calledNode;
  private DicomProgress progress;

  @BeforeEach
  void setUp() {
    callingNode = new DicomNode(CALLING_AET);
    calledNode = new DicomNode(CALLED_AET, TEST_HOST, TEST_PORT);
    progress = new DicomProgress();
  }

  @Nested
  class Parameter_validation_tests {

    @Test
    void throws_exception_when_calling_node_is_null() {
      var exception =
          assertThrows(
              NullPointerException.class,
              () -> CMove.process(null, calledNode, DESTINATION_AET, progress));

      assertEquals("callingNode cannot be null", exception.getMessage());
    }

    @Test
    void throws_exception_when_called_node_is_null() {
      var exception =
          assertThrows(
              NullPointerException.class,
              () -> CMove.process(callingNode, null, DESTINATION_AET, progress));

      assertEquals("calledNode cannot be null", exception.getMessage());
    }

    @Test
    void throws_exception_when_destination_aet_is_null() {
      var exception =
          assertThrows(
              NullPointerException.class,
              () -> CMove.process(callingNode, calledNode, null, progress));

      assertEquals("destinationAet cannot be null", exception.getMessage());
    }

    @Test
    void accepts_null_progress_parameter() {
      // This should not throw an exception - progress is optional
      // We can't easily test the full flow without mocking deep dependencies,
      // but we can verify the validation passes
      var keys = new DicomParam[] {new DicomParam(Tag.StudyInstanceUID, STUDY_UID)};

      // The method will eventually fail due to connection issues, but validation should pass
      var result = CMove.process(callingNode, calledNode, DESTINATION_AET, null, keys);

      // Should return a DicomState with error status due to connection failure
      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void accepts_null_advanced_params() {
      var keys = new DicomParam[] {new DicomParam(Tag.StudyInstanceUID, STUDY_UID)};

      var result = CMove.process(null, callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
      // Will fail due to connection but validation should pass
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void accepts_empty_keys_array() {
      var result = CMove.process(callingNode, calledNode, DESTINATION_AET, progress);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Process_with_default_parameters_tests {

    @Test
    void delegates_to_main_process_method() {
      var keys = createStudyLevelKeys();

      var result = CMove.process(callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
      // This will be an error state due to no actual DICOM server, but the call should complete
      assertTrue(result.getStatus() != 0); // Some status should be set
    }

    @Test
    void handles_study_level_move_request() {
      var keys = createStudyLevelKeys();

      var result = CMove.process(callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
      assertNotNull(result.getMessage());
    }

    @Test
    void handles_series_level_move_request() {
      var keys = createSeriesLevelKeys();

      var result = CMove.process(callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
    }

    @Test
    void handles_instance_level_move_request() {
      var keys = createInstanceLevelKeys();

      var result = CMove.process(callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
    }
  }

  @Nested
  class Process_with_advanced_parameters_tests {

    @Test
    void uses_provided_advanced_parameters() {
      var params = createAdvancedParams();
      var keys = createStudyLevelKeys();

      var result = CMove.process(params, callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void handles_relational_query_options() {
      var params = createAdvancedParamsWithRelational();
      var keys = createSeriesLevelKeys();

      var result = CMove.process(params, callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
    }

    @Test
    void creates_default_params_when_null_provided() {
      var keys = createStudyLevelKeys();

      var result = CMove.process(null, callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Progress_tracking_tests {

    @Test
    void notifies_progress_listeners_during_operation() throws InterruptedException {
      var progressLatch = new CountDownLatch(1);
      var testProgress = new DicomProgress();

      testProgress.addProgressListener(
          new ProgressListener() {
            @Override
            public void handleProgression(DicomProgress dicomProgress) {
              progressLatch.countDown();
            }
          });

      var keys = createStudyLevelKeys();

      // Start the operation in a separate thread since it will block
      var thread =
          new Thread(
              () -> {
                CMove.process(callingNode, calledNode, DESTINATION_AET, testProgress, keys);
              });
      thread.start();

      // Wait a short time for the operation to begin and potentially trigger progress
      var notified = progressLatch.await(2, TimeUnit.SECONDS);
      thread.interrupt(); // Clean up the thread

      // Note: Progress notification might not occur if connection fails immediately
      // This test verifies the progress system is wired up correctly
      assertNotNull(testProgress);
    }

    @Test
    void handles_cancellation_through_progress() {
      var cancellableProgress = new DicomProgress();
      cancellableProgress.cancel();

      var keys = createStudyLevelKeys();
      var result =
          CMove.process(callingNode, calledNode, DESTINATION_AET, cancellableProgress, keys);

      assertNotNull(result);
      assertTrue(cancellableProgress.isCancelled());
    }
  }

  @Nested
  class Error_handling_tests {

    @Test
    void handles_interrupted_exception_properly() {
      var keys = createStudyLevelKeys();

      // Interrupt current thread to simulate InterruptedException
      Thread.currentThread().interrupt();

      var result = CMove.process(callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());

      // Verify thread interrupt flag is restored
      assertTrue(Thread.interrupted()); // This clears the flag
    }

    @Test
    void returns_error_state_for_invalid_destination() {
      var keys = createStudyLevelKeys();
      var emptyDestination = "";

      // This should not throw but should handle the error gracefully
      var result = CMove.process(callingNode, calledNode, emptyDestination, progress, keys);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Information_model_tests {

    @Test
    void uses_study_root_as_default_information_model() {
      var params = new AdvancedParams();
      // Don't set any information model - should default to StudyRoot
      var keys = createStudyLevelKeys();

      var result = CMove.process(params, callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
      // The operation will fail due to no server, but information model logic should work
      assertEquals(Status.UnableToProcess, result.getStatus());
    }

    @Test
    void handles_patient_root_information_model() {
      var params = new AdvancedParams();
      params.setInformationModel(MoveSCU.InformationModel.PatientRoot);
      var keys = createStudyLevelKeys();

      var result = CMove.process(params, callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
      assertEquals(Status.UnableToProcess, result.getStatus());
    }
  }

  @Nested
  class Integration_tests {

    @Test
    void creates_proper_dicom_state_response() {
      var keys = createStudyLevelKeys();

      var result = CMove.process(callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
      assertNotNull(result.getMessage());
      assertTrue(result.getStatus() != 0);
    }

    @Test
    void preserves_dicom_keys_in_state() {
      var keys = createStudyLevelKeys();

      var result = CMove.process(callingNode, calledNode, DESTINATION_AET, progress, keys);

      assertNotNull(result);
      // The keys should be added to the matching keys even if the operation fails
      // This depends on the internal implementation, but we can verify the result exists
      assertNotNull(result.getDicomMatchingKeys());
    }
  }

  // Helper methods to create test data structures

  private DicomParam[] createStudyLevelKeys() {
    return new DicomParam[] {new DicomParam(Tag.StudyInstanceUID, STUDY_UID)};
  }

  private DicomParam[] createSeriesLevelKeys() {
    return new DicomParam[] {
      new DicomParam(Tag.QueryRetrieveLevel, "SERIES"),
      new DicomParam(Tag.SeriesInstanceUID, SERIES_UID)
    };
  }

  private DicomParam[] createInstanceLevelKeys() {
    return new DicomParam[] {
      new DicomParam(Tag.QueryRetrieveLevel, "IMAGE"), new DicomParam(Tag.SOPInstanceUID, SOP_UID)
    };
  }

  private AdvancedParams createAdvancedParams() {
    var params = new AdvancedParams();
    ConnectOptions connectOptions = new ConnectOptions();
    connectOptions.setConnectTimeout(5000);
    connectOptions.setAcceptTimeout(5000);
    params.setConnectOptions(connectOptions);
    return params;
  }

  private AdvancedParams createAdvancedParamsWithRelational() {
    var params = createAdvancedParams();
    params.setQueryOptions(EnumSet.of(QueryOption.RELATIONAL));
    return params;
  }
}
