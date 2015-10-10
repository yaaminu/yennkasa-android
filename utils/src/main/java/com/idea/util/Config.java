package com.idea.util;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * @author null-pointer
 */
public class Config {

    public static final String APP_PREFS = "prefs";
    public static final String APP_USER_AGENT = "pairapp-android-development-version";
    private static final String TAG = Config.class.getSimpleName();
    private static final String HOST_REAL_SERVER = "http://192.168.43.42:3000";
    private static final String LOCAL_HOST_GENYMOTION = "http://10.0.3.2:3000";
    private static final String DP_API_GENYMOTION = "http://10.0.3.2:5000/fileApi/dp";
    private static final String DP_API_REAL_PHONE = "http://192.168.43.42:5000/fileApi/dp";
    private static final String MESSAGE_API_GENY = "http://10.0.3.2:5000/fileApi/message";
    private static final String MESSAGE_API_REAL_PHONE = "http://192.168.43.42:5000/fileApi/message";
    private static final String MESSAGE_SOCKET_API_REMOTE = "https://pairap-message.herokuapp.com/message";
    private static final String MESSAGE_SOCKET_API_LOCAL = "http://10.0.3.2:3000/message";
    private static final String LIVE_SOCKET_API_REMOTE = "https://pairapp-live.herokuapp.com/live";
    private static final String LIVE_SOCKET_API_LOCAL = "http://10.0.3.2:4000/live";
    private static final String ENV_PROD = "prod";
    private static final String ENV_DEV = "dev";
    public static final String PAIRAPP_ENV = getEnvironment();
    public static final String PAIRAPP_ENDPOINT = getEndPoint();
    private static final String logMessage = "calling getApplication when init has not be called";
    private static final String detailMessage = "application is null. Did you forget to call Config.init()?";
    private static float SCREEN_DENSITY;
    private static String APP_NAME = "PairApp";
    private static Application application;
    private static AtomicBoolean isChatRoomOpen = new AtomicBoolean(false);

    private static boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        return state.equals(Environment.MEDIA_MOUNTED);
    }

    public static void init(Application pairApp) {
        Config.application = pairApp;
       // SCREEN_DENSITY = pairApp.getResources().getDisplayMetrics().density;
        setUpDirs();
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
            getTempDir().mkdirs();
        } else {
            PLog.w(TAG, "This is strange! no sdCard available on this device");
        }

    }

    public static boolean isAppOpen() {
        return isChatRoomOpen.get();
    }

    public static void appOpen(boolean chatRoomOpen) {
        isChatRoomOpen.set(chatRoomOpen);
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

    private static String getEndPoint() {
        if (PAIRAPP_ENV.equals(ENV_DEV)) {
            return LOCAL_HOST_GENYMOTION;
        } else {
            // TODO replace this with real url
            return HOST_REAL_SERVER;
        }
    }

    public static String getDpEndpoint() {
        if (PAIRAPP_ENV.equals(ENV_DEV)) {
            return DP_API_GENYMOTION;
        } else {
            // TODO replace this with real url
            return DP_API_REAL_PHONE;
        }
    }

    public static SharedPreferences getApplicationWidePrefs() {
        if (Config.application == null) {
            throw new IllegalStateException("application is null,did you forget to call init(Context) ?");
        }
        return getApplication().getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE);
    }

    public static String getMessageApiEndpoint() {
        if (PAIRAPP_ENV.equals(ENV_DEV)) {
            return MESSAGE_API_GENY;
        } else {
            // TODO replace this with real url
            return MESSAGE_API_REAL_PHONE;
        }
    }

    public static File getAppBinFilesBaseDir() {
        return new File(Environment
                .getExternalStoragePublicDirectory(APP_NAME), "Files");
    }

    public static File getAppImgMediaBaseDir() {
        return new File(Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), APP_NAME);
    }

    public static File getAppVidMediaBaseDir() {
        return new File(Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), APP_NAME);
    }

    public static File getAppProfilePicsBaseDir() {
        return new File(Environment
                .getExternalStoragePublicDirectory(APP_NAME), "profile");
    }

    public static File getTempDir() {
        return new File(Environment
                .getExternalStoragePublicDirectory(APP_NAME), "TMP");
    }

    public static String deviceArc() {
        //noinspection deprecation
        return System.getProperty("os.arch", Build.CPU_ABI).toLowerCase(Locale.US);
    }

    public static boolean supportsCalling() {
        return deviceArc().contains("arm");
    }

    public static float getScreenDensity() {
        return SCREEN_DENSITY;
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

    public static String getLiveEndpoint() {
        //STOPSHIP
        if (isEmulator()&& false) {
            return LIVE_SOCKET_API_LOCAL;
        } else {
            return LIVE_SOCKET_API_REMOTE;
        }
    }

    public static String getMessageEndpoint() {
        //STOPSHIP
        if (isEmulator() && false) {
            return MESSAGE_SOCKET_API_LOCAL;
        } else {
            return MESSAGE_SOCKET_API_REMOTE;
        }
    }

    private static final Map<String, String> properties = new HashMap<>();
}
