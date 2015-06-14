package com.pair.pairapp;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import com.pair.pairapp.ui.LoginFragment;


public class SetUpActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_up_activity);
        //add login fragment
        getSupportFragmentManager().beginTransaction().replace(R.id.container, new LoginFragment()).commit();
    }

}
