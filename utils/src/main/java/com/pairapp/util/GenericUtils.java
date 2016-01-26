package com.pairapp.util;

import android.support.annotation.StringRes;

/**
 * author Null-Pointer on 1/11/2016.
 */
public class GenericUtils {
    private GenericUtils() {
    }

    public static void ensureNotNull(Object... o) {
        if (o == null) throw new IllegalArgumentException("null");
        for (int i = 0; i < o.length; i++) {
            if (o[i] == null) {
                throw new IllegalArgumentException("null");
            }
        }
    }

    public static void ensureNotNull(Object o, String message) {
        ensureNotNull(message);
        if (o == null) throw new IllegalArgumentException(message);
    }

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public static String getString(@StringRes int res) {
        return Config.getApplicationContext().getString(res);
    }

    public static String getString(@StringRes int res, Object... args) {
        return Config.getApplicationContext().getString(res, args);
    }

    public static void ensureNotEmpty(String... args) {
        if (args == null) throw new IllegalArgumentException("null");
        for (int i = 0; i < args.length; i++) {
            if (isEmpty(args[i])) {
                throw new IllegalArgumentException("null");
            }
        }
    }

    public static void ensureConditionTrue(boolean condition, String message) {
        message = message == null ? "" : message;
        if (!condition)
            throw new IllegalArgumentException(message);
    }

}
