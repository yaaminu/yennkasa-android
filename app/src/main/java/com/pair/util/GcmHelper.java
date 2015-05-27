package com.pair.util;

import android.content.Context;

/**
 * Created by Null-Pointer on 5/27/2015.
 */
public class GcmHelper {

    public static void register(Context context, GCMRegCallback callback) {

    }

    public static interface GCMRegCallback {
        void done(Exception e, String regId);
    }
}
