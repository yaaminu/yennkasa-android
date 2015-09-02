package com.pair.messenger;

import android.content.Context;
import android.content.Intent;

import com.pair.data.Message;
import com.pair.util.L;
import com.parse.ParsePushBroadcastReceiver;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
public class MessageCenter extends ParsePushBroadcastReceiver {
    private static final String TAG = MessageCenter.class.getSimpleName();
    private static final String EXTRA_MESSAGE = "message";
    private static final String EXTRA_NEW_USER = "user";
    private static final String EXTRA_TYPE = Message.FIELD_TYPE;

    @Override
    protected void onPushReceive(Context context, Intent intent) {
        L.d(TAG, "push recieved");
        L.d(TAG, intent.getStringExtra(KEY_PUSH_DATA));

    }
}
