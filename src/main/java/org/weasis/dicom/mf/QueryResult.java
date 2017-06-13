package org.weasis.dicom.mf;

import java.util.List;

import org.weasis.dicom.mf.Patient;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.mf.ArcQuery.ViewerMessage;

public interface QueryResult {

    WadoParameters getWadoParameters();

    void removePatientId(List<String> patientIdList);

    void removeStudyUid(List<String> studyUidList);

    void removeAccessionNumber(List<String> accessionNumberList);

    void removeSeriesUid(List<String> seriesUidList);

    List<Patient> getPatients();

    ViewerMessage getViewerMessage();

    void setViewerMessage(ViewerMessage viewerMessage);

}