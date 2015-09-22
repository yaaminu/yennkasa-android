package com.pair.ui;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.pair.data.UserManager;
import com.pair.util.Config;
import com.pair.util.L;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Null-Pointer on 8/31/2015.
 */
public class DPLoader {
    public static final String TAG = DPLoader.class.getSimpleName();
    private static final String DUMMY_URL = "dummyurl";

    private static final Map<String, String> refreshing = new HashMap<>();

    public static RequestCreator load(Context context,String userId, String userDp) {
        File dpFile = new File(Config.getAppProfilePicsBaseDir(), userDp);
        if (dpFile.exists()) {
            return Picasso.with(context).load(dpFile);
        } else {
            attemptToRecoverDp(userId, userDp); //async
            return Picasso.with(context).load(DUMMY_URL);
        }
    }

    private static void attemptToRecoverDp(final String userId, final String userDp) {
        synchronized (refreshing) {
            if (refreshing.containsKey(userId)) {
                return;
            }
            refreshing.put(userId,userDp);
        }
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                UserManager.getInstance().refreshDp(userId, new DPLoaderCallback(userId));
                return null;
            }
        }.execute();
    }

    private static class DPLoaderCallback implements UserManager.CallBack {
        String userId;

        public DPLoaderCallback(String userId) {
            this.userId = userId;
        }

        @Override
        public void done(Exception e) {
            synchronized (refreshing) {
                refreshing.remove(userId);
            }
            if (e == null) {
                L.d(TAG, "successfully changed/refreshed dp of user with id: "+ userId);
            } else {
                Log.e(TAG, "dp change unsuccessful with reason: " + e.getMessage());
            }
        }
    }
}
