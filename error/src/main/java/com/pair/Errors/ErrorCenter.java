package com.pair.Errors;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.pair.util.NavigationManager;
import com.pair.util.ThreadUtils;

import java.lang.ref.WeakReference;

/**
 * a utility class aimed at centralising error reporting to the user in the future this class
 * may be used to even report errors to our backend.
 * Components who report errors in callbacks are advised to do so through this class.
 * <p/>
 * it smartly queues errors and waits for a new activity to be visible before showing the error to the user. this behaviour relies
 * on {@link com.pair.util.NavigationManager} to know the state of the current activity.
 *
 * @author by Null-Pointer on 9/6/2015.
 */
public class ErrorCenter {

    public static final int INDEFINITE = -1;
    private static final String TAG = ErrorCenter.class.getSimpleName();
    private static final long DEFAULT_TIMEOUT = 1000L;
    private static final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private static Error waitingError;
    private static WeakReference<ErrorShower> errorShower = new WeakReference<>(null);//to free us from null-checks

    /**
     * @param errorId      a unique identifier for this error. this is useful if a component needs to update
     *                     a particular error or cancel it at all.
     *                     which means reporting an error with the same id will replace the existing key.
     * @param errorMessage the message to be shown to the user
     * @see {@link #reportError(String, String, long)} if you want to timeout the error.
     */
    public static void reportError(String errorId, String errorMessage) {
        reportError(errorId, errorMessage, INDEFINITE);
    }

    /**
     * @param errorId      a unique identifier for this error. this is useful if a component needs to update
     *                     a particular error or cancel it at all..
     * @param errorMessage the message to be shown to the user
     * @param timeout      when the message should be considered stale. can't be less than 10000 (10 seconds)
     *                     the timeout is only relevant when there is no visible activity. if there is one it will
     *                     be ignored
     * @see {@link #reportError(String, String)} if you don't want to timeout the error.
     */
    public static synchronized void reportError(String errorId, final String errorMessage, long timeout) {
        if (timeout != INDEFINITE) {
            timeout = Math.max(timeout, DEFAULT_TIMEOUT); //time out cannot be less than DefaultTimeout
        }
        NavigationManager.States currentActivityState = null;
        try {
            currentActivityState = NavigationManager.getCurrentActivityState();
            if (!currentActivityState.equals(NavigationManager.States.DESTROYED) || !currentActivityState.equals(NavigationManager.States.STOPPED)) {
                final ErrorShower errorShower = ErrorCenter.errorShower.get();
                if (errorShower != null) {
                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            errorShower.showError(errorMessage);
                        }
                    });
                    return;
                }
            }
        } catch (NavigationManager.NoActiveActivityException e) {
            Log.d(TAG, "no visible activity. waiting till an activity shows up");
        }
        waitingError = new Error(errorId, errorMessage, timeout);
    }

    /**
     * cancels a pending error report
     *
     * @param errorId the id of the report
     */
    @SuppressWarnings("unused")
    public static void cancel(String errorId) {
        if (waitingError != null && waitingError.id.equals(errorId)) {
            Log.d(TAG, "cancelling error with id: " + errorId);
            waitingError = null;
        }
    }

    /**
     * register a new error shower. We don't hold a strong reference to the object so
     * you may not pass an anonymous instance.
     *
     * @param errorShower the error shower to register, may not be null
     * @throws IllegalArgumentException if null is passed
     */
    public static synchronized void registerErrorShower(ErrorShower errorShower) {
        if (errorShower == null) throw new IllegalArgumentException("null!");
        ErrorCenter.errorShower = new WeakReference<>(errorShower);
        showPendingError();
    }

    /**
     * unregistered an error shower.
     *
     * @param errorShower the error shower to unregister
     */
    public static synchronized void unRegisterErrorShower(ErrorShower errorShower) {
        if (ErrorCenter.errorShower != null) {
            ErrorShower ourErrorShower = ErrorCenter.errorShower.get();
            if (ourErrorShower != null && ourErrorShower == errorShower) {
                ErrorCenter.errorShower.clear();
                ErrorCenter.errorShower = null;
            }
        }
    }

    /**
     * a hook for components that feel there may be a pending error.
     * for e.g. an {@link android.app.Activity} can call this method whenever it comes to
     * the screen typically in its {@link Activity#onResume()}) callback.
     * the {@link NavigationManager} calls this method automatically anytime an {@link Activity}
     * reports itself as resumed.
     *
     * @throws IllegalStateException when  called from a thread other than the main Thread.
     */
    public static void showPendingError() {
        ThreadUtils.ensureMain();
        if (waitingError != null) {
            if (waitingError.timeout == INDEFINITE || waitingError.timeout <= System.currentTimeMillis()) {
                reportError(waitingError.id, waitingError.message, waitingError.timeout);
                return;
            }
        }
        Log.d(TAG, "no error to show either they have time out or there is none at all");
    }

    public interface ErrorShower {
        void showError(String errorMessage);
    }

    public static class Error {
        String message, id;
        long timeout;

        private Error(String id, String message, long timeout) {
            this.id = id;
            this.message = message;
            this.timeout = System.currentTimeMillis() + timeout;
        }
    }
}
