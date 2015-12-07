package com.idea.messenger;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.idea.Errors.ErrorCenter;
import com.idea.pairapp.R;
import com.idea.util.Config;
import com.idea.util.PLog;
import com.idea.util.ThreadUtils;
import com.sinch.android.rtc.ClientRegistration;
import com.sinch.android.rtc.Sinch;
import com.sinch.android.rtc.SinchClient;
import com.sinch.android.rtc.SinchClientListener;
import com.sinch.android.rtc.SinchError;

import java.lang.ref.WeakReference;
import java.util.Date;

/**
 * @author Null-Pointer on 8/28/2015.
 */
class SinchUtils {

    //    private static final String APP_KEY = "e4b6b10-e0c5-4446-bc42-8f2c704762a3";
    private static final String APP_KEY = "a9238082-659f-4a48-9813-c2c823545d69";
    //    private static final String APP_SECRET = "VtYW0Wn2DES2g7i5/6/zXw==";
    @SuppressWarnings("SpellCheckingInspection")
    private static final String APP_SECRET = "VtYW0Wn2DES2g7i5/6/zXw=="/*"6jnYbTKVb0ytmUfzsJUZMw=="*/, ENVIRONMENT = "sandbox.sinch.com";
    private static WeakReference<Looper> looperWeakReference;
    private static final String TAG = SinchUtils.class.getSimpleName();
    private static SinchClientListener clientListener = new SinchClientListener() {
        private int retryCount = 0, backOf = 500;

        @Override
        public void onClientStarted(SinchClient sinchClient) {
            PLog.d(TAG, "client started! %s", new Date().toString());
        }

        @Override
        public void onClientStopped(SinchClient sinchClient) {
            PLog.d(TAG, "client stopped %s", new Date().toString());
        }

        @Override
        public void onClientFailed(final SinchClient sinchClient, SinchError sinchError) {
            PLog.d(TAG, "error on sinch client %s", sinchError.getErrorType().toString());
            switch (sinchError.getErrorType()) {
                case NETWORK:
                    synchronized (SinchUtils.class) {
                        if (retryCount > 15) {
                            PLog.d(TAG, "failed to start sinch client after %s  attempts", 15 + "");
                            sinchClient.stopListeningOnActiveConnection();
                            sinchClient.terminate();
                            return;
                        }
                        retryCount++;
                        backOf *= 2;
                        Looper looper = getMyLooper();
                        new Handler(looper).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Context context = Config.getApplicationContext();
                                Intent intent = new Intent(context, PairAppClient.class);
                                intent.putExtra(PairAppClient.ACTION, PairAppClient.ACTION_START_SINCH_CLIENT);
                                context.startService(intent);
                            }
                        }, backOf);
                    }
                    break;
                case CAPABILITY:
                    throw new IllegalStateException("sinch client used for a service it does not support");
                case OTHER:
                    Context context = Config.getApplicationContext();
                    Intent intent = new Intent(context, PairAppClient.class);
                    intent.putExtra(PairAppClient.ACTION, PairAppClient.ACTION_START_SINCH_CLIENT);
                    ErrorCenter.reportError(TAG + "sinchFailure", context.getString(R.string.err_problem_setting_sinch_up), intent);
                    break;
                default:
                    throw new AssertionError("unknown error kind");
            }
        }

        private Looper getMyLooper() {
            if (looperWeakReference == null) {
                throw new IllegalStateException("no looper");
            }
            Looper looper = looperWeakReference.get();
            if (looper == null) {
                throw new IllegalStateException("no looper");
            }
            return looper;
        }

        @Override
        public void onRegistrationCredentialsRequired(SinchClient sinchClient, ClientRegistration clientRegistration) {
        }

        @Override
        public void onLogMessage(int i, String s, String s1) {
            logMessage(i, s, s1);
        }
    };

    static SinchClient makeSinchClient(Context context, String userId) {
        Looper looper = Looper.myLooper();
        if (looper == null) {
            throw new IllegalStateException("sinch client created on a thread with no looper");
        }
        ThreadUtils.ensureNotMain();
        looperWeakReference = new WeakReference<>(looper);
        SinchClient client = Sinch.getSinchClientBuilder().context(context).userId(userId)
                .applicationKey(APP_KEY)
                .applicationSecret(APP_SECRET)
                .environmentHost(ENVIRONMENT).build();
        client.addSinchClientListener(clientListener);
        return client;
    }


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

}
