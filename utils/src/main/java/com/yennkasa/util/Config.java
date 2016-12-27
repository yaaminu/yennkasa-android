package com.yennkasa.util;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;

import com.yennkasa.Errors.ErrorCenter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author null-pointer
 */
public class Config {

    public static final String APP_PREFS = "prefs";
    private static final String TAG = Config.class.getSimpleName();

    private static final String SERVER_URL_REMOTE = "https://chat-server-data.herokuapp.com";
    private static final String SERVER_URL_LOCAL = "http://10.0.3.2:4000";
    private static final String SERVER_URL_LOCAL_REAL_DEVICE = "http://192.168.43.115:4000";

    private static final String MESSAGE_SOCKET_API_LOCAL_REAL_DEVICE = "http://192.168.43.115:3000";
    private static final String MESSAGE_SOCKET_API_LOCAL = "http://10.0.3.2:3000";

    private static final String LIVE_SOCKET_API_REMOTE = "https://chat-server-yenkasa.herokuapp.com";
    private static final String ENV_PROD = "prod";
    private static final String ENV_DEV = "dev";
    public static final String PAIRAPP_ENV = getEnvironment();
    private static final String logMessage = "calling getApplication when init has not be called";
    private static final String detailMessage = "application is null. Did you forget to call Config.init()?";
    private static String APP_NAME = "PairApp";
    private static Application application;
    private static AtomicBoolean isAppOpen = new AtomicBoolean(false);
    private static volatile String currentPeer = "";

