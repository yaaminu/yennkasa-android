package com.idea.messenger;

import android.content.Context;
import android.content.Intent;

import com.github.nkzawa.emitter.Emitter;
import com.idea.data.Message;
import com.idea.data.UserManager;
import com.idea.net.sockets.SocketIoClient;
import com.idea.util.Config;
import com.idea.util.LiveCenter;
import com.idea.util.PLog;
import com.idea.util.TaskManager;
import com.idea.util.ThreadUtils;
import com.parse.ParseCloud;
import com.parse.ParsePushBroadcastReceiver;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import io.realm.Realm;

import static com.idea.messenger.ParseMessageProvider.FROM;
import static com.idea.messenger.ParseMessageProvider.IS_GROUP_MESSAGE;
import static com.idea.messenger.ParseMessageProvider.MESSAGE;
import static com.idea.messenger.ParseMessageProvider.TO;


/**
 * @author by Null-Pointer on 5/28/2015.
 */
public class MessageCenter extends ParsePushBroadcastReceiver {
    static final String KEY_MESSAGE = "message";
    private static final String TAG = MessageCenter.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final String EXTRA_MESSAGE = "message";
    @SuppressWarnings("unused")
    private static final String EXTRA_NEW_USER = "user";
    @SuppressWarnings("unused")
    private static final String EXTRA_TYPE = Message.FIELD_TYPE;
    private static final Emitter.Listener MESSAGE_STATUS_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            PLog.i(TAG, "message status report: " + args[0].toString());
            try {
                JSONObject object = new JSONObject(args[0].toString());
                int status = object.getInt(SocketIoClient.MSG_STS_STATUS);
                String messageId = object.getString(SocketIoClient.MSG_STS_MESSAGE_ID);
                updateMessageStatus(messageId, status);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    };

    private static void updateMessageStatus(String messageId, int status) throws JSONException {
        Realm realm = Message.REALM(Config.getApplicationContext());
        Message msg = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
        if (msg != null) {
            realm.beginTransaction();
            if (status == Message.STATE_SEEN) {
                PLog.i(TAG, "message seen");
                msg.setState(Message.STATE_SEEN);
            } else if (status == Message.STATE_RECEIVED && msg.getState() != Message.STATE_SEEN) {
                msg.setState(Message.STATE_RECEIVED);
            } else {
                PLog.d(TAG, "uknonwn message status %s", "" + status);
            }
            realm.commitTransaction();
            realm.close();
        } else {
            PLog.i(TAG, "message not available for update");
        }
    }

    private static final Emitter.Listener MESSAGE_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            PLog.i(TAG, "socket message received: " + args[0].toString());
            //process message
            String data = args[0].toString();
            processMessage(Config.getApplicationContext(), data);
            String sender = Message.fromJSON(data).getFrom();
            if (!LiveCenter.isOnline(sender))
                LiveCenter.trackUser(sender);
        }
    };
    static SocketIoClient messagingClient;

    static void startListeningForSocketMessages() {
        initClient();
    }

    private static void initClient() {
        synchronized (MessageCenter.class) {
            if (messagingClient == null) {
                messagingClient = SocketIoClient.getInstance(Config.getMessageEndpoint(), UserManager.getMainUserId());
                messagingClient.registerForEvent(SocketIoClient.EVENT_MESSAGE, MESSAGE_RECEIVER);
                messagingClient.registerForEvent(SocketIoClient.EVENT_MSG_STATUS, MESSAGE_STATUS_RECEIVER);
            }
        }
    }

    static void stopListeningForSocketMessages() {
        synchronized (MessageCenter.class) {
            if (messagingClient != null) {
                messagingClient.unRegisterEvent(SocketIoClient.EVENT_MESSAGE, MESSAGE_RECEIVER);
                messagingClient.registerForEvent(SocketIoClient.EVENT_MSG_STATUS, MESSAGE_STATUS_RECEIVER);
                messagingClient.close();
                messagingClient = null;
            }
        }
    }

    static synchronized void notifyReceived(final Message message) {
        NotifyMessageStatusJob job = NotifyMessageStatusJob.makeNew(Message.STATE_RECEIVED, message);
        TaskManager.runJob(job);

    }


    private static void processMessage(Context context, String data) {
        Intent intent;
        intent = new Intent(context, MessageProcessor.class);
        intent.putExtra(KEY_MESSAGE, data);
        context.startService(intent);
    }


    static synchronized void notifyMessageSeen(final Message message) {
        NotifyMessageStatusJob job = NotifyMessageStatusJob.makeNew(Message.STATE_SEEN, message);
        TaskManager.runJob(job);
    }

    synchronized static void doNotify(Message message, int state) throws com.parse.ParseException {
        ThreadUtils.ensureNotMain();
        if (Message.isGroupMessage(message))
            return;

        JSONObject obj = new JSONObject();
        try {
            obj.put(SocketIoClient.PROPERTY_TO, message.getFrom());
            obj.put(SocketIoClient.MSG_STS_MESSAGE_ID, message.getId());
            obj.put(SocketIoClient.MSG_STS_STATUS, state);
            obj.put(SocketIoClient.PROPERTY_FROM, message.getTo());
            obj.put(MessageProcessor.MESSAGE_STATUS, "messageStatus");
        } catch (JSONException e) {
            throw new RuntimeException(e.getCause());
        }

        if (LiveCenter.isOnline(message.getFrom())) {
            //use socketsIO
            if (messagingClient == null) {
                initClient();
            }
            messagingClient.send(SocketIoClient.EVENT_MSG_STATUS, obj);
        } else {
            //maybe push
            Map<String, Object> params = new HashMap<>(3);
            params.put(TO, message.getFrom());
            params.put(IS_GROUP_MESSAGE, false);
            params.put(FROM, message.getTo());
            params.put(MESSAGE, obj);
            ParseCloud.callFunction("pushToSyncMessages", params);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PLog.d(TAG, "push recieved");
        String data = intent.getStringExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA);
        PLog.d(TAG, data);
        try {
            JSONObject pushMessage = new JSONObject(data);

            String tmp = pushMessage.optString("message", data);
            if (!tmp.equals(MessageProcessor.SYNC_MESSAGES)) {
                data = tmp;
            }
            PLog.d(TAG, "mainPushMessage: %s", data);
        } catch (JSONException e) {
            throw new RuntimeException(e.getCause());
        }
        processMessage(context, data);
    }

}
