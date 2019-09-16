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