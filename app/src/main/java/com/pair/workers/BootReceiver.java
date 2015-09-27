package com.pair.workers;

import android.content.Context;
import android.content.Intent;

import com.pair.data.UserManager;
import com.pair.messenger.PairAppClient;
import com.pair.util.CLog;
import com.parse.ParseBroadcastReceiver;

public class BootReceiver extends ParseBroadcastReceiver {
    public static final String TAG = BootReceiver.class.getSimpleName();

    public BootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        CLog.i(TAG, "booting complete");
        super.onReceive(context, intent);
        if (UserManager.getInstance().isUserVerified()) {
            PairAppClient.startIfRequired(context);
            ContactSyncService.syncIfRequired(context);
        }
    }
}
