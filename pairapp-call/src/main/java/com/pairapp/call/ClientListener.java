package com.pairapp.call;

import com.pairapp.util.BuildConfig;
import com.pairapp.util.PLog;
import com.sinch.android.rtc.ClientRegistration;
import com.sinch.android.rtc.SinchClient;
import com.sinch.android.rtc.SinchClientListener;
import com.sinch.android.rtc.SinchError;

import static android.util.Log.DEBUG;
import static android.util.Log.ERROR;
import static android.util.Log.INFO;
import static android.util.Log.VERBOSE;

/**
 * @author aminu on 7/14/2016.
 */
class ClientListener implements SinchClientListener {

    public static final String TAG = CallManager.class.getSimpleName();

    ClientListener() {
    }

    @Override
    public void onClientStarted(SinchClient sinchClient) {
        PLog.d(TAG, "client started");
    }

    @Override
    public void onClientStopped(SinchClient sinchClient) {
        PLog.d(TAG, "client stopped");
    }

    @Override
    public void onClientFailed(SinchClient sinchClient, SinchError sinchError) {
        PLog.e(TAG, sinchError.getMessage());
    }

    @Override
    public void onRegistrationCredentialsRequired(SinchClient sinchClient, ClientRegistration clientRegistration) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onLogMessage(int level, String tag, String message) {
        switch (level) {
            case VERBOSE:
                PLog.v(tag, message);
                break;
            case DEBUG:
                PLog.d(tag, message);
                break;
            case INFO:
                PLog.i(tag, message);
                break;
            case ERROR:
                PLog.e(tag, message);
                break;
            default:
                if (BuildConfig.DEBUG) {
                    throw new AssertionError();
                }
                PLog.f(tag, message);
        }
    }
}
