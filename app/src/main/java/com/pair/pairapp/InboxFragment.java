package com.pair.pairapp;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.pair.adapter.InboxAdapter;
import com.pair.data.Chat;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * Created by Null-Pointer on 5/29/2015.
 */
public class InboxFragment extends ListFragment {

    private static final String [] days = {
          //  "Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"
    };
    private Realm realm;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inbox, container, false);
        realm = Realm.getInstance(getActivity());
        RealmResults<Chat> chats = realm.allObjectsSorted(Chat.class, "lastActiveTime", false);
        InboxAdapter adapter = new InboxAdapter(getActivity(),chats,true);
        setListAdapter(adapter);
        return view;
    }

    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }
}
