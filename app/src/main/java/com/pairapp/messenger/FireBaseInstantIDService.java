package com.pairapp.messenger;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdReceiver;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.pairapp.util.Config;
import com.pairapp.util.GenericUtils;

public class FireBaseInstantIDService extends FirebaseInstanceIdService {

    public static final String PROJECT_90955513635908935 = "project-90955513635908935";
    public static final String API_KEY = "AIzaSyBjUMhHCqKC_VWYFHhCPdkI-5-qcqYiUxY";
    public static final String GCM_SENDER_ID = "gcmSenderID";

    public FireBaseInstantIDService() {
    }

    @Override
    public void onTokenRefresh() {
        synchronized (FireBaseInstantIDService.class) {
            FirebaseApp firebaseApp = FirebaseApp.initializeApp(this, new FirebaseOptions.Builder().setApplicationId(PROJECT_90955513635908935)
                    .setApiKey(API_KEY)
                    .setGcmSenderId(GCM_SENDER_ID).build());
            String token = FirebaseInstanceId.getInstance(firebaseApp).getToken();
            if (GenericUtils.isEmpty(Config.getApplicationWidePrefs().getString(FireBaseInstantIDService.class.getName() + "_token__", null))) {
                //first time!!!

            }
        }
    }

    public static String getInstanceID() {
        return FirebaseInstanceId.getInstance().getToken();
    }
}
