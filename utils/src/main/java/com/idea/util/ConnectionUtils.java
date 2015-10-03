package com.idea.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class ConnectionUtils {

    @SuppressWarnings("unused")
    public static boolean isConnected() {
        NetworkInfo networkInfo = getNetworkInfo();
        return ((networkInfo != null) && networkInfo.isConnected());
    }

    public static boolean isConnectedOrConnecting() {
        NetworkInfo networkInfo = getNetworkInfo();
        return ((networkInfo != null) && networkInfo.isConnectedOrConnecting());
    }

    private static NetworkInfo getNetworkInfo() {
        Context context = Config.getApplicationContext();
        ConnectivityManager manager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        return manager.getActiveNetworkInfo();
    }

    public static boolean isWifiConnected() {
        NetworkInfo info = getNetworkInfo();
        int type = info.getType();
        return type == ConnectivityManager.TYPE_WIFI;
    }
}
