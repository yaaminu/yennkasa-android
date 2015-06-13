package com.pair.pairapp.ui;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.pair.adapter.MessageJsonAdapter;
import com.pair.adapter.MessagesAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.messenger.MessageDispatcher;
import com.pair.net.Dispatcher;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmResults;


public class ChatActivity extends ActionBarActivity implements View.OnClickListener {
    public static final String TAG = ChatActivity.class.getSimpleName();
    public static final String PEER_ID = "peer id";
    private RealmResults<Message> messages;
    private User peer;
    private Conversation currConversation;
    private Realm realm;
    private ListView messagesListView;
    private EditText editText;
    private Button sendButton;
    private MessageDispatcher dispatcher;
    private Dispatcher.DispatcherMonitor monitor = new Dispatcher.DispatcherMonitor() {
        @Override
        public void onSendFailed(String reason, String messageId) {
            //TODO handle this callback
        }

        @Override
        public void onSendSucceeded(final String messageId) {
            //if dispatcher calls this method on a background thread we are doomed :-)
            //that's why we are not using the realm of chatactivity  here...
            Realm realm = Realm.getInstance(ChatActivity.this);
            realm.executeTransaction(new Realm.Transaction() {
                //this code is not asynchronous so we can close() realm and be sure we are not closing a realm instance which is in use
                @Override
                public void execute(Realm realm) {
                    Message message = realm.where(Message.class).equalTo("id", messageId).findFirst();
                    if (message != null) {
                        message.setState(Message.STATE_SENT);
                    }
                    realm.close();
                }
            });
            realm.close();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        realm = realm.getInstance(this);
        setContentView(R.layout.activity_chat);
        editText = ((EditText) findViewById(R.id.et_inputMsg));
        sendButton = ((Button) findViewById(R.id.btn_send));
        dispatcher = MessageDispatcher.getInstance(MessageJsonAdapter.INSTANCE, monitor, 10);
        sendButton.setOnClickListener(this);

        Bundle bundle = getIntent().getExtras();
        String peerId = bundle.getString(PEER_ID);
        peer = realm.where(User.class).equalTo("_id", peerId).findFirst();
        String peerName = peer.getName();
        getSupportActionBar().setTitle(peerName);

        //TODO change this query to a more general one than will work even when we add group chat
        messages = realm.where(Message.class).equalTo("from", peer.get_id()).or().equalTo("to", peer.get_id()).findAllSorted("dateComposed", true);
        getConversation(peerId);
        MessagesAdapter adapter = new MessagesAdapter(this, messages, true);
        messagesListView = ((ListView) findViewById(R.id.lv_messages));
        messagesListView.setAdapter(adapter);
    }

    private void getConversation(String peerId) {
        currConversation = realm.where(Conversation.class).equalTo("peerId", peerId).findFirst();
        realm.beginTransaction();
        if (currConversation == null) { //first time
            currConversation = realm.createObject(Conversation.class);
            currConversation.setPeerId(peerId);
            currConversation.setLastActiveTime(new Date());
            Message message = null;
            //re-construct the conversation
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
        //set up session
        String formatted = DateUtils.formatDateTime(this, new Date().getTime(), DateUtils.FORMAT_NUMERIC_DATE);
        Message message = realm.where(Message.class).equalTo("type", Message.TYPE_DATE_MESSAGE).equalTo("id", formatted + "@" + peer.get_id()).findFirst();
        if (message == null) {
            message = realm.createObject(Message.class);
            message.setId(formatted + "@" + peer.get_id());
            message.setMessageBody(formatted);
            message.setTo(peer.get_id());
            message.setDateComposed(new Date());
            message.setType(Message.TYPE_DATE_MESSAGE);
        }
        currConversation.setActive(true);
        realm.commitTransaction();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        realm.beginTransaction();
        currConversation.setActive(false);
        realm.commitTransaction();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        String content = UiHelpers.getFieldContent(editText);
        editText.setText(""); //clear the text field
        //TODO use a regular expression to validate the message body
        if (!content.isEmpty()) {
            realm.beginTransaction();
            Message message = realm.createObject(Message.class);
            message.setMessageBody(content);
            message.setTo(peer.get_id());
            message.setFrom(getCurrentUser().get_id());
            message.setDateComposed(new Date());
            //generate a unique id
            long messageCount = realm.where(Message.class).count() + 1;
            message.setId(messageCount + "@" + getCurrentUser().get_id() + "@" + System.currentTimeMillis());
            message.setState(Message.STATE_PENDING);
            currConversation.setLastMessage(message);
            currConversation.setLastActiveTime(message.getDateComposed());
            currConversation.setSummary(message.getMessageBody());
            realm.commitTransaction();
            dispatcher.dispatch(message);
        }
    }

    private User getCurrentUser() {
        return UserManager.getInstance(getApplication()).getCurrentUser();
    }
}
