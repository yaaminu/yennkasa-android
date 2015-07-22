package com.pair.pairapp;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;

import com.pair.data.User;
import com.pair.pairapp.ui.LoginFragment;


public class SetUpActivity extends ActionBarActivity {


    public static final String f_TAG = "tag";
    public static final String ACTION_SIGN_UP = "su",ACTION_LOGIN="li";
    public static final String ACTION = "ac";
    public User registeringUser;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_up_activity);
        registeringUser = new User();
        registeringUser.setType(User.TYPE_NORMAL_USER);
        //add login fragment
        addFragment();
    }

    private void addFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(
                f_TAG);
        if (fragment == null) {
            fragment = new LoginFragment();
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, f_TAG).commit();
    }

    public void verificationCancelled(){
        addFragment();
    }
}
