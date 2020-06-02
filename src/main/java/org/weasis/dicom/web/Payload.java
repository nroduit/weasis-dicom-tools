package org.weasis.dicom.web;

import java.io.InputStream;

public interface Payload {
    long size();

    InputStream newInputStream();
}