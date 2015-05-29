package com.pair.pairapp;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;


public class SetUpActivity extends ActionBarActivity {

    public static final String ACTION = "action";
    public static final String ACTION_LOGIN = "login",
            ACTION_SIGNUP = "signup";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_up_activity);
        //add login fragment
        getSupportFragmentManager().beginTransaction().replace(R.id.container, new LoginFragment()).commit();
    }

}
