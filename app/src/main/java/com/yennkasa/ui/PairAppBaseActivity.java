package com.yennkasa.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.yennkasa.Errors.ErrorCenter;
import com.yennkasa.R;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.util.Config;
import com.yennkasa.util.Event;
import com.yennkasa.util.GcmUtils;
import com.yennkasa.util.NavigationManager;
import com.yennkasa.util.TaskManager;
import com.yennkasa.util.UiHelpers;

import java.util.HashSet;
import java.util.Set;

import io.realm.Realm;

import static com.yennkasa.messenger.MessengerBus.OFFLINE;
import static com.yennkasa.messenger.MessengerBus.ONLINE;
import static com.yennkasa.messenger.MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS;
import static com.yennkasa.messenger.MessengerBus.get;

/**
 * @author by Null-Pointer on 9/6/2015.
 */
@SuppressWarnings("deprecation")
public abstract class PairAppBaseActivity extends ActionBarActivity implements ErrorCenter.ErrorShower {
    @SuppressWarnings("unused")
    private static final String TAG = PairAppBaseActivity.class.getSimpleName();
    protected final UserManager userManager = UserManager.getInstance();
    private static boolean isUserVerified = false;
    private static volatile boolean promptShown = false;
    protected Realm userRealm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userRealm = User.Realm(this);
        if (!isUserVerified) {
            isUserVerified = userManager.isUserVerified(userRealm);
        }
        NavigationManager.onCreate(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        NavigationManager.onStart(this);
        ErrorCenter.registerErrorShower(this);
        if (isUserVerified) {
            TaskManager.executeNow(new Runnable() {
                @Override
                public void run() {
                    get(PAIRAPP_CLIENT_POSTABLE_BUS).postSticky(Event.createSticky(ONLINE, null, PairAppBaseActivity.this));
                }
            }, true);
        }
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
        if (isUserVerified) {
            Config.appOpen(true);
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
        if (isUserVerified) {
            TaskManager.executeNow(new Runnable() {
                @Override
                public void run() {
                    get(PAIRAPP_CLIENT_POSTABLE_BUS).postSticky(Event.createSticky(OFFLINE, null, PairAppBaseActivity.this));
                }
            }, false);
        }
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
        userRealm.close();
    }

    protected final User getCurrentUser() {
        return userManager.getCurrentUser(userRealm);
    }

    protected final boolean isUserLoggedIn() {
        return userManager.isUserLoggedIn(userRealm);
    }

    protected final boolean isUserVerified() {
        return isUserVerified;
    }

    protected final String getMainUserId() {
        return UserManager.getMainUserId(userRealm);
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
