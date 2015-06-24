package com.pair.pairapp.ui;


import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.ContextMenu;
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
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.pair.workers.ContactSyncService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.realm.RealmChangeListener;


/**
 * A simple {@link ListFragment} subclass.
 */
public class ContactFragment extends ListFragment implements RealmChangeListener {


    private static final String TAG = ContactFragment.class.getSimpleName();

    private ContactsAdapter adapter;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_contact, container, false);
        String title = getArguments().getString(MainActivity.ARG_TITLE);
        ActionBarActivity activity = (ActionBarActivity) getActivity();
        //noinspection ConstantConditions
        activity.getSupportActionBar().setTitle(title);

        List<Contact> contacts = new ArrayList<>();
        adapter = new ContactsAdapter(contacts);
        ContactsManager.INSTANCE.findAllContacts(null, comparator, contactsFindCallback);
        setListAdapter(adapter);
        return view;
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
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
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
    public void onChange() {
        ContactsManager.INSTANCE.findAllContacts(null, comparator, contactsFindCallback);
    }
}
