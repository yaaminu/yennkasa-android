package com.pair.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.pair.adapter.ConversationAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.UiHelpers;
import com.pair.view.SwipeDismissListViewTouchListener;
import com.rey.material.widget.FloatingActionButton;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * @author by Null-Pointer on 5/29/2015.
 */
public class ConversationsFragment extends ListFragment {

    private static final String TAG = ConversationsFragment.class.getSimpleName();
    private Realm realm;
    private RealmResults<Conversation> conversations;
    private ConversationAdapter adapter;

    public ConversationsFragment() {
    } //required no-arg constructor

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_inbox, container, false);
        realm = Realm.getInstance(Config.getApplicationContext());
        conversations = realm.allObjectsSorted(Conversation.class, Conversation.FIELD_LAST_ACTIVE_TIME, false);
        adapter = new ConversationAdapter(getActivity(), conversations, true);
        FloatingActionButton actionButton = ((FloatingActionButton) view.findViewById(R.id.fab_new_message));
        actionButton.setIcon(getResources().getDrawable(R.drawable.ic_action_edit_white), false);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiHelpers.gotoCreateMessageActivity((PairAppBaseActivity) getActivity());

            }
        });
        setListAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //noinspection ConstantConditions
        ((ActionBarActivity) getActivity()).getSupportActionBar().hide();
        SwipeDismissListViewTouchListener swipeDismissListViewTouchListener = new SwipeDismissListViewTouchListener(getListView(), new SwipeDismissListViewTouchListener.OnDismissCallback() {
            @Override
            public void onDismiss(ListView listView, final int[] reverseSortedPositions) {
                final Conversation deleted = deleteConversation(reverseSortedPositions);
                UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), R.string.sure_you_want_to_delete_conversation, R.string.yes, R.string.no, new UiHelpers.Listener() {
                    @Override
                    public void onClick() {
                        cleanMessages(deleted);
                    }
                }, new UiHelpers.Listener() {
                    @Override
                    public void onClick() {
                        realm.beginTransaction();
                        realm.copyToRealm(deleted);
                        realm.commitTransaction();
                    }
                });
            }
        });
        getListView().setOnTouchListener(swipeDismissListViewTouchListener);
        getListView().setOnScrollListener(swipeDismissListViewTouchListener.makeScrollListener());
    }


    private void cleanMessages(Conversation conversation) {
        realm.beginTransaction();
        final String peerId = conversation.getPeerId();
        realm.where(Message.class).equalTo(Message.FIELD_FROM, peerId)
                .or()
                .equalTo(Message.FIELD_TO, peerId)
                .findAll().clear();
        realm.commitTransaction();
    }

    private Conversation deleteConversation(int[] reverseSortedPositions) {
        realm.beginTransaction();
        try {
            Conversation conversation = conversations.get(reverseSortedPositions[0]);
            Conversation copy = Conversation.copy(conversation);
            conversation.removeFromRealm();
            realm.commitTransaction();
            return copy;
        } catch (Exception e) {
            realm.cancelTransaction();
            Log.e(TAG, e.getMessage(), e.getCause());
        }
        return null;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        String peerId = ((Conversation) l.getAdapter().getItem(position)).getPeerId();
        UiHelpers.enterChatRoom(getActivity(), peerId);
    }

    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }

}
