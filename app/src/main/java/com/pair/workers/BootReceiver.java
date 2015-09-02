package com.pair.workers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.pair.data.UserManager;
import com.pair.messenger.PairAppClient;
import com.parse.PushService;

public class BootReceiver extends BroadcastReceiver {
    public static final String TAG = BootReceiver.class.getSimpleName();

    public BootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "booting complete");
        PushService.startServiceIfRequired(context);
        if (UserManager.getInstance().isUserVerified()) {
            PairAppClient.start(context);
            ContactSyncService.start(context);
        }
    }
}
