package com.pair.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.PairApp;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.ui.DPLoader;
import com.pair.util.UiHelpers;

import java.util.Date;

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
        ViewHolder holder;
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
        final Conversation conversation = getItem(position);
        holder.chatSummary.setText(conversation.getSummary());
        Log.d(TAG, conversation.toString());
        User peer = UserManager.getInstance().fetchUserIfNeeded(conversation.getPeerId());
        String peerName = peer.getName();
        holder.peerName.setText(peerName);
        DPLoader.load(context, peer.getUserId(), peer.getDP())
                .error(User.isGroup(peer) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .placeholder(User.isGroup(peer) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .resize(150, 150)
                .into(holder.senderAvatar);
        Message message = conversation.getLastMessage();
        StringBuilder summary = new StringBuilder();
        if (message == null) {
            summary.append(context.getString(R.string.no_message));
            holder.dateLastActive.setText("");
        } else {
            long now = new Date().getTime();
            long then = message.getDateComposed().getTime();
            CharSequence formattedDate;

            long ONE_MINUTE = 60000;
            formattedDate = ((now - then) < ONE_MINUTE) ? context.getString(R.string.now) : getRelativeTimeSpanString(then, now, MINUTE_IN_MILLIS);
            holder.dateLastActive.setText(formattedDate);

            if (UserManager.getInstance().isGroup(conversation.getPeerId())) {
                if (Message.isOutGoing(message)) {
                    summary.append(context.getString(R.string.you)).append(":  ");
                } else {
                    User user = UserManager.getInstance().fetchUserIfNeeded(message.getFrom());
                    summary.append(user.getName()).append(":  ");
                }
            }
            if (Message.isTextMessage(message)) {
                summary.append(message.getMessageBody());
                // holder.mediaMessageIcon.setVisibility(View.GONE);
            } else {
                summary.append(PairApp.typeToString(context, message.getType()));
            }
        }
        if (message != null && Message.isIncoming(message) && message.getState() != Message.STATE_SEEN) {
            holder.chatSummary.setTextColor(context.getResources().getColor(R.color.black));
        } else {
            holder.chatSummary.setTextColor(context.getResources().getColor(R.color.light_gray));
        }
        holder.chatSummary.setText(summary);
        holder.peerId = conversation.getPeerId();

        final View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiHelpers.gotoProfileActivity(v.getContext(), conversation.getPeerId());
            }
        };
        holder.senderAvatar.setOnClickListener(listener);

        return convertView;
    }

    public class ViewHolder {
        public String peerId; //holds current item to be used by callers outside this adapter.
        TextView chatSummary, dateLastActive, peerName;
        ImageView senderAvatar;
    }

}
