package com.idea.ui;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.idea.Errors.ErrorCenter;
import com.idea.adapter.GroupsAdapter;
import com.idea.adapter.MessagesAdapter;
import com.idea.data.Conversation;
import com.idea.data.Message;
import com.idea.data.User;
import com.idea.data.UserManager;
import com.idea.pairapp.R;
import com.idea.util.FileUtils;
import com.idea.util.LiveCenter;
import com.idea.util.PLog;
import com.idea.util.PhoneNumberNormaliser;
import com.idea.util.SimpleDateUtil;
import com.idea.util.TaskManager;
import com.idea.util.TypeFaceUtil;
import com.idea.util.UiHelpers;
import com.idea.util.ViewUtils;
import com.rey.material.app.ToolbarManager;
import com.rey.material.widget.SnackBar;

import java.io.File;
import java.util.Date;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;

import static com.idea.data.Message.TYPE_TEXT_MESSAGE;


@SuppressWarnings({"ConstantConditions"})
public class ChatActivity extends MessageActivity implements View.OnClickListener,
        AbsListView.OnScrollListener, TextWatcher, LiveCenter.LiveCenterListener {
    public static final String EXTRA_PEER_ID = "peer id";
    private static final String TAG = ChatActivity.class.getSimpleName();
    private static final int ADD_USERS_REQUEST = 0x5;
    public static final int TAKE_PHOTO_REQUEST = 0x0;
    public static final int TAKE_VIDEO_REQUEST = 0x1;
    public static final int PICK_PHOTO_REQUEST = 0x2;
    public static final int PICK_VIDEO_REQUEST = 0x3;
    public static final int PICK_FILE_REQUEST = 0x4;
    public static final int ADD_TO_CONTACTS_REQUEST = 0x6;
    public static final String TYPING_MESSAGE = "typingMessage";
    public static final String CURSOR = "cursor";
    public static final String SELECTED_MESSAGE = "selectedMessage";
    public static final String WAS_TYPING = "wasTyping";
    public static final String SCROLL_POSITION = "scrollPosition";
    public static final String WAS_BLOCKED = "wasBlocked";
    public static final String UN_BLOCK = "block";
    private String selectedMessage;
    private int cursor = -1;
    private boolean wasTyping = false,
            outOfSync = false;
    private final MessagesAdapter.Delegate delegate = new MessagesAdapter.Delegate() {
        @Override
        public boolean onDateSetChanged() {
            return true;
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
            if (Message.isIncoming(message) || message.getState() == Message.STATE_PENDING) {
                return getMessageProgress(message);
            }
            return -1;
        }

        @Override
        public void download(Message message) {
            if (bound) {
                pairAppClientInterface.downloadAttachment(message);
            } else {
                bind();
            }
        }

        @Override
        public PairAppBaseActivity getContext() {
            return ChatActivity.this;
        }
    };
    //    boolean inContextualMode;
    private Handler handler;
    private RealmResults<Message> messages;
    private User peer;
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            wasTyping = false;
            LiveCenter.notifyNotTyping(peer.getUserId());
        }
    };
    private Conversation currConversation;
    private Realm messageConversationRealm, usersRealm;
    //    private SwipeDismissListViewTouchListener swipeDismissListViewTouchListener;
    private ListView messagesListView;
    private EditText messageEt;
    private View dateHeaderViewParent;
    private View sendButton;
    private MessagesAdapter adapter;
    private Toolbar toolBar;
    private ToolbarManager toolbarManager;
    private TextView logTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        handler = new Handler();
        toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        toolBar.setOnClickListener(this);
        logTv = (TextView) findViewById(R.id.tv_log_message);
        toolbarManager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);
        messageEt = ((EditText) findViewById(R.id.et_message));
        ViewUtils.setTypeface(messageEt, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        sendButton = findViewById(R.id.iv_send);
        ViewUtils.hideViews(sendButton);
        messagesListView = ((ListView) findViewById(R.id.lv_messages));
        dateHeaderViewParent = findViewById(R.id.date_header_parent);

        messageConversationRealm = Message.REALM(this);
        usersRealm = User.Realm(this);

        Bundle bundle = getIntent().getExtras();
        String peerId = bundle.getString(EXTRA_PEER_ID);
        peer = userManager.fetchUserIfRequired(usersRealm, peerId, true);
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
                    .equalTo(Message.FIELD_TO, peerId)
                    .endGroup();
        }
        messages = messageQuery.findAllSorted(Message.FIELD_DATE_COMPOSED, true, Message.FIELD_TYPE, false);
        setUpCurrentConversation();
        sendButton.setOnClickListener(this);
        messageEt.addTextChangedListener(this);
        setUpListView();
