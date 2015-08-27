package com.pair;

import android.app.Application;

import com.pair.parse_client.ParseClient;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class PairApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Config.init(this);
        ParseClient.init(this);
        // ParseObject object = new ParseObject("TestObject");
        // object.put("foo", "bar");
        // object.saveInBackground();
//        if (UserManager.getInstance().isUserVerified()) {
//            PairAppClient.start(this);
//            ContactSyncService.start(this);
//        }
    }

}
