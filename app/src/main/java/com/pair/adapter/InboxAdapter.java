package com.pair.adapter;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.data.Chat;
import com.pair.pairapp.R;

import java.util.Date;

import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 5/30/2015.
 */
public class InboxAdapter extends RealmBaseAdapter<Chat> {
    private static final String TAG = InboxAdapter.class.getSimpleName();
    public InboxAdapter(Context context, RealmResults<Chat> realmResults, boolean automaticUpdate) {
        super(context, realmResults, automaticUpdate);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        if(convertView ==null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.inbox_list_item_row, parent, false);
            holder = new ViewHolder();
            holder.chatSummary = (TextView) convertView.findViewById(R.id.tv_chat_summary);
            holder.dateLastActive = (TextView) convertView.findViewById(R.id.tv_date_last_active);
            holder.peerName = (TextView) convertView.findViewById(R.id.tv_sender);
            holder.senderAvatar = (ImageView) convertView.findViewById(R.id.iv_user_avatar);
            convertView.setTag(holder);
        }else{
            holder =(ViewHolder) convertView.getTag();
        }
        Chat chat = getItem(position);
        holder.chatSummary.setText(chat.getSummary());
        holder.peerName.setText(chat.getPeer().getName());
        holder.dateLastActive.setText(DateUtils.formatDateRange(context,new Date().getTime(),chat.getLastActiveTime().getTime(),DateUtils.FORMAT_NO_YEAR));
        return convertView;
    }

    private class ViewHolder {
        TextView chatSummary,dateLastActive,peerName;
        ImageView senderAvatar;
    }
}
