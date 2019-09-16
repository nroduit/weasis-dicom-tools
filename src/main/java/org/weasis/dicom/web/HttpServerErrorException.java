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
