package com.pairapp.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.pairapp.PairApp;
import com.pairapp.R;
import com.pairapp.data.Conversation;
import com.pairapp.util.LiveCenter;
import com.pairapp.util.UiHelpers;
import com.rey.material.widget.SnackBar;

import butterknife.Bind;
import butterknife.ButterKnife;


/**
 * @author Null-Pointer on 6/6/2015.
 */
public class MainActivity extends PairAppActivity implements NoticeFragment.NoticeFragmentCallback, ConversationsFragment.Callbacks {

    public static final String DEFAULT_FRAGMENT = "default_fragment";
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String ARG_TITLE = "title";
    public static final int CONVERSATION_TAB = 1;
    public static final int PEOPLE_TAB = 2;

    private static int savedPosition = 1;

    @Bind(R.id.main_toolbar)
    Toolbar toolbar;

    @Bind(R.id.bottom_bar)
    BottomNavigationView buttomBar;
    private BottomNavigationView.OnNavigationItemSelectedListener navigationListener = new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.tab_call:
                    fragment = new CallLogFragment();
                    break;
                case R.id.tab_conversation:
                    fragment = new ConversationsFragment();
                    break;
                case R.id.tab_people:
                    fragment = new ContactFragment();
                    break;
                case R.id.tab_groups:
                    fragment = new GroupsFragment();
                    break;
                default:
                    throw new AssertionError("impossible");
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, fragment)
                    .commit();
            return true;
        }
    };

    Fragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        handleIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search:
                // TODO: 12/15/16 go to search activity
                break;
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivityMain.class);
                startActivity(intent);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
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
                savedPosition = intent.getIntExtra(DEFAULT_FRAGMENT, savedPosition);
                // TODO: 12/15/16 use position
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
        buttomBar.setOnNavigationItemSelectedListener(navigationListener);
        setSupportActionBar(toolbar);
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


    void setPagePosition(int newPosition) {
        if (newPosition >= 0 && newPosition < buttomBar.getMaxItemCount() && savedPosition != newPosition) {
            // FIXME: 12/15/16 set to current index
        }
    }

    @NonNull
    @Override
    protected SnackBar getSnackBar() {
        return ((SnackBar) findViewById(R.id.notification_bar));
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
