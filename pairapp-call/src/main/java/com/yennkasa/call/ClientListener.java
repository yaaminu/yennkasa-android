package com.yennkasa.call;

import android.support.v4.util.Pair;

import com.yennkasa.util.BuildConfig;
import com.yennkasa.util.GenericUtils;
import com.yennkasa.util.PLog;
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
    private final CallManager.RegistrationTokenSource source;

    ClientListener(CallManager.RegistrationTokenSource source) {
        this.source = source;
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
        Pair<String, Long> tokenAndSequence = source.getSinchRegistrationToken();
        if (tokenAndSequence != null) {
            PLog.d(TAG, "successfully registered");
            PLog.d(TAG, "token: %s, sequence: %d", tokenAndSequence.first, tokenAndSequence.second);
            GenericUtils.ensureNotNull(tokenAndSequence.first, tokenAndSequence.second);
            clientRegistration.register(tokenAndSequence.first, tokenAndSequence.second);
        } else {
            PLog.d(TAG, "registration failed");
            clientRegistration.registerFailed();
        }
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
