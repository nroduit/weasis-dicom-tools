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
package org.weasis.dicom.mf;

public class ViewerMessage {
    public static final String TAG_DOCUMENT_MSG = "Message";
    public static final String MSG_ATTRIBUTE_TITLE = "title";
    public static final String MSG_ATTRIBUTE_DESC = "description";
    public static final String MSG_ATTRIBUTE_LEVEL = "severity";

    public enum eLevel {
        INFO, WARN, ERROR;
    }

    private final String message;
    private final String title;
    private final eLevel level;

    public ViewerMessage(String title, String message, eLevel level) {
        this.title = title;
        this.message = message;
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public String getTitle() {
        return title;
    }

    public eLevel getLevel() {
        return level;
    }
}