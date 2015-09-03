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
    static final String KEY_MESSAGE = "message";

    @Override
    public void onReceive(Context context, Intent intent) {
        L.d(TAG, "push recieved");
        final String data = intent.getStringExtra(KEY_PUSH_DATA);
        L.d(TAG, data);
        // TODO: 9/3/2015 check the purpose of the push
        intent = new Intent(context, MessageProcessor.class);
        intent.putExtra(KEY_MESSAGE, data);
        context.startService(intent);
    }

}
