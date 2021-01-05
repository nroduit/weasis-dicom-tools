/*
 * Copyright (c) 2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.op;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.net.DicomServiceException;

public enum QueryRetrieveLevel {
  PATIENT {
    @Override
    protected IOD queryKeysIOD(QueryRetrieveLevel rootLevel, boolean relational) {
      IOD iod = new IOD();
      iod.add(
          new IOD.DataElement(Tag.StudyInstanceUID, VR.UI, IOD.DataElementType.TYPE_0, -1, -1, 0));
      iod.add(
          new IOD.DataElement(Tag.SeriesInstanceUID, VR.UI, IOD.DataElementType.TYPE_0, -1, -1, 0));
      iod.add(
          new IOD.DataElement(Tag.SOPInstanceUID, VR.UI, IOD.DataElementType.TYPE_0, -1, -1, 0));
      return iod;
    }

    @Override
    protected IOD retrieveKeysIOD(QueryRetrieveLevel rootLevel, boolean relational) {
      IOD iod = queryKeysIOD(rootLevel, relational);
      iod.add(new IOD.DataElement(Tag.PatientID, VR.LO, IOD.DataElementType.TYPE_1, 1, 1, 0));
      return iod;
    }
  },
  STUDY {
    @Override
    protected IOD queryKeysIOD(QueryRetrieveLevel rootLevel, boolean relational) {
      IOD iod = new IOD();
      iod.add(
          new IOD.DataElement(
              Tag.PatientID,
              VR.LO,
              !relational && rootLevel == QueryRetrieveLevel.PATIENT
                  ? IOD.DataElementType.TYPE_1
                  : IOD.DataElementType.TYPE_3,
              1,
              1,
              0));
      iod.add(
          new IOD.DataElement(Tag.SeriesInstanceUID, VR.UI, IOD.DataElementType.TYPE_0, -1, -1, 0));
      iod.add(
          new IOD.DataElement(Tag.SOPInstanceUID, VR.UI, IOD.DataElementType.TYPE_0, -1, -1, 0));
      return iod;
    }

    @Override
    protected IOD retrieveKeysIOD(QueryRetrieveLevel rootLevel, boolean relational) {
      IOD iod = queryKeysIOD(rootLevel, relational);
      iod.add(
          new IOD.DataElement(Tag.StudyInstanceUID, VR.UI, IOD.DataElementType.TYPE_1, -1, -1, 0));
      return iod;
    }
  },
  SERIES {
    @Override
    protected IOD queryKeysIOD(QueryRetrieveLevel rootLevel, boolean relational) {
      IOD iod = new IOD();
      iod.add(
          new IOD.DataElement(
              Tag.PatientID,
              VR.LO,
              !relational && rootLevel == QueryRetrieveLevel.PATIENT
                  ? IOD.DataElementType.TYPE_1
                  : IOD.DataElementType.TYPE_3,
              1,
              1,
              0));
      iod.add(
          new IOD.DataElement(
              Tag.StudyInstanceUID,
              VR.UI,
              !relational ? IOD.DataElementType.TYPE_1 : IOD.DataElementType.TYPE_3,
              1,
              1,
              0));
      iod.add(
          new IOD.DataElement(Tag.SOPInstanceUID, VR.UI, IOD.DataElementType.TYPE_0, -1, -1, 0));
      return iod;
    }

    @Override
    protected IOD retrieveKeysIOD(QueryRetrieveLevel rootLevel, boolean relational) {
      IOD iod = queryKeysIOD(rootLevel, relational);
      iod.add(
          new IOD.DataElement(Tag.SeriesInstanceUID, VR.UI, IOD.DataElementType.TYPE_1, -1, -1, 0));
      return iod;
    }
  },
  IMAGE {
    @Override
    protected IOD queryKeysIOD(QueryRetrieveLevel rootLevel, boolean relational) {
      IOD iod = new IOD();
      iod.add(
          new IOD.DataElement(
              Tag.PatientID,
              VR.LO,
              !relational && rootLevel == QueryRetrieveLevel.PATIENT
                  ? IOD.DataElementType.TYPE_1
                  : IOD.DataElementType.TYPE_3,
              1,
              1,
              0));
      iod.add(
          new IOD.DataElement(
              Tag.StudyInstanceUID,
              VR.UI,
              !relational ? IOD.DataElementType.TYPE_1 : IOD.DataElementType.TYPE_3,
              1,
              1,
              0));
      iod.add(
          new IOD.DataElement(
              Tag.SeriesInstanceUID,
              VR.UI,
              !relational ? IOD.DataElementType.TYPE_1 : IOD.DataElementType.TYPE_3,
              1,
              1,
              0));
      return iod;
    }

    @Override
    protected IOD retrieveKeysIOD(QueryRetrieveLevel rootLevel, boolean relational) {
      IOD iod = queryKeysIOD(rootLevel, relational);
      iod.add(
          new IOD.DataElement(Tag.SOPInstanceUID, VR.UI, IOD.DataElementType.TYPE_1, -1, -1, 0));
      return iod;
    }
  },
  FRAME {
    @Override
    protected IOD queryKeysIOD(QueryRetrieveLevel rootLevel, boolean relational) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected IOD retrieveKeysIOD(QueryRetrieveLevel rootLevel, boolean relational) {
      return IMAGE.retrieveKeysIOD(rootLevel, relational);
    }
  };

  public static QueryRetrieveLevel valueOf(DicomObject attrs, String[] qrLevels)
      throws DicomServiceException {
    ValidationResult result = new ValidationResult();
    //        attrs.validate(new IOD.DataElement(Tag.QueryRetrieveLevel, VR.LO,
    //                IOD.DataElementType.TYPE_1, 1, 1, 0).setValues(qrLevels),
    //                result);
    check(result);
    return QueryRetrieveLevel.valueOf(attrs.getString(Tag.QueryRetrieveLevel).orElse(null));
  }

  public void validateQueryKeys(DicomObject attrs, QueryRetrieveLevel rootLevel, boolean relational)
      throws DicomServiceException {
    //    check(attrs.validate(queryKeysIOD(rootLevel, relational)));
  }

  public void validateRetrieveKeys(
      DicomObject attrs, QueryRetrieveLevel rootLevel, boolean relational)
      throws DicomServiceException {
    //      check(attrs.validate(retrieveKeysIOD(rootLevel, relational)));
  }

  protected abstract IOD queryKeysIOD(QueryRetrieveLevel rootLevel, boolean relational);

  protected abstract IOD retrieveKeysIOD(QueryRetrieveLevel rootLevel, boolean relational);

  private static void check(ValidationResult result) throws DicomServiceException {
    if (!result.isValid()) {
      //            throw new DicomServiceException(
      //                    Status.IdentifierDoesNotMatchSOPClass,
      //                    result.getErrorComment())
      //                .setOffendingElements(result.getOffendingElements());
    }
  }
}
