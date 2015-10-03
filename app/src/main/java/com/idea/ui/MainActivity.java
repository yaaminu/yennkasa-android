package com.idea.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.widget.LinearLayout;

import com.idea.PairApp;
import com.idea.data.Conversation;
import com.idea.data.RealmUtils;
import com.idea.data.User;
import com.idea.pairapp.R;
import com.idea.util.LiveCenter;
import com.idea.util.PLog;
import com.idea.util.TaskManager;
import com.idea.util.UiHelpers;
import com.parse.ParseAnalytics;
import com.rey.material.app.ToolbarManager;
import com.rey.material.widget.SnackBar;
import com.rey.material.widget.TabPageIndicator;

import io.realm.Realm;
import io.realm.RealmChangeListener;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class MainActivity extends PairAppActivity implements NoticeFragment.NoticeFragmentCallback, ConversationsFragment.Callbacks {
    public static final String DEFAULT_FRAGMENT = "default_fragment";
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String ARG_TITLE = "title";
    private static boolean cleanedMessages = false;
    private static int savedPosition = MyFragmentStatePagerAdapter.POSITION_CONVERSATION_FRAGMENT;
    private ViewPager pager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        handleIntent(intent);
        // STOPSHIP: 9/27/2015 remove this
        if (!cleanedMessages) {
            cleanedMessages = true;
            RealmUtils.runRealmOperation(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);

    }

    private void handleIntent(Intent intent) {
//        Realm realm2 = Realm.getInstance(this);
        ParseAnalytics.trackAppOpenedInBackground(intent);
        if (notIsMainIntent(intent)) {
            if (isUserVerified() && SetUpActivity.isEveryThingOk()) {
                setupViews();

                intent.putExtra(MainActivity.ARG_TITLE, getString(R.string.send_to));
                intent.setComponent(new ComponentName(this, CreateMessageActivity.class));
                startActivity(intent);
            } else {
                intent = new Intent(this, LoginSignupPrompt.class);
                startActivity(intent);
                finish();
            }
        } else if (isUserVerified()) {
            //noinspection ConstantConditions
            if (SetUpActivity.isEveryThingOk()) {
                setupViews();
                checkIfUserAvailable();
                final int default_fragment = intent.getIntExtra(DEFAULT_FRAGMENT, savedPosition);
                savedPosition = Math.min(default_fragment, MyFragmentStatePagerAdapter.POSITION_SETTINGS_FRAGMENT);
                savedPosition = Math.max(MyFragmentStatePagerAdapter.POSITION_CONVERSATION_FRAGMENT, default_fragment);
            } else {
                UiHelpers.gotoSetUpActivity(this);
            }
        } else {
            PairApp.disableComponents();
            UiHelpers.gotoSetUpActivity(this);
        }
    }

    private void setupViews() {
        setContentView(R.layout.activity_main);
        pager = ((ViewPager) findViewById(R.id.vp_pager));
        TabPageIndicator tabStrip = ((TabPageIndicator) findViewById(R.id.pts_title_strip));
        try {
            ((LinearLayout) tabStrip.getChildAt(0)).setGravity(Gravity.CENTER_HORIZONTAL);
        } catch (Exception ignored) { //this could raise an exception
            PLog.e(TAG, ignored.getMessage());
        }
        Toolbar toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        toolBar.setTitle("");
        //noinspection unused
        ToolbarManager toolbarManager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);
        pager.setAdapter(new MyFragmentStatePagerAdapter(getSupportFragmentManager()));
        tabStrip.setViewPager(pager);
    }

    private boolean notIsMainIntent(Intent intent) {
        return intent != null && intent.getAction() != null && !intent.getAction().equals(Intent.ACTION_MAIN);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isUserVerified()) {
            if (savedPosition != -1) {
                setPagePosition(savedPosition);
            }
        }
    }

    @Override
    protected void onStop() {
        if (isUserVerified()) {
            savedPosition = pager.getCurrentItem();
        }
        super.onStop();
    }


    void setPagePosition(int newPosition) {
        if (newPosition < 0 || newPosition >= pager.getAdapter().getCount() || pager.getCurrentItem() == newPosition) {
            //do nothing
        } else {
            pager.setCurrentItem(newPosition, true);
        }
    }

    @Override
    protected SnackBar getSnackBar() {
        return ((SnackBar) findViewById(R.id.notification_bar));
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
                    if (noUserAvailable) {
                        fragment = new NoticeFragment();
                    } else
                        fragment = new ConversationsFragment();
                    break;
                case POSITION_CONTACTS_FRAGMENT:
                    fragment = new ContactFragment();
                    break;
                case POSITION_GROUP_FRAGMENT:
                    fragment = new GroupsFragment();
                    break;
                case POSITION_SETTINGS_FRAGMENT:
                    fragment = new SettingsFragment2();
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

    @Override
    public void onAction() {
        pager.setCurrentItem(MyFragmentStatePagerAdapter.POSITION_CONTACTS_FRAGMENT, true);
    }

    private boolean noUserAvailable = false;

    private final RealmChangeListener changeListener = new RealmChangeListener() {
        @Override
        public void onChange() {
            checkIfUserAvailable();
            if (!noUserAvailable) {
                pager.getAdapter().notifyDataSetChanged();
            }
        }
    };
    private Realm realm;

    private void checkIfUserAvailable() {
        if (realm == null) {
            realm = User.Realm(MainActivity.this);
            realm.addChangeListener(changeListener);
        }
        noUserAvailable = realm.where(User.class).notEqualTo(User.FIELD_ID, getMainUserId()).findFirst() == null;
    }

    @Override
    protected void onDestroy() {
        if (realm != null) {
            realm.removeChangeListener(changeListener);
            realm.close();
        }
        super.onDestroy();
    }

    @Override
    public void onConversionClicked(Conversation conversation) {
        final String peerId = conversation.getPeerId();
        final Runnable invalidateTask = new Runnable() {
            @Override
            public void run() {
                LiveCenter.invalidateNewMessageCount(peerId); //FIXME this might block for long enough
            }
        };
        if (!TaskManager.executeNow(invalidateTask)) {
            TaskManager.execute(invalidateTask);
        }
        UiHelpers.enterChatRoom(this, peerId);
    }

    @Override
    public int unSeenMessagesCount(Conversation conversation) {
        return LiveCenter.getUnreadMessageFor(conversation.getPeerId());
    }
}