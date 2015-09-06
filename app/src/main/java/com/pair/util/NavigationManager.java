package com.pair.util;

import android.support.v4.app.FragmentActivity;

import com.pair.Errors.ErrorCenter;
import com.pair.Errors.NoVisibleActivityException;

/**
 * this class tracks user navigation pattern. Activities that need to report their
 * state may use this class. Its is a contract that every activity report to this
 * class whenever the become visible to the user (typically) in {@link FragmentActivity#onResume()}
 * and also immediately they become invisible to the user typically in {@link FragmentActivity#onPause()}.
 * <p/>
 * The reports must be made on the main thread otherwise the class will {@code throw IllegalStateException}
 * <p/>
 * Its important all activities follow this contract as some components will rely heavily on this
 * behaviour
 *
 * @author by Null-Pointer on 9/6/2015.
 */
public class NavigationManager {

    private static FragmentActivity currentActivity;
    private static volatile States state = States.DESTROYED;

    public static FragmentActivity getCurrentActivity() throws NoVisibleActivityException {
        if (currentActivity == null) {
            throw new NoVisibleActivityException();
        }
        return currentActivity;
    }

    public static States getCurrentActivityState() {
        return state;
    }

    public static void onCreate(FragmentActivity newActivity) {
        ThreadUtils.ensureMain();
        currentActivity = newActivity;
        state = States.CREATED;
    }

    public static void onStart(FragmentActivity newActivity) {
        ThreadUtils.ensureMain();
        ensureSameActivityAndUpdateState(newActivity, States.STARTED);
    }

    public static void onResume(FragmentActivity newActivity) {
        ThreadUtils.ensureMain();
        ensureSameActivityAndUpdateState(newActivity, States.RESUMED);
        ErrorCenter.showPendingError();
    }

    public static void onPause(FragmentActivity activity) {
        ThreadUtils.ensureMain();
        ensureSameActivityAndUpdateState(activity, States.PAUSED);
    }

    public static void onStop(FragmentActivity activity) {
        ThreadUtils.ensureMain();
        ensureSameActivityAndUpdateState(activity, States.STOPPED);
    }

    public static void onDestroy(FragmentActivity newActivity) {
        ThreadUtils.ensureMain();
        ensureSameActivityAndUpdateState(newActivity, States.DESTROYED);
        currentActivity = null;
    }

    private static void ensureSameActivityAndUpdateState(FragmentActivity activity, States newState) {
        if (currentActivity == activity) {
            state = newState;
        }
    }

    public enum States {
        CREATED, STARTED, RESUMED, PAUSED, STOPPED, DESTROYED
    }
}
