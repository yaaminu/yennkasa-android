package com.pairapp.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.pairapp.R;
import com.pairapp.adapter.ConversationAdapter;
import com.pairapp.data.Conversation;
import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.messenger.MessengerBus;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.LiveCenter;
import com.pairapp.util.PLog;
import com.pairapp.util.TaskManager;
import com.pairapp.util.UiHelpers;
import com.rey.material.widget.FloatingActionButton;

import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import io.realm.Sort;

import static com.pairapp.messenger.MessengerBus.NOT_TYPING;
import static com.pairapp.messenger.MessengerBus.PAIRAPP_CLIENT_LISTENABLE_BUS;
import static com.pairapp.messenger.MessengerBus.TYPING;

/**
 * @author by Null-Pointer on 5/29/2015.
 */
public class ConversationsFragment extends ListFragment {

    private static final String TAG = ConversationsFragment.class.getSimpleName();
    private Realm realm;
    private RealmResults<Conversation> conversations;
    private Conversation deleted;
    private final EventBus.EventsListener eventsListener = new EventListener();
    private final Set<String> typingUsers = new HashSet<>(5);
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
        public Realm realm() {
            return userRealm;
        }

        @Override
        public boolean autoUpdate() {
            return true;
        }

        @Override
        public boolean isCurrentUserTyping(String userId) {
            return typingUsers.contains(userId);
        }
    };
    private Callbacks interactionListener;
    private Realm userRealm;

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
        realm = Conversation.Realm(getActivity());
        userRealm = User.Realm(getActivity());
        conversations = realm.allObjectsSorted(Conversation.class, Conversation.FIELD_LAST_ACTIVE_TIME, Sort.DESCENDING);
        ConversationAdapter adapter = new ConversationAdapter(delegate);
        FloatingActionButton actionButton = ((FloatingActionButton) view.findViewById(R.id.fab_new_message));
        //noinspection deprecation
        actionButton.setIcon(getResources().getDrawable(R.drawable.ic_mode_edit_white_24dp), false);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiHelpers.gotoCreateMessageActivity(getActivity());

            }
        });
        setListAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //noinspection ConstantConditions
        ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                String[] contextMenuOptions = new String[4];

                contextMenuOptions[0] = getString(R.string.clear_messages);
                contextMenuOptions[1] = getString(R.string.action_delete_conversation);
                deleted = conversations.get(position);
                final String peerId = deleted.getPeerId();
                final UserManager userManager = UserManager.getInstance();
                String name = userManager.getName(peerId);
                if (TextUtils.isEmpty(name)) {
                    name = userManager.getName(peerId);
                }
                contextMenuOptions[2] = userManager.isBlocked(peerId) ? getString(R.string.unblock, name) :
                        getString(R.string.block, name);

                contextMenuOptions[3] = userManager.isMuted(peerId) ? getString(R.string.unmute, name) :
                        getString(R.string.mute, name);

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setAdapter(new ArrayAdapter<>(getActivity(),
                        android.R.layout.simple_list_item_1, contextMenuOptions), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final Runnable invalidateTask = new Runnable() {
                            @Override
                            public void run() {
                                LiveCenter.invalidateNewMessageCount(peerId);
                            }
                        };
                        TaskManager.executeNow(invalidateTask, false);
                        switch (which) {
                            case 0:
                                if (deleted.getLastMessage() != null) {
                                    warnAndDelete(peerId, getString(R.string.warn_msg_clear_conversation_messages));
                                }
                                break;
                            case 1:
                                if (deleted.getLastMessage() != null) {
                                    warnAndDelete(peerId);
                                } else {
                                    realm.beginTransaction();
                                    deleted.deleteFromRealm();
                                    realm.commitTransaction();
                                    UiHelpers.showToast(R.string.delete_success);
                                    deleteUserIfPossible(peerId);
                                }
                                break;
                            case 2:
                                if (userManager.isBlocked(peerId)) {
                                    userManager.unBlockUser(peerId);
                                    UiHelpers.showToast(R.string.user_unblocked);
                                } else {
                                    userManager.blockUser(peerId);
                                    UiHelpers.showToast(R.string.user_blocked);
                                }
                                break;
                            case 3:
                                if (userManager.isMuted(peerId)) {
                                    userManager.unMuteUser(peerId);
                                    UiHelpers.showToast(R.string.unmuted_user);
                                } else {
                                    userManager.muteUser(peerId);
                                    UiHelpers.showToast(R.string.muted_user);
                                }
                                break;
                            default:
                                break;
                        }
                    }
                });
                builder.create().show();
                return true;
            }
        });
    }

    private void deleteUserIfPossible(final String senderId) {
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                Realm userRealm = User.Realm(getActivity());
                User user = userRealm.where(User.class).equalTo(User.FIELD_ID, senderId).findFirst();
                if (user != null && !user.getInContacts() && user.getType() == User.TYPE_NORMAL_USER) {
                    Realm messageRealm = Message.REALM(getActivity());
                    Message msg = messageRealm.where(Message.class).equalTo(Message.FIELD_FROM, senderId).findFirst();
                    if (msg == null) {
                        //remove this user
                        userRealm.beginTransaction();
                        user.deleteFromRealm();
                        userRealm.commitTransaction();
                    }
                    messageRealm.close();
                }
                userRealm.close();
            }
        }, false);
    }


    private void cleanMessages(final String peerId, final Date toWhen) {
        final ProgressDialog dialogFragment = ProgressDialog.show(getActivity(), "",
                getString(R.string.st_please_wait), true, false, null);
        TaskManager.executeNow(new Runnable() {
                                   @Override
                                   public void run() {
                                       PLog.d(TAG, "deleting messages for conversion between user and %s", peerId);
                                       Realm realm = Message.REALM(getActivity());
                                       realm.beginTransaction(); //blocks all writes before runnig query because of the copyOnWrite style of realm
                                       String mainUserId = UserManager.getMainUserId();
                                       RealmResults<Message> messages;
                                       RealmQuery<Message> messageQuery = realm.where(Message.class);
                                       if (UserManager.getInstance().isGroup(peerId)) {
                                           messageQuery.equalTo(Message.FIELD_TO, peerId)
                                                   .or()
                                                   .equalTo(Message.FIELD_FROM, peerId);
                                       } else {
                                           messageQuery.beginGroup()
                                                   .equalTo(Message.FIELD_FROM, peerId)
                                                   .equalTo(Message.FIELD_TO, mainUserId)
                                                   .endGroup()
                                                   .or()
                                                   .beginGroup()
                                                   .equalTo(Message.FIELD_FROM, mainUserId)
                                                   .equalTo(Message.FIELD_TO, peerId)
                                                   .endGroup();
                                       }
                                       messages = messageQuery.lessThan(Message.FIELD_DATE_COMPOSED, toWhen).findAll(); //new messages will be ignored
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
                                       messages.deleteAllFromRealm();
                                       realm.commitTransaction();
                                       realm.close();
                                       SystemClock.sleep(1000);
                                       getActivity().runOnUiThread(new Runnable() {
                                           @Override
                                           public void run() {
                                               dialogFragment.dismiss();
                                           }
                                       });

                                   }
                               }

                , false);
    }

    @Override
    public void onStart() {
        super.onStart();
        MessengerBus.get(PAIRAPP_CLIENT_LISTENABLE_BUS).register(eventsListener, TYPING, NOT_TYPING);
    }

    @Override
    public void onStop() {
        MessengerBus.get(PAIRAPP_CLIENT_LISTENABLE_BUS).unregister(TYPING, eventsListener);
        MessengerBus.get(PAIRAPP_CLIENT_LISTENABLE_BUS).unregister(NOT_TYPING, eventsListener);
        super.onStop();
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


    private void warnAndDelete(final String peerid, String message) {
        //in case user waits before accepting to delete and while waiting new message arrives we don't want the user
        //to lose those messages too
        final Date now = new Date();

        UiHelpers.showPlainOlDialog(getContext(), message, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    cleanMessages(peerid, now);
                }
            }
        });
    }

    private void warnAndDelete(final String peerId) {
        //in case user waits before accepting to delete and while waiting new message arrives we don't want the user
        //to lose those messages too
        final Date now = new Date();
        UiHelpers.showPlainOlDialog(getContext(), getString(R.string.sure_you_want_to_delete_conversation)
                , new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            realm.beginTransaction();
                            deleted.deleteFromRealm();
                            realm.commitTransaction();
                            cleanMessages(peerId, now);
                            new Handler().post(new Runnable() {
                                public void run() {
                                    deleteUserIfPossible(peerId);
                                }
                            });
                        }
                    }
                });
    }


    private class EventListener extends MainThreadBaseEventListener {
        @Override
        protected void handleEvent(Event event) {
            Object tag = event.getTag();
            if (tag.equals(TYPING)) {
                if (typingUsers.add((String) event.getData())) {
                    ((ArrayAdapter) getListAdapter()).notifyDataSetChanged();
                }
            } else if (tag.equals(NOT_TYPING)) {
                if (typingUsers.remove((String) event.getData())) {
                    ((ArrayAdapter) getListAdapter()).notifyDataSetChanged();
                }
            } else {
                PLog.f(TAG, tag.toString());
                throw new AssertionError("unknown event");
            }
        }
    }

}
