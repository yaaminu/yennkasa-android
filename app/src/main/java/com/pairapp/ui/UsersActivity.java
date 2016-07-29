package com.pairapp.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
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

import com.pairapp.BuildConfig;
import com.pairapp.R;
import com.pairapp.adapter.UsersAdapter;
import com.pairapp.data.User;
import com.pairapp.util.UiHelpers;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

public class UsersActivity extends PairAppBaseActivity implements ItemsSelector.OnFragmentInteractionListener {

    public static final String EXTRA_USER_ID = "lnubj;l;l;k;ugvbbD";
    private UsersAdapter usersAdapter;
    private BaseAdapter membersAdapter;
    private String userId;
    private Toolbar toolBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_users);
        //noinspection ConstantConditions
        toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolBar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Realm realm = User.Realm(this);
        Bundle bundle = getIntent().getExtras();

        //where do we go?
        userId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (userId == null) {
            RealmResults<User> results = realm.where(User.class)
                    .notEqualTo(User.FIELD_ID, getMainUserId())
                    //// FIXME: 1/14/2016 should we sort on type ascending?
                    .findAllSorted(User.FIELD_NAME, Sort.ASCENDING, User.FIELD_TYPE, Sort.ASCENDING);
            usersAdapter = new UsersAdapter(this, realm, results);
        } else {
            User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
            if (user == null) {
                if (BuildConfig.DEBUG) {
                    throw new RuntimeException();
                }
                finish();
                return;
            } else {
                if (User.isGroup(user)) {
                    String title = user.getName() + "-" + getString(R.string.Members);
                    bundle.putString(MainActivity.ARG_TITLE, title);
                    membersAdapter = new MembersAdapter(this, realm, user.getMembers());
                } else {
                    //mutual groups
                    String title = getString(R.string.shared_groups);
                    bundle.putString(MainActivity.ARG_TITLE, title);
                    RealmResults<User> results = realm.where(User.class)
                            .equalTo(User.FIELD_TYPE, User.TYPE_GROUP)
                            .equalTo(User.FIELD_MEMBERS + "." + User.FIELD_ID, user.getUserId())
                            .findAllSorted(User.FIELD_NAME, Sort.ASCENDING);
                    usersAdapter = new UsersAdapter(this, realm, results);
                }
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
        getMenuInflater().inflate(R.menu.menu_users, menu);
        return true;
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
        if (userId != null && userManager.isGroup(userId)) {
            return membersAdapter;
        }
        return usersAdapter;
    }

    @Override
    public Filterable filter() {
        if (userId != null) {
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
    public Set<String> selectedItems() {
        return Collections.emptySet();
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
            UiHelpers.enterChatRoom(this, user.getUserId());
        }
    }

    @Override
    public ViewGroup searchBar() {
        return ((ViewGroup) findViewById(R.id.search_bar));
    }

    public class MembersAdapter extends UsersAdapter {

        private List<User> entries;

        public MembersAdapter(Context context, Realm realm, List<User> entries) {
            super(context, realm, null);
            entries = User.copy(entries);
            Collections.sort(entries, new Comparator<User>() {
                @Override
                public int compare(User lhs, User rhs) {
                    return lhs.getName().compareToIgnoreCase(rhs.getName());
                }
            });
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

    @Override
    public final View getToolBar() {
        return toolBar;
    }
}
