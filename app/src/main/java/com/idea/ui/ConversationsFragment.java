package com.idea.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.idea.adapter.ConversationAdapter;
import com.idea.data.Conversation;
import com.idea.data.Message;
import com.idea.data.UserManager;
import com.idea.pairapp.R;
import com.idea.util.Config;
import com.idea.util.LiveCenter;
import com.idea.util.PLog;
import com.idea.util.TaskManager;
import com.idea.util.UiHelpers;
import com.idea.view.SwipeDismissListViewTouchListener;
import com.rey.material.app.DialogFragment;
import com.rey.material.widget.FloatingActionButton;

import java.io.File;
import java.util.Date;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * @author by Null-Pointer on 5/29/2015.
 */
public class ConversationsFragment extends ListFragment {

    private static final String TAG = ConversationsFragment.class.getSimpleName();
    public static final String STOP_ANNOYING_ME = TAG + "askmeOndelete";
    private Realm realm;
    private RealmResults<Conversation> conversations;
    private ConversationAdapter adapter;
    private Conversation deleted;
    private ConversationAdapter.Delegate delegate = new ConversationAdapter.Delegate() {
        @Override
        public int unSeenMessagesCount(Conversation conversation) {
            return LiveCenter.getUnreadMessageFor(conversation.getPeerId());
        }

        @Override
        public RealmResults<Conversation> dataSet() {
            return conversations;
        }

        @Override
        public PairAppBaseActivity context() {
            return ((PairAppBaseActivity) getActivity());
        }

        @Override
        public boolean autoUpdate() {
            return true;
        }
    };
    private Callbacks interactionListener;

    interface Callbacks {
        void onConversionClicked(Conversation conversation);

        int unSeenMessagesCount(Conversation conversation);
    }

    public ConversationsFragment() {
    } //required no-arg constructor

    @Override
    public void onAttach(Context activity) {
        try {
            interactionListener = (Callbacks) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement " + Callbacks.class.getName());
        }
        super.onAttach(activity);
        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_inbox, container, false);
        realm = Conversation.Realm(Config.getApplicationContext());
        conversations = realm.allObjectsSorted(Conversation.class, Conversation.FIELD_LAST_ACTIVE_TIME, false);
        adapter = new ConversationAdapter(delegate);
        FloatingActionButton actionButton = ((FloatingActionButton) view.findViewById(R.id.fab_new_message));
        actionButton.setIcon(getResources().getDrawable(R.drawable.ic_mode_edit_white_24dp), false);
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
                showAlertDialog(reverseSortedPositions);
            }
        });
        getListView().setOnTouchListener(swipeDismissListViewTouchListener);
        getListView().setOnScrollListener(swipeDismissListViewTouchListener.makeScrollListener());
    }


    private void cleanMessages(Conversation conversation) {
        final String peerId = conversation.getPeerId();
        final Date date = new Date();
        final DialogFragment fragment = UiHelpers.newProgressDialog();
        fragment.show(getFragmentManager(),null);
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                LiveCenter.invalidateNewMessageCount(peerId);
                PLog.d(TAG, "deleting messages for conversion between user and %s", peerId);
                Realm realm = Message.REALM(getActivity());
                realm.beginTransaction();
                RealmResults<Message> messages = realm.where(Message.class).equalTo(Message.FIELD_FROM, peerId)
                        .or()
                        .equalTo(Message.FIELD_TO, peerId)
                        .lessThan(Message.FIELD_DATE_COMPOSED, date)
                        .findAll();
                if (UserManager.getInstance().getBoolPref(UserManager.DELETE_ATTACHMENT_ON_DELETE, false)) {
                    for (Message message : messages) {
                        if (Message.isVideoMessage(message) || Message.isPictureMessage(message) || Message.isBinMessage(message)) {
                            File file = new File(message.getMessageBody());
                            if (file.delete()) {
                                PLog.d(TAG, "deleted file %s", file.getAbsolutePath());
                            } else {
                                PLog.d(TAG, "failed to delete file: %s", file.getAbsolutePath());
                            }
                        }
                    }
                }
                messages.clear();
                realm.commitTransaction();
                realm.close();
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                     UiHelpers.dismissProgressDialog(fragment);
                    }
                });
            }
        });
    }

    private Conversation deleteConversation(int position) {
        realm.beginTransaction();
        try {
            Conversation conversation = conversations.get(position);
            Conversation copy = Conversation.copy(conversation);
            conversation.removeFromRealm();
            realm.commitTransaction();
            return copy;
        } catch (Exception e) {
            realm.cancelTransaction();
            PLog.e(TAG, e.getMessage(), e.getCause());
        }
        return null;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        interactionListener.onConversionClicked(((Conversation) l.getAdapter().getItem(position)));
    }

    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    private void showAlertDialog(int[] reverseSortedPositions) {
        for (int position : reverseSortedPositions) {
            deleted = deleteConversation(position);
            UiHelpers.showStopAnnoyingMeDialog(((PairAppBaseActivity) getActivity()), STOP_ANNOYING_ME,
                    getString(R.string.sure_you_want_to_delete_conversation), getString(android.R.string.ok), getString(R.string.no), new UiHelpers.Listener() {
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
    }

}
