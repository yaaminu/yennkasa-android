package com.pair.messenger;


import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;

import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.MessageJsonAdapter;

import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.exceptions.RealmException;

public class MessageProcessor extends IntentService {
    private static final String TAG = MessageProcessor.class.getSimpleName();
    public static final String SYNC_MESSAGES = "syncMessages";

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
        Log.i(TAG, data);
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
        Realm realm = Realm.getInstance(this);
        String peerId;
        //for messages sent to groups, the group is always the recipient
        //and the members the senders
        if (isGroupMessage(message)) {
            peerId = message.getTo();
        } else {
            peerId = message.getFrom();
        }
        //all other operations are deferred till we set up the conversation
        Conversation conversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();

        //WATCH OUT! don't touch this block except you are careful!
        //////////////////////////////////////////////////////////////////////////////////////////
        //ensure the conversation and session is set up
        // before persisting the message
        if (conversation == null) { //create a new one
            Conversation.newConversation(this, peerId);
            //round trips
            conversation = realm.where(Conversation.class)
                    .equalTo(Conversation.FIELD_PEER_ID,
                            peerId).findFirst();
            realm.beginTransaction();
        } else {
            realm.beginTransaction();
            Conversation.newSession(realm, conversation);
        }
        ///////////////////////////////////////////////////////////////////////////////////////////

        //force the new message to be newer than the session start up time by adding one to it
        message.setDateComposed(new Date(System.currentTimeMillis() + 1));
        message.setState(Message.STATE_RECEIVED);
        conversation.setLastActiveTime(new Date());//now
        try {
            conversation.setLastMessage(realm.copyToRealm(message));
        } catch (RealmException primaryKey) {
            realm.cancelTransaction();
            Log.i(TAG, primaryKey.getMessage());
            Log.d(TAG, "failed to process message");
            return;
        }
        conversation.setSummary(message.getMessageBody());
        realm.commitTransaction();
        Message copied = Message.copy(message);
        realm.close();
        NotificationManager.INSTANCE.onNewMessage(this, copied);
        // TODO: 6/14/2015 send a socket/gcm broadcast to server to notify sender of message state.
        MessageCenter.notifyReceived(copied);
    }

    // TODO: 9/3/2015 this is not safe!
    //we want to avoid round a trip to the database
    private boolean isGroupMessage(Message message) {
        return message.getTo().contains("@");
    }
}