package com.pair.messenger;


import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.pair.adapter.MessageJsonAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;

import java.util.Date;

import io.realm.Realm;


public class MessageProcessor extends IntentService {
    public static final String TAG = MessageProcessor.class.getSimpleName();

    public MessageProcessor() {
        super(TAG);
    }


    @Override
    protected void onHandleIntent(Intent intent) {

        Bundle bundle = intent.getExtras();
        String messageJson = bundle.getString("message");
        Log.i(TAG, messageJson);
        Realm realm = Realm.getInstance(this);

        Message message = MessageJsonAdapter.INSTANCE.fromJson(messageJson);
        //noinspection ConstantConditions
        message.setState(Message.STATE_RECEIVED);

        String peerId;
        if (isGroupMessage(message)) {
            peerId = message.getTo();
        } else {
            peerId = message.getFrom();
        }
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
        conversation.setLastActiveTime(new Date());//now
        conversation.setLastMessage(realm.copyToRealm(message));
        conversation.setSummary(message.getMessageBody());
        realm.commitTransaction();
        // TODO: 6/14/2015 send a socket/gcm broadcast to server to notify sender of message state.
        Message copied = Message.copy(message);
        realm.close();
        NotificationManager.INSTANCE.onNewMessage(this, copied);
    }

    // TODO: 9/3/2015 this is not safe!
    private boolean isGroupMessage(Message message) {
        return message.getTo().contains("@");
    }
}