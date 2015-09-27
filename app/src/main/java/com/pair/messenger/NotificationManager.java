package com.pair.messenger;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

import com.pair.data.Message;
import com.pair.data.User;
import com.pair.pairapp.R;
import com.pair.util.CLog;
import com.pair.util.Config;
import com.pair.util.TaskManager;
import com.pair.util.ThreadUtils;

import java.lang.ref.WeakReference;

import io.realm.Realm;


/**
 * @author Null-Pointer on 6/14/2015.
 */
final class NotificationManager {
    static final NotificationManager INSTANCE = new NotificationManager();
    private static final String TAG = NotificationManager.class.getSimpleName();
    private final Notifier BACKGROUND_NOTIFIER = new StatusBarNotifier();
    private volatile WeakReference<Notifier> UI_NOTIFIER;

    static CharSequence messageTypeToString(int type) {
        switch (type) {
            case Message.TYPE_PICTURE_MESSAGE:
                return Config.getApplicationContext().getString(R.string.picture);
            case Message.TYPE_VIDEO_MESSAGE:
                return Config.getApplicationContext().getString(R.string.video);
            case Message.TYPE_BIN_MESSAGE:
                return Config.getApplicationContext().getString(R.string.file);
            default:
                if (com.pair.pairapp.BuildConfig.DEBUG) {
                    throw new AssertionError("Unknown message type");
                }
                return "";
        }
    }

    static void playTone(Context context) {
        // TODO: 6/14/2015 fetch correct tone from preferences
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
        if (ringtone != null) {
            ringtone.play();
        } else {
            CLog.d(TAG, "unable to play ringtone");
            // TODO: 6/15/2015 fallback to default tone for app if available
        }
    }

    void onNewMessage(final Context context, final Message message) {
        if (ThreadUtils.isMainThread()) {
            TaskManager.execute(new Runnable() {
                @Override
                public void run() {
                    notifyUser(context, message, retrieveSendersName(message));
                }
            });
        } else {
            notifyUser(context, message, retrieveSendersName(message));
        }
    }

    private void notifyUser(final Context context, final Message message, final String sendersName) {
        if (Config.isAppOpen()) {
            //Toast.makeText(Config.getApplicationContext(), message.getFrom() + " : " + message.getMessageBody(), Toast.LENGTH_LONG).show();
            if (UI_NOTIFIER != null) {
                TaskManager.executeOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        if (UI_NOTIFIER != null) {
                            Notifier notifier = UI_NOTIFIER.get();
                            if (notifier != null) {
                                notifier.notifyUser(context, message, sendersName);
                            }
                        }
                    }
                });
            } else {
                BACKGROUND_NOTIFIER.notifyUser(context, message, sendersName);
            }
        } else {
            BACKGROUND_NOTIFIER.notifyUser(context, message, sendersName);
        }
    }

    private String retrieveSendersName(Message message) {
        ThreadUtils.ensureNotMain();
        final Realm realm = Realm.getInstance(Config.getApplicationContext());
        String sendersName;
        try {
            User user = realm.
                    where(User.class).equalTo(User.FIELD_ID, message.getFrom())
                    .findFirst();
            if (user == null) {
                //if user is null then retrieve users name from the senders id.
                //we are splitting if the message is from a group the senders id will
                //be in the format: "groupName@adminUserId".

                //if the sender is a normal user then the phoneNumber will be used as a temporary
                // name. the user manager will detect these situations and automatically attempt to
                // retrieve the users name from the peoples contact if its available otherwise this id(which is same as phoneNumber
                // will be used

                sendersName = message.getFrom().split("@")[0];
            } else {
                sendersName = user.getName();
            }
        } finally {
            realm.close();
        }
        return sendersName;
    }

    synchronized void registerUI_Notifier(Notifier notifier) {
        if (notifier == null) throw new IllegalArgumentException("notifier is null");
        if (UI_NOTIFIER != null && UI_NOTIFIER.get() == notifier) {
            return;
        }
        UI_NOTIFIER = new WeakReference<>(notifier);
    }

    synchronized void unRegisterUI_Notifier(Notifier notifier) {
        if (UI_NOTIFIER != null && UI_NOTIFIER.get() == notifier) {
            UI_NOTIFIER.clear();
            UI_NOTIFIER = null;
        }
    }

}
