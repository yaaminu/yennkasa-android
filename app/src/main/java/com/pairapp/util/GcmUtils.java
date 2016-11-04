package com.pairapp.util;

/**
 * @author by Null-Pointer on 5/27/2015.
 */
public class GcmUtils {
    private static final String TAG = GcmUtils.class.getSimpleName();

    public static boolean hasGcm() {
        return true;
    }

    public static boolean gcmUpdateRequired() {
        return false;
    }
}
