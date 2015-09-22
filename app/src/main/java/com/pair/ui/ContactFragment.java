package com.pair.ui;


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;

import com.pair.adapter.ContactsAdapter;
import com.pair.data.ContactsManager;
import com.pair.data.ContactsManager.Contact;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.pair.workers.ContactSyncService;
import com.rey.material.widget.FloatingActionButton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmChangeListener;


/**
 * A simple {@link ListFragment} subclass.
 */
public class ContactFragment extends ListFragment implements RealmChangeListener, AbsListView.OnScrollListener {

    private static final String TAG = ContactFragment.class.getSimpleName();
    private ContactsAdapter adapter;
    private Realm realm;
    private static List<Contact> contacts = new ArrayList<>();
    private final ContactsManager.Filter<Contact> filter = new ContactsManager.Filter<Contact>() {
        @Override
        public boolean accept(Contact contact) {
            return !UserManager.getInstance().isCurrentUser(contact.numberInIEE_Format);
        }
    };
    private final Comparator<Contact> comparator = new Comparator<Contact>() {
        @Override
        public int compare(Contact lhs, Contact rhs) {
            if (isBothRegistered(lhs, rhs)) { //both are registered
                return lhs.name.compareToIgnoreCase(rhs.name);
            } else if (isBothNotRegistered(lhs, rhs)) { //both are not registered
                return lhs.name.compareToIgnoreCase(rhs.name);
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
        public void done(List<Contact> freshContacts) {
            ((TextView) listView.getEmptyView()).setText(R.string.st_empty_contacts);
            contacts = freshContacts;
            adapter.refill(contacts);
        }
    };
    private ListView listView;

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
        adapter = new ContactsAdapter(getActivity(), contacts, false);
        //required so that we can operate on it with no fear since calling getListView before onCreateView returns is not safe
        listView = ((ListView) view.findViewById(android.R.id.list));
        listView.setOnScrollListener(this);
        TextView emptyView = (TextView) view.findViewById(android.R.id.empty);
        emptyView.setText(R.string.loading);
        setListAdapter(adapter);
        FloatingActionButton actionButton = ((FloatingActionButton) view.findViewById(R.id.fab_refresh));
        actionButton.setIcon(getResources().getDrawable(R.drawable.ic_action_replay), false);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ContactSyncService.startIfRequired(getActivity());
            }
        });
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
        Contact contact = (Contact) l.getAdapter().getItem(position); //very safe
        if (contact.isRegisteredUser) {
            UiHelpers.enterChatRoom(getActivity(), contact.numberInIEE_Format);
        } else {
            Log.d(TAG, "clicked an unregistered user");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ContactsManager.getInstance().findAllContacts(filter, comparator, contactsFindCallback);
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
        ContactsManager.getInstance().findAllContacts(filter, comparator, contactsFindCallback);
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }

}

