package com.pair.adapter;

import android.app.Activity;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.pair.data.Message;
import com.pair.data.User;
import com.pair.pairapp.R;
import com.pair.util.UserManager;

import java.util.Date;

import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 5/31/2015.
 */
public class MessagesAdapter extends RealmBaseAdapter<Message> {
    public static final String TAG = MessagesAdapter.class.getSimpleName();
    User thisUser;
    UserManager userManager;

    public MessagesAdapter(Activity context,RealmResults<Message> realmResults, boolean automaticUpdate) {
        super(context, realmResults, automaticUpdate);
        userManager = UserManager.getInstance(context.getApplication());
        thisUser = userManager.getCurrentUser();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        TextView content,dateComposed;
        Message message = getItem(position);
        //TODO use getItemViewType() to eliminate these messy conditions
        int layout = 0;
        if (isOwnMessage(message)) {
            layout = R.layout.list_item_message_right;
        } else {
            layout = R.layout.list_item_message_left;
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(layout,parent,false);
        content = ((TextView) view.findViewById(R.id.tv_message_content));
        dateComposed = ((TextView) view.findViewById(R.id.tv_message_date));
        content.setText(message.getMessageBody());
        DateUtils.formatElapsedTime(new Date().getTime() - message.getDateComposed().getTime());
        dateComposed.setText(message.getDateComposed().toString());
        Log.d(TAG,message.toString());
        return view;
    }

    private boolean isOwnMessage(Message message) {
        return (message.getFrom().equals(thisUser.get_id()));
    }

}
