package com.pairapp.messenger;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.util.Pair;
import android.util.Log;

import com.pairapp.Errors.ErrorCenter;
import com.pairapp.R;
import com.pairapp.call.CallController;
import com.pairapp.call.CallData;
import com.pairapp.data.CallBody;
import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.net.sockets.MessageParser;
import com.pairapp.net.sockets.Sendable;
import com.pairapp.net.sockets.Sender;
import com.pairapp.ui.MainActivity;
import com.pairapp.ui.VideoCallActivity;
import com.pairapp.ui.VoiceCallActivity;
import com.pairapp.util.Config;
import com.pairapp.util.Event;
import com.pairapp.util.PLog;
import com.pairapp.util.ThreadUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static com.pairapp.messenger.MessengerBus.GET_STATUS_MANAGER;
import static com.pairapp.messenger.MessengerBus.ON_CALL_EVENT;
import static com.pairapp.messenger.PairAppClient.listenableBus;

/**
 * @author aminu on 7/15/2016.
 */
@SuppressWarnings("WeakerAccess")
class PairAppClientInterface {

    public static final String READ_RECEIPT_DELIVERY_REPORT_COLLAPSE_KEY = "readReceiptDeliveryReport";
    public static final int WAIT_MILLIS_DELIVERY_REPORT = 0;
    public static final String TAG = PairAppClientInterface.class.getSimpleName();
    private final MessageParser parser;

    private Set<Activity> backStack = new HashSet<>(6);
    private final Context context;
    private final Sender sender;
    private final StatusManager statusManager;
    private final MessagePacker messagePacker;
    private final Handler handler;
    private final CallController callController;

