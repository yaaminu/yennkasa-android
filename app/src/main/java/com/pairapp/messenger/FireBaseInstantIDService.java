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
import com.pairapp.data.UserManager;
import com.pairapp.util.Config;
import com.pairapp.util.GenericUtils;

public class FireBaseInstantIDService extends FirebaseInstanceIdService {

    public FireBaseInstantIDService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public void onTokenRefresh() {
        synchronized (FireBaseInstantIDService.class) {
            String token = FirebaseInstanceId.getInstance().getToken();
            if (token != null) {
//                UserManager.getInstance().refreshPushToken(token);
            } else { //this device will not be able to receive new push notifications

            }
        }
    }

    public static String getInstanceID() {
        return FirebaseInstanceId.getInstance().getToken();
    }
}
