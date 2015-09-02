package com.pair;

import android.app.Application;

import com.pair.data.UserManager;
import com.pair.messenger.PairAppClient;
import com.pair.parse_client.ParseClient;
import com.pair.workers.ContactSyncService;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class PairApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Config.init(this);
        ParseClient.init(this);
        if (UserManager.getInstance().isUserVerified()) {
            PairAppClient.start(this);
            ContactSyncService.start(this);
        }
    }

}
