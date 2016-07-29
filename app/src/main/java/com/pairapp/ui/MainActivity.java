package com.pairapp.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;

import com.pairapp.PairApp;
import com.pairapp.R;
import com.pairapp.data.Conversation;
import com.pairapp.util.LiveCenter;
import com.pairapp.util.UiHelpers;
import com.rey.material.widget.SnackBar;

import butterknife.Bind;
import butterknife.ButterKnife;

//import com.digits.sdk.android.Digits;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class MainActivity extends PairAppActivity implements NoticeFragment.NoticeFragmentCallback, ConversationsFragment.Callbacks {

    public static final String DEFAULT_FRAGMENT = "default_fragment";
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String ARG_TITLE = "title";
    private static int savedPosition = MyFragmentStatePagerAdapter.POSITION_CALL_LOGS;

    @Bind(R.id.vp_pager)
    ViewPager pager;

    @Bind(R.id.pts_title_strip)
    TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        handleIntent(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);

    }

    private void handleIntent(Intent intent) {
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
                final int default_fragment = intent.getIntExtra(DEFAULT_FRAGMENT, savedPosition);
                if (default_fragment >= MyFragmentStatePagerAdapter.POSITION_CONVERSATION_FRAGMENT
                        && default_fragment <= MyFragmentStatePagerAdapter.POSITION_SETTINGS_FRAGMENT)
                    savedPosition = default_fragment;
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
        ButterKnife.bind(this);
        pager.setAdapter(new MyFragmentStatePagerAdapter(getSupportFragmentManager()));
        tabLayout.setupWithViewPager(pager);
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
        if (newPosition >= 0 && newPosition < pager.getAdapter().getCount() && pager.getCurrentItem() != newPosition) {
            pager.setCurrentItem(newPosition, true);
        }
    }

    @NonNull
    @Override
    protected SnackBar getSnackBar() {
        return ((SnackBar) findViewById(R.id.notification_bar));
    }

    //package private
    class MyFragmentStatePagerAdapter extends FragmentStatePagerAdapter {
        static final int
                POSITION_CALL_LOGS = 0x0,
                POSITION_CONVERSATION_FRAGMENT = 0x1,
                POSITION_CONTACTS_FRAGMENT = 0x2,
                POSITION_GROUP_FRAGMENT = 0x3, POSITION_SETTINGS_FRAGMENT = 0x4;
        String[] pageTitles;

        public MyFragmentStatePagerAdapter(FragmentManager fm) {
            super(fm);
            pageTitles = getResources().getStringArray(R.array.menuItems);
        }

        @Override
        public Fragment getItem(int position) {
            Fragment fragment;
            switch (position) {
                case POSITION_CALL_LOGS:
                    fragment = new CallLogFragment();
                    break;
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
                    fragment = new SettingsFragment2();
                    break;
                default:
                    throw new AssertionError("impossible");
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return pageTitles.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return pageTitles[position];
        }
    }

    @Override
    public void onAction() {
        UiHelpers.doInvite(this, null);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onConversionClicked(final Conversation conversation) {
        final String peerId = conversation.getPeerId();
        if (userManager.isBlocked(peerId)) {
            UiHelpers.showErrorDialog(this, R.string.blocked_user_notice, R.string.agree, R.string.disagree, new UiHelpers.Listener() {
                @Override
                public void onClick() {
                    userManager.unBlockUser(peerId);
                    UiHelpers.showToast(getString(R.string.user_unblocked));
                    UiHelpers.enterChatRoom(MainActivity.this, peerId);
                }
            }, new UiHelpers.Listener() {
                @Override
                public void onClick() {
                }
            });
        } else {
            UiHelpers.enterChatRoom(this, peerId);
        }
    }

    @Override
    protected boolean hideConnectionView() {
        return false;
    }

    @Override
    public int unSeenMessagesCount(Conversation conversation) {
        return LiveCenter.getUnreadMessageFor(conversation.getPeerId());
    }
}
