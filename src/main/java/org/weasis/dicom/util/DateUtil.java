/*******************************************************************************
 * Copyright (c) 2014 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DateUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(DateUtil.class);

    // To be thread safe the SimpleDateFormat instance is never modified, only use internally.
    private static final SimpleDateFormat dicomDate = new SimpleDateFormat("yyyyMMdd"); //$NON-NLS-1$
    private static final SimpleDateFormat dicomTime = new SimpleDateFormat("HHmmss"); //$NON-NLS-1$

    private DateUtil() {
    }

    public static Date getDicomDate(String date) {
        if (date != null) {
            try {
                if (date.length() > 8) {
                    char c = date.charAt(4);
                    if (!Character.isDigit(date.charAt(4))) {
                        // Format yyyy.mm.dd (prior DICOM3.0)
                        StringBuilder buf = new StringBuilder(10);
                        buf.append("yyyy"); //$NON-NLS-1$
                        buf.append(c);
                        buf.append("MM"); //$NON-NLS-1$
                        buf.append(c);
                        buf.append("dd"); //$NON-NLS-1$
                        return new SimpleDateFormat(buf.toString()).parse(date);
                    }
                }
                return dicomDate.parse(date);
            } catch (Exception e) {
                LOGGER.error("Parse DICOM date", e);
            }
        }
        return null;
    }

    public static Date getDicomTime(String dateTime) {
        if (dateTime != null) {
            try {
                return dicomTime.parse(dateTime);
            } catch (Exception e) {
                LOGGER.error("Parse DICOM datetime", e);
            }
        }
        return null;
    }

    public static String formatDicomDate(Date date) {
        if (date != null) {
            return dicomDate.format(date);
        }
        return ""; //$NON-NLS-1$
    }

    public static String formatDicomTime(Date date) {
        if (date != null) {
            return dicomTime.format(date);
        }
        return ""; //$NON-NLS-1$
    }

    public static Date dateTime(Date date, Date time) {
        if (time == null) {
            return date;
        } else if (date == null) {
            return time;
        }
        Calendar calendarA = Calendar.getInstance();
        calendarA.setTime(date);

        Calendar calendarB = Calendar.getInstance();
        calendarB.setTime(time);

        calendarA.set(Calendar.HOUR_OF_DAY, calendarB.get(Calendar.HOUR_OF_DAY));
        calendarA.set(Calendar.MINUTE, calendarB.get(Calendar.MINUTE));
        calendarA.set(Calendar.SECOND, calendarB.get(Calendar.SECOND));
        calendarA.set(Calendar.MILLISECOND, calendarB.get(Calendar.MILLISECOND));

        return calendarA.getTime();
    }
}
