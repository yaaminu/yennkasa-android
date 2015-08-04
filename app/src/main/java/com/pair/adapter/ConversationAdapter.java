package com.pair.adapter;

import android.app.AlarmManager;
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
import com.pair.util.Config;
import com.pair.util.PicassoWrapper;
import com.pair.util.UiHelpers;

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
    final long FIVE_MINUTES = AlarmManager.INTERVAL_FIFTEEN_MINUTES / 3;


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
        final Conversation conversation = getItem(position);
        holder.chatSummary.setText(conversation.getSummary());
        User peer = getPeer(conversation.getPeerId());
        String peerName = peer.getName();
        holder.peerName.setText(peerName);
        String dpUrl = Config.DP_ENDPOINT + "/" + peer.getDP();
        PicassoWrapper.with(context)
                .load(dpUrl)
                .error(R.drawable.avatar_empty)
                .placeholder(R.drawable.avatar_empty)
                .resize(150, 150)
                .into(holder.senderAvatar);

        long now = new Date().getTime();
        long then = conversation.getLastActiveTime().getTime();
        CharSequence formattedDate;
        formattedDate = ((now - then) <= FIVE_MINUTES) ? "moments ago" : getRelativeTimeSpanString(then, now, MINUTE_IN_MILLIS);
        holder.dateLastActive.setText(formattedDate);
        String summary = conversation.getSummary();
        if (TextUtils.isEmpty(summary)) {
            if (BuildConfig.DEBUG) { //development environment, crash and burn!
                throw new RuntimeException("conversation with no description and message");
            }
            summary = "Conversation with " + peerName;
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
        User copy = new User(peer); //shallow copy
        realm.close();
        return copy;
    }

    public class ViewHolder {
        public String peerId; //holds current item to be used by callers outside this adapter.
        TextView chatSummary, dateLastActive, peerName;
        ImageView senderAvatar;
    }

}
