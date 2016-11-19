package com.pairapp.messenger;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;

import com.pairapp.data.Conversation;
import com.pairapp.data.Message;
import com.pairapp.data.UserManager;
import com.pairapp.data.util.MessageUtils;
import com.pairapp.util.Config;
import com.pairapp.util.ConnectionUtils;
import com.pairapp.util.Event;
import com.pairapp.util.LiveCenter;
import com.pairapp.util.PLog;
import com.pairapp.util.TaskManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.realm.Realm;
import io.realm.exceptions.RealmPrimaryKeyConstraintException;

import static com.pairapp.messenger.MessengerBus.MESSAGE_RECEIVED;
import static com.pairapp.messenger.MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS;
import static com.pairapp.messenger.MessengerBus.get;

public class MessageProcessor extends IntentService {
    public static final String SYNC_MESSAGES = "syncMessages";
    private static final String TAG = MessageProcessor.class.getSimpleName();
    static final String MESSAGE = "message";
    private static final String UNKNOWN = "unknown";
    static final String MESSAGE_STATUS = "messageStatus";
    static final String UPDATE = "update";
    public static final String TIMESTAMP = "timestamp";
    private final Lock processLock = new ReentrantLock(true);

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
                    String data = bundle.getString(MESSAGE);
                    assert data != null;
                    long timestamp = intent.getLongExtra(TIMESTAMP, 0L);
                    PLog.d(TAG, "payload: %s, timestamp: %s", data, new Date(timestamp));
                    handleMessage(data, timestamp);
                } finally {
                    wakeLock.release();
                }
            }
        }, false);
    }

    private void handleMessage(String data, long timestamp) {
        try {
            final JSONObject data1 = new JSONObject(data);
            String type = getType(data1);
            //noinspection IfCanBeSwitch
            if (type.equals(SYNC_MESSAGES)) {
                // FIXME: 6/21/2016 setup a provider that will sync messages
            } else if (type.equals(MESSAGE)) {
                Message message = Message.fromJSON(data);
                doProcessMessage(message, timestamp);
            } else if (type.equals(MESSAGE_STATUS)) {
                Realm realm = Message.REALM(MessageProcessor.this);
                int state = data1.optInt(Message.MSG_STS_STATUS, Message.STATE_SEEN);
                String messageId = data1.getString(Message.MSG_STS_MESSAGE_ID);
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
            PLog.d(TAG, e.getMessage(), e);
        }
    }

    private String getType(JSONObject data) {
        if (data.has(Message.FIELD_MESSAGE_BODY) && data.has(Message.FIELD_ID)) {
            return MESSAGE;
        }
        if ((data.length() == 1 && data.has(Message.FIELD_ID)) || data.has(MESSAGE_STATUS)) {
            return MESSAGE_STATUS;
        }
        if (data.has(UPDATE)) {
            return UPDATE;
        }
        final String message = data.optString("message");
        if (SYNC_MESSAGES.equals(message)) {
            return SYNC_MESSAGES;
        }
        return UNKNOWN;
    }

    private void doProcessMessage(Message message, long timestamp) {
        try {
            processLock.lock();
            if (message.getFrom().equals(UserManager.getMainUserId())) {
                //how did this happen?
                return;
            }
            if (!MessageUtils.isSendableMessage(message)) {
                PLog.d(TAG, "unsupported message type %s", Message.toJSON(message));
                return;
            }

            Realm realm = Message.REALM(this);
            try {
                String peerId;
                //for messages sent to groups, the group is always the recipient
                //and the members the senders
                if (Message.isGroupMessage(message)) {
                    peerId = message.getTo();
                } else {
                    peerId = message.getFrom();
                }
                UserManager userManager = UserManager.getInstance();
                if (userManager.isBlocked(peerId)) {
                    PLog.d(TAG, "message from a blocked user, dropping message");
                    PLog.d(TAG, "%s is blocked", peerId);
                    return;
                }
                userManager.fetchUserIfRequired(peerId);
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
                message.setDateComposed(new Date(timestamp));
                message.setState(Message.STATE_RECEIVED);
                conversation.setLastActiveTime(new Date(Math.max(timestamp, System.currentTimeMillis())));//now
                try {
                    message = realm.copyToRealm(message);
                    conversation.setLastMessage(message);
                } catch (RealmPrimaryKeyConstraintException primaryKey) {
                    //lets eat up this exception
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
                NotificationManager.INSTANCE.onNewMessage(this, message);
                get(PAIRAPP_CLIENT_POSTABLE_BUS).post(Event.create(MESSAGE_RECEIVED, null, message.getId()));
                if (!Message.isTextMessage(message) && Worker.getCurrentActiveDownloads() < Worker.MAX_PARRALLEL_DOWNLOAD) {
                    if ((ConnectionUtils.isWifiConnected()
                            && userManager.getBoolPref(UserManager.AUTO_DOWNLOAD_MESSAGE_WIFI, false))
                            || (ConnectionUtils.isMobileConnected()
                            && userManager.getBoolPref(UserManager.AUTO_DOWNLOAD_MESSAGE_MOBILE, false))) {
                        Worker.download(this, message, true);
                    }
                }

            } finally {
                realm.close();
            }
        } finally {
            processLock.unlock();
        }
    }

}
