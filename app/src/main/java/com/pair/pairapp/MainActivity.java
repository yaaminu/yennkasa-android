package com.pair.pairapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.widget.LinearLayout;

import com.pair.data.UserManager;
import com.pair.messenger.PairAppBaseActivity;
import com.pair.pairapp.ui.ContactFragment;
import com.pair.pairapp.ui.ConversationsFragment;
import com.pair.pairapp.ui.GroupsFragment;
import com.pair.util.RealmUtils;
import com.rey.material.app.ToolbarManager;
import com.rey.material.widget.SnackBar;
import com.rey.material.widget.TabPageIndicator;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class MainActivity extends PairAppBaseActivity {
    private static boolean cleanedMessages = false;
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String ARG_TITLE = "title";
    private static int savedPosition = -1;
    private ViewPager pager;
    private ToolbarManager toolbarManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //user cannot get pass this if there is no gcm support as he will be presented a blocking dialog that cannot be dismissed
        if (UserManager.getInstance().isUserVerified()) {
            setContentView(R.layout.activity_main);
            //noinspection ConstantConditions
            pager = ((ViewPager) findViewById(R.id.vp_pager));
            TabPageIndicator tabStrip = ((TabPageIndicator) findViewById(R.id.pts_title_strip));
            try {
                ((LinearLayout) tabStrip.getChildAt(0)).setGravity(Gravity.CENTER_HORIZONTAL);
            } catch (Exception ignored) { //this could raise an exception
                Log.e(TAG, ignored.getMessage());
            }
            Toolbar toolBar = (Toolbar) findViewById(R.id.main_toolbar);
            toolbarManager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);
            pager.setAdapter(new MyFragmentStatePagerAdapter(getSupportFragmentManager()));
            snackBar = ((SnackBar) findViewById(R.id.notification_bar));
            tabStrip.setViewPager(pager);
        } else {
            gotoSetUpActivity();
        }
        if (!cleanedMessages) {
            cleanedMessages = true;
            RealmUtils.runRealmOperation(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Config.appOpen(true);
        if (savedPosition != -1) {
            pager.setCurrentItem(savedPosition);
        }
        if (pairAppClientInterface != null) {
            pairAppClientInterface.registerNotifier(this);
        }
    }

    @Override
    protected void onPause() {
        Config.appOpen(false);
        if (pairAppClientInterface != null) {
            pairAppClientInterface.unRegisterNotifier(this);
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        savedPosition = pager.getCurrentItem();
        super.onStop();
    }

    @Override
    protected void onBind() {
        pairAppClientInterface.registerNotifier(this);
    }

    @Override
    protected void onUnbind() {
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        toolbarManager.createMenu(R.menu.menu_pair_app);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        toolbarManager.onPrepareMenu();
        return super.onPrepareOptionsMenu(menu);
    }

    private void gotoSetUpActivity() {
        Intent intent = new Intent(this, SetUpActivity.class);
        startActivity(intent);
        finish();
    }

    private class MyFragmentStatePagerAdapter extends FragmentStatePagerAdapter {
        String[] pageTitles;

        public MyFragmentStatePagerAdapter(FragmentManager fm) {
            super(fm);
            pageTitles = getResources().getStringArray(R.array.menuItems);
        }

        @Override
        public Fragment getItem(int position) {
            Fragment fragment;
            switch (position) {
                case 0:
                    fragment = new ConversationsFragment();
                    break;
                case 1:
                    fragment = new ContactFragment();
                    break;
                case 2:
                    fragment = new GroupsFragment();
                    break;
                default:
                    throw new AssertionError("impossible");
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return pageTitles[position];
        }
    }

}
