package com.yennkasa.util;

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
        return cs == null || cs.toString().trim().length() == 0;
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

    public static boolean isCapitalised(String text) {
        ensureNotNull(text);
        return text.equals(capitalise(text));
    }

    public static String capitalise(String text) {
        text = text != null ? text : "";
        if (isEmpty(text.trim())) return text;
        StringBuilder builder = new StringBuilder(text);
        boolean previousWasSpace = true; //capitalize sentence
        //we assume that the string is trimmed
        for (int i = 0; i < builder.length(); i++) {
            char c = builder.charAt(i);
            if (previousWasSpace) {//don't check whether char is letter or not
                builder.setCharAt(i, Character.toUpperCase(c));
            } else {
                builder.setCharAt(i, Character.toLowerCase(c));
            }
            previousWasSpace = Character.isSpaceChar(c);
        }
        return builder.toString();
    }

}
