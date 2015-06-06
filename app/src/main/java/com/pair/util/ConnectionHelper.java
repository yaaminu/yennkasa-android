package com.pair.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class ConnectionHelper {

    public static boolean isConnected(Context context) {
        ConnectivityManager manager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        return ((networkInfo != null) && networkInfo.isConnected());
    }

    public static boolean isConnectedOrConnecting(Context context) {
        ConnectivityManager manager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        return ((networkInfo != null) && networkInfo.isConnectedOrConnecting());
    }
}
