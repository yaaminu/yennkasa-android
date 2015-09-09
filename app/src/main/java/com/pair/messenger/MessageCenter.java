package com.pair.messenger;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.google.gson.JsonObject;
import com.pair.Config;
import com.pair.data.Message;
import com.pair.data.UserManager;
import com.pair.net.sockets.SocketIoClient;
import com.pair.util.L;
import com.parse.ParsePushBroadcastReceiver;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
public class MessageCenter extends ParsePushBroadcastReceiver {
    private static final String TAG = MessageCenter.class.getSimpleName();
    private static final String EXTRA_MESSAGE = "message";
    private static final String EXTRA_NEW_USER = "user";
    private static final String EXTRA_TYPE = Message.FIELD_TYPE;
    static final String KEY_MESSAGE = "message";

    @Override
    public void onReceive(Context context, Intent intent) {
        L.d(TAG, "push recieved");
        final String data = intent.getStringExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA);
        L.d(TAG, data);

        // TODO: 9/3/2015 check the purpose of the push
        processMessage(context, data);
    }

//    private static final Emitter.Listener ON_MESSAGE_STATUS = new Emitter.Listener() {
//        @Override
//        public void call(Object... args) {
//            Log.i(TAG, "msgStatus event");
//        }
//    };

    private static final Emitter.Listener MESSAGE_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.i(TAG, "socket message received: " + args[0].toString());
            //process message
            String data = args[0].toString();
            processMessage(Config.getApplicationContext(), data);
        }
    };

    static SocketIoClient messagingClient;

    static void startListeningForSocketMessages() {
        if (messagingClient == null) {
            messagingClient = SocketIoClient.getInstance(Config.PAIRAPP_ENDPOINT + "/message", UserManager.getMainUserId());
        }
        messagingClient.registerForEvent(SocketIoClient.EVENT_MESSAGE, MESSAGE_RECEIVER);
    }

    static void stopListeningForSocketMessages() {
        if (messagingClient != null) {
            messagingClient.unRegisterEvent(SocketIoClient.EVENT_MESSAGE, MESSAGE_RECEIVER);
            messagingClient.close();
            messagingClient = null;
        }
    }

    static void notifyReceived(Message message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("to", message.getFrom());
        obj.addProperty("messageId", message.getId());
        obj.addProperty("status", Message.STATE_RECEIVED);
        messagingClient.broadcast(SocketIoClient.EVENT_MSG_STATUS, obj);
    }

    private static void processMessage(Context context, String data) {
        Intent intent;
        intent = new Intent(context, MessageProcessor.class);
        intent.putExtra(KEY_MESSAGE, data);
        context.startService(intent);
    }

}
