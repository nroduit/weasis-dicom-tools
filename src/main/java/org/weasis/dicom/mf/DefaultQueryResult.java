package org.weasis.dicom.mf;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultQueryResult extends AbstractQueryResult {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultQueryResult.class);

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