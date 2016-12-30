package com.yennkasa.workers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.parse.ParseBroadcastReceiver;
import com.yennkasa.data.PublicKeysUpdater;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.messenger.YennkasaClient;
import com.yennkasa.util.PLog;

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
                YennkasaClient.startIfRequired(context);
                AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                Intent resendMessagesIntent = new Intent(context, PublicKeysUpdater.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 1009,
                        resendMessagesIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                manager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                        AlarmManager.INTERVAL_FIFTEEN_MINUTES / 7, AlarmManager.INTERVAL_HOUR,
                        pendingIntent);
            }
        } finally {
            realm.close();
        }
    }
}
