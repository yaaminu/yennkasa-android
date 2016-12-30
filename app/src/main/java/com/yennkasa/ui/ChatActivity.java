package com.yennkasa.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.rey.material.widget.SnackBar;
import com.yennkasa.Errors.ErrorCenter;
import com.yennkasa.R;
import com.yennkasa.adapter.GroupsAdapter;
import com.yennkasa.adapter.MessagesAdapter;
import com.yennkasa.data.CallBody;
import com.yennkasa.data.Conversation;
import com.yennkasa.data.Message;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.messenger.MessengerBus;
import com.yennkasa.messenger.YennkasaClient;
import com.yennkasa.util.Config;
import com.yennkasa.util.Event;
import com.yennkasa.util.FileUtils;
import com.yennkasa.util.GenericUtils;
import com.yennkasa.util.LiveCenter;
import com.yennkasa.util.PLog;
import com.yennkasa.util.SimpleDateUtil;
import com.yennkasa.util.TaskManager;
import com.yennkasa.util.TypeFaceUtil;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.util.ViewUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import io.realm.Sort;

import static com.yennkasa.data.Message.TYPE_TEXT_MESSAGE;
import static com.yennkasa.messenger.MessengerBus.NOT_TYPING;
import static com.yennkasa.messenger.MessengerBus.ON_USER_OFFLINE;
import static com.yennkasa.messenger.MessengerBus.ON_USER_ONLINE;
import static com.yennkasa.messenger.MessengerBus.ON_USER_STOP_TYPING;
import static com.yennkasa.messenger.MessengerBus.ON_USER_TYPING;
import static com.yennkasa.messenger.MessengerBus.START_MONITORING_USER;
import static com.yennkasa.messenger.MessengerBus.STOP_MONITORING_USER;
import static com.yennkasa.messenger.MessengerBus.TYPING;
import static com.yennkasa.ui.UnknownContactFragment.UserProvider;


