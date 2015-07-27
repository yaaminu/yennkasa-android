package com.pair.pairapp;

import android.app.Application;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

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
        RealmHelper.runRealmOperation(this);
        Config.init(this);
        if (UserManager.getInstance().getMainUser() != null) {
            PairAppClient.start(this);
            ContactSyncService.start(this);
        }
    }
}
