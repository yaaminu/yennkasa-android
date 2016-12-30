package com.yennkasa.ui;


import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.yennkasa.R;
import com.yennkasa.adapter.ContactsAdapter;
import com.yennkasa.data.ContactsManager;
import com.yennkasa.data.ContactsManager.Contact;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.messenger.MessengerBus;
import com.yennkasa.util.Event;
import com.yennkasa.util.TypeFaceUtil;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.util.ViewUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmChangeListener;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;


/**
 * A simple {@link ListFragment} subclass.
 */
public class ContactFragment extends Fragment implements RealmChangeListener<Realm>, SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemClickListener {

    private static final String TAG = ContactFragment.class.getSimpleName();
    private static List<Contact> contacts = new ArrayList<>();
    private final ContactsManager.Filter<Contact> filter = new ContactsManager.Filter<Contact>() {
        @Override
        public boolean accept(Contact contact) {
            return !UserManager.getInstance().isCurrentUser(userRealm, contact.numberInIEE_Format);
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
    private Realm userRealm;
    private TextView emptyTextView;
    private ListView listView;
    private View refreshButton;
    private final View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            refresh();
            UiHelpers.showToast(getString(R.string.refreshing), Toast.LENGTH_LONG);
        }
    };
    private boolean isDestroyed;
    private ContactsAdapter.Delegate delegate = new ContactsAdapter.Delegate() {
        @Override
        public void onContactMenuOptionSelected(final MenuItem item, final Contact contact) {
            if (contact.isRegisteredUser || item.getItemId() == R.id.action_invite) {
                handleMenuItemClick(item.getItemId(), contact);
            } else {
                checkUserAvailability(item.getItemId(), contact);
            }
        }
    };

    private void checkUserAvailability(final int itemId, final Contact contact) {
        final ProgressDialog dialog = new ProgressDialog(getContext());
        dialog.setMessage(getString(R.string.check_user_availability, contact.name, getString(R.string.app_name)));
        dialog.setCancelable(false);
        dialog.show();
        subscription = UserManager.getInstance().isRegistered(contact.numberInIEE_Format)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<User>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        dialog.dismiss();
                        suggestInvitationToUser(contact);
                    }

                    @Override
                    public void onNext(User user) {
                        dialog.dismiss();
                        handleMenuItemClick(itemId, contact);
                    }
                });
    }

    @Nullable
    private Subscription subscription;

    private void suggestInvitationToUser(final Contact contact) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.invite_user, contact.name))
                .setMessage(getString(R.string.invite_prompt, contact.name, getString(R.string.app_name)))
                .setCancelable(true)
                .setPositiveButton(getString(R.string.invite), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        UiHelpers.doInvite(getContext(), contact.numberInIEE_Format);
                    }
                });
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                builder.create().show();
            }
        });
    }

    public void handleMenuItemClick(int itemId, Contact contact) {
        switch (itemId) {
            case R.id.action_call_user:
                Event event = Event.create(MessengerBus.VOICE_CALL_USER, null, contact.numberInIEE_Format);
                MessengerBus.get(MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS).post(event);
                break;
            case R.id.action_video_call_user:
                event = Event.create(MessengerBus.VIDEO_CALL_USER, null, contact.numberInIEE_Format);
                MessengerBus.get(MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS).post(event);
                break;
            case R.id.action_text:
                UiHelpers.enterChatRoom(getContext(), contact.numberInIEE_Format);
                break;
            case R.id.action_invite:
                UiHelpers.doInvite(getContext(), contact.numberInIEE_Format);
                break;
            default:
                throw new AssertionError();
        }
    }

    static class FindCallbackImpl implements ContactsManager.FindCallback<List<Contact>> {
        private final WeakReference<ContactFragment> fragment;

        public FindCallbackImpl(ContactFragment fragment) {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        public void done(Exception e, List<Contact> freshContacts) {
            final ContactFragment contactFragment = fragment.get();
            if (contactFragment == null || contactFragment.isDestroyed) return;
            contactFragment.emptyTextView.setText(R.string.st_empty_contacts);
            ViewUtils.showViews(contactFragment.refreshButton);

            if (e != null) {
                UiHelpers.showPlainOlDialog(fragment.get().getContext(), e.getMessage());
                ViewUtils.showViews(contactFragment.refreshButton);
                contactFragment.refreshButton.setOnClickListener(contactFragment.listener);
            } else {
                if (freshContacts.size() < 1) {
                    ViewUtils.showViews(contactFragment.refreshButton);
                    contactFragment.refreshButton.setOnClickListener(contactFragment.listener);
                } else {
                    ViewUtils.hideViews(contactFragment.refreshButton);
                    contactFragment.refreshButton.setOnClickListener(null);
                }
                contacts = freshContacts;
                contactFragment.adapter.refill(contacts);
                contactFragment.listView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        contactFragment.swipeRefreshLayout.setRefreshing(false);
                    }
                }, 10000);
            }
        }
    }

    private static ContactsManager.FindCallback<List<Contact>> contactsFindCallback;
    private SwipeRefreshLayout swipeRefreshLayout;

    public ContactFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        contactsFindCallback = new FindCallbackImpl(this);
        isDestroyed = false;
        userRealm = User.Realm(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_contact, container, false);
        adapter = new ContactsAdapter(getActivity(),
                UserManager.getInstance().getUserCountryISO(userRealm),
                contacts, false, delegate);
        //required so that we can operate on it with no fear since calling getListView before onCreateView returns is not safe
        listView = ((ListView) view.findViewById(R.id.list));
        swipeRefreshLayout = ((SwipeRefreshLayout) view.findViewById(R.id.swipe_refresh_layout));
        swipeRefreshLayout.setColorSchemeColors(ContextCompat
                .getColor(getContext(), R.color.colorAccent));
        refreshButton = view.findViewById(R.id.refresh_button);
        ViewUtils.setTypeface(((TextView) refreshButton), TypeFaceUtil.ROBOTO_REGULAR_TTF);

        ViewUtils.hideViews(refreshButton);
        swipeRefreshLayout.setOnRefreshListener(this);
        emptyTextView = ((TextView) view.findViewById(R.id.tv_empty));
        emptyTextView.setText(R.string.loading);
        ViewUtils.setTypeface(emptyTextView, TypeFaceUtil.ROBOTO_REGULAR_TTF);
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
            checkUserAvailability(R.id.action_text, contact);
            Log.d(TAG, "clicked an unregistered user");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        userRealm.addChangeListener(this);
        refreshLocalContacts();
    }

    @Override
    public void onPause() {
        userRealm.removeChangeListener(this);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
        userRealm.close();
        isDestroyed = true;
        super.onDestroy();
    }

    @Override
    public void onChange(Realm o) {
        refreshLocalContacts();
    }

    private void refreshLocalContacts() {
        ContactsManager.findAllContacts(filter, comparator, contactsFindCallback);
    }

    private void refresh() {
        ViewUtils.hideViews(refreshButton);
        emptyTextView.setText(R.string.loading);
        listView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    refreshLocalContacts();
                }
            }
        }, 10000);
    }

    @Override
    public void onRefresh() {
        swipeRefreshLayout.setRefreshing(true);
        refresh();
    }
}

