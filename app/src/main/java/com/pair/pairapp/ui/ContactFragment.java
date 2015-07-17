package com.pair.pairapp.ui;


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.pair.adapter.ContactsAdapter;
import com.pair.data.ContactsManager;
import com.pair.data.ContactsManager.Contact;
import com.pair.data.User;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;
import com.pair.workers.ContactSyncService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmChangeListener;


/**
 * A simple {@link ListFragment} subclass.
 */
public class ContactFragment extends ListFragment implements RealmChangeListener {


    private static final String TAG = ContactFragment.class.getSimpleName();

    private ContactsAdapter adapter;
    Realm realm;
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

    private final ContactsManager.FindCallback<List<Contact>> contactsFindCallback = new ContactsManager.FindCallback<List<Contact>>() {
        @Override
        public void done(List<Contact> contacts) {
            adapter.refill(contacts);
        }
    };

    public ContactFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Activity activity) {
        setRetainInstance(true);
        super.onAttach(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_contact, container, false);
        List<Contact> contacts = new ArrayList<>();
        adapter = new ContactsAdapter(contacts, false);
        final ContactsManager.Filter<Contact> filter = new ContactsManager.Filter<Contact>() {
            @Override
            public boolean accept(Contact contact) {
                User user = UserManager.INSTANCE.getMainUser(); //main user cannot be null
                return !(contact.phoneNumber.equals(user.get_id()));
            }
        };
        ContactsManager.INSTANCE.findAllContacts(filter, comparator, contactsFindCallback);
        setListAdapter(adapter);
        return view;
    }

    @Override
    public void onStart() {
        if (realm == null) {
            realm = Realm.getInstance(getActivity());
        }
        realm.addChangeListener(this);
        super.onStart();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Contact contact = ((ContactsAdapter.ViewHolder) v.getTag()).contact; //very safe
        if (contact.isRegisteredUser) {
            UiHelpers.enterChatRoom(getActivity(), contact.phoneNumber);
        } else {
            Log.d(TAG, "clicked an unregistered user");
        }
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.contact_fragment_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            ContactSyncService.start(getActivity());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        realm.removeChangeListener(this);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    @Override
    public void onChange() {
        ContactsManager.INSTANCE.findAllContacts(null, comparator, contactsFindCallback);
    }
}
