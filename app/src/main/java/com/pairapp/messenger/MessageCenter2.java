package com.pairapp.messenger;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.pairapp.util.PLog;
import com.pairapp.util.UiHelpers;

import org.json.JSONObject;

/**
 * @author by aminu on 11/8/2016.
 */

public class MessageCenter2 extends FirebaseMessagingService {
    private static final String TAG = "MessageCenter2";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        PLog.d(TAG, "push message received %s", remoteMessage);
        PLog.d(TAG, remoteMessage.getFrom());
        PLog.d(TAG, remoteMessage.getMessageId());
        PLog.d(TAG, remoteMessage.getMessageType());
        PLog.d(TAG, remoteMessage.getTo());
        PLog.d(TAG, "" + remoteMessage.getTtl());
        PLog.d(TAG, new JSONObject(remoteMessage.getData()).toString());
    }
}
