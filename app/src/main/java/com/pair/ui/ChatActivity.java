package com.pair.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.pair.Config;
import com.pair.Errors.ErrorCenter;
import com.pair.Errors.PairappException;
import com.pair.adapter.MessagesAdapter;
import com.pair.adapter.UsersAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.MessageJsonAdapter;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.messenger.MessageProcessor;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.util.FileUtils;
import com.pair.util.MediaUtils;
import com.pair.util.UiHelpers;
import com.rey.material.app.DialogFragment;
import com.rey.material.app.ToolbarManager;
import com.rey.material.widget.CheckBox;
import com.rey.material.widget.SnackBar;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import io.realm.exceptions.RealmException;

import static com.pair.data.Message.TYPE_DATE_MESSAGE;
import static com.pair.data.Message.TYPE_TEXT_MESSAGE;
import static com.pair.data.Message.TYPE_TYPING_MESSAGE;


@SuppressWarnings({"ConstantConditions", "FieldCanBeLocal"})
public class ChatActivity extends PairAppActivity implements View.OnClickListener, AbsListView.OnScrollListener, TextWatcher, ItemsSelector.OnFragmentInteractionListener {
    private static final int TAKE_PHOTO_REQUEST = 0x0,
            TAKE_VIDEO_REQUEST = 0x1,
            PICK_PHOTO_REQUEST = 0x2,
            PICK_VIDEO_REQUEST = 0x3,
            PICK_FILE_REQUEST = 0x4,
            ADD_USERS_REQUEST = 0x5;
    private static final String TAG = ChatActivity.class.getSimpleName();
    public static final String EXTRA_PEER_ID = "peer id";

    private RealmResults<Message> messages;
    private User peer;
    private Conversation currConversation;
    private Realm realm;
    private ListView messagesListView;
    private EditText messageEt;
    private View sendButton, attach, dateHeaderViewParent;
    private TextView dateHeader;
    private MessagesAdapter adapter;
    private boolean sessionSetup = false;
    private static Message selectedMessage;
    private ToolbarManager toolbarManager;
    private Toolbar toolBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        toolBar = (Toolbar) findViewById(R.id.main_toolbar);
        toolbarManager = new ToolbarManager(this, toolBar, 0, R.style.MenuItemRippleStyle, R.anim.abc_fade_in, R.anim.abc_fade_out);
        realm = Realm.getInstance(this);
        messageEt = ((EditText) findViewById(R.id.et_message));
        sendButton = findViewById(R.id.iv_send);
        messagesListView = ((ListView) findViewById(R.id.lv_messages));
        dateHeader = ((TextView) findViewById(R.id.tv_header_date));
        snackBar = (SnackBar) findViewById(R.id.notification_bar);
        attach = findViewById(R.id.iv_attach);
        dateHeaderViewParent = findViewById(R.id.cv_date_header_parent);

