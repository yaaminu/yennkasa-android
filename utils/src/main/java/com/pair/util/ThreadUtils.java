package com.pair.util;

import android.os.Looper;

/**
 * @author Null-Pointer on 8/25/2015.
 */
public class ThreadUtils {

    public static boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    public static void ensureNotMain() {
        if (isMainThread()) {
            oops("main thread!");
        }
    }

    public static void ensureMain() {
        if (!isMainThread()) {
            oops("call must be made on the main thread!");
        }
    }

    private static void oops(String oops) {
        throw new IllegalStateException(oops);
    }
}
