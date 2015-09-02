package com.pair.messenger;

import android.content.Context;
import android.util.Log;

import com.pair.Config;
import com.pair.util.L;
import com.sinch.android.rtc.Sinch;
import com.sinch.android.rtc.SinchClient;

/**
 * @author Null-Pointer on 8/28/2015.
 */
class SinchUtils {

    private static final String APP_KEY = "e4b6b10-e0c5-4446-bc42-8f2c704762a3";
    @SuppressWarnings("SpellCheckingInspection")
    private static final String APP_SECRET = "6jnYbTKVb0ytmUfzsJUZMw==", ENVIRONMENT = "sandbox.sinch.com";

    static SinchClient makeSinchClient(Context context, String userId) throws SinchNotFoundException {
        if (!isSinchSupported()) {
            throw new SinchNotFoundException("Sinch not supported exception");
        }
        return Sinch.getSinchClientBuilder().context(context).userId(userId)
                .applicationKey(APP_KEY)
                .applicationSecret(APP_SECRET)
                .environmentHost(ENVIRONMENT).build();
    }

    private static boolean isSinchSupported() {
        return Config.supportsCalling();
    }

    private static boolean isSupported(String... supportedAbis) {
        for (String supportedAbi : supportedAbis) {
            if (supportedAbi.equals("armeabi") || supportedAbi.equals("armeabi-v7a")) {
                return true;
            }
        }
        return false;
    }

    public static void logMessage(int i, String s, String s1) {
        switch (i) {
            case Log.VERBOSE:
                Log.v(s, s1);
                break;
            case Log.INFO:
                L.i(s, s1);
                break;
            case Log.DEBUG:
                L.d(s, s1);
                break;
            case Log.WARN:
                L.w(s, s1);
                break;
            case Log.ERROR:
                L.e(s, s1);
                break;
            default:
                L.d(s, s1);
        }
    }

    static class SinchNotFoundException extends Exception {
        SinchNotFoundException(String message) {
            super(message);
        }
    }
}
