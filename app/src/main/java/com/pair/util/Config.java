package com.pair.util;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author null-pointer
 */
public class Config {

    public static final String TAG = Config.class.getSimpleName();
    public static final String APP_PREFS = "prefs";
    private static final String HOST_REAL_SERVER = "http://192.168.43.42:3000";
    private static final String LOCAL_HOST_GENYMOTION = "http://10.0.3.2:3000";
    private static final String ENV_PROD = "prod";
    private static final String ENV_DEV = "dev";
    public static final String PAIRAPP_ENV = getEnvironment();
    public static final String PAIRAPP_ENDPOINT = getEndPoint();
    private static final String logMessage = "calling getApplication when init has not be called";
    private static final String detailMessage = "application is null. Did you forget to call Config.init()?";
    public static final String APP_USER_AGENT = "pairapp-android-development-version";
    private static String APP_NAME = "PairApp";
    public static final File APP_IMG_MEDIA_BASE_DIR = getImageBasedir();
    public static final File APP_VID_MEDIA_BASE_DIR = getVideoBaseDir();
    public static final File APP_PROFILE_PICS_BASE_DIR = getProfilePicsBasedir();

    private static File getProfilePicsBasedir() {
        if (isExternalStorageAvailable()) {
            return new File(Environment
                    .getExternalStoragePublicDirectory(APP_NAME), "profile");
        }
        return null;
    }

    private static File getImageBasedir() {
        if (isExternalStorageAvailable()) {
            return new File(Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), APP_NAME);
        }
        return null;
    }

    private static File getVideoBaseDir() {
        if (isExternalStorageAvailable()) {
            return new File(Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), APP_NAME);
        }
        return null;
    }

    private static boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        return state.equals(Environment.MEDIA_MOUNTED);
    }

    private static Application application;
    private static AtomicBoolean isChatRoomOpen = new AtomicBoolean(false);

    public static void init(Application pairApp) {
        Config.application = pairApp;
        setUpDirs();
    }

    private static void setUpDirs() {
        //no need to worry calling this several times
        //if the file is already a directory it will fail silently
        APP_IMG_MEDIA_BASE_DIR.mkdirs();
        APP_VID_MEDIA_BASE_DIR.mkdirs();
    }

    public static boolean isChatRoomOpen() {
        return isChatRoomOpen.get();
    }

    public static void setIsChatRoomOpen(boolean chatRoomOpen) {
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
        Log.w(TAG, msg);
        throw new IllegalStateException(detailMessage);
    }

    private static String getEnvironment() {
        if (isEmulator()) {
            return ENV_DEV;
        } else {
            return ENV_PROD;
        }
    }

    private static boolean isEmulator() {
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

    public static void enableComponent(Class clazz) {
        Log.d(TAG, "enabling " + clazz.getSimpleName());
        ComponentName receiver = new ComponentName(application, clazz);

        PackageManager pm = application.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    public static void disableComponent(Class clazz) {
        Log.d(TAG, "disabling " + clazz.getSimpleName());
        ComponentName receiver = new ComponentName(application, clazz);

        PackageManager pm = application.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}
