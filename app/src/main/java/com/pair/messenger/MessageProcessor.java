package com.pair.messenger;


import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.pair.data.Message;

import io.realm.Realm;


public class MessageProcessor extends IntentService {
    public static final String TAG = MessageProcessor.class.getSimpleName();

    public MessageProcessor() {
        super(TAG);
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        if(true){
            throw new RuntimeException("better");
        }
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);

        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) {

            if (GoogleCloudMessaging.
                    MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                Log.e(TAG, "Error: failed to send message");

            } else if (GoogleCloudMessaging.
                    MESSAGE_TYPE_DELETED.equals(messageType)) {
                Log.e(TAG, "Error: message deleted");

            } else if (GoogleCloudMessaging.
                    MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                String message = extras.getString("message");
                if (message == null) {
                    throw new RuntimeException("empty message received");
                }
                Log.i("TAG", "Received: " + message);
                Realm realm = Realm.getInstance(this);
                realm.beginTransaction();
                Message message1 = realm.createObjectFromJson(Message.class, message);
                message1.setState(Message.RECEIVED);
                realm.commitTransaction();
            }
        }
        MessageCenter.completeWakefulIntent(intent);
    }
}