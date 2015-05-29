package com.pair.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by Null-Pointer on 5/28/2015.
 */
public class MessageCenter extends BroadcastReceiver {
    private static final String TAG = MessageCenter.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
      Log.i(TAG, intent.getExtras().toString());
    }
}
