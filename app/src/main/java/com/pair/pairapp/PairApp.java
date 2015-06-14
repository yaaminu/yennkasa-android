package com.pair.pairapp;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;

import com.pair.messenger.PairAppClient;
import com.pair.util.Config;
import com.pair.workers.UserServices;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class PairApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Config.init(this);
        PairAppClient.start(this);
        syncContacts();
    }

    private void syncContacts() {
        //attempt to update user's friends at startup
        Intent intent = new Intent(this, UserServices.class);
        intent.putExtra(UserServices.ACTION, UserServices.ACTION_FETCH_FRIENDS);
        PendingIntent operation = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager manager = ((AlarmManager) getSystemService(ALARM_SERVICE));
        long now = 1;
        manager.setRepeating(AlarmManager.ELAPSED_REALTIME, now, AlarmManager.INTERVAL_HOUR, operation); //start now
    }
}
