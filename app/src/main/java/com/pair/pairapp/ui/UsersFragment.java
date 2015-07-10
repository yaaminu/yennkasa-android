package com.pair.pairapp.ui;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.pair.adapter.UsersAdapter;
import com.pair.data.User;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import io.realm.Realm;
import io.realm.RealmResults;


/**
 * A simple {@link Fragment} subclass.
 */
public class UsersFragment extends Fragment implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {


    public UsersFragment() {
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
        ListView usersList = ((ListView) view.findViewById(R.id.lv_users));
        Realm realm = Realm.getInstance(getActivity());
        RealmResults<User> results = realm.where(User.class).notEqualTo("_id", getCurrentUser().get_id()).findAllSorted("name", true);
        UsersAdapter adapter = new UsersAdapter(getActivity(), results, true);
        usersList.setAdapter(adapter);
        usersList.setOnItemClickListener(this);
        return view;
    }

    private User getCurrentUser() {
        return UserManager.getInstance(getActivity().getApplication()).getMainUser();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        //navigate to chat activity
        User user = ((UsersAdapter.ViewHolder) view.getTag()).user;
        String peerId = user.get_id();
        UiHelpers.enterChatRoom(getActivity(), peerId);

    }


    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

        return true;
    }
}
