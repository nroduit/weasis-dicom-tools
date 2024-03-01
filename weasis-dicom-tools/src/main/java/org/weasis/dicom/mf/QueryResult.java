/*
 * Copyright (c) 2017-2019 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
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
