package com.pair.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.data.Conversation;
import com.pair.data.User;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;

import io.realm.Realm;
import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.getRelativeTimeSpanString;

/**
 * @author Null-Pointer on 5/30/2015.
 */
public class ConversationAdapter extends RealmBaseAdapter<Conversation> {
    private static final String TAG = ConversationAdapter.class.getSimpleName();

    public ConversationAdapter(Context context, RealmResults<Conversation> realmResults, boolean automaticUpdate) {
        super(context, realmResults, automaticUpdate);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.inbox_list_item_row, parent, false);
            holder = new ViewHolder();
            holder.chatSummary = (TextView) convertView.findViewById(R.id.tv_chat_summary);
            holder.dateLastActive = (TextView) convertView.findViewById(R.id.tv_date_last_active);
            holder.peerName = (TextView) convertView.findViewById(R.id.tv_sender);
            holder.senderAvatar = (ImageView) convertView.findViewById(R.id.iv_user_avatar);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        Conversation conversation = getItem(position);
        holder.chatSummary.setText(conversation.getSummary());
        //TODO find a better way to handle this peer name thing
        String peerName = getPeerName(conversation.getPeerId());

        holder.peerName.setText(peerName);
        long now = System.currentTimeMillis();
        CharSequence formattedDate = getRelativeTimeSpanString(conversation.getLastActiveTime().getTime(), now, MINUTE_IN_MILLIS);
        holder.dateLastActive.setText(formattedDate);

        String summary = conversation.getSummary();
        if (TextUtils.isEmpty(summary)) {
            if (BuildConfig.DEBUG) { //development environment, crash and burn!
                throw new RuntimeException("conversation with no description and message");
            }
            summary = "Conversation with " + peerName;
        }
        holder.chatSummary.setText(summary);
        holder.currentConversation = conversation;
        return convertView;
    }

    private String getPeerName(String peerId) {
        Realm realm = Realm.getInstance(context);
        String peerName = realm.where(User.class).equalTo("_id", peerId).findFirst().getName();
        realm.close();
        return peerName;
    }

    public class ViewHolder {
        public Conversation currentConversation; //holds current item to be used by callers outside this adapter.
        TextView chatSummary, dateLastActive, peerName;
        ImageView senderAvatar;
    }

}
