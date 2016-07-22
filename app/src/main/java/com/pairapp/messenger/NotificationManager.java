package com.pairapp.messenger;

import android.content.Context;
import android.support.v4.util.Pair;

import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.util.Config;
import com.pairapp.util.Event;
import com.pairapp.util.PLog;
import com.pairapp.util.ThreadUtils;

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
    private final StatusBarNotifier statusBarNotifier = new StatusBarNotifier();

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
            if (MessengerBus.get(MessengerBus.PAIRAPP_CLIENT_LISTENABLE_BUS)
                    .post(Event.create(MessengerBus.UI_ON_NEW_MESSAGE_RECEIVED, null, Pair.create(message, sendersName)))) {
                return;
            }
        }

        // TODO: 7/22/2016 why is this necessary?
        if (Config.getCurrentActivePeer().equals(Message.isGroupMessage(message) ? message.getTo() : message.getFrom())) {
            return;
        }
        statusBarNotifier.notifyUser(context, message, sendersName);
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

    void clearAllMessageNotifications() {
        statusBarNotifier.clearNotifications();
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
