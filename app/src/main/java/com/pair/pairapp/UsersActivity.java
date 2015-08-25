package com.pair.pairapp;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Filterable;

import com.pair.adapter.UsersAdapter;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.ui.ItemsSelector;
import com.pair.util.UiHelpers;
import com.rey.material.app.ToolbarManager;

import io.realm.Realm;
import io.realm.RealmResults;

public class UsersActivity extends ActionBarActivity implements ItemsSelector.OnFragmentInteractionListener {

    private Toolbar toolBar;
    private ToolbarManager toolbarManager;
    private UsersAdapter usersAdapter;
    private Realm realm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_users);
        //noinspection ConstantConditions
        toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        toolbarManager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        realm = User.Realm(this);
        RealmResults<User> results = realm.where(User.class)
                .notEqualTo(User.FIELD_ID, UserManager.getInstance()
                        .getMainUser()
                        .get_id())
                .findAllSorted(User.FIELD_NAME, true);
        usersAdapter = new UsersAdapter(this, realm, results);
        Bundle bundle = getIntent().getExtras();
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
        return usersAdapter;
    }

    @Override
    public Filterable filter() {
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
        UiHelpers.enterChatRoom(this, user.get_id());
    }
}
