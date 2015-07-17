package com.pair.messenger;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.pair.data.Message;
import com.pair.util.Config;

/**
 * @author Null-Pointer on 6/14/2015.
 */
class NotificationManager {
    private final static int MESSAGE_NOTIFICATION_ID = 1001;
    private final static int MESSAGE_PENDING_INTENT_REQUEST_CODE = 1002;
    public static final NotificationManager INSTANCE = new NotificationManager();
    private static final String TAG = NotificationManager.class.getSimpleName();

    void onNewMessage(Message message, Intent action) {
        if (Config.isChatRoomOpen()) {
            // TODO: 6/14/2015 give title and description of notification based on type of message

            //TODO use a snackbar style notification, for now we show a toast
            Toast.makeText(Config.getApplicationContext(), message.getFrom() + " : " + message.getMessageBody(), Toast.LENGTH_LONG).show();
        } else {
            PendingIntent pendingIntent = PendingIntent.getActivity(Config.getApplicationContext(),
                    MESSAGE_PENDING_INTENT_REQUEST_CODE,
                    action,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            STATUS_BAR_NOTIFIER.notifyUser("New message from " + message.getFrom(),
                    message.getMessageBody(),
                    message.getMessageBody(),
                    pendingIntent);
        }
    }

    public interface Notifier {
        @SuppressWarnings("unused")
        void notifyUser(String title, String tickerText, String message, PendingIntent intent);
    }

    private final Notifier STATUS_BAR_NOTIFIER = new Notifier() {
        @Override
        public void notifyUser(String title, String tickerText, String message, PendingIntent intent) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(Config.getApplication());
            builder.setTicker(tickerText)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setLights(Color.GREEN, 1500, 3000)
                    .setContentIntent(intent);
            Notification notification = builder.build();
            android.app.NotificationManager notMgr = ((android.app.NotificationManager) Config.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE));
            notMgr.notify(MESSAGE_NOTIFICATION_ID, notification);
            playTone();
        }
    };

    private void playTone() {
        // TODO: 6/14/2015 fetch correct tone from preferences
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Ringtone ringtone = RingtoneManager.getRingtone(Config.getApplicationContext(), uri);
        if (ringtone != null) {
            ringtone.play();
        } else {
            Log.d(TAG, "unable to play ringtone");
            // TODO: 6/15/2015 fallback to default tone for app if available
        }
    }

}
