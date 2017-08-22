package org.weasis.dicom.mf;

import java.util.List;
import java.util.Map;

public interface QueryResult {

    WadoParameters getWadoParameters();

    void removePatientId(List<String> patientIdList, boolean containsIssuer);

    void removeStudyUid(List<String> studyUidList);

    void removeAccessionNumber(List<String> accessionNumberList);

    void removeSeriesUid(List<String> seriesUidList);

    void removeItemsWithoutElements();

    Map<String, Patient> getPatients();

    ViewerMessage getViewerMessage();

    void setViewerMessage(ViewerMessage viewerMessage);

}