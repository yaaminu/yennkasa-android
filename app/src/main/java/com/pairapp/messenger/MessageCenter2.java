package com.pairapp.messenger;

import android.content.SharedPreferences;
import android.os.Debug;
import android.provider.Settings;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.pairapp.BuildConfig;
import com.pairapp.data.UnproccessedMessage;
import com.pairapp.net.sockets.MessageParser;
import com.pairapp.util.Config;
import com.pairapp.util.Event;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;

import java.util.List;
import java.util.Objects;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static com.pairapp.messenger.MessengerBus.*;

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
        processPayload(payload);
    }

    private static void processPayload(String payload) {
        synchronized (MessageCenter2.class) {
            GenericUtils.ensureNotEmpty(payload);
            if (!get(PAIRAPP_CLIENT_POSTABLE_BUS)
                    .post(Event.create(MESSAGE_PUSH_INCOMING, null, payload))) {
                // TODO: 11/12/2016 Aminu is this message is going to be lost? or should we persist and replay it on next startup?
                PLog.f(TAG, "oh no!!!! no handler available to handle push message.This is very strange");
                PLog.d(TAG, "saving unhandled message until a handler comes up");
            }
        }
    }

    static void replayUnProccessedMessages() {
        if (true) return;
        synchronized (MessageCenter2.class) {
            Realm realm = UnproccessedMessage.REALM();
            try {
                realm.beginTransaction();
                RealmResults<UnproccessedMessage> messages = realm.where(UnproccessedMessage.class)
                        .findAllSorted(UnproccessedMessage.FIELD_DATE_CREATED, Sort.ASCENDING); //process older messages first
                for (UnproccessedMessage message : messages) {
                    if (get(PAIRAPP_CLIENT_POSTABLE_BUS).post(Event.create(MESSAGE_PUSH_INCOMING, null, message.getPayload()))) {
                        message.deleteFromRealm();
                    }
                }
                realm.commitTransaction();
            } finally {
                realm.close();
            }
        }
    }
}
