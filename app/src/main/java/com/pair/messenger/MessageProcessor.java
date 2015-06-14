package com.pair.messenger;


import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.pairapp.ui.ChatActivity;

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
        Realm realm = Realm.getInstance(this);
        realm.beginTransaction();
        Message message = realm.createObjectFromJson(Message.class, messageJson);
        // TODO: 6/14/2015 send a socket/gcm broadcast to server to notify sender of message state.
        //noinspection ConstantConditions
        message.setState(Message.STATE_RECEIVED);
        Conversation conversation = realm.where(Conversation.class).equalTo("peerId", message.getFrom()).findFirst();
        if (conversation == null) { //create a new one
            conversation = realm.createObject(Conversation.class);
            conversation.setActive(false);
            conversation.setPeerId(message.getFrom());
        }
        conversation.setLastActiveTime(new Date());//now
        conversation.setLastMessage(message);
        conversation.setSummary("<--" + message.getMessageBody());
        realm.commitTransaction();
        if (!conversation.isActive()) {
            Message copied = new Message(message);
            Intent action = new Intent(this, ChatActivity.class);
            action.putExtra(ChatActivity.PEER_ID, copied.getFrom());
            NotificationManager.INSTANCE.onNewMessage(copied, action);
        }
        realm.close();
        MessageCenter.completeWakefulIntent(intent);
    }
}