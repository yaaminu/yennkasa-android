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
    public View getView(int position, View convertView, ViewGroup parent) {
        Contact contact = ((Contact) getItem(position));
        if (contact.isRegisteredUser) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.registered_contact_item, parent, false);
            TextView userName = ((TextView) convertView.findViewById(R.id.tv_user_name)),
                    userStatus = ((TextView) convertView.findViewById(R.id.tv_user_status));
            userName.setText(contact.name);
            userStatus.setText(contact.status);
            convertView.setTag(contact);
        } else {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.unregistered_contact_item, parent, false);
            TextView userName = ((TextView) convertView.findViewById(R.id.tv_user_name)),
                    userStatus = ((TextView) convertView.findViewById(R.id.tv_phone_number));
            Button button = (Button) convertView.findViewById(R.id.bt_invite);
            button.setOnClickListener(new InviteContact(contact));
            userName.setText(contact.name);
            userStatus.setText(contact.phoneNumber);
            convertView.setClickable(false); //disable click
        }

        return convertView;
    }

    public void refill(List<Contact> contacts) {
        this.contacts = contacts;
        notifyDataSetChanged();
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


}
