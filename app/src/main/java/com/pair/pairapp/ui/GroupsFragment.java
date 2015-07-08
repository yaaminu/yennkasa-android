package com.pair.pairapp.ui;


import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.pair.adapter.GroupsAdapter;
import com.pair.data.User;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;

import io.realm.Realm;
import io.realm.RealmResults;


/**
 * A simple {@link ListFragment} subclass.
 */
public class GroupsFragment extends ListFragment {


    public GroupsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groups, container, false);
        Realm realm = Realm.getInstance(getActivity());
        String title = getArguments().getString(MainActivity.ARG_TITLE);
        ((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(title);
        RealmResults<User> groups = realm.where(User.class).equalTo("type", User.TYPE_GROUP).findAllSorted("name");
        BaseAdapter adapter = new GroupsAdapter(getActivity(), groups);
        setListAdapter(adapter);
        return view;
    }

}
