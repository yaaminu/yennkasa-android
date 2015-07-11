package com.pair.pairapp;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import com.pair.pairapp.ui.ProfileFragment;

public class ProfileActivity extends ActionBarActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Bundle bundle = getIntent().getExtras();
        String id = bundle.getString(ProfileActivity.EXTRA_USER_ID);
        if (id == null) {
            throw new IllegalStateException("should in user id");
        }
        final ProfileFragment fragment = new ProfileFragment();
        bundle = new Bundle();
        bundle.putString(ProfileFragment.ARG_USER_ID, id);
        fragment.setArguments(bundle);
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.container, fragment, null)
                .commit();

    }

    public static final String EXTRA_USER_ID = "id";
}
