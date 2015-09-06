package com.pair.ui;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.ActionBarActivity;

import com.pair.util.NavigationManager;

/**
 * @author by Null-Pointer on 9/6/2015.
 */
public abstract class PairAppBaseActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NavigationManager.onCreate(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        NavigationManager.onStart(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NavigationManager.onResume(this);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NavigationManager.onStop(this);
    }
}
