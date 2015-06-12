package com.pair.pairapp;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.pair.adapter.InboxAdapter;
import com.pair.data.Conversation;
import com.pair.data.User;
import com.pair.util.UiHelpers;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * Created by Null-Pointer on 5/29/2015.
 */
public class CoversationsFragment extends ListFragment {

    private Realm realm;

    public CoversationsFragment(){} //required no-arg constructor

    @Override
    public void onAttach(Activity activity) {
        setHasOptionsMenu(true);
        super.onAttach(activity);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inbox, container, false);
        realm = Realm.getInstance(getActivity());
        RealmResults<Conversation> conversations = realm.allObjectsSorted(Conversation.class, "lastActiveTime", false);
        InboxAdapter adapter = new InboxAdapter(getActivity(), conversations, true);
        setListAdapter(adapter);
        String title = getArguments().getString(MainActivity.ARG_TITLE);
        ActionBarActivity activity = (ActionBarActivity) getActivity();
        activity.getSupportActionBar().setTitle(title);
        return view;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Conversation conversation = ((InboxAdapter.ViewHolder) v.getTag()).currentConversation;
        String peerId = conversation.getPeerId();
        UiHelpers.enterChatRoom(getActivity(), peerId);

    }



    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.new_message_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.new_message) {
            realm.beginTransaction();
            Conversation conversation = realm.createObject(Conversation.class);
            User user = realm.where(User.class).equalTo("_id","0").findFirst();
            if(user == null){
                user = realm.createObject(User.class);
                user.set_id("0");
                user.setName("Amin");
                user.setStatus("amin\'s status");
            }
            conversation.setPeerId(user.get_id());
            conversation.setLastActiveTime(new Date());
            realm.commitTransaction();
            UiHelpers.enterChatRoom(getActivity(), user.get_id());

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }


}
