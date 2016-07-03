package com.pairapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.pairapp.Errors.ErrorCenter;
import com.pairapp.R;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.util.Event;
import com.pairapp.util.GcmUtils;
import com.pairapp.util.NavigationManager;
import com.pairapp.util.TaskManager;
import com.pairapp.util.UiHelpers;

import java.util.HashSet;
import java.util.Set;

import static com.pairapp.messenger.MessengerBus.OFFLINE;
import static com.pairapp.messenger.MessengerBus.ONLINE;
import static com.pairapp.messenger.MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS;
import static com.pairapp.messenger.MessengerBus.get;

/**
 * @author by Null-Pointer on 9/6/2015.
 */
@SuppressWarnings("deprecation")
public abstract class PairAppBaseActivity extends ActionBarActivity implements ErrorCenter.ErrorShower {
    @SuppressWarnings("unused")
    private static final String TAG = PairAppBaseActivity.class.getSimpleName();
    protected final UserManager userManager = UserManager.getInstance();
    private boolean isUserVerified = false;
    private static volatile boolean promptShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isUserVerified = userManager.isUserVerified();
        if (isUserVerified) {
            get(PAIRAPP_CLIENT_POSTABLE_BUS).postSticky(Event.createSticky(ONLINE, null, this));
        }
        NavigationManager.onCreate(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        NavigationManager.onStart(this);
        ErrorCenter.registerErrorShower(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NavigationManager.onResume(this);
        ErrorCenter.showPendingError();
        if (!promptShown) {
            TaskManager.executeNow(new Runnable() {
                @Override
                public void run() {
                    showMessage();
                }
            }, false);
        }
    }

    protected void showMessage() {
        promptShown = true;
        int message = 0;
        if (!GcmUtils.hasGcm()) {
            message = R.string.no_gcm_error_message;
        } else if (GcmUtils.gcmUpdateRequired()) {
            message = R.string.gcm_update_required_prompt;
        }
        if (message != 0) {
            final int tmp = message;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    UiHelpers.showStopAnnoyingMeDialog(PairAppBaseActivity.this,
                            "gcmUnavialble" + TAG, R.string.stop_annoying_me, tmp, R.string.i_know, android.R.string.cancel, null, null);
                }
            });
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //quick fix for menu items appearing twice on the toolbar. this is a bug in the library am using
        int menuSize = menu.size();
        Set<Integer> items = new HashSet<>(menuSize + 2);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!items.add(item.getItemId())) {
                item.setVisible(false);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onPause() {
        super.onPause();
        NavigationManager.onPause(this);
    }


    @Override
    protected void onStop() {
        super.onStop();
        NavigationManager.onStop(this);
        ErrorCenter.unRegisterErrorShower(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NavigationManager.onDestroy(this);
        if (isUserVerified) {
            get(PAIRAPP_CLIENT_POSTABLE_BUS).postSticky(Event.createSticky(OFFLINE, null, this));
        }
    }

    protected final User getCurrentUser() {
        return userManager.getCurrentUser();
    }

    protected final boolean isUserLoggedIn() {
        return userManager.isUserLoggedIn();
    }

    protected final boolean isUserVerified() {
        return isUserVerified;
    }

    protected final String getMainUserId() {
        return UserManager.getMainUserId();
    }

    @Override
    public void showError(ErrorCenter.Error error) {
        UiHelpers.showErrorDialog(this, error.message);
    }

    @Override
    public void disMissError(String errorId) {
        //do nothing
    }
}
