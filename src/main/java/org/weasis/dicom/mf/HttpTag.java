package org.weasis.dicom.mf;

import java.util.Objects;

public class HttpTag {
    private final String key;
    private final String value;

    public HttpTag(String key, String value) {
        this.key = Objects.requireNonNull(key);
        this.value = Objects.requireNonNull(value);
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}