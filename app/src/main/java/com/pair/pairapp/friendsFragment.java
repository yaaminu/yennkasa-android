package com.pair.pairapp;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import com.pair.adapter.FriendsAdapter;
import com.pair.adapter.InboxAdapter;
import com.pair.data.Conversation;
import com.pair.data.User;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import io.realm.Realm;
import io.realm.RealmResults;


/**
 * A simple {@link Fragment} subclass.
 */
public class FriendsFragment extends Fragment implements AdapterView.OnItemClickListener{


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
        FriendsAdapter adapter = new FriendsAdapter(getActivity(), results, true);
        friendsGrid.setAdapter(adapter);
        friendsGrid.setOnItemClickListener(this);
        return view;
    }

    private User getCurrentUser() {
        return UserManager.getInstance(getActivity().getApplication()).getCurrentUser();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        //navigate to chat activity
        User user = ((FriendsAdapter.ViewHolder) view.getTag()).user;
        String peerId = user.get_id();
        String peerName = user.getName();
        UiHelpers.enterChatRoom(getActivity(), peerId, peerName);

    }
}
