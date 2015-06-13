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
    }
}
