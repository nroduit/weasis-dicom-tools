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

import java.io.IOException;

public class MultipartStreamException extends IOException {
    private static final long serialVersionUID = -4358358366372546933L;

    public MultipartStreamException(String message) {
        super(message);
    }

    public MultipartStreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
