package com.pair.ui;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import com.pair.Errors.ErrorCenter;
import com.pair.data.UserManager;
import com.pair.messenger.PairAppClient;
import com.pair.util.NavigationManager;
import com.pair.util.UiHelpers;

/**
 * @author by Null-Pointer on 9/6/2015.
 */
public abstract class PairAppBaseActivity extends ActionBarActivity implements ErrorCenter.ErrorShower {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NavigationManager.onCreate(this);
        if (UserManager.getInstance().isUserVerified()) {
            PairAppClient.markUserAsOnline(this);
        }
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
        if (UserManager.getInstance().isUserVerified()) {
            PairAppClient.markUserAsOffline(this);
        }
    }

    @Override
    public void showError(String errorMessage) {
        UiHelpers.showErrorDialog(this, errorMessage);
    }
}
