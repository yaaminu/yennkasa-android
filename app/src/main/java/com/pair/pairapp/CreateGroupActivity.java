package com.pair.pairapp;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
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
import android.widget.TextView;

import com.google.i18n.phonenumbers.NumberParseException;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.util.PhoneNumberNormaliser;
import com.pair.util.UiHelpers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.realm.Realm;
import io.realm.RealmQuery;

public class CreateGroupActivity extends ActionBarActivity implements AdapterView.OnItemClickListener, TextView.OnEditorActionListener {

    private Set<String> selectedUsers = new HashSet<>();
    private ListView listView;
    private String groupName;
    private Realm realm;
    private List<User> users;
    private UsersAdapter adapter;
    private EditText groupNameEt, filterEt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);
        listView = ((ListView) findViewById(R.id.lv_peers_to_choose));
        groupNameEt = ((EditText) findViewById(R.id.et_group_name));
        filterEt = ((EditText) findViewById(R.id.et_filter_input_box));
        filterEt.addTextChangedListener(ADAPTER_FILTER);
        filterEt.setOnEditorActionListener(this);
        groupNameEt.setOnEditorActionListener(this);
        final View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.bt_proceed) {
                    proceedToAddMembers();
                } else if (id == R.id.bt_cancel) {
                    promptAndExit();
                } else if (id == R.id.bt_add) {
                    addMemberFromFilterEditText();
                }
            }
        };

        findViewById(R.id.bt_proceed).setOnClickListener(listener);
        findViewById(R.id.bt_cancel).setOnClickListener(listener);
        findViewById(R.id.bt_add).setOnClickListener(listener);
        realm = Realm.getInstance(this);
        users = realm.where(User.class).notEqualTo(User.FIELD_ID, UserManager.getInstance()
                .getMainUser().get_id()).notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP)
                .findAllSorted(User.FIELD_NAME);
        adapter = new UsersAdapter(users);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);
    }

    private void proceedToAddMembers() {
        String name = groupNameEt.getText().toString();
        if (!TextUtils.isEmpty(name)) {
            // TODO: 8/5/2015 validate group name
            groupName = name;
            findViewById(R.id.rl_group_name_panel).setVisibility(View.GONE);
            findViewById(R.id.add_peers_view).setVisibility(View.VISIBLE);
        } else {
            UiHelpers.showErrorDialog(this,getString(R.string.cant_be_empty));
        }
    }

    private void addMemberFromFilterEditText() {
        String phoneNumber = filterEt.getText().toString();
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
            } catch (NumberParseException e) {
                // FIXME: 8/5/2015 show a better error message
                final Locale locale = new Locale("", userCountryISO);
                UiHelpers.showErrorDialog(CreateGroupActivity.this, "Phone number: " + original + " may not be a valid phone number in " + locale.getDisplayName());
            }
            filterEt.setText("");
        } else {
            // FIXME: 8/5/2015 show a better error message
            UiHelpers.showErrorDialog(CreateGroupActivity.this, "cannot be empty");
        }
    }


    private final DialogInterface.OnClickListener cancelProgress = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            finish();
        }
    };

    private void promptAndExit() {
        UiHelpers.showErrorDialog(this, R.string.st_prompt, R.string.st_sure_to_exit, R.string.i_know, android.R.string.no, cancelProgress, null);
    }

    @Override
    public void onBackPressed() {
        promptAndExit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_create_group, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_done).
                setVisible(!(selectedUsers.size() < 2));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_done) {
            completeGroupCreation();
            return true;
        } else if (id == android.R.id.home) {
            promptAndExit();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void completeGroupCreation() {
        if (!selectedUsers.isEmpty()) {
            final ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage(getString(R.string.st_please_wait));
            progressDialog.setCancelable(false);
            progressDialog.show();
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
        if (listView.isItemChecked(position)) {
            selectedUsers.add(user.get_id());
        } else {
            selectedUsers.remove(user.get_id());
        }
        filterEt.setText("");
        supportInvalidateOptionsMenu();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        int id = v.getId();
        if (id == R.id.et_filter_input_box) {
            addMemberFromFilterEditText();
        } else if (id == R.id.et_group_name) {
            proceedToAddMembers();
        }
        return true;
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
            ((ListView) parent).setItemChecked(position, selectedUsers.contains(getItem(position).get_id()));
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

            @Override
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
                                    .getMainUser().get_id());
                    users = query.findAllSorted(User.FIELD_NAME);
                    //detach the objects from realm. as we want to pass it to background thread
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
                if (constraintAsString.startsWith("+")) { //then its not in in the IEE format. i.e +233XXXXXXXXX (for Ghanaian number)
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
