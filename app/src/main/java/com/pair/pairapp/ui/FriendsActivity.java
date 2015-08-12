package com.pair.pairapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListView;

import com.google.i18n.phonenumbers.NumberParseException;
import com.pair.adapter.UsersAdapter;
import com.pair.data.ContactsManager;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.util.PhoneNumberNormaliser;
import com.pair.util.UiHelpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;

public class FriendsActivity extends ActionBarActivity implements AdapterView.OnItemClickListener {

    public static final String EXTRA_GROUP_ID = "groupId";
    public static final String SELECTED_USERS = "results";
    public static final String EXTRA_ACTION = "action";
    public static final String ACTION_SELECT_RECIPIENTS = "selectRecipient";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_EXCLUDE = "exclude";
    private ListView listView;
    private EditText editText;
    private Button addButton;
    private Set<String> selectedUsers;
    private String groupId;
    private ArrayAdapter<ContactsManager.Contact> adapter;
    private Realm realm;

    private final Comparator<ContactsManager.Contact> comparator = new Comparator<ContactsManager.Contact>() {
        @Override
        public int compare(ContactsManager.Contact lhs, ContactsManager.Contact rhs) {
            if (isBothRegistered(lhs, rhs)) { //both are registered
                return lhs.name.compareTo(rhs.name);
            } else if (isBothNotRegistered(lhs, rhs)) { //both are not registered
                return lhs.name.compareTo(rhs.name);
            } else {
                if (lhs.isRegisteredUser) return -1;
                return 1;
            }
        }

        private boolean isBothRegistered(ContactsManager.Contact lhs, ContactsManager.Contact rhs) {
            return (lhs.isRegisteredUser && rhs.isRegisteredUser);
        }

        private boolean isBothNotRegistered(ContactsManager.Contact lhs, ContactsManager.Contact rhs) {
            return (!lhs.isRegisteredUser && !rhs.isRegisteredUser);
        }
    };
    private String TAG = FriendsActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_friends_);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        listView = ((ListView) findViewById(android.R.id.list));
        listView.setEmptyView(findViewById(android.R.id.empty));
        addButton = ((Button) findViewById(R.id.bt_add));
        editText = ((EditText) findViewById(R.id.et_filter_input_box));
        selectedUsers = new HashSet<>();
        if (getIntent().hasExtra(EXTRA_ACTION)) {
            final String action = getIntent().getStringExtra(EXTRA_ACTION);
            if (action.equals(ACTION_SELECT_RECIPIENTS)) {
                String[] exclude = getIntent().getStringArrayExtra(EXTRA_EXCLUDE);
                editText.setVisibility(View.GONE);
                addButton.setVisibility(View.GONE);
                realm = Realm.getInstance(this);
                RealmQuery<User> query = realm
                        .where(User.class)
                        .not().equalTo(User.FIELD_ID, UserManager.getInstance().getMainUser().get_id());
                if (exclude != null) {
                    for (String id : exclude) {
                        query = query.notEqualTo(User.FIELD_ID, id);
                    }
                }
                RealmResults<User> users = query.findAllSorted(User.FIELD_NAME);
                listView.setAdapter(new UsersAdapter(this, users,false));
            } else {
                throw new IllegalArgumentException("extra: " + action + " is unknown");
            }
        } else {
            addMembersToGroup();
        }
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title != null) {
            getSupportActionBar().setTitle(title);
        }
        listView.setOnItemClickListener(FriendsActivity.this);
    }

    private void addMembersToGroup() {
        editText.addTextChangedListener(ADAPTER_FILTER);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String numBerToAdd = editText.getText().toString().trim();
                if (!TextUtils.isEmpty(numBerToAdd)) {
                    try {
                        selectedUsers.add(PhoneNumberNormaliser.toIEE(numBerToAdd, UserManager.getInstance().getUserCountryISO()));
                    } catch (NumberParseException e) {
                        if (BuildConfig.DEBUG) {
                            Log.e(TAG, e.getMessage(), e.getCause());
                        } else {
                            Log.e(TAG, e.getMessage());
                        }
                        UiHelpers.showErrorDialog(getApplicationContext(), e.getMessage());
                        return;
                    }
                    editText.setText("");
                    supportInvalidateOptionsMenu();
                }
            }
        });
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        refreshDisplay();
    }

    private void refreshDisplay() {
        supportInvalidateOptionsMenu();
        ContactsManager.Filter<ContactsManager.Contact> filter;
        if (groupId != null) {
            filter = getContactFilter();
        } else {
            filter = new ContactsManager.Filter<ContactsManager.Contact>() {
                @Override
                public boolean accept(ContactsManager.Contact contact) {
                    return contact.isRegisteredUser;
                }
            };
        }
        ContactsManager.getInstance().findAllContacts(filter, comparator, new ContactsManager.FindCallback<List<ContactsManager.Contact>>() {
            @Override
            public void done(List<ContactsManager.Contact> contacts) {
                adapter = new Adapter(contacts);
                listView.setAdapter(adapter);
            }
        });
    }

    @NonNull
    private ContactsManager.Filter<ContactsManager.Contact> getContactFilter() {
        Realm realm = Realm.getInstance(this);
        final User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
        final List<String> membersId = User.aggregateUserIds(group.getMembers(), null);
        realm.close();
        return new ContactsManager.Filter<ContactsManager.Contact>() {
            @Override
            public boolean accept(ContactsManager.Contact contact) {
                User user = UserManager.getInstance().getMainUser(); //main user cannot be null
                return !(contact.numberInIEE_Format.equals(user.get_id())) && contact.isRegisteredUser && !membersId.contains(contact.numberInIEE_Format);
            }
        };
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_ok).setVisible(!selectedUsers.isEmpty());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_friends_, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_ok && !selectedUsers.isEmpty()) {
            setupResultsAndFinish();
        } else if (id == android.R.id.home) {
            setupResultsAndFinish();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        setupResultsAndFinish();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String itemId;
        try {
            ContactsManager.Contact contact = ((ContactsManager.Contact) parent.getAdapter().getItem(position));
            itemId = contact.numberInIEE_Format;
        } catch (ClassCastException e) {
            User user = ((User) parent.getAdapter().getItem(position));
            itemId = user.get_id();
        }
        if (listView.isItemChecked(position)) {
            selectedUsers.add(itemId);
        } else {
            selectedUsers.remove(itemId);
        }
        supportInvalidateOptionsMenu();
    }

    private class Adapter extends ArrayAdapter<ContactsManager.Contact> {

        public Adapter(List<ContactsManager.Contact> contacts) {
            super(FriendsActivity.this, android.R.layout.simple_list_item_checked, contacts);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            //noinspection ConstantConditions
            convertView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_checked, parent, false);
            final CheckedTextView checkedTextView = (CheckedTextView) convertView;
            checkedTextView.setText(getItem(position).name);
            convertView.setTag(getItem(position));
            return convertView;
        }
    }

    @Override
    protected void onDestroy() {
        if (realm != null) {
            realm.close();
        }
        super.onDestroy();
    }

    private void setupResultsAndFinish() {
        if (selectedUsers.isEmpty()) {
            setResult(RESULT_CANCELED);
        } else {
            Intent intent = new Intent();
            intent.putStringArrayListExtra(SELECTED_USERS, new ArrayList<>(selectedUsers));
            setResult(RESULT_OK, intent);
        }
        finish();
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
                adapter.getFilter().filter(s.toString());
            }
        }
    };
}
