package com.pair.util;

import android.os.Looper;

/**
 * @author Null-Pointer on 8/25/2015.
 */
public class ThreadUtils {

    public static boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }
}