    PairAppClientInterface(Context context, CallController callController, Sender sender, MessagePacker messagePacker,
                           StatusManager statusManager, Handler handler, MessageParser parser) {
        this.context = context;
        this.callController = callController;
        this.sender = sender;
        this.statusManager = statusManager;
        this.messagePacker = messagePacker;
        this.handler = handler;
        this.parser = parser;
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
            if (message != null && !Message.isGroupMessage(message) &&
                    message.getType() != Message.TYPE_CALL &&
                    message.getType() != Message.TYPE_DATE_MESSAGE &&
                    message.getType() != Message.TYPE_TYPING_MESSAGE) {
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
            if (message != null &&
                    message.getType() != Message.TYPE_CALL &&
                    message.getType() != Message.TYPE_DATE_MESSAGE &&
                    message.getType() != Message.TYPE_TYPING_MESSAGE) {
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

        if (message != null && !Message.isGroupMessage(message) &&
                message.getType() != Message.TYPE_CALL &&
                message.getType() != Message.TYPE_DATE_MESSAGE &&
                message.getType() != Message.TYPE_TYPING_MESSAGE) {
            sender.sendMessage(createReadReceiptSendable(message.getFrom(),
                    messagePacker.createMsgStatusMessage(message.getFrom(), message.getId(), true), System.currentTimeMillis() + WAIT_MILLIS_DELIVERY_REPORT));
        }
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

    void callUser(String userId, int type) {
        // TODO: 7/17/2016 check if user has internet connection,the call client is connected and the pairapp socket is also connected.
        PLog.d(TAG, "calling between user %s", userId);
        CallData callData = callController.callUser(userId, type);
        if (callData != null) {
            context.startActivity(getCallIntent(callData));
        }
        //if there's an error it will be handled in handleCallControllerError(Exception e) below
    }

    @NonNull
    private Intent getCallIntent(CallData callData) {
        Intent intent;
        if (callData.getCallType() == CallController.CALL_TYPE_VOICE) {
            intent = new Intent(context, VoiceCallActivity.class);
            intent.putExtra(VoiceCallActivity.EXTRA_CALL_DATA, callData);
        } else if (callData.getCallType() == CallController.CALL_TYPE_VIDEO) {
            intent = new Intent(context, VideoCallActivity.class);
            intent.putExtra(VideoCallActivity.EXTRA_CALL_DATA, callData);
        } else {
            throw new AssertionError();
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    void answerCall(CallData data) {
        PLog.d(TAG, "answering call between user and %s", data.getPeer());
        callController.answer(data);
    }

    void hangUpCall(CallData callData) {
        callController.hangUp(callData);
    }

    void onCallProgressing(@NonNull CallData data) {
        PLog.d(TAG, "call between user and %s progressing", data.getPeer());
        listenableBus().postSticky(Event.createSticky(ON_CALL_EVENT, null, data));
    }

    void onCallEstablished(@NonNull CallData data) {
        PLog.d(TAG, "call between user and %s established", data.getPeer());
        listenableBus().postSticky(Event.createSticky(ON_CALL_EVENT, null, data));
    }

    void onCallEnded(@NonNull CallData data) {
        listenableBus().postSticky(Event.createSticky(ON_CALL_EVENT, null, data));

        NotificationManagerCompat.from(Config.getApplication())
                .cancel(data.getPeer(), CallController.CALL_NOTIFICATION_ID);
        Realm realm = Message.REALM();
        long count;
        try {
            RealmResults<Message> messages = realm.where(Message.class).equalTo(Message.FIELD_TYPE, Message.TYPE_CALL)
                    .equalTo(Message.FIELD_STATE, Message.STATE_RECEIVED)
                    .lessThanOrEqualTo(Message.FIELD_CALL_BODY + "." + CallBody.FIELD_CALL_DURATION, 0)
                    .findAllSorted(Message.FIELD_DATE_COMPOSED, Sort.DESCENDING);

            if (messages.size() > 0) {
                String recipientsSummary = generateString(messages.distinct(Message.FIELD_FROM));
                String contentText = context.getString(R.string.missed_call_notification, messages.size(), recipientsSummary);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
                builder.setContentTitle(context.getString(R.string.pairapp_call));
                builder.setContentText(contentText);
                builder.setStyle(new NotificationCompat.BigTextStyle().bigText(contentText));
                builder.setSmallIcon(R.drawable.ic_stat_icon);
                builder.setAutoCancel(true);
                builder.setTicker(contentText);
                Intent intent = new Intent(context, MainActivity.class);
                builder.setContentIntent(PendingIntent.getActivity(context, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT));
                NotificationManagerCompat.from(context).notify(CallController.MISSED_CALL_NOTIFICATION_ID, builder.build());
            }
        } finally {
            realm.close();
        }
        PLog.d(TAG, "call between user and %s ended", data.getPeer());
    }

    void onInComingCall(@NonNull CallData data) {
        PLog.d(TAG, "incoming call from %s", data.getPeer());
        context.startActivity(getCallIntent(data));
    }

    public void handleCallControllerError(Exception error) {
        // TODO: 7/16/2016 show a notification
        PLog.d(TAG, "error from call manager, %s", error.getMessage());
        ErrorCenter.reportError(ON_CALL_EVENT, error.getMessage());
    }

    public void enableLoudSpeaker(CallData data) {
        PLog.d(TAG, "load speaker action");
        callController.enableSpeaker(data);
    }

    public void muteCall(CallData data) {
        PLog.d(TAG, "mute call");
        callController.muteCall(data);
    }

    public void onCallMuted(CallData data) {
        listenableBus().postSticky(Event.createSticky(ON_CALL_EVENT, null, data));
    }

    public void onLoudSpeaker(CallData data) {
        listenableBus().postSticky(Event.createSticky(ON_CALL_EVENT, null, data));
    }

    public void clearNotifications(long date) {
        NotificationManager.INSTANCE.clearAllMessageNotifications();
        NotificationManagerCompat.from(context).cancel(CallController.MISSED_CALL_NOTIFICATION_ID);
    }

    @NonNull
    String generateString(List<Message> messages) {
        UserManager manager = UserManager.getInstance();
        StringBuilder builder = new StringBuilder(messages.size() * 7);
        Realm realm = User.Realm(context);
        try {
            if (messages.size() > 2) {
                int extra = messages.size() - 1;
                builder.append(manager.fetchUserIfRequired(realm, messages.get(0).getFrom()).getName())
                        .append(context.getString(R.string.and))
                        .append(extra) // TODO: 7/26/2016 prepare this for other locales
                        .append(context.getString(R.string.other));
            } else if (messages.size() > 1) {
                builder.append(manager.fetchUserIfRequired(realm, messages.get(0).getFrom()).getName())
                        .append(context.getString(R.string.and))
                        .append(manager.fetchUserIfRequired(realm, messages.get(1).getFrom()).getName());
            } else if (messages.size() > 0) {
                builder.append(manager.fetchUserIfRequired(realm, messages.get(0).getFrom()).getName());
            }
        } finally {
            realm.close();
        }
        final String generated = builder.toString();
        PLog.d(TAG, "generated string is %s", generated);
        return generated;
    }

    public void onIncomingPushMessage(String dataBase64) {
        parser.feedBase64(dataBase64);
    }

    public void onRouteCallViaPush(Pair<String, String> data) {
        byte[] packed = messagePacker.packCallMessage(data.first, data.second);
        Sendable sendable = new Sendable.Builder()
                .collapseKey("call:" + data.first)
                .data(sender.bytesToString(packed))
                .maxRetries(3)
                .surviveRestarts(false)
                .validUntil(System.currentTimeMillis() + 15000).build();
        sender.sendMessage(sendable);
    }

    public void onInComingCallPushPayload(String payload) {
        callController.handleCallPushPayload(payload);
    }
}
