package com.pair.ui;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;

import com.google.i18n.phonenumbers.NumberParseException;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.util.PhoneNumberNormaliser;
import com.pair.util.UiHelpers;
import com.rey.material.app.DialogFragment;
import com.rey.material.app.ToolbarManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import io.realm.Realm;
import io.realm.RealmQuery;

public class CreateGroupActivity extends ActionBarActivity implements AdapterView.OnItemClickListener, ItemsSelector.OnFragmentInteractionListener, ToolbarManager.OnToolbarGroupChangedListener {

    private Set<String> selectedUsers = new HashSet<>();
    private String groupName;
    private Realm realm;
    private List<User> users;
    private UsersAdapter adapter;
    private EditText groupNameEt;
    private Toolbar toolBar;
    private ToolbarManager manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        toolBar = ((Toolbar) findViewById(R.id.main_toolbar));
        manager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);
        manager.setCurrentGroup(R.id.setup);
        manager.registerOnToolbarGroupChangedListener(this);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        groupNameEt = ((EditText) findViewById(R.id.et_group_name));
        realm = Realm.getInstance(this);
        users = realm.where(User.class).notEqualTo(User.FIELD_ID, UserManager.getInstance()
                .getMainUser().getUserId()).notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP)
                .findAllSorted(User.FIELD_NAME);
        adapter = new UsersAdapter(users);
    }

    private void proceedToAddMembers() {
        String name = groupNameEt.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            // TODO: 8/5/2015 validate group name
            UiHelpers.showErrorDialog(this, getString(R.string.cant_be_empty));
            groupNameEt.requestFocus();
        } else if (!groupNamePattern.matcher(name).matches()) {
            UiHelpers.showErrorDialog(this, getString(R.string.group_name_format_message).toUpperCase());
            groupNameEt.requestFocus();
        } else if (UserManager.getInstance().isGroup(User.generateGroupId(name))) {
            UiHelpers.showErrorDialog(this, getString(R.string.group_already_exists, name).toUpperCase());
            groupNameEt.requestFocus();
        } else {
            groupNameEt.setVisibility(View.GONE);
            groupName = name;
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.rl_main_container, new ItemsSelector())
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit();
            manager.setCurrentGroup(R.id.empty_group);
            supportInvalidateOptionsMenu();
        }
    }

    private static final Pattern groupNamePattern = Pattern.compile("^[a-zA-Z][a-zA-Z0-9 ]{4,30}");

    private final UiHelpers.Listener cancelProgress = new UiHelpers.Listener() {
        @Override
        public void onClick() {
            finish();
        }
    };

    private void promptAndExit() {
        UiHelpers.showErrorDialog(this, R.string.st_sure_to_exit, R.string.i_know, android.R.string.no, cancelProgress, null);
    }

    @Override
    public void onBackPressed() {
        promptAndExit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        manager.createMenu(R.menu.menu_create_group);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        manager.onPrepareMenu();
        //there is a bug in the library we are using which causes it duplicate
        // the menu items so this is a fix for it
        menu = toolBar.getMenu();
        int size = menu.size();
        if (size > 2) {
            boolean found = false;
            for (int i = 0; i < size; i++) {
                MenuItem item = menu.getItem(i);
                if (found) {
                    item.setVisible(false);
                } else if (item.isVisible()) {
                    found = true;
                }
            }
        }
        return true;// super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == android.R.id.home) {
            promptAndExit();
            return true;
        }

        if (id == R.id.action_proceed) {
            proceedToAddMembers();
            return true;
        }

        if (id == R.id.action_done) {
            completeGroupCreation();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void completeGroupCreation() {
        if (!selectedUsers.isEmpty()) {
            final DialogFragment progressDialog = UiHelpers.newProgressDialog();
            progressDialog.show(getSupportFragmentManager(), null);
            UserManager.getInstance().createGroup(groupName, new ArrayList<>(selectedUsers), new UserManager.CreateGroupCallBack() {
                @Override
                public void done(Exception e, String groupId) {
                    progressDialog.dismiss();
                    if (e != null) {
                        UiHelpers.showErrorDialog(CreateGroupActivity.this, e.getMessage());
                    } else {
                        UiHelpers.enterChatRoom(CreateGroupActivity.this, groupId);
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        User user = ((User) parent.getAdapter().getItem(position));
        if (((ListView) parent).isItemChecked(position)) {
            selectedUsers.add(user.getUserId());
        } else {
            selectedUsers.remove(user.getUserId());
        }
        if (selectedUsers.size() >= 2) {
            manager.setCurrentGroup(R.id.done_group);
        } else {
            manager.setCurrentGroup(R.id.empty_group); //hide all menu items
        }
        supportInvalidateOptionsMenu();
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
        return true;
    }

    @Override
    public void onCustomAdded(String item) {
        String phoneNumber = item;
        if (!TextUtils.isEmpty(phoneNumber)) {
            String original = phoneNumber; //see usage below
            final String userCountryISO = UserManager.getInstance().getUserCountryISO();
            try {
                phoneNumber = PhoneNumberNormaliser.cleanNonDialableChars(phoneNumber);
                phoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, userCountryISO);
                if (!PhoneNumberNormaliser.isValidPhoneNumber(phoneNumber, userCountryISO)) {
                    throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER, "invalid phone number");
                }
                selectedUsers.add(phoneNumber);
                adapter.notifyDataSetChanged();
                if (selectedUsers.size() >= 2) {
                    manager.setCurrentGroup(R.id.done_group);
                }
                supportInvalidateOptionsMenu();
            } catch (NumberParseException e) {
                // FIXME: 8/5/2015 show a better error message
                final Locale locale = new Locale("", userCountryISO);
                UiHelpers.showErrorDialog(CreateGroupActivity.this, "Phone number: " + original + " may not be a valid phone number in " + locale.getDisplayName());
            }
        } else {
            // FIXME: 8/5/2015 show a better error message
            UiHelpers.showErrorDialog(CreateGroupActivity.this, "cannot be empty");
        }
    }

    @Override
    public void onToolbarGroupChanged(int oldGroupId, int groupId) {
        manager.notifyNavigationStateChanged();
    }

    private class UsersAdapter extends BaseAdapter implements Filterable {

        private List<User> items;

        public UsersAdapter(List<User> items) {
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public User getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return -1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            //noinspection ConstantConditions
            convertView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_checked, parent, false);
            final CheckedTextView checkedTextView = (CheckedTextView) convertView;
            checkedTextView.setText(getItem(position).getName());
            ((ListView) parent).setItemChecked(position, selectedUsers.contains(getItem(position).getUserId()));
            return convertView;
        }

        private void refill(List<User> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @Override
        public Filter getFilter() {
            return filter;
        }

        private Filter filter = new Filter() {
            FilterResults results = new FilterResults();
            protected FilterResults performFiltering(CharSequence constraint) {
                Realm realm = Realm.getInstance(CreateGroupActivity.this); //get realm for this background thread
                List<User> users;
                String constraintAsString = constraint.toString().trim();
                constraintAsString = standardiseConstraintAndContinue(constraintAsString);
                if (constraintAsString == null) {
                    //the publish results will check for this negative value and use
                    // it to detect if it the filter had no effect
                    results.count = -1;
                    //no need to set the results.value field as it will not be used
                    return results;
                }
                try {
                    RealmQuery<User> query = realm.where(User.class)
                            .beginGroup()
                            .contains(User.FIELD_ID, constraintAsString)
                            .or().contains(User.FIELD_NAME, constraintAsString, false) //use contains for name not begins with or equal to
                            .endGroup()
                            .notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP)
                            .notEqualTo(User.FIELD_ID, UserManager.getInstance()
                                    .getMainUser().getUserId());
                    users = query.findAllSorted(User.FIELD_NAME);
                    //detach the objects from realm. as we want to pass it to a different thread
                    // TODO: 8/7/2015 this might not scale
                    users = User.copy(users); //in the future if the results is too large copying might not scale!
                } finally {
                    realm.close();
                }
                results.count = users.size();
                results.values = users;
                return results;
            }

            private String standardiseConstraintAndContinue(String constraintAsString) {
                if (constraintAsString.startsWith("+")) { //then its not in in the IEE format. eg +233XXXXXXXXX (for Ghanaian number)
                    if (constraintAsString.length() > 1) //avoid indexOutOfBoundException
                        constraintAsString = constraintAsString.substring(1);
                    else {
                        return null;
                    }
                } else if (constraintAsString.startsWith("00")) {
                    if (constraintAsString.length() > 2) //avoid indexOutOfBoundException
                        constraintAsString = constraintAsString.substring(2);
                    else {
                        return null;
                    }
                } else if (constraintAsString.startsWith("011")) {
                    if (constraintAsString.length() > 3) //avoid indexOutOfBoundException
                        constraintAsString = constraintAsString.substring(3);
                    else {
                        return null;
                    }
                    //the next condition will never have to worry about input like "00","011 as they will be sieved off!
                } else if (constraintAsString.startsWith("0")) { // TODO: 8/7/2015 replace this with trunk digit of current user currently we using Ghana,France,etc.
                    if (constraintAsString.length() > 1) //avoid indexOutOfBoundException
                        constraintAsString = constraintAsString.substring(1);
                    else {
                        return null;
                    }
                }

                return constraintAsString;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.count < 0) { //the filter the user input cannot be used as filter e.g. "00","0","011",etc so don't do anything
                    return;
                }
                //noinspection unchecked
                refill(((List<User>) results.values));
            }
        };
    }

    private final TextWatcher ADAPTER_FILTER = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            if (adapter != null) {
                if (!TextUtils.isEmpty(s))
                    adapter.getFilter().filter(s.toString());
                else
                    adapter.refill(users);
            }
        }
    };
}
