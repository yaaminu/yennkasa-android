package com.pair.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.pair.pairapp.R;

import java.util.List;

import static com.pair.data.ContactsManager.Contact;

/**
 * @author Null-Pointer on 6/12/2015.
 */
public class ContactsAdapter extends BaseAdapter {
    private static final String TAG = ContactsAdapter.class.getSimpleName();
    private List<Contact> contacts;

    public ContactsAdapter(List<Contact> contacts) {
        this.contacts = contacts;
    }

    @Override
    public int getCount() {
        return contacts.size();
    }

    @Override
    public Object getItem(int position) {
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
        return ((Contact) getItem(position)).isRegisteredUser ? 0 : 1;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Contact contact = ((Contact) getItem(position));

        ViewHolder holder;
        int layoutRes = layoutResource[getItemViewType(position)];
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
            holder = new ViewHolder();
            holder.userName = ((TextView) convertView.findViewById(R.id.tv_user_name));
            holder.userStatus = ((TextView) convertView.findViewById(R.id.tv_user_status));
            holder.inviteButton = (Button) convertView.findViewById(R.id.bt_invite);
            convertView.setTag(holder);
        } else {
            holder = ((ViewHolder) convertView.getTag());
        }

        holder.userName.setText(contact.name);
        if (contact.isRegisteredUser) {
            holder.userStatus.setText(contact.status);
        } else {
            holder.userStatus.setText(contact.phoneNumber);
            holder.inviteButton.setOnClickListener(new InviteContact(contact));
        }
        holder.contact = contact;
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
        public Contact contact;
    }
    private class InviteContact implements View.OnClickListener {

        Contact contact;

        public InviteContact(Contact contact) {
            this.contact = contact;
        }

        @Override
        public void onClick(View v) {
            Log.d(TAG, "invite button clicked");
        }
    }


    private final int[] layoutResource = {
            R.layout.registered_contact_item,
            R.layout.unregistered_contact_item
    };
}
