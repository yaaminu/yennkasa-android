package com.pairapp.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pairapp.PairApp;
import com.pairapp.R;
import com.pairapp.data.Conversation;
import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.ui.ImageLoader;
import com.pairapp.ui.PairAppBaseActivity;
import com.pairapp.util.TypeFaceUtil;
import com.pairapp.util.ViewUtils;

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
    private Delegate delegate;


    public ConversationAdapter(Delegate delegate) {
        super(delegate.context(), delegate.dataSet(), delegate.autoUpdate());
        this.delegate = delegate;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.inbox_list_item_row, parent, false);
            holder = new ViewHolder();
            holder.chatSummary = (TextView) convertView.findViewById(R.id.tv_chat_summary);
            holder.dateLastActive = (TextView) convertView.findViewById(R.id.tv_date_last_active);
            holder.peerName = (TextView) convertView.findViewById(R.id.tv_sender);
            holder.senderAvatar = (ImageView) convertView.findViewById(R.id.iv_user_avatar);
            holder.newMessagesCount = (TextView) convertView.findViewById(R.id.tv_new_messages_count);
            ViewUtils.setTypeface(holder.newMessagesCount, TypeFaceUtil.ROBOTO_REGULAR_TTF);
            ViewUtils.setTypeface(holder.chatSummary, TypeFaceUtil.ROBOTO_REGULAR_TTF);
            ViewUtils.setTypeface(holder.peerName, TypeFaceUtil.ROBOTO_BOLD_TTF);
            ViewUtils.setTypeface(holder.dateLastActive, TypeFaceUtil.ROBOTO_REGULAR_TTF);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        final Conversation conversation = getItem(position);
        final int unseenMessages = delegate.unSeenMessagesCount(conversation);
        holder.chatSummary.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        if (unseenMessages > 0) {
            ViewUtils.showViews(holder.newMessagesCount);
            holder.newMessagesCount.setText(String.valueOf(unseenMessages));
        } else {
            ViewUtils.hideViews(holder.newMessagesCount);
        }

        if (delegate.isCurrentUserTyping(conversation.getPeerId())) {
            holder.chatSummary.setText(delegate.context().getResources().getString(R.string.writing));
        } else {
            holder.chatSummary.setText(conversation.getSummary());
        }
        User peer = UserManager.getInstance().fetchUserIfRequired(delegate.realm(), conversation.getPeerId());
        String peerName = peer.getName();
        holder.peerName.setText(peerName);
        TargetOnclick targetOnclick = new TargetOnclick(holder.senderAvatar, conversation.getPeerId());
        ImageLoader.load(context, peer.getDP())
                .error(User.isGroup(peer) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .placeholder(User.isGroup(peer) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .resize((int) context.getResources().getDimension(R.dimen.thumbnail_width), (int) context.getResources().getDimension(R.dimen.thumbnail_height))
                .onlyScaleDown().into(targetOnclick);
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
                    summary.append(UserManager.getInstance().getName(message.getFrom())).append(":  ");
                }
            }
            if (Message.isTextMessage(message)) {
                summary.append(message.getMessageBody());
                // holder.mediaMessageIcon.setVisibility(View.GONE);
            } else if (Message.isCallMessage(message)) {
                holder.chatSummary.setCompoundDrawablesWithIntrinsicBounds(CallLogAdapter.getDrawable(message), 0, 0, 0);
                summary.append("  ").append(Message.getCallSummary(context, message));
            } else {
                summary.append(PairApp.typeToString(context, message));
            }
        }
        holder.chatSummary.setTextColor(context.getResources().getColor(R.color.light_gray));
        if (delegate.isCurrentUserTyping(conversation.getPeerId())) {
            holder.chatSummary.setTextColor(delegate.context().getResources().getColor(R.color.colorPrimaryDark));
        } else if (message != null) {
            if (Message.isIncoming(message) && message.getState() != Message.STATE_SEEN) {
                holder.chatSummary.setTextColor(context.getResources().getColor(R.color.black));
            } else if (message.getState() == Message.STATE_SEND_FAILED) {
                holder.chatSummary.setTextColor(context.getResources().getColor(R.color.red));
            }
            holder.chatSummary.setText(summary);
        }
        holder.peerId = conversation.getPeerId();

        holder.senderAvatar.setOnClickListener(targetOnclick);

        return convertView;
    }

    public class ViewHolder {
        public String peerId; //holds current item to be used by callers outside this adapter.
        TextView chatSummary, dateLastActive, peerName, newMessagesCount;
        ImageView senderAvatar;
    }

    public interface Delegate {
        int unSeenMessagesCount(Conversation conversation);

        RealmResults<Conversation> dataSet();

        PairAppBaseActivity context();

        Realm realm();

        boolean autoUpdate();

        boolean isCurrentUserTyping(String userId);
    }

}
