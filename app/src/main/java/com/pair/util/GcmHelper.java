package com.pair.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.io.IOException;

/**
 * Created by Null-Pointer on 5/27/2015.
 */
public class GcmHelper {
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static final String TAG = GcmHelper.class.getSimpleName();
    public static final String GCM_REG_ID = "gcmRegId";
    private static Dialog errorDialog;

    public static void register(Activity context, GCMRegCallback callback) {
        String gcmRegId = context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE).getString(GCM_REG_ID, null);
        if (gcmRegId == null) {
            new RegisterTask(context, callback).execute();
        } else {
            callback.done(null, gcmRegId);
        }
    }


    public static boolean checkPlayServices(Activity context) {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                errorDialog = GooglePlayServicesUtil.getErrorDialog(resultCode, context,
                        PLAY_SERVICES_RESOLUTION_REQUEST);
                errorDialog.setCancelable(false);
                errorDialog.show();
            } else {
                Log.i(TAG, "This device is not supported.");
            }
            return false;
        }
        return true;
    }

    private static class RegisterTask extends AsyncTask<Void, Void, RegisterTask.GcmRegResults> {
        Context context;
        GCMRegCallback callback;


        RegisterTask(Context context, GCMRegCallback callback) {
            this.context = context;
            this.callback = callback;
        }

        @Override
        protected GcmRegResults doInBackground(Void... params) {

            GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
            GcmRegResults results = new GcmRegResults();
            try {
                String registrationId = gcm.register("554068366623");
                context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE).edit().putString(GCM_REG_ID, registrationId).commit();
                results.errorMessage = null;
                results.regId = registrationId;
                return results;
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e.getCause());
                results.errorMessage = e.getMessage();
                results.regId = null;
                return results;
            }
        }

        @Override
        protected void onPostExecute(GcmRegResults results) {
            if (results.errorMessage != null) {
                callback.done(new Exception(results.errorMessage), null);
                return;
            }
            callback.done(null, results.regId);
        }

        class GcmRegResults {
            String errorMessage;
            String regId;
        }
    }

    public interface GCMRegCallback {
        void done(Exception e, String regId);
    }
}
