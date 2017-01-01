package com.yennkasa.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ConnectivityReceiver extends BroadcastReceiver {
    public ConnectivityReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectionUtils.setUpConnectionState(intent);
    }
}
