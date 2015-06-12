package com.pair.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

/**
 * Created by Null-Pointer on 5/28/2015.
 */
public class MessageCenter extends WakefulBroadcastReceiver {
    private static final String TAG = MessageCenter.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "message received");
        Bundle bundle = intent.getExtras();
        intent = new Intent(context,MessageProcessor.class);
        intent.putExtras(bundle);
        startWakefulService(context, intent);
        setResultCode(Activity.RESULT_OK);
//        String messageJson = bundle.getString("message");
//        Realm realm = Realm.getInstance(context);
//        realm.beginTransaction();
//        Message message = realm.createObjectFromJson(Message.class, messageJson);
//        message.setState(Message.RECEIVED);
//
//        Conversation conversation = realm.where(Conversation.class).equalTo("peerId", message.getFrom()).findFirst();
//        if (conversation == null) { //create a new one
//            conversation = realm.createObject(Conversation.class);
//            conversation.setActive(false);
//            conversation.setPeerId(message.getFrom());
//        }
//        conversation.setLastActiveTime(new Date());//now
//        conversation.setLastMessage(message);
//        realm.commitTransaction();
//        if (!conversation.isActive()) {
//            //notify user, for now we are showing a toast message
//            Toast toast = Toast.makeText(context, message.getFrom() +":\n" + message.getMessageBody(), Toast.LENGTH_LONG);
//            toast.show();
//        }
//        realm.close();

    }
}
