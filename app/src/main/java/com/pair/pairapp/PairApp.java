package com.pair.pairapp;

import android.app.Application;

import com.pair.messenger.PairAppClient;
import com.pair.util.Config;
import com.pair.util.UserManager;
import com.pair.workers.ContactSyncService;
import com.pair.workers.RealmHelper;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class PairApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Config.init(this);
        if (UserManager.getInstance().isUserVerified()) {
            RealmHelper.runRealmOperation(this);
            PairAppClient.start(this);
            ContactSyncService.start(this);
        }
    }
}
