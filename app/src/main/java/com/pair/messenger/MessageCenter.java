package com.pair.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.pair.data.Conversation;
import com.pair.data.Message;

import java.util.Date;

import io.realm.Realm;

/**
 * Created by Null-Pointer on 5/28/2015.
 */
public class MessageCenter extends WakefulBroadcastReceiver {
    private static final String TAG = MessageCenter.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {

        Bundle bundle = intent.getExtras();
        String messageJson = bundle.getString("message");
        Realm realm = Realm.getInstance(context);
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
        setResultCode(Activity.RESULT_OK);
    }
}