    private static boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        return state.equals(Environment.MEDIA_MOUNTED);
    }

    public static void init(Application pairApp) {
        Config.application = pairApp;
        new Thread(new Runnable() {
            @Override
            public void run() {
                setUpDirs();
            }
        });
    }


    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void setUpDirs() {
        if (isExternalStorageAvailable()) {
            //no need to worry calling this several times
            //if the file is already a directory it will fail silently
            getAppImgMediaBaseDir().mkdirs();
            getAppVidMediaBaseDir().mkdirs();
            getAppBinFilesBaseDir().mkdirs();
            getAppProfilePicsBaseDir().mkdirs();
            appAudioBaseDir().mkdirs();
            getTempDir().mkdirs();
        } else {
            PLog.w(TAG, "This is strange! no sdCard available on this device");
            ErrorCenter.reportError("noSdcard" + TAG, getApplicationContext().getString(R.string.no_sdcard));
        }

    }

    public static boolean isAppOpen() {
        return isAppOpen.get();
    }

    public static void appOpen(boolean chatRoomOpen) {
        isAppOpen.set(chatRoomOpen);
    }

    public static Context getApplicationContext() {
        if (application == null) {
            warnAndThrow(logMessage, detailMessage);
        }
        return application.getApplicationContext();
    }

    public static Application getApplication() {
        if (application == null) {
            warnAndThrow(logMessage, detailMessage);
        }
        return Config.application;
    }

    private static void warnAndThrow(String msg, String detailMessage) {
        PLog.w(TAG, msg);
        throw new IllegalStateException(detailMessage);
    }

    private static String getEnvironment() {
        if (isEmulator()) {
            return ENV_DEV;
        } else {
            return ENV_PROD;
        }
    }

    public static boolean isEmulator() {
        return Build.HARDWARE.contains("goldfish")
                || Build.PRODUCT.equals("sdk") // sdk
                || Build.PRODUCT.endsWith("_sdk") // google_sdk
                || Build.PRODUCT.startsWith("sdk_") // sdk_x86
                || Build.FINGERPRINT.contains("generic");
    }

    public static SharedPreferences getApplicationWidePrefs() {
        if (application == null) {
            throw new IllegalStateException("application is null,did you forget to call init(Context) ?");
        }
        return getPreferences(Config.APP_PREFS);
    }

    public static String getDataServer() {
        return SERVER_URL_REMOTE;
//        if (PAIRAPP_ENV.equals(ENV_DEV)) {
//            return SERVER_URL_LOCAL;
//        } else {
//            return SERVER_URL_LOCAL_REAL_DEVICE;
//        }
    }

    public static File getAppBinFilesBaseDir() {
        File file = new File(Environment
                .getExternalStoragePublicDirectory(APP_NAME), getApplicationContext().getString(R.string.folder_name_files));
        if (!file.isDirectory()) {
            if (!file.mkdirs()) {
                PLog.f(TAG, "failed to create files dir");
            }
        }
        return file;
    }

    public static File getAppImgMediaBaseDir() {
        File file = new File(Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), APP_NAME);
        if (!file.isDirectory()) {
            if (!file.mkdirs()) {
                PLog.f(TAG, "failed to create pictures dir");
            }
        }
        return file;
    }

    public static File appAudioBaseDir() {
        File file = new File(Environment
                .getExternalStoragePublicDirectory(APP_NAME), getApplicationContext().getString(R.string.folder_name_voice_notes));
        if (!file.isDirectory()) {
            if (!file.mkdirs()) {
                PLog.f(TAG, "failed to create voice notes dir");
            }
        }
        return file;
    }

    public static File getAppVidMediaBaseDir() {
        File file = new File(Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), APP_NAME);
        if (!file.isDirectory()) {
            if (!file.mkdirs()) {
                PLog.f(TAG, "failed to create movies dir");
            }
        }
        return file;
    }

    public static File getAppProfilePicsBaseDir() {
        File file = new File(Environment
                .getExternalStoragePublicDirectory(APP_NAME), getApplicationContext().getString(R.string.folder_name_profile));
        if (!file.isDirectory()) {
            if (!file.mkdirs()) {
                PLog.f(TAG, "failed to create profile picture dir");
            }
        }
        return file;
    }

    public static File getTempDir() {
        File file = new File(Environment
                .getExternalStoragePublicDirectory(APP_NAME), "TMP");
        if (!file.isDirectory()) {
            if (!file.mkdirs()) {
                PLog.f(TAG, "failed to create tmp dir");
            }
        }
        return file;
    }


    public synchronized static String get(String propertyName) {
        return internalGet(propertyName);
    }

    public synchronized static void set(String propertyName, String value) {
        internalSet(propertyName, value);
    }

    private static void internalSet(String propertyName, String value) {
        if (propertyName == null || value == null) {
            throw new IllegalArgumentException("null propertyName or value");
        }
        ensureNotAlreadySet(propertyName);
        properties.put(propertyName, value);
    }

    private static void ensureNotAlreadySet(String propertyName) {
        if (properties.get(propertyName) != null) {
            throw new IllegalArgumentException("property with name " + propertyName + " already set");
        }
    }

    private static String internalGet(String propertyName) {
        if (propertyName == null) {
            throw new IllegalArgumentException("propertyName cannot be null");
        }
        String property = properties.get(propertyName);
        if (property == null) {
            throw new IllegalArgumentException("property " + propertyName + "not set");
        }
        return property;
    }

    public static String getMessageEndpoint() {
//        if (isEmulator()) {
//            return MESSAGE_SOCKET_API_LOCAL;
//        } else {
//            return MESSAGE_SOCKET_API_LOCAL_REAL_DEVICE;
//        }
        return LIVE_SOCKET_API_REMOTE;
    }

    private static final Map<String, String> properties = new HashMap<>();


    public static void setCurrentActivePeer(String peer) {
        ThreadUtils.ensureMain();
        currentPeer = peer == null ? "" : peer;
    }

    public static String getCurrentActivePeer() {
        return currentPeer;
    }

    public static String linksEndPoint() {
        return "https://pairapp-link-maker.herokuapp.com";
    }

    public static SharedPreferences getPreferences(String s) {
        return getApplicationContext().getSharedPreferences(s, Context.MODE_PRIVATE);
    }

}
