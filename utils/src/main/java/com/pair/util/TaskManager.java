package com.pair.util;

/**
 * Created by Null-Pointer on 9/27/2015.
 */
public class TaskManager {

    public static void executeOnMainThread(Runnable r) {
        AndroidExecutors.uiThread().execute(r);
    }

    public static void execute(Runnable r) {
        AndroidExecutors.threadPool().execute(r);
    }

}
