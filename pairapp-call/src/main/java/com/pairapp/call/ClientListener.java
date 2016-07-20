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
    public static final int DEFAULT_DELAY = 3000;
    private final CallManager callManager;
    private long retryTime = DEFAULT_DELAY;
    private volatile int clientState = 0;

    ClientListener(CallManager callManager) {
        this.callManager = callManager;
    }

    @Override
    public void onClientStarted(SinchClient sinchClient) {
        PLog.d(TAG, "client started");
        clientState = 1;
        retryTime = DEFAULT_DELAY;
    }

    @Override
    public void onClientStopped(SinchClient sinchClient) {
        PLog.d(TAG, "client stopped");
        clientState = 2;
    }

    @Override
    public void onClientFailed(SinchClient sinchClient, SinchError sinchError) {
        PLog.e(TAG, sinchError.getMessage());
//        PLog.d(TAG, "restarting client at %s millis later", retryTime);
//        if (sinchError.getErrorType() == ErrorType.NETWORK || sinchError.getErrorType() == ErrorType.SIP) {
//            if (clientState != 2 && clientState != 1) {
//                new Handler(Looper.myLooper()).postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        if (clientState != 2 && clientState != 1) {
//                            retryTime *= 2;
//                            if (retryTime > SimpleDateUtil.ONE_HOUR / 4) {
//                                retryTime = SimpleDateUtil.ONE_HOUR / 4;
//                            }
//                            callManager.restart();
//                        }
//                    }
//                }, retryTime);
//            }
//        } else {
//            PLog.f(TAG, "unknown error %s", sinchError.getErrorType());
//        }
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
