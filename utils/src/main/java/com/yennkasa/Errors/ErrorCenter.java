package com.yennkasa.Errors;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.yennkasa.util.Config;
import com.yennkasa.util.NavigationManager;
import com.yennkasa.util.R;
import com.yennkasa.util.TaskManager;
import com.yennkasa.util.ThreadUtils;

import java.lang.ref.WeakReference;

/**
 * a utility class aimed at centralising error reporting to the user in the future this class
 * may be used to even report errors to our backend.
 * Components who report errors in callbacks are advised to do so through this class.
 * <p/>
 * it smartly queues errors and waits for a new activity to be visible before showing the error to the user. this behaviour relies
 * on {@link com.yennkasa.util.NavigationManager} to know the state of the current activity.
 *
 * @author by Null-Pointer on 9/6/2015.
 */
public class ErrorCenter {

    public static final int INDEFINITE = -1;
    private static final String TAG = ErrorCenter.class.getSimpleName();
    private static final long DEFAULT_TIMEOUT = 1000L;
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
        final Error tmp = new Error(errorId, errorMessage, timeout);
        try {
            currentActivityState = NavigationManager.getCurrentActivityState();
            if (!currentActivityState.equals(NavigationManager.States.DESTROYED) || !currentActivityState.equals(NavigationManager.States.STOPPED)) {
                final ErrorShower errorShower = ErrorCenter.errorShower.get();
                if (errorShower != null) {
                    if (ThreadUtils.isMainThread()) {
                        errorShower.showError(tmp);
                    } else {
                        TaskManager.executeOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                errorShower.showError(tmp);
                            }
                        });
                    }
                    return;
                }
            }
        } catch (NavigationManager.NoActiveActivityException e) {
            Log.d(TAG, "no visible activity. waiting till an activity shows up");
        }
        waitingError = tmp;
    }

    public static synchronized void reportError(String id, String errorMessage, Intent action) {
        // Intent intent = new Intent("com.idea.yennkasa.report");
        // intent.putExtra("message", errorMessage);
        Context applicationContext = Config.getApplicationContext();
        PendingIntent pendingIntent = null;
        if (action != null) {
            Class<?> clazz = action.getClass();
            if (clazz.getSuperclass().equals(Activity.class)) {
                pendingIntent = PendingIntent.getActivity(applicationContext, id.hashCode(), action, PendingIntent.FLAG_UPDATE_CURRENT);
            } else if (clazz.getSuperclass()/*null pointer ex impossible*/.equals(Service.class)) {
                pendingIntent = PendingIntent.getService(applicationContext, id.hashCode(), action, PendingIntent.FLAG_UPDATE_CURRENT);
            } else if (clazz.getSuperclass()/*null pointer ex impossible*/.equals(BroadcastReceiver.class)) {
                pendingIntent = PendingIntent.getBroadcast(applicationContext, id.hashCode(), action, PendingIntent.FLAG_UPDATE_CURRENT);
            } else if (clazz.getSuperclass()/*null pointer ex impossible*/.equals(WakefulBroadcastReceiver.class)) {
                pendingIntent = PendingIntent.getBroadcast(applicationContext, id.hashCode(), action, PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                throw new IllegalArgumentException("unresolvable component type");
            }
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(applicationContext)
                .setContentTitle(applicationContext.getString(R.string.error))
                .setContentText(errorMessage)
                .setAutoCancel(true)
                .setSmallIcon(android.R.drawable.stat_notify_error);
        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent);
        }

        NotificationManagerCompat manager = NotificationManagerCompat.from(applicationContext);// getSystemService(NOTIFICATION_SERVICE));
        manager.notify(id, 100000111, builder.build());
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
        } else if (errorShower != null) {
            ErrorShower shower = ErrorCenter.errorShower.get();
            if (shower != null)
                shower.disMissError(errorId);
        }
    }

    /**
     * register a new error shower. We don't hold a strong references so
     * you may not pass an anonymous instance.
     *
     * @param errorShower the error shower to register, may not be null
     * @throws IllegalArgumentException if errorShow is null
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
            }
            waitingError = null;
            return;
        }
        Log.d(TAG, "no error to show either they have time out or there is none at all");
    }

    public static void reportError(String id, String message, ReportStyle style, int indefinite) {
        Error tmp = new Error(id, message, indefinite, style);
        if (errorShower != null && errorShower.get() != null) {
            errorShower.get().showError(tmp);
        } else {
            waitingError = tmp;
        }
    }

    public interface ErrorShower {
        void showError(Error error);

        void disMissError(String errorId);
    }

    public static class Error {
        public final String message, id;
        public final long timeout;
        public final ReportStyle style;

        private Error(String id, String message, long timeout) {
            this(id, message, timeout, ReportStyle.DIALOG);
        }

        private Error(String id, String message, long timeout, ReportStyle style) {
            this.id = id;
            this.message = message;
            this.timeout = System.currentTimeMillis() + timeout;
            this.style = style;
        }
    }

    public enum ReportStyle {
        DIALOG, NOTIFICATION, STICKY, DIALOG_NOT
    }
}
