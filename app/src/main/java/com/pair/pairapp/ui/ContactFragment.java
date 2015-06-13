package com.pair.pairapp.ui;


import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.pair.adapter.ContactsAdapter;
import com.pair.data.ContactsManager;
import com.pair.data.ContactsManager.Contact;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


/**
 * A simple {@link ListFragment} subclass.
 */
public class ContactFragment extends ListFragment {


    private static final String TAG = ContactFragment.class.getSimpleName();
    private final Comparator<Contact> comparator = new Comparator<Contact>() {
        @Override
        public int compare(Contact lhs, Contact rhs) {
            if (isBothRegistered(lhs, rhs)) { //both are registered
                return lhs.name.compareTo(rhs.name);
            } else if (isBothNotRegistered(lhs, rhs)) { //both are not registered
                return lhs.name.compareTo(rhs.name);
            } else {
                if (lhs.isRegisteredUser) return -1;
                return 1;
            }
        }

        private boolean isBothRegistered(Contact lhs, Contact rhs) {
            return (lhs.isRegisteredUser && rhs.isRegisteredUser);
        }

        private boolean isBothNotRegistered(Contact lhs, Contact rhs) {
            return (!lhs.isRegisteredUser && !rhs.isRegisteredUser);
        }
    };


    public ContactFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_contact, container, false);
        String title = getArguments().getString(MainActivity.ARG_TITLE);
        ActionBarActivity activity = (ActionBarActivity) getActivity();
        activity.getSupportActionBar().setTitle(title);

        List<Contact> contacts = new ArrayList<>();
        final ContactsAdapter adapter = new ContactsAdapter(contacts);
        ContactsManager.INSTANCE.findAllContacts(comparator, new ContactsManager.FindCallback<List<Contact>>() {
            @Override
            public void done(List<Contact> contacts) {
                adapter.refill(contacts);
            }
        });
        setListAdapter(adapter);
        return view;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Contact contact = ((Contact) v.getTag()); //very safe
        if (contact.isRegisteredUser) {
            UiHelpers.enterChatRoom(getActivity(), contact.phoneNumber);
        } else {
            Log.d(TAG, "clicked an unregistered user");
        }
    }

    public static final ContactFragment INSTANCE = new ContactFragment();
}
