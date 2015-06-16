package com.pair.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class ConnectionHelper {

    @SuppressWarnings("unused")
    public static boolean isConnected() {
        Context context = Config.getApplicationContext();
        ConnectivityManager manager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        return ((networkInfo != null) && networkInfo.isConnected());
    }

    public static boolean isConnectedOrConnecting() {
        Context context = Config.getApplicationContext();
        ConnectivityManager manager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        return ((networkInfo != null) && networkInfo.isConnectedOrConnecting());
    }
}
