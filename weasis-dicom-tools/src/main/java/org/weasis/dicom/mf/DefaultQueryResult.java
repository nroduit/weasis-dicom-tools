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

import java.util.Collection;
import java.util.Objects;

/**
 * Default implementation of {@link QueryResult} that provides standard WADO (Web Access to DICOM
 * Objects) parameter handling for DICOM query results.
 *
 * <p>This implementation extends {@link AbstractQueryResult} with concrete WADO parameter support,
 * making it suitable for most standard DICOM web access scenarios. The WADO parameters define how
 * DICOM objects can be retrieved via web protocols.
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * WadoParameters wadoParams = new WadoParameters(...);
 * List<Patient> patients = fetchPatientsFromArchive();
 * QueryResult result = new DefaultQueryResult(patients, wadoParams);
 * }</pre>
 *
 * @see AbstractQueryResult
 * @see WadoParameters
 * @see QueryResult
 */
public class DefaultQueryResult extends AbstractQueryResult {

  protected final WadoParameters wadoParameters;

  /**
   * Creates a query result with the specified patients collection and WADO parameters.
   *
   * @param patients the collection of patients to include in this result, may be null or empty
   * @param wadoParameters the WADO parameters for web access, required
   * @throws NullPointerException if wadoParameters is null
   * @since 5.34.0.4
   */
  public DefaultQueryResult(Collection<Patient> patients, WadoParameters wadoParameters) {
    super(patients);
    this.wadoParameters = Objects.requireNonNull(wadoParameters, "WADO parameters cannot be null");
  }

  /**
   * Creates an empty query result with the specified WADO parameters.
   *
   * @param wadoParameters the WADO parameters for web access, required
   * @throws NullPointerException if wadoParameters is null
   * @since 5.34.0.4
   */
  public DefaultQueryResult(WadoParameters wadoParameters) {
    this(null, wadoParameters);
  }

  @Override
  public WadoParameters getWadoParameters() {
    return wadoParameters;
  }

  /**
   * Returns a string representation of this query result including patient count and WADO
   * parameters.
   *
   * @return a string representation of this query result
   */
  @Override
  public String toString() {
    return "DefaultQueryResult{"
        + "patientCount="
        + getPatientCount()
        + ", wadoParameters="
        + wadoParameters
        + ", hasViewerMessage="
        + (getViewerMessage() != null)
        + '}';
  }

  /**
   * Checks equality based on patients, WADO parameters, and viewer message.
   *
   * @param obj the object to compare with
   * @return true if objects are equal, false otherwise
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DefaultQueryResult that)) {
      return false;
    }
    return Objects.equals(getPatients(), that.getPatients())
        && Objects.equals(wadoParameters, that.wadoParameters)
        && Objects.equals(getViewerMessage(), that.getViewerMessage());
  }

  /**
   * Returns a hash code based on patients, WADO parameters, and viewer message.
   *
   * @return the hash code
   */
  @Override
  public int hashCode() {
    return Objects.hash(getPatients(), wadoParameters, getViewerMessage());
  }
}
