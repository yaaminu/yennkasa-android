package com.pair.pairapp;

import android.app.Application;

import com.pair.util.Config;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class PairApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Config.init(this);
    }
}
