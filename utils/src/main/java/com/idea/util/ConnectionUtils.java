package com.idea.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class ConnectionUtils {

    private static final String TAG = ConnectionUtils.class.getSimpleName();

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

    public static boolean isActuallyConnected() {
        ThreadUtils.ensureNotMain();
        return isConnected() && isReallyReallyConnected();
    }

    private static boolean isReallyReallyConnected() {
        HttpURLConnection connection = null;
        InputStream in = null;
        //noinspection EmptyCatchBlock
        try {
            connection = (HttpURLConnection) new URL("https://facebook.com").openConnection();
            connection.connect();
            in = connection.getInputStream();
            if (in.read() != -1) {
                PLog.d(TAG, "user has real network connection");
                return true;
            }
        } catch (UnknownHostException | SocketTimeoutException e) {
        } catch (MalformedURLException e) {
            throw new RuntimeException(e.getCause());
        } catch (IOException e) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            IOUtils.closeQuietly(in);
        }
        PLog.d(TAG, "not connected");
        return false;
    }
}
