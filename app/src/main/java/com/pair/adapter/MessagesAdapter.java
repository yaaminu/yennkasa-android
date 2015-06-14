package com.pair.adapter;

import android.app.Activity;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.pair.data.Message;
import com.pair.data.User;
import com.pair.pairapp.R;
import com.pair.util.UserManager;

import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 5/31/2015.
 */
public class MessagesAdapter extends RealmBaseAdapter<Message> {
    public static final String TAG = MessagesAdapter.class.getSimpleName();
    private User thisUser;
    private UserManager userManager;
    private final int OWN_MESSAGE = 1, IN_MESSAGE = 2, DATE_MESSAGE = 0;

    public MessagesAdapter(Activity context, RealmResults<Message> realmResults, boolean automaticUpdate) {
        super(context, realmResults, automaticUpdate);
        userManager = UserManager.getInstance(context.getApplication());
        thisUser = userManager.getCurrentUser();
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        if (message.getType() == Message.TYPE_DATE_MESSAGE) {
            return DATE_MESSAGE;
        } else if (message.getType() == Message.TYPE_TEXT_MESSAGE) {
            if (isOwnMessage(message)) {
                return OWN_MESSAGE;
            } else {
                return IN_MESSAGE;
            }
        }

        throw new RuntimeException("impossible");
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        Message message = getItem(position);

        if (convertView == null) {
            int layout = layoutResources[getItemViewType(position)];
            convertView = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            holder = new ViewHolder();
            holder.content = ((TextView) convertView.findViewById(R.id.tv_message_content));
            holder.dateComposed = ((TextView) convertView.findViewById(R.id.tv_message_date));
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        if (isDateMessage(position)) {
            holder.content.setText(message.getMessageBody());
        } else if (message.getType() == Message.TYPE_TEXT_MESSAGE) {
            //normal message
            holder.content.toString();
            holder.content.setText(message.getMessageBody());
            String formattedDate = DateUtils.formatDateTime(context, message.getDateComposed().getTime(), DateUtils.FORMAT_SHOW_TIME);
            holder.dateComposed.setText(formattedDate);
        }
        return convertView;
    }

    private boolean isDateMessage(int position) {
        return getItemViewType(position) == DATE_MESSAGE;
    }

    private boolean isOwnMessage(Message message) {
        return (message.getFrom().equals(thisUser.get_id()));
    }

    private class ViewHolder {
        TextView content, dateComposed;
        //TODO add more fields as we support different media like pics
    }

    int[] layoutResources = {
            R.layout.message_item_session_date,
            R.layout.list_item_message_right,
            R.layout.list_item_message_left
    };
}
