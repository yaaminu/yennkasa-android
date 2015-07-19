package com.pair.adapter;

import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.PicassoWrapper;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import java.util.List;

import static com.pair.data.ContactsManager.Contact;

/**
 * @author Null-Pointer on 6/12/2015.
 */
public class ContactsAdapter extends BaseAdapter {
    private static final String TAG = ContactsAdapter.class.getSimpleName();
    private List<Contact> contacts;
    private boolean isAddOrRemoveFromGroup;

    public ContactsAdapter(List<Contact> contacts, boolean isAddOrRemoveFromGroup) {
        this.contacts = contacts;
        this.isAddOrRemoveFromGroup = isAddOrRemoveFromGroup;
    }

    @Override
    public int getCount() {
        return contacts.size();
    }

    @Override
    public Contact getItem(int position) {
        return contacts.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public int getItemViewType(int position) {
        return (isAddOrRemoveFromGroup) ? 2 : getItem(position).isRegisteredUser ? 0 : 1;
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Contact contact = getItem(position);

        final ViewHolder holder;
        int layoutRes = layoutResource[getItemViewType(position)];
        if (convertView == null) {
            //noinspection ConstantConditions
            convertView = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
            holder = new ViewHolder();
            if (isAddOrRemoveFromGroup) {
                holder.userName = ((TextView) convertView);
            } else {
                holder.userName = ((TextView) convertView.findViewById(R.id.tv_user_name));
            }
            holder.userStatus = ((TextView) convertView.findViewById(R.id.tv_user_status));
            holder.inviteButton = (Button) convertView.findViewById(R.id.bt_invite);
            holder.userDp = ((ImageView) convertView.findViewById(R.id.iv_display_picture));
            convertView.setTag(holder);
        } else {
            holder = ((ViewHolder) convertView.getTag());
        }

        holder.contact = contact;
        holder.userName.setText(contact.name);
        if (isAddOrRemoveFromGroup) {
            return convertView;
        }
        if (contact.isRegisteredUser) {
            holder.userStatus.setText(contact.status);
            // FIXME: 7/11/2015 normalise number first
            PicassoWrapper.with(parent.getContext())
                    .load(Config.DP_ENDPOINT + "/" + contact.phoneNumber)
                    .error(R.drawable.avatar_empty)
                    .resize(150, 150)
                    .centerInside()
                    .into(holder.userDp);
            holder.userName.setClickable(true);
            holder.userDp.setClickable(true);
            final View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UiHelpers.gotoProfileActivity(v.getContext(), holder.contact.phoneNumber);
                }
            };
            holder.userName.setOnClickListener(listener);
            holder.userDp.setOnClickListener(listener);
        } else {
            holder.userStatus.setText(contact.phoneNumber);
            final View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + contact.phoneNumber));
                    v.getContext().startActivity(intent);
                }
            };
            holder.userStatus.setOnClickListener(listener);
            holder.userName.setOnClickListener(listener);
            holder.inviteButton.setOnClickListener(new InviteContact(contact));
        }

        return convertView;
    }

    public void refill(List<Contact> contacts) {
        this.contacts = contacts;
        notifyDataSetChanged();
    }

    public class ViewHolder {
        private TextView userName,
                userStatus;
        private Button inviteButton;
        private ImageView userDp;
        public Contact contact;
    }

    private class InviteContact implements View.OnClickListener {

        Contact contact;

        public InviteContact(Contact contact) {
            this.contact = contact;
        }

        @Override
        public void onClick(View v) {
            String message = "sms:Try out PAIRAPP messenger for android . Its free and fast!\\n download here: http://pairapp.com/download";
            SmsManager.getDefault().sendTextMessage(contact.phoneNumber, UserManager.INSTANCE.getMainUser().get_id(), message, null, null);
        }
    }


    private final int[] layoutResource = {
            R.layout.registered_contact_item,
            R.layout.unregistered_contact_item,
            android.R.layout.simple_list_item_checked
    };
}
