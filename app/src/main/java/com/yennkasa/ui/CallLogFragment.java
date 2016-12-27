package com.yennkasa.ui;


import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.yennkasa.R;
import com.yennkasa.adapter.CallLogAdapter;
import com.yennkasa.adapter.YennkasaBaseAdapter;
import com.yennkasa.data.Message;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.messenger.MessengerBus;
import com.yennkasa.util.Event;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.util.ViewUtils;
import com.yennkasa.view.LinearLayout;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.Sort;

import static com.yennkasa.data.CallBody.CALL_TYPE_VIDEO;
import static com.yennkasa.data.CallBody.CALL_TYPE_VOICE;

/**
 * A simple {@link Fragment} subclass.
 */
public class CallLogFragment extends Fragment {

    @Bind(R.id.recycler_view)
    RecyclerView recyclerView;

    @Bind(R.id.empty_view)
    View emptyView;

    Realm realm, userRealm;
    private CallLogAdapter adapter;
    private UserManager instance;

    public CallLogFragment() {
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = UserManager.getInstance();
        realm = Message.REALM();
        userRealm = User.Realm(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_call_log, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        adapter = new CallLogAdapter(delegate);
        realm.addChangeListener(changeListener);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity(), LinearLayout.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
    }


    private final RealmChangeListener<Realm> changeListener = new RealmChangeListener<Realm>() {
        @Override
        public void onChange(Realm r) {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    };
    private final CallLogAdapter.CallLogDelegate delegate = new CallLogAdapter.CallLogDelegate() {
        @NonNull
        @Override
        public Context getContext() {
            return getActivity();
        }

        @Override
        public void onItemClick(YennkasaBaseAdapter<Message> adapter, View view, int position, long id) {
            onItemLongClick(adapter, view, position, id);
        }

        @Override
        public boolean onItemLongClick(final YennkasaBaseAdapter<Message> adapter, View view, final int position, long id) {
            final Message message = adapter.getItem(position);
            String[] items = new String[3];
            final boolean outGoing = Message.isOutGoing(delegate.userRealm(), message);
            items[0] = outGoing ? getString(R.string.call_again) : getString(R.string.call_back);
            items[1] = getString(R.string.send_message);
            items[2] = getString(R.string.delete_log_entry);
            new AlertDialog.Builder(getContext())
                    .setCancelable(true)
                    .setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String peer = outGoing ? message.getTo() : message.getFrom();
                            switch (which) {
                                case 0:
                                    //noinspection ConstantConditions
                                    int callType = message.getCallBody().getCallType();
                                    String tag;
                                    if (callType == CALL_TYPE_VOICE) {
                                        tag = MessengerBus.VOICE_CALL_USER;
                                    } else if (callType == CALL_TYPE_VIDEO) {
                                        tag = MessengerBus.VIDEO_CALL_USER;
                                    } else {
                                        throw new AssertionError();
                                    }
                                    Event event = Event.create(tag, null, peer);
                                    MessengerBus.get(MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS).post(event);
                                    break;
                                case 1:
                                    UiHelpers.enterChatRoom(getContext(), peer);
                                    break;
                                case 2:
                                    final Message copiedMessage = realm.copyFromRealm(message);
                                    realm.beginTransaction();
                                    message.deleteFromRealm();
                                    realm.commitTransaction();
                                    //noinspection ConstantConditions
                                    Snackbar snackbar = Snackbar.make(getView(), R.string.log_entry_deleted_message, Snackbar.LENGTH_LONG);
                                    snackbar.setAction("Undo", new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            realm.beginTransaction();
                                            realm.copyToRealmOrUpdate(copiedMessage);
                                            realm.commitTransaction();
                                        }
                                    });
                                    snackbar.show();
                                    break;
                                default:
                                    throw new AssertionError();
                            }
                        }
                    }).create().show();
            return true;
        }


        @NonNull
        @Override
        public List<Message> dataSet() {
            List<Message> calls = realm.where(Message.class).equalTo(Message.FIELD_TYPE, Message.TYPE_CALL).findAllSorted(Message.FIELD_DATE_COMPOSED, Sort.DESCENDING);
            if (calls.isEmpty()) {
                ViewUtils.showViews(emptyView);
            } else {
                ViewUtils.hideViews(emptyView);
            }
            return calls;
        }

        @Override
        public Realm userRealm() {
            return userRealm;
        }

        @NonNull
        @Override
        public User getUser(String peerId) {
            return instance.fetchUserIfRequired(userRealm, peerId);
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        recyclerView.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroy() {
        realm.close();
        userRealm.close();
        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        //noinspection ConstantConditions
        realm.removeChangeListener(changeListener);
        ButterKnife.unbind(getView());
        super.onDestroyView();
    }

    @OnClick(R.id.fab_new_call)
    void newCall(View view) {
        ((MainActivity) getActivity()).setPagePosition(MainActivity.PEOPLE_TAB);
    }
}
