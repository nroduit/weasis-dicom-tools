package org.weasis.dicom.mf;

import java.util.List;

public interface QueryResult {

    WadoParameters getWadoParameters();

    void removePatientId(List<String> patientIdList);

    void removeStudyUid(List<String> studyUidList);

    void removeAccessionNumber(List<String> accessionNumberList);

    void removeSeriesUid(List<String> seriesUidList);
    
    void removeItemsWithoutElements();
    
    List<Patient> getPatients();

    ViewerMessage getViewerMessage();

    void setViewerMessage(ViewerMessage viewerMessage);

}