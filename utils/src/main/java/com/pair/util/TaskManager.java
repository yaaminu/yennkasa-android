package com.pair.util;

import android.app.Application;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author by Null-Pointer on 9/27/2015.
 */
public class TaskManager {

    public static final String TAG = TaskManager.class.getSimpleName();
    private static final AtomicBoolean initialised = new AtomicBoolean(false);

    public static void init(Application application){
         if(!initialised.getAndSet(true)){
             PLog.w(TAG,"initialising %s",TAG);

             // TODO: 9/28/2015 look up for all pending tasks like dp change
         }
    }
    public static void executeOnMainThread(Runnable r) {
        ensureInitialised();
        AndroidExecutors.uiThread().execute(r);
    }

    public static void execute(Runnable r) {
        ensureInitialised();
        AndroidExecutors.threadPool().execute(r);
    }

    private static void ensureInitialised(){
        if(!initialised.get()){
            throw new IllegalArgumentException("did you forget to init()?");
        }
    }

}
