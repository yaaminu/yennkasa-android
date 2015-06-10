package com.pair.messenger;


import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

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
        Realm realm = Realm.getInstance(this);
        realm.beginTransaction();
        Message message = realm.createObjectFromJson(Message.class, messageJson);
        message.setState(Message.RECEIVED);

        Conversation conversation = realm.where(Conversation.class).equalTo("peerId", message.getFrom()).findFirst();
        if (conversation == null) { //create a new one
            conversation = realm.createObject(Conversation.class);
            conversation.setPeerId(message.getFrom());
        }
        conversation.setLastActiveTime(new Date());//now
        conversation.setLastMessage(message);
        realm.commitTransaction();
        realm.close();

        //TODO notify user
        MessageCenter.completeWakefulIntent(intent);
    }
}