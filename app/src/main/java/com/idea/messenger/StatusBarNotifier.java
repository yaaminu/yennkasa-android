package com.idea.messenger;

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
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import com.idea.PairApp;
import com.idea.ui.ChatActivity;
import com.idea.util.LiveCenter;
import com.idea.data.Message;
import com.idea.data.UserManager;
import com.idea.pairapp.R;
import com.idea.util.Config;
import com.idea.util.PLog;

import java.util.ArrayList;
import java.util.List;
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
                .setContentTitle(context.getString(R.string.new_message));

        builder.setContentText(formatNotificationMessage(message,sender));

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
                PLog.e(TAG, " unable to play default notification tone");
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
        if (UserManager.getInstance().getBoolPref(UserManager.VIBRATE, false)) {
            PLog.v(TAG, "vibrating....");
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

    @NonNull
    private String formatNotificationMessage(Message message, String sender) {
        String text;
        List<String> recentChatList = new ArrayList<>(LiveCenter.getAllPeersWithUnreadMessages());
        final int recentCount = recentChatList.size(), unReadMessages = LiveCenter.getTotalUnreadMessages();
        switch (recentCount) {
            case 0:
                return getString(R.string.new_message);
            case 1:
                String messageBody = Message.isTextMessage(message) ? message.getMessageBody() : PairApp.typeToString(Config.getApplicationContext(), message.getType());
                text = sender + ":  " + messageBody;
                break;
            case 2:
                text = unReadMessages + " " + getString(R.string.new_message_from) + " " + recentChatList.get(0) + getString(R.string.and) + recentChatList.get(1);
                break;
            case 3:
                text = unReadMessages + "  " + getString(R.string.new_message_from) + " " + recentChatList.get(0) + ", " + recentChatList.get(1) + getString(R.string.and) + recentChatList.get(2);
                break;
            default:
                text = "" + recentCount + " " + getString(R.string.new_message_from) + " " + recentChatList.get(0) + getString(R.string.and) + (recentCount - 1) + getString(R.string.others);
                break; //redundant but safe
        }
        return text;
    }

    private String getString(@StringRes int resId) {
        if (resId == 0) throw new IllegalArgumentException("invalid resource id");
        return Config.getApplicationContext().getString(resId);
    }
}
