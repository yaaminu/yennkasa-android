package com.idea.messenger;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.TextUtils;

import com.idea.data.Conversation;
import com.idea.data.Message;
import com.idea.data.MessageJsonAdapter;
import com.idea.data.UserManager;
import com.idea.data.util.MessageUtils;
import com.idea.net.sockets.SocketIoClient;
import com.idea.util.ConnectionUtils;
import com.idea.util.LiveCenter;
import com.idea.util.PLog;
import com.idea.util.TaskManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.exceptions.RealmException;

public class MessageProcessor extends IntentService {
    public static final String SYNC_MESSAGES = "syncMessages";
    private static final String TAG = MessageProcessor.class.getSimpleName();
    public static final String MESSAGE = "message";
    public static final String UNKNOWN = "unknown";
    public static final String MESSAGE_STATUS = "messageStatus";

    public MessageProcessor() {
        super(TAG);
    }


    @Override
    protected void onHandleIntent(final Intent intent) {

        PowerManager manager = ((PowerManager) getSystemService(POWER_SERVICE));

        final PowerManager.WakeLock wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                wakeLock.acquire();
                try {
                    Bundle bundle = intent.getExtras();
                    String data = bundle.getString(MessageCenter.KEY_MESSAGE);

                    assert data != null;
                    PLog.d(TAG, data);
                    handleMessage(data);
                } finally {
                    wakeLock.release();
                }
            }
        }, true);
    }

    private void handleMessage(String data) {
        try {
            final JSONObject data1 = new JSONObject(data);
            String type = getType(data1);
            if (data.equals(SYNC_MESSAGES)) {
                MessagesProvider provider = PairAppClient.getMessageProvider();
                List<Message> messages = provider.retrieveMessages();
                for (Message message : messages) {
                    doProcessMessage(message);
                }
            } else if (type.equals(MESSAGE)) {
                Message message = MessageJsonAdapter.INSTANCE.fromJson(data);
                doProcessMessage(message);
            } else if (type.equals(MESSAGE_STATUS)) {
                Realm realm = Message.REALM(MessageProcessor.this);
                int state = data1.getInt(SocketIoClient.MSG_STS_STATUS);
                String messageId = data1.getString(SocketIoClient.MSG_STS_MESSAGE_ID);
                Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
                if (message != null && message.isValid()) {
                    realm.beginTransaction();
                    if (state == Message.STATE_RECEIVED && message.getState() != Message.STATE_SEEN) {
                        message.setState(Message.STATE_RECEIVED);
                    } else if (state == Message.STATE_SEEN) {
                        message.setState(Message.STATE_SEEN);
                    }
                    realm.commitTransaction();
                } else {
                    PLog.d(TAG, "message not available for update");
                }
                realm.close();
            } else {
                throw new JSONException("unknown message");
            }
        } catch (JSONException e) {
            PLog.i(TAG, "unknown message");
        }
    }

    private String getType(JSONObject data) {
        if (data.has(SocketIoClient.MSG_STS_STATUS)) {
            return SocketIoClient.MSG_STS_STATUS;
        } else if (data.has(Message.FIELD_MESSAGE_BODY)) {
            return MESSAGE;
        }

        final String message = data.optString("message");
        if (!TextUtils.isEmpty(message) && message.equals(SYNC_MESSAGES)) {
            return SYNC_MESSAGES;
        }
        return UNKNOWN;
    }

    private void doProcessMessage(Message message) {
        if (message.getFrom().equals(UserManager.getMainUserId())) {
            //how did this happen?
            return;
        }
        Realm realm = Message.REALM(this);
        try {
            String peerId;
            //for messages sent to groups, the group is always the recipient
            //and the members the senders
            if (Message.isGroupMessage(message)) {
                peerId = message.getTo();
                if (!UserManager.getInstance().isGroup(peerId)) {
                    PLog.d(TAG, "message from unknown group %s, dropped", peerId);
                    UserManager.getInstance().refreshGroup(peerId);
                    return;
                }
            } else {
                peerId = message.getFrom();
            }

            UserManager.getInstance().fetchUserIfRequired(peerId);
            //all other operations are deferred till we set up the conversation
            Conversation conversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();

            //WATCH OUT! don't touch this block!
            //////////////////////////////////////////////////////////////////////////////////////////
            //ensure the conversation and session is set up
            // before persisting the message
            if (conversation == null) { //create a new one
                conversation = Conversation.newConversation(realm, peerId);
                realm.beginTransaction();
            } else {
                realm.beginTransaction();
                Conversation.newSession(realm, conversation);
            }
            ///////////////////////////////////////////////////////////////////////////////////////////

            //force the new message to be newer than the session start up time
            message.setDateComposed(new Date(System.currentTimeMillis() + 10));
            message.setState(Message.STATE_RECEIVED);
            conversation.setLastActiveTime(new Date());//now
            try {
                message = realm.copyToRealm(message);
                conversation.setLastMessage(message);
            } catch (RealmException primaryKey) {
                //lets eat up this error
                realm.cancelTransaction();
                PLog.d(TAG, primaryKey.getMessage());
                PLog.d(TAG, "failed to process message");
                return;
            }
            conversation.setSummary(Message.isTextMessage(message) ? message.getMessageBody() : ""); //ui elements must detect this
            message = Message.copy(message);
            if (!conversation.isActive()) { //hopefully we might be able to void race conditions
                LiveCenter.incrementUnreadMessageForPeer(conversation.getPeerId());
            }
            realm.commitTransaction();
            realm.close();
            NotificationManager.INSTANCE.onNewMessage(this, message);
            MessageCenter.notifyReceived(message);
            if (!Message.isTextMessage(message)) {
                if (ConnectionUtils.isWifiConnected() || UserManager.getInstance().getBoolPref(UserManager.AUTO_DOWNLOAD_MESSAGE, false)) {
                    MessageUtils.download(message, null);
                }
            }
        } finally {
            realm.close();
        }
    }


}