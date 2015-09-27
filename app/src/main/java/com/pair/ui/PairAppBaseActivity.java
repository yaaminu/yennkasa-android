package com.pair.ui;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import com.pair.Errors.ErrorCenter;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.messenger.PairAppClient;
import com.pair.util.NavigationManager;
import com.pair.util.UiHelpers;

/**
 * @author by Null-Pointer on 9/6/2015.
 */
public abstract class PairAppBaseActivity extends ActionBarActivity implements ErrorCenter.ErrorShower {
    protected final UserManager userManager = UserManager.getInstance();
    private boolean isUserVerified = false;

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
        NavigationManager.onStop(this);
        if (isUserVerified) {
            PairAppClient.markUserAsOffline(this);
        }
    }

    protected final User getCurrentUser() {
        return userManager.getCurrentUser();
    }

    protected final boolean isUserCLoggedIn() {
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
