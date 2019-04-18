package org.weasis.dicom.web;

public class HttpServerErrorException extends RuntimeException {

    private static final long serialVersionUID = 1253673551984892314L;

    public HttpServerErrorException(String message) {
        super(message);
    }

    public HttpServerErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
