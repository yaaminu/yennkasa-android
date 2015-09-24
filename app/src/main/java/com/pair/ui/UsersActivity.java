package com.pair.ui;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import com.pair.Errors.ErrorCenter;
import com.pair.Errors.PairappException;
import com.pair.adapter.UsersAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.rey.material.app.DialogFragment;
import com.rey.material.app.ToolbarManager;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;

public class UsersActivity extends PairAppBaseActivity implements ItemsSelector.OnFragmentInteractionListener {

    public static final String EXTRA_GROUP_ID = "GROUPiD";
    public static final String SEND = "send";
    public static final String ACTION = "action";
    private ToolbarManager toolbarManager;
    private UsersAdapter usersAdapter;
    private BaseAdapter membersAdapter;
    private String groupId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_users);
        //noinspection ConstantConditions
        Toolbar toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        toolbarManager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Realm realm = User.Realm(this);
        Bundle bundle = getIntent().getExtras();

        //where do we go?
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        if (groupId == null) {
            RealmResults<User> results = realm.where(User.class)
                    .notEqualTo(User.FIELD_ID, getMainUserId())
                    .findAllSorted(User.FIELD_NAME, true);
            usersAdapter = new UsersAdapter(this, realm, results);
        } else {
            User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
            if (group == null) {
                if (BuildConfig.DEBUG) {
                    throw new RuntimeException();
                }
                finish();
                return;
            } else {
                String title = group.getName() + "-" + getString(R.string.Members);
                bundle.putString(MainActivity.ARG_TITLE, title);
                membersAdapter = new MembersAdapter(this, realm, group.getMembers());
            }
        }
        Fragment fragment = new ItemsSelector();
        fragment.setArguments(bundle);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        toolbarManager.createMenu(R.menu.menu_users);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        toolbarManager.onPrepareMenu();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public BaseAdapter getAdapter() {
        if (groupId != null) {
            return membersAdapter;
        }
        return usersAdapter;
    }

    @Override
    public Filterable filter() {
        if (groupId != null) {
            return null;
        }
        return usersAdapter;
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
        return false;
    }

    @Override
    public boolean supportAddCustom() {
        return false;
    }

    @Override
    public void onCustomAdded(String item) {

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        User user = (User) parent.getAdapter().getItem(position);
        if (userManager.isCurrentUser(user.getUserId())) {
            UiHelpers.gotoProfileActivity(this, user.getUserId());
        } else {
            Intent mission = getIntent();
            String action = mission.getStringExtra(ACTION);
            if (action != null && action.equals(SEND)) {
                String messageToBeSent = mission.getStringExtra(Intent.EXTRA_TEXT);
                if (TextUtils.isEmpty(messageToBeSent)) {
                    UiHelpers.enterChatRoom(this, user.getUserId());
                } else {
                    createMessageAndEnterChatRoom(messageToBeSent, user.getUserId());
                }
            } else {
                UiHelpers.enterChatRoom(this, user.getUserId());
            }
        }
    }

    private void createMessageAndEnterChatRoom(String messageToBeSent, String to) {
        new CreateMessageTask().execute(messageToBeSent, to);
    }

    private class CreateMessageTask extends AsyncTask<String, Void, String> {
        DialogFragment dialogFragment;
        final String TAG = CreateMessageTask.class.getSimpleName();

        @Override
        protected void onPreExecute() {
            dialogFragment = UiHelpers.newProgressDialog();
            dialogFragment.show(getSupportFragmentManager(), null);
        }

        @Override
        protected String doInBackground(String... params) {
            String messageBody = params[0], to = params[1];
            Realm realm = Message.REALM(UsersActivity.this);
            Conversation conversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, to).findFirst();
            if (conversation == null) {
                Conversation.newConversation(UsersActivity.this, to);
            }
            //round trips!
            conversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, to).findFirst();
            realm.beginTransaction();
            Message message;
            try {
                message = Message.makeNew(realm, messageBody, to, Message.TYPE_TEXT_MESSAGE);
                message.setDateComposed(new Date(System.currentTimeMillis() + 1));
                conversation.setLastMessage(message);
                conversation.setActive(true);
                conversation.setSummary(getString(R.string.you) + ": " + messageBody);
                realm.commitTransaction();
                return to;
            } catch (PairappException e) {
                Log.e(TAG, "error while creating message", e.getCause());
                realm.cancelTransaction();
            } finally {
                realm.close();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String to) {
            findViewById(R.id.main_toolbar).post(
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                dialogFragment.dismiss();
                            } catch (Exception ignored) {
                                //if user has navigated away
                            }
                        }
                    }
            );
            if (to == null) {
                ErrorCenter.reportError(TAG,getString(R.string.invalid_message));
            } else {
                UiHelpers.enterChatRoom(UsersActivity.this, to);
                finish();
            }
        }
    }

    public class MembersAdapter extends UsersAdapter {

        private RealmList<User> entries;

        public MembersAdapter(Context context, Realm realm, RealmList<User> entries) {
            super(context, realm, null);
            this.entries = entries;
        }

        @Override
        public long getItemId(int i) {
            return -1;
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public User getItem(int i) {
            return entries.get(i);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            if (userManager.isCurrentUser(getItem(position).getUserId())) {
                ((TextView) view.findViewById(R.id.tv_user_name)).setText(R.string.you);
            }

            return view;
        }

        @Override
        public Filter getFilter() {
            throw new UnsupportedOperationException();
        }
    }
}
