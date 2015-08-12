package com.pair.messenger;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.util.Log;

import com.pair.data.Message;
import com.pair.data.User;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.Config;

import io.realm.Realm;

/**
 * @author Null-Pointer on 6/14/2015.
 */
final class NotificationManager {
    private static final String TAG = NotificationManager.class.getSimpleName();

    public static final NotificationManager INSTANCE = new NotificationManager();
    private Notifier UI_NOTIFIER;
    private final Notifier BACKGROUND_NOTIFIER = new StatusBarNotifier();


    void onNewMessage(final Context context, final Message message) {
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    message.setFrom(retrieveSendersName(message));
                    notifyUser(context, message);
                    return null;
                }
            }.execute();
        } else {
            message.setFrom(retrieveSendersName(message));
            notifyUser(context, message);
        }
    }

    private void notifyUser(Context context, final Message message) {
        if (Config.isChatRoomOpen()) {
            // TODO: 6/14/2015 give title and description of notification based on type of message

            //TODO use a snackbar style notification, for now we show a toast
            //Toast.makeText(Config.getApplicationContext(), message.getFrom() + " : " + message.getMessageBody(), Toast.LENGTH_LONG).show();
            if (UI_NOTIFIER != null) {
                android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        UI_NOTIFIER.notifyUser(null, message); //the ui notifier is expected to have access to context
                    }
                });
            } else {
                BACKGROUND_NOTIFIER.notifyUser(context, message);
            }
        } else {
            BACKGROUND_NOTIFIER.notifyUser(context, message);
        }
    }

    private String retrieveSendersName(Message message) {
        if (BuildConfig.DEBUG && Looper.getMainLooper().getThread() == Thread.currentThread()) {
            throw new IllegalStateException("this method should run in the background");
        }
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

    void registerUI_Notifier(Notifier notifier) {
        if (notifier == null) throw new IllegalArgumentException("notifier is null");
        UI_NOTIFIER = notifier;
    }

    void unRegisterUI_Notifier(Notifier notifier) {
        UI_NOTIFIER = null;
    }

    static CharSequence messageTypeToString(int type) {
        switch (type) {
            case Message.TYPE_PICTURE_MESSAGE:
                return "Image";
            case Message.TYPE_VIDEO_MESSAGE:
                return "Video";
            case Message.TYPE_BIN_MESSAGE:
                return "File";
            default:
                throw new AssertionError("Unknown message type");
        }
    }

    static void playTone(Context context) {
        // TODO: 6/14/2015 fetch correct tone from preferences
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
        if (ringtone != null) {
            ringtone.play();
        } else {
            Log.d(TAG, "unable to play ringtone");
            // TODO: 6/15/2015 fallback to default tone for app if available
        }
    }

}
