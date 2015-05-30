package com.pair.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.pair.data.Chat;

import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 5/30/2015.
 */
public class InboxAdapter extends RealmBaseAdapter<Chat> {
    public InboxAdapter(Context context, RealmResults<Chat> realmResults, boolean automaticUpdate) {
        super(context, realmResults, automaticUpdate);
    }


    @Override
    public int getCount() {
        return 10;
    }

    @Override
    public Chat getItem(int i) {
        return new Chat();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false);
        ((TextView) view).setText("item " + position);
        return view;
    }
}
