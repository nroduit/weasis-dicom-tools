package org.weasis.dicom.web;

import java.io.InputStream;
import java.nio.ByteBuffer;

public interface Payload {
    long size();

    ByteBuffer newByteBuffer();

    InputStream newInputStream();
  }