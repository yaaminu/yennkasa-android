package com.idea.workers;

import android.content.Context;
import android.content.Intent;

import com.idea.data.UserManager;
import com.idea.messenger.PairAppClient;
import com.idea.util.PLog;
import com.parse.ParseBroadcastReceiver;

public class BootReceiver extends ParseBroadcastReceiver {
    public static final String TAG = BootReceiver.class.getSimpleName();

    public BootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PLog.i(TAG, "booting complete");
        super.onReceive(context, intent);
        if (UserManager.getInstance().isUserVerified()) {
            PairAppClient.startIfRequired(context);
            ContactSyncService.syncIfRequired(context);
        }
    }
}
