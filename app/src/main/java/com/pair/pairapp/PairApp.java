package com.pair.pairapp;

import android.app.Application;

import com.pair.data.UserManager;
import com.pair.messenger.PairAppClient;
import com.pair.workers.ContactSyncService;
import com.parse.Parse;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class PairApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Config.init(this);
        Parse.enableLocalDatastore(this);
        Parse.initialize(this, "RcCxnXwO1mpkSNrU9u4zMtxQac4uabLNIFa662ZY", "f1ad1Vfjisr7mVBDSeoFO1DobD6OaLkggHvT2Nk4");
        if (UserManager.getInstance().isUserVerified()) {
            PairAppClient.start(this);
            ContactSyncService.start(this);
        }
    }
}
