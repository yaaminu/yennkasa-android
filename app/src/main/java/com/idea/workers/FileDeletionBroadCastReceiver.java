package com.idea.workers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.idea.util.PLog;

public class FileDeletionBroadCastReceiver extends BroadcastReceiver {
    private final String TAG = FileDeletionBroadCastReceiver.class.getSimpleName();
    public FileDeletionBroadCastReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PLog.d(TAG, "received request to delete a file");
        PLog.d(TAG, "" + intent.getAction());
        PLog.d(TAG, intent.getStringExtra("body"));
        intent.setAction(WorkHorse.FILE_META_OPERATION);
        intent.setComponent(new ComponentName(context, WorkHorse.class));
        context.startService(intent);
    }
}
