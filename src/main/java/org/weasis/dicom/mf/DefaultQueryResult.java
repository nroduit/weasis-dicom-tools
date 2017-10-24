package org.weasis.dicom.mf;

import java.util.List;
import java.util.Objects;

public class DefaultQueryResult extends AbstractQueryResult {
    
    protected final WadoParameters wadoParameters;

    public DefaultQueryResult(List<Patient> patients, WadoParameters wadoParameters) {
        super(patients);
        this.wadoParameters = Objects.requireNonNull(wadoParameters);
    }

    @Override
    public WadoParameters getWadoParameters() {
        return wadoParameters;
    }

}