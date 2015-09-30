package com.pair.messenger;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import com.pair.data.Message;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.ui.ChatActivity;
import com.pair.util.Config;
import com.pair.util.PLog;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Null-Pointer on 8/12/2015.
 */
public class StatusBarNotifier implements Notifier {
    private final static int MESSAGE_NOTIFICATION_ID = 1001;
    private final static int MESSAGE_PENDING_INTENT_REQUEST_CODE = 1002;
    private static final String TAG = StatusBarNotifier.class.getSimpleName();
    public static final int VIBRATION_DURATION = 2000;
    private AtomicBoolean shouldPlayTone = new AtomicBoolean(true);
    Timer timer = new Timer();

    @Override
    public void notifyUser(Context context, Message message, String sender) {
        Intent action = new Intent(context, ChatActivity.class);
        action.putExtra(ChatActivity.EXTRA_PEER_ID, message.getFrom());

        PendingIntent pendingIntent = PendingIntent.getActivity(Config.getApplicationContext(),
                MESSAGE_PENDING_INTENT_REQUEST_CODE,
                action,
                PendingIntent.FLAG_CANCEL_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(Config.getApplication());
        builder.setTicker(context.getString(R.string.new_message))
                .setContentTitle(String.format(context.getString(R.string.message_from), sender));

        if (Message.isTextMessage(message)) {
            builder.setContentText(message.getMessageBody());
        } else {
            builder.setContentText(NotificationManager.messageTypeToString(message.getType()));
        }

        builder.setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentIntent(pendingIntent);
        if (UserManager.getInstance().getBoolPref(UserManager.LIGHTS, false)) {
            builder.setLights(Color.GREEN, 1500, 3000);
        }
        Notification notification = builder.build();
        doNotify(context, notification);
    }

    private void doNotify(Context context, Notification notification) {
        android.app.NotificationManager notMgr = ((android.app.NotificationManager) Config.getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE));
        notMgr.notify(MESSAGE_NOTIFICATION_ID, notification);
        if (shouldPlayTone.getAndSet(false)) {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (!shouldPlayTone.get())
                        shouldPlayTone.set(true);
                }
            }, 15000);
            onNotified(context);
        }

    }

    static void onNotified(Context context) {
        vibrateIfAllowed(context);
        playToneIfAllowed(context);

    }

    private static void playToneIfAllowed(Context context) {
        String uriString = UserManager.getInstance().getStringPref(UserManager.NEW_MESSAGE_TONE, "");
        Uri uri;
        if (TextUtils.isEmpty(uriString) || uriString.equals(UserManager.DEFAULT)) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (uri == null) {
                PLog.d(TAG, " unable to play notification tone");
            }
        } else {
            uri = Uri.parse(uriString);
        }
        PLog.d(TAG, "Retrieved ringtone %s", uri + "");
        Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
        if (ringtone != null) {
            ringtone.play();
        } else {
            PLog.d(TAG, "unable to play ringtone");
        }
    }

    private static void vibrateIfAllowed(Context context) {
        if(UserManager.getInstance().getBoolPref(UserManager.VIBRATE,false)) {
            PLog.v(TAG,"vibrating....");
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                if (vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        AudioAttributes audioAttributes = new AudioAttributes.Builder().setFlags(AudioAttributes.USAGE_NOTIFICATION).build();
                        vibrator.vibrate(VIBRATION_DURATION, audioAttributes);
                    } else {
                        vibrator.vibrate(VIBRATION_DURATION);
                    }
                }
            } else {
                vibrator.vibrate(VIBRATION_DURATION);
            }
        }
    }

    @Override
    public location where() {
        return location.BACKGROUND;
    }
}
