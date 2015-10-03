package com.idea.messenger;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;

import com.idea.data.Conversation;
import com.idea.data.Message;
import com.idea.data.MessageJsonAdapter;
import com.idea.data.UserManager;
import com.idea.data.util.MessageUtils;
import com.idea.util.ConnectionUtils;
import com.idea.util.LiveCenter;
import com.idea.util.PLog;

import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.exceptions.RealmException;

public class MessageProcessor extends IntentService {
    public static final String SYNC_MESSAGES = "syncMessages";
    private static final String TAG = MessageProcessor.class.getSimpleName();

    public MessageProcessor() {
        super(TAG);
    }


    @Override
    protected void onHandleIntent(Intent intent) {

        PowerManager manager = ((PowerManager) getSystemService(POWER_SERVICE));

        PowerManager.WakeLock wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();

        Bundle bundle = intent.getExtras();
        String data = bundle.getString(MessageCenter.KEY_MESSAGE);

        assert data != null;
        PLog.i(TAG, data);
        if (data.equals(SYNC_MESSAGES)) {
            MessagesProvider provider = PairAppClient.getMessageProvider();
            List<Message> messages = provider.retrieveMessages();
            for (Message message : messages) {
                doProcessMessage(message);
            }
        } else {
            Message message = MessageJsonAdapter.INSTANCE.fromJson(data);
            doProcessMessage(message);
        }
        wakeLock.release();

    }

    private void doProcessMessage(Message message) {
        if (message.getFrom().equals(UserManager.getMainUserId())) {
            //how did this happen?
            return;
        }
        Realm realm = Realm.getInstance(this);
        String peerId;
        //for messages sent to groups, the group is always the recipient
        //and the members the senders
        if (Message.isGroupMessage(message)) {
            peerId = message.getTo();
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
        realm.commitTransaction();

        message = Message.copy(message);
        if (!conversation.isActive()) { //hopefully we might be able to void race conditions
            LiveCenter.incrementUnreadMessageForPeer(conversation.getPeerId());
        }
        realm.close();
        NotificationManager.INSTANCE.onNewMessage(this, message);
        MessageCenter.notifyReceived(message);
        if (!Message.isTextMessage(message)) {
            if (ConnectionUtils.isWifiConnected() || UserManager.getInstance().getBoolPref(UserManager.AUTO_DOWNLOAD_MESSAGE, false)) {
                MessageUtils.download(message, null);
            }
        }
    }

}