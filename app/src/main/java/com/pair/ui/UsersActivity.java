package com.pair.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import com.pair.adapter.UsersAdapter;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.rey.material.app.ToolbarManager;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;

public class UsersActivity extends ActionBarActivity implements ItemsSelector.OnFragmentInteractionListener {

    public static final String EXTRA_GROUP_ID = "GROUPiD";
    private Toolbar toolBar;
    private ToolbarManager toolbarManager;
    private UsersAdapter usersAdapter;
    private Realm realm;
    private BaseAdapter membersAdapter;
    private String groupId;
    private String title;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_users);
        //noinspection ConstantConditions
        toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        toolbarManager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        realm = User.Realm(this);
        Bundle bundle = getIntent().getExtras();
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        if (groupId == null) {
            RealmResults<User> results = realm.where(User.class)
                    .notEqualTo(User.FIELD_ID, UserManager.getInstance()
                            .getMainUser()
                            .getUserId())
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
                title = group.getName() + "-" + getString(R.string.Members);
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
        if (UserManager.getInstance().isMainUser(user.getUserId())) {
            UiHelpers.gotoProfileActivity(this, user.getUserId());
        } else {
            UiHelpers.enterChatRoom(this, user.getUserId());
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
            if (UserManager.getInstance().isMainUser(getItem(position).getUserId())) {
                ((TextView) view.findViewById(R.id.tv_user_name)).setText(R.string.you);
                view.findViewById(R.id.tv_user_status).setVisibility(View.GONE);
            }

            return view;
        }

        @Override
        public Filter getFilter() {
            throw new UnsupportedOperationException();
        }
    }
}
