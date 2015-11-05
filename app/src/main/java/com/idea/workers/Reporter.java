package com.idea.workers;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.idea.pairapp.R;

public class Reporter extends BroadcastReceiver {
    public Reporter() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String message = intent.getStringExtra("message");
        String id = intent.getStringExtra("id");
        Intent action = intent.getParcelableExtra("action");
        Notification notification = new NotificationCompat.Builder(context)
                .setContentTitle(context.getString(R.string.error))
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context, id.hashCode(), action, PendingIntent.FLAG_UPDATE_CURRENT))
                .setSmallIcon(R.drawable.ic_launcher).build();
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);// getSystemService(NOTIFICATION_SERVICE));
        manager.notify(100000111+id.hashCode(), notification);
    }
}
