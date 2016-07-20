package com.pairapp.ui;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;

import com.pairapp.R;
import com.rey.material.app.ToolbarManager;
import com.rey.material.widget.SnackBar;

public class SettingsActivity extends PairAppActivity {

   public static final String EXTRA_ITEM = "ITEM";
    private int item;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        item = getIntent().getIntExtra(EXTRA_ITEM, 1);
        if (item < 1 || item > 3) {
            throw new IllegalArgumentException("unknown!");
        }

        Toolbar toolBar = ((Toolbar) findViewById(R.id.main_toolbar));
        ToolbarManager manager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);

        ActionBar supportActionBar = getSupportActionBar();
        //noinspection ConstantConditions
        supportActionBar.setDisplayHomeAsUpEnabled(true);
        Fragment fragment;
        switch (item) {
            case 1:
                fragment = new SettingsFragment();
                supportActionBar.setTitle(R.string.preferences);
                break;
            case 2:
                fragment = new FeedBackFragment();
                supportActionBar.setTitle(R.string.Feedback);
                break;
            case 3:
                supportActionBar.hide();
                fragment = new AboutFragment();
                supportActionBar.setTitle(R.string.about_app);
                break;
            default:throw new AssertionError();
        }
        addFragment(fragment);

    }

    private void addFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, "tag"+item).commit();
    }

    @NonNull
    @Override
    protected SnackBar getSnackBar() {
        return ((SnackBar) findViewById(R.id.notification_bar));
    }

}
