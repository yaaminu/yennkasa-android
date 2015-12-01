package com.idea.ui;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.idea.Errors.ErrorCenter;
import com.idea.data.User;
import com.idea.data.UserManager;
import com.idea.messenger.PairAppClient;
import com.idea.pairapp.R;
import com.idea.util.GcmUtils;
import com.idea.util.NavigationManager;
import com.idea.util.UiHelpers;

import java.util.HashSet;
import java.util.Set;

/**
 * @author by Null-Pointer on 9/6/2015.
 */
@SuppressWarnings("deprecation")
public abstract class PairAppBaseActivity extends ActionBarActivity implements ErrorCenter.ErrorShower {
    @SuppressWarnings("unused")
    private static final String TAG = PairAppBaseActivity.class.getSimpleName();
    protected final UserManager userManager = UserManager.getInstance();
    private boolean isUserVerified = false;
    private static boolean promptShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isUserVerified = userManager.isUserVerified();
        if (isUserVerified) {
            PairAppClient.markUserAsOnline(this);
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
            promptShown = true;
            if (!GcmUtils.hasGcm()) {
                UiHelpers.showStopAnnoyingMeDialog(this, "gcmUnavialble" + TAG, R.string.stop_annoying_me, R.string.no_gcm_error_message, R.string.i_know, android.R.string.cancel, null, null);
            } else if (GcmUtils.gcmUpdateRequired()) {
                UiHelpers.showStopAnnoyingMeDialog(this, "gcmUnavialble" + TAG, R.string.stop_annoying_me, R.string.gcm_update_required_prompt, R.string.i_know, android.R.string.cancel, null, null);
            }
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
    protected void onDestroy() {
        super.onDestroy();
        NavigationManager.onDestroy(this);
        if (isUserVerified) {
            PairAppClient.markUserAsOffline(this);
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
    public final void showError(String errorMessage) {
        UiHelpers.showErrorDialog(this, errorMessage);
    }

}