//        final ImageView imageView = (ImageView) toolBar.findViewById(R.id.riv_peer_avatar);
//        ImageLoader.load(this, peer.getUserId(), peer.getDP())
//                .placeholder(userManager.isGroup(peer.getUserId()) ? R.drawable.group_avatar : R.drawable.user_avartar)
//                .into(imageView);
        // TODO: 8/22/2015 in future we will move to the last  un seen message if any
//        inContextualMode = false;
        if (userManager.isBlocked(peerId)) {
            userWasBlocked = true;
            UiHelpers.showErrorDialog(ChatActivity.this,getString(R.string.blocked_user_notice),okListener);
        }
    }


    private boolean acceptedToUblock = false, userWasBlocked = false;
    private final UiHelpers.Listener okListener = new UiHelpers.Listener() {
        @Override
        public void onClick() {
            acceptedToUblock = true;
            userManager.unBlockUser(peer.getUserId());
            UiHelpers.showToast(getString(R.string.user_unblocked));
        }
    };

    private void setUpListView() {
        adapter = new MessagesAdapter(delegate, messages, userManager.isGroup(usersRealm, peer.getUserId()));
        messagesListView.setAdapter(adapter);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//        swipeDismissListViewTouchListener = new SwipeDismissListViewTouchListener(messagesListView, new SwipeDismissListViewTouchListener.OnDismissCallback() {
//            @Override
//            public void onDismiss(ListView listView, int[] reverseSortedPositions) {
//                deleteMessage(reverseSortedPositions[0]);
//                outOfSync = false;
//                adapter.notifyDataSetChanged();
//            }
//        });
//        }
        messagesListView.setOnScrollListener(this);
        registerForContextMenu(messagesListView);
        messagesListView.setSelection(messages.size()); //move to last
    }

    private void setUpCurrentConversation() {
        String peerId = peer.getUserId();
        currConversation = messageConversationRealm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();
        if (currConversation == null) { //first time
            currConversation = Conversation.newConversationWithoutSession(messageConversationRealm, peerId, true);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        toolbarManager.onPrepareMenu();
//        if(inContextualMode){
//
//        }else {
        menu = toolBar.getMenu();
        if (menu != null && menu.size() > 0) { //required for toolbar to behave on older platforms <=10
            User mainUser = getCurrentUser();
            final User admin = peer.getAdmin();
            menu.findItem(R.id.action_invite_friends)
                    .setVisible(peer.getType() == User.TYPE_GROUP && admin != null && admin.getUserId().equals(mainUser.getUserId()));
            boolean visible = peer.getType() != User.TYPE_GROUP
                    && !peer.getInContacts();
            menu.findItem(R.id.action_add_contact).setVisible(visible);
        }
//        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        toolbarManager.createMenu(R.menu.chat_menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_invite_friends) {
            Intent intent = new Intent(this, InviteActivity.class);
            intent.putExtra(InviteActivity.EXTRA_GROUP_ID, peer.getUserId());
            startActivityForResult(intent, ADD_USERS_REQUEST);
            return true;
        } else if (id == R.id.action_attach) {
            if (!acceptedToUblock && userWasBlocked) {
                userManager.unBlockUser(peer.getUserId());
                UiHelpers.showToast(R.string.user_unblocked);
            }
            UiHelpers.attach(this);
            return true;
        } else if (id == R.id.action_add_contact) {
            Intent intent = new Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT);
            intent.setData(Uri.parse("tel:" + PhoneNumberNormaliser.toLocalFormat(peer.getUserId(), peer.getCountry())));
            try {
                startActivityForResult(intent, ADD_TO_CONTACTS_REQUEST);
            } catch (ActivityNotFoundException e) {
                // TODO: 8/23/2015 should we tell the user or is it that our intent was wrongly targeted?
                UiHelpers.showPlainOlDialog(this, getString(R.string.no_contact_app_on_device));
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        clearRecentChat(peer.getUserId());
        if (!User.isGroup(peer)) {
            updateUserStatus(LiveCenter.isOnline(peer.getUserId()));
            LiveCenter.trackUser(peer.getUserId());
            LiveCenter.notifyInChatRoom(peer.getUserId());
            LiveCenter.registerTypingListener(this);
        } else {
            getSupportActionBar().setSubtitle(GroupsAdapter.join(",", peer.getMembers()));
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        if (currConversation != null) {
            messageConversationRealm.beginTransaction();
            currConversation.setActive(false);
            currConversation.setLastActiveTime(new Date());
            if (messages != null && !messages.isEmpty()) {
                final Message currConversationLastMessage = currConversation.getLastMessage();
                if (currConversationLastMessage == null || !messages.last().getId().equals(currConversationLastMessage.getId())) {
                    for (int i = messages.size() - 1; i >= 0; i--) {
                        Message lastMessage = messages.get(i);
                        if (!Message.isDateMessage(lastMessage) || !Message.isTypingMessage(lastMessage)) {
                            currConversation.setLastMessage(lastMessage);
                            break;
                        }
                    }
                }
            }
            messageConversationRealm.commitTransaction();
        }
        if (!userManager.isGroup(peer.getUserId())) {
            LiveCenter.notifyNotTyping(peer.getUserId());
            LiveCenter.notifyLeftChatRoom(peer.getUserId());
            LiveCenter.doNotTrackUser(peer.getUserId());
            LiveCenter.unRegisterTypingListener(this);
        }
        super.onPause();
    }

    @Override
    protected SnackBar getSnackBar() {
        return (SnackBar) findViewById(R.id.notification_bar);
    }

    @Override
    protected void onDestroy() {
        messageConversationRealm.close();
        usersRealm.close();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (!acceptedToUblock && userWasBlocked) {
            userManager.unBlockUser(peer.getUserId());
            UiHelpers.showToast(R.string.user_unblocked);
        }
        switch (id) {
            case R.id.iv_send:
                if (!messageEt.getText().toString().trim().isEmpty())
                    sendTextMessage();
                break;
            case R.id.main_toolbar:
                UiHelpers.gotoProfileActivity(this, peer.getUserId());
                break;
            default:
                throw new AssertionError();
        }
    }

    private void sendTextMessage() {
        String content = messageEt.getText().toString().trim();
        messageEt.setText("");
        //TODO use a regular expression to validate the message body
        if (!TextUtils.isEmpty(content)) {
            super.sendMessage(content, peer.getUserId(), Message.TYPE_TEXT_MESSAGE, true);
            messagesListView.setSelection(messages.size());
        }

    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == ADD_TO_CONTACTS_REQUEST) {
            userManager.fetchUserIfRequired(usersRealm, peer.getUserId());
            supportInvalidateOptionsMenu();
        } else {
            sendMessage(requestCode, data, peer.getUserId());
        }
    }

    @Override
    protected void onMessageQueued(@SuppressWarnings("UnusedParameters") String messageId) {
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
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (outOfSync) return;

        User user = usersRealm.where(User.class).notEqualTo(User.FIELD_ID, getMainUserId()).notEqualTo(User.FIELD_ID, peer.getUserId()).findFirst();
        boolean can4ward = user != null; //no other user apart from peer. cannot forward so lets hide it


        AdapterView.AdapterContextMenuInfo info = ((AdapterView.AdapterContextMenuInfo) menuInfo);
        Message message = messages.get(info.position);
        selectedMessage = message.getId();
        cursor = info.position;
        if (message.getType() != Message.TYPE_DATE_MESSAGE && message.getType() != Message.TYPE_TYPING_MESSAGE) {
            getMenuInflater().inflate(R.menu.message_context_menu, menu);
            menu.findItem(R.id.action_copy).setVisible(message.getType() == TYPE_TEXT_MESSAGE);
            if (message.getType() != TYPE_TEXT_MESSAGE) {
                menu.findItem(R.id.action_forward).setVisible(can4ward && new File(message.getMessageBody()).exists());
            } else {
                menu.findItem(R.id.action_forward).setVisible(can4ward);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        Message message = messageConversationRealm.where(Message.class).equalTo(Message.FIELD_ID, selectedMessage).findFirst();
        if (message == null) {
            UiHelpers.showPlainOlDialog(this, getString(R.string.err_message_not_found));
            return false;
        }
        if (itemId == R.id.action_copy) {
            @SuppressWarnings("deprecation")
            ClipboardManager manager = ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE));
            manager.setText(message.getMessageBody());
            return true;
        } else if (itemId == R.id.action_delete) {
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
        }
        return super.onContextItemSelected(item);
    }

    private void deleteMessage(int position) {
        outOfSync = true;
        messageConversationRealm.beginTransaction(); //beginning transaction earlier to force realm to prevent other realms from changing the data set
        //hook up message to remove.
        //if it is the only message for the day remove the date message
        //if it is the last message
        //if there are other messages set the newest to the just removed message as the last message of the conversation
        final Message currMessage = messages.get(position),
                previousToCurrMessage = messages.get(position - 1), //at least there will be a date message
                nextToCurrMessage = (messages.size() - 1 > position ? messages.get(position + 1) : null);

        final boolean wasLastForTheDay = nextToCurrMessage == null || Message.isDateMessage(nextToCurrMessage);
        final String messageBody = currMessage.getMessageBody();
        currMessage.removeFromRealm();
        if (Message.isDateMessage(previousToCurrMessage) &&
                wasLastForTheDay) {
            previousToCurrMessage.removeFromRealm(); //this will be a date message
        }
        if (currConversation.getLastMessage() == null) {
            int allMessages = messages.size() - 1;
            for (int i = allMessages; i > 0/*0th is the date*/; i--) {
                final Message cursor = messages.get(i);
                if (!Message.isDateMessage(cursor) && !Message.isTypingMessage(cursor)) {
                    currConversation.setLastMessage(cursor);
                    break;
                }
            }
        }
        messageConversationRealm.commitTransaction();
        outOfSync = false;
        adapter.notifyDataSetChanged();
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                if (UserManager.getInstance().getBoolPref(UserManager.DELETE_ATTACHMENT_ON_DELETE, false)) {
                    File file = new File(messageBody);
                    if (file.exists()) {
                        if (file.delete()) {
                            PLog.d(TAG, "deleted file successfully");
                        }
                    }
                }
            }
        }, false);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        handler.removeCallbacks(runnable);
        if (!s.toString().trim().isEmpty()) {
            ViewUtils.showViews(sendButton);
            if (!wasTyping) {
                wasTyping = true;
                LiveCenter.notifyTyping(peer.getUserId());
            }
            //TODO add some deviation to the timeout
            handler.postDelayed(runnable, 10000);
        } else {
            ViewUtils.hideViews(sendButton);
        }
        if (wasTyping) {
            wasTyping = false;
            LiveCenter.notifyNotTyping(peer.getUserId());
        }
    }

    @Override
    public void notifyUser(Context context, final Message message, String sender) {
        //noinspection StatementWithEmptyBody
        if (message.getTo().equals(peer.getUserId()) || message.getFrom().equals(peer.getUserId())
                || peer.getName().equals(sender)) {
            // TODO: 8/17/2015 give user a tiny hint of new messages and allow fast scroll
        } else {
            super.notifyUser(this, message, sender);
        }
    }

    @Override
    public void onTyping(String userId) {
        if (peer.getUserId().equals(userId)) {
            getSupportActionBar().setSubtitle(getString(R.string.writing));
        }
    }

    @Override
    public void onStopTyping(String userId) {
        if (peer.getUserId().equals(userId)) {
            updateUserStatus(LiveCenter.isOnline(userId));
        }
    }

    @Override
    public void onUserStatusChanged(String userId, boolean isOnline) {
        if (userId.equals(peer.getUserId())) {
            updateUserStatus(isOnline);
        }
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
        int scrollPosition = b.getInt(SCROLL_POSITION, messages.size());
        messagesListView.setSelection(scrollPosition);
        selectedMessage = b.getString(SELECTED_MESSAGE);
        if (selectedMessage == null) {
            selectedMessage = "";
        }
        wasTyping = b.getBoolean(WAS_TYPING, false);
        String typingMessage = b.getString(TYPING_MESSAGE);
        if (typingMessage != null) {
            messageEt.setText(typingMessage);
        }
        acceptedToUblock = b.getBoolean(UN_BLOCK, acceptedToUblock);
        userWasBlocked = b.getBoolean(WAS_BLOCKED, userWasBlocked);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Bundle b = new Bundle();
        b.putString(TYPING_MESSAGE, messageEt.getText().toString());
        b.putInt(SCROLL_POSITION, messagesListView.getLastVisiblePosition());
        if (cursor > -1) {
            b.putInt(CURSOR, cursor);
            b.putString(SELECTED_MESSAGE, selectedMessage);
        }
        b.putBoolean(UN_BLOCK, acceptedToUblock);
        b.putBoolean(WAS_BLOCKED, userWasBlocked);
        b.putBoolean(WAS_TYPING, wasTyping);
        outState.putParcelable(peer.getUserId(), b);
        super.onSaveInstanceState(outState);
    }
}
