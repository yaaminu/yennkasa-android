package com.pairapp.workers;

import android.content.Context;
import android.content.Intent;

import com.pairapp.data.ContactSyncService;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.messenger.PairAppClient;
import com.pairapp.util.PLog;
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
                ContactSyncService.syncIfRequired(realm, context);
            }
        } finally {
            realm.close();
        }
    }
}
