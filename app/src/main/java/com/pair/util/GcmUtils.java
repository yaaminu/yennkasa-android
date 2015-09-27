package com.pair.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.pair.pairapp.BuildConfig;

import java.io.IOException;


/**
 * @author by Null-Pointer on 5/27/2015.
 */
public class GcmUtils {
    public static final String GCM_REG_ID = "gcmRegId";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static final String TAG = GcmUtils.class.getSimpleName();

    public static void register(Activity context, GCMRegCallback callback) {
        String gcmRegId = context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE).getString(GCM_REG_ID, null);
        if (gcmRegId == null) {
            new RegisterTask(context, callback).execute();
        } else {
            callback.done(null, gcmRegId);
        }
    }


    public static boolean checkPlayServices(Activity context) {
        // FIXME: 8/14/2015 change this

        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
        PLog.i(TAG, "results code: " + resultCode);
        if (resultCode != ConnectionResult.SUCCESS && resultCode != ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED) {
            return false;
        }
        return true;
    }

    public static void unRegister(final Context context, final UnregisterCallback callBack) {

        new AsyncTask<Void, Void, Exception>() {

            @SuppressLint("CommitPrefEdits")
            @Override
            protected Exception doInBackground(Void... params) {
                GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
                try {
                    gcm.unregister();
                    SharedPreferences sharedPreferences = context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE);
                    sharedPreferences.edit().remove(GCM_REG_ID).commit();
                    return null;
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) {
                        PLog.e(TAG, e.getMessage(), e.getCause());
                    } else {
                        PLog.e(TAG, e.getMessage());
                    }
                    return e;
                }
            }

            @Override
            protected void onPostExecute(Exception e) {
                callBack.done(e);
            }
        }.execute();

    }

    public interface UnregisterCallback {
        void done(Exception e);
    }

    public interface GCMRegCallback {
        void done(Exception e, String regId);
    }

    private static class RegisterTask extends AsyncTask<Void, Void, RegisterTask.GcmRegResults> {
        Context context;
        GCMRegCallback callback;

        RegisterTask(Context context, GCMRegCallback callback) {
            this.context = context;
            this.callback = callback;
        }

        @SuppressLint("CommitPrefEdits")
        @Override
        protected GcmRegResults doInBackground(Void... params) {
            // FIXME: 8/15/2015
            if (true) {
                return new GcmRegResults("kflafkdl;fk", null);
            }
            SharedPreferences sharedPreferences = context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE);
            String regId = sharedPreferences.getString(GCM_REG_ID, null);
            if (regId != null) {
                return new GcmRegResults(regId, null);
            }
            try {
                GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
                String registrationId = gcm.register("554068366623");
                sharedPreferences.edit().putString(GCM_REG_ID, registrationId).commit();
                return new GcmRegResults(registrationId, null);
            } catch (IOException e) {
                PLog.e(TAG, e.getMessage(), e.getCause());
                return new GcmRegResults(null, e);
            }

        }

        @Override
        protected void onPostExecute(GcmRegResults results) {
            callback.done(results.error, results.regId);

        }

        class GcmRegResults {
            Exception error;
            String regId;
            GcmRegResults(String regId, Exception error) {
                this.regId = regId;
                this.error = error;
            }
        }
    }
}
