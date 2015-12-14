package com.pairapp.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Filterable;
import android.widget.Toast;

import com.google.i18n.phonenumbers.NumberParseException;
import com.pairapp.Errors.ErrorCenter;
import com.pairapp.adapter.MultiChoiceUsersAdapter;
import com.pairapp.adapter.UsersAdapter;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.BuildConfig;
import com.pairapp.R;
import com.pairapp.util.PLog;
import com.pairapp.util.PhoneNumberNormaliser;
import com.pairapp.util.TypeFaceUtil;
import com.pairapp.util.UiHelpers;
import com.pairapp.util.ViewUtils;
import com.rey.material.app.DialogFragment;
import com.rey.material.app.ToolbarManager;
import com.rey.material.widget.CheckBox;
import com.rey.material.widget.SnackBar;
import com.rey.material.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;

public class InviteActivity extends PairAppActivity implements ItemsSelector.OnFragmentInteractionListener {


    public static final String EXTRA_GROUP_ID = "groupId";
    public static final String SELECTED_USER_NAMES = "selectedUserNames";
    public static final String SELECTED_USERS = "selectedUsers";
    private final Set<String> existingGroupMembers = new HashSet<>();
    private String TAG = InviteActivity.class.getSimpleName();
    private Realm realm;
    private UsersAdapter usersAdapter;
    private Set<String> selectedUsers;
    private String groupId;
    private final View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.tv_menu_item_done) {
                proceedToAddMembers();
            }
        }
    };
    private View menuItemDone;
    private Set<String> selectedUserNames = new TreeSet<>();
    private ItemsSelector fragment;
    private Toolbar toolBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_invite);
        toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        menuItemDone = toolBar.findViewById(R.id.tv_menu_item_done);
        menuItemDone.setOnClickListener(listener);

        //noinspection unused
        ToolbarManager toolbarManager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        if (savedInstanceState != null) {
            List<String> tmp = savedInstanceState.getStringArrayList(SELECTED_USERS),
                    tmp2 = savedInstanceState.getStringArrayList(SELECTED_USER_NAMES);
            if (tmp != null && tmp2 != null) {
                selectedUsers = new HashSet<>(tmp);
                selectedUserNames = new HashSet<>(tmp2);
            }
        } else {
            selectedUsers = new HashSet<>();
            selectedUserNames = new HashSet<>();

        }
        realm = User.Realm(this);
        usersAdapter = new CustomUserAdapter(prepareQuery().findAllSorted(User.FIELD_NAME));
        fragment = new ItemsSelector();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    @Override
    protected SnackBar getSnackBar() {
        return ((SnackBar) findViewById(R.id.notification_bar));
    }

