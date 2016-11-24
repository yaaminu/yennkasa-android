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
        final Realm userRealm = User.Realm(Config.getApplicationContext());
        try {
            notifyUser(context, userRealm, message, retrieveSendersName(userRealm, message));
        } finally {
            userRealm.close();
        }
    }

    private void notifyUser(final Context context, Realm userRealm, final Message message, final String sendersName) {

        if (UserManager.getInstance().isMuted(Message.isGroupMessage(userRealm, message) ? message.getTo() : message.getFrom())) {
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
        if (Config.getCurrentActivePeer().equals(Message.isGroupMessage(userRealm, message) ? message.getTo() : message.getFrom())) {
            return;
        }
        statusBarNotifier.notifyUser(context, message, sendersName);
    }


    private String retrieveSendersName(Realm userRealm, Message message) {
        ThreadUtils.ensureNotMain();
        String sendersName;
        sendersName = UserManager.getInstance().fetchUserIfRequired(userRealm, message.getFrom()).getName();
        if (Message.isGroupMessage(userRealm, message)) {
            sendersName = sendersName + "@" + UserManager.getInstance().fetchUserIfRequired(userRealm,
                    message.getTo()).getName();
        }
        return sendersName;
    }

    void clearAllMessageNotifications() {
        ThreadUtils.ensureNotMain();
        statusBarNotifier.clearNotifications();
    }

    void reNotifyForReceivedMessages(Context con, String currentUserId) {
        ThreadUtils.ensureNotMain();
        Realm realm = Message.REALM(con);
        List<Message> messages = realm.where(Message.class)
                .notEqualTo(Message.FIELD_FROM, currentUserId)
                .equalTo(Message.FIELD_STATE, Message.STATE_RECEIVED)
                .notEqualTo(Message.FIELD_TYPE, Message.TYPE_CALL)
                .findAllSorted(Message.FIELD_DATE_COMPOSED, Sort.DESCENDING);
        if (!messages.isEmpty()) {
            Message message = messages.get(messages.size() - 1);
            onNewMessage(con, realm.copyFromRealm(message));
        }
        realm.close();
    }
}
