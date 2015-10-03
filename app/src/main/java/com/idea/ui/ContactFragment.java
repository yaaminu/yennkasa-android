package com.idea.ui;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import com.handmark.pulltorefresh.library.ILoadingLayout;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.idea.adapter.ContactsAdapter;
import com.idea.data.ContactsManager;
import com.idea.data.ContactsManager.Contact;
import com.idea.data.User;
import com.idea.data.UserManager;
import com.idea.pairapp.R;
import com.idea.util.TypeFaceUtil;
import com.idea.util.UiHelpers;
import com.idea.util.ViewUtils;
import com.idea.workers.ContactSyncService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmChangeListener;


/**
 * A simple {@link ListFragment} subclass.
 */
public class ContactFragment extends Fragment implements RealmChangeListener, AdapterView.OnItemClickListener, PullToRefreshBase.OnRefreshListener {

    private static final String TAG = ContactFragment.class.getSimpleName();
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
    private ContactsAdapter adapter;
    private Realm realm;
    private TextView emptyTextView;
    private PullToRefreshListView listView;
    private View refreshButton;
    private final View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            refresh();
            UiHelpers.showToast(getString(R.string.refreshing), Toast.LENGTH_LONG);
        }
    };
    private final ContactsManager.FindCallback<List<Contact>> contactsFindCallback = new ContactsManager.FindCallback<List<Contact>>() {
        @Override
        public void done(List<Contact> freshContacts) {
            emptyTextView.setText(R.string.st_empty_contacts);
            ViewUtils.showViews(refreshButton);
            if (freshContacts.size() < 1) {
                ViewUtils.showViews(refreshButton);
                refreshButton.setOnClickListener(listener);
            } else {
                ViewUtils.hideViews(refreshButton);
                refreshButton.setOnClickListener(null);
            }
            contacts = freshContacts;
            adapter.refill(contacts);
            listView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    listView.onRefreshComplete();
                }
            }, 10000);
        }
    };

    public ContactFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context activity) {
        setRetainInstance(true);
        super.onAttach(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        realm = User.Realm(getActivity());
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
        listView = ((PullToRefreshListView) view.findViewById(R.id.list));
        ILoadingLayout proxy = listView.getLoadingLayoutProxy();
        proxy.setRefreshingLabel(getString(R.string.refreshing));
        proxy.setReleaseLabel(getString(R.string.release_to_refresh));
        proxy.setPullLabel(getString(R.string.pull_to_refresh));
        refreshButton = view.findViewById(R.id.refresh_button);
        ViewUtils.setTypeface(((TextView) refreshButton), TypeFaceUtil.ROBOTO_REGULAR_TTF);

        ViewUtils.hideViews(refreshButton);
        listView.setScrollEmptyView(false);
        listView.setOnRefreshListener(this);
        emptyTextView = ((TextView) view.findViewById(R.id.tv_empty));
        emptyTextView.setText(R.string.loading);
        ViewUtils.setTypeface(emptyTextView, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);
        listView.setEmptyView(view.findViewById(R.id.empty_view));
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Contact contact = (Contact) parent.getAdapter().getItem(position);
        if (contact.isRegisteredUser) {
            UiHelpers.enterChatRoom(getActivity(), contact.numberInIEE_Format);
        } else {
            Log.d(TAG, "clicked an unregistered user");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        realm.addChangeListener(this);
        refreshLocalContacts();
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
        refreshLocalContacts();
    }

    private void refreshLocalContacts() {
        ContactsManager.getInstance().findAllContacts(filter, comparator, contactsFindCallback);
    }


    @Override
    public void onRefresh(PullToRefreshBase refreshView) {
        refresh();
    }

    private void refresh() {
        ViewUtils.hideViews(refreshButton);
        emptyTextView.setText(R.string.loading);
        listView.postDelayed(new Runnable() {
            @Override
            public void run() {
                ContactSyncService.syncIfRequired(getActivity());
                refreshLocalContacts();
            }
        }, 10000);
    }

}

