package com.idea.ui;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import com.idea.Errors.ErrorCenter;
import com.idea.data.User;
import com.idea.data.UserManager;
import com.idea.messenger.PairAppClient;
import com.idea.util.NavigationManager;
import com.idea.util.UiHelpers;

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
