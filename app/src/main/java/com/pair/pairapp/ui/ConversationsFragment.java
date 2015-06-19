package com.pair.pairapp.ui;

import android.app.Activity;
import android.app.AlarmManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
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

import com.pair.adapter.ConversationAdapter;
import com.pair.data.Conversation;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;

import java.util.Timer;
import java.util.TimerTask;

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
    public void onAttach(Activity activity) {
        setHasOptionsMenu(true);
        super.onAttach(activity);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(null, null, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_inbox, container, false);
        realm = Realm.getInstance(getActivity());
        realm.beginTransaction();
        conversations = realm.allObjectsSorted(Conversation.class, "lastActiveTime", false);
        for (int i = 0; i < conversations.size(); i++) {
            Conversation conversation = conversations.get(i);
            if (conversation.getLastMessage() == null) {
                conversations.remove(i);
            }
        }
        realm.commitTransaction();
        adapter = new ConversationAdapter(getActivity(), conversations, true);
        setListAdapter(adapter);
        String title = getArguments().getString(MainActivity.ARG_TITLE);
        ActionBarActivity activity = (ActionBarActivity) getActivity();
        //noinspection ConstantConditions
        activity.getSupportActionBar().setTitle(title);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            long ONE_MINUTE = AlarmManager.INTERVAL_FIFTEEN_MINUTES / 15;
            timer.scheduleAtFixedRate(task, 0L, ONE_MINUTE);
        } catch (Exception ignored) { //timer is already scheduled!

        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        registerForContextMenu(getListView());
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Conversation conversation = ((ConversationAdapter.ViewHolder) v.getTag()).currentConversation;
        String peerId = conversation.getPeerId();
        UiHelpers.enterChatRoom(getActivity(), peerId);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.new_message_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.new_message) {
            Fragment fragment = ContactFragment.INSTANCE;
            Bundle args = new Bundle();
            args.putString(MainActivity.ARG_TITLE, getActivity().getString(R.string.title_pick_contact));
            fragment.setArguments(args);
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, fragment)
                    .addToBackStack(null)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        getActivity().getMenuInflater().inflate(R.menu.inbox_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return super.onContextItemSelected(item);
    }

    @Override
    public void onDestroy() {
        realm.close();
        task.cancel();
        super.onDestroy();
    }

    private TimerTask task = new TimerTask() {
        @Override
        public void run() {
            getActivity().runOnUiThread(refreshRealm);
        }
    };

    private Runnable refreshRealm = new Runnable() {
        @Override
        public void run() {
            // conversations = realm.where(Conversation.class).findAll();
            adapter.notifyDataSetChanged();
            Log.i(TAG, "refreshing");
        }
    };

    private Timer timer = new Timer("dateRefresher", true);
}
