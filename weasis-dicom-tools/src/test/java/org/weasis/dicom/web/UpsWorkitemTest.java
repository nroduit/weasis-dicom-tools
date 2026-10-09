/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.web;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class UpsWorkitemTest {

  private static final Code SEGMENTATION =
      new Code("128121", "DCM", null, "Automated Image Segmentation");
  private static final String STUDY = "1.2.3";
  private static final String SERIES = "1.2.3.4";
  private static final String BASE = "https://pacs/dicomweb/studies/" + STUDY + "/series/" + SERIES;

  private static Attributes study() {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 100");
    attrs.setString(Tag.PatientName, VR.PN, "Doe^John");
    attrs.setString(Tag.PatientID, VR.LO, "P1");
    attrs.setString(Tag.IssuerOfPatientID, VR.LO, "HOSP");
    attrs.setString(Tag.PatientBirthDate, VR.DA, "19700101");
    attrs.setString(Tag.StudyInstanceUID, VR.UI, STUDY);
    attrs.setString(Tag.AccessionNumber, VR.SH, "ACC1");
    attrs.setString(Tag.StudyDescription, VR.LO, "CT chest");
    return attrs;
  }

  private static Attributes instance(String sopUid, String retrieveUrl) {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.StudyInstanceUID, VR.UI, STUDY);
    attrs.setString(Tag.SeriesInstanceUID, VR.UI, SERIES);
    attrs.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
    attrs.setString(Tag.SOPInstanceUID, VR.UI, sopUid);
    if (retrieveUrl != null) {
      attrs.setString(Tag.RetrieveURL, VR.UR, retrieveUrl);
    }
    return attrs;
  }

  @Test
  void type_1_defaults_and_type_2_empties_are_present() {
    Attributes ws = UpsWorkitem.of(SEGMENTATION).input(instance("1.1", null)).build();

    assertAll(
        () -> assertEquals("SCHEDULED", ws.getString(Tag.ProcedureStepState)),
        () -> assertEquals("READY", ws.getString(Tag.InputReadinessState)),
        () -> assertEquals("MEDIUM", ws.getString(Tag.ScheduledProcedureStepPriority)),
        () -> assertEquals("Automated Image Segmentation", ws.getString(Tag.ProcedureStepLabel)),
        () -> assertTrue(ws.containsValue(Tag.ScheduledProcedureStepStartDateTime)),
        () ->
            assertEquals(
                "128121",
                ws.getNestedDataset(Tag.ScheduledWorkitemCodeSequence).getString(Tag.CodeValue)),
        () -> assertTrue(ws.contains(Tag.TransactionUID) && !ws.containsValue(Tag.TransactionUID)),
        () -> assertTrue(ws.contains(Tag.WorklistLabel) && !ws.containsValue(Tag.WorklistLabel)),
        () -> assertTrue(ws.getSequence(Tag.ScheduledStationNameCodeSequence).isEmpty()),
        () -> assertTrue(ws.getSequence(Tag.ReferencedRequestSequence).isEmpty()),
        () ->
            assertTrue(
                ws.getSequence(Tag.UnifiedProcedureStepPerformedProcedureSequence).isEmpty()),
        () -> assertTrue(ws.contains(Tag.PatientName) && !ws.containsValue(Tag.PatientName)),
        () -> assertFalse(ws.contains(Tag.SOPInstanceUID), "the UID goes in the query parameter"),
        () -> assertFalse(ws.contains(Tag.OutputDestinationSequence), "Type 3, absent"));
  }

  @Test
  void patient_request_and_options_are_filled_from_the_study() {
    Attributes ws =
        UpsWorkitem.of(SEGMENTATION)
            .label("Lung nodules")
            .priority(UpsWorkitem.Priority.HIGH)
            .worklistLabel("AI")
            .patient(study())
            .request(study())
            .station("AI-STATION", null)
            .outputDestination("PACS")
            .outputDestinationUrl("https://pacs/dicomweb/studies")
            .input(instance("1.1", null))
            .build();

    Attributes request = ws.getNestedDataset(Tag.ReferencedRequestSequence);
    Sequence outputs = ws.getSequence(Tag.OutputDestinationSequence);
    assertAll(
        () -> assertEquals("Lung nodules", ws.getString(Tag.ProcedureStepLabel)),
        () -> assertEquals("HIGH", ws.getString(Tag.ScheduledProcedureStepPriority)),
        () -> assertEquals("AI", ws.getString(Tag.WorklistLabel)),
        () -> assertEquals("ISO_IR 100", ws.getString(Tag.SpecificCharacterSet)),
        () -> assertEquals("P1", ws.getString(Tag.PatientID)),
        () -> assertEquals("HOSP", ws.getString(Tag.IssuerOfPatientID)),
        () -> assertEquals("19700101", ws.getString(Tag.PatientBirthDate)),
        () -> assertTrue(ws.contains(Tag.PatientSex) && !ws.containsValue(Tag.PatientSex)),
        () -> assertNull(ws.getString(Tag.StudyDescription), "not a UPS attribute"),
        () -> assertEquals(STUDY, ws.getString(Tag.StudyInstanceUID)),
        () -> assertEquals(STUDY, request.getString(Tag.StudyInstanceUID)),
        () -> assertEquals("ACC1", request.getString(Tag.AccessionNumber)),
        () -> assertTrue(request.contains(Tag.RequestedProcedureID), "Type 2, empty"),
        () -> assertTrue(request.getSequence(Tag.ReferencedStudySequence).isEmpty()),
        () -> {
          Attributes station = ws.getNestedDataset(Tag.ScheduledStationNameCodeSequence);
          assertEquals("AI-STATION", station.getString(Tag.CodeValue));
          assertEquals(UpsWorkitem.LOCAL_SCHEME, station.getString(Tag.CodingSchemeDesignator));
          assertEquals("AI-STATION", station.getString(Tag.CodeMeaning));
        },
        () -> assertEquals(2, outputs.size()),
        () ->
            assertEquals(
                "PACS",
                outputs
                    .get(0)
                    .getNestedDataset(Tag.DICOMStorageSequence)
                    .getString(Tag.DestinationAE)),
        () ->
            assertEquals(
                "https://pacs/dicomweb/studies",
                outputs
                    .get(1)
                    .getNestedDataset(Tag.STOWRSStorageSequence)
                    .getString(Tag.StorageURL)));
  }

  @Test
  void inputs_are_grouped_by_series_with_their_retrieval_location() {
    Attributes other = instance("2.1", null);
    other.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.5");
    other.setString(Tag.RetrieveAETitle, VR.AE, "PACS");
    Attributes ws =
        UpsWorkitem.of(SEGMENTATION)
            .inputs(
                List.of(
                    instance("1.1", BASE + "/instances/1.1"),
                    instance("1.2", BASE + "/instances/1.2"),
                    other))
            .build();

    Sequence items = ws.getSequence(Tag.InputInformationSequence);
    Attributes first = items.get(0);
    Attributes second = items.get(1);
    assertAll(
        () -> assertEquals(2, items.size()),
        () -> assertEquals("DICOM", first.getString(Tag.TypeOfInstances)),
        () -> assertEquals(SERIES, first.getString(Tag.SeriesInstanceUID)),
        () -> assertEquals(2, first.getSequence(Tag.ReferencedSOPSequence).size()),
        () ->
            assertEquals(
                UID.CTImageStorage,
                first
                    .getSequence(Tag.ReferencedSOPSequence)
                    .get(0)
                    .getString(Tag.ReferencedSOPClassUID)),
        () ->
            assertEquals(
                BASE,
                first.getNestedDataset(Tag.WADORSRetrievalSequence).getString(Tag.RetrieveURL)),
        () -> assertFalse(first.contains(Tag.DICOMRetrievalSequence)),
        () ->
            assertEquals(
                "PACS",
                second.getNestedDataset(Tag.DICOMRetrievalSequence).getString(Tag.RetrieveAETitle)),
        () -> assertFalse(second.contains(Tag.WADORSRetrievalSequence)),
        () ->
            assertEquals(
                "https://x/studies/1/series/2",
                UpsWorkitem.seriesUrl("https://x/studies/1/series/2")));
  }

  @Test
  void a_workitem_without_input_or_identifiers_is_refused() {
    Attributes noSeries = instance("1.1", null);
    noSeries.remove(Tag.SeriesInstanceUID);
    Attributes noStudyUid = new Attributes();

    assertAll(
        () -> assertThrows(IllegalStateException.class, () -> UpsWorkitem.of(SEGMENTATION).build()),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> UpsWorkitem.of(SEGMENTATION).input(noSeries)),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> UpsWorkitem.of(SEGMENTATION).request(noStudyUid)),
        () -> assertThrows(NullPointerException.class, () -> UpsWorkitem.of(null)));
  }

  @Test
  void create_workitem_accepts_the_built_data_set() throws Exception {
    try (RecordingHttpServer server = new RecordingHttpServer()) {
      UpsRS ups = new UpsRS(UpsConfig.builder().baseUrl(server.url("/dicomweb")).build());
      Attributes ws =
          UpsWorkitem.of(SEGMENTATION).request(study()).input(instance("1.1", null)).build();

      ups.createWorkitem(ws, "1.2.3.9");

      Attributes body = UpsRS.readDatasets(server.requests().get(0).body()).get(0);
      assertEquals("SCHEDULED", body.getString(Tag.ProcedureStepState));
      assertEquals(STUDY, body.getString(Tag.StudyInstanceUID));
      assertEquals("/dicomweb/workitems?workitem=1.2.3.9", server.requests().get(0).uri());
    }
  }
}
