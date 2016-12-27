package com.yennkasa.messenger;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.yennkasa.BuildConfig;
import com.yennkasa.R;
import com.yennkasa.util.Config;
import com.yennkasa.util.PLog;
import com.yennkasa.util.TaskManager;
import com.yennkasa.util.ThreadUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Semaphore;

/**
 * @author aminu on 7/1/2016.
 */
class UpdateHelper {
    private static final String TAG = UpdateHelper.class.getSimpleName();
    private final Semaphore updateLock = new Semaphore(1, true);
    public static final String UPDATE_KEY = UpdateHelper.class.getName() + "updateKey";

    public void notifyUpdateAvailable(JSONObject data1) throws JSONException {
        ThreadUtils.ensureNotMain();
        try {
            updateLock.acquire();
            final String version = data1.getString(PairAppClient.VERSION);
            final int versionCode = data1.getInt("versionCode");
            if (versionCode > BuildConfig.VERSION_CODE) {
                PLog.i(TAG, "update available, latest version: %s", version);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(data1.getString("link")));
                final Context applicationContext = Config.getApplicationContext();
                Notification notification = new NotificationCompat.Builder(applicationContext)
                        .setContentTitle(applicationContext.getString(R.string.update_available))
                        .setContentIntent(PendingIntent.getActivity(applicationContext, 1003, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                        .setSubText(applicationContext.getString(R.string.download_update))
                        .setSmallIcon(R.drawable.ic_stat_icon).build();
                NotificationManagerCompat manager = NotificationManagerCompat.from(applicationContext);// getSystemService(NOTIFICATION_SERVICE));
                manager.notify("update" + TAG, PairAppClient.notId, notification);
                SharedPreferences preferences = Config.getApplicationWidePrefs();
                final String savedUpdate = preferences.getString(UPDATE_KEY, null);
                if (savedUpdate != null) {
                    JSONObject object = new JSONObject(savedUpdate);
                    if (object.optInt("versionCode", versionCode) >= versionCode) {
                        PLog.d(TAG, "push for update arrived late");
                        return;
                    }
                }
                preferences.edit().putString(UPDATE_KEY, data1.toString()).apply();
            } else {
                PLog.d(TAG, "client up to date");
            }
        } catch (InterruptedException e) {
            PLog.d(TAG, "itterupted while wating to acquire update lock");
        } finally {
            updateLock.release();
        }
    }


    public void checkUpdates() {
        if (!updateLock.tryAcquire()) {
            PLog.d(TAG, "update lock held, deferring update check from prefs");
            return;
        }
        try {
            final SharedPreferences applicationWidePrefs = Config.getApplicationWidePrefs();
            String updateJson = applicationWidePrefs.getString(UPDATE_KEY, null);
            if (updateJson != null) {
                try {
                    final JSONObject object = new JSONObject(updateJson);
                    int versionCode = object.getInt("versionCode");
                    if (versionCode > BuildConfig.VERSION_CODE) {
                        TaskManager.execute(new Runnable() {
                            public void run() {
                                try {
                                    notifyUpdateAvailable(object);
                                } catch (JSONException e) {
                                    PLog.d(TAG, "error while trying to deserialize update data");
                                    applicationWidePrefs.edit().remove(UPDATE_KEY).apply();
                                }
                            }
                        }, false);
                    } else {
                        applicationWidePrefs.edit().remove(UPDATE_KEY).apply();
                    }
                } catch (JSONException e) {
                    PLog.d(TAG, "error while trying to deserialize update data");
                    applicationWidePrefs.edit().remove(UPDATE_KEY).apply();
                }
            }
        } finally {
            updateLock.release();
        }
    }
}
