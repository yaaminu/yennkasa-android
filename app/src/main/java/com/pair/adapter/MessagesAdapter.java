package com.pair.adapter;

import android.app.Activity;
import android.app.AlarmManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
    private static final String TAG = MessagesAdapter.class.getSimpleName();
    private static final long THREE_MINUTES = AlarmManager.INTERVAL_FIFTEEN_MINUTES / 5;
    private User thisUser;
    private UserManager userManager;
    private final int OUTGOING_MESSAGE = 1, INCOMING_MESSAGE = 2, DATE_MESSAGE = 0;

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
            if (isOutgoingMessage(message)) {
                return OUTGOING_MESSAGE;
            } else {
                return INCOMING_MESSAGE;
            }
        }
        return OUTGOING_MESSAGE;
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
            holder.preview = (ImageView) convertView.findViewById(R.id.iv_message_preview);
            holder.dateComposed = ((TextView) convertView.findViewById(R.id.tv_message_date));
            convertView.setTag(holder);
        }

        holder = (ViewHolder) convertView.getTag();

        if (isDateMessage(position)) {
            holder.content.setText(message.getMessageBody());
            return convertView;
        } else if (message.getType() == Message.TYPE_TEXT_MESSAGE) {
            //normal message
            holder.preview.setVisibility(View.GONE);
            holder.content.setVisibility(View.VISIBLE);
            holder.content.setText(message.getMessageBody());
        } else if (message.getType() == Message.TYPE_PICTURE_MESSAGE) {
            holder.content.setVisibility(View.GONE);
            holder.preview.setVisibility(View.VISIBLE);
            //for some reasons
            Bitmap image = BitmapFactory.decodeFile(message.getMessageBody());
            holder.preview.setImageBitmap(image);
        }
        hideDateIfClose(position, holder, message);
        return convertView;
    }

    private void hideDateIfClose(int position, ViewHolder holder, Message message) {
        String formattedDate = DateUtils.formatDateTime(context, message.getDateComposed().getTime(), DateUtils.FORMAT_SHOW_TIME);
        //show messages at a particular time together
        if (!isLast(position)) { //prevents out of bound access
            Message nextMessage = getItem(position + 1);
            //ensure they are all from same user
            if ((isOutgoingMessage(message) && isOutgoingMessage(nextMessage))
                    || (!isOutgoingMessage(message) && !isOutgoingMessage(nextMessage))) {
                if (nextMessage.getDateComposed().getTime() - message.getDateComposed().getTime() < THREE_MINUTES) {
                    holder.dateComposed.setVisibility(View.GONE);
                } else {
                    doNotCollapse(holder, formattedDate);
                }
            } else {
                doNotCollapse(holder, formattedDate);
            }
        } else {
            doNotCollapse(holder, formattedDate);
        }
    }

    private void doNotCollapse(ViewHolder holder, String formattedDate) {
        holder.dateComposed.setVisibility(View.VISIBLE);
        holder.dateComposed.setText(formattedDate);
    }

    private boolean isLast(int position) {
        return getCount() - 1 == position;
    }

    private boolean isDateMessage(int position) {
        return getItemViewType(position) == DATE_MESSAGE;
    }

    private boolean isOutgoingMessage(Message message) {
        return (message.getFrom().equals(thisUser.get_id()));
    }

    private class ViewHolder {
        TextView content, dateComposed;
        public ImageView preview;
        //TODO add more fields as we support different media like pics
    }

    int[] layoutResources = {
            R.layout.message_item_session_date,
            R.layout.list_item_message_outgoing,
            R.layout.list_item_message_incoming
    };
}
