package com.pair.pairapp;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;

import com.pair.data.Message;
import com.pair.data.UserManager;
import com.pair.pairapp.ui.ProfileFragment;
import com.rey.material.app.ToolbarManager;

public class ProfileActivity extends ActionBarActivity {

    private ToolbarManager manager;
    private Toolbar toolBar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Bundle bundle = getIntent().getExtras();
        toolBar = ((Toolbar) findViewById(R.id.main_toolbar));
        manager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        manager.createMenu(R.menu.menu_profile);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        manager.onPrepareMenu();
        return super.onPrepareOptionsMenu(menu);
    }

    public static final String EXTRA_USER_ID = Message.FIELD_ID;
}
