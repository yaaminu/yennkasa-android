package com.pairapp.messenger;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.pairapp.call.CallController;
import com.pairapp.call.CallData;
import com.pairapp.data.Message;
import com.pairapp.net.sockets.Sendable;
import com.pairapp.net.sockets.Sender;
import com.pairapp.util.Config;
import com.pairapp.util.Event;
import com.pairapp.util.PLog;
import com.pairapp.util.ThreadUtils;

import java.util.HashSet;
import java.util.Set;

import io.realm.Realm;

import static com.pairapp.messenger.MessengerBus.GET_STATUS_MANAGER;
import static com.pairapp.messenger.PairAppClient.listenableBus;

/**
 * @author aminu on 7/15/2016.
 */
class PairAppClientInterface {

    public static final String READ_RECEIPT_DELIVERY_REPORT_COLLAPSE_KEY = "readReceiptDeliveryReport";
    public static final int WAIT_MILLIS_DELIVERY_REPORT = 0;

    private Set<Activity> backStack = new HashSet<>(6);
    private final Context context;
    private final Sender sender;
    private final StatusManager statusManager;
    private final MessagePacker messagePacker;
    private final Handler handler;
    private final CallController callController;

    PairAppClientInterface(Context context, CallController callController, Sender sender, MessagePacker messagePacker,
                           StatusManager statusManager, Handler handler) {
        this.context = context;
        this.callController = callController;
        this.sender = sender;
        this.statusManager = statusManager;
        this.messagePacker = messagePacker;
        this.handler = handler;
    }

    void sendMessage(Message message) {
        android.os.Message msg = android.os.Message.obtain();
        msg.obj = Message.copy(message);
        msg.what = PairAppClient.MessageHandler.SEND_MESSAGE;
        handler.sendMessage(msg);
    }

    void cancelDisPatch(Message message) {
        if (!Message.isOutGoing(message)) {
            throw new IllegalArgumentException("only outgoing messages may be cancelled!");
        }
        android.os.Message msg = android.os.Message.obtain();
        msg.obj = Message.copy(message);
        msg.what = PairAppClient.MessageHandler.CANCEL_DISPATCH;
        handler.sendMessage(msg);
    }

    void registerUINotifier(final Notifier notifier) {
        NotificationManager.INSTANCE.registerUI_Notifier(notifier);
    }

    void unRegisterUINotifier(Notifier notifier) {
        NotificationManager.INSTANCE.unRegisterUI_Notifier(notifier);
    }

    void markUserAsOffline(Activity activity) {
        ThreadUtils.ensureMain();
        PairAppClient.ensureUserLoggedIn();
        if (activity == null) {
            throw new IllegalArgumentException();
        }
        backStack.remove(activity);
        if (backStack.isEmpty()) {
            PLog.d(PairAppClient.TAG, "marking user as offline");
            statusManager.announceStatusChange(false);
        }
    }

    void markUserAsOnline(Activity activity) {
        ThreadUtils.ensureMain();
        PairAppClient.ensureUserLoggedIn();
        if (activity == null) {
            throw new IllegalArgumentException();
        }
        if (backStack.isEmpty()) {
            Log.d(PairAppClient.TAG, "marking user as online");
            statusManager.announceStatusChange(true);
        }
        backStack.add(activity);
    }

    void notifyPeerTyping(final String user) {
        statusManager.announceStartTyping(user);
    }

    void notifyPeerNotTyping(final String user) {
        statusManager.announceStopTyping(user);
    }

    void startMonitoringUser(final String user) {
        statusManager.startMonitoringUser(user);
    }

    void stopMonitoringUser(String user) {
        statusManager.stopMonitoringUser(user);
    }


    private Sendable createReadReceiptSendable(String recipient, byte[] data, long startProcessingAt) {
        return new Sendable.Builder()
                .collapseKey(recipient + READ_RECEIPT_DELIVERY_REPORT_COLLAPSE_KEY)
                .data(sender.bytesToString(data))
                .startProcessingAt(startProcessingAt)
                .validUntil(System.currentTimeMillis() + AlarmManager.INTERVAL_DAY * 30) //30 days
                .surviveRestarts(true)
                .maxRetries(Sendable.RETRY_FOREVER)
                .build();
    }

    void markMessageSeen(final String msgId) {
        Realm realm = Message.REALM(context);
        try {
            Message message = Message.markMessageSeen(realm, msgId);
            if (message != null) {
                sender.sendMessage(createReadReceiptSendable(message.getFrom(),
                        messagePacker.createMsgStatusMessage(message.getFrom(), msgId, false), System.currentTimeMillis()));
            }
        } finally {
            realm.close();
        }
    }


    void markMessageDelivered(final String msgId) {
        Realm realm = Message.REALM(context);
        try {
            Message message = Message.markMessageDelivered(realm, msgId);
            if (message != null) {
                //we don't want to send the delivered when the user is in the chat room.
                //that's too wasteful so wait for few seconds and if user does not scroll to it
                //then continue
                if (message.getFrom().equals(Config.getCurrentActivePeer())) {
                    delayAndNotifyIfNotMarkedAsSeen(msgId);
                } else {
                    notifySenderMessageDelivered(message);
                }
            }
        } finally {
            realm.close();
        }
    }

    private void delayAndNotifyIfNotMarkedAsSeen(final String msgId) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Realm realm = Message.REALM(context);
                try {
                    Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, msgId).findFirst();
                    if (message != null) {
                        if (message.getState() == Message.STATE_SEEN) return;
                        notifySenderMessageDelivered(message);
                    }
                } finally {
                    realm.close();
                }
            }
        }, 2000);
    }

    private void notifySenderMessageDelivered(Message message) {
        sender.sendMessage(createReadReceiptSendable(message.getFrom(),
                messagePacker.createMsgStatusMessage(message.getFrom(), message.getId(), true), System.currentTimeMillis() + WAIT_MILLIS_DELIVERY_REPORT));
    }

    void onMessageDelivered(String msgId) {
        Realm realm = Message.REALM(context);
        try {
            Message.markMessageDelivered(realm, msgId);
        } finally {
            realm.close();
        }
    }

    void onMessageSeen(String msgId) {
        Realm realm = Message.REALM(context);
        try {
            Message.markMessageSeen(realm, msgId);
        } finally {
            realm.close();
        }
    }

    void getStatusManager() {
        if (statusManager == null) {
            throw new AssertionError();
        }
        listenableBus().postSticky(Event.createSticky(GET_STATUS_MANAGER, null, statusManager));
    }

    void voiceCallUser(String userId) {
        // TODO: 7/15/2016 use the returned calldata
        callController.callUser(userId, CallController.CALL_TYPE_VOICE);
    }

    void answerCall(CallData data) {
        callController.answer(data);
    }

    void hangUpCall(CallData callData) {
        callController.hangUp(callData);
    }

    void onCallProgressing(@NonNull CallData data) {
        listenableBus().postSticky(Event.createSticky(MessengerBus.CALL_PROGRESSING, null, data));
    }

    void onCallEstablished(@NonNull CallData data) {
        listenableBus().postSticky(Event.createSticky(MessengerBus.CALL_ESTABLISHED, null, data));
    }

    void onCallEnded(@NonNull CallData data) {
        // TODO: 7/15/2016 log the call
        listenableBus().postSticky(Event.createSticky(MessengerBus.CALL_ENDED, null, data));
    }

    void onInComingCall(@NonNull CallData data) {
        // TODO: 7/15/2016 start incomingCallActivity
    }
}
