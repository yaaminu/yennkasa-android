package com.pair.pairapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
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

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * Created by Null-Pointer on 5/29/2015.
 */
public class CoversationsFragment extends ListFragment {

    private static final String[] days = {
            //  "Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"
    };
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
        return view;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Conversation conversation = ((InboxAdapter.ViewHolder) v.getTag()).currentConversation;
        String peerId = conversation.getPeerId();
        String peerName = conversation.getPeerId();
        enterChatRoom(peerId, peerName);

    }

    private void enterChatRoom(String peerId, String peerName) {
        Intent intent = new Intent(getActivity(), ChatActivity.class);
        intent.putExtra(ChatActivity.PEER_NAME, peerName);
        intent.putExtra(ChatActivity.PEER_ID, peerId);
        startActivity(intent);
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
            enterChatRoom(user.get_id(), user.getName());

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }


}
