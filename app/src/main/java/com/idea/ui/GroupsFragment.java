package com.idea.ui;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.idea.adapter.GroupsAdapter;
import com.idea.util.UiHelpers;
import com.idea.data.Message;
import com.idea.data.User;
import com.idea.data.UserManager;
import com.idea.pairapp.R;
import com.rey.material.widget.FloatingActionButton;

import io.realm.Realm;
import io.realm.RealmResults;


/**
 * A simple {@link ListFragment} subclass.
 */
public class GroupsFragment extends ListFragment {
    private Realm realm;

    public GroupsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context activity) {
        setRetainInstance(true);
        super.onAttach(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_groups, container, false);
        FloatingActionButton actionButton = ((FloatingActionButton) view.findViewById(R.id.fab_new_group));
        actionButton.setIcon(getResources().getDrawable(R.drawable.ic_group_add_white_24dp), false);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gotoCreateGroupActivity();
            }
        });
        realm = Realm.getInstance(getActivity());
        UserManager.getInstance().refreshGroups();
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final RealmResults<User> groups = realm.where(User.class).equalTo(Message.FIELD_TYPE, User.TYPE_GROUP).findAllSorted(User.FIELD_NAME);
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
        String groupId = ((User) l.getAdapter().getItem(position)).getUserId(); //very safe
        UiHelpers.enterChatRoom(getActivity(), groupId);
    }

    private void gotoCreateGroupActivity() {
        startActivity(new Intent(getActivity(), CreateGroupActivity.class));
    }
}