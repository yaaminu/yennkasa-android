package com.yennkasa.workers;

import android.content.Context;
import android.content.Intent;

import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.messenger.PairAppClient;
import com.yennkasa.util.PLog;
import com.parse.ParseBroadcastReceiver;

import io.realm.Realm;

public class BootReceiver extends ParseBroadcastReceiver {
    public static final String TAG = BootReceiver.class.getSimpleName();

    public BootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PLog.i(TAG, "booting complete");
        super.onReceive(context, intent);
        Realm realm = User.Realm(context);
        try {
            if (UserManager.getInstance().isUserVerified(realm)) {
                PairAppClient.startIfRequired(context);
            }
        } finally {
            realm.close();
        }
    }
}
