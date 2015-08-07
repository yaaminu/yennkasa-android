package com.pair.pairapp.ui;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;

import com.pair.adapter.GroupsAdapter;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.pairapp.CreateGroupActivity;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import io.realm.Realm;
import io.realm.RealmResults;


/**
 * A simple {@link ListFragment} subclass.
 */
public class GroupsFragment extends ListFragment {
    private Realm realm;
    private EditText et;

    public GroupsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Activity activity) {
        setRetainInstance(true);
        super.onAttach(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_groups, container, false);
        realm = Realm.getInstance(Config.getApplicationContext());
        UserManager.getInstance().refreshGroups();
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        RealmResults<User> groups = realm.where(User.class).equalTo(Message.FIELD_TYPE, User.TYPE_GROUP).findAllSorted(User.FIELD_NAME);
        BaseAdapter adapter = new GroupsAdapter(getActivity(), groups);
        setListAdapter(adapter);
    }


    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        String groupId = ((GroupsAdapter.ViewHolder) v.getTag()).groupId; //very safe
        UiHelpers.enterChatRoom(getActivity(), groupId);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, R.id.action_createGroup, 0, "Create Group");
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        long id = item.getItemId();
        if (id == R.id.action_createGroup) {
            startActivity(new Intent(getActivity(), CreateGroupActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("unused")
    public Dialog createDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        @SuppressWarnings("ConstantConditions") View view = LayoutInflater.from(getActivity()).inflate(R.layout.create_group, null);
        et = ((EditText) view.findViewById(R.id.et_group_name));
        builder.setTitle("Create Group")
                .setCancelable(false)
                .setView(view)
                .setPositiveButton(android.R.string.ok, listener)
                .setNegativeButton(android.R.string.no, null);
        return builder.create();
    }

    final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            String groupName = UiHelpers.getFieldContent(et);
            if (!TextUtils.isEmpty(groupName)) {
                //TODO use regex to validate name
                MainActivity.groupName = groupName;
                Intent intent = new Intent(getActivity(), FriendsActivity.class);
                startActivityForResult(intent, MainActivity.SELECT_USERS_REQUEST);
            }
        }
    };
}
