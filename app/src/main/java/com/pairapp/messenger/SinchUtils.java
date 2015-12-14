package com.pairapp.messenger;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import com.pairapp.util.PLog;
import com.pairapp.util.ThreadUtils;
import com.sinch.android.rtc.Sinch;
import com.sinch.android.rtc.SinchClient;
import com.sinch.android.rtc.SinchClientListener;

/**
 * @author Null-Pointer on 8/28/2015.
 */
class SinchUtils {

    static SinchClient makeSinchClient(Context context, String userId, SinchClientListener clientListener) {
        Looper looper = Looper.myLooper();
        if (looper == null) {
            throw new IllegalStateException("sinch client created on a thread with no looper");
        }
        ThreadUtils.ensureNotMain();
        SinchClient client = Sinch.getSinchClientBuilder().context(context).userId(userId)
                .applicationKey(APP_KEY)
                .applicationSecret(APP_SECRET)
                .environmentHost(ENVIRONMENT).build();
        client.addSinchClientListener(clientListener);
        return client;
    }

    //    private static final String APP_KEY = "e4b6b10-e0c5-4446-bc42-8f2c704762a3";
    private static final String APP_KEY = "a9238082-659f-4a48-9813-c2c823545d69";
    //    private static final String APP_SECRET = "VtYW0Wn2DES2g7i5/6/zXw==";

    public static void logMessage(int i, String s, String s1) {
        switch (i) {
            case Log.VERBOSE:
                PLog.v(s, s1);
                break;
            case Log.INFO:
                PLog.i(s, s1);
                break;
            case Log.DEBUG:
                PLog.d(s, s1);
                break;
            case Log.WARN:
                PLog.w(s, s1);
                break;
            case Log.ERROR:
                PLog.e(s, s1);
                break;
            default:
                PLog.d(s, s1);
        }
    }

    @SuppressWarnings("SpellCheckingInspection")
    private static final String APP_SECRET = "VtYW0Wn2DES2g7i5/6/zXw=="/*"6jnYbTKVb0ytmUfzsJUZMw=="*/, ENVIRONMENT = "sandbox.sinch.com";


}
