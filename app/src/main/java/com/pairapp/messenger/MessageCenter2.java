package com.pairapp.messenger;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.pairapp.util.Event;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;

import static com.pairapp.messenger.MessengerBus.*;

/**
 * @author by aminu on 11/8/2016.
 */

public class MessageCenter2 extends FirebaseMessagingService {
    private static final String TAG = "MessageCenter2";
    public static final String PAYLOAD = "payload";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        PLog.d(TAG, "push message received %s", remoteMessage);
        String payload = remoteMessage.getData().get(PAYLOAD);
        GenericUtils.ensureNotEmpty(payload);
        if (!get(PAIRAPP_CLIENT_POSTABLE_BUS)
                .post(Event.create(MESSAGE_PUSH_INCOMING, null, payload))) {
            // TODO: 11/12/2016 Aminu is this message is going to be lost? or should we persist and replay it on next startup?
            PLog.f(TAG, "oh no!!!! no handler available to handle push message.This is very strange");
        }
    }
}
