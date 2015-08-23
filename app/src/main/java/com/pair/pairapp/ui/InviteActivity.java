package com.pair.pairapp.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;

import com.google.i18n.phonenumbers.NumberParseException;
import com.pair.adapter.UsersAdapter;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.util.PhoneNumberNormaliser;
import com.pair.util.UiHelpers;
import com.rey.material.app.ToolbarManager;
import com.rey.material.widget.CheckBox;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;

public class InviteActivity extends ActionBarActivity implements ItemsSelector.OnFragmentInteractionListener {


    public static final String EXTRA_GROUP_ID = "groupId";
    private String TAG = InviteActivity.class.getSimpleName();
    private Realm realm;
    private UsersAdapter usersAdapter;
    private Set<String> selectedUsers;
    private ToolbarManager toolbarManager;
    private Toolbar toolBar;
    private UserManager userManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_invite);
        toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        toolbarManager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        selectedUsers = new HashSet<>();
        userManager = UserManager.getInstance();
        realm = User.Realm(this);
        usersAdapter = new CustomUserAdapter(this, prepareQuery().findAllSorted(User.FIELD_NAME));
        Fragment fragment = new ItemsSelector();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    private RealmQuery<User> prepareQuery() {
        String id = getIntent().getStringExtra(EXTRA_GROUP_ID);
        User potentiallyGroup = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
        if (potentiallyGroup != null) {
            RealmQuery<User> userRealmQuery = realm.where(User.class)
                    .notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP)
                    .notEqualTo(User.FIELD_ID, userManager.getMainUser().get_id());
            List<User> existingMembers = potentiallyGroup.getMembers();
            for (User existingMember : existingMembers) {
                userRealmQuery.notEqualTo(User.FIELD_ID, existingMember.get_id());
            }
            return userRealmQuery;
        } else { //is it an anonymous group? not yet supported
            throw new RuntimeException("anonymous groups are not supported yet");
        }
    }

//    private User createNewGroup() {
//        User user = new User();
//        long count = realm.where(User.class).beginsWith(User.FIELD_ID,"$anonymous$",true).count();
//        user.set_id(User.generateGroupId("$anonymous$"+(count+1)));
//        user.setType(User.TYPE_GROUP);
//        user.setName("Anonymous " + count);
//        user.setAccountCreated();
//    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        toolbarManager.onPrepareMenu();
        menu = toolBar.getMenu();
        menu.findItem(R.id.action_ok).setVisible(!selectedUsers.isEmpty());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        toolbarManager.createMenu(R.menu.menu_friends_);
        return true;

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_ok) {
            Log.i(TAG, "add members" + selectedUsers.toString());
            return true;
        }

        if (id == android.R.id.home) {
            UiHelpers.promptAndExit(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (realm != null) {
            realm.close();
        }
        super.onDestroy();
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
        TextView emptyView = new TextView(this);
        emptyView.setText("Looks like all your friends are already members of this group.\n" +
                " You can invite other users who are not in you contact by adding their numbers directly");
        return emptyView;
    }

    @Override
    public boolean multiChoice() {
        return true;
    }

    @Override
    public boolean supportAddCustom() {
        return true;
    }

    @Override
    public void onCustomAdded(String item) {
        if (TextUtils.isEmpty(item)) {
            UiHelpers.showErrorDialog(this, getString(R.string.enter_a_number));
            return;
        }
        try {
            item = PhoneNumberNormaliser.cleanNonDialableChars(item);
            item = PhoneNumberNormaliser.toIEE(item, userManager.getUserCountryISO());
            if (!PhoneNumberNormaliser.isValidPhoneNumber("+" + item, userManager.getUserCountryISO())) {
                throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER, "invalid phone number");
            }
            selectedUsers.add(item);
            usersAdapter.notifyDataSetChanged();
            supportInvalidateOptionsMenu();
        } catch (NumberParseException e) {
            UiHelpers.showErrorDialog(this, getString(R.string.invalid_phone_number, item));
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        User user = usersAdapter.getItem(position);
        if (((ListView) parent).isItemChecked(position)) {
            selectedUsers.add(user.get_id());
        } else {
            selectedUsers.remove(user.get_id());
        }
        ((CheckBox) view.findViewById(R.id.cb_checked)).setChecked(selectedUsers.contains(usersAdapter.getItem(position).get_id()));
        supportInvalidateOptionsMenu();
    }

    private class CustomUserAdapter extends UsersAdapter {

        public CustomUserAdapter(Context context, RealmResults<User> realmResults) {
            super(context, realmResults, true);
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            final String userId = getItem(position).get_id();
            final CheckBox checkBox = (CheckBox) view.findViewById(R.id.cb_checked);
            checkBox.setChecked(selectedUsers.contains(userId));
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        selectedUsers.add(userId);
                    } else {
                        selectedUsers.remove(userId);
                    }
                    ((ListView) parent).setItemChecked(position, isChecked);
                    supportInvalidateOptionsMenu();
                }
            });
            return view;
        }

        @Override
        protected RealmQuery<User> getOriginalQuery() {
            return prepareQuery();
        }
    }
}
