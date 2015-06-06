package com.pair.pairapp;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.Toast;

import com.pair.adapter.FriendsAdapter;
import com.pair.data.User;
import com.pair.util.UserManager;

import io.realm.Realm;
import io.realm.RealmResults;


/**
 * A simple {@link Fragment} subclass.
 */
public class FriendsFragment extends Fragment {


    public FriendsFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        String title = getArguments().getString(MainActivity.ARG_TITLE);
        ActionBarActivity activity = (ActionBarActivity) getActivity();
        activity.getSupportActionBar().setTitle(title);


        View view = inflater.inflate(R.layout.fragment_freinds, container, false);
        GridView friendsGrid = ((GridView) view.findViewById(R.id.gv_friends));
        Realm realm = Realm.getInstance(getActivity());
        RealmResults<User> results = realm.where(User.class).notEqualTo("_id", getCurrentUser().get_id()).findAllSorted("name", true);
        Toast.makeText(getActivity(),results.size() + "",Toast.LENGTH_SHORT).show();
        FriendsAdapter adapter = new FriendsAdapter(getActivity(), results, true);
        friendsGrid.setAdapter(adapter);
        return view;
    }

    private User getCurrentUser() {
        return UserManager.getInstance(getActivity().getApplication()).getCurrentUser();
    }
}
