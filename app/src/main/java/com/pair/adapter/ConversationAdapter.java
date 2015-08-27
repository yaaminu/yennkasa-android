package com.pair.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.Config;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.squareup.picasso.Picasso;

import java.util.Date;

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
    private final Picasso PICASSO;


    public ConversationAdapter(Context context, RealmResults<Conversation> realmResults, boolean automaticUpdate) {
        super(context, realmResults, automaticUpdate);
        PICASSO = Picasso.with(context);
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
        User peer = getPeer(conversation.getPeerId());
        String peerName = peer.getName();
        holder.peerName.setText(peerName);
        String dpUrl = Config.DP_ENDPOINT + "/" + peer.getDP();
        PICASSO.load(dpUrl)
                .error(User.isGroup(peer) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .placeholder(User.isGroup(peer) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .resize(150, 150)
                .into(holder.senderAvatar);

        long now = new Date().getTime();
        long then = conversation.getLastActiveTime().getTime();
        CharSequence formattedDate;

        long ONE_MINUTE = 60000;
        formattedDate = ((now - then) < ONE_MINUTE) ? context.getString(R.string.now) : getRelativeTimeSpanString(then, now, MINUTE_IN_MILLIS);
        holder.dateLastActive.setText(formattedDate);

        Message message = conversation.getLastMessage();
        StringBuilder summary = new StringBuilder();
        if (message == null) {
            summary.append(context.getString(R.string.no_message));
        } else {
            if (UserManager.getInstance().isGroup(conversation.getPeerId())) {
                if (Message.isOutGoing(message)) {
                    summary.append(context.getString(R.string.you)).append(":  ");
                } else {
                    Realm realm = User.Realm(context);
                    User user = realm.where(User.class).equalTo(User.FIELD_ID, message.getFrom()).findFirst();
                    if (user == null) {
                        summary.append(message.getFrom()).append(":  ");
                    } else {
                        summary.append(user.getName()).append(":  ");
                    }
                    realm.close();
                }
            }
            if (Message.isTextMessage(message)) {
                summary.append(message.getMessageBody());
                // holder.mediaMessageIcon.setVisibility(View.GONE);
            } else {
                summary.append(Message.typeToString(context, message.getType()));
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

    private User getPeer(String peerId) {
        Realm realm = Realm.getInstance(context);
        User peer = realm.where(User.class).equalTo(User.FIELD_ID, peerId).findFirst();
        User copy = User.copy(peer); //shallow copy
        realm.close();
        return copy;
    }

//    private String getDescription(int messageType) {
//        switch (messageType) {
//            case Message.TYPE_BIN_MESSAGE:
//                return "File";
//            case Message.TYPE_PICTURE_MESSAGE:
//                return "Picture";
//            case Message.TYPE_VIDEO_MESSAGE:
//                return "video";
//            default:
//                throw new AssertionError("unknown message type");
//        }
//    }

    public class ViewHolder {
        public String peerId; //holds current item to be used by callers outside this adapter.
        TextView chatSummary, dateLastActive, peerName;
        ImageView senderAvatar;
    }

}
