package com.pairapp.util;

import android.content.Context;
import android.text.format.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;

/**
 * @author _2am on 9/18/2015.
 */
public class SimpleDateUtil {

    private static SimpleDateFormat dayPrecisionFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US),
            secondsPrecissionFormatter = new SimpleDateFormat("yyyyMMdd_HHmmss");

    public static String formatDateRage(Context context, Date fromWhen) {
        return formatInternal(context, fromWhen);
    }

    private static String formatInternal(Context context, Date when) {
        return formatInternal(context, new Formatter(Locale.getDefault()), when);
    }

    private static String formatInternal(Context context, Formatter formatter, Date when) {
        final Date today = new Date();
        long elapsed = today.getTime() - when.getTime();

        final long oneDay = 24 * 60 * 60 * 1000;

        if (elapsed <= 8 * oneDay /*8 days apart (at least same week)*/) { //one more day for correctness
            if (elapsed <= 2 * oneDay/*yesterday*/) { //one more day for correctness.
                if (today.getDay() == when.getDay()) {
                    return context.getString(R.string.today);
                }
                //we are checking for roll-overs for e.g sunday=1 and saturday = 7 in
                //in the western locales
                final long daysBetween = Math.abs(today.getDay() - when.getDay());
                if (daysBetween == 1 || daysBetween > 2 /*handle roll-overs*/) {
                    return context.getString(R.string.yesterday);
                }
            }
            return DateUtils.formatDateTime(context, when.getTime(), DateUtils.FORMAT_SHOW_WEEKDAY);
        }
        return DateUtils.formatDateTime(context, when.getTime(), DateUtils.FORMAT_NUMERIC_DATE);
    }

    public static String formatSessionDate(Date time) {
//        if (time.getTime() > System.currentTimeMillis() + 24 * 60 * 60 * 1000) {
//            throw new IllegalArgumentException("date in the future");
//        }
        return dayPrecisionFormatter.format(time); //this will be unique for every day
    }

    public static String formatSessionDate() {
        return formatSessionDate(new Date());
    }

    public static String timeStampNow() {
        return timeStampNow(new Date());
    }

    public static String timeStampNow(Date date) {
        if (date == null) {
            throw new IllegalArgumentException("null!");
        }
        return secondsPrecissionFormatter.format(date);
    }
}
