package com.pair.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
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
        Log.i(TAG, "resutlt code: " + resultCode);
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
            //FIXME
            if(true){
                return new GcmRegResults("fakeId",null);
            }
            SharedPreferences sharedPreferences = context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE);
            String regId = sharedPreferences.getString(GCM_REG_ID, null);
            if (regId != null) {
                return new GcmRegResults(regId, null);
            }
            //else
            try {
                GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
                String registrationId = gcm.register("554068366623");
                sharedPreferences.edit().putString(GCM_REG_ID, registrationId).commit();
                return new GcmRegResults(registrationId, null);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e.getCause());
                return new GcmRegResults(null, e);
            }
        }

        @Override
        protected void onPostExecute(GcmRegResults results) {
            callback.done(results.error, results.regId);

        }

        boolean foo() throws Exception{
            return  true;
        }
        class GcmRegResults {
            GcmRegResults(String regId, Exception error) {
                this.regId = regId;
                this.error = error;
            }

            Exception error;
            String regId;
        }
    }

    public interface GCMRegCallback {
        void done(Exception e, String regId);
    }
}
