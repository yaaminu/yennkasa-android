package com.pair.ui;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.pair.Config;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.util.L;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.File;

import io.realm.Realm;

/**
 * @author Null-Pointer on 8/31/2015.
 */
public class DPLoader {
    public static final String TAG = DPLoader.class.getSimpleName();
    private static final String DUMMY_URL = "dummyurl";


    public static RequestCreator load(String userId, String userDp) {
        Context context = Config.getApplicationContext();
        File dpFile = new File(Config.getAppProfilePicsBaseDir(), userDp);
        if (dpFile.exists()) {
            return Picasso.with(context).load(dpFile);
        } else {
            attemptToRecoverDp(userId, userDp, context); //async
            return Picasso.with(context).load(DUMMY_URL);
        }
    }

    private static void attemptToRecoverDp(final String userId, final String userDp, final Context context) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                String encoded = userDp;
                if (userDp.startsWith("http")) { //encode on demand
                    encoded = UserManager.getInstance().encodeDp(userDp);
                }
                File dpFile = new File(Config.getAppProfilePicsBaseDir(), encoded);
                //has the dp really changed? or just a refresh changed the literal dp string
                if (dpFile.exists()) {
                    Realm realm = User.Realm(context);
                    User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
                    realm.beginTransaction();
                    user.setDP(encoded);
                    realm.commitTransaction();
                    realm.close();
                } else {
                    UserManager.getInstance().refreshDp(userId, new DPLoaderCallback(dpFile));
                }
                return null;
            }
        }.execute();
    }

    private static class DPLoaderCallback implements UserManager.CallBack {
        private final File file;

        public DPLoaderCallback(File file) {
            this.file = file;
        }

        @Override
        public void done(Exception e) {
            if (e == null) {
                L.d(TAG, "changed/refreshed dp successfully");
                Picasso.with(Config.getApplicationContext()).invalidate(file);
            } else {
                Log.e(TAG, "dp change unsuccessful with reason: " + e.getMessage(), e.getCause());
            }
        }
    }
}