        attach.setOnClickListener(this);
        Bundle bundle = getIntent().getExtras();
        String peerId = bundle.getString(EXTRA_PEER_ID);
        peer = realm.where(User.class).equalTo(User.FIELD_ID, peerId).findFirst();
        if (peer == null) {
            realm.beginTransaction();
            peer = realm.createObject(User.class);
            peer.setUserId(peerId);
            String[] parts = peerId.split("@"); //in case the peer is a group
            peer.setType(parts.length > 1 ? User.TYPE_GROUP : User.TYPE_NORMAL_USER);
            peer.setHasCall(false); //we cannot tell for now
            peer.setDP(peerId);
            peer.setName(parts[0]);
            peer.setStatus(getString(R.string.st_offline));
            realm.commitTransaction();
            UserManager.getInstance().refreshUserDetails(peerId); //async
        }
        String peerName = peer.getName();
        //noinspection ConstantConditions
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(peerName);
        if (!User.isGroup(peer)) {
            actionBar.setSubtitle(peer.getStatus());
        }
        actionBar.setDisplayHomeAsUpEnabled(true);
        messages = realm.where(Message.class).equalTo(Message.FIELD_FROM, peer.getUserId())
                .or()
                .equalTo(Message.FIELD_TO, peer.getUserId())
                .findAllSorted(Message.FIELD_DATE_COMPOSED, true);
        setUpCurrentConversation();
        adapter = new MessagesAdapter(this, messages, true);
        messagesListView.setAdapter(adapter);
        sendButton.setOnClickListener(this);
        messageEt.addTextChangedListener(this);
        messagesListView.setOnScrollListener(this);
        registerForContextMenu(messagesListView);
        ensureDateSet();
        // TODO: 8/22/2015 in future we will move to the last  un seen message if any
        messagesListView.setSelection(messages.size()); //move to last
    }

    private void setUpCurrentConversation() {
        String peerId = peer.getUserId();
        currConversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();
        // FIXME: 8/4/2015 move this to a background thread
        if (currConversation == null) { //first time
            Conversation.newConversation(this, peerId, true);
        }
        //round trips!
        currConversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        toolbarManager.onPrepareMenu();
        menu = toolBar.getMenu();
        if (menu != null && menu.size() > 0) { //required for toolbar to behave on older platforms <=10
            User mainUser = UserManager.getInstance().getCurrentUser();
            menu.findItem(R.id.action_invite_friends)
                    .setVisible(peer.getType() == User.TYPE_GROUP && peer.getAdmin().getUserId().equals(mainUser.getUserId()) && !isForwarding);
            menu.findItem(R.id.action_view_profile).setTitle((peer.getType() == User.TYPE_GROUP) ? R.string.st_group_info : R.string.st_view_profile);
            menu.findItem(R.id.action_view_profile).setVisible(!isForwarding);
            menu.findItem(R.id.action_done).setVisible(isForwarding && !recipientsIds.isEmpty());
        }
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
        if (id == android.R.id.home) {
            if (isForwarding) {
                isForwarding = false;
                goBack();
                return true;
            }
            return super.onOptionsItemSelected(item);
        } else if (id == R.id.action_invite_friends) {
            Intent intent = new Intent(this, InviteActivity.class);
            intent.putExtra(InviteActivity.EXTRA_GROUP_ID, peer.getUserId());
            startActivityForResult(intent, ADD_USERS_REQUEST);
            return true;
        } else if (id == R.id.action_view_profile) {
            UiHelpers.gotoProfileActivity(this, peer.getUserId());
            return true;
        } else if (id == R.id.action_done) {
            forwardToAll(recipientsIds);
            goBack();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    boolean wasDateHeaderVisible = false;

    private void goBack() {
        isForwarding = false;
        getSupportFragmentManager().popBackStackImmediate();
        getSupportActionBar().setTitle(peer.getName());
        findViewById(R.id.ll_list_view_container).setVisibility(View.VISIBLE);
        dateHeaderViewParent.setVisibility(wasDateHeaderVisible ? View.VISIBLE : View.GONE);
        getSupportActionBar().setSubtitle(peer.getStatus());
        supportInvalidateOptionsMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG,"on resume");
        Config.appOpen(true);
        super.clearRecentChat();
        testChatActivity();
    }

    @Override
    protected void onPause() {
        Log.i(TAG,"onpause");
        if (currConversation != null) {
            realm.beginTransaction();
            currConversation.setActive(false);
            realm.commitTransaction();
        }
        Config.appOpen(false);
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onBind() {
        pairAppClientInterface.registerUINotifier(this);
    }

    @Override
    protected void onUnbind() {
        pairAppClientInterface.unRegisterUINotifier(this);
    }

    @Override
    protected void onDestroy() {
        if (timer != null) {
//            timer.cancel();
//            timer.purge();
        }
        realm.close();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.iv_send:
                sendTextMessage();
                break;
            case R.id.iv_attach:
                attach();
                break;
            default:
                throw new AssertionError();
        }
    }

    private void sendTextMessage() {
        String content = messageEt.getText().toString().trim();
        messageEt.setText(""); //clear the text field
        //TODO use a regular expression to validate the message body
        if (!TextUtils.isEmpty(content)) {
            if (messages.isEmpty()) {
                ensureDateSet();
            }
            try {
                enqueueMessage(createMessage(content, Message.TYPE_TEXT_MESSAGE));
            } catch (PairappException e) {
                Log.e(TAG, e.getMessage(), e.getCause());
                ErrorCenter.reportError(TAG, e.getMessage());
            }
        }

    }

    private void enqueueMessage(Message message) {
        doSendMessage(message);
    }

    private void doSendMessage(Message message) {
        if (bound) {
            pairAppClientInterface.sendMessage(message);
        } else {
            doBind(); //after binding dispatcher will smartly dispatch all unsent messages
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messagesListView.setSelection(messages.size() - 1);
            }
        });
    }

    private void trySetupNewSession() {
        //set up session
        if (!sessionSetup) {
            if (currConversation == null) {
                setUpCurrentConversation();
            }
            Conversation.newSession(realm, currConversation);
            sessionSetup = true;
        }
    }

    private Message createMessage(String messageBody, int type) throws PairappException {
        // FIXME: 8/4/2015 run in a background thread
        realm.beginTransaction();
        trySetupNewSession();
        try {
            Message message = Message.makeNew(realm, messageBody, peer.getUserId(), type);
            currConversation.setLastMessage(message);
            currConversation.setLastActiveTime(message.getDateComposed());
            String summary;
            if (type == Message.TYPE_TEXT_MESSAGE) {
                summary = Message.state(this, message.getState()) + "      " + message.getMessageBody();
            } else {
                summary = Message.state(this, message.getState()) + "  " + getDescription(type);
            }
            currConversation.setSummary(summary);
            realm.commitTransaction();
            return message;
        } catch (PairappException e) { //caught for the for the purpose of cleanup
            realm.cancelTransaction();
            throw new PairappException(e.getMessage(), "");
        }
    }

    private User getCurrentUser() {
        return UserManager.getInstance().getCurrentUser();
    }

    private static String getDescription(int type) {
        switch (type) {
            case Message.TYPE_BIN_MESSAGE:
                return "File";
            case Message.TYPE_PICTURE_MESSAGE:
                return "Image";
            case Message.TYPE_VIDEO_MESSAGE:
                return "video";
            default:
                return "Image";
        }
    }

    private DialogInterface.OnClickListener dialogListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case TAKE_PHOTO_REQUEST:
                    //take picture.
                    takePhoto();
                    break;
                case TAKE_VIDEO_REQUEST:
                    recordVideo();
                    break;
                case PICK_PHOTO_REQUEST:
                    choosePicture();
                    break;
                case PICK_VIDEO_REQUEST:
                    chooseVideo();
                    break;
                case PICK_FILE_REQUEST:
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    startActivityForResult(intent, PICK_FILE_REQUEST);
                    break; //safety!
                default:
                    break;
            }
        }
    };


    private void chooseVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        startActivityForResult(intent, PICK_VIDEO_REQUEST);
    }

    private void choosePicture() {
        Intent attachIntent;
        attachIntent = new Intent(Intent.ACTION_GET_CONTENT);
        attachIntent.setType("image/*");
        startActivityForResult(attachIntent, PICK_PHOTO_REQUEST);
    }

    private Uri mMediaUri;

    private void recordVideo() {
        try {
            mMediaUri = FileUtils.getOutputUri(FileUtils.MEDIA_TYPE_VIDEO);
            MediaUtils.recordVideo(this, mMediaUri, TAKE_VIDEO_REQUEST);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, e.getMessage(), e.getCause());
            } else {
                Log.e(TAG, e.getMessage());
            }
            Toast.makeText(ChatActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void takePhoto() {
        try {
            mMediaUri = FileUtils.getOutputUri(FileUtils.MEDIA_TYPE_IMAGE);
            MediaUtils.takePhoto(this, mMediaUri, TAKE_PHOTO_REQUEST);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, e.getMessage(), e.getCause());
            } else {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG,"onActivityResult");
        if (resultCode != RESULT_OK) {
            return;
        }
        Message message;
        try {
            switch (requestCode) {
                case PICK_PHOTO_REQUEST:
                    message = createMessage(getActualPath(data), Message.TYPE_PICTURE_MESSAGE);
                    break;
                case TAKE_PHOTO_REQUEST:
                    message = createMessage(mMediaUri.getPath(), Message.TYPE_PICTURE_MESSAGE);
                    break;
                case TAKE_VIDEO_REQUEST:
                    message = createMessage(mMediaUri.getPath(), Message.TYPE_VIDEO_MESSAGE);
                    break;
                case PICK_VIDEO_REQUEST:
                    message = createMessage(getActualPath(data), Message.TYPE_VIDEO_MESSAGE);
                    break;
                case PICK_FILE_REQUEST:
                    String actualPath = getActualPath(data);
                    String extension = FileUtils.getExtension(actualPath);
                    int type = Message.TYPE_BIN_MESSAGE;
                    //some image formats like bmp,gif are considered invalid
                    if (extension.equals("jpeg") || extension.equals("jpg") || extension.equals("png")) {
                        type = Message.TYPE_PICTURE_MESSAGE;
                    } else if (extension.equals("mp4") || extension.equals("3gp")) {
                        type = Message.TYPE_VIDEO_MESSAGE;
                    }
                    message = createMessage(actualPath, type);
                    break;
                default:
                    throw new AssertionError("impossible");
            }
            enqueueMessage(message);
        } catch (PairappException e) {
            ErrorCenter.reportError(TAG,e.getMessage());
        }
    }

    private void forwardToAll(final Set<String> recipients) {
        final DialogFragment fragment = UiHelpers.newProgressDialog();
        fragment.show(getSupportFragmentManager(), null);
        if (recipients == null || recipients.isEmpty()) return;
        final Message backGroundRealmVersion = new Message(selectedMessage); //copy first
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                doForwardMessage(recipients, backGroundRealmVersion);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                fragment.dismiss();
            }
        };

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            task.execute();
        }
    }

    private void doForwardMessage(Set<String> recipients, Message backGroundRealmVersion) {
        List<Message> tobeSent = new ArrayList<>(recipients.size());
        Realm realm = Realm.getInstance(this);
        try {
            for (String recipient : recipients) {
                Conversation conversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, recipient).findFirst();
                realm.beginTransaction();
                if (conversation == null) {
                    conversation = realm.createObject(Conversation.class);
                    conversation.setPeerId(recipient);
                }
                conversation.setActive(false);
                conversation.setLastActiveTime(new Date());
                Conversation.newSession(realm, conversation);
                realm.commitTransaction();

                realm.beginTransaction();
                backGroundRealmVersion.setFrom(getCurrentUser().getUserId());
                backGroundRealmVersion.setTo(recipient);
                backGroundRealmVersion.setDateComposed(new Date(System.currentTimeMillis() + 1));
                backGroundRealmVersion.setState(Message.STATE_PENDING);
                backGroundRealmVersion.setId(Message.generateIdPossiblyUnique());
                Message message = realm.copyToRealm(backGroundRealmVersion);
                tobeSent.add(message);
                conversation.setLastMessage(message);
                if (message.getType() == TYPE_TEXT_MESSAGE) {
                    conversation.setSummary(message.getMessageBody());
                } else {
                    conversation.setSummary(getDescription(message.getType()));
                }
                realm.commitTransaction();
            }
            if (bound) {
                pairAppClientInterface.sendMessages(tobeSent);
            }
        } finally {
            realm.close();
        }
    }


    private String getActualPath(Intent data) {
        String actualPath;
        Uri uri = data.getData();
        if (uri.getScheme().equals("content")) {
            actualPath = FileUtils.resolveContentUriToFilePath(uri);
        } else {
            actualPath = uri.getPath();
        }
        return actualPath;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {

    }

    @Override
    public void onScroll(AbsListView view, final int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        //check if the members have filled the screen
        if (firstVisibleItem == 0) { //first/second item
            dateHeaderViewParent.setVisibility(View.GONE);// TODO: 8/7/2015 fade instead of hiding right away
            return;
        }
        if (visibleItemCount != 0 && visibleItemCount < totalItemCount) {
            dateHeaderViewParent.setVisibility(View.VISIBLE);
            for (int i = firstVisibleItem; i >= 0; i--) { //loop backwards
                final Message message = messages.get(i);
                if (message.getType() == TYPE_DATE_MESSAGE) {
                    dateHeader.setText(message.getMessageBody());
                    return;
                }
            }
            //if we've got here then somehow a  session was not set up correctly.
            // do we have to clean that mess or
            //do this: throw new AssertionError("impossible");

            ensureDateSet();
        }
    }

    private void ensureDateSet() {
        Message message;
        if (!messages.isEmpty() && Message.isDateMessage(messages.get(0))) {
            return;
        }

        try {
            message = new Message();
            String formatted = DateUtils.formatDateTime(this, new Date().getTime(), DateUtils.FORMAT_NUMERIC_DATE);
            message.setId(peer.getUserId() + formatted);
            message.setMessageBody(formatted);
            message.setTo(UserManager.getInstance().getCurrentUser().getUserId());
            message.setFrom(peer.getUserId());
            Date latestDate = messages.minDate(Message.FIELD_DATE_COMPOSED);
            //one second older than the oldest message
            latestDate = latestDate == null ? new Date() : new Date(latestDate.getTime() - 1000);
            message.setDateComposed(latestDate);
            message.setType(TYPE_DATE_MESSAGE);
            realm.beginTransaction();
            realm.copyToRealm(message);
            realm.commitTransaction();
        } catch (RealmException primaryKeyException) {
            Log.d(TAG, "date already setup");
            realm.cancelTransaction();
        }
    }

    private static int cursor = -1; //static so that it can resist activity restarts.

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = ((AdapterView.AdapterContextMenuInfo) menuInfo);
        selectedMessage = messages.get(info.position);
        cursor = info.position;
        if (selectedMessage.getType() != Message.TYPE_DATE_MESSAGE && selectedMessage.getType() != Message.TYPE_TYPING_MESSAGE) {
            getMenuInflater().inflate(R.menu.message_context_menu, menu);
            menu.findItem(R.id.action_copy).setVisible(selectedMessage.getType() == TYPE_TEXT_MESSAGE);
            if (selectedMessage.getType() != TYPE_TEXT_MESSAGE) {
                menu.findItem(R.id.action_forward).setVisible(new File(selectedMessage.getMessageBody()).exists());
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_copy) {
            ClipboardManager manager = ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE));
            manager.setText(selectedMessage.getMessageBody());
            return true;
        } else if (itemId == R.id.action_delete) {
            realm.beginTransaction();
            Message next;
            try {
                next = messages.get(cursor + 1);
                if (next.getType() == TYPE_DATE_MESSAGE || next.getType() == TYPE_TYPING_MESSAGE) {
                    throw new ArrayIndexOutOfBoundsException(null);//jump to catch clause
                }
            } catch (ArrayIndexOutOfBoundsException wasLastMessage) { //move up
                Message previous = messages.get(cursor - 1); //cannot be null and cannot be of type: Message#TYPING_MESSAGE
                if (previous.getType() == TYPE_DATE_MESSAGE) {
                    previous.removeFromRealm(); //delete session message
                }
            }
            selectedMessage.removeFromRealm(); // remove the message here so that you ensure the cursor remain valid
            realm.commitTransaction();
            return true;
        } else if (itemId == R.id.action_forward) {
            Fragment fragment = new ItemsSelector();
            Bundle bundle = new Bundle(1);
            bundle.putString(MainActivity.ARG_TITLE, getString(R.string.forward_to));
            fragment.setArguments(bundle);
            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment)
                    .addToBackStack(null)
                    .commit();
            findViewById(R.id.ll_list_view_container).setVisibility(View.GONE);
            dateHeaderViewParent.setVisibility(View.GONE);
            wasDateHeaderVisible = (dateHeaderViewParent.getVisibility() == View.VISIBLE);
            getSupportActionBar().setSubtitle("");
            isForwarding = true;
            supportInvalidateOptionsMenu();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * code purposely for testing we will take this off in production
     */
    private void testChatActivity() {
//        final String senderId = peer.getUserId();
//        timer = new Timer(true);
//        TimerTask task = new TimerTask() {
//            @Override
//            public void run() {
//                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST);
//                testMessageProcessor(RealmUtils.seedIncomingMessages(senderId, getCurrentUser().getUserId()));
//            }
//        };
//        timer.scheduleAtFixedRate(task, 1000, 45000);
    }

    Timer timer;

    private void testMessageProcessor(Message messages) {
        JsonObject object = MessageJsonAdapter.INSTANCE.toJson(messages);
        Context context = Config.getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("message", object.toString());
        Intent intent = new Intent(context, MessageProcessor.class);
        intent.putExtras(bundle);
        context.startService(intent);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
//        if (!s.toString().trim().isEmpty()) {
//            liveTexView.setVisibility(View.VISIBLE);
//            liveTexView.setText(s);
//        } else {
//            liveTexView.setVisibility(View.GONE);
//        }
    }

    @Override
    public void notifyUser(Context context, final Message message, String sender) {
        if (sender.equals(peer.getName())) {
            // TODO: 8/17/2015 give user a tiny hint of new messages and allow fast scroll
        } else {
            super.notifyUser(this, message, sender);
        }
    }

    private void attach() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setItems(R.array.attach_options, dialogListener);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onBackPressed() {
        if (isForwarding) {
            isForwarding = false;
            goBack();
        } else {
            super.onBackPressed();
        }
    }

    private boolean isForwarding = false;

    private RealmQuery<User> getRecipients() {
        return realm.where(User.class)
                .notEqualTo(User.FIELD_ID, getCurrentUser().getUserId())
                .notEqualTo(User.FIELD_ID, peer.getUserId());
    }

    private UsersAdapter recipientsAdapter;

    @Override
    public BaseAdapter getAdapter() {
        final RealmResults<User> results = getRecipients().findAllSorted(User.FIELD_NAME);
        recipientsIds.clear();
        recipientsAdapter = new UsersAdapter(this, realm, results, true) {
            @Override
            protected RealmQuery<User> getOriginalQuery() {
                return getRecipients();
            }

            @Override
            public View getView(final int position, View convertView, final ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                final CheckBox checkBox = (CheckBox) view.findViewById(R.id.cb_checked);
                final String userId = getItem(position).getUserId();
                checkBox.setChecked(recipientsIds.contains(userId));
                checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            recipientsIds.add(userId);
                        } else {
                            recipientsIds.remove(userId);
                        }
                        ((ListView) parent).setItemChecked(position, isChecked);
                        supportInvalidateOptionsMenu();
                    }
                });
                return view;
            }
        };
        return recipientsAdapter;
    }

    @Override
    public Filterable filter() {
        return recipientsAdapter;
    }

    @Override
    public ItemsSelector.ContainerType preferredContainer() {
        return ItemsSelector.ContainerType.LIST;
    }

    @Override
    public View emptyView() {
        return null;
    }

    @Override
    public boolean multiChoice() {
        return true;
    }

    @Override
    public boolean supportAddCustom() {
        return false;
    }

    @Override
    public void onCustomAdded(String item) {

    }

    private static Set<String> recipientsIds = new HashSet<>();

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final UsersAdapter adapter = (UsersAdapter) parent.getAdapter();
        User user = adapter.getItem(position);
        if (((ListView) parent).isItemChecked(position)) {
            recipientsIds.add(user.getUserId());
        } else {
            recipientsIds.remove(user.getUserId());
        }
        ((CheckBox) view.findViewById(R.id.cb_checked)).setChecked(recipientsIds.contains(adapter.getItem(position).getUserId()));
        supportInvalidateOptionsMenu();
    }
}