@SuppressWarnings({"ConstantConditions"})
public class ChatActivity extends MessageActivity implements View.OnClickListener,
        AbsListView.OnScrollListener, AdapterView.OnItemLongClickListener, UserProvider {
    public static final String EXTRA_PEER_ID = "peer id";
    private static final String TAG = ChatActivity.class.getSimpleName();
    private static final int ADD_USERS_REQUEST = 0x5;
    public static final int TAKE_PHOTO_REQUEST = 0x0;
    public static final int TAKE_VIDEO_REQUEST = 0x1;
    public static final int PICK_PHOTO_REQUEST = 0x2;
    public static final int PICK_VIDEO_REQUEST = 0x3;
    public static final int PICK_FILE_REQUEST = 0x4;
    public static final int RECORD_AUDIO_REQUEST = 0x7;
    public static final String TYPING_MESSAGE = "typingMessage";
    public static final String CURSOR = "cursor";
    public static final String SELECTED_MESSAGE = "selectedMessage";
    public static final String WAS_TYPING = "wasTyping";
    public static final String SCROLL_POSITION = "scrollPosition";
    private static final String SAVED_MESSAGES_MESSAGE_BOX = "saved.Messages.message.box";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_HIDE_NUMBER = "HideNumber";
    private int cursor = -1;
    private boolean wasTyping = false;
    private final MessagesAdapter.Delegate delegate = new MessagesAdapter.Delegate() {

        @Override
        public Realm userRealm() {
            return userRealm;
        }

        @Override
        public void onCallClicked(Message message) {
            switch (message.getCallBody().getCallType()) {
                case CallBody.CALL_TYPE_VIDEO:
                    callUser(MessengerBus.VIDEO_CALL_USER, Message.isIncoming(userRealm, message) ? message.getFrom() : message.getTo());
                    break;
                case CallBody.CALL_TYPE_VOICE:
                    callUser(MessengerBus.VOICE_CALL_USER, Message.isIncoming(userRealm, message) ? message.getFrom() : message.getTo());
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        @Override
        public boolean onDateSetChanged() {
            return !editingMessage;
        }

        @Override
        public void onMessageSeen(Message message) {
            ChatActivity.this.onMessageSeen(message);
        }

        @Override
        public void onReSendMessage(Message message) {
            resendMessage(message.getId());
        }

        @Override
        public int getProgress(Message message) {
            if (Message.isIncoming(userRealm, message) || message.getState() == Message.STATE_PENDING) {
                return getMessageProgress(message);
            }
            return -1;
        }

        @Override
        public void download(Message message) {
            UiHelpers.showToast(getString(R.string.preparing_to_download), Toast.LENGTH_SHORT);
            YennkasaClient.downloadAttachment(message);
        }

        @Override
        public PairAppBaseActivity getContext() {
            return ChatActivity.this;
        }

        @Override
        public void cancelDownload(Message message) {
            YennkasaClient.cancelDownload(message);
        }

        @Override
        public void onCancelSendMessage(Message message) {
            postEvent(Event.create(MessengerBus.CANCEL_MESSAGE_DISPATCH, null, Message.copy(message)));
        }
    };
    private Handler handler;
    private RealmResults<Message> messages;
    private User peer;
    private Conversation currConversation;
    private Realm messageConversationRealm;

    @Bind(R.id.lv_messages)
    ListView messagesListView;
    @Bind(R.id.et_message)
    EditText messageEt;
    @Bind(R.id.date_header_parent)
    View dateHeaderViewParent;
    @Bind(R.id.iv_send)
    View sendButton;
    @Bind(R.id.tv_log_message)
    TextView logTv;

    @Bind(R.id.ib_attach_more)
    View attachMoreView;

    private MessagesAdapter adapter;
    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {

        private boolean can4ward;

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            selectedMessages.clear();
            inContextualMode = true;
            messagesListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
            mode.getMenuInflater().inflate(R.menu.message_context_menu, menu);
            User user = userRealm.where(User.class).notEqualTo(User.FIELD_ID, getMainUserId()).notEqualTo(User.FIELD_ID, peer.getUserId()).findFirst();
            can4ward = user != null;
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            menu.findItem(R.id.action_delete).setVisible(!selectedMessages.isEmpty());
            menu.findItem(R.id.action_forward).setVisible(!selectedMessages.isEmpty());
            MenuItem item = menu.findItem(R.id.action_copy);
            item.setVisible(!selectedMessages.isEmpty() && can4ward);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            //block user
            //execute action in background
            int itemId = item.getItemId();
            if (itemId == R.id.action_copy) {
                @SuppressWarnings("deprecation")
                ClipboardManager manager = ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE));
                manager.setText(TextUtils.join("\n\n", selectedMessages));
                return true;
            } else if (itemId == R.id.action_delete) {
                messageConversationRealm.beginTransaction();
                for (Message message : selectedMessages) {
                    message.deleteFromRealm();
                }
                messageConversationRealm.commitTransaction();
                return true;
            } else if (itemId == R.id.action_forward) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setComponent(new ComponentName(ChatActivity.this, CreateMessageActivity.class));
                intent.putExtra(MainActivity.ARG_TITLE, getString(R.string.forward_to));
                intent.putExtra(CreateMessageActivity.EXTRA_FORWARDED_FROM, peer.getUserId());
                intent.putExtra(Intent.EXTRA_TEXT, TextUtils.join("\n\n", selectedMessages));
                startActivity(intent);
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            inContextualMode = false;
            messagesListView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        }
    };
    private boolean editingMessage = false;
    private MediaPlayer player;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        messageConversationRealm = Message.REALM(this);
        Bundle bundle = getIntent().getExtras();
        String peerId = bundle.getString(EXTRA_PEER_ID);
        peer = userManager.fetchUserIfRequired(userRealm, peerId, true, true);
        UserManager.getInstance().refreshPublicKeysIfRequired(peerId);

        setContentView(R.layout.activity_chat);
        ButterKnife.bind(this);
        handler = new Handler();
        Toolbar toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        toolBar.setOnClickListener(this);
        setSupportActionBar(toolBar);
        ViewUtils.setTypeface(messageEt, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        ViewUtils.hideViews(sendButton);

        handleIntent();
    }

    private User createUser(String peerId, String name, boolean isInContacts) {
        User user = new User();
        user.setName(name);
        user.setUserId(peerId);
        user.setAccountCreated(System.currentTimeMillis());
        user.setDP("avartar_empty");
        user.setType(User.TYPE_NORMAL_USER);
        user.setCountry("");
        user.setInContacts(isInContacts);
        return user;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent();
    }

    private void handleIntent() {
        String peerName = peer.getName();
        //noinspection ConstantConditions
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(peerName);
        actionBar.setDisplayHomeAsUpEnabled(true);
        RealmQuery<Message> messageQuery = messageConversationRealm.where(Message.class);
        String mainUserId = getMainUserId();
        if (User.isGroup(peer)) {
            messageQuery.equalTo(Message.FIELD_TO, peer.getUserId())
                    .or()
                    .equalTo(Message.FIELD_FROM, peer.getUserId());
        } else {
            messageQuery.beginGroup()
                    .equalTo(Message.FIELD_FROM, peer.getUserId())
                    .equalTo(Message.FIELD_TO, mainUserId)
                    .endGroup()
                    .or()
                    .beginGroup()
                    .equalTo(Message.FIELD_FROM, mainUserId)
                    .equalTo(Message.FIELD_TO, peer.getUserId())
                    .endGroup();
        }
        messages = messageQuery.findAllSorted(Message.FIELD_DATE_COMPOSED, Sort.ASCENDING, Message.FIELD_TYPE, Sort.DESCENDING);
        setUpCurrentConversation();
        sendButton.setOnClickListener(this);
        setUpListView();
        if (!peer.getInContacts() && !User.isGroup(peer)) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_unknown_contact_fragment, new UnknownContactFragment())
                            .commit();
                }
            });
        }
        // TODO: 8/22/2015 in future we will move to the last  un seen message if any
    }

    private void setUpListView() {
        adapter = new MessagesAdapter(delegate, messages, userManager.isGroup(userRealm, peer.getUserId()));
        messagesListView.setAdapter(adapter);

        messagesListView.setOnScrollListener(this);
        registerForContextMenu(messagesListView);
        messagesListView.setSelection(messages.size()); //move to last
    }

    private void setUpCurrentConversation() {
        String peerId = peer.getUserId();
        currConversation = messageConversationRealm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();
        if (currConversation == null) { //first time
            currConversation = Conversation.newConversationWithoutSession(messageConversationRealm, peerId, true);
        } else {
            messageConversationRealm.beginTransaction();
            currConversation.setActive(true);
            messageConversationRealm.commitTransaction();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_call_user).setVisible(peer.getAdmin() == null);
        menu.findItem(R.id.action_video_call_user).setVisible(peer.getAdmin() == null);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_call_user) {
            callUser(MessengerBus.VOICE_CALL_USER, peer.getUserId());
        } else if (id == R.id.action_video_call_user) {
            callUser(MessengerBus.VIDEO_CALL_USER, peer.getUserId());
        }
        return super.onOptionsItemSelected(item);
    }

    private void callUser(Object callType, String userId) {
        Event event = Event.create(callType, null, userId);
        MessengerBus.get(MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS).post(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        clearRecentChat(peer.getUserId());
        Config.setCurrentActivePeer(peer.getUserId());
        if (!User.isGroup(peer)) {
            postEvent(Event.create(START_MONITORING_USER, null, peer.getUserId()));
            registerForEvent(ON_USER_ONLINE, ON_USER_STOP_TYPING, ON_USER_TYPING, ON_USER_OFFLINE);
        } else {
            getSupportActionBar().setSubtitle(GroupsAdapter.join(",", peer.getMembers()));
        }
        String savedPreviousMessage = Config.getPreferences(TAG + SAVED_MESSAGES_MESSAGE_BOX).getString(peer.getUserId(), null);
        if (savedPreviousMessage != null) {
            messageEt.setText(savedPreviousMessage);
        }
        adapter.notifyDataSetChanged();
        messageEt.addTextChangedListener(this);
    }


    @Override
    protected void onPause() {
        Config.setCurrentActivePeer(null);
        if (currConversation != null) {
            messageConversationRealm.beginTransaction();
            currConversation.setActive(false);
            currConversation.setLastActiveTime(new Date());
            if (messages != null && !messages.isEmpty()) {
                final Message currConversationLastMessage = currConversation.getLastMessage();
                if (currConversationLastMessage == null || !messages.last().getId().equals(currConversationLastMessage.getId())) {
                    for (int i = messages.size() - 1; i >= 0; i--) {
                        Message lastMessage = messages.get(i);
                        if (!Message.isDateMessage(lastMessage) && !Message.isTypingMessage(lastMessage)) {
                            currConversation.setLastMessage(lastMessage);
                            break;
                        }
                    }
                }
            }
            messageConversationRealm.commitTransaction();
        }
        if (!User.isGroup(peer)) {
            postEvent(Event.create(STOP_MONITORING_USER, null, peer.getUserId()));
        }
        unRegister(ON_USER_ONLINE, ON_USER_OFFLINE, ON_USER_STOP_TYPING, ON_USER_TYPING);
        String s1 = messageEt.getText().toString();
        Config.getPreferences(TAG + SAVED_MESSAGES_MESSAGE_BOX).edit().putString(peer.getUserId(), s1).apply();
        super.onPause();
    }

    @NonNull
    @Override
    protected SnackBar getSnackBar() {
        return (SnackBar) findViewById(R.id.notification_bar);
    }

    @Override
    protected void onDestroy() {
        messageConversationRealm.close();
        clearRecentChat(peer.getUserId());
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.iv_send:
                String content = messageEt.getText().toString().trim();
                if (!GenericUtils.isEmpty(content)) {
                    if (editingMessage) {
                        editingMessage = false;
                        editMessage(messageId, content);
                    } else {
                        super.sendMessageActive(content, peer.getUserId(),
                                Message.TYPE_TEXT_MESSAGE, true);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                messagesListView.setSelection(messages.size());
                            }
                        });
                    }
                    messageEt.setText("");
                    stopTypingRunnable.run();
                }
                break;
            case R.id.main_toolbar:
                UiHelpers.gotoProfileActivity(this, peer.getUserId(),
                        !getIntent().getBooleanExtra(EXTRA_HIDE_NUMBER, false));
                break;
            default:
                throw new AssertionError();
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case TAKE_PHOTO_REQUEST:
            case TAKE_VIDEO_REQUEST:
            case PICK_PHOTO_REQUEST:
            case PICK_VIDEO_REQUEST:
            case PICK_FILE_REQUEST:
            case RECORD_AUDIO_REQUEST:
                sendMessage(requestCode, data, peer.getUserId());
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }


    @Override
    protected void onMessageQueued(String messageId) {
        PLog.d(TAG, "message with id: %s queued", messageId);
        messagesListView.setSelection(messages.size());
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
    }

    @Override
    public void onScroll(AbsListView view, final int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (firstVisibleItem == 0) { //first/second item
            dateHeaderViewParent.setVisibility(View.GONE);// TODO: 8/7/2015 fade instead of hiding right away
            return;
        }
        if (visibleItemCount != 0 && visibleItemCount < totalItemCount) {
            dateHeaderViewParent.setVisibility(View.VISIBLE);
            for (int i = firstVisibleItem + visibleItemCount - 1; i >= 0; i--) { //loop backwards
                final Message message = messages.get(i);
                if (Message.isDateMessage(message)) {
                    logTv.setText(SimpleDateUtil.formatDateRage(this, message.getDateComposed()));
                    return;
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        User user = userRealm.where(User.class).notEqualTo(User.FIELD_ID, getMainUserId()).notEqualTo(User.FIELD_ID, peer.getUserId()).findFirst();
        final boolean can4ward = user != null; //no other user apart from peer. cannot forward so lets hide it

        AdapterView.AdapterContextMenuInfo info = ((AdapterView.AdapterContextMenuInfo) menuInfo);
        Message message = messages.get(info.position);
        cursor = info.position;
        messageId = message.getId();
        if (message.getType() != Message.TYPE_DATE_MESSAGE && message.getType() != Message.TYPE_TYPING_MESSAGE) {
            getMenuInflater().inflate(R.menu.message_context_menu, menu);
            menu.findItem(R.id.action_copy).setVisible(message.getType() == TYPE_TEXT_MESSAGE);
            if (message.getType() != TYPE_TEXT_MESSAGE) {
                menu.findItem(R.id.action_forward).setVisible(can4ward && new File(message.getMessageBody()).exists());
            } else {
                menu.findItem(R.id.action_forward).setVisible(can4ward);
            }
            menu.findItem(R.id.action_edit_sent).setVisible(Message.canEdit(userRealm, message));
        }
    }

    private String messageId;

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (TextUtils.isEmpty(messageId)) {
            UiHelpers.showPlainOlDialog(this, getString(R.string.err_message_not_found));
            return false;
        }

        if (messages.size() < cursor/*guard against out of bounds access*/ || !messages.get(cursor).getId().equals(messageId)) { //something bad happened
            //this won't happen most of the time
            //no hacks, lets walk the list, it wont take a lot of time even if it had 10000 items!
            cursor = -1;
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).getId().equals(messageId)) {
                    cursor = i;
                    break;
                }
            }
        }
        if (cursor == -1) {
            UiHelpers.showPlainOlDialog(this, getString(R.string.err_message_not_found));
            return false;
        }

        Message message = messages.get(cursor);
        assert message != null;
        if (itemId == R.id.action_copy) {
            @SuppressWarnings("deprecation")
            ClipboardManager manager = ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE));
            manager.setText(message.getMessageBody());
            return true;
        } else if (itemId == R.id.action_delete) {
            //since realm is single threaded,we can count on it that messages.get(cusor) will give the
            //same message until this method completes that's why w are passing cursor without message
            deleteMessage(cursor);
            return true;
        } else if (itemId == R.id.action_forward) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setComponent(new ComponentName(this, CreateMessageActivity.class));
            intent.putExtra(MainActivity.ARG_TITLE, getString(R.string.forward_to));
            intent.putExtra(CreateMessageActivity.EXTRA_FORWARDED_FROM, peer.getUserId());
            if (Message.isTextMessage(message)) {
                intent.putExtra(Intent.EXTRA_TEXT, message.getMessageBody());
                intent.setType("text/*");
                startActivity(intent);
            } else {
                final File file = new File(message.getMessageBody());
                if (file.exists()) {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                    intent.setType(FileUtils.getMimeType(file.getAbsolutePath()));
                    startActivity(intent);
                } else {
                    ErrorCenter.reportError(TAG, getString(R.string.file_not_found));
                }
            }
            return true;
        } else if (itemId == R.id.action_edit_sent) {
            messageEt.setText(message.getMessageBody());
            messageEt.setSelection(message.getMessageBody().length());
            messagesListView.setSelection(cursor);
            editingMessage = true;
            return true;
        }
        messageId = "";
        cursor = -1;
        return super.onContextItemSelected(item);
    }

    private void deleteMessage(int position) {
        messageConversationRealm.beginTransaction(); //beginning transaction earlier to force realm to prevent other realms from changing the data set
        //hook up message to remove.
        //if it is the only message for the day remove the date message
        //if it is the last message set the latest message of this conversation to the previous message

        final Message currMessage = messages.get(position), //the selected message
                previousToCurrMessage = messages.get(position - 1), //at least there will be a date message since users cannot select date messages
                nextToCurrMessage = (messages.size() - 1 > position ? messages.get(position + 1) : null);

        final boolean wasLastForTheDay = nextToCurrMessage == null || Message.isDateMessage(nextToCurrMessage);
        final Message deletedMessage = Message.copy(currMessage);
        currMessage.deleteFromRealm();
//        if (deletedMessage.getState() == Message.STATE_PENDING) {
//            postEvent(Event.create(MessengerBus.REVERT_SENDING, null, deletedMessage.getId()));
//        }
        if (Message.isDateMessage(previousToCurrMessage) &&
                wasLastForTheDay) {
            previousToCurrMessage.deleteFromRealm(); //this will be a date message so delete it as there is no message for that day
        }
        if (currConversation.getLastMessage() == null) { //the last message of this conversation is what was just deleted
            //it will be inefficient to start from zero and move up. so we start from the
            //last element in the list (messages). we are subtracting the size from two
            //because array indexing starts from zero so the last will be n-1 and the realm transaction
            //has not bee committed so the deleted message still count in the value returned from
            //messages.size() hence subtracting only one will give you the index of the just deleted last message!!!
            //and not only will that be erroneous, it will also lead to crashes.
            /*************************************************************************************/
            int allMessages = messages.size() - 2; //don't change if you don't understand
            for (int i = allMessages; i > 0/*0th is the date*/; i--) {
                final Message cursor = messages.get(i);
                if (!Message.isDateMessage(cursor) && !Message.isTypingMessage(cursor)) {
                    currConversation.setLastMessage(cursor);
                    break;
                }
            }
            /**************************************************************************************/
        }
        messageConversationRealm.commitTransaction();
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                Realm userRealm = User.Realm(ChatActivity.this);
                try {
                    if (!Message.isTextMessage(deletedMessage)) {
                        if (Message.isOutGoing(userRealm, deletedMessage)) {
                            if (deletedMessage.getState() == Message.STATE_PENDING) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        delegate.onCancelSendMessage(deletedMessage);
                                    }
                                });
                            }
                        } else {
                            if (deletedMessage.getMessageBody().startsWith("http")) { //it's probably being downloaded
                                if (LiveCenter.getProgress(deletedMessage.getId()) != -1) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            delegate.cancelDownload(deletedMessage);
                                        }
                                    });
                                }
                            }
                        }
                    }
                    if (UserManager.getInstance().getBoolPref(UserManager.DELETE_ATTACHMENT_ON_DELETE, false)) {
                        File file = new File(deletedMessage.getMessageBody());
                        if (file.exists()) {
                            if (file.delete()) {
                                PLog.d(TAG, "deleted file successfully");
                            }
                        }
                    }
                } finally {
                    userRealm.close();
                }
            }
        }, false);
    }

    @NonNull
    private Runnable typingTimerTask = new Runnable() {
        @Override
        public void run() {
            if (wasTyping) {
                postEvent(Event.create(TYPING, null, peer.getUserId()));
                handler.postDelayed(typingTimerTask, 30000);
            }
        }
    };

    private final Runnable stopTypingRunnable = new Runnable() {
        @Override
        public void run() {
            if (wasTyping) {
                wasTyping = false;
                postEvent(Event.create(NOT_TYPING, null, peer.getUserId()));
            }
        }
    };

    @Override
    public void afterTextChanged(Editable s) {
        super.afterTextChanged(s);
        //remove the callback and post it again to run 10000 millis from now
        //effectively renewing the timeout!!!
        handler.removeCallbacks(stopTypingRunnable);
        //TODO add some deviation to the timeout
        handler.postDelayed(stopTypingRunnable, 10000);

        if (!s.toString().trim().isEmpty()) {
            ViewUtils.showViews(sendButton);
            ViewUtils.hideViews(attachMoreView);
            if (!wasTyping) {
                wasTyping = true;
                typingTimerTask.run();
            }
        } else {
            handler.removeCallbacks(typingTimerTask);
            ViewUtils.hideViews(sendButton);
            ViewUtils.showViews(attachMoreView);
        }
    }

    @Override
    protected void onSendSticker(final String stickerCode) {
        super.sendMessageActive(stickerCode, peer.getUserId(), Message.TYPE_STICKER, true);
    }

    @Override
    public void notifyUser(Context context, final Message message, String sender) {
        //noinspection StatementWithEmptyBody
        if (message.getTo().equals(peer.getUserId()) || message.getFrom().equals(peer.getUserId())
                || peer.getName().equals(sender)) {
            playTone();
        } else {
            super.notifyUser(this, message, sender);
        }
    }

    private final MediaPlayer.OnCompletionListener completionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            mp.release();
            player = null;
        }
    };

    private void playTone() {
        AudioManager manager = ((AudioManager) getSystemService(AUDIO_SERVICE));
        if (userManager.getBoolPref(UserManager.IN_APP_NOTIFICATIONS, true) &&
                manager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
            if (player == null) {
                player = new MediaPlayer();
            }
            try {
                player.reset();
                player.setOnCompletionListener(completionListener);
                AssetFileDescriptor fd = getResources().openRawResourceFd(R.raw.sound_a);
                player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                player.prepare();
                fd.close();
                player.setLooping(false);
                player.setVolume(1f, 1f);
                player.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void onTyping() {
        getSupportActionBar().setSubtitle(getString(R.string.writing));
    }

    public void onStopTyping() {
        onUserStatusChanged(isCurrentUserOnline);
    }

    public void onUserStatusChanged(boolean isOnline) {
        updateUserStatus(isOnline);
    }

    private void updateUserStatus(boolean isOnline) {
        getSupportActionBar().setSubtitle(isOnline ? R.string.st_online : R.string.st_offline);
    }

    @Override
    protected void reportProgress(String messageId, int progress) {
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onCancelledOrDone(String messageid) {
        adapter.notifyDataSetChanged();
    }


    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Bundle b = savedInstanceState.getBundle(peer.getUserId());
        cursor = b.getInt(CURSOR, -1);
        messageId = b.getString(SELECTED_MESSAGE);
        int scrollPosition = b.getInt(SCROLL_POSITION, messages.size());
        messagesListView.setSelection(scrollPosition);
        wasTyping = b.getBoolean(WAS_TYPING, false);
        String typingMessage = b.getString(TYPING_MESSAGE);
        if (typingMessage != null) {
            messageEt.setText(typingMessage);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Bundle b = new Bundle();
        b.putString(TYPING_MESSAGE, messageEt.getText().toString());
        b.putInt(SCROLL_POSITION, messagesListView.getLastVisiblePosition());
        if (cursor > -1) {
            b.putInt(CURSOR, cursor);
            b.putString(SELECTED_MESSAGE, messageId);
        }

        b.putBoolean(WAS_TYPING, wasTyping);
        outState.putParcelable(peer.getUserId(), b);
        super.onSaveInstanceState(outState);
    }

    private boolean inContextualMode = false;
    private final Set<Message> selectedMessages = new HashSet<>();

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (!inContextualMode) {
            startSupportActionMode(actionModeCallback);
        }
        Message message = adapter.getItem(position);
        boolean itemChecked = !messagesListView.isItemChecked(position);
        messagesListView.setItemChecked(position, itemChecked);
        if (itemChecked) {
            selectedMessages.add(message);
        } else {
            selectedMessages.remove(message);
        }
        return true;
    }


    private boolean isCurrentUserOnline = false, isCurrentUserTyping = false;

    @Override
    protected void handleEvent(Event event) {
        if (event.getData().equals(peer.getUserId())) {
            Object tag = event.getTag();
            if (tag.equals(ON_USER_ONLINE)) {
                isCurrentUserOnline = true;
                if (isCurrentUserTyping) return;
                updateUserStatus(true);
            } else if (tag.equals(ON_USER_OFFLINE)) {
                isCurrentUserOnline = false;
                isCurrentUserTyping = false;
                updateUserStatus(false);
            } else if (tag.equals(ON_USER_TYPING)) {
                isCurrentUserOnline = true;
                isCurrentUserTyping = true;
                onTyping();
            } else if (tag.equals(ON_USER_STOP_TYPING)) {
                isCurrentUserTyping = false;
                onStopTyping();
            }
        }
    }

    @Override
    protected boolean hideConnectionView() {
        return false;
    }

    @OnClick(R.id.ib_attach_more)
    public void attachMore(View view) {
        UiHelpers.attach(this);
    }

    @Override
    public User currentUser() {
        return peer;
    }

    @Override
    public boolean hideNotice() {
//        return currConversation.getLastMessage() != null;
        return (peer.getInContacts() || User.isGroup(peer)) && !getIntent().getBooleanExtra(EXTRA_HIDE_NUMBER, false);
    }

    @Override
    public Realm realm() {
        return userRealm;
    }
}
