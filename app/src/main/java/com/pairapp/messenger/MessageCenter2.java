package com.pairapp.messenger;

import android.content.Context;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.pairapp.BuildConfig;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.util.Event;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;

import io.realm.Realm;

import static com.pairapp.messenger.MessengerBus.MESSAGE_PUSH_INCOMING;
import static com.pairapp.messenger.MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS;
import static com.pairapp.messenger.MessengerBus.get;

/**
 * @author by aminu on 11/8/2016.
 */

public class MessageCenter2 extends FirebaseMessagingService {
    private static final String TAG = "MessageCenter2";
    public static final String PAYLOAD = "payload";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String payload = remoteMessage.getData().get(PAYLOAD);
        PLog.d(TAG, "push message received %s", payload);
        Realm userRealm = User.Realm(this);
        try {
            if (UserManager.getInstance().isUserVerified(userRealm)) {
                processPayload(this, payload);
            } else {
                PLog.f(TAG, "user is not verified, but recieved push!!!");
                if (BuildConfig.DEBUG) {
                    throw new RuntimeException("push enabled when user is not verified");
                }
            }
        } finally {
            userRealm.close();
        }
    }

    private static void processPayload(Context context, String payload) {
        synchronized (MessageCenter2.class) {
            GenericUtils.ensureNotEmpty(payload);
            if (!get(PAIRAPP_CLIENT_POSTABLE_BUS)
                    .post(Event.create(MESSAGE_PUSH_INCOMING, null, payload))) {
                // TODO: 11/12/2016 Aminu is this message is going to be lost? or should we persist and replay it on next startup?
                PLog.f(TAG, "oh no!!!! no handler available to handle push message.This is very strange");
                PLog.f(TAG, "starting handler if possible");
                PairAppClient.startIfRequired(context);
                //keep looping till we are able to process this message
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    PLog.e(TAG, e.getMessage(), e);
                }
                processPayload(context, payload);
            }
        }
    }
}
