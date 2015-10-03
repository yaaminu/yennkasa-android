package com.idea.messenger;

import android.content.Context;
import android.content.Intent;

import com.github.nkzawa.emitter.Emitter;
import com.idea.util.LiveCenter;
import com.idea.data.Message;
import com.idea.data.UserManager;
import com.idea.net.sockets.SocketIoClient;
import com.idea.util.Config;
import com.idea.util.L;
import com.idea.util.PLog;
import com.idea.util.TaskManager;
import com.idea.util.ThreadUtils;
import com.parse.ParsePushBroadcastReceiver;

import org.json.JSONException;
import org.json.JSONObject;

import io.realm.Realm;


/**
 * @author by Null-Pointer on 5/28/2015.
 */
public class MessageCenter extends ParsePushBroadcastReceiver {
    static final String KEY_MESSAGE = "message";
    private static final String TAG = MessageCenter.class.getSimpleName();
    private static final String EXTRA_MESSAGE = "message";
    private static final String EXTRA_NEW_USER = "user";
    private static final String EXTRA_TYPE = Message.FIELD_TYPE;
    private static final Emitter.Listener MESSAGE_STATUS_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            PLog.i(TAG, "message status report: " + args[0].toString());
            try {
                JSONObject object = new JSONObject(args[0].toString());
                int status = object.getInt(SocketIoClient.MSG_STS_STATUS);
                String messageId = object.getString(SocketIoClient.MSG_STS_MESSAGE_ID);
                if (status == Message.STATE_SEEN) {
                    PLog.i(TAG, "message seen");
                    Realm realm = Message.REALM(Config.getApplicationContext());
                    Message msg = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
                    if (msg != null) {
                        realm.beginTransaction();
                        msg.setState(Message.STATE_SEEN);
                        realm.commitTransaction();
                    } else {
                        PLog.i(TAG, "message not available for update");
                    }
                    realm.close();
                }
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    };
    private static final Emitter.Listener MESSAGE_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            PLog.i(TAG, "socket message received: " + args[0].toString());
            //process message
            String data = args[0].toString();
            processMessage(Config.getApplicationContext(), data);
        }
    };
    static SocketIoClient messagingClient;

    static void startListeningForSocketMessages() {
        initClient();
    }

    private static void initClient() {
        synchronized (MessageCenter.class) {
            if (messagingClient == null) {
                messagingClient = SocketIoClient.getInstance(Config.PAIRAPP_ENDPOINT + "/message", UserManager.getMainUserId());
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

    static void notifyReceived(final Message message) {
        //noinspection StatementWithEmptyBody
        if (ThreadUtils.isMainThread()) {
            TaskManager.execute(new Runnable() {
                @Override
                public void run() {
                    doNotifyReceived(message);
                }
            });
            return;
        }
        doNotifyReceived(message);    }

    private static void doNotifyReceived(Message message) {
        if (LiveCenter.isOnline(message.getFrom())) {
            if (messagingClient == null) {
                initClient();
            }
            JSONObject obj = new JSONObject();
            try {
                obj.put(SocketIoClient.PROPERTY_TO, message.getFrom());
                obj.put(SocketIoClient.MSG_STS_MESSAGE_ID, message.getId());
                obj.put(SocketIoClient.MSG_STS_STATUS, Message.STATE_RECEIVED);
                messagingClient.send(SocketIoClient.EVENT_MSG_STATUS, obj);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        } else {
            //maybe push.
            // TODO: 9/18/2015 use push
        }
    }

    private static void processMessage(Context context, String data) {
        Intent intent;
        intent = new Intent(context, MessageProcessor.class);
        intent.putExtra(KEY_MESSAGE, data);
        context.startService(intent);
    }

    static void notifyMessageSeen(final Message message) {
        //noinspection StatementWithEmptyBody
        if (ThreadUtils.isMainThread()) {
            TaskManager.execute(new Runnable() {
                @Override
                public void run() {
                    doNotifySeen(message);
                }
            });
            return;
        }
        doNotifySeen(message);

    }

    private static void doNotifySeen(Message message) {
        String sender = message.getFrom();
        if (UserManager.getInstance().isGroup(message.getTo())) {
            sender = message.getTo();
        }
        LiveCenter.invalidateNewMessageCount(sender);

        if (LiveCenter.isOnline(message.getFrom())) {
            //use socketsIO
            if (messagingClient == null) {
                initClient();
            }
            JSONObject obj = new JSONObject();
            try {
                obj.put(SocketIoClient.PROPERTY_TO, message.getFrom());
                obj.put(SocketIoClient.MSG_STS_MESSAGE_ID, message.getId());
                obj.put(SocketIoClient.MSG_STS_STATUS, Message.STATE_SEEN);
                messagingClient.send(SocketIoClient.EVENT_MSG_STATUS, obj);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        } else {
            //maybe push
            // TODO: 9/18/2015 use push
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        L.d(TAG, "push recieved");
        final String data = intent.getStringExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA);
        L.d(TAG, data);

        // TODO: 9/3/2015 check the purpose of the push
        processMessage(context, data);
    }
}
