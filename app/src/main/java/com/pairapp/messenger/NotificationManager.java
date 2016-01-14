package com.pairapp.messenger;

import android.content.Context;

import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.util.Config;
import com.pairapp.util.PLog;
import com.pairapp.util.TaskManager;
import com.pairapp.util.ThreadUtils;

import java.lang.ref.WeakReference;
import java.util.List;

import io.realm.Realm;
import io.realm.Sort;


/**
 * @author Null-Pointer on 6/14/2015.
 */
//should this class be public?
public final class NotificationManager {
    static final NotificationManager INSTANCE = new NotificationManager();
    private static final String TAG = NotificationManager.class.getSimpleName();
    private final Notifier BACKGROUND_NOTIFIER = new StatusBarNotifier();
    private volatile WeakReference<Notifier> UI_NOTIFIER;

    void onNewMessage(final Context context, final Message message) {
        ThreadUtils.ensureNotMain();
        notifyUser(context, message, retrieveSendersName(message));
    }

    private void notifyUser(final Context context, final Message message, final String sendersName) {

        if (UserManager.getInstance().isMuted(Message.isGroupMessage(message) ? message.getTo() : message.getFrom())) {
            PLog.d(TAG, "user muted not notifying");
            return;
        }
        if (Config.isAppOpen() && UserManager.getInstance().getBoolPref(UserManager.IN_APP_NOTIFICATIONS, false)) {
            //Toast.makeText(Config.getApplicationContext(), message.getFrom() + " : " + message.getMessageBody(), Toast.LENGTH_LONG).show();
            if (UI_NOTIFIER != null) {
                final Notifier notifier = UI_NOTIFIER.get();
                if (notifier != null) {
                    TaskManager.executeOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            notifier.notifyUser(context, message, sendersName);
                        }
                    });
                    return;
                }
            }
        }

        if (Config.getCurrentActivePeer().equals(Message.isGroupMessage(message) ? message.getTo() : message.getFrom())) {
            return;
        }
        BACKGROUND_NOTIFIER.notifyUser(context, message, sendersName);
    }


    private String retrieveSendersName(Message message) {
        ThreadUtils.ensureNotMain();
        final Realm realm = User.Realm(Config.getApplicationContext());
        String sendersName;
        try {
            User user = realm.
                    where(User.class).equalTo(User.FIELD_ID, message.getFrom())
                    .findFirst();

            if (user == null) {
                user = UserManager.getInstance().fetchUserIfRequired(
                        Message.isGroupMessage(message)
                                ? message.getTo()
                                : message.getFrom());
                sendersName = user.getName();
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

    void clearAllMessageNotifications() {
        BACKGROUND_NOTIFIER.clearNotifications();
    }

    void reNotifyForReceivedMessages() {
        Context con = Config.getApplicationContext();
        Realm realm = Message.REALM(con);
        List<Message> messages = realm.where(Message.class)
                .notEqualTo(Message.FIELD_FROM, UserManager.getMainUserId())
                .equalTo(Message.FIELD_STATE, Message.STATE_RECEIVED).findAllSorted(Message.FIELD_DATE_COMPOSED, Sort.DESCENDING);
        if (!messages.isEmpty()) {
            Message message = messages.get(messages.size() - 1);
            onNewMessage(con, message);
        }
    }
}
