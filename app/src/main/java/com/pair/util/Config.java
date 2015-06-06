package com.pair.util;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * Created by Null-Pointer on 5/26/2015.
 */
public class Config {

    public static final String TAG = Config.class.getSimpleName();
    private static final String HOST_REAL_SERVER = "http://192.168.43.42:3000";
    private static final String LOCAL_HOST_GENYMOTION = "http://10.0.3.2:3000";
    private static final String ENV_PROD = "prod";
    private static Application application;
    private static final String ENV_DEV = "dev";
    public static final String APP_PREFS = "prefs";

    public static final String PAIRAPP_ENV = getEnvironment();
    public static final String PAIRAPP_ENDPOINT = getEndPoint();
    public static String auth = "kiiboda+=s3cr3t3";

    public static void init(Application pairApp){
        Config.application = pairApp;
    }
    public static Context getApplicationContext(){
        if (application == null) {
            Log.w(TAG,"calling getApplicationContext when init has not be called");
            throw new IllegalStateException("applicationContext is null. Did you forget to call Config.init()?");
        }
        return application.getApplicationContext();
    }

    public static Application getApplication(){
        if (application == null) {
            Log.w(TAG,"calling getApplication when init has not be called");
            throw new IllegalStateException("application is null. Did you forget to call Config.init()?");
        }
        return  Config.application;
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
            // FIXME replace this with real url
            return HOST_REAL_SERVER;
        }
    }
}
