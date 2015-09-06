package com.pair.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;

import com.pair.pairapp.R;
import com.rey.material.app.ToolbarManager;

public class ProfileActivity extends PairAppBaseActivity {

    private ToolbarManager manager;
    private Toolbar toolBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Bundle bundle = getIntent().getExtras();
        toolBar = ((Toolbar) findViewById(R.id.main_toolbar));
        manager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);

        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        String id = bundle.getString(ProfileActivity.EXTRA_USER_ID);
        if (id == null) {
            throw new IllegalArgumentException("should pass in user id");
        }
        Fragment fragment = new ProfileFragment();
        bundle = new Bundle();
        bundle.putString(ProfileFragment.ARG_USER_ID, id);
        fragment.setArguments(bundle);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();

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

    public static final String EXTRA_USER_ID = "user id";
}
