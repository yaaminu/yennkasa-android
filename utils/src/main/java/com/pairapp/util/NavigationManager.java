package com.pairapp.util;

import android.support.v4.app.FragmentActivity;

/**
 * this class tracks user navigation pattern. Activities that need to report their
 * state may use this class. Its is a contract that every activity report to this
 * class whenever it becomes visible to the user (typically) in {@link FragmentActivity#onResume()}
 * and also immediately they become invisible to the user typically in {@link FragmentActivity#onPause()}.
 * <p/>
 * The reports must be made on the main thread otherwise  {@code IllegalStateException} will be thrown
 * <p/>
 * It's important all activities follow this contract as some components will rely heavily on this
 * behaviour.
 * <p/>
 *
 * @author by Null-Pointer on 9/6/2015.
 */
public class NavigationManager {

    private static volatile FragmentActivity currentActivity;
    private static volatile States state = States.DESTROYED;

    /**
     * returns the current activity in this application.The returned activity may not be visible to the user or
     * there may not be an activity available at all, in that case a {@link NoActiveActivityException} is thrown.
     * <p/>
     * if one needs the returned activity(if there is any at all) to be an a given state, one must check using the
     * {@link #getCurrentActivityState()}.
     *
     * @return the current activity for this application
     * @throws NoActiveActivityException if there is no active Activity
     */
    public static FragmentActivity getCurrentActivity() throws NoActiveActivityException {
        if (currentActivity == null) {
            throw new NoActiveActivityException();
        }
        return currentActivity;
    }

    /**
     * returns the current state of the activity returned by {@link #getCurrentActivity()}.
     *
     * @return one of the values {@link com.pairapp.util.NavigationManager.States}
     * @throws NoActiveActivityException if there is no active activity
     * @see #getCurrentActivity()
     * @see com.pairapp.util.NavigationManager.States
     */
    public static States getCurrentActivityState() throws NoActiveActivityException {
        if (currentActivity == null) {
            return States.DESTROYED;
        }
        return state;
    }

    public static void onCreate(FragmentActivity newActivity) {
        ThreadUtils.ensureMain();
        currentActivity = newActivity;
        state = States.CREATED;
    }

    public static void onStart(FragmentActivity newActivity) {
        ThreadUtils.ensureMain();
        if (currentActivity != newActivity) {
            currentActivity = newActivity;
        }
        ensureSameActivityAndUpdateState(newActivity, States.STARTED);
    }

    public static void onResume(FragmentActivity newActivity) {
        ThreadUtils.ensureMain();
        if (currentActivity != newActivity) {
            currentActivity = newActivity;
        }
        ensureSameActivityAndUpdateState(newActivity, States.RESUMED);
    }

    public static void onPause(FragmentActivity activity) {
        ThreadUtils.ensureMain();
        if (currentActivity == null) {
            currentActivity = activity;
        }
        ensureSameActivityAndUpdateState(activity, States.PAUSED);
    }

    public static void onStop(FragmentActivity activity) {
        ThreadUtils.ensureMain();
        ensureSameActivityAndUpdateState(activity, States.STOPPED);
    }

    public static void onDestroy(FragmentActivity newActivity) {
        ThreadUtils.ensureMain();
        ensureSameActivityAndUpdateState(newActivity, States.DESTROYED);
        if (newActivity == currentActivity) {
            currentActivity = null;
        }
    }

    private static void ensureSameActivityAndUpdateState(FragmentActivity activity, States newState) {
        if (currentActivity == activity) {
            state = newState;
        }
    }

    public enum States {
        CREATED, STARTED, RESUMED, PAUSED, STOPPED, DESTROYED
    }

    /**
     * @author by Null-Pointer on 9/6/2015.
     */
    public static class NoActiveActivityException extends Exception {
        public NoActiveActivityException(Throwable cause) {
            super(cause);
        }

        public NoActiveActivityException(String message) {
            super(message);
        }

        public NoActiveActivityException() {
            super();
        }
    }
}
