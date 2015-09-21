package com.pair.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.pair.pairapp.R;
import com.rey.material.app.ToolbarManager;
import com.rey.material.widget.SnackBar;

public class ProfileActivity extends PairAppActivity {

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
    protected SnackBar getSnackBar() {
        return ((SnackBar) findViewById(R.id.notification_bar));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        manager.createMenu(R.menu.menu_profile);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        manager.onPrepareMenu();
        menu = toolBar.getMenu();
        MenuItem item = menu.add(1, 1, 1, R.string.edit_username);
        item.setIcon(R.drawable.ic_action_edit_white);
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        item.setVisible(true);
        super.onPrepareOptionsMenu(menu);
        return super.onPrepareOptionsMenu(menu);
    }

    public static final String EXTRA_USER_ID = "user id";
}
