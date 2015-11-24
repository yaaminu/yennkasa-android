package com.idea.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
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
import android.widget.EditText;
import android.widget.Filterable;
import android.widget.TextView;

import com.idea.Errors.ErrorCenter;
import com.idea.Errors.PairappException;
import com.idea.adapter.MultiChoiceUsersAdapter;
import com.idea.adapter.UsersAdapter;
import com.idea.data.Message;
import com.idea.data.User;
import com.idea.pairapp.BuildConfig;
import com.idea.pairapp.R;
import com.idea.util.FileUtils;
import com.idea.util.MediaUtils;
import com.idea.util.PLog;
import com.idea.util.TypeFaceUtil;
import com.idea.util.UiHelpers;
import com.idea.util.ViewUtils;
import com.rey.material.app.DialogFragment;
import com.rey.material.widget.CheckBox;
import com.rey.material.widget.SnackBar;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;

public class CreateMessageActivity extends MessageActivity
        implements ItemsSelector.OnFragmentInteractionListener, TextWatcher, View.OnClickListener {

    static final String EXTRA_FORWARDED_FROM = "fLKDFAJKAom"; //reduce the likeliness of conflict
    private static final String TAG = CreateMessageActivity.class.getSimpleName();
    private final Set<String> selectedItems = new HashSet<>();
    private EditText messageEt;
    private TextView tvAttachmentDescription;
    private View attachmentPreview;
    private String attachmentBody;
    private int attachmentType;

    private UsersAdapter adapter;
    private Realm realm;
    private Toolbar toolBar;
    //    private ToolbarManager toolbarManager;
    private boolean isAttaching = false;
    private boolean isNotDefaultIntent = false;
    private Set<String> selectedUserNames = new TreeSet<>();
    private Fragment fragment;
    View attachMenuItem, sendMenuItem;

    View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.tv_menu_item_attach:
                    UiHelpers.attach(CreateMessageActivity.this);
                    break;
                case R.id.tv_menu_item_send:
                    onSendMenuItemClicked();
                    break;
                default:
                    throw new AssertionError();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_message);
        toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolBar);
        messageEt = ((EditText) findViewById(R.id.et_message));
        messageEt.addTextChangedListener(this);
        sendMenuItem = findViewById(R.id.tv_menu_item_send);
        attachMenuItem = findViewById(R.id.tv_menu_item_attach);
        attachMenuItem.setOnClickListener(listener);
        sendMenuItem.setOnClickListener(listener);
        attachmentPreview = findViewById(R.id.attachment_preview);
        View cancelAttachment = findViewById(R.id.cancel_attachment);
        tvAttachmentDescription = (TextView) findViewById(R.id.attachment_description);

        ViewUtils.setTypeface(tvAttachmentDescription, TypeFaceUtil.DROID_SERIF_BOLD_TTF);
        ViewUtils.setTypeface(messageEt, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        attachmentPreview.setOnClickListener(this);
        cancelAttachment.setOnClickListener(this);

        selectedItems.clear();
        realm = User.Realm(this);
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
                    String message = intent.getStringExtra(Intent.EXTRA_TEXT);
                    messageEt.setText(message);
                    isAttaching = false;
                    isNotDefaultIntent = true;
                    ViewUtils.hideViews(attachmentPreview);
                    ViewUtils.showViews(messageEt);
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
            ViewUtils.hideViews(attachmentPreview);
            ViewUtils.showViews(messageEt);
            isNotDefaultIntent = false;
        }

        if (adapter.getCount() >= 1) {
            fragment = new ItemsSelector();
        } else {
            ViewUtils.hideViews(attachmentPreview);
            ViewUtils.hideViews(messageEt);
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
                ViewUtils.showViews(messageEt);
                ViewUtils.hideViews(attachmentPreview);
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
        RealmQuery<User> query = realm.where(User.class);

        final String forwardedFrom = getIntent().getStringExtra(EXTRA_FORWARDED_FROM);
        if (forwardedFrom != null) {
            query.notEqualTo(User.FIELD_ID, forwardedFrom);
        }
        return query.notEqualTo(User.FIELD_ID, getMainUserId());

    }

    @Override
    protected SnackBar getSnackBar() {
        return ((SnackBar) findViewById(R.id.notification_bar));
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
//        toolbarManager.onPrepareMenu();
        final boolean showMenu = adapter.getCount() >= 1;
        ViewUtils.toggleVisibility(sendMenuItem, ((!messageEt.getText().toString().isEmpty() || isAttaching)
                && !selectedItems.isEmpty() && showMenu));
        ViewUtils.toggleVisibility(attachMenuItem, !selectedItems.isEmpty() && !isAttaching && !isNotDefaultIntent && showMenu);
        return super.onPrepareOptionsMenu(menu);
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        //toolBar.inflateMenu(R.menu.menu_create_message);
//        return super.onCreateOptionsMenu(menu);
//    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//
//        if (id == R.id.action_attach) {
//            UiHelpers.attach(this);
//            return true;
//        } else if (id == R.id.action_send_message) {
//            onSendMenuItemClicked();
//            return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }

    private void onSendMenuItemClicked() {
        int type;
        String messageBody;
        if (isAttaching) {
            type = attachmentType;
            messageBody = attachmentBody;
        } else {
            messageBody = messageEt.getText().toString().trim();
            if (TextUtils.isEmpty(messageBody)) {
                return;
            }
            type = Message.TYPE_TEXT_MESSAGE;
        }
        final DialogFragment progressDialog = UiHelpers.newProgressDialog();
        progressDialog.show(getSupportFragmentManager(), null);
        sendMessage(messageBody, selectedItems, type, new SendCallback() {
            @Override
            public void onSendComplete(Exception e) {
                UiHelpers.dismissProgressDialog(progressDialog);
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
                    } catch (PairappException e) {

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
        String attachmentDescription = new File(pathAndType.first).getName() + "\n" + FileUtils.sizeInLowestPrecision(pathAndType.first);
        attachmentBody = pathAndType.first;
        attachmentType = pathAndType.second;
        isAttaching = true;
        tvAttachmentDescription.setText(attachmentDescription);
        ViewUtils.hideViews(messageEt);
        ViewUtils.showViews(attachmentPreview);
    }

    @Override
    protected void onDestroy() {
        realm.close();
        super.onDestroy();
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
        return true;
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
//        User user = adapter.getItem(position);
//        if (((ListView) parent).isItemChecked(position)) {
//            selectedItems.add(user.getUserId());
//            selectedUserNames.add(user.getName());
//            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
//                ((CheckBox) view.findViewById(R.id.cb_checked)).setCheckedImmediately(true);
//            } else {
//                ((CheckBox) view.findViewById(R.id.cb_checked)).setCheckedAnimated(true);
//            }
//        } else {
//            selectedItems.remove(user.getUserId());
//            selectedUserNames.remove(user.getName());
//            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
//                ((CheckBox) view.findViewById(R.id.cb_checked)).setCheckedImmediately(false);
//            } else {
//                ((CheckBox) view.findViewById(R.id.cb_checked)).setCheckedAnimated(false);
//            }
//        }
//        supportInvalidateOptionsMenu();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        supportInvalidateOptionsMenu();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.attachment_preview:
                if (isAttaching && attachmentBody != null) {
                    try {
                        UiHelpers.attemptToViewFile(this, attachmentBody);
                    } catch (PairappException e) {
                        ErrorCenter.reportError("viewAttachment", e.getMessage());
                    }
                    break;
                }
                if (BuildConfig.DEBUG) {
                    throw new IllegalStateException("this view should be hidden");
                }
                PLog.i(TAG, "missing message body");
                break;
            case R.id.cancel_attachment:
                isAttaching = false;
                attachmentBody = null;
                attachmentType = -1;
                ViewUtils.hideViews(attachmentPreview);
                ViewUtils.showViews(messageEt);
                supportInvalidateOptionsMenu();
                break;
            default:
                throw new AssertionError("unknown view");
        }
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
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.DEFAULT_FRAGMENT, MainActivity.MyFragmentStatePagerAdapter.POSITION_CONTACTS_FRAGMENT);
        NavUtils.navigateUpTo(this, intent);
    }

    @Override
    public ViewGroup searchBar() {
        return ((ViewGroup) findViewById(R.id.search_bar));
    }

    @Override
    public void onBackPressed() {
        onAction();
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

    private class CustomAdapter extends MultiChoiceUsersAdapter {
        private CustomAdapter() {
            super(delegagte, realm, prepareQuery().findAllSorted(User.FIELD_NAME, true, User.FIELD_TYPE, false), selectedItems, R.id.cb_checked);
        }

        @Override
        protected RealmQuery<User> getOriginalQuery() {
            return prepareQuery();
        }

        @Override
        protected RealmResults<User> doFilter(String constraint) {
            if (TextUtils.isEmpty(constraint)) {
                return prepareQuery().findAllSorted(User.FIELD_NAME, true, User.FIELD_TYPE, false);
            }
            RealmQuery<User> query = realm.where(User.class);//.equalTo(User.FIELD_TYPE, User.TYPE_GROUP);

            query.contains(User.FIELD_NAME, constraint, false);
            final String forwardedFrom = getIntent().getStringExtra(EXTRA_FORWARDED_FROM);
            if (forwardedFrom != null) {
                query.notEqualTo(User.FIELD_ID, forwardedFrom);
            }
            query.notEqualTo(User.FIELD_ID, getMainUserId());
            return query.findAllSorted(User.FIELD_NAME, true, User.FIELD_TYPE, false);
        }
    }

    @Override
    public final View getToolBar() {
        return toolBar;
    }
}
