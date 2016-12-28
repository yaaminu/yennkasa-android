package com.yennkasa.data;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import com.yennkasa.util.Config;

import io.realm.Realm;


/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 */
public class Worker extends IntentService {

    static final String REFRESH_USER = "refreshUser";
    static final String CHANGE_DP = "changeDp";
    static final String REFRESH_GROUPS = "refreshGroups";

    public Worker() {
        super("Worker");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            Realm realm = User.Realm(this);
            try {
                final String action = intent.getAction();
                if (REFRESH_USER.equals(action)) {
                    final String userId = intent.getStringExtra(User.FIELD_ID);
                    UserManager.getInstance().doRefreshUserDetails(userId);
                } else if (CHANGE_DP.equals(action)) {
                    final String userId = intent.getStringExtra(User.FIELD_ID);
                    final String dp = intent.getStringExtra(User.FIELD_DP);
                    UserManager.getInstance().completeDpChange(realm, userId, dp);
                } else if (REFRESH_GROUPS.equals(action)) {
                    UserManager.getInstance().doRefreshGroups(realm);
                }
            } finally {
                realm.close();
            }
        }
    }

    static void refreshUser(String userId) {
        Context context = Config.getApplicationContext();
        Intent intent = new Intent(context, Worker.class);
        intent.setAction(REFRESH_USER);
        intent.putExtra(User.FIELD_ID, userId);
        context.startService(intent);
    }

    static void changeDp(String userId, String dp) {
        Context context = Config.getApplicationContext();
        Intent intent = new Intent(context, Worker.class);
        intent.setAction(CHANGE_DP);
        intent.putExtra(User.FIELD_ID, userId);
        intent.putExtra(User.FIELD_DP, dp);
        context.startService(intent);
    }

    public static void getGroups() {
        Context context = Config.getApplicationContext();
        Intent intent = new Intent(context, Worker.class);
        intent.setAction(REFRESH_GROUPS);
    }
}
