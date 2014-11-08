/*******************************************************************************
 * Copyright (c) 2014 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.tool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Status;
import org.dcm4che3.tool.findscu.FindSCU;
import org.dcm4che3.tool.findscu.FindSCU.InformationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.util.StringUtil;

public class ModalityWorklist {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModalityWorklist.class);

    public static final DicomParam PatientWeight = new DicomParam(Tag.PatientWeight);
    public static final DicomParam MedicalAlerts = new DicomParam(Tag.MedicalAlerts);
    public static final DicomParam Allergies = new DicomParam(Tag.Allergies);
    public static final DicomParam PregnancyStatus = new DicomParam(Tag.PregnancyStatus);
    public static final DicomParam RequestingPhysician = new DicomParam(Tag.RequestingPhysician);
    public static final DicomParam RequestingService = new DicomParam(Tag.RequestingService);
    public static final DicomParam RequestedProcedureDescription = new DicomParam(Tag.RequestedProcedureDescription);
    public static final DicomParam AdmissionID = new DicomParam(Tag.AdmissionID);
    public static final DicomParam SpecialNeeds = new DicomParam(Tag.SpecialNeeds);
    public static final DicomParam CurrentPatientLocation = new DicomParam(Tag.CurrentPatientLocation);
    public static final DicomParam PatientState = new DicomParam(Tag.PatientState);
    public static final DicomParam RequestedProcedureID = new DicomParam(Tag.RequestedProcedureID);
    public static final DicomParam RequestedProcedurePriority = new DicomParam(Tag.RequestedProcedurePriority);
    public static final DicomParam PatientTransportArrangements = new DicomParam(Tag.PatientTransportArrangements);
    public static final DicomParam PlacerOrderNumberImagingServiceRequest = new DicomParam(
        Tag.PlacerOrderNumberImagingServiceRequest);
    public static final DicomParam FillerOrderNumberImagingServiceRequest = new DicomParam(
        Tag.FillerOrderNumberImagingServiceRequest);
    public static final DicomParam ConfidentialityConstraintOnPatientDataDescription = new DicomParam(
        Tag.ConfidentialityConstraintOnPatientDataDescription);

    public static final DicomParam RequestedContrastAgent = new DicomParam(Tag.RequestedContrastAgent);
    public static final DicomParam ScheduledStationAETitle = new DicomParam(Tag.ScheduledStationAETitle);
    public static final DicomParam ScheduledProcedureStepStartDate =
        new DicomParam(Tag.ScheduledProcedureStepStartDate);
    public static final DicomParam ScheduledProcedureStepStartTime =
        new DicomParam(Tag.ScheduledProcedureStepStartTime);
    public static final DicomParam ScheduledPerformingPhysicianName = new DicomParam(
        Tag.ScheduledPerformingPhysicianName);
    public static final DicomParam ScheduledProcedureStepDescription = new DicomParam(
        Tag.ScheduledProcedureStepDescription);
    public static final DicomParam ScheduledProcedureStepID = new DicomParam(Tag.ScheduledProcedureStepID);
    public static final DicomParam ScheduledStationName = new DicomParam(Tag.ScheduledStationName);
    public static final DicomParam ScheduledProcedureStepLocation = new DicomParam(Tag.ScheduledProcedureStepLocation);
    public static final DicomParam PreMedication = new DicomParam(Tag.PreMedication);
    public static final DicomParam ScheduledProcedureStepStatus = new DicomParam(Tag.ScheduledProcedureStepStatus);

    private ModalityWorklist() {
    }

    /**
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param keys
     *            the matching and returning keys. DicomParam with no value is a returning key.
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(DicomNode callingNode, DicomNode calledNode, DicomParam[] spsKeys,
        DicomParam... keys) {
        return process(null, callingNode, calledNode, 0, spsKeys, keys);
    }

    /**
     * @param params
     *            optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param keys
     *            the matching and returning keys. DicomParam with no value is a returning key.
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        DicomParam[] spsKeys, DicomParam... keys) {
        return process(params, callingNode, calledNode, 0, spsKeys, keys);
    }

    /**
     * @param params
     *            optional advanced parameters (proxy, authentication, connection and TLS)
     * @param callingNode
     *            the calling DICOM node configuration
     * @param calledNode
     *            the called DICOM node configuration
     * @param cancelAfter
     *            cancel the query request after the receive of the specified number of matches.
     * @param keys
     *            the matching and returning keys. DicomParam with no value is a returning key.
     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
     *         progression.
     */
    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
        int cancelAfter, DicomParam[] spsKeys, DicomParam... keys) {
        if (callingNode == null || calledNode == null) {
            throw new IllegalArgumentException("callingNode or calledNode cannot be null!");
        }

        FindSCU findSCU = null;
        String message = null;
        AdvancedParams options = params == null ? new AdvancedParams() : params;

        try {
            findSCU = new FindSCU();
            Connection remote = findSCU.getRemoteConnection();
            Connection conn = findSCU.getConnection();
            options.configureConnect(findSCU.getAAssociateRQ(), remote, calledNode);
            options.configureBind(findSCU.getApplicationEntity(), conn, callingNode);

            // configure
            options.configure(conn);
            options.configureTLS(conn, remote);

            findSCU.setInformationModel(getInformationModel(options), options.getTsuidOrder(),
                options.getQueryOptions());

            for (DicomParam p : keys) {
                CFind.addAttributes(findSCU.getKeys(), p);
            }
            findSCU.getKeys().newSequence(Tag.RequestedProcedureCodeSequence, 1).add(new Attributes(0));
            Attributes sps = new Attributes(spsKeys.length);
            for (DicomParam p : spsKeys) {
                CFind.addAttributes(sps, p);
            }
            findSCU.getKeys().newSequence(Tag.ScheduledProcedureStepSequence, sps.size()).add(sps);
            findSCU.getKeys().newSequence(Tag.ScheduledProtocolCodeSequence, 1).add(new Attributes(0));

            findSCU.setCancelAfter(cancelAfter);
            findSCU.setPriority(options.getPriority());

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            findSCU.getDevice().setExecutor(executorService);
            findSCU.getDevice().setScheduledExecutor(scheduledExecutorService);
            try {
                findSCU.open();
                findSCU.query();
            } finally {
                findSCU.close();
                executorService.shutdown();
                scheduledExecutorService.shutdown();
            }
        } catch (Exception e) {
            message = "findscu: " + e.getMessage();
            StringUtil.logError(LOGGER, e, message);
            DicomState dcmState = findSCU == null ? null : findSCU.getState();
            if (dcmState != null) {
                dcmState.setStatus(Status.UnableToProcess);
            }
        }

        DicomState dcmState = findSCU == null ? null : findSCU.getState();
        if (dcmState == null) {
            dcmState = new DicomState(Status.UnableToProcess, message, null);
        }
        return dcmState;
    }

    private static InformationModel getInformationModel(AdvancedParams options) {
        Object model = options.getInformationModel();
        if (model instanceof InformationModel) {
            return (InformationModel) model;
        }
        return InformationModel.MWL;
    }

    public static void main(String[] args) {
        DicomParam[] SPS_RETURN_KEYS =
            { CFind.Modality, RequestedContrastAgent, new DicomParam(Tag.ScheduledStationAETitle, "ADVT"),
                ScheduledProcedureStepStartDate, ScheduledProcedureStepStartTime, ScheduledPerformingPhysicianName,
                ScheduledProcedureStepDescription, ScheduledProcedureStepID, ScheduledStationName,
                ScheduledProcedureStepLocation, PreMedication, ScheduledProcedureStepStatus };

        DicomParam[] RETURN_KEYS =
            { CFind.AccessionNumber, CFind.ReferringPhysicianName, CFind.PatientName, CFind.PatientID,
                CFind.PatientBirthDate, CFind.PatientSex, PatientWeight, MedicalAlerts, Allergies, PregnancyStatus,
                CFind.StudyInstanceUID, RequestingPhysician, RequestingService, RequestedProcedureDescription,
                AdmissionID, SpecialNeeds, CurrentPatientLocation, PatientState, RequestedProcedureID,
                RequestedProcedurePriority, PatientTransportArrangements, PlacerOrderNumberImagingServiceRequest,
                FillerOrderNumberImagingServiceRequest, ConfidentialityConstraintOnPatientDataDescription, };

        DicomNode calling = new DicomNode("WEASIS-SCU");
        DicomNode called = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);

        DicomState state = process(null, calling, called, 0, SPS_RETURN_KEYS, RETURN_KEYS);
        state.getStatus();
    }
}
