package com.yennkasa.ui;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.yennkasa.PairApp;
import com.yennkasa.R;
import com.yennkasa.data.Conversation;
import com.yennkasa.data.UserManager;
import com.yennkasa.util.LiveCenter;
import com.yennkasa.util.UiHelpers;
import com.rey.material.widget.SnackBar;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnFocusChange;
import butterknife.OnTextChanged;


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


    @OnClick(R.id.action_more)
    void onMenuButtonClicked(View view) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.inflate(R.menu.main);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.action_settings) {
                    Intent intent = new Intent(MainActivity.this, SettingsActivityMain.class);
                    startActivity(intent);
                    return true;
                } else {
                    throw new AssertionError();
                }
            }
        });
        popupMenu.show();
    }

    @OnFocusChange(R.id.et_filter)
    void onFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    @OnTextChanged(R.id.et_filter)
    void onTextChanged(Editable editable) {
        if (UserManager.getInstance().getBoolPref(UserManager.ENABLE_SEARCH, true)) {
            gotoSearch(editable);
        } else {
            suggestEnable(editable);
        }
    }

    private void suggestEnable(final Editable editable) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.enable_search_message)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        enableSearch(editable);
                    }
                }).setNegativeButton(R.string.cancel, null)
                .create().show();

    }

    private void enableSearch(final Editable editable) {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(getString(R.string.st_please_wait));
        dialog.setCancelable(false);
        dialog.show();
        userManager.enableSearch(true, new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                dialog.dismiss();
                if (e == null) {
                    gotoSearch(editable);
                } else {
                    UiHelpers.showPlainOlDialog(MainActivity.this, e.getMessage());
                }
            }
        });
    }

    private void gotoSearch(Editable editable) {
        String text = editable.toString().trim();
        if (text.length() > 0) {
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra(SearchActivity.EXTRA_TEXT, text);
            ((EditText) ButterKnife.findById(this, R.id.et_filter)).setText("");
            startActivity(intent);
        }
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
        fragment = new ConversationsFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    private boolean notIsMainIntent(Intent intent) {
        return intent != null && intent.getAction() != null && !intent.getAction().equals(Intent.ACTION_MAIN);
    }

    @Bind(R.id.et_filter)
    EditText filterEt;

    @Override
    public void onBackPressed() {
        if (filterEt.hasFocus()) {
            filterEt.clearFocus();
        } else {
            super.onBackPressed();
        }
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
    protected void onResume() {
        super.onResume();
        filterEt.clearFocus();
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
