/*******************************************************************************
 * Copyright (c) 2009-2019 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.op;


import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.ElementDictionary;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;

public class CFind {

    private static final Logger LOGGER = LoggerFactory.getLogger(CFind.class);

    public static final DicomParam PatientID = new DicomParam(Tag.PatientID);
    public static final DicomParam IssuerOfPatientID = new DicomParam(Tag.IssuerOfPatientID);
    public static final DicomParam PatientName = new DicomParam(Tag.PatientName);
    public static final DicomParam PatientBirthDate = new DicomParam(Tag.PatientBirthDate);
    public static final DicomParam PatientSex = new DicomParam(Tag.PatientSex);

    public static final DicomParam StudyInstanceUID = new DicomParam(Tag.StudyInstanceUID);
    public static final DicomParam AccessionNumber = new DicomParam(Tag.AccessionNumber);
    public static final DicomParam IssuerOfAccessionNumberSequence =
        new DicomParam(Tag.IssuerOfAccessionNumberSequence);
    public static final DicomParam StudyID = new DicomParam(Tag.StudyID);
    public static final DicomParam ReferringPhysicianName = new DicomParam(Tag.ReferringPhysicianName);
    public static final DicomParam StudyDescription = new DicomParam(Tag.StudyDescription);
    public static final DicomParam StudyDate = new DicomParam(Tag.StudyDate);
    public static final DicomParam StudyTime = new DicomParam(Tag.StudyTime);

    public static final DicomParam SeriesInstanceUID = new DicomParam(Tag.SeriesInstanceUID);
    public static final DicomParam Modality = new DicomParam(Tag.Modality);
    public static final DicomParam SeriesNumber = new DicomParam(Tag.SeriesNumber);
    public static final DicomParam SeriesDescription = new DicomParam(Tag.SeriesDescription);

    public static final DicomParam SOPInstanceUID = new DicomParam(Tag.SOPInstanceUID);
    public static final DicomParam InstanceNumber = new DicomParam(Tag.InstanceNumber);

    private CFind() {
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
    public static DicomState process(DicomNode callingNode, DicomNode calledNode, DicomParam... keys) {
        return null;
  //      return process(null, callingNode, calledNode, 0, QueryRetrieveLevel.STUDY, keys);
    }
//
//    /**
//     * @param params
//     *            optional advanced parameters (proxy, authentication, connection and TLS)
//     * @param callingNode
//     *            the calling DICOM node configuration
//     * @param calledNode
//     *            the called DICOM node configuration
//     * @param keys
//     *            the matching and returning keys. DicomParam with no value is a returning key.
//     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
//     *         progression.
//     */
//    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
//        DicomParam... keys) {
//        return process(params, callingNode, calledNode, 0, QueryRetrieveLevel.STUDY, keys);
//    }
//
//    /**
//     * @param params
//     *            optional advanced parameters (proxy, authentication, connection and TLS)
//     * @param callingNode
//     *            the calling DICOM node configuration
//     * @param calledNode
//     *            the called DICOM node configuration
//     * @param cancelAfter
//     *            cancel the query request after the receive of the specified number of matches.
//     * @param level
//     *            specifies retrieve level. Use by default STUDY for PatientRoot, StudyRoot, PatientStudyOnly model.
//     * @param keys
//     *            the matching and returning keys. DicomParam with no value is a returning key.
//     * @return The DicomSate instance which contains the DICOM response, the DICOM status, the error message and the
//     *         progression.
//     */
//    public static DicomState process(AdvancedParams params, DicomNode callingNode, DicomNode calledNode,
//        int cancelAfter, QueryRetrieveLevel level, DicomParam... keys) {
//        if (callingNode == null || calledNode == null) {
//            throw new IllegalArgumentException("callingNode or calledNode cannot be null!");
//        }
//
//        AdvancedParams options = params == null ? new AdvancedParams() : params;

//        try (FindSCU findSCU = new FindSCU()) {
//            Connection remote = findSCU.getRemoteConnection();
//            Connection conn = findSCU.getConnection();
//            options.configureConnect(findSCU.getAAssociateRQ(), remote, calledNode);
//            options.configureBind(findSCU.getApplicationEntity(), conn, callingNode);
//            DeviceOpService service = new DeviceOpService(findSCU.getDevice());
//
//            // configure
//            options.configure(conn);
//            options.configureTLS(conn, remote);
//
//            findSCU.setInformationModel(getInformationModel(options), options.getTsuidOrder(),
//                options.getQueryOptions());
//            if (level != null) {
//                findSCU.addLevel(level.name());
//            }
//
//            for (DicomParam p : keys) {
//                addAttributes(findSCU.getKeys(), p);
//            }
//            findSCU.setCancelAfter(cancelAfter);
//            findSCU.setPriority(options.getPriority());
//
//            service.start();
//            try {
//                DicomState dcmState = findSCU.getState();
//                long t1 = System.currentTimeMillis();
//                findSCU.open();
//                long t2 = System.currentTimeMillis();
//                findSCU.query();
//                ServiceUtil.forceGettingAttributes(dcmState, findSCU);
//                long t3 = System.currentTimeMillis();
//                String timeMsg =
//                    MessageFormat.format("DICOM C-Find connected in {2}ms from {0} to {1}. Query in {3}ms.",
//                        findSCU.getAAssociateRQ().getCallingAET(), findSCU.getAAssociateRQ().getCalledAET(), t2 - t1,
//                        t3 - t2);
//                return DicomState.buildMessage(dcmState, timeMsg, null);
//            } catch (Exception e) {
//                LOGGER.error("findscu", e);
//                ServiceUtil.forceGettingAttributes(findSCU.getState(), findSCU);
//                return DicomState.buildMessage(findSCU.getState(), null, e);
//            } finally {
//                FileUtil.safeClose(findSCU);
//                service.stop();
//            }
//        } catch (Exception e) {
//            LOGGER.error("findscu", e);
//            return new DicomState(Status.UnableToProcess,
//                "DICOM Find failed" + StringUtil.COLON_AND_SPACE + e.getMessage(), null);
//        }
//    }
//
//    private static InformationModel getInformationModel(AdvancedParams options) {
//        Object model = options.getInformationModel();
//        if (model instanceof InformationModel) {
//            return (InformationModel) model;
//        }
//        return InformationModel.StudyRoot;
//    }

    public static void addAttributes(DicomObject attrs, DicomParam param) {
        int tag = param.getTag();
        String[] ss = param.getValues();
        VR vr = ElementDictionary.vrOf(tag, attrs.getPrivateCreator(tag));
        if (ss == null || ss.length == 0) {
            // Returning key
            if (vr == VR.SQ) {
                attrs.newDicomSequence(tag);
            } else {
                attrs.setNull(tag, vr);
            }
        } else {
            // Matching key
            attrs.setString(tag, vr, ss);
        }
    }

}