//    private User createNewGroup() {
//        User user = new User();
//        long count = realm.where(User.class).beginsWith(User.FIELD_ID,"$anonymous$",true).count();
//        user.setUserId(User.generateGroupId("$anonymous$"+(count+1)));
//        user.setType(User.TYPE_GROUP);
//        user.setName("Anonymous " + count);
//        user.setAccountCreated();
//    }

    private RealmQuery<User> prepareQuery() {
        User potentiallyGroup = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
        if (potentiallyGroup != null) {
            RealmQuery<User> userRealmQuery = realm.where(User.class)
                    .notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP)
                    .notEqualTo(User.FIELD_ID, getMainUserId());
            List<User> existingMembers = potentiallyGroup.getMembers();
            for (User existingMember : existingMembers) {
                userRealmQuery.notEqualTo(User.FIELD_ID, existingMember.getUserId());
                existingGroupMembers.add(existingMember.getUserId());
            }
            return userRealmQuery;
        } else { //is it an anonymous group? not yet supported
            if (BuildConfig.DEBUG) {
                throw new RuntimeException("anonymous groups are not supported yet");
            }
            NavUtils.navigateUpFromSameTask(this);
        }
        return realm.where(User.class).equalTo(User.FIELD_ID, " "); //empty results because we are exiting
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menuItemDone.setVisibility(selectedUsers.isEmpty() ? View.GONE : View.VISIBLE);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            UiHelpers.promptAndExit(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void proceedToAddMembers() {
        PLog.i(TAG, "add members" + selectedUsers.toString());
        final DialogFragment progressView = UiHelpers.newProgressDialog();
        progressView.show(getSupportFragmentManager(), null);
        userManager.addMembersToGroup(groupId, selectedUsers, new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                UiHelpers.dismissProgressDialog(progressView);
                if (e != null) {
                    ErrorCenter.reportError(TAG, e.getMessage());
                } else {
                    NavUtils.navigateUpFromSameTask(InviteActivity.this);
                }
            }
        });
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
        ViewUtils.setTypeface(emptyView, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        emptyView.setText(getString(R.string.add_custom_number));
        emptyView.setTextSize(R.dimen.standard_text_size);
        emptyView.setTextColor(getResources().getColor(R.color.black));
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
    public Set<String> selectedItems() {
        return selectedUserNames;
    }

    @Override
    public void onCustomAdded(String text) {
        if (TextUtils.isEmpty(text)) {
            UiHelpers.showErrorDialog(this, getString(R.string.enter_a_number));
            return;
        }
        String formattedText = text;
        try {
            formattedText = PhoneNumberNormaliser.cleanNonDialableChars(formattedText);
            formattedText = PhoneNumberNormaliser.toIEE(formattedText, userManager.getUserCountryISO());
            if (!PhoneNumberNormaliser.isValidPhoneNumber("+" + formattedText, userManager.getUserCountryISO())) {
                throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER, "invalid phone number");
            }
            finallyAddNumber(formattedText);
        } catch (NumberParseException e) {
            UiHelpers.showErrorDialog(this, getString(R.string.invalid_phone_number, text));
        }
    }

    private void finallyAddNumber(String phoneNumber) {
        if (existingGroupMembers.contains(phoneNumber)) {
            UiHelpers.showErrorDialog(this, getString(R.string.duplicate_group_member));
        } else if (phoneNumber.equals(getMainUserId())) {
            UiHelpers.showErrorDialog(this, getString(R.string.you_add_already_a_member));
        } else if (selectedUsers.contains(phoneNumber)) {
            UiHelpers.showErrorDialog(this, getString(R.string.duplicate_number_notice));
        } else {
            selectedUsers.add(phoneNumber);
            selectedUserNames.add(PhoneNumberNormaliser.toLocalFormat(phoneNumber, getCurrentUser().getCountry()));
            usersAdapter.notifyDataSetChanged();
            fragment.onItemsChanged();
            supportInvalidateOptionsMenu();
            UiHelpers.showToast(getString(R.string.added_custom_notice_toast), Toast.LENGTH_LONG);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//        User user = usersAdapter.getItem(position);
//        if (((ListView) parent).isItemChecked(position)) {
//            selectedUsers.add(user.getUserId());
//            selectedUserNames.add(user.getName());
//            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
//                ((CheckBox) view.findViewById(R.id.cb_checked)).setCheckedImmediately(true);
//            } else {
//                ((CheckBox) view.findViewById(R.id.cb_checked)).setCheckedAnimated(true);
//            }
//        } else {
//            selectedUsers.remove(user.getUserId());
//            selectedUserNames.remove(user.getName());
//            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
//                ((CheckBox) view.findViewById(R.id.cb_checked)).setCheckedImmediately(false);
//            } else {
//                ((CheckBox) view.findViewById(R.id.cb_checked)).setCheckedAnimated(false);
//            }
//        }
//
//        supportInvalidateOptionsMenu();
    }

    @Override
    public ViewGroup searchBar() {
        return ((ViewGroup) findViewById(R.id.search_bar));
    }

    private final MultiChoiceUsersAdapter.Delegate delegate = new MultiChoiceUsersAdapter.Delegate() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id, boolean isSelected) {
            User user = ((User) parent.getAdapter().getItem(position));
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.cb_checked);
            String userName = user.getName();
            if (!userName.startsWith("@")) {
                userName = "@" + userName;
            }
            if (isSelected) {
                selectedUsers.add(user.getUserId());
                selectedUserNames.add(userName);
                if (!checkBox.isChecked()) {
                    checkBox.setChecked(true);
                }
            } else {
                selectedUsers.remove(user.getUserId());
                selectedUserNames.remove(userName);
                if (checkBox.isChecked()) {
                    checkBox.setChecked(false);
                }
            }
            fragment.onItemsChanged();
            supportInvalidateOptionsMenu();
        }

        @Override
        public Context getContext() {
            return InviteActivity.this;
        }
    };

    private class CustomUserAdapter extends MultiChoiceUsersAdapter {

        public CustomUserAdapter(RealmResults<User> realmResults) {
            super(delegate, realm, realmResults, selectedUsers, R.id.cb_checked);
        }

        @Override
        protected RealmQuery<User> getOriginalQuery() {
            return prepareQuery();
        }
    }

    @Override
    public final View getToolBar() {
        return toolBar;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putStringArrayList(SELECTED_USERS, new ArrayList<>(selectedUsers));
        outState.putStringArrayList(SELECTED_USER_NAMES, new ArrayList<>(selectedUserNames));
        super.onSaveInstanceState(outState);
    }

}
