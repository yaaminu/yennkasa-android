package com.pair.workers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.pair.messenger.PairAppClient;

public class BootReceiver extends BroadcastReceiver {
    public BootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PairAppClient.start(context);
        syncContacts(context);
    }

    private void syncContacts(Context context) {
        //attempt to update user's friends at startup
        Intent intent = new Intent(context, UserServices.class);
        intent.putExtra(UserServices.ACTION, UserServices.ACTION_FETCH_FRIENDS);
        PendingIntent operation = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager manager = ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
        long now = 1;
        manager.setRepeating(AlarmManager.ELAPSED_REALTIME, now, AlarmManager.INTERVAL_HOUR, operation); //start now
    }
}
