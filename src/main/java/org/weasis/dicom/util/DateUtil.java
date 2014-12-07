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

    private static Logger LOGGER = LoggerFactory.getLogger(DateUtil.class);

    public static String DATE_FORMAT = "yyyyMMdd";
    public static String TIME_FORMAT = "HHmmss";

    public static SimpleDateFormat DicomTimeFormat = new SimpleDateFormat(TIME_FORMAT);
    public static SimpleDateFormat DicomDateFormat = new SimpleDateFormat(DATE_FORMAT);

    private DateUtil() {
    }

    public static java.util.Date getDate(String dateInput) {
        if (dateInput != null) {
            try {
                return DicomDateFormat.parse(dateInput);
            } catch (Exception e) {
                LOGGER.error("Cannot parse date {}", dateInput);
            }
        }
        return null;
    }

    public static java.util.Date getTime(String dateInput) {
        if (dateInput != null) {
            try {
                return DicomTimeFormat.parse(dateInput);
            } catch (Exception e) {
                LOGGER.error("Cannot parse time {}", dateInput);
            }
        }
        return null;
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
