package org.weasis.dicom.mf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class AbstractQueryResult implements QueryResult{

    protected final List<Patient> patients;
    protected ViewerMessage viewerMessage;

    public AbstractQueryResult() {
        this(null);
    }

    public AbstractQueryResult(List<Patient> patients) {
        this.patients = Optional.ofNullable(patients).orElseGet(ArrayList::new);
    }
    
    @Override
    public abstract WadoParameters getWadoParameters();
    
    @Override
    public void removePatientId(List<String> patientIdList) {
        if (patientIdList != null && !patientIdList.isEmpty()) {
            for (int i = patients.size() - 1; i >= 0; i--) {
                if (!patientIdList.contains(patients.get(i).getPatientID())) {
                    patients.remove(i);
                }
            }
        }
    }

    @Override
    public void removeStudyUid(List<String> studyUidList) {
        if (studyUidList != null && !studyUidList.isEmpty()) {
            for (Patient p : patients) {
                List<Study> studies = p.getStudies();
                for (int i = studies.size() - 1; i >= 0; i--) {
                    if (!studyUidList.contains(studies.get(i).getStudyInstanceUID())) {
                        studies.remove(i);
                    }
                }
            }
        }
    }

    @Override
    public void removeAccessionNumber(List<String> accessionNumberList) {
        if (accessionNumberList != null && !accessionNumberList.isEmpty()) {
            for (Patient p : patients) {
                List<Study> studies = p.getStudies();
                for (int i = studies.size() - 1; i >= 0; i--) {
                    if (!accessionNumberList.contains(studies.get(i).getAccessionNumber())) {
                        studies.remove(i);
                    }
                }
            }
        }
    }

    @Override
    public void removeSeriesUid(List<String> seriesUidList) {
        if (seriesUidList != null && !seriesUidList.isEmpty()) {
            for (Patient p : patients) {
                List<Study> studies = p.getStudies();
                for (int i = studies.size() - 1; i >= 0; i--) {
                    List<Series> series = studies.get(i).getSeriesList();
                    for (int k = series.size() - 1; k >= 0; k--) {
                        if (!seriesUidList.contains(series.get(k).getSeriesInstanceUID())) {
                            series.remove(k);
                            if (series.isEmpty()) {
                                studies.remove(i);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<Patient> getPatients() {
        return patients;
    }

    @Override
    public ViewerMessage getViewerMessage() {
        return viewerMessage;
    }

    @Override
    public void setViewerMessage(ViewerMessage viewerMessage) {
        this.viewerMessage = viewerMessage;
    }

}