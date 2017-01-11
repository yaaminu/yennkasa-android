package com.yennkasa.messenger;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.TextUtils;

import com.yennkasa.R;
import com.yennkasa.data.Message;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.data.util.MessageUtils;
import com.yennkasa.ui.ChatActivity;
import com.yennkasa.ui.MainActivity;
import com.yennkasa.util.Config;
import com.yennkasa.util.LiveCenter;
import com.yennkasa.util.MediaUtils;
import com.yennkasa.util.PLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;

/**
 * @author Null-Pointer on 8/12/2015.
 */
class StatusBarNotifier {
    private final static int MESSAGE_NOTIFICATION_ID = 19928;
    private final static int MESSAGE_PENDING_INTENT_REQUEST_CODE = 1002;
    private static final String TAG = StatusBarNotifier.class.getSimpleName();
    public static final int VIBRATION_DURATION = 250;
    private AtomicBoolean shouldPlayTone = new AtomicBoolean(true);
    Timer timer = new Timer();

    void notifyUser(Context context, Message message, String sender) {
        Realm userRealm = User.Realm(context);
        try {
            final String notificationMessage = formatNotificationMessage(message, sender);
            if (notificationMessage == null) {
                return;
            }
            Intent action;
            if (LiveCenter.getTotalUnreadMessages() > 1) {
                action = new Intent(context, MainActivity.class);
            } else {
                // TODO: 1/11/17 use backstack to push user to chat screen directly
                action = new Intent(context, MainActivity.class);
                action.putExtra(ChatActivity.EXTRA_PEER_ID, Message.isGroupMessage(userRealm, message) ? message.getTo() : message.getFrom());
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(Config.getApplicationContext(),
                    MESSAGE_PENDING_INTENT_REQUEST_CODE,
                    action,
                    PendingIntent.FLAG_CANCEL_CURRENT);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(Config.getApplication());
            builder.setTicker(notificationMessage)
                    .setContentTitle(context.getString(R.string.new_message))
                    .setAutoCancel(true);

            builder.setContentText(notificationMessage);
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(notificationMessage));
            builder.setSmallIcon(R.drawable.ic_stat_icon)
                    .setContentIntent(pendingIntent);
            if (UserManager.getInstance().getBoolPref(UserManager.LIGHTS, false)) {
                builder.setLights(Color.GREEN, 1500, 3000);
            }
            Notification notification = builder.build();
            doNotify(context, userRealm, notification, message);
        } finally {
            userRealm.close();
        }
    }

    private void doNotify(Context context, Realm userRealm, Notification notification, Message message) {
        String senderId = Message.isGroupMessage(userRealm, message) ? message.getTo() : message.getFrom();
        NotificationManagerCompat.from(context).notify(MESSAGE_NOTIFICATION_ID, notification);
        if (shouldPlayTone.getAndSet(false)) {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (!shouldPlayTone.get())
                        shouldPlayTone.set(true);
                }
            }, 7000);
            onNotified(context, senderId);
        }

    }

    void onNotified(Context context, String senderId) {
        if (UserManager.getInstance().isMuted(senderId)) {
            PLog.d(TAG, " %s is muted not playing tone", senderId);
            return;
        }
        vibrateIfAllowed(context);
        playToneIfAllowed(context);

    }

    private static void playToneIfAllowed(Context context) {

        String uriString = UserManager.getInstance().getStringPref(UserManager.NEW_MESSAGE_TONE, UserManager.SILENT);
        if (uriString.equals(UserManager.SILENT)) {
            PLog.d(TAG, "silent, aborting ringtone playing");
            return;
        }
        Uri uri;
        if (TextUtils.isEmpty(uriString) || uriString.equals(UserManager.DEFAULT)) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (uri == null) {
                PLog.e(TAG, " unable to play default notification tone");
                return;
            }
        } else {
            uri = Uri.parse(uriString);
        }
        PLog.d(TAG, "Retrieved ringtone %s", uri + "");
        MediaUtils.playTone(context, uri);
    }

    public void vibrateIfAllowed(final Context context) {
        AudioManager manager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        int ringerMode = manager.getRingerMode();
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE || ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            if (UserManager.getInstance().getBoolPref(UserManager.VIBRATE, false)) {
                doVibrate(context);
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        doVibrate(context);
                    }
                }, 500);
            }
        }
    }

    private static void doVibrate(Context context) {
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

    private String formatNotificationMessage(Message message, String sender) {
        String text;
        final Context applicationContext = Config.getApplicationContext();
        List<String> recentChatList = new ArrayList<>(LiveCenter.getAllPeersWithUnreadMessages());
        Realm realm = User.Realm(applicationContext);
        for (int i = 0; i < recentChatList.size(); i++) {
            if (i > 3) {
                break;
            }
            User user = UserManager.getInstance().fetchUserIfRequired(realm, recentChatList.get(i));
            recentChatList.set(i, user.getName());
        }
        realm.close();
        final int recentCount = recentChatList.size(), unReadMessages = LiveCenter.getTotalUnreadMessages();
        if (unReadMessages < 1) {
            return null;
        }
        switch (recentCount) {
            case 0:
                if (com.yennkasa.BuildConfig.DEBUG) throw new AssertionError();
                return getString(R.string.new_message);
            case 1:
                if (unReadMessages == 1) {
                    String messageBody = Message.isTextMessage(message) ? message.getMessageBody() : MessageUtils.typeToString(applicationContext, message);
                    text = sender + ":  " + messageBody;
                } else {
                    text = unReadMessages + " " + getString(unReadMessages > 1 ? R.string.new_message_from : R.string.message_from_2) + " " + sender;
                }
                break;
            case 2:
                text = unReadMessages
                        + " " +
                        getString(unReadMessages > 1 ? R.string.new_message_from : R.string.message_from_2) + " "
                        + recentChatList.get(0) + getString(R.string.and) + recentChatList.get(1);
                break;
            case 3:
                text = unReadMessages + "  " + getString(unReadMessages > 1 ? R.string.new_message_from : R.string.message_from_2) + " " + recentChatList.get(0) + ", " + recentChatList.get(1) + getString(R.string.and) + recentChatList.get(2);
                break;
            default:
                text = "" + recentCount + " " + getString(unReadMessages > 1 ? R.string.new_message_from : R.string.message_from_2) + " " + recentChatList.get(0) + getString(R.string.and) + (recentCount - 1) + getString(R.string.others);
                break; //redundant but safe
        }
        return text;
    }

    private String getString(@StringRes int resId) {
        if (resId == 0) throw new IllegalArgumentException("invalid resource id");
        return Config.getApplicationContext().getString(resId);
    }

    public void clearNotifications() {
        NotificationManagerCompat.from(Config.getApplicationContext()).cancel(MESSAGE_NOTIFICATION_ID);
    }
}
