package com.pair.util;



/**
 * @author Null-Pointer on 8/25/2015.
 */
public class L {
    public static final String TAG = L.class.getSimpleName();

    public static void e(Exception e) {
        if (BuildConfig.DEBUG) {
            CLog.e(TAG, e.getMessage(), e.getCause());
        } else {
            CLog.e(TAG, e.getMessage());
        }
    }

    public static void e(String tag, String message) {
        CLog.e(tag, message);
    }

    public static void d(String tag, String message) {
        CLog.e(tag, message);
    }

    public static void i(String tag, String message) {
        CLog.e(tag, message);
    }

    public static void w(String tag, String message) {
        CLog.e(tag, message);
    }

    public static void wtf(String tag, String message) {
        CLog.e(tag, message);
    }

    public static void v(String tag, String message) {
        CLog.e(tag, message);
    }

    public static void e(String tag, Throwable e) {
        CLog.e(tag, e.getMessage(), e.getCause());
    }
}
