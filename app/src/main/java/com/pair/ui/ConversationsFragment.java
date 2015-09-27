package com.pair.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ListView;

import com.pair.adapter.ConversationAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.util.CLog;
import com.pair.util.Config;
import com.pair.util.UiHelpers;
import com.pair.view.SwipeDismissListViewTouchListener;
import com.rey.material.app.Dialog;
import com.rey.material.app.DialogFragment;
import com.rey.material.app.SimpleDialog;
import com.rey.material.widget.CheckBox;
import com.rey.material.widget.FloatingActionButton;

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
                deleted = deleteConversation(reverseSortedPositions);
                boolean stopAnnoyingMe = UserManager.getInstance().getUserPreference().getBoolean(STOP_ANNOYING_ME, false);
                if (stopAnnoyingMe) {
                    cleanMessages(deleted);
                    return;
                }
                showAlertDialog();
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
            CLog.e(TAG, e.getMessage(), e.getCause());
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

    private void showAlertDialog() {

        SimpleDialog.Builder builder = new SimpleDialog.Builder(R.style.SimpleDialogLight) {
            boolean touchedCheckBox = false,checkBoxValue;
            public CompoundButton.OnCheckedChangeListener listener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    touchedCheckBox = true;
                    checkBoxValue = isChecked;
                    UiHelpers.showToast(String.valueOf(isChecked));
                }
            };

            @Override
            protected void onBuildDone(Dialog dialog) {
                dialog.layoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                CheckBox checkBox = ((CheckBox) dialog.findViewById(R.id.cb_stop_annoying_me));
                checkBox.setOnCheckedChangeListener(listener);
            }

            @Override
            public void onPositiveActionClicked(DialogFragment fragment) {
                cleanMessages(deleted);
                super.onPositiveActionClicked(fragment);
                updateStopAnnoyingMe(checkBoxValue);
            }

            @Override
            public void onNegativeActionClicked(DialogFragment fragment) {
                realm.beginTransaction();
                realm.copyToRealm(deleted);
                realm.commitTransaction();
                super.onNegativeActionClicked(fragment);
            }

            private void updateStopAnnoyingMe(boolean newValue) {
                if(touchedCheckBox) {
                    UserManager.getInstance().getUserPreference().edit().putBoolean(STOP_ANNOYING_ME, newValue).apply();
                }
            }
        };
        builder.contentView(R.layout.delete_conversation_prompt);
        builder.positiveAction(getString(android.R.string.ok))
                .negativeAction(getString(R.string.no));
        DialogFragment fragment = DialogFragment.newInstance(builder);
        fragment.show(getFragmentManager(), null);
    }

}
