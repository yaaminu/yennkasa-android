package com.pair.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.pair.data.Message;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
public class MessageCenter extends WakefulBroadcastReceiver {
    private static final String TAG = MessageCenter.class.getSimpleName();
    private static final String EXTRA_MESSAGE = "message";
    private static final String EXTRA_NEW_USER = "user";
    private static final String EXTRA_TYPE = Message.FIELD_TYPE;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "message received");
        Bundle bundle = intent.getExtras();

        final String extra_type = bundle.getString(EXTRA_TYPE);
        if(extra_type != null) {
            if (extra_type.equals(EXTRA_MESSAGE)) {
                intent = new Intent(context, MessageProcessor.class);
                intent.putExtras(bundle);
                startWakefulService(context, intent);
            } else if (extra_type.equals(EXTRA_NEW_USER)) {
                Log.i(TAG, "new user registered");
                // TODO: 6/19/2015 add user to our list of users
            } else {
                Log.i(TAG, "an unknown message received : " + bundle.getString(extra_type));
            }
        }else{
            Log.w(TAG,"message with unknown type: " + bundle.toString());
        }
        setResultCode(Activity.RESULT_OK);
    }
}
