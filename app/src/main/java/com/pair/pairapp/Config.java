package com.pair.pairapp;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.pair.messenger.MessageProcessor;
import com.pair.messenger.PairAppClient;
import com.pair.workers.BootReceiver;
import com.pair.workers.ContactSyncService;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit.RequestInterceptor;

/**
 * @author null-pointer
 */
public class Config {

    private static final String TAG = Config.class.getSimpleName();
    public static final String APP_PREFS = "prefs";
    private static final String HOST_REAL_SERVER = "http://192.168.43.42:3000";
    private static final String LOCAL_HOST_GENYMOTION = "http://10.0.3.2:3000";
    private static final String DP_API_GENYMOTION = "http://10.0.3.2:5000/fileApi/dp";
    private static final String DP_API_REAL_PHONE = "http://192.168.43.42:5000/fileApi/dp";
    private static final String MESSAGE_API_GENY = "http://10.0.3.2:5000/fileApi/message";
    private static final String MESSAGE_API_REAL_PHONE = "http://192.168.43.42:5000/fileApi/message";
    private static final String ENV_PROD = "prod";
    private static final String ENV_DEV = "dev";
    public static final String PAIRAPP_ENV = getEnvironment();
    public static final String DP_ENDPOINT = getDpEndpoint();
    public static final String MESSAGE_ENDPOINT = getMessageApiEndpoint();
    public static final String PAIRAPP_ENDPOINT = getEndPoint();
    private static final String logMessage = "calling getApplication when init has not be called";
    private static final String detailMessage = "application is null. Did you forget to call Config.init()?";
    public static final String APP_USER_AGENT = "pairapp-android-development-version";
    private static String APP_NAME = "PairApp";

    //shared with message adapter
    public static final RequestInterceptor INTERCEPTOR = new RequestInterceptor() {
        @Override
        public void intercept(RequestFacade requestFacade) {
            requestFacade.addHeader("Authorization", "kiiboda+=s3cr3te");
            requestFacade.addHeader("User-Agent", Config.APP_USER_AGENT);
        }
    };

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


    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void setUpDirs() {
        if (isExternalStorageAvailable()) {
            //no need to worry calling this several times
            //if the file is already a directory it will fail silently
            getAppImgMediaBaseDir().mkdirs();
            getAppVidMediaBaseDir().mkdirs();
            getAppBinFilesBaseDir().mkdirs();
        } else {
            Log.w(TAG, "This is strange! no sdCard available on this device");
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

    private static String getDpEndpoint() {
        if (PAIRAPP_ENV.equals(ENV_DEV)) {
            return DP_API_GENYMOTION;
        } else {
            // TODO replace this with real url
            return DP_API_REAL_PHONE;
        }
    }

    private static void enableComponent(Class clazz) {
        Log.d(TAG, "enabling " + clazz.getSimpleName());
        ComponentName receiver = new ComponentName(application, clazz);

        PackageManager pm = application.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    private static void disableComponent(Class clazz) {
        Log.d(TAG, "disabling " + clazz.getSimpleName());
        ComponentName receiver = new ComponentName(application, clazz);

        PackageManager pm = application.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    public static void enableComponents() {
        enableComponent(BootReceiver.class);
        enableComponent(ContactSyncService.class);
        enableComponent(PairAppClient.class);
        enableComponent(MessageProcessor.class);
    }

    public static void disableComponents() {
        disableComponent(BootReceiver.class);
        disableComponent(ContactSyncService.class);
        disableComponent(PairAppClient.class);
        disableComponent(MessageProcessor.class);
    }

    public static SharedPreferences getApplicationWidePrefs() {
        if (Config.application == null) {
            throw new IllegalStateException("application is null,did you forget to call init(Context) ?");
        }
        return getApplication().getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE);
    }

    private static String getMessageApiEndpoint() {
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
}
