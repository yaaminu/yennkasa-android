package com.pair.pairapp;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;

import com.pair.data.Message;
import com.pair.data.UserManager;
import com.pair.pairapp.ui.ProfileFragment;

public class ProfileActivity extends ActionBarActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Bundle bundle = getIntent().getExtras();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        String id = bundle.getString(ProfileActivity.EXTRA_USER_ID);
        if (id == null) {
            throw new IllegalArgumentException("should in user id");
        }
        Fragment fragment;
        if (!UserManager.getInstance().isMainUser(id)) {
            fragment = new ProfileFragment();
            bundle = new Bundle();
            bundle.putString(ProfileFragment.ARG_USER_ID, id);
            fragment.setArguments(bundle);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, fragment)
                    .commit();
        }
    }

    public static final String EXTRA_USER_ID = Message.FIELD_ID;
}
