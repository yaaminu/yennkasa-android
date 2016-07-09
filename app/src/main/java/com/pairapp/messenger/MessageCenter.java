package com.pairapp.messenger;

import android.content.Context;
import android.content.Intent;

import com.pairapp.util.PLog;
import com.parse.ParsePushBroadcastReceiver;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * @author by Null-Pointer on 5/28/2015.
 */
public class MessageCenter extends ParsePushBroadcastReceiver {
    private static final String TAG = MessageCenter.class.getSimpleName();

    private static void processMessage(Context context, String data) {
        Intent intent;
        intent = new Intent(context, MessageProcessor.class);
        intent.putExtra(MessageProcessor.MESSAGE, data);
        context.startService(intent);
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        PLog.d(TAG, "incoming message");
        String data = intent.getStringExtra(KEY_PUSH_DATA);
        PLog.d(TAG, data);
        try {
            JSONObject pushMessage = new JSONObject(data);

            String tmp = pushMessage.optString("message", data);
            if (!tmp.equals(MessageProcessor.SYNC_MESSAGES)) {
                data = tmp;
            }
            PLog.d(TAG, "main Message: %s", data);
        } catch (JSONException e) {
            throw new RuntimeException(e.getCause());
        }
        processMessage(context, data);
    }

}
