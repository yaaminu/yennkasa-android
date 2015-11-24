package com.idea.util;

import android.app.Application;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author by Null-Pointer on 9/27/2015.
 */
public class TaskManager {

    public static final String TAG = TaskManager.class.getSimpleName();
    private static final AtomicBoolean initialised = new AtomicBoolean(false);

    private static ThreadFactory factory = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new SmartThread(r);
        }
    };
    private static ExecutorService cachedThreadPool = Executors.newCachedThreadPool(factory);

    public static void init(Application application) {
        if (!initialised.getAndSet(true)) {
            PLog.w(TAG, "initialising %s", TAG);
            // TODO: 9/28/2015 look up for all pending tasks like dp change
            final Timer timer = new Timer("cleanup Timer", true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        File file = Config.getTempDir();
                        if (file.exists()) {
                            org.apache.commons.io.FileUtils.cleanDirectory(Config.getTempDir());
                        }
                    } catch (IOException ignored) {

                    } finally {
                        timer.cancel();
                    }
                }
            }, 1); //immediately

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


    private static int expressQueueLength = 0;
    private static final int maxLength = Runtime.getRuntime().availableProcessors() * 15;

    public static boolean executeNow(Runnable runnable) {
        return executeNow(runnable, false);
    }

    public static boolean executeNow(Runnable runnable, boolean runOnExecutorIfFailed) {
        ensureInitialised();
        synchronized (expressQueueLock) {
            if (expressExecutionQueueTooLong()) {
                if (runOnExecutorIfFailed) {
                    execute(runnable);
                }
                return false;
            }
            expressQueueLength++;
        }
        cachedThreadPool.execute(runnable);
        return true;
    }

    private static boolean expressExecutionQueueTooLong() {
        return expressQueueLength >= maxLength;
    }

    private static void ensureInitialised() {
        if (!initialised.get()) {
            throw new IllegalArgumentException("did you forget to init()?");
        }
    }

    private static class SmartThread extends Thread {
        private final Runnable target;

        private SmartThread(Runnable r) {
            target = r;
        }

        @Override
        public void run() {
            target.run();
            synchronized (expressQueueLock) {
                expressQueueLength--;
            }
        }
    }

    private static final Object expressQueueLock = new Object();
}
