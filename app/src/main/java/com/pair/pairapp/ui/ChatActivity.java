package com.pair.pairapp.ui;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.pair.adapter.MessagesAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.messenger.PairAppClient;
import com.pair.net.Dispatcher;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.FileHelper;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import java.util.ArrayList;
import java.util.Date;

import io.realm.Realm;
import io.realm.RealmResults;

import static com.pair.data.Message.TYPE_DATE_MESSAGE;


@SuppressWarnings({"ConstantConditions", "FieldCanBeLocal"})
public class ChatActivity extends ActionBarActivity implements View.OnClickListener, AbsListView.OnScrollListener, TextView.OnEditorActionListener {
    private static final int TAKE_PHOTO_REQUEST = 0x0;
    private static final int TAKE_VIDEO_REQUEST = 0x1;
    private static final int PICK_PHOTO_REQUEST = 0x2;
    private static final int PICK_VIDEO_REQUEST = 0x3;
    private static final int PICK_FILE_REQUEST = 0x4;
    private static final int ADD_USERS_REQUEST = 0x5;

    private static final String TAG = ChatActivity.class.getSimpleName();
    public static final String EXTRA_PEER_ID = "peer id";

    private RealmResults<Message> messages;
    private User peer;
    private Conversation currConversation;
    private Realm realm;
    private ListView messagesListView;
    private EditText editText;
    private Button sendButton;
    private TextView dateHeader;
    private Dispatcher<Message> dispatcher;
    private boolean bound = false;
    private MessagesAdapter adapter;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            dispatcher = ((PairAppClient.PairAppClientInterface) service).getMessageDispatcher();
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            dispatcher = null; //free memory.
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        realm = Realm.getInstance(this);
        editText = ((EditText) findViewById(R.id.et_inputMsg));
        sendButton = ((Button) findViewById(R.id.btn_send));
        messagesListView = ((ListView) findViewById(R.id.lv_messages));
        dateHeader = ((TextView) findViewById(R.id.tv_header_date));
        sendButton.setOnClickListener(this);
        editText.setOnEditorActionListener(this);
        Bundle bundle = getIntent().getExtras();
        String peerId = bundle.getString(EXTRA_PEER_ID);
        peer = realm.where(User.class).equalTo(User.FIELD_ID, peerId).findFirst();
        if (peer == null) {
            realm.beginTransaction();
            peer = realm.createObject(User.class);
            peer.set_id(peerId);
            String [] parts = peerId.split("@");
            peer.setType(parts.length > 1?User.TYPE_GROUP:User.TYPE_NORMAL_USER);
            peer.setDP(peerId);
            peer.setLocalName(parts[0]);
            peer.setName(parts[0]);
            realm.commitTransaction();
        }
        String peerName = peer.getName();
        //noinspection ConstantConditions
        getSupportActionBar().setTitle(peerName);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        messages = realm.where(Message.class).equalTo(Message.FIELD_FROM, peer.get_id())
                .or()
                .equalTo(Message.FIELD_TO, peer.get_id())
                .findAllSorted(Message.FIELD_DATE_COMPOSED, true);
        getConversation(peerId);
        adapter = new MessagesAdapter(this, messages, true);
        messagesListView.setAdapter(adapter);
        messagesListView.setOnScrollListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        doBind();
    }

    private void doBind() {
        Intent intent = new Intent(this, PairAppClient.class);
        intent.putExtra(PairAppClient.ACTION, PairAppClient.ACTION_SEND_ALL_UNSENT);
        bindService(intent, connection, BIND_AUTO_CREATE);
    }

    private void getConversation(String peerId) {
        currConversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();
        // FIXME: 8/4/2015 move this to a background thread
        realm.beginTransaction();
        if (currConversation == null) { //first time
            currConversation = realm.createObject(Conversation.class);
            currConversation.setPeerId(peerId);
            currConversation.setLastActiveTime(new Date());
            Message message = null;
            //re-construct the conversation
            // TODO: 8/2/2015 one day we will take this off if conversation is deleted all messages in that conversation will be deleted as well
            if (messages.size() > 0) { // i don't no why , but realm throws some exception when i attempt to read from an empty realm results
                message = messages.last();
            }
            if (message == null) {
                currConversation.setSummary("touch to start chatting with " + peer.getName());
            } else {
                currConversation.setLastMessage(message);
                currConversation.setSummary(message.getMessageBody());
            }
        }
        currConversation.setActive(true);
        realm.commitTransaction();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        User mainUser = UserManager.getInstance().getMainUser();
        menu.findItem(R.id.action_add_peers).setVisible(peer.getType() == User.TYPE_GROUP && peer.getAdmin().get_id().equals(mainUser.get_id()));
        menu.findItem(R.id.action_peer_info).setTitle((peer.getType() == User.TYPE_GROUP) ? R.string.st_group_info : R.string.st_peer_info);
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
        if (id == R.id.action_attach) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setItems(R.array.attach_options, dialogListener);
            AlertDialog dialog = builder.create();
            dialog.show();
            return true;
        } else if (id == R.id.action_add_peers) {
            Intent intent = new Intent(this, FriendsActivity.class);
            intent.putExtra(FriendsActivity.EXTRA_GROUP_ID, peer.get_id());
            startActivityForResult(intent, ADD_USERS_REQUEST);
            return true;
        } else if (id == R.id.action_peer_info) {
            UiHelpers.gotoProfileActivity(this, peer.get_id());
            return true;
        } else if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Config.setIsChatRoomOpen(true);
    }

    @Override
    protected void onPause() {
        realm.beginTransaction();
        currConversation.setActive(false);
        realm.commitTransaction();
        Config.setIsChatRoomOpen(false);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (bound) {
            bound = false;
            unbindService(connection);
        }
        dispatcher = null;
        adapter = null;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        doSendMessage();
    }

    private void doSendMessage() {
        String content = UiHelpers.getFieldContent(editText);
        editText.setText(""); //clear the text field
        //TODO use a regular expression to validate the message body
        if (!TextUtils.isEmpty(content)) {
            Message message = createMessage(content, Message.TYPE_TEXT_MESSAGE);
            sendMessage(message);
        }
    }

    private void sendMessage(Message message) {
        if (bound && (dispatcher != null)) {
            dispatcher.dispatch(message);
        } else {
            doBind(); //after binding dispatcher will smartly dispatch all unsent messages
        }
    }

    private void trySetupNewSession() {
        //set up session
        Conversation.newSession(realm, currConversation);
    }

    private Message createMessage(String messageBody, int type) {
        // FIXME: 8/4/2015 run a background thread
        realm.beginTransaction();
        trySetupNewSession();
        Message message = realm.createObject(Message.class);
        message.setMessageBody(messageBody);
        message.setTo(peer.get_id());
        message.setFrom(getCurrentUser().get_id());
        message.setDateComposed(new Date());
        message.setId(Message.generateIdPossiblyUnique());
        message.setState(Message.STATE_PENDING);
        message.setType(type);
        currConversation.setLastMessage(message);
        currConversation.setLastActiveTime(message.getDateComposed());
        String summary;
        if (type == Message.TYPE_TEXT_MESSAGE) {
            summary = "You: " + message.getMessageBody();
        } else {
            summary = "You: " + getDescription(type);
        }
        currConversation.setSummary(summary);
        realm.commitTransaction();
        return message;
    }

    private User getCurrentUser() {
        return UserManager.getInstance().getMainUser();
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
            Intent attachIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            mMediaUri = FileHelper.getOutputUri(FileHelper.MEDIA_TYPE_IMAGE);
            attachIntent.putExtra(MediaStore.EXTRA_OUTPUT, mMediaUri);
            attachIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
            attachIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60 * 10);
            startActivityForResult(attachIntent, TAKE_VIDEO_REQUEST);
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
            Intent attachIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            mMediaUri = FileHelper.getOutputUri(FileHelper.MEDIA_TYPE_IMAGE);
            attachIntent.putExtra(MediaStore.EXTRA_OUTPUT, mMediaUri);
            startActivityForResult(attachIntent, TAKE_PHOTO_REQUEST);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, e.getMessage(), e.getCause());
            } else {
                Log.e(TAG, e.getMessage());
            }
            Toast.makeText(ChatActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode != RESULT_OK) {
            Toast.makeText(this, "request canceled", Toast.LENGTH_LONG).show();
            return;
        }

        Message message = null;
        switch (requestCode) {
            case ADD_USERS_REQUEST:
                addMembersToGroup(data.getStringArrayListExtra(FriendsActivity.SELECTED_USERS));
                break;
            case PICK_PHOTO_REQUEST:
                message = createMessage(getActualPath(data), Message.TYPE_PICTURE_MESSAGE);
                break;
            case TAKE_PHOTO_REQUEST:
                message = createMessage(mMediaUri.getPath(), Message.TYPE_PICTURE_MESSAGE);
                break;
            case TAKE_VIDEO_REQUEST:
                //fall through
                message = createMessage(mMediaUri.getPath(), Message.TYPE_VIDEO_MESSAGE);
                break;
            case PICK_VIDEO_REQUEST:
                message = createMessage(getActualPath(data), Message.TYPE_VIDEO_MESSAGE);
                break;
            case PICK_FILE_REQUEST:
                String actualPath = getActualPath(data);
                String extension = FileHelper.getExtension(actualPath);
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
        sendMessage(message);
    }

    private void addMembersToGroup(ArrayList<String> members) {

        UserManager userManager = UserManager.getInstance();
        if (userManager.isGroup(peer.get_id())) {
            userManager.addMembersToGroup(peer.get_id(), members, new UserManager.CallBack() {
                @Override
                public void done(Exception e) {
                    if (e != null) {
                        UiHelpers.showToast(e.getMessage());
                    }
                }
            });
        }
    }

    private String getActualPath(Intent data) {
        String actualPath;
        Uri uri = data.getData();
        if (uri.getScheme().equals("content")) {
            actualPath = FileHelper.resolveContentUriToFilePath(uri);
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
        if (firstVisibleItem == 0) { //first item
            dateHeader.setVisibility(View.GONE);
            return;
        }
        if (visibleItemCount != 0 && visibleItemCount < totalItemCount && firstVisibleItem > 0) {
            dateHeader.setVisibility(View.VISIBLE);
            for (int i = firstVisibleItem; i >= 0; i--) { //loop backwards
                final Message message = messages.get(i);
                if (message.getType() == TYPE_DATE_MESSAGE) {
                    dateHeader.setText(message.getMessageBody());
                    return;
                }
            }
            throw new AssertionError("impossible");
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        doSendMessage();
        return true;
    }
}