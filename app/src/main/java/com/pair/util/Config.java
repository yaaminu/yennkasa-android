package com.pair.util;

import android.os.Build;

/**
 * Created by Null-Pointer on 5/26/2015.
 */
public class Config {

    private static final String HOST_REAL_SERVER = "http://192.168.43.42:3000";
    private static final String LOCAL_HOST_GENYMOTION = "http://10.0.3.2:3000";
    private static final String ENV_PROD = "prod";
    private static final String ENV_DEV = "dev";

    public static final String PAIRAPP_ENV = getEnvironment();
    public static final String PAIRAPP_ENDPOINT = getEndPoint();
    public static String auth = "kiiboda+=s3cr3t3";

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
