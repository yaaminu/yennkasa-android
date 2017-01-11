package com.yennkasa.data;

import android.annotation.SuppressLint;

import com.path.android.jobqueue.Params;
import com.yennkasa.util.Config;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.FileUtils;
import com.yennkasa.util.PLog;
import com.yennkasa.util.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by yaaminu on 1/11/17.
 */
class PublicKeyRefreshJob extends Task {
    private static final String TAG = "PublicKeyRefreshJob";
    public static final String JOB_GROUP_PREFIX = "publicKeyRefresh";
    private final String userId;

    public PublicKeyRefreshJob() {
        this.userId = "";
    }

    public PublicKeyRefreshJob(Params params, String userId) {
        super(params);
        this.userId = userId;

    }

    public static PublicKeyRefreshJob create(String userId) {
        Params params = new Params(1000);
        params.setGroupId(JOB_GROUP_PREFIX + userId);
        params.setRequiresNetwork(true);
        params.setPersistent(false);
        return new PublicKeyRefreshJob(params, userId);
    }

    @Override
    protected JSONObject toJSON() {
        try {
            return new JSONObject().put(User.FIELD_ID, userId);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Task fromJSON(JSONObject jsonObject) {
        try {
            return PublicKeyRefreshJob.create(jsonObject.getString(User.FIELD_ID));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onAdded() {
        PLog.d(TAG, "added job %s", this);
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public void onRun() throws Throwable {
        String prefKey = FileUtils.sha1("user.public.key.rsa.last.updated" + this.userId);
        long lastRefreshed = Config.getApplicationWidePrefs()
                .getLong(prefKey, 0);
        if (System.currentTimeMillis() - lastRefreshed < TimeUnit.MINUTES.toMillis(5)) {
            PLog.d(TAG, "refreshed public key for user %s not too long ago", this.userId);
            PLog.d(TAG, "we last refreshed @ %s", new Date(lastRefreshed));
            return;
        }
        UserManager userManager = UserManager.getInstance();
        String previousPublicKey = userManager.localPublicKeyForUser(this.userId);
        String newPublicKey = userManager.publicKeyForUser(this.userId, true);
        if (newPublicKey != null && !newPublicKey.equals(previousPublicKey)) {
            Config.getApplicationWidePrefs().edit().putLong(prefKey, System.currentTimeMillis()).commit();
            PLog.d(TAG, "public key for %s changed", this.userId);
            PLog.d(TAG, "public key was %s", previousPublicKey);
            PLog.d(TAG, "new public key is %s", newPublicKey);
            //new key so re-send all undelivered messages
            EventBus.getDefault().post(Event.create(UserManager.ACTION_SEND_MESSAGE, null, this.userId));
        } else {
            PLog.d(TAG, "public key not updated.");
            PLog.d(TAG, "it's  because %s", previousPublicKey != null
                    && newPublicKey != null ? "it has not changed" : "we failed to fetch new key from the server");
        }
    }

    @Override
    protected int getRetryLimit() {
        return 3;
    }

    @Override
    protected void onCancel() {
        PLog.d(TAG, "canceled job %s", this);
    }

    @Override
    public String toString() {
        return "PublicKeyRefreshJob{" +
                "userId='" + userId + '\'' +
                '}';
    }
}
