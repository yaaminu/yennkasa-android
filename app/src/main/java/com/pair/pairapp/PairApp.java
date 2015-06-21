package com.pair.pairapp;

import android.app.Application;

import com.pair.messenger.PairAppClient;
import com.pair.util.Config;
import com.pair.workers.ContactSyncService;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class PairApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        PairAppClient.start(this);
        ContactSyncService.start(this);
        Config.init(this);
    }
}
