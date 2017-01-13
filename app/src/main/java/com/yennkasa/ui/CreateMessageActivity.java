package com.yennkasa.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Filterable;

import com.rey.material.widget.CheckBox;
import com.rey.material.widget.SnackBar;
import com.yennkasa.Errors.ErrorCenter;
import com.yennkasa.Errors.YennkasaException;
import com.yennkasa.R;
import com.yennkasa.adapter.MultiChoiceUsersAdapter;
import com.yennkasa.adapter.UsersAdapter;
import com.yennkasa.data.Message;
import com.yennkasa.data.User;
import com.yennkasa.messenger.MessengerBus;
import com.yennkasa.util.Event;
import com.yennkasa.util.FileUtils;
import com.yennkasa.util.MediaUtils;
import com.yennkasa.util.PLog;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.util.ViewUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.realm.Case;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import io.realm.Sort;

public class CreateMessageActivity extends PairAppActivity
        implements ItemsSelector.OnFragmentInteractionListener, TextWatcher {

    static final String EXTRA_FORWARDED_FROM = "fLKDFAJKAom"; //reduce the likeliness of conflict
    private static final String TAG = CreateMessageActivity.class.getSimpleName();
    public static final String SELECTED_USERS = "selectedUsers";
    public static final String TYPING_MESSAGE = "typingMessage";
    public static final String SELECTED_USER_NAMES = "selectedUserNames";
    public static final String FORWARD_FROM = "forwardFrom";
    public static final String NOT_DEFAULT_INTENT = "notDefaultIntent";
    public static final String WAS_ATTACHING = "wasAttaching";
    public static final String ATTACHMENT_PATH = "attachmentPath";
    public static final String ATTACHMENT_TYPE = "attachmentType";
    public static final String ATTACHMENT_DESCRIPTION = "attachmentDescription";
    public static final String EXTRA_CALLING = "calling";
    public static final String EXTRA_IS_VIDEO = "videoCalling";
    private Set<String> selectedItems = new HashSet<>();
    private String attachmentBodyORMessageBody;
    private int attachmentType;

    private UsersAdapter adapter;
    private Toolbar toolBar;
    private boolean isAttaching = false;
    private boolean isNotDefaultIntent = false;
    private Set<String> selectedUserNames = new TreeSet<>();
    private Fragment fragment;
    View sendMenuItem;

    View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.tv_menu_item_send:
                    onSendMenuItemClicked();
                    break;
                default:
                    throw new AssertionError();
            }
        }
    };
    private String forwardedFrom;
    private String attachmentDescription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_message);
        toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolBar);
        sendMenuItem = findViewById(R.id.tv_menu_item_send);
        sendMenuItem.setOnClickListener(listener);

        selectedItems.clear();
        if (savedInstanceState != null) {
            List<String> selectedUsers = savedInstanceState.getStringArrayList(SELECTED_USERS),
                    selectedUserNames = savedInstanceState.getStringArrayList(SELECTED_USER_NAMES);
            if (selectedUserNames != null && selectedUsers != null) {//both must be valid before we use them
                this.selectedItems = new HashSet<>(selectedUsers);
                this.selectedUserNames = new HashSet<>(selectedUserNames);
                adapter.notifyDataSetChanged();
            }
        }
        adapter = new CustomAdapter();
        final Intent intent = getIntent();

        final String title = intent.getStringExtra(MainActivity.ARG_TITLE);
        setActionBArTitle(title);

        final String intentAction = intent.getAction();
        if (intentAction != null) {
            //we need to validate everything without making an assumption. input coming from other programs
            //cannot be trusted. simple!.
            if (intentAction.equals(Intent.ACTION_SEND)) {
                if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                    //text message.
                    attachmentBodyORMessageBody = intent.getStringExtra(Intent.EXTRA_TEXT);
                    isAttaching = false;
                    isNotDefaultIntent = true;
                } else if (intent.getParcelableExtra(Intent.EXTRA_STREAM) != null) {
                    //binary message
                    //may be content uri i.e content:// style
                    String uri;
                    final Parcelable parcelableExtra = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    try {
                        uri = FileUtils.resolveContentUriToFilePath(((Uri) parcelableExtra));
                        completeAttachIntent(uri);
                    } catch (ClassCastException notPlayUri) { //bad apps exist
                        uri = Uri.parse(parcelableExtra.toString()).toString();
                        completeAttachIntent(uri);
                    }
                } else if (intent.getData() != null) {
                    //binary message
                    String uri = FileUtils.resolveContentUriToFilePath(intent.getData());
                    completeAttachIntent(uri);
                } else if (intent.getStringExtra(Intent.EXTRA_STREAM) != null) {
                    String uri = FileUtils.resolveContentUriToFilePath(intent.getStringExtra(Intent.EXTRA_STREAM));
                    completeAttachIntent(uri);
                }
            }
        } else {
            isNotDefaultIntent = false;
        }

        if (adapter.getCount() >= 1) {
            fragment = new ItemsSelector();
            if (savedInstanceState != null) {
                attachmentBodyORMessageBody = savedInstanceState.getString(TYPING_MESSAGE);
                isAttaching = savedInstanceState.getBoolean(WAS_ATTACHING, false);
                if (isAttaching) {
                    int type = savedInstanceState.getInt(ATTACHMENT_TYPE, -1);
                    attachmentBodyORMessageBody = savedInstanceState.getString(ATTACHMENT_PATH);
                    if (type != -1 && attachmentBodyORMessageBody != null && attachmentDescription != null) {
                        attachmentType = type;
                    }
                }
            }
        } else {
            fragment = new NoticeFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .commit();

    }

    private void completeAttachIntent(final String path) {
        if (path != null && new File(path).exists()) {
            isNotDefaultIntent = true;
            isAttaching = true;
            int messageType = Message.TYPE_BIN_MESSAGE;
            if (MediaUtils.isImage(path)) {
                messageType = Message.TYPE_PICTURE_MESSAGE;
            } else if (MediaUtils.isVideo(path)) {
                messageType = Message.TYPE_VIDEO_MESSAGE;
            }
            try {
                doAttach(new Pair<>(path, messageType));
            } catch (IOException e) {
                // TODO: 9/20/2015 handle this error well.
//                ErrorCenter.reportError("attachCreateMessage", e.getMessage());
                UiHelpers.showPlainOlDialog(this, e.getMessage(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        NavUtils.navigateUpFromSameTask(CreateMessageActivity.this);
                    }
                }, false);
                setActionBArTitle(null);
            }
        } else {
            PLog.f(TAG, "error while attaching. uri returned is null or the file it points to does not exist");
            ErrorCenter.reportError(TAG + "attaching", getString(R.string.error_use_file_manager));
        }
    }

    private void setActionBArTitle(String title) {
        final ActionBar supportActionBar = getSupportActionBar();
        //noinspection ConstantConditions
        supportActionBar.setDisplayHomeAsUpEnabled(true);
        if ((!TextUtils.isEmpty(title))) {
            supportActionBar.setTitle(title);
        }
    }

    private RealmQuery<User> prepareQuery() {
        RealmQuery<User> query = userRealm.where(User.class);

        forwardedFrom = getIntent().getStringExtra(EXTRA_FORWARDED_FROM);
        if (forwardedFrom != null) {
            query.notEqualTo(User.FIELD_ID, forwardedFrom);
        }
        return query.notEqualTo(User.FIELD_ID, getMainUserId());

    }

    @NonNull
    @Override
    protected SnackBar getSnackBar() {
        return ((SnackBar) findViewById(R.id.notification_bar));
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
//        toolbarManager.onPrepareMenu();
        final boolean showMenu = adapter.getCount() >= 1;
        ViewUtils.toggleVisibility(sendMenuItem, !TextUtils.isEmpty(attachmentBodyORMessageBody)
                && !selectedItems.isEmpty() && showMenu);
        return super.onPrepareOptionsMenu(menu);
    }

    private void onSendMenuItemClicked() {
        int type;
        if (isAttaching) {
            type = attachmentType;
        } else {
            if (TextUtils.isEmpty(attachmentBodyORMessageBody)) {
                return;
            }
            type = Message.TYPE_TEXT_MESSAGE;
        }
        final ProgressDialog progressDialog = ProgressDialog.show(this, "", getString(R.string.st_please_wait), false, false, null);
        progressDialog.show();
        MessageActivity.sendMessage(attachmentBodyORMessageBody, selectedItems, type, new MessageActivity.SendCallback() {
            @Override
            public void onSendComplete(Exception e) {
                progressDialog.dismiss();
                Intent intent = new Intent(CreateMessageActivity.this, MainActivity.class);
                NavUtils.navigateUpTo(CreateMessageActivity.this, intent);
            }
        });
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (resultCode == RESULT_OK) {
            AsyncTask<Void, Void, Object> task = new AsyncTask<Void, Void, Object>() {
                @Override
                protected Pair<String, Integer> doInBackground(Void... params) {
                    try {
                        return UiHelpers.completeAttachIntent(requestCode, data);
                    } catch (YennkasaException e) {

                        return null;
                    }
                }

                @Override
                protected void onPostExecute(Object pathAndType) {
                    if (pathAndType instanceof Exception) { //error
                        isAttaching = false;
                        ErrorCenter.reportError("attachError", ((Exception) pathAndType).getMessage());
                        return;
                    }
                    try {
                        //noinspection unchecked
                        doAttach((Pair<String, Integer>) pathAndType);
                        supportInvalidateOptionsMenu();
                    } catch (IOException e) {
                        isAttaching = false;
                        ErrorCenter.reportError("attachError", getString(R.string.an_error_occurred));
                    }
                }
            };
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                task.execute();
            }
        }
    }

    private void doAttach(Pair<String, Integer> pathAndType) throws IOException {
        attachmentDescription = new File(pathAndType.first).getName() + "\n" + FileUtils.sizeInLowestPrecision(pathAndType.first);
        attachmentBodyORMessageBody = pathAndType.first;
        attachmentType = pathAndType.second;
        isAttaching = true;
    }

    @Override
    public BaseAdapter getAdapter() {
        return adapter;
    }

    @Override
    public Filterable filter() {
        return adapter;
    }

    @Override
    public ItemsSelector.ContainerType preferredContainer() {
        return ItemsSelector.ContainerType.LIST;
    }

    @Override
    public View emptyView() {
        return null;
    }

    @Override
    public boolean multiChoice() {
        return isNotDefaultIntent;
    }

    @Override
    public boolean supportAddCustom() {
        return false;
    }

    @Override
    public Set<String> selectedItems() {
        return selectedUserNames;
    }

    @Override
    public void onCustomAdded(String item) {

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        User item = adapter.getItem(position);
        String userId = item.getUserId();
        if (!isNotDefaultIntent) {
            if (getIntent().getBooleanExtra(EXTRA_CALLING, false)) {
                String tag = getIntent().getBooleanExtra(EXTRA_IS_VIDEO, false)
                        ? MessengerBus.VIDEO_CALL_USER
                        : MessengerBus.VOICE_CALL_USER;

                Event event = Event.create(tag, null, userId);
                MessengerBus.get(MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS).post(event);
            } else {
                UiHelpers.enterChatRoom(this, userId, item.getInContacts());
            }
            finish();
        } else if (adapter.getCount() == 1) {
            selectedItems.add(userId);
            onSendMenuItemClicked();
        }
    }


    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        supportInvalidateOptionsMenu();
    }


    @Override
    public Spanned getNoticeText() {
        return null;
    }

    @Override
    public CharSequence getActionText() {
        return null;
    }

    @Override
    public void onAction() {
        UiHelpers.doInvite(this, null);
    }

    @Override
    public ViewGroup searchBar() {
        return ((ViewGroup) findViewById(R.id.search_bar));
    }

    @Override
    public void onBackPressed() {
        if (fragment instanceof NoticeFragment) {
            onAction();
        } else {
            super.onBackPressed();
        }
    }

    private final MultiChoiceUsersAdapter.Delegate delegagte = new MultiChoiceUsersAdapter.Delegate() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id, boolean isSelected) {
            User user = ((User) parent.getAdapter().getItem(position));
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.cb_checked);
            String userName = user.getName();
            if (!userName.startsWith("@")) {
                userName = "@" + userName;
            }
            if (isSelected) {
                selectedItems.add(user.getUserId());
                selectedUserNames.add(userName);
                if (!checkBox.isChecked()) {
                    checkBox.setChecked(true);
                }
            } else {
                selectedItems.remove(user.getUserId());
                selectedUserNames.remove(userName);
                if (checkBox.isChecked()) {
                    checkBox.setChecked(false);
                }
            }
            try {
                ((ItemsSelector) fragment).onItemsChanged();
            } catch (ClassCastException e) {
                throw new RuntimeException(e.getCause());
            }
            supportInvalidateOptionsMenu();
        }

        @Override
        public Context getContext() {
            return CreateMessageActivity.this;
        }
    };

    class CustomAdapter extends MultiChoiceUsersAdapter {
        private CustomAdapter() {
            super(delegagte, userRealm, prepareQuery().findAllSorted(User.FIELD_NAME, Sort.ASCENDING, User.FIELD_TYPE, Sort.DESCENDING), selectedItems, R.id.cb_checked);
        }

        @Override
        protected RealmQuery<User> getOriginalQuery() {
            return prepareQuery();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            if (adapter.getCount() > 1 && isNotDefaultIntent) {
                view.findViewById(R.id.cb_checked).setVisibility(View.VISIBLE);
            } else {
                view.findViewById(R.id.cb_checked).setVisibility(View.GONE);
            }
            return view;
        }

        @Override
        protected RealmResults<User> doFilter(String constraint) {
            if (TextUtils.isEmpty(constraint)) {
                return prepareQuery().findAllSorted(User.FIELD_NAME, Sort.ASCENDING, User.FIELD_TYPE, Sort.DESCENDING);
            }
            RealmQuery<User> query = userRealm.where(User.class);//.equalTo(User.FIELD_TYPE, User.TYPE_GROUP);

            if (TextUtils.isDigitsOnly(constraint)) {
                query.notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP).contains(User.FIELD_ID, constraint);
            } else {
                query.contains(User.FIELD_NAME, constraint, Case.INSENSITIVE);
            }
            final String forwardedFrom = getIntent().getStringExtra(EXTRA_FORWARDED_FROM);
            if (forwardedFrom != null) {
                query.notEqualTo(User.FIELD_ID, forwardedFrom);
            }
            query.notEqualTo(User.FIELD_ID, getMainUserId());

            return query.findAllSorted(User.FIELD_NAME, Sort.ASCENDING, User.FIELD_TYPE, Sort.DESCENDING);
        }
    }

    @Override
    public final View getToolBar() {
        return toolBar;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(TYPING_MESSAGE, attachmentBodyORMessageBody);
        outState.putStringArrayList(SELECTED_USERS, new ArrayList<>(selectedItems));
        outState.putStringArrayList(SELECTED_USER_NAMES, new ArrayList<>(selectedUserNames));
        outState.putBoolean(WAS_ATTACHING, isAttaching);
        if (!TextUtils.isEmpty(forwardedFrom)) {
            outState.putString(FORWARD_FROM, forwardedFrom);
        }
        outState.putBoolean(NOT_DEFAULT_INTENT, isNotDefaultIntent);
        outState.putString(ATTACHMENT_PATH, attachmentBodyORMessageBody);
        outState.putInt(ATTACHMENT_TYPE, attachmentType);
        outState.putString(ATTACHMENT_DESCRIPTION, attachmentDescription);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }
}
