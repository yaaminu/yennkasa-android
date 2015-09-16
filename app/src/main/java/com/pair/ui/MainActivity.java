package com.pair.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;

import com.pair.Config;
import com.pair.PairApp;
import com.pair.data.Conversation;
import com.pair.data.RealmUtils;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.parse.ParseAnalytics;
import com.rey.material.app.ToolbarManager;
import com.rey.material.widget.SnackBar;
import com.rey.material.widget.TabPageIndicator;

import io.realm.Realm;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class MainActivity extends PairAppActivity {
    public static final String DEFAULT_FRAGMENT = "default_fragment";
    private static boolean cleanedMessages = false;
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String ARG_TITLE = "title";
    private static int savedPosition = MyFragmentStatePagerAdapter.POSITION_CONVERSATION_FRAGMENT;
    private ViewPager pager;
    private ToolbarManager toolbarManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        ParseAnalytics.trackAppOpenedInBackground(intent);
        if (notIsMainIntent()) {
            if (UserManager.getInstance().isUserVerified()) {
                setupViews();
                Bundle bundle = intent.getExtras();
                bundle.putString(UsersActivity.ACTION, UsersActivity.SEND);
                UiHelpers.pickRecipient(this, bundle);
            } else {
                UiHelpers.showErrorDialog(this, "please login/sign up first");
                gotoSetUpActivity();
            }
        } else if (UserManager.getInstance().isUserVerified()) {
            //noinspection ConstantConditions
            setupViews();
            final int default_fragment = intent.getIntExtra(DEFAULT_FRAGMENT, MyFragmentStatePagerAdapter.POSITION_CONVERSATION_FRAGMENT);
            if (default_fragment != MyFragmentStatePagerAdapter.POSITION_CONVERSATION_FRAGMENT) {
                savedPosition = Math.abs(savedPosition);
                savedPosition = default_fragment > pager.getAdapter().getCount() - 1
                        ? MyFragmentStatePagerAdapter.POSITION_CONVERSATION_FRAGMENT
                        : default_fragment;
            }
            if (default_fragment == MyFragmentStatePagerAdapter.POSITION_CONVERSATION_FRAGMENT) {
                Realm realm = Conversation.Realm(this);
                if (realm.where(Conversation.class).count() < 1) {
                    savedPosition = MyFragmentStatePagerAdapter.POSITION_CONTACTS_FRAGMENT;
                }
                realm.close();
            }
            UiHelpers.showToast(Config.deviceArc());
        } else {
            PairApp.disableComponents();
            gotoSetUpActivity();
        }
        if (!cleanedMessages) {
            cleanedMessages = true;
            RealmUtils.runRealmOperation(this);
        }
    }

    private void setupViews() {
        setContentView(R.layout.activity_main);
        pager = ((ViewPager) findViewById(R.id.vp_pager));
        TabPageIndicator tabStrip = ((TabPageIndicator) findViewById(R.id.pts_title_strip));
        try {
            ((LinearLayout) tabStrip.getChildAt(0)).setGravity(Gravity.CENTER_HORIZONTAL);
        } catch (Exception ignored) { //this could raise an exception
            Log.e(TAG, ignored.getMessage());
        }
        Toolbar toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        toolBar.setTitle("");
        toolbarManager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);
        pager.setAdapter(new MyFragmentStatePagerAdapter(getSupportFragmentManager()));
        snackBar = ((SnackBar) findViewById(R.id.notification_bar));
        tabStrip.setViewPager(pager);
    }

    private boolean notIsMainIntent() {
        String action = getIntent().getAction();
        return action != null && !action.equals(Intent.ACTION_MAIN);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (savedPosition != -1) {
            setPagePosition(savedPosition);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Config.appOpen(true);
        if (pairAppClientInterface != null) {
            pairAppClientInterface.registerUINotifier(this);
        }
    }

    @Override
    protected void onPause() {
        Config.appOpen(false);
        if (pairAppClientInterface != null) {
            pairAppClientInterface.unRegisterUINotifier(this);
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
        pairAppClientInterface.registerUINotifier(this);
    }

    @Override
    protected void onUnbind() {
    }


    void setPagePosition(int newPosition) {
        if (newPosition < 0 || newPosition >= pager.getAdapter().getCount() || pager.getCurrentItem() == newPosition) {
            //do nothing
        } else {
            pager.setCurrentItem(newPosition, true);
        }
    }

    private void gotoSetUpActivity() {
        Intent intent = new Intent(this, SetUpActivity.class);
        startActivity(intent);
        finish();
    }

    //package private
    class MyFragmentStatePagerAdapter extends FragmentStatePagerAdapter {
        static final int POSITION_CONVERSATION_FRAGMENT = 0x0,
                POSITION_CONTACTS_FRAGMENT = 0x1,
                POSITION_GROUP_FRAGMENT = 0x2, POSITION_SETTINGS_FRAGMENT = 0x3;
        String[] pageTitles;

        public MyFragmentStatePagerAdapter(FragmentManager fm) {
            super(fm);
            pageTitles = getResources().getStringArray(R.array.menuItems);
        }

        @Override
        public Fragment getItem(int position) {
            Fragment fragment;
            switch (position) {
                case POSITION_CONVERSATION_FRAGMENT:
                    fragment = new ConversationsFragment();
                    break;
                case POSITION_CONTACTS_FRAGMENT:
                    fragment = new ContactFragment();
                    break;
                case POSITION_GROUP_FRAGMENT:
                    fragment = new GroupsFragment();
                    break;
                case POSITION_SETTINGS_FRAGMENT:
                    fragment = new ProfileFragment();
                    Bundle bundle = new Bundle(1);
                    bundle.putString(ProfileFragment.ARG_USER_ID, UserManager.getMainUserId());
                    fragment.setArguments(bundle);
                    break;
                default:
                    throw new AssertionError("impossible");
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return pageTitles[position];
        }

    }

}
