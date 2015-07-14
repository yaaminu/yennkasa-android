package com.pair.pairapp.ui;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.Toast;

import com.pair.data.ContactsManager;
import com.pair.data.User;
import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.realm.Realm;

public class FriendsActivity extends ActionBarActivity implements AdapterView.OnItemClickListener {

    public static final String EXTRA_GROUP_ID = "groupId";
    public static final String EXTRA_ACTION = "action",
            EXTRA_ACTION_ADD = "add",
            EXTRA_ACTION_REMOVE = "remove";
    private ListView listView;
    private List<String> selectedFriends;
    private String action, groupId;


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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_friends_);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        listView = ((ListView) findViewById(R.id.lv_friends_list));

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        action = getIntent().getStringExtra(EXTRA_ACTION);
        selectedFriends = new ArrayList<>();
        pdialog = new ProgressDialog(this);
        refreshDisplay();
    }

    private void refreshDisplay() {
        supportInvalidateOptionsMenu();
        Realm realm = Realm.getInstance(this);
        User group = realm.where(User.class).equalTo("_id", groupId).findFirst();
        final List<String> membersId = User.aggregateUserIds(group.getMembers(), null);
        realm.close();
        final ContactsManager.Filter<ContactsManager.Contact> filter = new ContactsManager.Filter<ContactsManager.Contact>() {
            @Override
            public boolean accept(ContactsManager.Contact contact) {
                User user = UserManager.INSTANCE.getMainUser(); //main user cannot be null
                return !(contact.phoneNumber.equals(user.get_id())) && contact.isRegisteredUser && !membersId.contains(contact.phoneNumber);
            }
        };
        ContactsManager.INSTANCE.findAllContacts(filter, comparator, new ContactsManager.FindCallback<List<ContactsManager.Contact>>() {
            @Override
            public void done(List<ContactsManager.Contact> contacts) {
                Adapter adapter = new Adapter(contacts);
                listView.setAdapter(adapter);
                listView.setOnItemClickListener(FriendsActivity.this);
            }
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_ok).setVisible(!selectedFriends.isEmpty());
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
        if (id == R.id.action_ok && !selectedFriends.isEmpty()) {
            UserManager manager = UserManager.INSTANCE;
            pdialog.setMessage(getString(R.string.st_please_wait));
            pdialog.setCancelable(false);
            if (action.equals(EXTRA_ACTION_ADD)) {
                pdialog.show();
                manager.addMembers(groupId, selectedFriends, CALLBACK);
            } else if (action.equals(EXTRA_ACTION_REMOVE)) {
                pdialog.show();
                manager.removeMembers(groupId, selectedFriends, CALLBACK);
            } else {
                throw new UnsupportedOperationException("unknown action passed as extra");
            }
            return true;
        } else if (id == android.R.id.home) {
            goBack();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    private void goBack() {
        UiHelpers.enterChatRoom(this, groupId);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ContactsManager.Contact contact = ((ContactsManager.Contact) view.getTag());
        if (listView.isItemChecked(position)) {
            selectedFriends.add(contact.phoneNumber);
        } else {
            selectedFriends.remove(contact.phoneNumber);
        }
        supportInvalidateOptionsMenu();
    }

    private class Adapter extends ArrayAdapter<ContactsManager.Contact> {

        public Adapter(List<ContactsManager.Contact> contacts) {
            super(FriendsActivity.this, android.R.layout.simple_list_item_checked, contacts);
        }

        @Override
        public ContactsManager.Contact getItem(int position) {
            return super.getItem(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_checked, parent, false);
            }
            ((CheckedTextView) convertView).setText(getItem(position).name);
            convertView.setTag(getItem(position));
            return convertView;
        }
    }

    private final UserManager.CallBack CALLBACK = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {
            pdialog.dismiss();
            if (e != null) {
                Toast.makeText(Config.getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            } else {
                selectedFriends.clear();
                refreshDisplay();
            }
        }
    };

    private ProgressDialog pdialog;
}
